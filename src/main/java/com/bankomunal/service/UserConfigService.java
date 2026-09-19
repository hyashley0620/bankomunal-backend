package com.bankomunal.service;

import com.bankomunal.dto.request.UserConfigRequest;
import com.bankomunal.dto.response.UserConfigResponse;
import com.bankomunal.entity.NotificationPreference;
import com.bankomunal.entity.User;
import com.bankomunal.repository.NotificationPreferenceRepository;
import com.bankomunal.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserConfigService {

    private final NotificationPreferenceRepository prefRepo;
    private final UserRepository userRepository;

    /**
     * Obtiene las preferencias actuales del usuario.
     */
    @Transactional
    public UserConfigResponse getConfig(User user) {
        NotificationPreference pref = getOrCreate(user);
        return toResponse(pref, null, user);
    }

    @Transactional
    public UserConfigResponse updateConfig(UserConfigRequest req, User user) {
        NotificationPreference pref = getOrCreate(user);

        if (req.getNotifEmail() != null)
            pref.setNotifEmail(req.getNotifEmail());
        if (req.getNotifPush() != null)
            pref.setNotifPush(req.getNotifPush());
        if (req.getAlertaSaldo() != null)
            pref.setAlertaSaldo(req.getAlertaSaldo());

        if (req.getNotifWhatsapp() != null)
            pref.setNotifSms(req.getNotifWhatsapp());
        if (req.getNotifSms() != null)
            pref.setNotifSms(req.getNotifSms());

        prefRepo.save(pref);

        boolean userChanged = false;
        if (req.getMfaEnabled() != null) {
            user.setMfaEnabled(Boolean.TRUE.equals(req.getMfaEnabled()));
            userChanged = true;
        }
        if (req.getIdioma() != null && ("es".equals(req.getIdioma()) || "en".equals(req.getIdioma()))) {
            user.setIdioma(req.getIdioma());
            userChanged = true;
        }
        if (userChanged)
            userRepository.save(user);

        return toResponse(pref, "Configuración guardada correctamente.", user);
    }

    /* ── Helpers ─────────────────────────────────────────────────────────── */

    /**
     * Devuelve las preferencias del usuario, creándolas si no existen.
     * Garantiza que siempre haya un registro en notification_preferences.
     */
    private NotificationPreference getOrCreate(User user) {
        return prefRepo.findByUserId(user.getId())
                .orElseGet(() -> {
                    NotificationPreference nueva = NotificationPreference.builder()
                            .user(user)
                            .notifEmail(true)
                            .notifPush(true)
                            .notifSms(false)
                            .alertaSaldo(true)
                            .build();
                    return prefRepo.save(nueva);
                });
    }

    private UserConfigResponse toResponse(NotificationPreference pref, String mensaje, User user) {
        return UserConfigResponse.builder()
                .notifEmail(pref.getNotifEmail())
                .notifPush(pref.getNotifPush())
                .notifSms(pref.getNotifSms())
                .alertaSaldo(pref.getAlertaSaldo())
                .idioma(user.getIdioma())
                .mensaje(mensaje != null ? mensaje : "OK")
                .mfaEnabled(user.isMfaEnabled())
                .lastPasswordChange(user.getPasswordChangedAt())
                .build();
    }
}
