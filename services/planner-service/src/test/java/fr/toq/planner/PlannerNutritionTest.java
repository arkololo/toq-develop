package fr.toq.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import fr.toq.planner.api.PlannerController;
import fr.toq.planner.api.PlannerNutritionController;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.Jwt;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:plannernutrition;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "TOQ_OIDC_ISSUER_URI=http://issuer.test/realms/TOQ",
    "TOQ_OIDC_JWK_SET_URI=http://127.0.0.1:65534/jwks"
})
class PlannerNutritionTest {
  @Autowired PlannerController planner;
  @Autowired PlannerNutritionController nutrition;
  @Autowired JdbcClient jdbc;
  @Test void sums_planned_portions_for_owner_and_exposes_partial_data() {
    LocalDate monday = LocalDate.of(2026, 9, 21);
    Jwt owner = jwt("nutrition-owner");
    planner.list(monday, owner);
    UUID plan = jdbc.sql("SELECT id FROM week_plan WHERE owner_sub=:sub").param("sub", owner.getSubject()).query(UUID.class).single();
    String snapshot = "{\"baseServings\":2,\"ingredients\":[{\"name\":\"Riz\",\"quantity\":200,\"unit\":\"g\",\"kcal100g\":100,\"proteinG100g\":2,\"carbohydrateG100g\":20,\"fatG100g\":1}]}";
    jdbc.sql("INSERT INTO planned_meal(id,plan_id,recipe_id,recipe_title,meal_date,slot,portions,ingredients_json) VALUES(:id,:plan,:recipe,'Riz',:date,'LUNCH',1,:snapshot)")
        .param("id", UUID.randomUUID()).param("plan", plan).param("recipe", UUID.randomUUID()).param("date", monday).param("snapshot", snapshot).update();
    assertThat(nutrition.week(monday, owner).getFirst().kcal()).isEqualByComparingTo("100");
    assertThat(nutrition.week(monday, owner).getFirst().complete()).isTrue();
    assertThat(nutrition.week(monday, jwt("other")).getFirst().mealCount()).isZero();
  }
  @Test void uses_manual_recipe_macros_per_portion_for_new_plans() {
    LocalDate monday = LocalDate.of(2026, 9, 28);
    Jwt owner = jwt("manual-macro-owner");
    planner.list(monday, owner);
    UUID plan = jdbc.sql("SELECT id FROM week_plan WHERE owner_sub=:sub").param("sub", owner.getSubject()).query(UUID.class).single();
    String snapshot = "{\"baseServings\":2,\"kcalPerServing\":250,\"proteinGPerServing\":12,\"carbohydrateGPerServing\":30,\"fatGPerServing\":8,\"ingredients\":[{\"name\":\"Riz\",\"quantity\":200,\"unit\":\"g\"}]}";
    jdbc.sql("INSERT INTO planned_meal(id,plan_id,recipe_id,recipe_title,meal_date,slot,portions,ingredients_json) VALUES(:id,:plan,:recipe,'Riz',:date,'LUNCH',3,:snapshot)")
        .param("id", UUID.randomUUID()).param("plan", plan).param("recipe", UUID.randomUUID()).param("date", monday).param("snapshot", snapshot).update();
    var day = nutrition.week(monday, owner).getFirst();
    assertThat(day.kcal()).isEqualByComparingTo("750");
    assertThat(day.proteinG()).isEqualByComparingTo("36");
    assertThat(day.complete()).isTrue();
  }
  @Test void recipe_changes_refresh_existing_meals_and_mark_shopping_list_stale() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/api/v1/recipes/", exchange -> {
      byte[] body = "{\"title\":\"Riz corrigé\",\"version\":2,\"baseServings\":1,\"kcalPerServing\":320,\"proteinGPerServing\":9,\"carbohydrateGPerServing\":60,\"fatGPerServing\":5,\"ingredients\":[{\"name\":\"Riz\",\"quantity\":100,\"unit\":\"g\"}]}".getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, body.length);
      try (var stream = exchange.getResponseBody()) { stream.write(body); }
    });
    server.start();
    try {
      var livePlanner = new PlannerController(jdbc, "http://127.0.0.1:" + server.getAddress().getPort());
      var liveNutrition = new PlannerNutritionController(jdbc, livePlanner);
      LocalDate monday = LocalDate.of(2026, 10, 5);
      Jwt owner = jwt("refresh-owner");
      livePlanner.list(monday, owner);
      UUID plan = jdbc.sql("SELECT id FROM week_plan WHERE owner_sub=:sub").param("sub", owner.getSubject()).query(UUID.class).single();
      jdbc.sql("INSERT INTO planned_meal(id,plan_id,recipe_id,recipe_title,meal_date,slot,portions,ingredients_json) VALUES(:id,:plan,:recipe,'Riz',:date,'LUNCH',2,:snapshot)")
          .param("id", UUID.randomUUID()).param("plan", plan).param("recipe", UUID.randomUUID()).param("date", monday)
          .param("snapshot", "{\"title\":\"Riz\",\"version\":1,\"baseServings\":1,\"kcalPerServing\":100,\"proteinGPerServing\":2,\"carbohydrateGPerServing\":20,\"fatGPerServing\":1,\"ingredients\":[]}").update();
      livePlanner.generate(monday, owner);
      // The first refresh happened during generation; simulate a later recipe edit.
      jdbc.sql("UPDATE planned_meal SET ingredients_json=:old WHERE plan_id=:plan")
          .param("old", "{\"title\":\"Riz\",\"version\":1,\"baseServings\":1,\"kcalPerServing\":100,\"proteinGPerServing\":2,\"carbohydrateGPerServing\":20,\"fatGPerServing\":1,\"ingredients\":[]}")
          .param("plan", plan).update();
      assertThat(liveNutrition.week(monday, owner).getFirst().kcal()).isEqualByComparingTo("640");
      assertThat(livePlanner.list(monday, owner).getFirst().recipeTitle()).isEqualTo("Riz corrigé");
      assertThat(livePlanner.shopping(monday, owner).stale()).isTrue();
    } finally { server.stop(0); }
  }
  @Test void updates_and_moves_only_the_owners_meal() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/api/v1/recipes/", exchange -> {
      byte[] body = "{\"title\":\"Soupe\",\"baseServings\":2,\"ingredients\":[{\"name\":\"Carotte\",\"quantity\":2,\"unit\":\"pièce\"}]}".getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, body.length);
      try (var stream = exchange.getResponseBody()) { stream.write(body); }
    });
    server.start();
    try {
      var live = new PlannerController(jdbc, "http://127.0.0.1:" + server.getAddress().getPort());
      LocalDate week = LocalDate.of(2026, 10, 12);
      Jwt owner = jwt("move-owner");
      UUID recipe = UUID.randomUUID();
      var added = live.add(week, owner, new PlannerController.MealInput(recipe, week, "DINNER", 1));
      assertThatThrownBy(() -> live.update(week, added.id(), jwt("other"),
          new PlannerController.MealInput(recipe, week.plusDays(7), "LUNCH", 2))).isInstanceOf(Exception.class);
      var moved = live.update(week, added.id(), owner,
          new PlannerController.MealInput(recipe, week.plusDays(7), "LUNCH", 2));
      assertThat(moved.portions()).isEqualTo(2);
      assertThat(live.list(week, owner)).isEmpty();
      assertThat(live.list(week.plusDays(7), owner)).hasSize(1);
    } finally { server.stop(0); }
  }
  @Test void replaces_a_complete_week_atomically_and_only_for_its_owner() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/api/v1/recipes/", exchange -> {
      byte[] body = "{\"title\":\"Porridge\",\"baseServings\":1,\"ingredients\":[{\"name\":\"Avoine\",\"quantity\":50,\"unit\":\"g\"}]}".getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, body.length);
      try (var stream = exchange.getResponseBody()) { stream.write(body); }
    });
    server.start();
    try {
      var live = new PlannerController(jdbc, "http://127.0.0.1:" + server.getAddress().getPort());
      LocalDate week = LocalDate.of(2026, 10, 19);
      Jwt owner = jwt("whole-week-owner"), other = jwt("whole-week-other");
      UUID recipe = UUID.randomUUID();
      live.add(week, owner, new PlannerController.MealInput(recipe, week, "SNACK", 1));
      live.add(week, other, new PlannerController.MealInput(recipe, week, "DINNER", 1));
      List<PlannerController.MealInput> proposal = new ArrayList<>();
      for (int day = 0; day < 7; day++) for (String slot : List.of("BREAKFAST", "LUNCH", "DINNER"))
        proposal.add(new PlannerController.MealInput(recipe, week.plusDays(day), slot, 2));
      assertThatThrownBy(() -> live.replaceWeek(week, owner, proposal.subList(0, 20))).isInstanceOf(Exception.class);
      assertThat(live.list(week, owner)).hasSize(1);
      assertThat(live.replaceWeek(week, owner, proposal)).hasSize(21);
      assertThat(live.list(week, owner)).hasSize(21);
      assertThat(live.list(week, other)).hasSize(1);
    } finally { server.stop(0); }
  }
  private Jwt jwt(String sub) { Instant now=Instant.now(); return Jwt.withTokenValue("test").header("alg","none").subject(sub).issuedAt(now).expiresAt(now.plusSeconds(300)).build(); }
}
