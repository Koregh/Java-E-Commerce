package com.fuzzyfilms.ecommerce.controller;

import com.fuzzyfilms.ecommerce.dto.ContaDTO;
import com.fuzzyfilms.ecommerce.model.AuditLog.TipoAcao;
import com.fuzzyfilms.ecommerce.model.Pedido;
import com.fuzzyfilms.ecommerce.model.Pedido.StatusPedido;
import com.fuzzyfilms.ecommerce.model.User;
import com.fuzzyfilms.ecommerce.repository.EnderecoRepository;
import com.fuzzyfilms.ecommerce.repository.PedidoRepository;
import com.fuzzyfilms.ecommerce.repository.UserRepository;
import com.fuzzyfilms.ecommerce.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Controller
@RequestMapping("/minha-conta")
public class ContaController {

    private static final Set<StatusPedido> STATUS_ATIVOS = EnumSet.of(
        StatusPedido.RECEBIDO,
        StatusPedido.COLETADO,
        StatusPedido.NO_ARMAZEM,
        StatusPedido.SAIU_DO_ARMAZEM,
        StatusPedido.EM_TRANSITO,
        StatusPedido.EM_ROTA
    );

    @Autowired private UserRepository     userRepo;
    @Autowired private PedidoRepository   pedidoRepo;
    @Autowired private EnderecoRepository enderecoRepo;
    @Autowired private PasswordEncoder    encoder;
    @Autowired private EmailService       emailService;
    @Autowired private TwoFactorService   tfaService;
    @Autowired private AuditService       auditService;

    @Autowired
private SessionRegistry sessionRegistry;

private void expireAllUserSessions(User user) {
    List<Object> principals = sessionRegistry.getAllPrincipals();
    for (Object principal : principals) {
        if (principal instanceof UserDetails && 
            ((UserDetails) principal).getUsername().equals(user.getEmail())) {
            for (SessionInformation session : sessionRegistry.getAllSessions(principal, false)) {
                session.expireNow();
            }
        }
    }
}

    // ─── GET /minha-conta ────────────────────────────────────────────
    @GetMapping
    public String pagina(@AuthenticationPrincipal UserDetails ud, Model model) {
        User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();
        
        // Usa DTO para não expor o email bruto
        ContaDTO dto = new ContaDTO(
            user.getId(),
            user.getNome(),
            mascararEmail(user.getEmail()),
            user.getCargo()
        );
        model.addAttribute("usuario", dto);
        model.addAttribute("usuario_nome", user.getNome());
        enderecoRepo.findByUser(user).ifPresent(e -> model.addAttribute("endereco", e));
        return "minha_conta";
    }

    // ─── POST /minha-conta/atualizar (nome — sem 2FA) ────────────────
    @PostMapping("/atualizar")
    public String atualizarNome(@RequestParam String nome,
                                @AuthenticationPrincipal UserDetails ud,
                                HttpServletRequest req,
                                RedirectAttributes ra) {
        User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();
        if (nome.trim().length() < 2 || nome.trim().length() > 50) {
            ra.addFlashAttribute("erro", "Nome deve ter entre 2 e 50 caracteres.");
            return "redirect:/minha-conta";
        }
        user.setNome(nome.trim());
        userRepo.save(user);
        ra.addFlashAttribute("sucesso", "Nome atualizado com sucesso!");
        return "redirect:/minha-conta";
    }

    // ════════════════════════════════════════════════════════════════
    //   MUDANÇA DE E-MAIL — com 2FA obrigatório
    // ════════════════════════════════════════════════════════════════
    @PostMapping("/email/solicitar")
    public String solicitarTrocaEmail(@RequestParam String email,
                                       @RequestParam String senha_atual,
                                       @AuthenticationPrincipal UserDetails ud,
                                       HttpSession session,
                                       RedirectAttributes ra) {
        User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();

        if (!encoder.matches(senha_atual, user.getSenha())) {
            ra.addFlashAttribute("erro", "Senha atual incorreta.");
            return "redirect:/minha-conta";
        }
        String novoEmail = email.toLowerCase().trim();
        if (novoEmail.equals(user.getEmail())) {
            ra.addFlashAttribute("erro", "O novo e-mail é igual ao atual.");
            return "redirect:/minha-conta";
        }
        if (userRepo.existsByEmail(novoEmail)) {
            ra.addFlashAttribute("erro", "Este e-mail já está em uso.");
            return "redirect:/minha-conta";
        }

        user.setEmailPendente(novoEmail);
        userRepo.save(user);
         expireAllUserSessions(user); 

        boolean enviou = tfaService.gerarEEnviar(user, "Confirmação de troca de e-mail");
        if (!enviou) {
            ra.addFlashAttribute("erro", "Muitas tentativas. Aguarde antes de solicitar novamente.");
            return "redirect:/minha-conta";
        }

        session.setAttribute("2fa_contexto", "email");
        session.setAttribute("2fa_user_id", user.getId());
        ra.addFlashAttribute("info",
            "Código enviado para " + mascararEmail(user.getEmail()) + ". Válido por 15 minutos.");
        return "redirect:/minha-conta/verificar";
    }

