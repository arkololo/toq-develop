package fr.toq.nutrition;

import static org.assertj.core.api.Assertions.assertThat;
import com.sun.net.httpserver.HttpServer;
import fr.toq.nutrition.api.NutritionCalculator;
import fr.toq.nutrition.api.RecipeNutritionController;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class RecipeNutritionControllerTest {
  @Test void recipe_level_values_take_priority_over_missing_ingredient_values() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/api/v1/recipes/", exchange -> {
      byte[] bytes = "{\"title\":\"Omelette\",\"baseServings\":2,\"kcalPerServing\":150,\"proteinGPerServing\":12,\"carbohydrateGPerServing\":2,\"fatGPerServing\":10,\"ingredients\":[{\"name\":\"Œufs\",\"quantity\":2,\"unit\":\"pièce\"}]}".getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, bytes.length);
      try (var output = exchange.getResponseBody()) { output.write(bytes); }
    });
    server.start();
    try {
      var controller = new RecipeNutritionController(new NutritionCalculator(), "http://127.0.0.1:" + server.getAddress().getPort());
      Jwt user = Jwt.withTokenValue("test").header("alg", "none").subject("owner")
          .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
      var result = controller.nutrition(UUID.randomUUID(), user);
      assertThat(result.complete()).isTrue();
      assertThat(result.totals().kcalPerServing()).isEqualByComparingTo("150");
      assertThat(result.totals().kcal()).isEqualByComparingTo("300");
    } finally { server.stop(0); }
  }
}
