package com.fuzzyfilms.ecommerce.controller;

import com.fuzzyfilms.ecommerce.model.*;
import com.fuzzyfilms.ecommerce.model.AuditLog.TipoAcao;
import com.fuzzyfilms.ecommerce.model.Pedido.StatusPedido;
import com.fuzzyfilms.ecommerce.repository.*;
import com.fuzzyfilms.ecommerce.service.AuditService;
import com.fuzzyfilms.ecommerce.service.CarrinhoService;
import com.fuzzyfilms.ecommerce.service.EmailService;



import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/pedidos")
public class PedidoController {

    @Autowired private PedidoRepository   pedidoRepo;
    @Autowired private ProdutoRepository  produtoRepo;
    @Autowired private UserRepository     userRepo;
    @Autowired private EnderecoRepository enderecoRepo;
    @Autowired private AuditService       auditService;
    @Autowired private EmailService emailService;
    @Autowired private PedidoItemRepository pedidoItemRepo;
    @Autowired private CarrinhoService carrinhoService;

    // ─── Meus pedidos ────────────────────────────────────────────────
    @GetMapping
public String meusPedidos(@RequestParam(defaultValue = "0") int page,
                          @RequestParam(defaultValue = "10") int size,
                          @AuthenticationPrincipal UserDetails ud,
                          Model model) {
    User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();
    Pageable pageable = PageRequest.of(page, size);
    Page<Pedido> pagina = pedidoRepo.findByCompradorOrderByCriadoEmDesc(user, pageable);
    
    model.addAttribute("pedidos", pagina.getContent());
    model.addAttribute("paginaAtual", page);
    model.addAttribute("totalPaginas", pagina.getTotalPages());
    model.addAttribute("usuario", user);
    model.addAttribute("usuario_nome", user.getNome());
    model.addAttribute("statusLabels", statusLabels());
    return "pedidos";
}

    // ─── Detalhe / rastreio ──────────────────────────────────────────
    @GetMapping("/{id}")
    public String detalhe(@PathVariable Long id,
                          @AuthenticationPrincipal UserDetails ud,
                          Model model, RedirectAttributes ra) {
        User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();
        Pedido pedido = pedidoRepo.findById(id).orElse(null);

        if (pedido == null ||
            (!pedido.getComprador().getId().equals(user.getId()) &&
             user.getCargo() == Cargo.CLIENTE)) {
            ra.addFlashAttribute("erro", "Pedido não encontrado.");
            return "redirect:/pedidos";
        }

        // Busca endereço do comprador
        Endereco enderecoCliente = enderecoRepo.findByUser(pedido.getComprador()).orElse(null);
        model.addAttribute("enderecoCliente", enderecoCliente);

        // Lista de status em ordem (para a timeline)
        List<Pedido.StatusPedido> statusOrdem = List.of(
            Pedido.StatusPedido.RECEBIDO,
            Pedido.StatusPedido.COLETADO,
            Pedido.StatusPedido.NO_ARMAZEM,
            Pedido.StatusPedido.SAIU_DO_ARMAZEM,
            Pedido.StatusPedido.EM_TRANSITO,
            Pedido.StatusPedido.EM_ROTA,
            Pedido.StatusPedido.ENTREGUE
        );
        int currentIndex = statusOrdem.indexOf(pedido.getStatus());

        model.addAttribute("pedido", pedido);
        model.addAttribute("usuario", user);
        model.addAttribute("usuario_nome", user.getNome());
        model.addAttribute("statusOrdem", statusOrdem);
        model.addAttribute("currentIndex", currentIndex);
        model.addAttribute("statusLabels", statusLabels());
        model.addAttribute("statusIcons", statusIcons());
        model.addAttribute("isAdminOuGerente",
            user.getCargo() == Cargo.ADMINISTRADOR || user.getCargo() == Cargo.GERENTE);
        model.addAttribute("disponivel", pedido.getStatus() == Pedido.StatusPedido.NO_ARMAZEM ||
                                          pedido.getStatus() == Pedido.StatusPedido.COLETADO);
        model.addAttribute("previsaoEntrega", calcularPrevisao(pedido));

        return "pedido_rastreio";
    }

