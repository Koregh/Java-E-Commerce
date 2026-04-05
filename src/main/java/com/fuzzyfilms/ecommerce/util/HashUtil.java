package com.fuzzyfilms.ecommerce.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utilitários de hash seguros.
 *
 * CPF  → HMAC-SHA256 com secret configurável (app.cpf-secret).
 *         Permite verificar unicidade no banco sem armazenar o CPF em claro.
 *         Rainbow tables não funcionam sem conhecer o secret.
 *
 * 2FA  → SHA-256 do código de 6 dígitos para armazenamento.
 *         Comparação usa MessageDigest.isEqual (constant-time).
 */
public class HashUtil {

    private HashUtil() {}

    // ── CPF: HMAC-SHA256 ─────────────────────────────────────────────────

    /**
     * Gera HMAC-SHA256 do CPF usando o secret da aplicação.
     *
     * @param cpfLimpo CPF somente dígitos (11 chars), já validado
     * @param secret   valor de app.cpf-secret (mínimo 32 chars recomendado)
     * @return hex lowercase do HMAC (64 chars)
     */
    public static String hmacCpf(String cpfLimpo, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(), "HmacSHA256"));
            byte[] raw = mac.doFinal(cpfLimpo.getBytes());
            return bytesToHex(raw);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Erro ao calcular HMAC-SHA256 do CPF", e);
        }
    }

    // ── 2FA: SHA-256 + comparação constant-time ──────────────────────────

    /**
     * Gera SHA-256 do código 2FA para armazenamento no banco.
     * Nunca salve o código em texto puro.
     */
    public static String hash2fa(String codigo) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return bytesToHex(md.digest(codigo.getBytes()));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Erro ao calcular hash do código 2FA", e);
        }
    }

    /**
     * Compara o código informado com o hash armazenado usando
     * comparação constant-time (evita timing attacks).
     *
     * @param codigoInformado código digitado pelo usuário
     * @param hashArmazenado  hash SHA-256 salvo no banco
     * @return true se o código confere
     */
    public static boolean verificar2fa(String codigoInformado, String hashArmazenado) {
        if (codigoInformado == null || hashArmazenado == null) return false;
        byte[] hashInput = hexToBytes(hash2fa(codigoInformado.trim()));
        byte[] hashSalvo = hexToBytes(hashArmazenado);
        return MessageDigest.isEqual(hashInput, hashSalvo);
    }

    // ── Utilitários internos ─────────────────────────────────────────────

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                                 + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}