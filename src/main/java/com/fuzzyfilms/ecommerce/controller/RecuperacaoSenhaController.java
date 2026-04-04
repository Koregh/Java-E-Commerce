package com.fuzzyfilms.ecommerce.controller;

import com.fuzzyfilms.ecommerce.model.User;
import com.fuzzyfilms.ecommerce.repository.UserRepository;
import com.fuzzyfilms.ecommerce.service.EmailService;
import com.fuzzyfilms.ecommerce.service.TwoFactorService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class RecuperacaoSenhaController {

    @Autowired private UserRepository userRepo;
    @Autowired private TwoFactorService tfaService;
    @Autowired private PasswordEncoder encoder;

    // ─── Página para solicitar recuperação (informar e-mail) ───
    @GetMapping("/esqueci-senha")
    public String formEsqueciSenha() {
        return "esqueci_senha";
    }

    // ─── 1ª etapa: enviar código 2FA para o e-mail informado ───
    @PostMapping("/esqueci-senha")
    public String enviarCodigoRecuperacao(@RequestParam String email,
                                          HttpSession session,
                                          RedirectAttributes ra) {
        User user = userRepo.findByEmail(email).orElse(null);
        if (user == null) {
            ra.addFlashAttribute("erro", "E-mail não cadastrado.");
            return "redirect:/esqueci-senha";
        }

        // Salva o email na sessão para a próxima etapa
        session.setAttribute("reset_email", email);

        // Envia código 2FA para o e-mail do usuário
        boolean enviou = tfaService.gerarEEnviar(user, "Recuperação de senha");
        if (!enviou) {
            ra.addFlashAttribute("erro", "Muitas tentativas. Aguarde antes de solicitar novo código.");
            return "redirect:/esqueci-senha";
        }

        ra.addFlashAttribute("info", "Código de verificação enviado para " + mascararEmail(email));
        return "redirect:/esqueci-senha/verificar";
    }

    // ─── Página para digitar o código 2FA ─────────────────────
    @GetMapping("/esqueci-senha/verificar")
    public String paginaVerificarCodigo(HttpSession session, Model model) {
        String email = (String) session.getAttribute("reset_email");
        if (email == null) return "redirect:/esqueci-senha";
        model.addAttribute("email", mascararEmail(email));
        return "verificar_codigo_recuperacao";
    }

    // ─── Verificar o código e redirecionar para redefinir senha ─
    @PostMapping("/esqueci-senha/verificar")
    public String verificarCodigo(@RequestParam String codigo,
                                  HttpSession session,
                                  RedirectAttributes ra) {
        String email = (String) session.getAttribute("reset_email");
        if (email == null) return "redirect:/esqueci-senha";

        User user = userRepo.findByEmail(email).orElse(null);
        if (user == null) {
            session.removeAttribute("reset_email");
            return "redirect:/esqueci-senha";
        }

        String resultado = tfaService.verificar(user, codigo);
        if ("ok".equals(resultado)) {
            // Código correto: permite redefinir a senha
            session.setAttribute("reset_autorizado", true);
            return "redirect:/esqueci-senha/redefinir";
        } else if ("bloqueado".equals(resultado)) {
            ra.addFlashAttribute("erro", "Muitas tentativas incorretas. Solicite um novo código.");
            return "redirect:/esqueci-senha";
        } else if ("expirado".equals(resultado)) {
            ra.addFlashAttribute("erro", "Código expirado. Solicite novamente.");
            return "redirect:/esqueci-senha";
        } else {
            int restantes = 5 - user.getTentativas2fa();
            ra.addFlashAttribute("erro", "Código inválido. " + restantes + " tentativa(s) restante(s).");
            return "redirect:/esqueci-senha/verificar";
        }
    }

    // ─── Página para redefinir a senha ────────────────────────
    @GetMapping("/esqueci-senha/redefinir")
    public String formRedefinirSenha(HttpSession session, Model model) {
        Boolean autorizado = (Boolean) session.getAttribute("reset_autorizado");
        if (autorizado == null || !autorizado) return "redirect:/esqueci-senha";
        return "redefinir_senha";
    }

    // ─── Salvar a nova senha ─────────────────────────────────
    @PostMapping("/esqueci-senha/redefinir")
    public String redefinirSenha(@RequestParam String senha,
                                 @RequestParam String confirmar,
                                 HttpSession session,
                                 RedirectAttributes ra) {
        Boolean autorizado = (Boolean) session.getAttribute("reset_autorizado");
        String email = (String) session.getAttribute("reset_email");
        if (autorizado == null || !autorizado || email == null) {
            return "redirect:/esqueci-senha";
        }

        if (!senha.equals(confirmar)) {
            ra.addFlashAttribute("erro", "As senhas não coincidem.");
            return "redirect:/esqueci-senha/redefinir";
        }
        if (senha.length() < 8) {
            ra.addFlashAttribute("erro", "A senha deve ter no mínimo 8 caracteres.");
            return "redirect:/esqueci-senha/redefinir";
        }
        if (!senha.matches(".*[a-zA-Z].*") || !senha.matches(".*[0-9].*")) {
            ra.addFlashAttribute("erro", "A senha deve conter ao menos 1 letra e 1 número.");
            return "redirect:/esqueci-senha/redefinir";
        }

        User user = userRepo.findByEmail(email).orElse(null);
        if (user != null) {
            user.setSenha(encoder.encode(senha));
            userRepo.save(user);
        }

        // Limpa sessão
        session.removeAttribute("reset_email");
        session.removeAttribute("reset_autorizado");

        ra.addFlashAttribute("sucesso", "Senha alterada com sucesso! Faça login com sua nova senha.");
        return "redirect:/login";
    }

    // Helper para mascarar e-mail
    private String mascararEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 2) return email;
        return email.substring(0, 2) + "***" + email.substring(at);
    }
}