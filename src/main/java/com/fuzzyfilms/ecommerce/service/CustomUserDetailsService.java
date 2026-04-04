package com.fuzzyfilms.ecommerce.service;

import com.fuzzyfilms.ecommerce.model.User;
import com.fuzzyfilms.ecommerce.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepository repo;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User u = repo.findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado: " + email));
        return new org.springframework.security.core.userdetails.User(
            u.getEmail(), u.getSenha(),
            List.of(() -> "ROLE_" + u.getCargo().name())
        );
    }
}
