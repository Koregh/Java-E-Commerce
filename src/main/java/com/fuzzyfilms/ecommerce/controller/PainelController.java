package com.fuzzyfilms.ecommerce.controller;

import com.fuzzyfilms.ecommerce.model.*;
import com.fuzzyfilms.ecommerce.model.AuditLog.TipoAcao;
import com.fuzzyfilms.ecommerce.model.Pedido.StatusPedido;
import com.fuzzyfilms.ecommerce.repository.*;
import com.fuzzyfilms.ecommerce.service.AuditService;
import com.fuzzyfilms.ecommerce.service.TwoFactorService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/painel")
public class PainelController {

    @Autowired private AuditLogRepository auditRepo;
    @Autowired private UserRepository userRepo;
    @Autowired private PedidoRepository pedidoRepo;
    @Autowired private AuditService auditService;
    @Autowired private TwoFactorService tfaService;

    // ─── Painel principal — todos os logs ────────────────────────────
@GetMapping
public String painel(@RequestParam(required = false) AuditLog.TipoAcao tipo,
                     @RequestParam(defaultValue = "0") int page,
                     @RequestParam(defaultValue = "25") int size,
                     Model model,
                     @AuthenticationPrincipal UserDetails ud) {
    Pageable pageable = PageRequest.of(page, size);
    Page<AuditLog> pagina;
    if (tipo != null) {
        pagina = auditRepo.findByTipoAcaoInOrderByCriadoEmDesc(List.of(tipo), pageable);
    } else {
        pagina = auditRepo.findAllByOrderByCriadoEmDesc(pageable);
    }
    model.addAttribute("logs", pagina.getContent());
    model.addAttribute("paginaAtual", page);
    model.addAttribute("totalPaginas", pagina.getTotalPages());
    model.addAttribute("tipoSelecionado", tipo);
    addUsuario(model, ud);
    return "painel/logs";
}
    // ─── Aba: mudanças de conta ──────────────────────────────────────
    @GetMapping("/conta")
    public String logsConta(Model model, @AuthenticationPrincipal UserDetails ud) {
        var tipos = List.of(TipoAcao.MUDOU_EMAIL, TipoAcao.MUDOU_SENHA,
                            TipoAcao.EXCLUIU_CONTA, TipoAcao.CADASTRO, TipoAcao.EXPORTOU_DADOS);
        model.addAttribute("logs", auditRepo.findByTipoAcaoInOrderByCriadoEmDesc(tipos));
        model.addAttribute("aba", "conta");
        addUsuario(model, ud);
        return "painel/logs";
    }

    // ─── Aba: produtos ───────────────────────────────────────────────
    @GetMapping("/produtos")
    public String logsProdutos(Model model, @AuthenticationPrincipal UserDetails ud) {
        var tipos = List.of(TipoAcao.PRODUTO_CRIADO, TipoAcao.PRODUTO_EDITADO,
                            TipoAcao.PRODUTO_DELETADO, TipoAcao.PRODUTO_STATUS_ALTERADO);
        model.addAttribute("logs", auditRepo.findByTipoAcaoInOrderByCriadoEmDesc(tipos));
        model.addAttribute("aba", "produtos");
        addUsuario(model, ud);
        return "painel/logs";
    }

