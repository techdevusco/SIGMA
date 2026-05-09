package com.SIGMA.USCO.config;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class EnvLoader implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        String envFilePath = Paths.get(".env").toAbsolutePath().toString();

        if (Files.exists(Paths.get(envFilePath))) {
            Map<String, Object> envVariables = new HashMap<>();

            try (FileInputStream fis = new FileInputStream(envFilePath)) {
                java.util.Properties props = new java.util.Properties();
                props.load(fis);

                for (String key : props.stringPropertyNames()) {
                    String value = props.getProperty(key);
                    envVariables.put(key, value);
                    System.setProperty(key, value);
                }

                applicationContext.getEnvironment().getPropertySources()
                        .addFirst(new MapPropertySource("envFile", envVariables));

                System.out.println("✅ Variables de entorno cargadas desde .env");

            } catch (IOException e) {
                System.err.println("⚠️ Error al cargar .env: " + e.getMessage());
            }
        } else {
            System.out.println("ℹ️ Archivo .env no encontrado, usando variables del sistema");
        }
    }
}