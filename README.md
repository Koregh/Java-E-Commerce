# Koregh Store — E-commerce Platform

Sistema de e-commerce completo desenvolvido com **Spring Boot 3**, com foco em segurança aplicada, arquitetura em camadas e boas práticas de desenvolvimento backend.

[![Java](https://img.shields.io/badge/Java-17+-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.4-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-336791?style=for-the-badge&logo=postgresql&logoColor=white)](https://postgresql.org)
[![Spring Security](https://img.shields.io/badge/Spring_Security-6DB33F?style=for-the-badge&logo=springsecurity&logoColor=white)](https://spring.io/projects/spring-security)
[![License](https://img.shields.io/badge/License-MIT-22C55E?style=for-the-badge)](LICENSE)

---

## O que este projeto demonstra

Este projeto foi construído para ir além do CRUD básico. Cada decisão técnica tem uma justificativa de segurança ou arquitetura, documentada no código.

### Segurança aplicada na prática

**Autenticação em duas etapas (2FA)**
O código de 6 dígitos gerado com `SecureRandom` nunca é armazenado em texto puro, vai direto para um hash SHA-256 no banco. A verificação usa `MessageDigest.isEqual()`, que é constant-time e imune a timing attacks. O código expira em 15 minutos e bloqueia o usuário após 5 tentativas erradas por 30 minutos.

**CPF com HMAC-SHA256**
CPFs não ficam em texto puro no banco. São transformados em HMAC-SHA256 com um secret configurável, o que permite verificar unicidade sem expor o dado real — rainbow tables não funcionam sem conhecer a chave.

**CSRF completo via Spring Security 6**
Nenhum endpoint ignora a validação CSRF, inclusive `/login/auth` e `/login/2fa`. Os tokens são enviados pelos templates Thymeleaf e verificados pelo `XorCsrfTokenRequestAttributeHandler`.

**Rate limiting por IP**
Um `OncePerRequestFilter` customizado protege os endpoints de autenticação e cadastro com uma sliding window de 30 requisições/minuto por IP, usando `ConcurrentHashMap` + `ConcurrentLinkedDeque` thread-safe.

**BCrypt com fator de custo 12**
Senhas são hasheadas com `BCryptPasswordEncoder(12)` — custo acima do padrão para resistir a ataques offline com hardware moderno.

**Controle de sessões**
Limite de 3 sessões simultâneas por usuário via `SessionRegistry`. Logout invalida a sessão e apaga o cookie `JSESSIONID`.

**Audit log**
Todas as ações relevantes (login, alteração de cadastro, mudança de cargo, pedidos) são registradas com e-mail do usuário, tipo de ação, descrição e IP de origem.

### Arquitetura

```
src/main/java/com/koreghstore/ecommerce/
├── config/          # SecurityConfig, WebConfig, DotenvLoader
├── controller/      # Camada HTTP — sem lógica de negócio
├── service/         # Regras de negócio: TwoFactorService, FreteService,
│                    # MercadoPagoService, AuditService, EmailService...
├── repository/      # Spring Data JPA — acesso ao banco
├── model/           # Entidades JPA: User, Produto, Pedido, PedidoItem,
│                    # Endereco, FreteConfig, AuditLog
├── filter/          # RateLimitFilter (OncePerRequestFilter)
├── dto/             # ContaDTO e afins
└── util/            # HashUtil — HMAC-SHA256 e comparação constant-time
```

Controllers não contêm lógica de negócio. Services não conhecem HTTP. Repositórios são interfaces Spring Data — separação de responsabilidades real, não decorativa.

### Funcionalidades do sistema

- Cadastro e login com 2FA por e-mail
- Carrinho de compras persistido em sessão
- Checkout com suporte a PIX e cartão via Mercado Pago
- Cálculo de frete por produto (grátis, fixo) com fallback global configurável
- CRUD de produtos com controle de acesso por cargo (ADMINISTRADOR / GERENTE)
- Upload seguro de imagens com nome UUID e validação de extensão
- Gestão de endereços de entrega com validação via CEP
- Painel administrativo: gerenciamento de pedidos, usuários e logs de auditoria
- Fluxo completo de recuperação de senha com código temporário por e-mail
- Verificação 2FA em ações sensíveis (alteração de e-mail, cargo, endereço)

---

## Stack

| Camada | Tecnologia |
|---|---|
| Framework | Spring Boot 3.4 |
| Segurança | Spring Security 6 |
| Persistência | Spring Data JPA + PostgreSQL |
| Templates | Thymeleaf + thymeleaf-extras-springsecurity6 |
| Pagamentos | Mercado Pago SDK Java 2.1 |
| Build | Maven |
| Utilitários | Lombok, dotenv-java |

---

## Instalação

**1. Pré-requisitos:** Java 17+, Maven, PostgreSQL

**2. Clone e configure**
```bash
git clone https://github.com/Koregh/E-Commerce.git
cd ecommerce
cp .env.example .env
# Edite o .env com suas credenciais
```

**3. Variáveis de ambiente (`.env`)**
```dotenv
DB_URL=jdbc:postgresql://localhost:5432/koreghstore
DB_USER=seu_usuario
DB_PASSWORD=sua_senha

SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USER=seu-email@gmail.com
SMTP_PASSWORD=           # Senha de App do Google

CPF_SECRET=              # Mínimo 32 chars — mantém os HMACs seguros

MP_ACCESS_TOKEN=         # Token do Mercado Pago (sandbox para testes)
```

> Para Gmail, gere uma **Senha de App** em [myaccount.google.com/apppasswords](https://myaccount.google.com/apppasswords) com 2FA ativado na conta.

**4. Rode**
```bash
mvn spring-boot:run
```

Acesse em `http://localhost:8080`

---

## Decisões que valem destaque

**Por que SHA-256 no código 2FA e não bcrypt?**
Códigos 2FA têm entropia baixa (6 dígitos = ~20 bits) e bcrypt não ajuda nisso — um atacante com o banco pode testar os 1 milhão de combinações em segundos independentemente do custo do hash. O controle real está no rate limiting e no bloqueio após 5 tentativas, que são feitos na camada de aplicação. SHA-256 com comparação constant-time é suficiente e mais performático para este caso.

**Por que HMAC e não SHA-256 para o CPF?**
SHA-256 puro é determinístico e sem salt — dois usuários com o mesmo CPF teriam o mesmo hash, e rainbow tables para CPFs são viáveis (são apenas 11 dígitos numéricos). HMAC-SHA256 com um secret da aplicação elimina esse vetor: sem a chave, não há ataque offline possível.

**Por que Rate Limiting no filtro e não na camada de serviço?**
O filtro roda antes do DispatcherServlet, bloqueando a requisição antes de qualquer processamento. Colocar na camada de serviço deixaria o stack do Spring inteiro sendo instanciado a cada tentativa de brute force.

---

## Contexto acadêmico

Desenvolvido como projeto A3 da graduação. O objetivo foi aplicar conceitos reais de segurança e arquitetura em um sistema funcional completo, não apenas implementar as funcionalidades mínimas.
