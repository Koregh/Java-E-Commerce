package com.fuzzyfilms.ecommerce.controller;

import com.fuzzyfilms.ecommerce.model.FreteConfig;
import com.fuzzyfilms.ecommerce.model.Produto;
import com.fuzzyfilms.ecommerce.repository.FreteConfigRepository;
import com.fuzzyfilms.ecommerce.repository.ProdutoRepository;
import com.fuzzyfilms.ecommerce.service.CarrinhoService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;

@Controller
@RequestMapping("/carrinho")
public class CarrinhoController {

    @Autowired private ProdutoRepository produtoRepo;
    @Autowired private CarrinhoService carrinhoService; // ← injetado

    @Autowired private FreteConfigRepository freteConfigRepo;

@GetMapping
public String verCarrinho(HttpSession session, Model model) {
    List<CarrinhoItem> itens = carrinhoService.obterCarrinho(session);
    model.addAttribute("itens", itens);
    double subtotal = itens.stream()
            .mapToDouble(i -> i.getProduto().getPreco().doubleValue() * i.getQuantidade())
            .sum();

    // Carrega configuração global
    FreteConfig global = freteConfigRepo.findById(1L).orElse(new FreteConfig());

    // Calcula frete total baseado nos produtos
    double totalFrete = 0.0;
    for (CarrinhoItem item : itens) {
        Produto p = item.getProduto();
        if (p.getTipoFrete() != null) {
            if (p.getTipoFrete() == Produto.TipoFreteP.FIXO && p.getValorFrete() != null && p.getValorFrete().doubleValue() > 0) {
                totalFrete += p.getValorFrete().doubleValue(); // uma vez por produto
            }
            // se for GRATIS, não soma nada
        } else {
            // fallback para global
            if (global.getTipoFrete() == FreteConfig.TipoFrete.FIXO && global.getValorFixo() != null) {
                totalFrete += global.getValorFixo().doubleValue();
            }
        }
    }

    model.addAttribute("subtotal", subtotal);
    model.addAttribute("frete", totalFrete);
    model.addAttribute("total", subtotal + totalFrete);
    model.addAttribute("freteConfig", global); // ainda usado para fallback se algum produto não tiver frete
    return "carrinho";
}

    @PostMapping("/adicionar/{id}")
    public String adicionar(@PathVariable Long id, @RequestParam(defaultValue = "1") int quantidade, HttpSession session) {
        Produto produto = produtoRepo.findById(id).orElseThrow();
        carrinhoService.adicionarItem(session, produto, quantidade);
        return "redirect:/carrinho";
    }

    @PostMapping("/remover/{id}")
    public String remover(@PathVariable Long id, HttpSession session) {
        List<CarrinhoItem> itens = carrinhoService.obterCarrinho(session);
        itens.removeIf(i -> i.getProduto().getId().equals(id));
        session.setAttribute("carrinho", itens);
        return "redirect:/carrinho";
    }

    @PostMapping("/atualizar")
public String atualizarQuantidade(@RequestParam Long produtoId,
                                  @RequestParam int quantidade,
                                  HttpSession session,
                                  RedirectAttributes ra) {
    List<CarrinhoItem> itens = carrinhoService.obterCarrinho(session);
    for (CarrinhoItem item : itens) {
        if (item.getProduto().getId().equals(produtoId)) {
            if (quantidade <= 0) {
                itens.remove(item);
            } else {
                item.setQuantidade(quantidade);
            }
            break;
        }
    }
    session.setAttribute("carrinho", itens);
    ra.addFlashAttribute("sucesso", "Quantidade atualizada.");
    return "redirect:/carrinho";
}

    // Classe interna para item do carrinho (pode ser movida para fora se preferir)
    public static class CarrinhoItem {
        private Produto produto;
        private int quantidade;
        public CarrinhoItem(Produto produto, int quantidade) { this.produto = produto; this.quantidade = quantidade; }
        public Produto getProduto() { return produto; }
        public int getQuantidade() { return quantidade; }
        public void setQuantidade(int quantidade) { this.quantidade = quantidade; }
    }
}