package com.fuzzyfilms.ecommerce.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "produtos")
public class Produto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(length = 500)
    private String descricao;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal preco;

    @Column(nullable = false)
    private int estoque = 0;

    @Column(name = "vendas_realizadas")
    private int vendasRealizadas = 0;

    @Column(length = 255)
    private String imagem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendedor_id", nullable = false)
    private User vendedor;

    @Column(name = "criado_em")
    private LocalDateTime criadoEm = LocalDateTime.now();

    @Column(name = "ativo")
    private boolean ativo = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_produto", nullable = false, length = 20)
    private StatusProduto statusProduto = StatusProduto.ATIVO;

    // ─── Transporte ───────────────────────────────────────────────────

    /** Tipo de frete específico deste produto. Null = usa config global. */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_frete", length = 10)
    private TipoFreteP tipoFrete;

    /** Se null/false: usa config global */
    @Column(name = "aceita_entrega_urgente")
    private Boolean aceitaEntregaUrgente = false;

    /** Se null/false: usa config global */
    @Column(name = "aceita_agendamento")
    private Boolean aceitaAgendamento = false;

    /**
     * Horários de entrega disponíveis (CSV), ex:
     * "08:00–12:00,12:00–18:00"
     * Null = sem restrição
     */
    @Column(name = "horarios_entrega", length = 300)
    private String horariosEntrega;

    public enum TipoFreteP {
        GRATIS, FIXO
    }

    public enum StatusProduto {
        ATIVO,
        INATIVO,
        SUSPENSO   // suspenso por conformidade / AQL
    }
}
