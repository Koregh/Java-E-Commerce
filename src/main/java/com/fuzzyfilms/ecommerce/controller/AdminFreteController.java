package com.fuzzyfilms.ecommerce.controller;

import com.fuzzyfilms.ecommerce.model.FreteConfig;
import com.fuzzyfilms.ecommerce.repository.FreteConfigRepository;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/frete")
@PreAuthorize("hasAnyRole('ADMINISTRADOR', 'GERENTE')")
public class AdminFreteController {

    @Autowired
    private FreteConfigRepository freteConfigRepo;

    @GetMapping
public String form(Model model) {
    FreteConfig config = freteConfigRepo.findById(1L).orElseGet(() -> {
        FreteConfig newConfig = new FreteConfig();
        newConfig.setId(1L);
        return newConfig;
    });
    model.addAttribute("config", config);
    return "admin/frete_config";
}

@PostMapping("/salvar")
public String salvar(@ModelAttribute FreteConfig config, RedirectAttributes ra) {
     System.out.println(">>> CONFIG RECEBIDA: " + config);
    System.out.println(">>> TIPO: " + config.getTipoFrete());
    System.out.println(">>> VALOR FIXO: " + config.getValorFixo());
    config.setId(1L); // força o ID fixo
    if ("GRATIS".equals(config.getTipoFrete().name())) {
        config.setValorFixo(null);
    }
    freteConfigRepo.save(config);
    ra.addFlashAttribute("sucesso", "Configuração salva!");
    return "redirect:/admin/frete";
}
}