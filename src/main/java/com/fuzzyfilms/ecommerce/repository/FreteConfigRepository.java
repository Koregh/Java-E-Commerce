package com.fuzzyfilms.ecommerce.repository;

import com.fuzzyfilms.ecommerce.model.FreteConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface FreteConfigRepository extends JpaRepository<FreteConfig, Long> {
      @Query("SELECT f FROM FreteConfig f ORDER BY f.id ASC LIMIT 1")
    Optional<FreteConfig> findFirst();
}