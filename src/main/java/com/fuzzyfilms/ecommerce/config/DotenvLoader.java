package com.fuzzyfilms.ecommerce.config;

import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import io.github.cdimascio.dotenv.Dotenv;

@Component
public class DotenvLoader implements ApplicationListener<ContextRefreshedEvent> {
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        dotenv.entries().forEach(entry -> {
            System.setProperty(entry.getKey(), entry.getValue());
            System.out.println("✅ Carregado: " + entry.getKey() + " = " + entry.getValue());
        });
    }
}