package com.stoloto.balloongame.service;

import com.stoloto.balloongame.config.AdminAuthProperties;
import com.stoloto.balloongame.domain.entity.AdminUserEntity;
import com.stoloto.balloongame.domain.repository.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds the first admin when {@code admin_user} is empty. Password is never logged.
 */
@Slf4j
@Component
@Order(50)
@RequiredArgsConstructor
public class AdminBootstrapJob implements ApplicationRunner {

    private final AdminUserRepository userRepository;
    private final AdminAuthProperties properties;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }
        String username = properties.getBootstrapUsername();
        String password = properties.getBootstrapPassword();
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            log.warn("admin_user is empty and bootstrap credentials are missing — no admin created");
            return;
        }
        AdminUserEntity user = new AdminUserEntity(
                username.trim(),
                passwordEncoder.encode(password),
                username.trim()
        );
        userRepository.save(user);
        log.info("Bootstrapped admin user username={}", user.getUsername());
    }
}
