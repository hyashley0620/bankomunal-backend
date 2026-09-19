package com.bankomunal.security;

import com.bankomunal.entity.Role;
import com.bankomunal.entity.RolePermission;
import com.bankomunal.entity.User;
import com.bankomunal.repository.RolePermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Punto único donde se hacen cumplir los permisos granulares
 * (leer/crear/editar/borrar) por módulo que el admin configura en la
 * pantalla "Roles y Permisos".
 *
 * admin conserva bypass total — mismo criterio que ya usan
 * AuthService/AdminService al armar la respuesta de la pantalla de permisos.
 * Si el rol no tiene fila guardada para ese módulo, se deniega (más seguro
 * que asumir acceso, mismo criterio que AdminService#getPermisosDeRol).
 */
@Component("permisos")
@RequiredArgsConstructor
public class PermisoService {

    private final RolePermissionRepository rolePermissionRepository;

    /**
     * @param modulo uno de: usuarios | gestion-prestamos | reportes-financieros
     *               | auditoria | roles-permisos | respaldo-recuperacion
     * @param accion uno de: leer | crear | editar | borrar
     */
    public boolean tiene(String modulo, String accion) {
        User user = usuarioActual();
        if (user == null || user.getRoles() == null) {
            return false;
        }

        for (Role rol : user.getRoles()) {
            if ("admin".equalsIgnoreCase(rol.getName())) {
                return true;
            }
            RolePermission permiso = rolePermissionRepository
                    .findByRoleIdAndModulo(rol.getId(), modulo)
                    .orElse(null);
            if (permiso != null && aplicaAccion(permiso, accion)) {
                return true;
            }
        }
        return false;
    }

    private boolean aplicaAccion(RolePermission permiso, String accion) {
        return switch (accion) {
            case "leer" -> permiso.isLeer();
            case "crear" -> permiso.isCrear();
            case "editar" -> permiso.isEditar();
            case "borrar" -> permiso.isBorrar();
            default -> false;
        };
    }

    private User usuarioActual() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            return null;
        }
        return user;
    }
}
