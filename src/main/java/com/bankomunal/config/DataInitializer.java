package com.bankomunal.config;

import com.bankomunal.entity.*;
import com.bankomunal.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.Set;
import java.util.HashSet;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(1) // debe correr antes que RolePermissionSeeder (necesita que los roles ya existan)
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        Role adminRole = ensureRole("admin", "Administrador del sistema");
        Role socioRole = ensureRole("socio", "Socio activo de la comunidad");
        ensureRole("tesorero", "Tesorero del grupo");
        ensureRole("secretario", "Secretario del grupo");
        ensureRole("auditor", "Auditor con acceso de solo lectura");

        if (!userRepository.existsByEmail("admin@bankomunal.com")) {
            User admin = userRepository.save(User.builder()
                    .firstName("Admin").lastName("Sistema")
                    .email("admin@bankomunal.com")
                    .identificationNumber("000000001")
                    .passwordHash(passwordEncoder.encode("Admin2026*"))
                    .status(User.UserStatus.active)
                    .autorizaDatos(true).roles(new HashSet<>(Set.of(adminRole))).build());
            accountRepository.save(Account.builder()
                    .accountCode("BKM-ADMIN-001").accountType(Account.AccountType.individual)
                    .ownerUser(admin).balance(BigDecimal.ZERO).build());
            log.info(" admin@bankomunal.com / Admin2026*");
        }

        if (!userRepository.existsByEmail("carlos@test.com")) {
            User carlos = userRepository.save(User.builder()
                    .firstName("Carlos").lastName("Ramírez")
                    .email("carlos@test.com")
                    .identificationNumber("1001234567")
                    .passwordHash(passwordEncoder.encode("Carlos2026*"))
                    .status(User.UserStatus.active)
                    .autorizaDatos(true).roles(new HashSet<>(Set.of(socioRole))).build());
            accountRepository.save(Account.builder()
                    .accountCode("BKM-CARLOS-001").accountType(Account.AccountType.individual)
                    .ownerUser(carlos).balance(new BigDecimal("500000.00")).build());
            log.info(" carlos@test.com / Carlos2026*");
        }

        if (!userRepository.existsByEmail("laura@test.com")) {
            User laura = userRepository.save(User.builder()
                    .firstName("Laura").lastName("Gómez")
                    .email("laura@test.com")
                    .identificationNumber("1009876543")
                    .passwordHash(passwordEncoder.encode("Laura2026*"))
                    .status(User.UserStatus.active)
                    .autorizaDatos(true).roles(new HashSet<>(Set.of(socioRole))).build());
            accountRepository.save(Account.builder()
                    .accountCode("BKM-LAURA-001").accountType(Account.AccountType.individual)
                    .ownerUser(laura).balance(new BigDecimal("250000.00")).build());
            log.info(" laura@test.com / Laura2026*");
        }

        if (!userRepository.existsByEmail("pedro@test.com")) {
            User pedro = userRepository.save(User.builder()
                    .firstName("Pedro").lastName("Mora")
                    .email("pedro@test.com")
                    .identificationNumber("1005555555")
                    .passwordHash(passwordEncoder.encode("Pedro2026*"))
                    .status(User.UserStatus.active)
                    .autorizaDatos(true).roles(new HashSet<>(Set.of(socioRole))).build());
            accountRepository.save(Account.builder()
                    .accountCode("BKM-PEDRO-001").accountType(Account.AccountType.individual)
                    .ownerUser(pedro).balance(new BigDecimal("450000.00")).build());
            log.info(" pedro@test.com / Pedro2026*");
        }

        log.info(" DataInitializer completado.");
    }

    private Role ensureRole(String name, String description) {
        return roleRepository.findByName(name)
                .orElseGet(() -> roleRepository.save(Role.builder().name(name).description(description).build()));
    }
}
