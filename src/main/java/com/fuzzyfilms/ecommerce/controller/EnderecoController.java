package com.fuzzyfilms.ecommerce.controller;

import com.fuzzyfilms.ecommerce.model.AuditLog.TipoAcao;
import com.fuzzyfilms.ecommerce.model.Endereco;
import com.fuzzyfilms.ecommerce.model.User;
import com.fuzzyfilms.ecommerce.repository.EnderecoRepository;
import com.fuzzyfilms.ecommerce.repository.UserRepository;
import com.fuzzyfilms.ecommerce.service.AuditService;
import com.fuzzyfilms.ecommerce.service.EnderecoValidacaoService;
import com.fuzzyfilms.ecommerce.service.TwoFactorService;
import com.fuzzyfilms.ecommerce.util.HashUtil;
import org.springframework.beans.factory.annotation.Value;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/minha-conta/endereco")
public class EnderecoController {

    @Autowired private EnderecoRepository enderecoRepo;
    @Autowired private UserRepository userRepo;
    @Autowired private EnderecoValidacaoService validacaoService;
    @Autowired private TwoFactorService tfaService;
    @Autowired private AuditService auditService;

    /** Secret para HMAC do CPF — configure em app.cpf-secret (mínimo 32 chars). */
    @Value("${app.cpf-secret}")
    private String cpfSecret;

    // ─── Exibir formulário (GET) ────────────────────────────────────
    @GetMapping
    public String form(@AuthenticationPrincipal UserDetails ud, Model model) {
        User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();
        Endereco end = enderecoRepo.findByUser(user).orElse(null);
        model.addAttribute("endereco", end);
        model.addAttribute("sucesso", null);
model.addAttribute("erro", null);
model.addAttribute("info", null);
        return "endereco";
    }

    // ─── 1ª etapa: validar dados e decidir se usa 2FA ───────────────
    @PostMapping("/salvar")
    public String prepararSalvar(@RequestParam String logradouro,
                                 @RequestParam String cep,
                                 @RequestParam String numero,
                                 @RequestParam(required = false) String complemento,
                                 @RequestParam String cpf,
                                 @RequestParam(required = false) String telefone, 
                                 @RequestParam String horarioEntrega,
                                 @AuthenticationPrincipal UserDetails ud,
                                 HttpServletRequest req,
                                 HttpSession session,
                                 RedirectAttributes ra) {

        User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();

        // --- Validações de campos ---
        if (logradouro.trim().length() < 3) {
            ra.addFlashAttribute("erro", "Endereço deve ter no mínimo 3 caracteres.");
            return "redirect:/minha-conta/endereco";
        }
        if (numero.trim().isEmpty()) {
            ra.addFlashAttribute("erro", "Número é obrigatório.");
            return "redirect:/minha-conta/endereco";
        }
        if (horarioEntrega == null || horarioEntrega.isBlank()) {
            ra.addFlashAttribute("erro", "Selecione um horário de entrega.");
            return "redirect:/minha-conta/endereco";
        }

        // --- Validação do CEP (via ViaCEP) ---
        EnderecoValidacaoService.ViaCepResponse viaCep = validacaoService.validarCep(cep);
        if (viaCep == null) {
            ra.addFlashAttribute("erro", "CEP inválido ou não encontrado.");
            return "redirect:/minha-conta/endereco";
        }
        String cidade = viaCep.getLocalidade();
        String estado = viaCep.getUf();
        String logradouroCompleto = viaCep.getLogradouro();
        if (logradouroCompleto == null || logradouroCompleto.isBlank()) {
            logradouroCompleto = logradouro;
        }

        // --- Validação do CPF (formato e algoritmo) ---
        String cpfLimpo = cpf.replaceAll("\\D", "");
        if (!validacaoService.cpfValido(cpfLimpo)) {
            ra.addFlashAttribute("erro", "CPF inválido.");
            return "redirect:/minha-conta/endereco";
        }
        String cpfHash = HashUtil.hmacCpf(cpfLimpo, cpfSecret);
        Endereco enderecoExistente = enderecoRepo.findByUser(user).orElse(null);

        // Verifica unicidade do CPF — mensagem genérica para não revelar o motivo
        if (enderecoExistente == null) {
            if (enderecoRepo.existsByCpfHash(cpfHash)) {
                ra.addFlashAttribute("erro", "Não foi possível salvar o endereço. Verifique os dados e tente novamente.");
                return "redirect:/minha-conta/endereco";
            }
        } else {
            String oldHash = enderecoExistente.getCpfHash();
            if (!oldHash.equals(cpfHash) && enderecoRepo.existsByCpfHash(cpfHash)) {
                ra.addFlashAttribute("erro", "Não foi possível salvar o endereço. Verifique os dados e tente novamente.");
                return "redirect:/minha-conta/endereco";
            }
        }

        // --- Prepara objeto Endereco ---
        Endereco enderecoTemp = new Endereco();
        enderecoTemp.setUser(user);
        enderecoTemp.setEndereco(logradouroCompleto);
        enderecoTemp.setCep(validacaoService.formatarCep(cep));
        enderecoTemp.setNumero(numero.trim());
        enderecoTemp.setComplemento(complemento != null ? complemento.trim() : null);
        enderecoTemp.setCidade(cidade);
        enderecoTemp.setEstado(estado);
        enderecoTemp.setCpfHash(cpfHash);
        enderecoTemp.setTelefone(telefone);
        enderecoTemp.setCpfSufixo(validacaoService.extrairSufixo(cpfLimpo));
        enderecoTemp.setHorarioEntrega(horarioEntrega);

        // --- Decisão: se é edição (exige 2FA) ou primeiro cadastro (salva direto) ---
        boolean isEditing = (enderecoExistente != null);

        if (isEditing) {
            // Edição: salva temporariamente na sessão e envia 2FA
            session.setAttribute("endereco_temp", enderecoTemp);
            session.setAttribute("2fa_endereco_contexto", "endereco");
            session.setAttribute("2fa_user_id", user.getId());

            boolean enviou = tfaService.gerarEEnviar(user, "Confirmação de edição de endereço");
            if (!enviou) {
                ra.addFlashAttribute("erro", "Muitas tentativas. Aguarde antes de solicitar novamente.");
                return "redirect:/minha-conta/endereco";
            }
            ra.addFlashAttribute("info", "Código de verificação enviado para " + mascararEmail(user.getEmail()));
            return "redirect:/minha-conta/endereco/verificar";
        } else {
            // Primeiro cadastro: salva imediatamente, sem 2FA
            enderecoRepo.save(enderecoTemp);
            auditService.log(user, TipoAcao.ATUALIZOU_ENDERECO,
                "Endereço cadastrado", req.getRemoteAddr());
            ra.addFlashAttribute("sucesso", "Endereço salvo com sucesso!");
            return "redirect:/minha-conta";
        }
    }