    // ─── Comprar produto ─────────────────────────────────────────────
 @PostMapping("/comprar/{produtoId}")
public String comprarAgora(@PathVariable Long produtoId,
                           @RequestParam(defaultValue = "1") int quantidade,
                           @AuthenticationPrincipal UserDetails ud,
                           HttpSession session,
                           RedirectAttributes ra) {
    Produto produto = produtoRepo.findById(produtoId).orElse(null);
    if (produto == null || produto.getEstoque() < quantidade) {
        ra.addFlashAttribute("erro", "Produto indisponível.");
        return "redirect:/produto/" + produtoId;
    }
    // Limpa o carrinho anterior para compra única
    carrinhoService.limparCarrinho(session);
    // Adiciona o produto ao carrinho
    carrinhoService.adicionarItem(session, produto, quantidade);
    return "redirect:/checkout";
}
// ─── Atualizar rota (Admin/Gerente) ─────────────────────────────
   @PostMapping("/{id}/rota")
public String atualizarRota(@PathVariable Long id,
                            @RequestParam(required = false) String codigoRastreio,
                            @RequestParam(required = false) String transportadora,
                            @RequestParam(required = false) LocalDate previsaoRota,
                            @AuthenticationPrincipal UserDetails ud,
                            HttpServletRequest req,
                            RedirectAttributes ra) {
    User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();
    if (user.getCargo() == Cargo.CLIENTE) return "redirect:/pedidos";

    Pedido pedido = pedidoRepo.findById(id).orElse(null);
    if (pedido == null) {
        ra.addFlashAttribute("erro", "Pedido não encontrado.");
        return "redirect:/pedidos";
    }

    pedido.setCodigoRastreio(codigoRastreio);
    pedido.setTransportadora(transportadora);
    pedido.setPrevisaoEntregaRota(previsaoRota);
    pedidoRepo.save(pedido);

    auditService.log(user, TipoAcao.PEDIDO_STATUS_ALTERADO,
        "Rota atualizada: código " + codigoRastreio + " - " + transportadora, req.getRemoteAddr());

    // 🔔 NOTIFICAÇÃO PARA O CLIENTE
    StringBuilder mensagem = new StringBuilder();
    mensagem.append("Olá, ").append(pedido.getComprador().getNome()).append("!\n\n");
    mensagem.append("As informações de entrega do seu pedido #").append(pedido.getId()).append(" foram atualizadas:\n");

    if (codigoRastreio != null && !codigoRastreio.isBlank())
        mensagem.append("📬 Código de rastreio: ").append(codigoRastreio).append("\n");
    if (transportadora != null && !transportadora.isBlank())
        mensagem.append("🚚 Transportadora: ").append(transportadora).append("\n");
    if (previsaoRota != null) {
        String previsaoStr = previsaoRota.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        mensagem.append("📅 Previsão de entrega: ").append(previsaoStr).append("\n");
    }
    mensagem.append("\nAcompanhe seu pedido em: https://miniloja.com/pedidos/").append(pedido.getId());
    mensagem.append("\n\nAtenciosamente,\nEquipe FUZZI FILMS");

    emailService.enviar(pedido.getComprador().getEmail(),
        "🚚 Atualização na entrega do pedido #" + pedido.getId(),
        mensagem.toString());

    ra.addFlashAttribute("sucesso", "Informações de rota atualizadas! Cliente notificado por e-mail.");
    return "redirect:/pedidos/" + id;
}

