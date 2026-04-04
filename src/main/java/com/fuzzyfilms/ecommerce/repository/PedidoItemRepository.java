package com.fuzzyfilms.ecommerce.repository;

import com.fuzzyfilms.ecommerce.model.PedidoItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PedidoItemRepository extends JpaRepository<PedidoItem, Long> {
}