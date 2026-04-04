package com.fuzzyfilms.ecommerce.controller;

import com.fuzzyfilms.ecommerce.model.*;
import com.fuzzyfilms.ecommerce.repository.*;
import com.fuzzyfilms.ecommerce.service.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Controller
public class CheckoutController {

    @Autowired private UserRepository userRepo;
    @Autowired private EnderecoRepository enderecoRepo;
    @Autowired private CarrinhoService carrinhoService;
    @Autowired private FreteService freteService;
    @Autowired private MercadoPagoService mercadoPagoService;
    @Autowired private PedidoRepository pedidoRepo;
    @Autowired private PedidoItemRepository pedidoItemRepo;   // ← ADICIONADO

    @GetMapping("/checkout")
    public String checkout(HttpSession session,
                           @AuthenticationPrincipal UserDetails ud,
                           Model model,
                           RedirectAttributes ra) {
        User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();

        Endereco endereco = enderecoRepo.findByUser(user).orElse(null);
        if (endereco == null || endereco.getCpfHash() == null) {
            ra.addFlashAttribute("erro", "Você precisa cadastrar um endereço e CPF antes de finalizar a compra.");
            return "redirect:/minha-conta/endereco";
        }

        var itens = carrinhoService.obterCarrinho(session);
        if (itens.isEmpty()) {
            ra.addFlashAttribute("erro", "Seu carrinho está vazio.");
            return "redirect:/carrinho";
        }

        BigDecimal subtotal = itens.stream()
                .map(item -> item.getProduto().getPreco().multiply(BigDecimal.valueOf(item.getQuantidade())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal frete = freteService.calcularFrete(endereco.getCep());
        BigDecimal total = subtotal.add(frete);

        model.addAttribute("itens", itens);
        model.addAttribute("subtotal", subtotal);
        model.addAttribute("frete", frete);
        model.addAttribute("total", total);
        model.addAttribute("endereco", endereco);
        return "checkout";
    }

    @GetMapping("/pagamento/pix/{pedidoId}")
public String paginaPix(@PathVariable Long pedidoId, Model model, RedirectAttributes ra) {
    Pedido pedido = pedidoRepo.findById(pedidoId).orElse(null);
    if (pedido == null || pedido.getQrCode() == null) {
        ra.addFlashAttribute("erro", "QR Code não encontrado.");
        return "redirect:/pedidos";
    }
    model.addAttribute("qrCode", pedido.getQrCode());
    model.addAttribute("pedido", pedido);
    return "pix_pagamento";
}

    @PostMapping("/checkout/pagar")
    public String processarPagamento(@RequestParam String metodo,
                                     HttpSession session,
                                     @AuthenticationPrincipal UserDetails ud,
                                     RedirectAttributes ra) throws Exception {
        User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();
        Endereco endereco = enderecoRepo.findByUser(user).orElse(null);
        if (endereco == null || endereco.getCpfHash() == null) {
            ra.addFlashAttribute("erro", "Endereço/CPF obrigatórios.");
            return "redirect:/minha-conta/endereco";
        }

        var itens = carrinhoService.obterCarrinho(session);
        if (itens.isEmpty()) {
            ra.addFlashAttribute("erro", "Carrinho vazio.");
            return "redirect:/carrinho";
        }

        // Recalcular totais
        BigDecimal subtotal = itens.stream()
                .map(item -> item.getProduto().getPreco().multiply(BigDecimal.valueOf(item.getQuantidade())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal frete = freteService.calcularFrete(endereco.getCep());
        BigDecimal total = subtotal.add(frete);

        // 1. Criar pedido (sem produto/quantidade diretos)
        Pedido pedido = new Pedido();
        pedido.setComprador(user);
        pedido.setValorTotal(total);
        pedido.setStatus(Pedido.StatusPedido.AGUARDANDO_PAGAMENTO);
        pedido.setCriadoEm(LocalDateTime.now());
        pedido.setValorFrete(frete);
        pedido.setEnderecoEntrega(endereco.getEndereco() + ", " + endereco.getNumero() + " - " + endereco.getCidade() + "/" + endereco.getEstado());
        pedidoRepo.save(pedido); // salva para gerar o ID

        // 2. Criar os itens do pedido
        for (CarrinhoController.CarrinhoItem item : itens) {
            PedidoItem pedidoItem = new PedidoItem();
            pedidoItem.setPedido(pedido);
            pedidoItem.setProduto(item.getProduto());
            pedidoItem.setQuantidade(item.getQuantidade());
            pedidoItem.setPrecoUnitario(item.getProduto().getPreco());
            pedidoItemRepo.save(pedidoItem);
        }

        String externalId = "PED_" + pedido.getId();

        if ("PIX".equalsIgnoreCase(metodo)) {
            String qrCode = mercadoPagoService.gerarQrCodePix(externalId, total, "Pedido FUZZI FILMS");
            pedido.setQrCode(qrCode);
            pedidoRepo.save(pedido);
            carrinhoService.limparCarrinho(session);
            return "redirect:/pagamento/pix/" + pedido.getId();
        } else if ("CREDITO".equalsIgnoreCase(metodo) || "MERCADO_PAGO".equalsIgnoreCase(metodo)) {
           String preferenceId = mercadoPagoService.criarPreferencia("Pedido FUZZY FILMS", total, externalId);
            pedido.setPreferenceId(preferenceId);
            pedidoRepo.save(pedido);
            carrinhoService.limparCarrinho(session);
            return "redirect:https://www.mercadopago.com.br/checkout/v1/redirect?pref_id=" + preferenceId;
        } else {
            ra.addFlashAttribute("erro", "Método de pagamento inválido.");
            return "redirect:/checkout";
        }
    }
}