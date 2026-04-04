package com.fuzzyfilms.ecommerce.repository;

import com.fuzzyfilms.ecommerce.model.AuditLog;
import com.fuzzyfilms.ecommerce.model.AuditLog.TipoAcao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findAllByOrderByCriadoEmDesc();
     @Query("SELECT a FROM AuditLog a WHERE a.tipo IN :tipos ORDER BY a.criadoEm DESC")
    List<AuditLog> findByTipoAcaoInOrderByCriadoEmDesc(@Param("tipos") List<AuditLog.TipoAcao> tipos);
    List<AuditLog> findByUsuarioEmailOrderByCriadoEmDesc(String email);
      Page<AuditLog> findAllByOrderByCriadoEmDesc(Pageable pageable);

        @Query("SELECT a FROM AuditLog a WHERE a.tipo IN :tipos ORDER BY a.criadoEm DESC")
    Page<AuditLog> findByTipoAcaoInOrderByCriadoEmDesc(@Param("tipos") List<TipoAcao> tipos, Pageable pageable);
}
