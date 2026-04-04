package com.fuzzyfilms.ecommerce.repository;

import com.fuzzyfilms.ecommerce.model.Produto;
import com.fuzzyfilms.ecommerce.model.Produto.StatusProduto;
import com.fuzzyfilms.ecommerce.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

public interface ProdutoRepository extends JpaRepository<Produto, Long> {
    List<Produto> findByAtivoTrueAndStatusProdutoOrderByIdDesc(StatusProduto status);
    List<Produto> findAllByAtivoTrueOrderByIdDesc();
     Page<Produto> findByAtivoTrueAndStatusProdutoOrderByIdDesc(StatusProduto status, Pageable pageable);
    List<Produto> findByVendedorAndAtivoTrueOrderByIdDesc(User vendedor);
}
