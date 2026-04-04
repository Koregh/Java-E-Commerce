package com.fuzzyfilms.ecommerce.controller;

import com.fuzzyfilms.ecommerce.model.FreteConfig;
import com.fuzzyfilms.ecommerce.repository.FreteConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/frete")
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class AdminFreteController {

    @Autowired
    private FreteConfigRepository freteConfigRepo;

    @GetMapping
    public String form(Model model) {
        FreteConfig config = freteConfigRepo.findAll().stream().findFirst().orElse(new FreteConfig());
        model.addAttribute("config", config);
        return "admin/frete_config";
    }

    @PostMapping("/salvar")
    public String salvar(@ModelAttribute FreteConfig config, RedirectAttributes ra) {
        // Se não houver ID, salva novo; senão atualiza
        freteConfigRepo.save(config);
        ra.addFlashAttribute("sucesso", "Configuração de frete salva!");
        return "redirect:/admin/frete";
    }
}