    // ════════════════════════════════════════════════════════════════
    //   MUDANÇA DE SENHA — com 2FA obrigatório
    // ════════════════════════════════════════════════════════════════
    @PostMapping("/senha/solicitar")
    public String solicitarTrocaSenha(@RequestParam String senha_atual,
                                       @RequestParam String nova_senha,
                                       @RequestParam String confirmar_senha,
                                       @AuthenticationPrincipal UserDetails ud,
                                       HttpSession session,
                                       RedirectAttributes ra) {
        User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();

        if (!encoder.matches(senha_atual, user.getSenha())) {
            ra.addFlashAttribute("erro", "Senha atual incorreta.");
            return "redirect:/minha-conta";
        }
        if (!nova_senha.equals(confirmar_senha)) {
            ra.addFlashAttribute("erro", "As senhas não coincidem.");
            return "redirect:/minha-conta";
        }
        if (nova_senha.length() < 8) {
            ra.addFlashAttribute("erro", "A nova senha deve ter no mínimo 8 caracteres.");
            return "redirect:/minha-conta";
        }
        if (!nova_senha.matches(".*[a-zA-Z].*") || !nova_senha.matches(".*[0-9].*")) {
            ra.addFlashAttribute("erro", "A nova senha deve conter ao menos 1 letra e 1 número.");
            return "redirect:/minha-conta";
        }

        user.setSenhaPendente(encoder.encode(nova_senha));
        userRepo.save(user);
         expireAllUserSessions(user); 

        boolean enviou = tfaService.gerarEEnviar(user, "Confirmação de troca de senha");
        if (!enviou) {
            ra.addFlashAttribute("erro", "Muitas tentativas. Aguarde antes de solicitar novamente.");
            return "redirect:/minha-conta";
        }

        session.setAttribute("2fa_contexto", "senha");
        session.setAttribute("2fa_user_id", user.getId());
        ra.addFlashAttribute("info",
            "Código enviado para " + mascararEmail(user.getEmail()) + ". Válido por 15 minutos.");
        return "redirect:/minha-conta/verificar";
    }

    // ─── GET /minha-conta/verificar ──────────────────────────────────
    @GetMapping("/verificar")
    public String paginaVerificar(HttpSession session, Model model,
                                   @AuthenticationPrincipal UserDetails ud) {
        String contexto = (String) session.getAttribute("2fa_contexto");
        if (contexto == null) return "redirect:/minha-conta";
        User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();
        model.addAttribute("email", mascararEmail(user.getEmail()));
        model.addAttribute("contexto", contexto);
        // Não passamos o objeto User inteiro, apenas o email mascarado
        return "verificar_2fa_conta";
    }

   private String obterLocalizacaoPorIP(String ip) {
    try {
        var rt = new RestTemplate();
        var url = "http://ip-api.com/json/" + ip + "?fields=status,country,regionName,city";
        var resp = rt.getForObject(url, java.util.Map.class);
        if (resp != null && "success".equals(resp.get("status"))) {
            return resp.get("city") + ", " + resp.get("regionName") + ", " + resp.get("country");
        }
    } catch (Exception e) { }
    return "Não disponível";
}

    // ─── POST /minha-conta/verificar ─────────────────────────────────
    @PostMapping("/verificar")
    public String confirmar2FA(@RequestParam String codigo,
                                HttpSession session,
                                HttpServletRequest req,
                                @AuthenticationPrincipal UserDetails ud,
                                RedirectAttributes ra) {
        String contexto = (String) session.getAttribute("2fa_contexto");
        Long userId     = (Long)   session.getAttribute("2fa_user_id");
        if (contexto == null || userId == null) return "redirect:/minha-conta";

        User user = userRepo.findById(userId).orElse(null);
        if (user == null) return "redirect:/minha-conta";

        String resultado = tfaService.verificar(user, codigo);

        switch (resultado) {
            case "ok" -> {
                session.removeAttribute("2fa_contexto");
                session.removeAttribute("2fa_user_id");

                                   String ip = req.getRemoteAddr();
String userAgent = req.getHeader("User-Agent");
String localizacao = obterLocalizacaoPorIP(ip); // implemente ou use "Não disponível"

                if ("email".equals(contexto)) {
                    String antigoEmail = user.getEmail();
                    String novoEmail   = user.getEmailPendente();
                    user.setEmail(novoEmail);
                    user.setEmailPendente(null);
                    userRepo.save(user);
                    emailService.enviarAlertaEmail(antigoEmail, novoEmail, ip, userAgent, localizacao);
                    auditService.log(user, TipoAcao.MUDOU_EMAIL,
                        "E-mail alterado de " + antigoEmail + " para " + novoEmail,
                        req.getRemoteAddr());
                    ra.addFlashAttribute("sucesso", "E-mail alterado! Faça login novamente.");
                    session.invalidate();
                    return "redirect:/login";

                } else if ("senha".equals(contexto)) {
                    user.setSenha(user.getSenhaPendente());
                    user.setSenhaPendente(null);
                    userRepo.save(user);
                    emailService.enviarAlertaSenha(user.getEmail(), ip, userAgent, localizacao);
                    auditService.log(user, TipoAcao.MUDOU_SENHA,
                        "Senha alterada", req.getRemoteAddr());
                    ra.addFlashAttribute("sucesso", "Senha alterada! Faça login novamente.");
                    session.invalidate();
                    return "redirect:/login";
                }
            }
            case "bloqueado" ->
                ra.addFlashAttribute("erro", "Muitas tentativas incorretas. Gere um novo código.");
            case "expirado" -> {
                ra.addFlashAttribute("erro", "Código expirado. Solicite novamente.");
                session.removeAttribute("2fa_contexto");
                session.removeAttribute("2fa_user_id");
                return "redirect:/minha-conta";
            }
            default -> {
                int restantes = 5 - user.getTentativas2fa();
                ra.addFlashAttribute("erro",
                    "Código inválido. " + restantes + " tentativa(s) restante(s).");
            }
        }
        return "redirect:/minha-conta/verificar";
    }

