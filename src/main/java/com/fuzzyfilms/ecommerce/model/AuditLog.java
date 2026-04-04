package com.fuzzyfilms.ecommerce.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)   // agora mapeia para "tipo"
    private TipoAcao tipo;                    // renomeado de tipoAcao para tipo

    @Column(columnDefinition = "TEXT")
    private String descricao;

    private String usuarioEmail;
    private String ip;
    private LocalDateTime criadoEm = LocalDateTime.now();

    public enum TipoAcao {
        CADASTRO, LOGIN, LOGOUT, MUDOU_EMAIL, MUDOU_SENHA, MUDOU_ENDERECO,
        EXCLUIU_CONTA, PRODUTO_CRIADO, PRODUTO_EDITADO, PRODUTO_DELETADO,
        PRODUTO_STATUS_ALTERADO, PEDIDO_CRIADO, PEDIDO_STATUS_ALTERADO,
        USUARIO_BLOQUEADO, TENTATIVA_2FA_FALHA, ACESSO_NEGADO, CARGO_ALTERADO,
        EXPORTOU_DADOS, ATUALIZOU_ENDERECO
    }

    // Getters e Setters
    public Long getId() { return id; }
    public TipoAcao getTipo() { return tipo; }
    public void setTipo(TipoAcao tipo) { this.tipo = tipo; }
    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
    public String getUsuarioEmail() { return usuarioEmail; }
    public void setUsuarioEmail(String usuarioEmail) { this.usuarioEmail = usuarioEmail; }
    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
}