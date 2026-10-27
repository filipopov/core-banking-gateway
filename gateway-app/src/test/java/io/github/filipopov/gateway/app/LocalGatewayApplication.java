package io.github.filipopov.gateway.app;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.springframework.boot.SpringApplication;

/**
 * Runs the gateway together with both mock providers in one JVM, no Docker needed:
 *
 * <pre>
 * ./mvnw install -DskipTests
 * ./mvnw -pl gateway-app spring-boot:test-run
 * </pre>
 *
 * Ledgerline is served on port 8081 and Coreflow on 8082, matching {@code application.yml}.
 */
public final class LocalGatewayApplication {

    private LocalGatewayApplication() {
    }

    public static void main(String[] args) {
        startMock("ledgerline", 8081);
        startMock("coreflow", 8082);
        SpringApplication.from(GatewayApplication::main).run(args);
    }

    private static void startMock(String provider, int port) {
        var server = new WireMockServer(wireMockConfig().port(port).usingFilesUnderDirectory(MockFiles.dir(provider)));
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop, provider + "-mock-shutdown"));
    }
}
