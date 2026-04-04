package com.fuzzyfilms.ecommerce.config;

import com.fuzzyfilms.ecommerce.service.CustomUserDetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;



@Configuration
public class SecurityConfig {

    @Autowired
    private CustomUserDetailsService uds;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
public HttpSessionEventPublisher httpSessionEventPublisher() {
    return new HttpSessionEventPublisher();
}

@Bean
public SessionRegistry sessionRegistry() {
    return new SessionRegistryImpl();
}

    @Bean
    public AuthenticationManager authManager(HttpSecurity http) throws Exception {
        return http.getSharedObject(AuthenticationManagerBuilder.class)
            .userDetailsService(uds)
            .passwordEncoder(passwordEncoder())
            .and()
            .build();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
        
            .authorizeHttpRequests(auth -> auth
               .requestMatchers(
    "/", "/login", "/login/auth", "/login/2fa", "/login/reenviar",
    "/cadastro", "/privacidade", "/termos", "/contato", "/sobre", "/trocas",
    "/esqueci-senha", "/esqueci-senha/verificar", "/esqueci-senha/redefinir",
    "/css/**", "/images/**", "/uploads/**",
    "/produto/**"
).permitAll()
                .requestMatchers("/painel/**")
                    .hasAnyRole("ADMINISTRADOR", "GERENTE")
                .requestMatchers(
                    "/produto/adicionar", "/produto/editar/**",
                    "/produto/deletar/**", "/produto/*/status"
                ).hasAnyRole("ADMINISTRADOR", "GERENTE")
                .requestMatchers("/painel/usuarios/**")
                    .hasRole("GERENTE")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form.disable())
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout=true")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
            )
             .sessionManagement(session -> session
            .maximumSessions(3)
            .sessionRegistry(sessionRegistry())
        )
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new XorCsrfTokenRequestAttributeHandler())
                .ignoringRequestMatchers("/login/auth", "/login/2fa", "/login/reenviar")
            );

        return http.build();
    }
}