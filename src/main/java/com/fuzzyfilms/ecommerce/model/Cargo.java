package com.fuzzyfilms.ecommerce.model;

public enum Cargo {
    CLIENTE,
    ADMINISTRADOR,  // pode postar/editar produtos, acesso ao painel de logs
    GERENTE         // tudo do ADMIN + pode promover/rebaixar usuários
}
