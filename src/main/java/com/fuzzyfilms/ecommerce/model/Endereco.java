    package com.fuzzyfilms.ecommerce.model;

    import jakarta.persistence.*;
    import lombok.Data;
    import java.time.LocalDateTime;

    @Data
    @Entity
    @Table(name = "enderecos")
    public class Endereco {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @OneToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "user_id", nullable = false, unique = true)
        private User user;

        /** Logradouro: Rua, Av., etc. */
        @Column(nullable = false, length = 200)
        private String endereco;

        @Column(nullable = false, length = 9)   // "00000-000"
        private String cep;

        @Column(nullable = false)
        private String numero;

          @Column(name = "telefone", length = 20)
private String telefone;

        @Column(length = 100)
        private String complemento;   // opcional

        @Column(nullable = false, length = 100)
        private String cidade;

        @Column(nullable = false, length = 2)
        private String estado;        // UF, ex: "SP"

        /**
         * CPF único por conta — armazenado com hash (BCrypt).
         * Exibir sempre mascarado: ***.***.123-45
         */
        @Column(nullable = false)
        private String cpfHash;

        /** Sufixo visível para exibição: últimos 6 chars do CPF limpo (ex.: "12345"). */
        @Column(nullable = false, length = 6)
        private String cpfSufixo;     // "123-45" → sufixo dos 5 últimos dígitos

        /** Horário de entrega preferido, ex: "08:00–12:00" ou "14:00–18:00". */
        @Column(nullable = false, length = 30)
        private String horarioEntrega;

        @Column(name = "criado_em")
        private LocalDateTime criadoEm = LocalDateTime.now();

        @Column(name = "atualizado_em")
        private LocalDateTime atualizadoEm = LocalDateTime.now();

        // ─── helpers ────────────────────────────────────────────────────

        /** Retorna CPF mascarado para exibição: ***.***.XXX-XX */
        public String getCpfMascarado() {
            // cpfSufixo guarda os últimos 5 dígitos sem formatação, ex "12345"
            if (cpfSufixo == null || cpfSufixo.length() < 5) return "***.***.***-**";
            String d = cpfSufixo; // "12345"
            return "***.***."+d.substring(0,3)+"-"+d.substring(3);
        }
    }