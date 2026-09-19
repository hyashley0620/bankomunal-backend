package com.bankomunal.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

import java.nio.file.Paths;

@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        /*
         * IMPORTANTE: Spring exige que las rutas de resourceLocations terminen con "/".
         * Paths.toUri().toString() NO agrega la barra si el directorio no existe
         * en el momento del arranque. Por eso se construye la URI manualmente
         * asegurando el "/" final.
         *
         * Dos handlers:
         * GET /uploads/** → <workDir>/uploads/
         * GET /fotos/** → <workDir>/uploads/fotos/
         */
        String workDir = Paths.get("").toAbsolutePath().toString()
                .replace("\\", "/");
        // Asegurar que no termine en /
        if (workDir.endsWith("/"))
            workDir = workDir.substring(0, workDir.length() - 1);

        String uploadsLocation = "file:///" + workDir + "/uploads/";
        String fotosLegacyLocation = "file:///" + workDir + "/uploads/fotos/";

        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadsLocation);

        // Compatibilidad con selfie_path antiguas tipo "fotos/foto_2_xxx.jpg"
        // que el navegador solicita como GET /fotos/foto_2_xxx.jpg
        registry.addResourceHandler("/fotos/**")
                .addResourceLocations(fotosLegacyLocation);
    }
}