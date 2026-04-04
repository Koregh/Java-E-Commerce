package com.fuzzyfilms.ecommerce.repository;

import com.fuzzyfilms.ecommerce.model.Pedido;
import com.fuzzyfilms.ecommerce.model.Pedido.StatusPedido;
import com.fuzzyfilms.ecommerce.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {
    List<Pedido> findByCompradorOrderByCriadoEmDesc(User comprador);
    List<Pedido> findAllByOrderByCriadoEmDesc();
    List<Pedido> findByStatusOrderByCriadoEmDesc(Pedido.StatusPedido status);
    Page<Pedido> findByCompradorOrderByCriadoEmDesc(User comprador, Pageable pageable);
     Page<Pedido> findAllByOrderByCriadoEmDesc(Pageable pageable);
    Page<Pedido> findByStatusOrderByCriadoEmDesc(StatusPedido status, Pageable pageable);
    
}