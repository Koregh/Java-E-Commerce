package com.fuzzyfilms.ecommerce.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "frete_config")
public class FreteConfig {

    @Id
    private Long id = 1L; // Singleton

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoFrete tipoFrete = TipoFrete.GRATIS;

    @Column(name = "cep_base", length = 8)
    private String cepBase;

    @Column(name = "valor_fixo")
    private BigDecimal valorFixo;

    @ElementCollection
    @CollectionTable(name = "frete_faixas", joinColumns = @JoinColumn(name = "config_id"))
    private List<FaixaFrete> faixas = new ArrayList<>();

    @Column(name = "limite_hora_compra")
    private LocalTime limiteHoraCompra = LocalTime.of(23, 59);

    @ElementCollection
    @CollectionTable(name = "frete_dias_bloqueados", joinColumns = @JoinColumn(name = "config_id"))
    @Column(name = "dia_semana")
    private List<Integer> diasBloqueados = List.of(6, 7); // 6=sábado, 7=domingo

    // getters e setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public TipoFrete getTipoFrete() { return tipoFrete; }
    public void setTipoFrete(TipoFrete tipoFrete) { this.tipoFrete = tipoFrete; }
    public String getCepBase() { return cepBase; }
    public void setCepBase(String cepBase) { this.cepBase = cepBase; }
    public BigDecimal getValorFixo() { return valorFixo; }
    public void setValorFixo(BigDecimal valorFixo) { this.valorFixo = valorFixo; }
    public List<FaixaFrete> getFaixas() { return faixas; }
    public void setFaixas(List<FaixaFrete> faixas) { this.faixas = faixas; }
    public LocalTime getLimiteHoraCompra() { return limiteHoraCompra; }
    public void setLimiteHoraCompra(LocalTime limiteHoraCompra) { this.limiteHoraCompra = limiteHoraCompra; }
    public List<Integer> getDiasBloqueados() { return diasBloqueados; }
    public void setDiasBloqueados(List<Integer> diasBloqueados) { this.diasBloqueados = diasBloqueados; }

    public enum TipoFrete {
        GRATIS, FIXO
    }

    @Embeddable
    public static class FaixaFrete {
        private int kmMin;
        private int kmMax;
        private int diasEntrega;
        private BigDecimal valor; // se null, usa frete dinâmico baseado em dias

        // getters e setters
        public int getKmMin() { return kmMin; }
        public void setKmMin(int kmMin) { this.kmMin = kmMin; }
        public int getKmMax() { return kmMax; }
        public void setKmMax(int kmMax) { this.kmMax = kmMax; }
        public int getDiasEntrega() { return diasEntrega; }
        public void setDiasEntrega(int diasEntrega) { this.diasEntrega = diasEntrega; }
        public BigDecimal getValor() { return valor; }
        public void setValor(BigDecimal valor) { this.valor = valor; }
    }
}