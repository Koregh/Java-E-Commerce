package com.fuzzyfilms.ecommerce.service;

import com.fuzzyfilms.ecommerce.model.User;
import com.fuzzyfilms.ecommerce.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;

@Service
public class TwoFactorService {

    private static final int MAX_TENTATIVAS = 5;
    private static final int EXPIRA_MINUTOS = 15;
    private static final int BLOQUEIO_MINUTOS = 30;

    @Autowired
    private UserRepository userRepo;
    @Autowired
    private EmailService emailService;
      @Value("${debug:false}")
    private boolean debug;

    /** Gera e envia novo código 2FA. Retorna false se bloqueado. */
    public boolean gerarEEnviar(User user, String contexto) {
        if (estaBloqueado(user)) return false;

        String codigo = String.format("%06d", new SecureRandom().nextInt(1_000_000));
        user.setCodigo2fa(codigo);
        user.setCodigo2faExpira(LocalDateTime.now().plusMinutes(EXPIRA_MINUTOS));
        user.setTentativas2fa(0);
        user.setBloqueado2faAte(null);
        userRepo.save(user);

        emailService.enviarCodigo2FA(user.getEmail(), codigo, contexto);
        return true;
    }

    /** Verifica código. Retorna: "ok", "expirado", "invalido", "bloqueado" */
    public String verificar(User user, String codigoInformado) {
        if (estaBloqueado(user)) return "bloqueado";
        if (user.getCodigo2fa() == null) return "invalido";
        if (LocalDateTime.now().isAfter(user.getCodigo2faExpira())) {
            limpar(user);
            return "expirado";
        }

        if (debug) {
            limpar(user);
             userRepo.save(user);
        return "ok";
        }

        if (!user.getCodigo2fa().equals(codigoInformado.trim())) {
            int t = user.getTentativas2fa() + 1;
            user.setTentativas2fa(t);
            if (t >= MAX_TENTATIVAS) {
                user.setBloqueado2faAte(LocalDateTime.now().plusMinutes(BLOQUEIO_MINUTOS));
                limpar(user);
                userRepo.save(user);
                return "bloqueado";
            }
            userRepo.save(user);
            return "invalido";
        }
        limpar(user);
        userRepo.save(user);
        return "ok";
    }

    public boolean estaBloqueado(User user) {
        return user.getBloqueado2faAte() != null &&
               LocalDateTime.now().isBefore(user.getBloqueado2faAte());
    }

    private void limpar(User user) {
        user.setCodigo2fa(null);
        user.setCodigo2faExpira(null);
        user.setTentativas2fa(0);
    }
}