    // ─── Lista de pedidos (para admin/gerente) ───────────────────────
   @GetMapping("/pedidos")
public String gerenciarPedidos(@RequestParam(required = false) StatusPedido status,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "25") int size,
                               Model model,
                               @AuthenticationPrincipal UserDetails ud) {
    User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();
    if (user.getCargo() != Cargo.ADMINISTRADOR && user.getCargo() != Cargo.GERENTE) {
        return "redirect:/painel";
    }
    
    Pageable pageable = PageRequest.of(page, size);
    Page<Pedido> pagina;
    if (status != null) {
        pagina = pedidoRepo.findByStatusOrderByCriadoEmDesc(status, pageable);
    } else {
        pagina = pedidoRepo.findAllByOrderByCriadoEmDesc(pageable);
    }
    
    model.addAttribute("pedidos", pagina.getContent());
    model.addAttribute("paginaAtual", page);
    model.addAttribute("totalPaginas", pagina.getTotalPages());
    model.addAttribute("totalElementos", pagina.getTotalElements());
    model.addAttribute("statusLabels", statusLabels());
    addUsuario(model, ud);
    return "painel/pedidos";
}

    // ─── Aba: pedidos (histórico de status) ──────────────────────────
    @GetMapping("/pedidosLogs")
    public String logsPedidos(Model model, @AuthenticationPrincipal UserDetails ud) {
        var tipos = List.of(TipoAcao.PEDIDO_STATUS_ALTERADO);
        model.addAttribute("logs", auditRepo.findByTipoAcaoInOrderByCriadoEmDesc(tipos));
        model.addAttribute("aba", "pedidos");
        addUsuario(model, ud);
        return "painel/logs";
    }

    // ─── Aba: vendas (produtos comprados) ────────────────────────────
    @GetMapping("/vendas")
    public String logsVendas(Model model, @AuthenticationPrincipal UserDetails ud) {
        var tipos = List.of(TipoAcao.PEDIDO_CRIADO);
        model.addAttribute("logs", auditRepo.findByTipoAcaoInOrderByCriadoEmDesc(tipos));
        model.addAttribute("aba", "vendas");
        addUsuario(model, ud);
        return "painel/logs";
    }

    // ─── Aba: acessos ────────────────────────────────────────────────
    @GetMapping("/acessos")
    public String logsAcessos(Model model, @AuthenticationPrincipal UserDetails ud) {
        var tipos = List.of(TipoAcao.LOGIN, TipoAcao.LOGOUT);
        model.addAttribute("logs", auditRepo.findByTipoAcaoInOrderByCriadoEmDesc(tipos));
        model.addAttribute("aba", "acessos");
        addUsuario(model, ud);
        return "painel/logs";
    }

    // ─── Aba: segurança ──────────────────────────────────────────────
    @GetMapping("/seguranca")
    public String logsSeguranca(Model model, @AuthenticationPrincipal UserDetails ud) {
        var tipos = List.of(TipoAcao.USUARIO_BLOQUEADO, TipoAcao.TENTATIVA_2FA_FALHA,
                            TipoAcao.ACESSO_NEGADO);
        model.addAttribute("logs", auditRepo.findByTipoAcaoInOrderByCriadoEmDesc(tipos));
        model.addAttribute("aba", "seguranca");
        addUsuario(model, ud);
        return "painel/logs";
    }

    // ─── Gerenciar usuários (Gerente exclusivo) ──────────────────────
   @GetMapping("/usuarios")
