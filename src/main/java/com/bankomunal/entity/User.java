package com.bankomunal.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class User {

    public enum UserStatus {
        pending, active, suspended, blocked, deleted
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;
    @Column(name = "last_name", length = 100)
    private String lastName;
    @Column(nullable = false, unique = true, length = 150)
    private String email;
    @Column(name = "tipo_documento", length = 30)
    private String tipoDocumento;
    @Column(name = "identification_number", length = 50, unique = true)
    private String identificationNumber;
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;
    @Column(length = 30)
    private String phone;
    @Column(length = 20)
    private String genero;
    @Column(name = "fecha_nacimiento")
    private LocalDate fechaNacimiento;
    @Column(length = 255)
    private String direccion;
    @Column(length = 100)
    private String ciudad;
    @Column(length = 100)
    private String departamento;
    @Column(length = 100)
    private String ocupacion;
    @Column
    private Integer creditScore;
    @Column
    private Integer puntos;
    @Column(length = 50)
    private String nivel;

    /** Idioma preferido de la interfaz ('es' | 'en'), editable desde Configuración. */
    @Column(length = 5)
    @Builder.Default
    private String idioma = "es";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private UserStatus status = UserStatus.pending;

    @Column(name = "biometric_consent")
    @Builder.Default
    private Boolean biometricConsent = false;

    @Column(name = "autoriza_datos")
    @Builder.Default
    private Boolean autorizaDatos = false;

    @Column(name = "cedula_frontal_path")
    private String cedulaFrontalPath;
    @Column(name = "cedula_posterior_path")
    private String cedulaPosteriorPath;
    @Column(name = "selfie_path")
    private String selfiePath;

    @Column(name = "failed_login_attempts")
    @Builder.Default
    private Integer failedLoginAttempts = 0;
    @Column(name = "last_failed_login")
    private LocalDateTime lastFailedLogin;
    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;
    @Column(name = "active_token", length = 512)
    private String activeToken;
    /** Si el socio activó verificación en dos pasos (2FA) en Seguridad. */
    @Column(name = "mfa_enabled", nullable = false)
    @Builder.Default
    private boolean mfaEnabled = false;
    /** Código OTP temporal enviado por correo al iniciar sesión con 2FA activo. */
    @Column(name = "mfa_code", length = 20)
    private String mfaCode;
    @Column(name = "mfa_expires_at")
    private LocalDateTime mfaExpiresAt;

    /** fecha del último cambio de contraseña, para seguridad.html */
    @Column(name = "password_changed_at")
    private LocalDateTime passwordChangedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"), inverseJoinColumns = @JoinColumn(name = "role_id"))
    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (autorizaDatos == null)
            autorizaDatos = false;
        if (biometricConsent == null)
            biometricConsent = false;
        if (failedLoginAttempts == null)
            failedLoginAttempts = 0;
        if (puntos == null)
            puntos = 0;
        if (nivel == null)
            nivel = "basico";
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isAutorizaDatos() {
        return autorizaDatos != null && autorizaDatos;
    }

    public boolean isBiometricConsent() {
        return biometricConsent != null && biometricConsent;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts != null ? failedLoginAttempts : 0;
    }

    public void setFailedLoginAttempts(int v) {
        this.failedLoginAttempts = v;
    }
}
