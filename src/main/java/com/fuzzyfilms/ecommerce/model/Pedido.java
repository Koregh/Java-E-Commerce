package com.fuzzyfilms.ecommerce.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "pedidos")
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comprador_id", nullable = false)
    private User comprador;

    // REMOVIDOS: produto, quantidade, descricaoItens

    @Column(name = "valor_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorTotal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatusPedido status = StatusPedido.RECEBIDO;

    @Enumerated(EnumType.STRING)
    @Column(name = "modo_entrega", nullable = false, length = 20)
    private ModoEntrega modoEntrega = ModoEntrega.RECEBER;

    @Column(name = "criado_em")
    private LocalDateTime criadoEm = LocalDateTime.now();

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm = LocalDateTime.now();

    @Column(name = "observacao", length = 500)
    private String observacao;

    @Column(name = "codigo_rastreio")
    private String codigoRastreio;

    @Column(name = "transportadora")
    private String transportadora;

    @Column(name = "previsao_entrega_rota")
    private LocalDate previsaoEntregaRota;

    @Column(name = "payment_id")
    private String paymentId;

    @Column(name = "payment_status")
    private String paymentStatus;

    @Column(name = "payment_method")
    private String paymentMethod;

    @Column(name = "qr_code")
    private String qrCode;

    @Column(name = "valor_frete")
    private BigDecimal valorFrete;

    @Column(name = "endereco_entrega", length = 300)
    private String enderecoEntrega;

    @Column(name = "preference_id")
    private String preferenceId;

    // NOVO: lista de itens do pedido
    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PedidoItem> itens = new ArrayList<>();

    // ─── Enums ───────────────────────────────────────────────────────
    public enum StatusPedido {
        RECEBIDO, COLETADO, NO_ARMAZEM, SAIU_DO_ARMAZEM, EM_TRANSITO,
        EM_ROTA, ENTREGUE, CANCELADO, REEMBOLSADO, AGUARDANDO_PAGAMENTO,
        DESTINATARIO_AUSENTE, ENDERECO_NAO_LOCALIZADO
    }

    public enum ModoEntrega {
        RECEBER, RETIRAR_NO_LOCAL
    }
}