public String usuarios(@RequestParam(defaultValue = "0") int page,
                       Model model,
                       @AuthenticationPrincipal UserDetails ud) {
    User me = userRepo.findByEmail(ud.getUsername()).orElseThrow();
    if (me.getCargo() != Cargo.GERENTE) return "redirect:/painel";

    Pageable pageable = PageRequest.of(page, 25); // 25 por página
    Page<User> pagina = userRepo.findAllByOrderByNomeAsc(pageable);

    model.addAttribute("usuarios", pagina.getContent());   // lista da página atual
    model.addAttribute("paginaAtual", page);
    model.addAttribute("totalPaginas", pagina.getTotalPages());
    model.addAttribute("cargos", Cargo.values());
    addUsuario(model, ud);
    return "painel/usuarios";
}

    // ═══════════════════════════════════════════════════════════════════
    //   FLUXO DE ALTERAÇÃO DE CARGO COM 2FA (verificação de e-mail + código)
    // ═══════════════════════════════════════════════════════════════════

    // ─── 1ª etapa: solicitar alteração (verifica e-mail e envia 2FA) ───
    @PostMapping("/usuarios/{id}/cargo/solicitar")
    public String solicitarAlteracaoCargo(@PathVariable Long id,
                                          @RequestParam Cargo cargo,
                                          @RequestParam String emailConfirmacao,
                                          @AuthenticationPrincipal UserDetails ud,
                                          HttpSession session,
                                          RedirectAttributes ra) {
        User gerente = userRepo.findByEmail(ud.getUsername()).orElseThrow();
        if (gerente.getCargo() != Cargo.GERENTE) return "redirect:/painel";

        // Verifica se o e-mail digitado corresponde ao e-mail do gerente logado
        if (!gerente.getEmail().equalsIgnoreCase(emailConfirmacao.trim())) {
            ra.addFlashAttribute("erro", "E-mail de confirmação incorreto. Digite seu próprio e-mail.");
            return "redirect:/painel/usuarios";
        }

        User alvo = userRepo.findById(id).orElse(null);
        if (alvo == null) {
            ra.addFlashAttribute("erro", "Usuário não encontrado.");
            return "redirect:/painel/usuarios";
        }
        if (alvo.getId().equals(gerente.getId())) {
            ra.addFlashAttribute("erro", "Você não pode alterar seu próprio cargo.");
            return "redirect:/painel/usuarios";
        }

        // Verifica se já foi verificado nos últimos 60 minutos (cache na sessão)
        Long lastVerified = (Long) session.getAttribute("cargo_2fa_verified_at");
        if (lastVerified != null && System.currentTimeMillis() - lastVerified < 60 * 60 * 1000) {
            // Ainda dentro do prazo, executa diretamente sem novo 2FA
            return executarAlteracaoCargo(alvo, cargo, gerente, ra);
        }

        // Salva pendência na sessão
        session.setAttribute("cargo_pendente_alvo_id", id);
        session.setAttribute("cargo_pendente_novo", cargo);
        session.setAttribute("cargo_pendente_contexto", "cargo");

        // Gera e envia código 2FA para o e-mail do gerente
        boolean enviou = tfaService.gerarEEnviar(gerente, "Confirmação de alteração de cargo");
        if (!enviou) {
            ra.addFlashAttribute("erro", "Muitas tentativas. Aguarde antes de solicitar novamente.");
            return "redirect:/painel/usuarios";
        }

        ra.addFlashAttribute("info", "Código de verificação enviado para " + mascararEmail(gerente.getEmail()));
        return "redirect:/painel/usuarios/verificar-cargo";
    }

    // ─── GET: página de verificação 2FA para cargo ─────────────────────
    @GetMapping("/usuarios/verificar-cargo")
    public String paginaVerificarCargo(HttpSession session, Model model,
                                       @AuthenticationPrincipal UserDetails ud) {
        if (session.getAttribute("cargo_pendente_alvo_id") == null) {
            return "redirect:/painel/usuarios";
        }
        User gerente = userRepo.findByEmail(ud.getUsername()).orElseThrow();
        model.addAttribute("email", mascararEmail(gerente.getEmail()));
        model.addAttribute("contexto", "cargo");
        return "verificar_2fa_cargo";
    }

    // ─── POST: confirmar código e efetivar alteração ───────────────────
    @PostMapping("/usuarios/confirmar-cargo")
    public String confirmarAlteracaoCargo(@RequestParam String codigo,
                                          HttpSession session,
                                          HttpServletRequest req,
                                          @AuthenticationPrincipal UserDetails ud,
                                          RedirectAttributes ra) {
        String contexto = (String) session.getAttribute("cargo_pendente_contexto");
        Long alvoId = (Long) session.getAttribute("cargo_pendente_alvo_id");
        Cargo novoCargo = (Cargo) session.getAttribute("cargo_pendente_novo");

        if (contexto == null || alvoId == null || novoCargo == null) {
            ra.addFlashAttribute("erro", "Sessão expirada. Solicite novamente.");
            return "redirect:/painel/usuarios";
        }

        User gerente = userRepo.findByEmail(ud.getUsername()).orElseThrow();
        User alvo = userRepo.findById(alvoId).orElse(null);
        if (alvo == null) {
            ra.addFlashAttribute("erro", "Usuário alvo não encontrado.");
            return "redirect:/painel/usuarios";
        }

        String resultado = tfaService.verificar(gerente, codigo);

        if ("ok".equals(resultado)) {
            // Limpa pendências e marca verificação bem-sucedida na sessão
            session.removeAttribute("cargo_pendente_alvo_id");
            session.removeAttribute("cargo_pendente_novo");
            session.removeAttribute("cargo_pendente_contexto");
            session.setAttribute("cargo_2fa_verified_at", System.currentTimeMillis());

            return executarAlteracaoCargo(alvo, novoCargo, gerente, ra);
        } else if ("bloqueado".equals(resultado)) {
            ra.addFlashAttribute("erro", "Muitas tentativas incorretas. Solicite um novo código.");
        } else if ("expirado".equals(resultado)) {
            ra.addFlashAttribute("erro", "Código expirado. Solicite novamente.");
        } else {
            int restantes = 5 - gerente.getTentativas2fa();
            ra.addFlashAttribute("erro", "Código inválido. " + restantes + " tentativa(s) restante(s).");
        }
        return "redirect:/painel/usuarios/verificar-cargo";
    }

    // ─── Reenviar código 2FA para cargo ────────────────────────────────
    @PostMapping("/usuarios/verificar-cargo/reenviar")
    public String reenviarCodigoCargo(HttpSession session,
                                      @AuthenticationPrincipal UserDetails ud,
                                      RedirectAttributes ra) {
        if (session.getAttribute("cargo_pendente_alvo_id") == null) {
            return "redirect:/painel/usuarios";
        }
        User gerente = userRepo.findByEmail(ud.getUsername()).orElseThrow();
        boolean enviou = tfaService.gerarEEnviar(gerente, "Reenvio - alteração de cargo");
        if (enviou) {
            ra.addFlashAttribute("info", "Novo código enviado para " + mascararEmail(gerente.getEmail()));
        } else {
            ra.addFlashAttribute("erro", "Muitas tentativas. Aguarde antes de solicitar novo código.");
        }
        return "redirect:/painel/usuarios/verificar-cargo";
    }

    // ─── Executa a alteração de cargo (usado após 2FA ou cache) ───────
    private String executarAlteracaoCargo(User alvo, Cargo novoCargo, User gerente,
                                          RedirectAttributes ra) {
        Cargo anterior = alvo.getCargo();
        alvo.setCargo(novoCargo);
        userRepo.save(alvo);

        auditService.log(gerente, TipoAcao.CARGO_ALTERADO,
            "Cargo de " + alvo.getEmail() + " alterado de " + anterior + " para " + novoCargo,
            null); // IP pode ser adicionado se necessário

        ra.addFlashAttribute("sucesso", "Cargo de " + alvo.getNome() + " atualizado para " + novoCargo.name() + ".");
        return "redirect:/painel/usuarios";
    }

    // ─── Helpers ───────────────────────────────────────────────────────
    private void addUsuario(Model model, UserDetails ud) {
        User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();
        model.addAttribute("usuario", user);
        model.addAttribute("usuario_nome", user.getNome());
        model.addAttribute("cargo", user.getCargo().name());
        model.addAttribute("isGerente", user.getCargo() == Cargo.GERENTE);
    }

    private String mascararEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 2) return email;
        return email.substring(0, 2) + "***" + email.substring(at);
    }
    


    private Map<Pedido.StatusPedido, String> statusLabels() {
        Map<Pedido.StatusPedido, String> map = new LinkedHashMap<>();
        map.put(StatusPedido.RECEBIDO, "Pedido recebido");
        map.put(StatusPedido.COLETADO, "Pedido coletado");
        map.put(StatusPedido.NO_ARMAZEM, "No armazém");
        map.put(StatusPedido.SAIU_DO_ARMAZEM, "Saiu do armazém");
        map.put(StatusPedido.EM_TRANSITO, "Em trânsito");
        map.put(StatusPedido.EM_ROTA, "Em rota de entrega");
        map.put(StatusPedido.ENTREGUE, "Entregue");
        map.put(StatusPedido.CANCELADO, "Cancelado");
        map.put(StatusPedido.REEMBOLSADO, "Reembolsado");
        map.put(StatusPedido.DESTINATARIO_AUSENTE, "Destinatário ausente");
        map.put(StatusPedido.ENDERECO_NAO_LOCALIZADO, "Endereço não localizado");
        return map;
    }
}