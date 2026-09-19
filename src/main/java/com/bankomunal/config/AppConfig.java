package com.bankomunal.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * AppConfig — Bean central del PasswordEncoder.
 * Al tenerlo aquí como @Primary se evita cualquier conflicto
 * de múltiples beans PasswordEncoder en el contexto de Spring.
 */
@Configuration
public class AppConfig {

    @Bean
    @Primary
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}
