package com.fuzzyfilms.ecommerce.service;

import com.fuzzyfilms.ecommerce.model.User;
import com.fuzzyfilms.ecommerce.repository.UserRepository;
import com.fuzzyfilms.ecommerce.util.HashUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.security.SecureRandom;
import java.time.LocalDateTime;

/**
 * Serviço de autenticação de dois fatores.
 *
 * O código de 6 dígitos é armazenado HASHEADO (SHA-256) no banco —
 * nunca em texto puro. A verificação usa HashUtil.verificar2fa(),
 * que compara em constant-time para evitar timing attacks.
 */
@Service
public class TwoFactorService {

    private static final int MAX_TENTATIVAS   = 5;
    private static final int EXPIRA_MINUTOS   = 15;
    private static final int BLOQUEIO_MINUTOS = 30;

    @Autowired private UserRepository userRepo;
    @Autowired private EmailService emailService;

    /**
     * Gera código 2FA, armazena o HASH no banco e envia o código em claro por e-mail.
     * Retorna false se o usuário estiver bloqueado.
     */
    public boolean gerarEEnviar(User user, String contexto) {
        if (estaBloqueado(user)) return false;

        // Gera código de 6 dígitos com SecureRandom
        String codigo = String.format("%06d", new SecureRandom().nextInt(1_000_000));

        // Armazena HASH — nunca o código em claro
        user.setCodigo2fa(HashUtil.hash2fa(codigo));
        user.setCodigo2faExpira(LocalDateTime.now().plusMinutes(EXPIRA_MINUTOS));
        user.setTentativas2fa(0);
        user.setBloqueado2faAte(null);
        userRepo.save(user);

        // Envia o código em claro apenas por e-mail
        emailService.enviarCodigo2FA(user.getEmail(), codigo, contexto);
        return true;
    }

    /**
     * Verifica o código informado pelo usuário.
     *
     * @return "ok" | "expirado" | "invalido" | "bloqueado"
     */
    public String verificar(User user, String codigoInformado) {
        if (estaBloqueado(user)) return "bloqueado";
        if (user.getCodigo2fa() == null) return "invalido";

        if (LocalDateTime.now().isAfter(user.getCodigo2faExpira())) {
            limpar(user);
            userRepo.save(user);
            return "expirado";
        }

        // Comparação constant-time via HashUtil
        boolean correto = HashUtil.verificar2fa(codigoInformado, user.getCodigo2fa());

        if (!correto) {
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