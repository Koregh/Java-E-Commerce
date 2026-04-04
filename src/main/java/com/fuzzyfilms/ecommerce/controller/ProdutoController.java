    package com.fuzzyfilms.ecommerce.controller;

    import com.fuzzyfilms.ecommerce.model.*;
    import com.fuzzyfilms.ecommerce.model.AuditLog.TipoAcao;
    import com.fuzzyfilms.ecommerce.model.Produto.StatusProduto;
    import com.fuzzyfilms.ecommerce.repository.*;
    import com.fuzzyfilms.ecommerce.service.AuditService;
    import jakarta.servlet.http.HttpServletRequest;
    import org.springframework.beans.factory.annotation.Autowired;
    import org.springframework.beans.factory.annotation.Value;
    import org.springframework.data.domain.PageRequest;
    import org.springframework.security.core.annotation.AuthenticationPrincipal;
    import org.springframework.security.core.userdetails.UserDetails;
    import org.springframework.stereotype.Controller;
    import org.springframework.ui.Model;
    import org.springframework.web.bind.annotation.*;
    import org.springframework.web.multipart.MultipartFile;
    import org.springframework.web.servlet.mvc.support.RedirectAttributes;

    import javax.imageio.ImageIO;
    import java.awt.image.BufferedImage;
    import java.io.IOException;
    import org.springframework.data.domain.Page;
    import org.springframework.data.domain.PageRequest;
    import org.springframework.data.domain.Pageable;
    import java.math.BigDecimal;
    import java.nio.file.*;
    import java.util.List;
    import java.util.UUID;

    @Controller
    public class ProdutoController {

        @Autowired private ProdutoRepository produtoRepo;
        @Autowired private UserRepository userRepo;
        @Autowired private AuditService auditService;
        @Autowired private FreteConfigRepository freteConfigRepo;
        

        // Diretório de upload (absoluto, dentro da pasta do projeto)
        @Value("${upload.dir:uploads/images/}")
        private String uploadDir;

        // ─── Listagem home (loja pública) ─────────────────────────────────
    @GetMapping("/")
    public String home(@RequestParam(defaultValue = "0") int page,
                    @RequestParam(defaultValue = "20") int size,
                    Model model,
                    @AuthenticationPrincipal UserDetails ud) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Produto> pagina = produtoRepo.findByAtivoTrueAndStatusProdutoOrderByIdDesc(StatusProduto.ATIVO, pageable);
        
        freteConfigRepo.findById(1L).ifPresent(config -> model.addAttribute("freteConfig", config));
        
        model.addAttribute("produtos", pagina.getContent());
        model.addAttribute("paginaAtual", page);
        model.addAttribute("totalPaginas", pagina.getTotalPages());
        

        

        if (ud != null) {
            userRepo.findByEmail(ud.getUsername()).ifPresent(user -> {
                model.addAttribute("usuario", user);
                model.addAttribute("usuario_nome", user.getNome());
                model.addAttribute("cargo", user.getCargo().name());
                model.addAttribute("isAdminOuGerente",
                    user.getCargo() == Cargo.ADMINISTRADOR || user.getCargo() == Cargo.GERENTE);
            });
        }
        return "index";
    }
        // ─── Detalhe produto ─────────────────────────────────────────────
        @GetMapping("/produto/{id}")
        public String detalhe(@PathVariable Long id, Model model,
                            @AuthenticationPrincipal UserDetails ud) {
            Produto p = produtoRepo.findById(id)
                    .filter(Produto::isAtivo)
                    .orElse(null);
            if (p == null) return "redirect:/";

            FreteConfig freteConfig = freteConfigRepo.findById(1L).orElse(new FreteConfig());
            model.addAttribute("freteConfig", freteConfig);
            model.addAttribute("produto", p);
            model.addAttribute("statusProdutos", StatusProduto.values());

            if (ud != null) {
                userRepo.findByEmail(ud.getUsername()).ifPresent(u -> {
                    model.addAttribute("usuario", u);
                    model.addAttribute("usuario_nome", u.getNome());
                    model.addAttribute("isAdminOuGerente",
                            u.getCargo() == Cargo.ADMINISTRADOR || u.getCargo() == Cargo.GERENTE);
                });
            }
            return "produto_detalhe";
        }

        // ─── Formulário adicionar ────────────────────────────────────────
        @GetMapping("/produto/adicionar")
        public String formAdicionar(Model model) {
            model.addAttribute("produto", null);
            model.addAttribute("statusProdutos", StatusProduto.values());
            return "produto_form";
        }

        // ─── POST adicionar (com validações completas) ───────────────────
        @PostMapping("/produto/adicionar")
        public String salvarNovo(@RequestParam String nome,
                                @RequestParam(required = false) String descricao,
                                @RequestParam(required = false) BigDecimal preco,
                                @RequestParam(required = false) Integer estoque,
                                @RequestParam(required = false) MultipartFile imagem,
                                @RequestParam(required = false) String tipoFrete,
                                 @RequestParam(required = false) BigDecimal valorFrete,
                                @RequestParam(required = false, defaultValue = "false") boolean aceitaEntregaUrgente,
                                @RequestParam(required = false, defaultValue = "false") boolean aceitaAgendamento,
                                @RequestParam(required = false) String horariosEntrega,
                                @AuthenticationPrincipal UserDetails ud,
                                HttpServletRequest req,
                                RedirectAttributes ra) throws IOException {

            User vendedor = userRepo.findByEmail(ud.getUsername()).orElseThrow();

            // Validação nome
            if (nome == null || nome.trim().length() < 2) {
                ra.addFlashAttribute("erro", "Nome do produto deve ter pelo menos 2 caracteres.");
                return "redirect:/produto/adicionar";
            }

            // Validação preço
            if (preco == null || preco.compareTo(BigDecimal.ZERO) <= 0) {
                ra.addFlashAttribute("erro", "Preço deve ser um número maior que zero (ex: 19.90).");
                return "redirect:/produto/adicionar";
            }
            if (preco.scale() > 2) {
                ra.addFlashAttribute("erro", "Preço deve ter no máximo 2 casas decimais (ex: 19.90).");
                return "redirect:/produto/adicionar";
            }

            // Validação estoque
            if (estoque == null || estoque < 0) {
                ra.addFlashAttribute("erro", "Estoque deve ser um número inteiro maior ou igual a zero.");
                return "redirect:/produto/adicionar";
            }

            // Validação imagem (obrigatória para novo produto)
            if (imagem == null || imagem.isEmpty()) {
                ra.addFlashAttribute("erro", "Imagem é obrigatória para um novo produto.");
                return "redirect:/produto/adicionar";
            }

            Produto p = new Produto();
            p.setNome(nome.trim());
            p.setDescricao(descricao != null ? descricao.trim() : null);
            p.setPreco(preco);
            p.setEstoque(estoque);
            p.setVendedor(vendedor);
            p.setValorFrete(valorFrete);
            p.setStatusProduto(StatusProduto.ATIVO);
            p.setAtivo(true); // produto ativo (não deletado)

            // Transporte
            if (tipoFrete != null && !tipoFrete.isBlank()) {
                try {
                    p.setTipoFrete(Produto.TipoFreteP.valueOf(tipoFrete));
                } catch (IllegalArgumentException e) {
                    ra.addFlashAttribute("erro", "Tipo de frete inválido.");
                    return "redirect:/produto/adicionar";
                }
            }
            p.setAceitaEntregaUrgente(aceitaEntregaUrgente);
            p.setAceitaAgendamento(aceitaAgendamento);
            p.setHorariosEntrega(horariosEntrega != null && !horariosEntrega.isBlank() ? horariosEntrega.trim() : null);

            // Salvar imagem
        // Salvar imagem
    try {
        String nomeArquivo = salvarImagem(imagem);
        if (nomeArquivo == null) {
            ra.addFlashAttribute("erro", "Formato de imagem inválido. Use PNG, JPG ou WEBP.");
            return "redirect:/produto/adicionar";
        }
        p.setImagem(nomeArquivo);
    } catch (RuntimeException e) {
        ra.addFlashAttribute("erro", e.getMessage()); // ex: "A imagem deve ser quadrada..."
        return "redirect:/produto/adicionar";
    }

            produtoRepo.save(p);
            auditService.log(vendedor, TipoAcao.PRODUTO_CRIADO,
                    "Produto criado: " + p.getNome() + " (ID " + p.getId() + ")", req.getRemoteAddr());

            ra.addFlashAttribute("sucesso", "Produto adicionado com sucesso!");
            return "redirect:/dashboard";
        }

        // ─── Formulário editar ───────────────────────────────────────────
        @GetMapping("/produto/editar/{id}")
        public String formEditar(@PathVariable Long id, Model model,
                                @AuthenticationPrincipal UserDetails ud,
                                RedirectAttributes ra) {
            Produto p = produtoRepo.findById(id).orElse(null);
            if (p == null || !p.isAtivo()) {
                ra.addFlashAttribute("erro", "Produto não encontrado.");
                return "redirect:/dashboard";
            }

            User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();
            if (user.getCargo() == Cargo.ADMINISTRADOR &&
                    !p.getVendedor().getId().equals(user.getId())) {
                ra.addFlashAttribute("erro", "Você não tem permissão para editar este produto.");
                return "redirect:/dashboard";
            }

            model.addAttribute("produto", p);
            model.addAttribute("statusProdutos", StatusProduto.values());
            return "produto_form";
        }

        // ─── POST editar (com validações) ─────────────────────────────────
        @PostMapping("/produto/editar/{id}")
        public String salvarEdicao(@PathVariable Long id,
                                @RequestParam String nome,
                                @RequestParam(required = false) String descricao,
                                @RequestParam(required = false) BigDecimal preco,
                                @RequestParam(required = false) Integer estoque,
                                @RequestParam(required = false) MultipartFile imagem,
                                @RequestParam(required = false) String tipoFrete,
                                  @RequestParam(required = false) BigDecimal valorFrete,
                                @RequestParam(required = false, defaultValue = "false") boolean aceitaEntregaUrgente,
                                @RequestParam(required = false, defaultValue = "false") boolean aceitaAgendamento,
                                @RequestParam(required = false) String horariosEntrega,
                                @AuthenticationPrincipal UserDetails ud,
                                HttpServletRequest req,
                                RedirectAttributes ra) throws IOException {

            Produto p = produtoRepo.findById(id).orElse(null);
            if (p == null || !p.isAtivo()) {
                ra.addFlashAttribute("erro", "Produto não encontrado.");
                return "redirect:/dashboard";
            }

            User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();
            if (user.getCargo() == Cargo.ADMINISTRADOR &&
                    !p.getVendedor().getId().equals(user.getId())) {
                ra.addFlashAttribute("erro", "Sem permissão para editar este produto.");
                return "redirect:/dashboard";
            }

            // Validações
            if (nome == null || nome.trim().length() < 2) {
                ra.addFlashAttribute("erro", "Nome do produto deve ter pelo menos 2 caracteres.");
                return "redirect:/produto/editar/" + id;
            }
            if (preco == null || preco.compareTo(BigDecimal.ZERO) <= 0) {
                ra.addFlashAttribute("erro", "Preço deve ser um número maior que zero (ex: 19.90).");
                return "redirect:/produto/editar/" + id;
            }
            if (preco.scale() > 2) {
                ra.addFlashAttribute("erro", "Preço deve ter no máximo 2 casas decimais.");
                return "redirect:/produto/editar/" + id;
            }
            if (estoque == null || estoque < 0) {
                ra.addFlashAttribute("erro", "Estoque deve ser um número inteiro maior ou igual a zero.");
                return "redirect:/produto/editar/" + id;
            }

            p.setNome(nome.trim());
            p.setDescricao(descricao != null ? descricao.trim() : null);
            p.setPreco(preco);
            p.setEstoque(estoque);
               p.setValorFrete(valorFrete);

            // Transporte
            if (tipoFrete != null && !tipoFrete.isBlank()) {
                try {
                    p.setTipoFrete(Produto.TipoFreteP.valueOf(tipoFrete));
                } catch (IllegalArgumentException e) {
                    ra.addFlashAttribute("erro", "Tipo de frete inválido.");
                    return "redirect:/produto/editar/" + id;
                }
            } else {
                p.setTipoFrete(null);
            }
            p.setAceitaEntregaUrgente(aceitaEntregaUrgente);
            p.setAceitaAgendamento(aceitaAgendamento);
            p.setHorariosEntrega(horariosEntrega != null && !horariosEntrega.isBlank() ? horariosEntrega.trim() : null);

        // Imagem (opcional na edição)
    if (imagem != null && !imagem.isEmpty()) {
        try {
            String nomeArquivo = salvarImagem(imagem);
            if (nomeArquivo == null) {
                ra.addFlashAttribute("erro", "Formato de imagem inválido. Use PNG, JPG ou WEBP.");
                return "redirect:/produto/editar/" + id;
            }
            p.setImagem(nomeArquivo);
        } catch (RuntimeException e) {
            ra.addFlashAttribute("erro", e.getMessage());
            return "redirect:/produto/editar/" + id;
        }
    }

            produtoRepo.save(p);
            auditService.log(user, TipoAcao.PRODUTO_EDITADO,
                    "Produto editado: " + p.getNome() + " (ID " + p.getId() + ")", req.getRemoteAddr());

            ra.addFlashAttribute("sucesso", "Produto atualizado com sucesso!");
            return "redirect:/dashboard";
        }

        // ─── POST alterar status produto (não altera 'ativo') ────────────
        @PostMapping("/produto/{id}/status")
        public String alterarStatus(@PathVariable Long id,
                                    @RequestParam StatusProduto statusProduto,
                                    @AuthenticationPrincipal UserDetails ud,
                                    HttpServletRequest req,
                                    RedirectAttributes ra) {
            User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();
            if (user.getCargo() == Cargo.CLIENTE) return "redirect:/";

            Produto p = produtoRepo.findById(id).orElse(null);
            if (p == null) {
                ra.addFlashAttribute("erro", "Produto não encontrado.");
                return "redirect:/dashboard";
            }
            if (user.getCargo() == Cargo.ADMINISTRADOR &&
                    !p.getVendedor().getId().equals(user.getId())) {
                ra.addFlashAttribute("erro", "Sem permissão para alterar status deste produto.");
                return "redirect:/dashboard";
            }

            StatusProduto anterior = p.getStatusProduto();
            p.setStatusProduto(statusProduto);
            // NÃO ALTERA o campo 'ativo' aqui! Isso só acontece no delete.
            produtoRepo.save(p);

            auditService.log(user, TipoAcao.PRODUTO_STATUS_ALTERADO,
                    "Status do produto \"" + p.getNome() + "\" (ID " + p.getId() + "): " +
                            anterior + " → " + statusProduto, req.getRemoteAddr());

            ra.addFlashAttribute("sucesso", "Status do produto alterado para: " + statusProduto.name());
            return "redirect:/dashboard";
        }

        // ─── Deletar produto (exclusão lógica) ───────────────────────────
        @GetMapping("/produto/deletar/{id}")
        public String deletar(@PathVariable Long id,
                            @AuthenticationPrincipal UserDetails ud,
                            HttpServletRequest req,
                            RedirectAttributes ra) {
            Produto p = produtoRepo.findById(id).orElse(null);
            if (p == null || !p.isAtivo()) {
                ra.addFlashAttribute("erro", "Produto não encontrado.");
                return "redirect:/dashboard";
            }

            User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();
            if (user.getCargo() == Cargo.ADMINISTRADOR &&
                    !p.getVendedor().getId().equals(user.getId())) {
                ra.addFlashAttribute("erro", "Sem permissão para deletar este produto.");
                return "redirect:/dashboard";
            }

            p.setAtivo(false);
            p.setStatusProduto(StatusProduto.INATIVO);
            produtoRepo.save(p);
            auditService.log(user, TipoAcao.PRODUTO_DELETADO,
                    "Produto deletado: " + p.getNome() + " (ID " + p.getId() + ")", req.getRemoteAddr());

            ra.addFlashAttribute("sucesso", "Produto removido.");
            return "redirect:/dashboard";
        }

        // ─── Dashboard (lista produtos ativos – não deletados) ────────────
        @GetMapping("/dashboard")
        public String dashboard(Model model, @AuthenticationPrincipal UserDetails ud) {
            User user = userRepo.findByEmail(ud.getUsername()).orElseThrow();

            List<Produto> produtos;
            if (user.getCargo() == Cargo.GERENTE) {
                produtos = produtoRepo.findAllByAtivoTrueOrderByIdDesc();
            } else {
                produtos = produtoRepo.findByVendedorAndAtivoTrueOrderByIdDesc(user);
            }

            model.addAttribute("usuario", user);
            model.addAttribute("usuario_nome", user.getNome());
            model.addAttribute("cargo", user.getCargo().name());
            model.addAttribute("produtos", produtos);
            model.addAttribute("statusProdutos", StatusProduto.values());
            return "dashboard";
        }

        private String salvarImagem(MultipartFile file) throws IOException {
        // Validação 1: formato da extensão
        String original = file.getOriginalFilename();
        if (original == null) return null;
        String ext = original.toLowerCase();
        if (!ext.endsWith(".png") && !ext.endsWith(".jpg") &&
            !ext.endsWith(".jpeg") && !ext.endsWith(".webp")) return null;

        // Validação 2: tamanho máximo (5MB)
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new RuntimeException("A imagem excede 5MB.");
        }

        // Validação 3: imagem é quadrada? (largura == altura)
        BufferedImage imagem = ImageIO.read(file.getInputStream());
        if (imagem == null) {
            throw new RuntimeException("Arquivo não é uma imagem válida.");
        }
        int width = imagem.getWidth();
        int height = imagem.getHeight();
        if (width != height) {
            throw new RuntimeException("A imagem deve ser quadrada (largura = altura).");
        }

        // Se passou por todas, salva
        Path rootDir = Paths.get(System.getProperty("user.dir")).resolve(uploadDir);
        Files.createDirectories(rootDir);
        String nomeArquivo = UUID.randomUUID() + "_" + original.replaceAll("[^a-zA-Z0-9._-]", "_");
        Path destino = rootDir.resolve(nomeArquivo);
        file.transferTo(destino.toFile());
        return nomeArquivo;
    }
    }