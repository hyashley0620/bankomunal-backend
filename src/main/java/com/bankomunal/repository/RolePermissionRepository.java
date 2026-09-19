package com.bankomunal.repository;

import com.bankomunal.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {
    List<RolePermission> findByRoleId(Long roleId);

    Optional<RolePermission> findByRoleIdAndModulo(Long roleId, String modulo);

}
