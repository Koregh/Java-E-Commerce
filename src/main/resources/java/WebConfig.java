package com.fuzzyfilms.ecommerce.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Serve imagens uploadadas de fora do classpath
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:uploads/");
    }
}
