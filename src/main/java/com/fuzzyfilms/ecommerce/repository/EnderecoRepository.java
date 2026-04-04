package com.fuzzyfilms.ecommerce.repository;

import com.fuzzyfilms.ecommerce.model.Endereco;
import com.fuzzyfilms.ecommerce.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface EnderecoRepository extends JpaRepository<Endereco, Long> {

    Optional<Endereco> findByUser(User user);

    /** Verifica se algum outro usuário já cadastrou este CPF (sufixo + hash). */
    boolean existsByCpfHash(String cpfHash);

    /** Mesmo CPF mas pertencente a outro user (para edição). */
    boolean existsByCpfHashAndUserNot(String cpfHash, User user);
}