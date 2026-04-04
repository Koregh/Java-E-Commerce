package com.fuzzyfilms.ecommerce.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class EmailService {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    // Rate limiting: guarda o último timestamp de envio para cada e-mail
    private final ConcurrentHashMap<String, Long> lastSendTime = new ConcurrentHashMap<>();
    private static final long MIN_INTERVAL_MILLIS = 60_000; // 1 minuto

    // ─────────────────────────────────────────────────────────────
    //  Core sender (com verificação de intervalo)
    // ─────────────────────────────────────────────────────────────
    public boolean enviar(String para, String assunto, String htmlCorpo) {
        // Verifica se pode enviar
        if (!podeEnviar(para)) {
            System.out.println("⏳ Aguarde antes de enviar outro e-mail para " + para + " (intervalo de 1 minuto)");
            return false;
        }

        if (mailSender == null) {
            System.out.println("=== EMAIL (dev) ===");
            System.out.println("Para: " + para);
            System.out.println("Assunto: " + assunto);
            System.out.println("[HTML omitido no console]");
            System.out.println("==================");
            atualizaTimestamp(para);
            return true;
        }

        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom("noreply@fuzzyfilms.com.br", "FUZZI FILMS");
            helper.setTo(para);
            helper.setSubject(assunto);
            helper.setText(htmlCorpo, true);
            mailSender.send(msg);
            atualizaTimestamp(para);
            return true;
        } catch (MessagingException | java.io.UnsupportedEncodingException e) {
            throw new RuntimeException("Erro ao enviar e-mail para " + para, e);
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Métodos com informações de segurança (IP, dispositivo, local)
    // ─────────────────────────────────────────────────────────────

    public void enviarAlertaLogin(String para, String nome, String ip, String userAgent, String localizacao) {
        String assunto = "Novo acesso à sua conta — FUZZI FILMS";
        String corpo = layoutSeguranca(
            "Novo acesso detectado",
            "Seu login foi realizado com sucesso.",
            ip, userAgent, localizacao,
            "Se você não reconhece este acesso, altere sua senha imediatamente."
        );
        enviar(para, assunto, corpo);
    }

    public void enviarAlertaEmail(String para, String novoEmail, String ip, String userAgent, String localizacao) {
        String assunto = "Seu e-mail foi alterado — FUZZI FILMS";
        String corpo = layoutSeguranca(
            "Alteração de e-mail",
            "O e-mail da sua conta foi alterado para: <strong style='color:#1c1a18;'>" + novoEmail + "</strong>",
            ip, userAgent, localizacao,
            "Se você não fez essa alteração, entre em contato imediatamente."
        );
        enviar(para, assunto, corpo);
    }

    public void enviarAlertaSenha(String para, String ip, String userAgent, String localizacao) {
        String assunto = "Sua senha foi alterada — FUZZI FILMS";
        String corpo = layoutSeguranca(
            "Alteração de senha",
            "Sua senha foi alterada com sucesso.",
            ip, userAgent, localizacao,
            "Se você não realizou essa ação, entre em contato imediatamente."
        );
        enviar(para, assunto, corpo);
    }

    // ─────────────────────────────────────────────────────────────
    //  Templates
    // ─────────────────────────────────────────────────────────────

    public void enviarBoasVindas(String para, String nome) {
        enviar(para,
            "Bem-vindo(a) à FUZZI FILMS!",
            layoutBoasVindas(nome));
    }

    public void enviarCodigo2FA(String para, String codigo, String contexto) {
        enviar(para,
            "Seu código de verificação — FUZZI FILMS",
            layout2FA(codigo, contexto));
    }

    // ─────────────────────────────────────────────────────────────
    //  Rate limiting helpers
    // ─────────────────────────────────────────────────────────────

    private boolean podeEnviar(String email) {
        Long last = lastSendTime.get(email);
        if (last == null) return true;
        return (System.currentTimeMillis() - last) >= MIN_INTERVAL_MILLIS;
    }

    private void atualizaTimestamp(String email) {
        lastSendTime.put(email, System.currentTimeMillis());
    }

    // ─────────────────────────────────────────────────────────────
    //  Layout: e-mails de segurança (login, senha, e-mail)
    // ─────────────────────────────────────────────────────────────

    private String layoutSeguranca(String titulo, String descricao,
                                   String ip, String dispositivo, String localizacao,
                                   String instrucao) {
        String dataHora = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
        String ipVal   = (ip != null && !ip.isBlank())          ? ip          : "Não identificado";
        String dispVal = (dispositivo != null && !dispositivo.isBlank()) ? dispositivo : "Não identificado";
        String locVal  = (localizacao != null && !localizacao.isBlank())  ? localizacao  : "Não disponível";

        return wrapBase(
            "<div style='padding:32px 36px;'>" +
            "  <p style='margin:0 0 6px;font-size:11px;font-weight:600;letter-spacing:1.2px;" +
            "     text-transform:uppercase;color:#b83026;'>Segurança da conta</p>" +
            "  <h1 style='margin:0 0 14px;font-size:22px;font-weight:600;color:#1c1a18;" +
            "     line-height:1.2;'>" + titulo + "</h1>" +
            "  <p style='margin:0 0 24px;font-size:15px;color:#4a4745;line-height:1.65;'>" + descricao + "</p>" +

            "  <table width='100%' cellpadding='0' cellspacing='0'" +
            "     style='background:#f7f5f1;border-radius:10px;border:1px solid #e4e1db;" +
            "            margin-bottom:24px;'>" +
            "    <tr><td style='padding:20px 22px;'>" +
            "      <p style='margin:0 0 2px;font-size:10px;font-weight:600;letter-spacing:1px;" +
            "         text-transform:uppercase;color:#aca89f;'>Detalhes do acesso</p>" +
            metaRow("Data / Hora", dataHora) +
            metaRow("Endereço IP", ipVal) +
            metaRow("Dispositivo", dispVal) +
            metaRow("Localização aprox.", locVal) +
            "    </td><tr>" +
            "  </table>" +

            "  <table width='100%' cellpadding='0' cellspacing='0'" +
            "     style='background:#fdf1f0;border-radius:8px;border-left:3px solid #b83026;" +
            "            margin-bottom:28px;'>" +
            "    <tr><td style='padding:14px 18px;font-size:13px;color:#7b1d14;line-height:1.55;'>" +
            instrucao +
            "    </td></tr>" +
            "  </table>" +

            "  <p style='margin:0;font-size:12px;color:#aca89f;'>Este é um e-mail automático. Não responda esta mensagem.</p>" +
            "</div>"
        );
    }

    // ─────────────────────────────────────────────────────────────
    //  Layout: código 2FA
    // ─────────────────────────────────────────────────────────────

    private String layout2FA(String codigo, String contexto) {
        return wrapBase(
            "<div style='padding:32px 36px;'>" +
            "  <p style='margin:0 0 6px;font-size:11px;font-weight:600;letter-spacing:1.2px;" +
            "     text-transform:uppercase;color:#b83026;'>Verificação</p>" +
            "  <h1 style='margin:0 0 14px;font-size:22px;font-weight:600;color:#1c1a18;" +
            "     line-height:1.2;'>Código de verificação</h1>" +
            "  <p style='margin:0 0 28px;font-size:15px;color:#4a4745;line-height:1.65;'>" +
            "     Use o código abaixo para confirmar <strong style='color:#1c1a18;'>" + contexto + "</strong>:" +
            "  </p>" +

            "  <div style='text-align:center;margin:0 0 28px;'>" +
            "    <div style='display:inline-block;background:#f7f5f1;" +
            "         border:1.5px solid #e4e1db;border-radius:10px;" +
            "         padding:18px 44px;font-family:\"Courier New\",\"Lucida Console\",monospace;" +
            "         font-size:34px;font-weight:700;letter-spacing:12px;color:#141414;" +
            "         word-spacing:-4px;'>" + codigo + "</div>" +
            "  </div>" +

            "  <table width='100%' cellpadding='0' cellspacing='0'" +
            "     style='background:#f7f5f1;border-radius:8px;border:1px solid #e4e1db;" +
            "            margin-bottom:28px;'>" +
            "    <tr><td style='padding:14px 18px;font-size:13px;color:#6e6a63;line-height:1.55;'>" +
            "      Válido por <strong style='color:#1c1a18;'>15 minutos</strong>. " +
            "      Nunca compartilhe este código com ninguém." +
            "    </td></tr>" +
            "  </table>" +

            "  <p style='margin:0;font-size:12px;color:#aca89f;'>Este é um e-mail automático. Não responda esta mensagem.</p>" +
            "</div>"
        );
    }

    // ─────────────────────────────────────────────────────────────
    //  Layout: boas-vindas
    // ─────────────────────────────────────────────────────────────

    private String layoutBoasVindas(String nome) {
        return wrapBase(
            "<div style='padding:40px 36px;text-align:center;'>" +

            "  <div style='width:60px;height:60px;border-radius:50%;background:#fdf1f0;" +
            "       border:1.5px solid #edc8c5;margin:0 auto 22px;" +
            "       display:flex;align-items:center;justify-content:center;font-size:26px;" +
            "       line-height:60px;'>&#127916;</div>" +

            "  <p style='margin:0 0 6px;font-size:11px;font-weight:600;letter-spacing:1.2px;" +
            "     text-transform:uppercase;color:#b83026;'>Conta criada</p>" +
            "  <h1 style='margin:0 0 14px;font-size:22px;font-weight:600;color:#1c1a18;" +
            "     line-height:1.3;'>Bem-vindo(a), " + nome + "!</h1>" +
            "  <p style='margin:0 0 32px;font-size:15px;color:#4a4745;line-height:1.65;" +
            "     max-width:340px;display:inline-block;'>" +
            "     Sua conta foi criada com sucesso. Você já pode fazer login e explorar nossos produtos." +
            "  </p>" +
            "  <br>" +

            "  <a href='https://fuzzyfilms.com.br/login'" +
            "     style='display:inline-block;background:#b83026;color:#ffffff;" +
            "            text-decoration:none;padding:13px 32px;border-radius:7px;" +
            "            font-family:Arial,sans-serif;font-size:14px;font-weight:600;" +
            "            letter-spacing:0.02em;margin-bottom:32px;'>" +
            "     Acessar minha conta" +
            "  </a>" +

            "  <p style='margin:0;font-size:12px;color:#aca89f;'>Este é um e-mail automático. Não responda esta mensagem.</p>" +
            "</div>"
        );
    }

    // ─────────────────────────────────────────────────────────────
    //  Wrapper base — estrutura comum a todos os e-mails
    // ─────────────────────────────────────────────────────────────

    private String wrapBase(String conteudo) {
        return "<!DOCTYPE html>" +
        "<html lang='pt-BR'><head>" +
        "<meta charset='UTF-8'>" +
        "<meta name='viewport' content='width=device-width,initial-scale=1'>" +
        "<style>" +
        "  body,table,td,p,a{-webkit-text-size-adjust:100%;-ms-text-size-adjust:100%;}" +
        "  img{border:0;display:block;}" +
        "  body{margin:0;padding:0;background:#f2f0eb;font-family:Arial,'Helvetica Neue',sans-serif;}" +
        "</style>" +
        "</head>" +
        "<body style='margin:0;padding:0;background:#f2f0eb;'>" +

        "<table width='100%' cellpadding='0' cellspacing='0' border='0'" +
        "  style='background:#f2f0eb;padding:40px 16px;'>" +
        "<tr><td align='center'>" +

        "<table width='560' cellpadding='0' cellspacing='0' border='0'" +
        "  style='max-width:560px;background:#ffffff;border-radius:14px;" +
        "         border:1px solid #e4e1db;" +
        "         box-shadow:0 4px 24px rgba(0,0,0,0.07);'>" +

        "<tr><td style='background:#1a1a1a;border-radius:14px 14px 0 0;" +
        "     padding:18px 32px;'>" +
        "  <table cellpadding='0' cellspacing='0' border='0'>蜜饯" +
        "    <td style='width:8px;height:8px;'>" +
        "      <div style='width:8px;height:8px;border-radius:50%;background:#b83026;'></div>" +
        "    </td>" +
        "    <td style='padding-left:10px;font-family:Arial,sans-serif;font-size:15px;" +
        "       font-weight:600;color:#ffffff;letter-spacing:0.05em;'>FUZZI FILMS</td>" +
        "   </tr></table>" +
        "</td></tr>" +

        "<tr><td>" + conteudo + "</td></tr>" +

        "<tr><td style='background:#f7f5f1;border-radius:0 0 14px 14px;" +
        "     padding:14px 32px;border-top:1px solid #e4e1db;text-align:center;" +
        "     font-family:Arial,sans-serif;font-size:11px;color:#aca89f;" +
        "     letter-spacing:0.02em;'>" +
        "  &copy; FUZZI FILMS &nbsp;&middot;&nbsp; Protegemos seus dados." +
        "</td></tr>" +

        "</table>" +
        "</td></tr></table>" +
        "</body></html>";
    }

    // ─────────────────────────────────────────────────────────────
    //  Utilitário — linha de metadado (ex: "IP · 177.0.0.1")
    // ─────────────────────────────────────────────────────────────

    private String metaRow(String label, String valor) {
        return "<p style='margin:10px 0 0;font-size:13px;color:#6e6a63;line-height:1.5;'>" +
               "<span style='color:#1c1a18;font-weight:600;'>" + label + "</span>" +
               "<span style='color:#cdc9c1;margin:0 8px;'>·</span>" +
               valor + "</p>";
    }
}