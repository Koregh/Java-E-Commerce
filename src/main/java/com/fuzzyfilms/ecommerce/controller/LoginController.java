package com.fuzzyfilms.ecommerce.controller;

import com.fuzzyfilms.ecommerce.model.User;
import com.fuzzyfilms.ecommerce.repository.UserRepository;
import com.fuzzyfilms.ecommerce.service.AuditService;
import com.fuzzyfilms.ecommerce.service.TwoFactorService;
import com.fuzzyfilms.ecommerce.model.AuditLog.TipoAcao;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.time.LocalDateTime;

@Controller
public class LoginController {

    @Autowired private UserRepository userRepo;
    @Autowired private TwoFactorService tfaService;
    @Autowired private AuditService auditService;
    @Autowired private AuthenticationManager authManager;

    // ─── GET /login ───────────────────────────────────────────────────
    @GetMapping("/login")
    public String loginPage(HttpSession session, Model model) {
        if (session.getAttribute("2fa_ok") != null) return "redirect:/";
        return "login";
    }

    // ─── POST /login/auth ─────────────────────────────────────────────
    @PostMapping("/login/auth")
    public String autenticar(@RequestParam String username,
                              @RequestParam String password,
                              HttpServletRequest req,
                              RedirectAttributes ra) {
        String ip = req.getRemoteAddr();

        User user = userRepo.findByEmail(username).orElse(null);
        if (user == null) {
            ra.addFlashAttribute("erro", "E-mail ou senha inválidos.");
            return "redirect:/login";
        }

        // Brute-force: login bloqueado?
        if (user.getBloqueadoLoginAte() != null &&
            LocalDateTime.now().isBefore(user.getBloqueadoLoginAte())) {
            ra.addFlashAttribute("erro", "Conta bloqueada por excesso de tentativas. Tente novamente mais tarde.");
            return "redirect:/login";
        }

        try {
            Authentication auth = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password));

            // Credenciais OK — resetar contador
            user.setTentativasLogin(0);
            user.setBloqueadoLoginAte(null);
            userRepo.save(user);

            // Gerar 2FA
            boolean enviou = tfaService.gerarEEnviar(user, "Login na FUZZI FILMS");
            if (!enviou) {
                ra.addFlashAttribute("erro", "Conta bloqueada por excesso de tentativas de verificação.");
                return "redirect:/login";
            }

            HttpSession session = req.getSession(true);
            session.setAttribute("2fa_pendente_email", username);
            session.setAttribute("spring_auth_pendente", auth);

        } catch (BadCredentialsException e) {
            int t = user.getTentativasLogin() + 1;
            user.setTentativasLogin(t);
            if (t >= 5) {
                user.setBloqueadoLoginAte(LocalDateTime.now().plusMinutes(30));
                user.setTentativasLogin(0);
                auditService.log(user, TipoAcao.USUARIO_BLOQUEADO, "Bloqueio por brute-force no login", ip);
            }
            userRepo.save(user);
            ra.addFlashAttribute("erro", "E-mail ou senha inválidos.");
            return "redirect:/login";
        }

        return "redirect:/login/2fa";
    }

    // ─── GET /login/2fa ───────────────────────────────────────────────
    @GetMapping("/login/2fa")
    public String pagina2fa(HttpSession session, Model model) {
        String email = (String) session.getAttribute("2fa_pendente_email");
        if (email == null) return "redirect:/login";
        model.addAttribute("email", email);
        return "verificar_2fa";
    }

    // ─── POST /login/2fa ──────────────────────────────────────────────
    @PostMapping("/login/2fa")
    public String verificar2fa(@RequestParam String codigo,
                                HttpServletRequest req,
                                RedirectAttributes ra) {
        HttpSession session = req.getSession(false);
        if (session == null) return "redirect:/login";

        String email = (String) session.getAttribute("2fa_pendente_email");
        Authentication authPendente = (Authentication) session.getAttribute("spring_auth_pendente");
        if (email == null || authPendente == null) return "redirect:/login";

        User user = userRepo.findByEmail(email).orElse(null);
        if (user == null) return "redirect:/login";

        String resultado = tfaService.verificar(user, codigo);

        switch (resultado) {
            case "ok" -> {
                SecurityContextHolder.getContext().setAuthentication(authPendente);
                session.setAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                    SecurityContextHolder.getContext());
                session.setAttribute("2fa_ok", true);
                session.removeAttribute("2fa_pendente_email");
                session.removeAttribute("spring_auth_pendente");
                auditService.log(user, TipoAcao.LOGIN, "Login bem-sucedido", req.getRemoteAddr());
                return "redirect:/";
            }
            case "bloqueado" -> {
                auditService.log(user, TipoAcao.USUARIO_BLOQUEADO,
                    "Bloqueio por excesso de tentativas 2FA no login", req.getRemoteAddr());
                session.invalidate();
                ra.addFlashAttribute("erro", "Muitas tentativas incorretas. Aguarde 30 minutos e faça login novamente.");
                return "redirect:/login";
            }
            case "expirado" -> {
                ra.addFlashAttribute("erro", "Código expirado. Faça login novamente para receber um novo código.");
                session.invalidate();
                return "redirect:/login";
            }
            default -> {
                // Registrar tentativa falha de 2FA
                auditService.log(user, TipoAcao.TENTATIVA_2FA_FALHA,
                    "Código 2FA inválido no login (" + user.getTentativas2fa() + "/5 tentativas)", req.getRemoteAddr());
                int restantes = 5 - user.getTentativas2fa();
                ra.addFlashAttribute("erro", "Código inválido. " + restantes + " tentativa(s) restante(s).");
                ra.addFlashAttribute("email", email);
                return "redirect:/login/2fa";
            }
        }
    }

    // ─── POST /login/reenviar ─────────────────────────────────────────
    @PostMapping("/login/reenviar")
    public String reenviar2fa(HttpSession session, RedirectAttributes ra) {
        String email = (String) session.getAttribute("2fa_pendente_email");
        if (email == null) return "redirect:/login";

        User user = userRepo.findByEmail(email).orElse(null);
        if (user == null) return "redirect:/login";

        boolean enviou = tfaService.gerarEEnviar(user, "Login na FUZZI FILMS (reenvio)");
        if (enviou) ra.addFlashAttribute("info", "Novo código enviado para " + email);
        else ra.addFlashAttribute("erro", "Conta bloqueada. Aguarde antes de solicitar novo código.");
        return "redirect:/login/2fa";
    }
}
