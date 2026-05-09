package com.SIGMA.USCO;

import com.SIGMA.USCO.config.EnvLoader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SigmaApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(SigmaApplication.class);
        app.addInitializers(new EnvLoader());
        app.run(args);
    }

}