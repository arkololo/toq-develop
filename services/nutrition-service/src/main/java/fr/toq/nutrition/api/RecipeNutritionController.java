package fr.toq.nutrition.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/nutrition/recipes")
public class RecipeNutritionController {
  private final NutritionCalculator calculator;
  private final String recipeBaseUrl;
  private final ObjectMapper mapper = new ObjectMapper();
  private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

  public RecipeNutritionController(NutritionCalculator calculator, @Value("${toq.recipe-base-url:http://recipe-service:8080}") String recipeBaseUrl) {
    this.calculator = calculator; this.recipeBaseUrl = recipeBaseUrl;
  }

  @GetMapping("/{id}")
  public Result nutrition(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    JsonNode recipe = authorizedRecipe(id, jwt.getTokenValue());
    int servings = recipe.path("baseServings").asInt();
    if (servings <= 0) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Portions de recette invalides.");
    if (recipe.path("kcalPerServing").isNumber() && recipe.path("proteinGPerServing").isNumber()
        && recipe.path("carbohydrateGPerServing").isNumber() && recipe.path("fatGPerServing").isNumber()) {
      BigDecimal kcal = recipe.path("kcalPerServing").decimalValue();
      BigDecimal protein = recipe.path("proteinGPerServing").decimalValue();
      BigDecimal carbs = recipe.path("carbohydrateGPerServing").decimalValue();
      BigDecimal fat = recipe.path("fatGPerServing").decimalValue();
      BigDecimal factor = BigDecimal.valueOf(servings);
      NutritionCalculator.Totals totals = new NutritionCalculator.Totals(kcal.multiply(factor).setScale(1, RoundingMode.HALF_UP),
          protein.multiply(factor).setScale(1, RoundingMode.HALF_UP), carbs.multiply(factor).setScale(1, RoundingMode.HALF_UP),
          fat.multiply(factor).setScale(1, RoundingMode.HALF_UP), kcal, protein, carbs, fat);
      return new Result(id, recipe.path("title").asText(), true, List.of(), totals);
    }
    List<NutritionCalculator.Ingredient> known = new ArrayList<>();
    List<String> missing = new ArrayList<>();
    for (JsonNode item : recipe.path("ingredients")) {
      String name = item.path("name").asText();
      String unit = item.path("unit").asText().toLowerCase();
      if (!List.of("g", "kg").contains(unit) || !item.path("kcal100g").isNumber() || !item.path("proteinG100g").isNumber() || !item.path("carbohydrateG100g").isNumber() || !item.path("fatG100g").isNumber()) {
        missing.add(name); continue;
      }
      BigDecimal quantityG = item.path("quantity").decimalValue().multiply(unit.equals("kg") ? BigDecimal.valueOf(1000) : BigDecimal.ONE);
      known.add(new NutritionCalculator.Ingredient(quantityG, item.path("kcal100g").decimalValue(), item.path("proteinG100g").decimalValue(), item.path("carbohydrateG100g").decimalValue(), item.path("fatG100g").decimalValue()));
    }
    return new Result(id, recipe.path("title").asText(), missing.isEmpty(), missing, calculator.calculate(known, servings));
  }

  private JsonNode authorizedRecipe(UUID id, String token) {
    try {
      var request = HttpRequest.newBuilder(URI.create(recipeBaseUrl + "/api/v1/recipes/" + id)).header("Authorization", "Bearer " + token).timeout(Duration.ofSeconds(5)).GET().build();
      var response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() == 404) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recette introuvable.");
      if (response.statusCode() != 200) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Recipe Service indisponible.");
      return mapper.readTree(response.body());
    } catch (ResponseStatusException e) { throw e; }
    catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Recipe Service indisponible."); }
  }
  public record Result(UUID recipeId, String title, boolean complete, List<String> missingIngredients, NutritionCalculator.Totals totals) { }
}
