package com.bankomunal.config;

import com.bankomunal.entity.Role;
import com.bankomunal.entity.RolePermission;
import com.bankomunal.repository.RolePermissionRepository;
import com.bankomunal.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Siembra permisos por defecto para los roles base (admin, tesorero,
 * secretario, auditor) la primera vez que arranca el backend, para que la
 * pantalla de Roles y Permisos no aparezca vacía y el sidebar tenga algo
 * sensato que mostrarle a cada rol desde el primer login. El admin puede
 * ajustarlos después desde roles-permisos.html — este seeder NUNCA sobre-
 * escribe permisos ya guardados, solo llena los que falten.
 *
 * Diseño de acceso por rol (community-banking típico):
 * - tesorero: gestiona préstamos (aprobar/rechazar/registrar pagos) y
 *   consulta reportes financieros.
 * - secretario: gestiona el registro de socios (ver, registrar nuevos) y
 *   consulta reportes.
 * - auditor: solo lectura — auditoría y reportes, nada de escritura en
 *   ningún módulo.
 * - admin: acceso total a los 6 módulos administrativos.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(2) // corre despues de DataInitializer, que crea los roles base
public class RolePermissionSeeder implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;

    @Override
    @Transactional
    public void run(String... args) {
        sembrar("admin", Map.of(
                "usuarios", perm(true, true, true, true),
                "gestion-prestamos", perm(true, true, true, true),
                "reportes-financieros", perm(true, true, true, true),
                "auditoria", perm(true, true, true, true),
                "roles-permisos", perm(true, true, true, true),
                "respaldo-recuperacion", perm(true, true, true, true)));

        sembrar("tesorero", Map.of(
                "gestion-prestamos", perm(true, true, true, false),
                "reportes-financieros", perm(true, false, false, false)));

        sembrar("secretario", Map.of(
                "usuarios", perm(true, true, false, false),
                "reportes-financieros", perm(true, false, false, false)));

        sembrar("auditor", Map.of(
                "auditoria", perm(true, false, false, false),
                "reportes-financieros", perm(true, false, false, false)));

        // "socio" queda intencionalmente sin filas: no tiene acceso a
        // ningún módulo administrativo (comportamiento por defecto seguro).
    }

    private void sembrar(String nombreRol, Map<String, boolean[]> permisosPorModulo) {
        Role rol = roleRepository.findByName(nombreRol).orElse(null);
        if (rol == null) {
            log.warn("RolePermissionSeeder: el rol '{}' no existe todavía, se omite.", nombreRol);
            return;
        }
        List<RolePermission> existentes = rolePermissionRepository.findByRoleId(rol.getId());
        if (!existentes.isEmpty()) {
            return; // ya se sembró antes, o el admin ya lo configuró — no tocar
        }
        permisosPorModulo.forEach((modulo, flags) -> rolePermissionRepository.save(RolePermission.builder()
                .role(rol).modulo(modulo)
                .leer(flags[0]).crear(flags[1]).editar(flags[2]).borrar(flags[3])
                .build()));
        log.info("RolePermissionSeeder: permisos por defecto sembrados para el rol '{}'.", nombreRol);
    }

    private boolean[] perm(boolean leer, boolean crear, boolean editar, boolean borrar) {
        return new boolean[] { leer, crear, editar, borrar };
    }
}