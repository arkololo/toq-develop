package fr.toq.recipe;

import com.zaxxer.hikari.HikariDataSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import javax.sql.DataSource;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "management.server.port=0",
        "spring.datasource.url=jdbc:h2:mem:recipe;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "TOQ_OIDC_ISSUER_URI=http://issuer.test/realms/TOQ",
        "TOQ_OIDC_JWK_SET_URI=http://127.0.0.1:65534/jwks",
        "logging.level.org.springframework.boot.jdbc.health.DataSourceHealthIndicator=ERROR"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BootstrapTest {

    @Value("${local.server.port}")
    private int serverPort;

    @Value("${local.management.port}")
    private int managementPort;

    @Autowired
    private DataSource dataSource;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Test
    @Order(1)
    void bootsWithDatabaseAndExposesHealthOnSeparateManagementPort() throws Exception {
        assertThat(managementPort).isNotEqualTo(serverPort);
        for (String path : new String[] {
                "/actuator/health", "/actuator/health/readiness", "/actuator/health/liveness"}) {
            HttpResponse<String> response = get(managementPort, path);
            assertThat(response.statusCode()).as(path).isEqualTo(200);
            assertThat(response.body()).contains("\"status\":\"UP\"").doesNotContain("jdbc:");
        }
    }

    @Test
    @Order(2)
    void refusesBusinessRoutesAndManagementInformationOnApplicationPort() throws Exception {
        for (String path : new String[] {
                "/api/v1/recipes", "/internal/v1/test", "/actuator/health", "/actuator/env", "/login"}) {
            HttpResponse<String> response = get(serverPort, path);
            assertThat(response.statusCode()).as(path).isIn(401, 403);
            assertThat(response.headers().firstValue("Set-Cookie")).isEmpty();
        }
        assertThat(get(managementPort, "/actuator/env").statusCode()).isIn(401, 403);
    }

    @Test
    @Order(3)
    void readinessFailsWhenDatabaseIsUnavailableButLivenessStaysUp() throws Exception {
        // Closing the pool simulates a lost DB dependency after successful startup.
        // Run last because this deliberately ends the test context's DB access.
        dataSource.unwrap(HikariDataSource.class).close();

        HttpResponse<String> readiness = get(managementPort, "/actuator/health/readiness");
        assertThat(readiness.statusCode()).isEqualTo(503);
        assertThat(readiness.body()).contains("\"status\":\"DOWN\"").doesNotContain("exception");
        assertThat(get(managementPort, "/actuator/health/liveness").statusCode()).isEqualTo(200);
    }

    private HttpResponse<String> get(int port, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
