package fr.toq.planner.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/plans")
public class PlannerController {
  private final JdbcClient jdbc;
  private final String recipeBaseUrl;
  private final ObjectMapper mapper = new ObjectMapper();
  private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

  public PlannerController(JdbcClient jdbc, @Value("${toq.recipe-base-url:http://recipe-service:8080}") String recipeBaseUrl) {
    this.jdbc = jdbc;
    this.recipeBaseUrl = recipeBaseUrl;
  }

  @GetMapping("/{weekStart}")
  public List<Meal> list(@PathVariable LocalDate weekStart, @AuthenticationPrincipal Jwt jwt) {
    UUID planId = plan(weekStart, jwt.getSubject());
    refreshSnapshots(planId, jwt.getTokenValue());
    return jdbc.sql("SELECT id, recipe_id, recipe_title, meal_date, slot, portions FROM planned_meal WHERE plan_id=:plan ORDER BY meal_date,slot")
        .param("plan", planId).query((rs, row) -> new Meal(rs.getObject("id", UUID.class), rs.getObject("recipe_id", UUID.class), rs.getString("recipe_title"), rs.getObject("meal_date", LocalDate.class), rs.getString("slot"), rs.getInt("portions"))).list();
  }

  @PostMapping("/{weekStart}/meals")
  @Transactional
  public Meal add(@PathVariable LocalDate weekStart, @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody MealInput input) {
    validateWeek(weekStart);
    if (input.date() == null || input.date().isBefore(weekStart) || input.date().isAfter(weekStart.plusDays(6))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date hors de la semaine.");
    if (!List.of("BREAKFAST", "LUNCH", "DINNER", "SNACK").contains(input.slot())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Créneau invalide.");
    JsonNode recipe = authorizedRecipe(input.recipeId(), jwt.getTokenValue());
    String title = recipe.path("title").asText("");
    if (title.isBlank() || recipe.path("baseServings").asInt(0) <= 0) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Recette incomplète.");
    UUID planId = plan(weekStart, jwt.getSubject());
    UUID id = UUID.randomUUID();
    jdbc.sql("INSERT INTO planned_meal(id,plan_id,recipe_id,recipe_title,meal_date,slot,portions,ingredients_json) VALUES(:id,:plan,:recipe,:title,:date,:slot,:portions,:ingredients)")
        .param("id", id).param("plan", planId).param("recipe", input.recipeId()).param("title", title).param("date", input.date()).param("slot", input.slot()).param("portions", input.portions()).param("ingredients", recipe.toString()).update();
    bump(planId);
    return new Meal(id, input.recipeId(), title, input.date(), input.slot(), input.portions());
  }

  /** Replace one complete week in a single transaction after the assistant proposal is approved. */
  @PutMapping("/{weekStart}/meals")
  @Transactional
  public List<Meal> replaceWeek(@PathVariable LocalDate weekStart, @AuthenticationPrincipal Jwt jwt,
      @RequestBody List<@Valid MealInput> inputs) {
    validateWeek(weekStart);
    if (inputs == null || inputs.size() < 21 || inputs.size() > 28)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Une semaine complète contient 21 à 28 repas.");
    Map<String, JsonNode> recipes = new LinkedHashMap<>();
    java.util.Set<String> occupied = new java.util.HashSet<>();
    for (MealInput input : inputs) {
      if (input == null || input.recipeId() == null || input.date() == null || input.slot() == null
          || input.portions() < 1 || input.portions() > 20
          || input.date().isBefore(weekStart) || input.date().isAfter(weekStart.plusDays(6))
          || !List.of("BREAKFAST", "LUNCH", "DINNER", "SNACK").contains(input.slot())
          || !occupied.add(input.date() + "/" + input.slot()))
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Repas ou créneau invalide.");
      recipes.computeIfAbsent(input.recipeId().toString(), ignored -> authorizedRecipe(input.recipeId(), jwt.getTokenValue()));
    }
    for (int day = 0; day < 7; day++) for (String required : List.of("BREAKFAST", "LUNCH", "DINNER"))
      if (!occupied.contains(weekStart.plusDays(day) + "/" + required))
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Petit déjeuner, déjeuner et dîner requis chaque jour.");
    UUID planId = plan(weekStart, jwt.getSubject());
    jdbc.sql("DELETE FROM planned_meal WHERE plan_id=:plan").param("plan", planId).update();
    List<Meal> saved = new ArrayList<>();
    for (MealInput input : inputs) {
      JsonNode recipe = recipes.get(input.recipeId().toString());
      String title = recipe.path("title").asText("");
      if (title.isBlank() || recipe.path("baseServings").asInt(0) <= 0)
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Recette incomplète.");
      UUID id = UUID.randomUUID();
      jdbc.sql("INSERT INTO planned_meal(id,plan_id,recipe_id,recipe_title,meal_date,slot,portions,ingredients_json) VALUES(:id,:plan,:recipe,:title,:date,:slot,:portions,:ingredients)")
          .param("id", id).param("plan", planId).param("recipe", input.recipeId()).param("title", title)
          .param("date", input.date()).param("slot", input.slot()).param("portions", input.portions())
          .param("ingredients", recipe.toString()).update();
      saved.add(new Meal(id, input.recipeId(), title, input.date(), input.slot(), input.portions()));
    }
    bump(planId);
    return saved;
  }

  @PutMapping("/{weekStart}/meals/{mealId}")
  @Transactional
  public Meal update(@PathVariable LocalDate weekStart, @PathVariable UUID mealId,
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody MealInput input) {
    validateWeek(weekStart);
    if (input.date() == null || input.date().isBefore(weekStart.minusDays(90)) || input.date().isAfter(weekStart.plusDays(90)))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date hors de la période autorisée.");
    if (!List.of("BREAKFAST", "LUNCH", "DINNER", "SNACK").contains(input.slot()))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Créneau invalide.");
    UUID planId = plan(weekStart, jwt.getSubject());
    LocalDate targetWeek = input.date().minusDays(input.date().getDayOfWeek().getValue() - 1);
    UUID targetPlanId = targetWeek.equals(weekStart) ? planId : plan(targetWeek, jwt.getSubject());
    JsonNode recipe = authorizedRecipe(input.recipeId(), jwt.getTokenValue());
    String title = recipe.path("title").asText("");
    if (title.isBlank() || recipe.path("baseServings").asInt(0) <= 0)
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Recette incomplète.");
    int changed = jdbc.sql("UPDATE planned_meal SET plan_id=:targetPlan,recipe_id=:recipe,recipe_title=:title,meal_date=:date,slot=:slot,portions=:portions,ingredients_json=:snapshot WHERE id=:id AND plan_id=:plan")
        .param("targetPlan", targetPlanId)
        .param("recipe", input.recipeId()).param("title", title).param("date", input.date())
        .param("slot", input.slot()).param("portions", input.portions()).param("snapshot", recipe.toString())
        .param("id", mealId).param("plan", planId).update();
    if (changed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Repas introuvable.");
    bump(planId);
    if (!targetPlanId.equals(planId)) bump(targetPlanId);
    return new Meal(mealId, input.recipeId(), title, input.date(), input.slot(), input.portions());
  }

  @DeleteMapping("/{weekStart}/meals/{mealId}")
  @Transactional
  public void remove(@PathVariable LocalDate weekStart, @PathVariable UUID mealId, @AuthenticationPrincipal Jwt jwt) {
    UUID planId = plan(weekStart, jwt.getSubject());
    int deleted = jdbc.sql("DELETE FROM planned_meal WHERE id=:id AND plan_id=:plan").param("id", mealId).param("plan", planId).update();
    if (deleted == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Repas introuvable.");
    bump(planId);
  }

  @GetMapping("/{weekStart}/shopping-list")
  public ShoppingList shopping(@PathVariable LocalDate weekStart, @AuthenticationPrincipal Jwt jwt) {
    UUID planId = plan(weekStart, jwt.getSubject());
    refreshSnapshots(planId, jwt.getTokenValue());
    long version = jdbc.sql("SELECT version FROM week_plan WHERE id=:id").param("id", planId).query(Long.class).single();
    var existing = jdbc.sql("SELECT id,source_version FROM shopping_list WHERE plan_id=:plan").param("plan", planId)
        .query((rs, row) -> new ListHeader(rs.getObject("id", UUID.class), rs.getLong("source_version"))).optional();
    if (existing.isEmpty()) return new ShoppingList(null, true, List.of());
    List<ShoppingItem> items = jdbc.sql("SELECT id,label,quantity,unit,checked FROM shopping_item WHERE list_id=:list ORDER BY label")
        .param("list", existing.get().id()).query((rs, row) -> new ShoppingItem(rs.getObject("id", UUID.class), rs.getString("label"), rs.getBigDecimal("quantity"), rs.getString("unit"), rs.getBoolean("checked"))).list();
    return new ShoppingList(existing.get().id(), version != existing.get().sourceVersion(), items);
  }

  @PostMapping("/{weekStart}/shopping-list")
  @Transactional
  public ShoppingList generate(@PathVariable LocalDate weekStart, @AuthenticationPrincipal Jwt jwt) {
    UUID planId = plan(weekStart, jwt.getSubject());
    refreshSnapshots(planId, jwt.getTokenValue());
    long version = jdbc.sql("SELECT version FROM week_plan WHERE id=:id").param("id", planId).query(Long.class).single();
    Map<String, IngredientTotal> totals = new LinkedHashMap<>();
    List<MealSnapshot> meals = jdbc.sql("SELECT portions,ingredients_json FROM planned_meal WHERE plan_id=:plan").param("plan", planId)
        .query((rs, row) -> new MealSnapshot(rs.getInt("portions"), rs.getString("ingredients_json"))).list();
    for (MealSnapshot meal : meals) {
      try {
        JsonNode recipe = mapper.readTree(meal.json());
        int baseServings = recipe.path("baseServings").asInt();
        if (baseServings <= 0) throw new IllegalArgumentException("Portions invalides");
        for (JsonNode ingredient : recipe.path("ingredients")) {
          String label = ingredient.path("name").asText().trim();
          String rawUnit = ingredient.path("unit").asText().trim().toLowerCase(java.util.Locale.ROOT);
          String unit = normalizeUnit(rawUnit);
          BigDecimal quantity = ingredient.path("quantity").decimalValue().multiply(BigDecimal.valueOf(meal.portions())).divide(BigDecimal.valueOf(baseServings), 3, java.math.RoundingMode.HALF_UP);
          if (rawUnit.equals("kg") || rawUnit.equals("kilogramme") || rawUnit.equals("kilogrammes") || rawUnit.equals("l") || rawUnit.equals("litre") || rawUnit.equals("litres")) quantity = quantity.multiply(BigDecimal.valueOf(1000));
          if (label.isBlank() || unit.isBlank() || quantity.signum() <= 0) continue;
          String key = label.toLowerCase() + "|" + unit;
          totals.merge(key, new IngredientTotal(label, unit, quantity), (a, b) -> new IngredientTotal(a.label(), a.unit(), a.quantity().add(b.quantity())));
        }
      } catch (Exception e) { throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Instantané de recette invalide."); }
    }
    UUID listId = jdbc.sql("SELECT id FROM shopping_list WHERE plan_id=:plan").param("plan", planId).query(UUID.class).optional().orElse(null);
    Map<String, BigDecimal> checked = new LinkedHashMap<>();
    if (listId != null) jdbc.sql("SELECT label,unit,quantity FROM shopping_item WHERE list_id=:list AND checked=true").param("list", listId)
        .query((rs, row) -> { checked.put(rs.getString("label").toLowerCase(java.util.Locale.ROOT) + "|" + rs.getString("unit"), rs.getBigDecimal("quantity")); return 0; }).list();
    if (listId == null) {
      listId = UUID.randomUUID();
      jdbc.sql("INSERT INTO shopping_list(id,plan_id,source_version,generated_at) VALUES(:id,:plan,:version,:now)").param("id", listId).param("plan", planId).param("version", version).param("now", Timestamp.from(Instant.now())).update();
    } else {
      jdbc.sql("DELETE FROM shopping_item WHERE list_id=:list").param("list", listId).update();
      jdbc.sql("UPDATE shopping_list SET source_version=:version,generated_at=:now WHERE id=:id").param("version", version).param("now", Timestamp.from(Instant.now())).param("id", listId).update();
    }
    for (IngredientTotal total : totals.values()) jdbc.sql("INSERT INTO shopping_item(id,list_id,label,quantity,unit,checked) VALUES(:id,:list,:label,:quantity,:unit,:checked)")
        .param("id", UUID.randomUUID()).param("list", listId).param("label", total.label()).param("quantity", total.quantity()).param("unit", total.unit())
        .param("checked", total.quantity().compareTo(checked.getOrDefault(total.label().toLowerCase(java.util.Locale.ROOT) + "|" + total.unit(), BigDecimal.valueOf(-1))) == 0).update();
    return shopping(weekStart, jwt);
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

  void refreshSnapshots(UUID planId, String token) {
    var meals = jdbc.sql("SELECT id,recipe_id,ingredients_json FROM planned_meal WHERE plan_id=:plan")
        .param("plan", planId).query((rs, row) -> new RecipeSnapshot(rs.getObject("id", UUID.class),
            rs.getObject("recipe_id", UUID.class), rs.getString("ingredients_json"))).list();
    Map<UUID, JsonNode> current = new LinkedHashMap<>();
    for (RecipeSnapshot meal : meals) {
      JsonNode recipe;
      if (current.containsKey(meal.recipeId())) recipe = current.get(meal.recipeId());
      else {
        try { recipe = authorizedRecipe(meal.recipeId(), token); }
        catch (ResponseStatusException e) {
          if (e.getStatusCode() == HttpStatus.NOT_FOUND || e.getStatusCode() == HttpStatus.BAD_GATEWAY) {
            current.put(meal.recipeId(), null); continue;
          }
          throw e;
        }
        current.put(meal.recipeId(), recipe);
      }
      if (recipe == null || recipe.toString().equals(meal.json())) continue;
      String title = recipe.path("title").asText("");
      if (title.isBlank()) continue;
      int changed = jdbc.sql("UPDATE planned_meal SET ingredients_json=:fresh,recipe_title=:title WHERE id=:id AND plan_id=:plan AND ingredients_json=:old")
          .param("fresh", recipe.toString()).param("title", title).param("id", meal.id()).param("plan", planId).param("old", meal.json()).update();
      if (changed > 0) bump(planId);
    }
  }

  private UUID plan(LocalDate week, String owner) {
    validateWeek(week);
    return jdbc.sql("SELECT id FROM week_plan WHERE owner_sub=:owner AND week_start=:week").param("owner", owner).param("week", week).query(UUID.class).optional().orElseGet(() -> {
      UUID id = UUID.randomUUID();
      jdbc.sql("INSERT INTO week_plan(id,owner_sub,week_start) VALUES(:id,:owner,:week)").param("id", id).param("owner", owner).param("week", week).update();
      return id;
    });
  }
  private void validateWeek(LocalDate week) { if (week.getDayOfWeek().getValue() != 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La semaine commence le lundi."); }
  private String normalizeUnit(String raw) { return switch (raw) {
      case "kg", "kilogramme", "kilogrammes", "g", "gramme", "grammes" -> "g";
      case "l", "litre", "litres", "ml", "millilitre", "millilitres" -> "ml";
      case "pièce", "pièces", "piece", "pieces", "unité", "unités", "unite", "unites" -> "pièce";
      default -> raw;
    }; }
  private void bump(UUID planId) { jdbc.sql("UPDATE week_plan SET version=version+1 WHERE id=:id").param("id", planId).update(); }
  public record Meal(UUID id, UUID recipeId, String recipeTitle, LocalDate date, String slot, int portions) { }
  public record MealInput(@NotNull UUID recipeId, @NotNull LocalDate date, @NotNull String slot, @Positive int portions) { }
  public record ShoppingItem(UUID id, String label, BigDecimal quantity, String unit, boolean checked) { }
  public record ShoppingList(UUID id, boolean stale, List<ShoppingItem> items) { }
  private record ListHeader(UUID id, long sourceVersion) { }
  private record MealSnapshot(int portions, String json) { }
  private record RecipeSnapshot(UUID id, UUID recipeId, String json) { }
  private record IngredientTotal(String label, String unit, BigDecimal quantity) { }
}
