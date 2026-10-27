package fr.toq.recipe;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import fr.toq.recipe.api.RecipeTemplateController;
import fr.toq.recipe.api.MealTranslator;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class RecipeTemplateControllerTest {
  @Test void lists_and_opens_external_recipe_as_template() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/api/json/v1/1/", exchange -> {
      String body = exchange.getRequestURI().getPath().endsWith("lookup.php")
          ? "{\"meals\":[{\"idMeal\":\"123\",\"strMeal\":\"Soupe\",\"strMealThumb\":\"https://www.themealdb.com/images/soupe.jpg\",\"strCategory\":\"Vegetarian\",\"strArea\":\"French\",\"strIngredient1\":\"Carrot\",\"strMeasure1\":\"2 tbs\",\"strIngredient2\":\"Butter\",\"strMeasure2\":\"1 tsp\",\"strInstructions\":\"Cook carrots.\\nServe.\"}]}"
          : "{\"meals\":[{\"idMeal\":\"123\",\"strMeal\":\"Soupe\",\"strMealThumb\":\"https://www.themealdb.com/images/soupe.jpg\"}]}";
      byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, bytes.length);
      try (var output = exchange.getResponseBody()) { output.write(bytes); }
    });
    server.start();
    try {
      RecipeTemplateController controller = new RecipeTemplateController("http://127.0.0.1:" + server.getAddress().getPort() + "/api/json/v1/1");
      assertThat(controller.search("")).extracting(RecipeTemplateController.TemplateSummary::title).containsExactly("Soupe");
      var detail = controller.detail("123");
      assertThat(detail.ingredients()).extracting(RecipeTemplateController.TemplateIngredient::name).containsExactly("Carrot", "Butter");
      assertThat(detail.ingredients()).extracting(RecipeTemplateController.TemplateIngredient::measure).containsExactly("2 cuillère à soupe", "1 cuillère à café");
      assertThat(detail.steps()).containsExactly("Cook carrots.", "Serve.");
    } finally { server.stop(0); }
  }

  @Test void french_search_is_translated_when_the_direct_search_has_no_results() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/api/json/v1/1/", exchange -> reply(exchange,
        exchange.getRequestURI().getRawQuery().contains("chicken") ? "{\"meals\":[{\"idMeal\":\"123\",\"strMeal\":\"Chicken soup\"}]}"
            : "{\"meals\":null}"));
    server.start();
    try {
      String base = "http://127.0.0.1:" + server.getAddress().getPort();
      var controller = new RecipeTemplateController(base + "/api/json/v1/1", new MealTranslator(base + "/get"));
      assertThat(controller.search("poulet")).extracting(RecipeTemplateController.TemplateSummary::id).containsExactly("123");
      assertThat(new MealTranslator().searchEnglish("pâtes")).isEqualTo("pasta");
      assertThat(new MealTranslator().searchEnglish("pâte")).isEqualTo("pasta");
    } finally { server.stop(0); }
  }

  private void reply(com.sun.net.httpserver.HttpExchange exchange, String body) throws java.io.IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(200, bytes.length);
    try (var output = exchange.getResponseBody()) { output.write(bytes); }
  }
}
