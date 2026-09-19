package com.bankomunal.service;

import com.bankomunal.entity.*;
import com.bankomunal.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SystemConfigService {

    private final SystemConfigRepository configRepository;

    public String getValor(String key) {
        return configRepository.findByConfigKey(key)
                .map(SystemConfig::getConfigValue)
                .orElse(null);
    }

    @Transactional
    public Map<String, Object> actualizar(String key, String valor, User admin) {
        SystemConfig cfg = configRepository.findByConfigKey(key)
                .orElseGet(() -> SystemConfig.builder().configKey(key).build());
        cfg.setConfigValue(valor);
        cfg.setUpdatedBy(admin);
        configRepository.save(cfg);
        return Map.of("key", key, "value", valor, "mensaje", "Configuración actualizada.");
    }
}