    // ─── POST reenviar código na conta ────────────────────────────────
    @PostMapping("/verificar/reenviar")
    public String reenviar(HttpSession session,
                            @AuthenticationPrincipal UserDetails ud,
                            RedirectAttributes ra) {
        String contexto = (String) session.getAttribute("2fa_contexto");
        if (contexto == null) return "redirect:/minha-conta";
        User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();
        boolean enviou = tfaService.gerarEEnviar(user, "Reenvio — " + contexto);
        if (enviou) ra.addFlashAttribute("info",  "Novo código enviado.");
        else        ra.addFlashAttribute("erro",  "Aguarde antes de solicitar novo código.");
        return "redirect:/minha-conta/verificar";
    }

    // ════════════════════════════════════════════════════════════════
    //   EXCLUIR CONTA — bloqueado se houver pedidos ativos
    // ════════════════════════════════════════════════════════════════
    @PostMapping("/deletar")
    public String deletarConta(@RequestParam String senha_confirmacao,
                                @AuthenticationPrincipal UserDetails ud,
                                HttpServletRequest req,
                                HttpSession session,
                                RedirectAttributes ra) {
        User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();

        if (!encoder.matches(senha_confirmacao, user.getSenha())) {
            ra.addFlashAttribute("erro", "Senha incorreta. Conta não excluída.");
            return "redirect:/minha-conta";
        }

        List<Pedido> pedidosAtivos = pedidoRepo.findByCompradorOrderByCriadoEmDesc(user)
            .stream()
            .filter(p -> STATUS_ATIVOS.contains(p.getStatus()))
            .toList();

        if (!pedidosAtivos.isEmpty()) {
            ra.addFlashAttribute("erro",
                "Você possui " + pedidosAtivos.size() + " pedido(s) em andamento. " +
                "Aguarde a conclusão ou cancelamento antes de excluir sua conta.");
            return "redirect:/minha-conta";
        }

        auditService.log(user, TipoAcao.EXCLUIU_CONTA,
            "Conta excluída: " + user.getEmail(), req.getRemoteAddr());

        user.setAtivo(false);
        user.setNome("[excluído]");
        user.setEmail("deleted_" + user.getId() + "@excluido.local");
        user.setSenha("[excluído]");
        userRepo.save(user);

        session.invalidate();
        ra.addFlashAttribute("sucesso", "Conta excluída com sucesso.");
        return "redirect:/login";
    }

    // ─── Exportar dados ───────────────────────────────────────────────
    @GetMapping("/exportar")
    public org.springframework.http.ResponseEntity<String> exportar(
            @AuthenticationPrincipal UserDetails ud,
            HttpServletRequest req) {
        User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();
        auditService.log(user, TipoAcao.EXPORTOU_DADOS,
            "Exportação de dados pessoais", req.getRemoteAddr());

        // Exporta apenas dados não sensíveis (email já está no JSON, mas é necessário para o usuário)
        // Se quiser mascarar o email no JSON, ajuste aqui.
        String json = "{\n" +
            "  \"id\": "         + user.getId()      + ",\n" +
            "  \"nome\": \""      + user.getNome()    + "\",\n" +
            "  \"email\": \""     + user.getEmail()   + "\",\n" +
            "  \"cargo\": \""     + user.getCargo()   + "\",\n" +
            "  \"criado_em\": \"" + user.getCriadoEm()+ "\"\n" +
            "}";

        return org.springframework.http.ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=meus-dados.json")
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .body(json);
    }

    // ─── Helpers ─────────────────────────────────────────────────────
    private String mascararEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 2) return email;
        return email.substring(0, 2) + "***" + email.substring(at);
    }
}