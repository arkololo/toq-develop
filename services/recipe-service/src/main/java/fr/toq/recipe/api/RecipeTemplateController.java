package fr.toq.recipe.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/recipes/templates")
public class RecipeTemplateController {
  private final String baseUrl;
  private final MealTranslator translator;
  private final ObjectMapper mapper = new ObjectMapper();
  private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
  @Autowired
  public RecipeTemplateController(@Value("${toq.themealdb.base-url:https://www.themealdb.com/api/json/v1/1}") String baseUrl, MealTranslator translator) {
    this.baseUrl = baseUrl; this.translator = translator;
  }
  public RecipeTemplateController(String baseUrl) { this(baseUrl, new MealTranslator("")); }

  @GetMapping
  public List<TemplateSummary> search(@RequestParam(defaultValue="") String query) {
    String term = query.trim();
    if (term.length() > 80) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Recherche trop longue.");
    String english = translator.searchEnglish(term);
    JsonNode root = fetch(term.isEmpty() ? "/filter.php?c=Vegetarian" : "/search.php?s=" + URLEncoder.encode(english, StandardCharsets.UTF_8));
    if (!term.isEmpty() && !root.path("meals").isArray() && english.contains(" "))
      root = fetch("/search.php?s=" + URLEncoder.encode(english.split(" ")[0], StandardCharsets.UTF_8));
    List<TemplateSummary> result = new ArrayList<>();
    for (JsonNode meal : root.path("meals")) {
      if (result.size() >= 24) break;
      String id = meal.path("idMeal").asText("");
      if (id.matches("[0-9]{1,12}")) result.add(new TemplateSummary(id, meal.path("strMeal").asText(""), imageUrl(meal.path("strMealThumb").asText(""))));
    }
    return result;
  }

  @GetMapping("/{id}")
  public RecipeTemplate detail(@PathVariable String id) {
    if (!id.matches("[0-9]{1,12}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identifiant invalide.");
    JsonNode meals = fetch("/lookup.php?i=" + id).path("meals");
    if (!meals.isArray() || meals.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Modèle introuvable.");
    JsonNode meal = meals.get(0);
    List<TemplateIngredient> ingredients = new ArrayList<>();
    for (int i = 1; i <= 20; i++) {
      String name = meal.path("strIngredient" + i).asText("").trim();
      if (!name.isEmpty()) ingredients.add(new TemplateIngredient(name, frenchMeasure(meal.path("strMeasure" + i).asText("").trim())));
    }
    String originalTitle = meal.path("strMeal").asText("");
    List<String> steps = meal.path("strInstructions").asText("").lines().map(String::trim).filter(line -> !line.isEmpty()).toList();
    if (steps.isEmpty()) steps = List.of("Consulter la recette source.");
    return new RecipeTemplate(id, originalTitle, originalTitle,
        imageUrl(meal.path("strMealThumb").asText("")), meal.path("strCategory").asText(""), meal.path("strArea").asText(""),
        ingredients, steps, false);
  }

  public String imageUrlFor(String id) {
    if (!id.matches("[0-9]{1,12}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identifiant invalide.");
    JsonNode meals = fetch("/lookup.php?i=" + id).path("meals");
    if (!meals.isArray() || meals.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Modèle introuvable.");
    return imageUrl(meals.get(0).path("strMealThumb").asText(""));
  }

  private JsonNode fetch(String path) {
    try {
      HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(6))
          .header("User-Agent", "TOQ-local/1.0 (recipe templates)").GET().build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Catalogue de recettes indisponible.");
      return mapper.readTree(response.body());
    } catch (ResponseStatusException e) { throw e; }
      catch (Exception e) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Catalogue de recettes indisponible."); }
  }
  private String imageUrl(String raw) {
    try { URI uri = URI.create(raw); return uri.getScheme().equals("https") && (uri.getHost().equals("www.themealdb.com") || uri.getHost().equals("themealdb.com")) ? raw : null; }
    catch (Exception e) { return null; }
  }
  static String frenchMeasure(String raw) {
    String text = raw.replaceAll("(?i)\\b(tbs|tbsp|tablespoons?)\\b", "cuillère à soupe")
        .replaceAll("(?i)\\b(tsp|teaspoons?)\\b", "cuillère à café")
        .replaceAll("(?i)\\bcups?\\b", "tasse")
        .replaceAll("(?i)\\bpinch\\b", "pincée");
    return text.trim();
  }
  public record TemplateSummary(String id, String title, String imageUrl) {}
  public record TemplateIngredient(String name, String measure) {}
  public record RecipeTemplate(String id, String title, String originalTitle, String imageUrl, String category, String area,
                               List<TemplateIngredient> ingredients, List<String> steps, boolean translated) {}
}
