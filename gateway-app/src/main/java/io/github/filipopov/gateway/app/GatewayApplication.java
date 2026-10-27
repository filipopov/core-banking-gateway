package io.github.filipopov.gateway.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Composition root: the only module that sees every other module. */
@SpringBootApplication(scanBasePackages = {"io.github.filipopov.gateway.app", "io.github.filipopov.gateway.api"})
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
