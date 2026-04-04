package com.fuzzyfilms.ecommerce.service;

import com.fuzzyfilms.ecommerce.controller.CarrinhoController.CarrinhoItem;
import com.fuzzyfilms.ecommerce.model.Produto;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional; // ← import adicionado

@Service
public class CarrinhoService {

    public List<CarrinhoItem> obterCarrinho(HttpSession session) {
        List<CarrinhoItem> carrinho = (List<CarrinhoItem>) session.getAttribute("carrinho");
        if (carrinho == null) {
            carrinho = new ArrayList<>();
            session.setAttribute("carrinho", carrinho);
        }
        return carrinho;
    }

    public void adicionarItem(HttpSession session, Produto produto, int quantidade) {
        List<CarrinhoItem> carrinho = obterCarrinho(session);
        Optional<CarrinhoItem> existente = carrinho.stream()
                .filter(i -> i.getProduto().getId().equals(produto.getId()))
                .findFirst();
        if (existente.isPresent()) {
            existente.get().setQuantidade(existente.get().getQuantidade() + quantidade);
        } else {
            carrinho.add(new CarrinhoItem(produto, quantidade));
        }
        session.setAttribute("carrinho", carrinho);
    }

    public void limparCarrinho(HttpSession session) {
        session.removeAttribute("carrinho");
    }
}