    // ─── Atualizar status (Admin/Gerente) ────────────────────────────
   @PostMapping("/{id}/status")
public String atualizarStatus(@PathVariable Long id,
                              @RequestParam StatusPedido status,
                              @RequestParam(required = false) String observacao,
                              @AuthenticationPrincipal UserDetails ud,
                              HttpServletRequest req,
                              RedirectAttributes ra) {
    User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();
    if (user.getCargo() == Cargo.CLIENTE) return "redirect:/pedidos";

    Pedido pedido = pedidoRepo.findById(id).orElse(null);
    if (pedido == null) return "redirect:/painel/pedidos";

    StatusPedido anterior = pedido.getStatus();
    pedido.setStatus(status);
    pedido.setAtualizadoEm(LocalDateTime.now());
    if (observacao != null && !observacao.isBlank())
        pedido.setObservacao(observacao.trim());
    pedidoRepo.save(pedido);

    auditService.log(user, TipoAcao.PEDIDO_STATUS_ALTERADO,
        "Pedido #" + id + ": " + anterior + " → " + status, req.getRemoteAddr());

    // 🔔 NOTIFICAÇÃO PARA O CLIENTE
    String assunto = "📦 Status do seu pedido #" + pedido.getId() + " foi atualizado";
    String mensagem = String.format(
        "Olá, %s!\n\n" +
        "O status do seu pedido #%d foi alterado para: %s.\n" +
        "Detalhes: %s\n\n" +
        "Acompanhe seu pedido em: https://miniloja.com/pedidos/%d\n\n" +
        "Atenciosamente,\nEquipe FUZZI FILMS",
        pedido.getComprador().getNome(),
        pedido.getId(),
        labelStatus(status),
        (observacao != null && !observacao.isBlank() ? observacao : "Nenhuma observação adicional."),
        pedido.getId()
    );
    emailService.enviar(pedido.getComprador().getEmail(), assunto, mensagem);

    ra.addFlashAttribute("sucesso", "Status atualizado para: " + labelStatus(status) + " - Cliente notificado por e-mail.");
    return "redirect:/pedidos/" + id;
}
    // ─── Helpers ─────────────────────────────────────────────────────
    private String calcularPrevisao(Pedido pedido) {
        if (pedido.getStatus() == StatusPedido.ENTREGUE) return "Entregue";
        if (pedido.getStatus() == StatusPedido.CANCELADO ||
            pedido.getStatus() == StatusPedido.REEMBOLSADO) return "—";

        int diasEstimados = switch (pedido.getStatus()) {
            case RECEBIDO        -> 7;
            case COLETADO        -> 6;
            case NO_ARMAZEM      -> 5;
            case SAIU_DO_ARMAZEM -> 3;
            case EM_TRANSITO     -> 2;
            case EM_ROTA         -> 1;
            default              -> 0;
        };
        if (diasEstimados == 0) return "Hoje";

        LocalDate previsao = LocalDate.now().plusDays(diasEstimados);
        var fmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String horario = enderecoRepo.findByUser(pedido.getComprador())
            .map(e -> " (entre " + e.getHorarioEntrega() + ")")
            .orElse("");
        return "Estimativa: " + previsao.format(fmt) + horario +
               " *Sujeito a variações — estimativa sem garantia de prazo fixo.";
    }

    private Map<String, String> statusLabels() {
    Map<String, String> m = new LinkedHashMap<>();
    m.put("RECEBIDO", "Pedido recebido");
    m.put("COLETADO", "Pedido coletado");
    m.put("NO_ARMAZEM", "No armazém");
    m.put("SAIU_DO_ARMAZEM", "Saiu do armazém");
    m.put("EM_TRANSITO", "Em trânsito");
    m.put("EM_ROTA", "Em rota de entrega");
    m.put("ENTREGUE", "Entregue");
    m.put("CANCELADO", "Cancelado");
    m.put("REEMBOLSADO", "Reembolsado");
    m.put("DESTINATARIO_AUSENTE", "Destinatário ausente");
    m.put("ENDERECO_NAO_LOCALIZADO", "Endereço não localizado");
    return m;
}

private Map<String, String> statusIcons() {
    Map<String, String> m = new LinkedHashMap<>();
    m.put("RECEBIDO", "📋");
    m.put("COLETADO", "📦");
    m.put("NO_ARMAZEM", "🏭");
    m.put("SAIU_DO_ARMAZEM", "🚚");
    m.put("EM_TRANSITO", "🛣️");
    m.put("EM_ROTA", "🏠");
    m.put("ENTREGUE", "✅");
    m.put("CANCELADO", "❌");
    m.put("REEMBOLSADO", "💰");
    return m;
}

    private String labelStatus(StatusPedido s) {
        return statusLabels().getOrDefault(s, s.name());
    }
}