package com.fuzzyfilms.ecommerce.repository;

import com.fuzzyfilms.ecommerce.model.Cargo;
import com.fuzzyfilms.ecommerce.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    List<User> findAllByOrderByNomeAsc();
    List<User> findByCargoOrderByNomeAsc(Cargo cargo);
    Page<User> findAllByOrderByNomeAsc(Pageable pageable);
}