    // ─── GET /minha-conta/endereco/verificar (página de 2FA) ─────────
    @GetMapping("/verificar")
    public String paginaVerificar(HttpSession session, Model model,
                                  @AuthenticationPrincipal UserDetails ud) {
        String contexto = (String) session.getAttribute("2fa_endereco_contexto");
        if (contexto == null) return "redirect:/minha-conta/endereco";
        User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();
        model.addAttribute("email", mascararEmail(user.getEmail()));
        model.addAttribute("contexto", "endereco");
        return "verificar_2fa_endereco";
    }

    // ─── POST /minha-conta/endereco/verificar (confirmar 2FA e salvar) ───
    @PostMapping("/verificar")
    public String confirmar2FA(@RequestParam String codigo,
                               HttpSession session,
                               HttpServletRequest req,
                               @AuthenticationPrincipal UserDetails ud,
                               RedirectAttributes ra) {
        String contexto = (String) session.getAttribute("2fa_endereco_contexto");
        Long userId = (Long) session.getAttribute("2fa_user_id");
        if (contexto == null || userId == null) return "redirect:/minha-conta/endereco";

        User user = userRepo.findById(userId).orElse(null);
        if (user == null) return "redirect:/minha-conta/endereco";

        String resultado = tfaService.verificar(user, codigo);

        if ("ok".equals(resultado)) {
            Endereco enderecoTemp = (Endereco) session.getAttribute("endereco_temp");
            if (enderecoTemp == null) {
                ra.addFlashAttribute("erro", "Sessão expirada. Tente novamente.");
                return "redirect:/minha-conta/endereco";
            }

            // Atualiza o endereço existente
            Endereco existente = enderecoRepo.findByUser(user).orElseThrow();
            existente.setEndereco(enderecoTemp.getEndereco());
            existente.setCep(enderecoTemp.getCep());
            existente.setNumero(enderecoTemp.getNumero());
            existente.setComplemento(enderecoTemp.getComplemento());
            existente.setCidade(enderecoTemp.getCidade());
            existente.setEstado(enderecoTemp.getEstado());
            existente.setCpfHash(enderecoTemp.getCpfHash());
            existente.setCpfSufixo(enderecoTemp.getCpfSufixo());
            existente.setHorarioEntrega(enderecoTemp.getHorarioEntrega());
            enderecoRepo.save(existente);

            auditService.log(user, TipoAcao.ATUALIZOU_ENDERECO,
                "Endereço atualizado", req.getRemoteAddr());

            session.removeAttribute("endereco_temp");
            session.removeAttribute("2fa_endereco_contexto");
            session.removeAttribute("2fa_user_id");
            ra.addFlashAttribute("sucesso", "Endereço atualizado com sucesso!");
            return "redirect:/minha-conta";

        } else if ("bloqueado".equals(resultado)) {
            ra.addFlashAttribute("erro", "Muitas tentativas incorretas. Gere um novo código.");
        } else if ("expirado".equals(resultado)) {
            ra.addFlashAttribute("erro", "Código expirado. Solicite novamente.");
            session.removeAttribute("2fa_endereco_contexto");
            session.removeAttribute("2fa_user_id");
        } else {
            int restantes = 5 - user.getTentativas2fa();
            ra.addFlashAttribute("erro", "Código inválido. " + restantes + " tentativa(s) restante(s).");
        }
        return "redirect:/minha-conta/endereco/verificar";
    }

    // ─── POST reenviar código (endereço) ────────────────────────────
    @PostMapping("/verificar/reenviar")
    public String reenviar(HttpSession session,
                           @AuthenticationPrincipal UserDetails ud,
                           RedirectAttributes ra) {
        String contexto = (String) session.getAttribute("2fa_endereco_contexto");
        if (contexto == null) return "redirect:/minha-conta/endereco";
        User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();
        boolean enviou = tfaService.gerarEEnviar(user, "Reenvio — Endereço");
        if (enviou) ra.addFlashAttribute("info", "Novo código enviado.");
        else ra.addFlashAttribute("erro", "Aguarde antes de solicitar novo código.");
        return "redirect:/minha-conta/endereco/verificar";
    }

    // ─── Helper ─────────────────────────────────────────────────────
    private String mascararEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 2) return email;
        return email.substring(0, 2) + "***" + email.substring(at);
    }
}