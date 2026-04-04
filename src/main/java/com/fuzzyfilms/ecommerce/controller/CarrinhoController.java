package com.fuzzyfilms.ecommerce.controller;

import com.fuzzyfilms.ecommerce.model.Produto;
import com.fuzzyfilms.ecommerce.repository.ProdutoRepository;
import com.fuzzyfilms.ecommerce.service.CarrinhoService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Controller
@RequestMapping("/carrinho")
public class CarrinhoController {

    @Autowired private ProdutoRepository produtoRepo;
    @Autowired private CarrinhoService carrinhoService; // ← injetado

    @GetMapping
    public String verCarrinho(HttpSession session, Model model) {
        // Usa o service para obter o carrinho
        List<CarrinhoItem> itens = carrinhoService.obterCarrinho(session);
        model.addAttribute("itens", itens);
        double total = itens.stream().mapToDouble(i -> i.getProduto().getPreco().doubleValue() * i.getQuantidade()).sum();
        model.addAttribute("total", total);
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