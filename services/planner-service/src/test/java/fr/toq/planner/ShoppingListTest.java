package fr.toq.planner;

import static org.assertj.core.api.Assertions.assertThat;

import fr.toq.planner.api.PlannerController;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.Jwt;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:shopping;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "TOQ_OIDC_ISSUER_URI=http://issuer.test/realms/TOQ",
    "TOQ_OIDC_JWK_SET_URI=http://127.0.0.1:65534/jwks"
})
class ShoppingListTest {
  @Autowired PlannerController planner;
  @Autowired JdbcClient jdbc;

  @Test void consolidates_same_ingredient_and_marks_list_stale_after_change() {
    LocalDate week = LocalDate.of(2026, 9, 21);
    Jwt owner = jwt("owner-a");
    planner.list(week, owner);
    UUID planId = jdbc.sql("SELECT id FROM week_plan WHERE owner_sub=:owner").param("owner", "owner-a").query(UUID.class).single();
    String snapshot = "{\"baseServings\":2,\"ingredients\":[{\"name\":\"Riz\",\"quantity\":100,\"unit\":\"g\"}]}";
    for (int portions : new int[] {2, 1}) jdbc.sql("INSERT INTO planned_meal(id,plan_id,recipe_id,recipe_title,meal_date,slot,portions,ingredients_json) VALUES(:id,:plan,:recipe,'Riz',:date,'DINNER',:portions,:snapshot)")
        .param("id", UUID.randomUUID()).param("plan", planId).param("recipe", UUID.randomUUID()).param("date", week).param("portions", portions).param("snapshot", snapshot).update();
    var result = planner.generate(week, owner);
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().quantity()).isEqualByComparingTo("150");
    assertThat(planner.shopping(week, jwt("owner-b")).items()).isEmpty();
    jdbc.sql("UPDATE week_plan SET version=version+1 WHERE id=:id").param("id", planId).update();
    assertThat(planner.shopping(week, owner).stale()).isTrue();
  }

  @Test void normalizes_kilograms_and_grams_in_one_shopping_item() {
    LocalDate week = LocalDate.of(2026, 9, 28);
    Jwt owner = jwt("owner-units");
    planner.list(week, owner);
    UUID planId = jdbc.sql("SELECT id FROM week_plan WHERE owner_sub=:owner").param("owner", "owner-units").query(UUID.class).single();
    String[] snapshots = {
        "{\"baseServings\":1,\"ingredients\":[{\"name\":\"Riz\",\"quantity\":1,\"unit\":\"kg\"}]}",
        "{\"baseServings\":1,\"ingredients\":[{\"name\":\"Riz\",\"quantity\":250,\"unit\":\"g\"}]}"
    };
    for (String snapshot : snapshots) jdbc.sql("INSERT INTO planned_meal(id,plan_id,recipe_id,recipe_title,meal_date,slot,portions,ingredients_json) VALUES(:id,:plan,:recipe,'Riz',:date,'DINNER',1,:snapshot)")
        .param("id", UUID.randomUUID()).param("plan", planId).param("recipe", UUID.randomUUID()).param("date", week).param("snapshot", snapshot).update();
    var list = planner.generate(week, owner);
    assertThat(list.items()).hasSize(1);
    assertThat(list.items().getFirst().quantity()).isEqualByComparingTo("1250");
    assertThat(list.items().getFirst().unit()).isEqualTo("g");
  }

  private Jwt jwt(String subject) {
    Instant now = Instant.now();
    return Jwt.withTokenValue("test").header("alg", "none").subject(subject).issuedAt(now).expiresAt(now.plusSeconds(300)).build();
  }
}
