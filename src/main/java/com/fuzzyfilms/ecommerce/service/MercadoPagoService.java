package com.fuzzyfilms.ecommerce.service;

import org.springframework.stereotype.Service;
import java.math.BigDecimal;

@Service
public class MercadoPagoService {

    /**
     * Cria uma preferência de pagamento (checkout hospedado) para cartão de crédito.
     * @param descricao Descrição do produto/pedido
     * @param total Valor total do pedido
     * @param externalReference Referência externa (ex: ID do pedido)
     * @return ID da preferência gerada
     */
    public String criarPreferencia(String descricao, BigDecimal total, String externalReference) {
        // Mock: retorna um ID fictício (em produção, chama a API real)
        return "PREF_" + System.currentTimeMillis();
    }

    /**
     * Gera um QR Code PIX para pagamento.
     * @param externalReference Referência externa (ex: ID do pedido)
     * @param valor Valor do pedido
     * @param descricao Descrição do produto/pedido
     * @return String contendo o QR Code (mock)
     */
    public String gerarQrCodePix(String externalReference, BigDecimal valor, String descricao) {
        // Mock: retorna um QR Code fictício (em produção, chama a API real)
        return "00020101021226800014br.gov.bcb.pix2565qrcodefalso...";
    }
}