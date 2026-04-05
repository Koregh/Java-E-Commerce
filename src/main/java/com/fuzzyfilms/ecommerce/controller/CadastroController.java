package com.fuzzyfilms.ecommerce.controller;

import com.fuzzyfilms.ecommerce.model.AuditLog.TipoAcao;
import com.fuzzyfilms.ecommerce.model.User;
import com.fuzzyfilms.ecommerce.repository.UserRepository;
import com.fuzzyfilms.ecommerce.service.AuditService;
import com.fuzzyfilms.ecommerce.service.EmailService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class CadastroController {

    @Autowired private UserRepository userRepo;
    @Autowired private PasswordEncoder encoder;
    @Autowired private EmailService emailService;
    @Autowired private AuditService auditService;

    @GetMapping("/cadastro")
    public String form() {
        return "cadastro";
    }

     @GetMapping("/privacidade")
    public String privacidade() {
        return "privacidade";
    }

      @GetMapping("/termos")
    public String termos() {
        return "termos";
    }

      @GetMapping("/sobre")
    public String sobre() {
        return "sobre";
    }


     @GetMapping("/trocas")
    public String trocas() {
        return "trocas";
    }

        public static boolean isValidEmail(String email) {
    String regex = "^[\\w!#$%&'*+/=?`{|}~^-]+(?:\\.[\\w!#$%&'*+/=?`{|}~^-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,6}$";
    return email != null && email.matches(regex);
}

    // Exige: 8+ caracteres, pelo menos 1 maiúscula, 1 minúscula, 1 número, 1 caractere especial
private static final String SENHA_REGEX = 
    "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$";

  @PostMapping("/cadastro")
public String registrar(@RequestParam String nome,
                        @RequestParam String email,
                        @RequestParam String senha,
                        @RequestParam String confirmar,
                        @RequestParam(required = false) String aceite_lgpd,
                        HttpServletRequest req,
                        RedirectAttributes ra,
                        Model model) {

    // Validações
    if (aceite_lgpd == null) {
        model.addAttribute("erro", "Você precisa aceitar a Política de Privacidade.");
        return "cadastro";
    }
    if (!senha.equals(confirmar)) {
        model.addAttribute("erro", "As senhas não coincidem.");
        return "cadastro";
    }
    if (senha.length() < 8) {
        model.addAttribute("erro", "A senha deve ter no mínimo 8 caracteres.");
        return "cadastro";
    }
    if (nome.trim().length() < 2 || nome.trim().length() > 50) {
        model.addAttribute("erro", "Nome deve ter entre 2 e 50 caracteres.");
        return "cadastro";
    }

    if (!isValidEmail(email)) {
        model.addAttribute("erro", "E-mail inválido.");
        return "cadastro";
    }

    if (!senha.matches(SENHA_REGEX)) {
    model.addAttribute("erro", 
        "A senha deve ter no mínimo 8 caracteres, incluindo letra maiúscula, minúscula, número e caractere especial (@$!%*?&).");
    return "cadastro";
}

    // Criar usuário (dentro do try-catch)
    User u = new User();
    u.setNome(nome.trim());
    u.setEmail(email.toLowerCase().trim());
    u.setSenha(encoder.encode(senha));

    try {
        userRepo.save(u);
    } catch (DataIntegrityViolationException e) {
        model.addAttribute("erro", "Este e-mail já está cadastrado.");
        return "cadastro";
    }

    // E-mail de boas-vindas (agora u está no escopo correto)
    emailService.enviarBoasVindas(u.getEmail(), u.getNome());

    // Log de auditoria
    auditService.log(u, TipoAcao.CADASTRO,
        "Novo cadastro: " + u.getNome(), req.getRemoteAddr());

    ra.addFlashAttribute("sucesso", "Conta criada! Faça login para continuar.");
    return "redirect:/login";
}
}
