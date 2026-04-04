package com.fuzzyfilms.ecommerce.controller;

import com.fuzzyfilms.ecommerce.model.User;
import com.fuzzyfilms.ecommerce.repository.UserRepository;
import com.fuzzyfilms.ecommerce.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ContatoController {

    @Autowired private EmailService emailService;
    @Autowired private UserRepository userRepo;

    @GetMapping("/contato")
    public String contato() {
        return "contato";
    }

    @PostMapping("/contato/enviar")
    public String enviarMensagem(@RequestParam String nome,
                                 @RequestParam String email,
                                 @RequestParam String assunto,
                                 @RequestParam String mensagem,
                                 @AuthenticationPrincipal UserDetails ud,
                                 RedirectAttributes ra) {
        try {
            String corpo = "Nome: " + nome + "\nE-mail: " + email + "\nAssunto: " + assunto + "\n\nMensagem:\n" + mensagem;
            emailService.enviar("contato@fuzzyfilms.com", "Contato via site - " + assunto, corpo);
            ra.addFlashAttribute("sucesso", "Mensagem enviada com sucesso! Responderemos em até 2 dias úteis.");
        } catch (Exception e) {
            ra.addFlashAttribute("erro", "Erro ao enviar mensagem. Tente novamente ou use o e-mail diretamente.");
        }
        return "redirect:/contato";
    }
}