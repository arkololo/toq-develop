package fr.toq.nutrition;

import static org.assertj.core.api.Assertions.assertThat;
import fr.toq.nutrition.api.CiqualController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:nutrition;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "TOQ_OIDC_ISSUER_URI=http://issuer.test/realms/TOQ",
    "TOQ_OIDC_JWK_SET_URI=http://127.0.0.1:65534/jwks"
})
class CiqualControllerTest {
  @Autowired CiqualController ciqual;
  @Test void searches_official_2025_foods_and_preserves_missing_values() {
    var results = ciqual.search("carotte");
    assertThat(results).isNotEmpty();
    assertThat(results).allMatch(food -> food.name().toLowerCase().contains("carotte"));
    assertThat(ciqual.food(results.getFirst().code()).name()).isEqualTo(results.getFirst().name());
    assertThat(ciqual.search("pates legumes")).isNotEmpty().allMatch(food ->
        food.name().toLowerCase().contains("pâtes") && food.name().toLowerCase().contains("légumes"));
  }
}
