package com.SIGMA.USCO;

import com.SIGMA.USCO.config.EnvLoader;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class SigmaApplication {

    @PostConstruct
    public void init() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Bogota"));
    }

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(SigmaApplication.class);
        app.addInitializers(new EnvLoader());
        app.run(args);
    }

}