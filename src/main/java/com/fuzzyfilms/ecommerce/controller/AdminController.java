package com.fuzzyfilms.ecommerce.controller;

import com.fuzzyfilms.ecommerce.model.AuditLog.TipoAcao;
import com.fuzzyfilms.ecommerce.model.Cargo;
import com.fuzzyfilms.ecommerce.model.User;
import com.fuzzyfilms.ecommerce.repository.UserRepository;
import com.fuzzyfilms.ecommerce.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasAnyRole('ADMINISTRADOR', 'GERENTE')")
public class AdminController {

    @Autowired private UserRepository userRepo;
    @Autowired private AuditService auditService;

    // Listar todos os usuários
    @GetMapping("/usuarios")
    public String listarUsuarios(Model model) {
        model.addAttribute("usuarios", userRepo.findAll());
        return "admin/usuarios";
    }

    @PostMapping("/usuario/{id}/cargo")
public String alterarCargo(@PathVariable Long id,
                           @RequestParam String cargo,
                           @AuthenticationPrincipal UserDetails currentUser,
                           HttpServletRequest req,
                           RedirectAttributes ra) {
    User alvo = userRepo.findById(id).orElse(null);
    if (alvo == null) {
        ra.addFlashAttribute("erro", "Usuário não encontrado.");
        return "redirect:/admin/usuarios";
    }

    

    User admin = userRepo.findByEmail(currentUser.getUsername()).orElseThrow();

    if (admin.getId().equals(alvo.getId())) {
    ra.addFlashAttribute("erro", "Você não pode alterar seu próprio cargo.");
    return "redirect:/admin/usuarios";
}       

    if (admin.getId().equals(alvo.getId()) && !alvo.getCargo().name().equals(cargo)) {
        ra.addFlashAttribute("erro", "Você não pode alterar seu próprio cargo.");
        return "redirect:/admin/usuarios";
    }

    String cargoAntigo = alvo.getCargo().name();
    try {
        Cargo novoCargo = Cargo.valueOf(cargo.toUpperCase()); // <-- corrigido
        alvo.setCargo(novoCargo);
        userRepo.save(alvo);

        auditService.log(admin, TipoAcao.CARGO_ALTERADO,
            "Cargo de " + alvo.getEmail() + " alterado de " + cargoAntigo + " para " + cargo,
            req.getRemoteAddr());

        ra.addFlashAttribute("sucesso", "Cargo de " + alvo.getEmail() + " alterado para " + cargo);
    } catch (IllegalArgumentException e) {
        ra.addFlashAttribute("erro", "Cargo inválido: " + cargo);
    }
    return "redirect:/admin/usuarios";
}
}