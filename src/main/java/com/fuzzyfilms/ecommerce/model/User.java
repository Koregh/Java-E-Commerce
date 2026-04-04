package com.fuzzyfilms.ecommerce.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(nullable = false)
    private String senha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Cargo cargo = Cargo.CLIENTE;

    @Column(name = "ativo")
    private Boolean ativo = true;

    @Column(name = "criado_em")
    private LocalDateTime criadoEm = LocalDateTime.now();

    // ── 2FA ────────────────────────────────────────────────────
    @Column(name = "codigo_2fa", length = 6)
    private String codigo2fa;

    @Column(name = "codigo_2fa_expira")
    private LocalDateTime codigo2faExpira;

    @Column(name = "tentativas_2fa")
    private Integer  tentativas2fa = 0;

    @Column(name = "bloqueado_2fa_ate")
    private LocalDateTime bloqueado2faAte;

    // ── Pendentes de confirmação por 2FA ────────────────────────
    @Column(name = "email_pendente", length = 254)
    private String emailPendente;

    @Column(name = "senha_pendente")
    private String senhaPendente;

    @Column(name = "reset_token")
private String resetToken;

@Column(name = "reset_token_expira")
private LocalDateTime resetTokenExpira;

@Column(length = 20)
private String telefone;

    // ── Brute-force login ───────────────────────────────────────
    @Column(name = "tentativas_login")
    private Integer  tentativasLogin = 0;

    @Column(name = "bloqueado_login_ate")
    private LocalDateTime bloqueadoLoginAte;

    
}
