package com.fuzzyfilms.ecommerce.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Valida CEP (ViaCEP) e CPF (algoritmo dos dígitos verificadores).
 * Aceita apenas CEPs nacionais (Brasil).
 */
@Service
public class EnderecoValidacaoService {

    private static final String VIACEP_URL = "https://viacep.com.br/ws/%s/json/";

    // ─── CEP ────────────────────────────────────────────────────────

    /**
     * Valida formato e existência do CEP via ViaCEP.
     * @return resultado com campos preenchidos, ou null se inválido/não encontrado.
     */
    public ViaCepResponse validarCep(String cep) {
        String cepLimpo = cep.replaceAll("\\D", "");
        if (cepLimpo.length() != 8) return null;
        try {
            RestTemplate rt = new RestTemplate();
            ViaCepResponse resp = rt.getForObject(
                String.format(VIACEP_URL, cepLimpo), ViaCepResponse.class);
            if (resp == null || resp.getErro() != null) return null;
            return resp;
        } catch (Exception e) {
            return null;
        }
    }

    /** Formata CEP: "01310100" → "01310-100" */
    public String formatarCep(String cep) {
        String c = cep.replaceAll("\\D", "");
        if (c.length() != 8) return cep;
        return c.substring(0, 5) + "-" + c.substring(5);
    }

    // ─── CPF ────────────────────────────────────────────────────────

    /**
     * Valida CPF pelo algoritmo oficial dos dígitos verificadores.
     * Rejeita sequências repetidas (111.111.111-11 etc.).
     */
    public boolean cpfValido(String cpf) {
        String c = cpf.replaceAll("\\D", "");
        if (c.length() != 11) return false;
        if (c.chars().distinct().count() == 1) return false; // todos iguais

        int soma = 0;
        for (int i = 0; i < 9; i++) soma += (c.charAt(i) - '0') * (10 - i);
        int r1 = 11 - (soma % 11);
        int d1 = (r1 >= 10) ? 0 : r1;
        if (d1 != (c.charAt(9) - '0')) return false;

        soma = 0;
        for (int i = 0; i < 10; i++) soma += (c.charAt(i) - '0') * (11 - i);
        int r2 = 11 - (soma % 11);
        int d2 = (r2 >= 10) ? 0 : r2;
        return d2 == (c.charAt(10) - '0');
    }

    /**
     * Extrai sufixo visível do CPF: últimos 5 dígitos.
     * Ex: "12345678901" → "78901"
     */
    public String extrairSufixo(String cpf) {
        String c = cpf.replaceAll("\\D", "");
        if (c.length() != 11) return "";
        return c.substring(6); // dígitos 7–11 → exibidos como "XXX-XX"
    }

    // ─── DTO ViaCEP ──────────────────────────────────────────────────

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ViaCepResponse {
        private String cep;
        private String logradouro;
        private String bairro;
        private String localidade;   // cidade
        private String uf;           // estado
        private String erro;         // "true" quando CEP não existe
    }
}