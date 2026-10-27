package fr.toq.planner.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/plans/{weekStart}/nutrition")
public class PlannerNutritionController {
  private final JdbcClient jdbc;
  private final PlannerController planner;
  private final ObjectMapper mapper = new ObjectMapper();
  public PlannerNutritionController(JdbcClient jdbc, PlannerController planner) { this.jdbc = jdbc; this.planner = planner; }

  @GetMapping
  public List<DayNutrition> week(@PathVariable LocalDate weekStart, @AuthenticationPrincipal Jwt jwt) {
    if (weekStart.getDayOfWeek().getValue() != 1) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "La semaine commence le lundi.");
    List<MutableDay> days = new ArrayList<>();
    for (int i = 0; i < 7; i++) days.add(new MutableDay(weekStart.plusDays(i)));
    var planId = jdbc.sql("SELECT id FROM week_plan WHERE owner_sub=:owner AND week_start=:week")
        .param("owner", jwt.getSubject()).param("week", weekStart).query(UUID.class).optional();
    if (planId.isEmpty()) return days.stream().map(MutableDay::result).toList();
    planner.refreshSnapshots(planId.get(), jwt.getTokenValue());
    var meals = jdbc.sql("SELECT meal_date,portions,ingredients_json FROM planned_meal WHERE plan_id=:plan")
        .param("plan", planId.get()).query((rs, row) -> new Snapshot(rs.getObject("meal_date", LocalDate.class), rs.getInt("portions"), rs.getString("ingredients_json"))).list();
    for (Snapshot meal : meals) {
      MutableDay day = days.get((int) java.time.temporal.ChronoUnit.DAYS.between(weekStart, meal.date()));
      day.mealCount++;
      try {
        JsonNode recipe = mapper.readTree(meal.json());
        int base = recipe.path("baseServings").asInt();
        if (base < 1) { day.complete = false; continue; }
        if (recipe.path("kcalPerServing").isNumber() && recipe.path("proteinGPerServing").isNumber()
            && recipe.path("carbohydrateGPerServing").isNumber() && recipe.path("fatGPerServing").isNumber()) {
          BigDecimal portions = BigDecimal.valueOf(meal.portions());
          day.kcal = day.kcal.add(recipe.path("kcalPerServing").decimalValue().multiply(portions));
          day.protein = day.protein.add(recipe.path("proteinGPerServing").decimalValue().multiply(portions));
          day.carbs = day.carbs.add(recipe.path("carbohydrateGPerServing").decimalValue().multiply(portions));
          day.fat = day.fat.add(recipe.path("fatGPerServing").decimalValue().multiply(portions));
          continue;
        }
        for (JsonNode item : recipe.path("ingredients")) {
          String unit = item.path("unit").asText("").toLowerCase(java.util.Locale.ROOT);
          if ((!unit.equals("g") && !unit.equals("kg")) || !item.path("quantity").isNumber() ||
              !item.path("kcal100g").isNumber() || !item.path("proteinG100g").isNumber() ||
              !item.path("carbohydrateG100g").isNumber() || !item.path("fatG100g").isNumber()) { day.complete = false; continue; }
          BigDecimal grams = item.path("quantity").decimalValue().multiply(unit.equals("kg") ? BigDecimal.valueOf(1000) : BigDecimal.ONE);
          BigDecimal factor = grams.multiply(BigDecimal.valueOf(meal.portions())).divide(BigDecimal.valueOf(base * 100L), 6, RoundingMode.HALF_UP);
          day.kcal = day.kcal.add(item.path("kcal100g").decimalValue().multiply(factor));
          day.protein = day.protein.add(item.path("proteinG100g").decimalValue().multiply(factor));
          day.carbs = day.carbs.add(item.path("carbohydrateG100g").decimalValue().multiply(factor));
          day.fat = day.fat.add(item.path("fatG100g").decimalValue().multiply(factor));
        }
      } catch (Exception e) { day.complete = false; }
    }
    return days.stream().map(MutableDay::result).toList();
  }
  private record Snapshot(LocalDate date, int portions, String json) {}
  public record DayNutrition(LocalDate date, BigDecimal kcal, BigDecimal proteinG, BigDecimal carbohydrateG, BigDecimal fatG, boolean complete, int mealCount) {}
  private static class MutableDay {
    final LocalDate date;
    BigDecimal kcal=BigDecimal.ZERO, protein=BigDecimal.ZERO, carbs=BigDecimal.ZERO, fat=BigDecimal.ZERO;
    boolean complete=true;
    int mealCount=0;
    MutableDay(LocalDate date) { this.date=date; }
    DayNutrition result() { return new DayNutrition(date, kcal.setScale(1,RoundingMode.HALF_UP), protein.setScale(1,RoundingMode.HALF_UP), carbs.setScale(1,RoundingMode.HALF_UP), fat.setScale(1,RoundingMode.HALF_UP), complete && mealCount > 0, mealCount); }
  }
}
