package fr.toq.recipe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.toq.recipe.api.RecipeController;
import fr.toq.recipe.api.RecipeImageController;
import fr.toq.recipe.api.RecipeNotFoundException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.mock.web.MockMultipartFile;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:recipecrud;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "TOQ_OIDC_ISSUER_URI=http://issuer.test/realms/TOQ",
    "TOQ_OIDC_JWK_SET_URI=http://127.0.0.1:65534/jwks"
})
class RecipeControllerTest {
  @Autowired RecipeController recipes;
  @Autowired RecipeImageController images;

  @Test
  void a_recipe_is_private_and_can_be_archived() {
    Jwt owner = jwt("owner-a");
    var request = new RecipeController.RecipeRequest("Riz tomate", "Rapide", 2,
        List.of(new RecipeController.IngredientRequest("Riz", new BigDecimal("150"), "g", null, null, null, null)),
        List.of("Cuire le riz."));

    var created = recipes.create(owner, request).getBody();
    assertThat(created).isNotNull();
    assertThat(recipes.list(owner, "tomate", 0, 20)).hasSize(1);
    assertThatThrownBy(() -> recipes.get(created.id(), jwt("owner-b")))
        .isInstanceOf(RecipeNotFoundException.class);

    recipes.archive(created.id(), owner);
    assertThat(recipes.list(owner, null, 0, 20)).isEmpty();
  }

  @Test
  void only_the_owner_can_upload_and_read_a_recipe_photo() throws Exception {
    Jwt owner = jwt("owner-photo");
    var request = new RecipeController.RecipeRequest("Soupe", null, 1,
        List.of(new RecipeController.IngredientRequest("Carotte", new BigDecimal("2"), "pièce", null, null, null, null)),
        List.of("Cuire."));
    var created = recipes.create(owner, request).getBody();
    assertThat(created).isNotNull();
    ByteArrayOutputStream png = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png", png);
    var file = new MockMultipartFile("file", "soupe.png", "image/png", png.toByteArray());
    images.upload(created.id(), owner, file);
    assertThat(recipes.get(created.id(), owner).hasImage()).isTrue();
    assertThat(images.get(created.id(), owner).getBody()).isEqualTo(png.toByteArray());
    assertThatThrownBy(() -> images.get(created.id(), jwt("other"))).isInstanceOf(RecipeNotFoundException.class);
  }

  @Test
  void stores_recipe_macros_per_portion_independently_of_ingredients() {
    Jwt owner = jwt("manual-macros");
    var request = new RecipeController.RecipeRequest("Omelette", null, 2,
        List.of(new RecipeController.IngredientRequest("Œufs", new BigDecimal("2"), "pièce", null, null, null, null)),
        List.of("Cuire les œufs."), new BigDecimal("150"), new BigDecimal("12"), new BigDecimal("2"), new BigDecimal("10"), null);
    var saved = recipes.create(owner, request).getBody();
    assertThat(saved).isNotNull();
    assertThat(recipes.get(saved.id(), owner).kcalPerServing()).isEqualByComparingTo("150");
    assertThat(recipes.get(saved.id(), owner).ingredients().getFirst().kcal100g()).isNull();
  }

  @Test
  void creates_a_batch_atomically() {
    Jwt owner = jwt("batch-owner");
    var first = new RecipeController.RecipeRequest("Porridge", null, 1,
        List.of(new RecipeController.IngredientRequest("Avoine", new BigDecimal("50"), "g", null, null, null, null)),
        List.of("Cuire l'avoine."));
    var second = new RecipeController.RecipeRequest("Salade", null, 2,
        List.of(new RecipeController.IngredientRequest("Tomate", new BigDecimal("2"), "pièce", null, null, null, null)),
        List.of("Couper les tomates."));
    var created = recipes.createBatch(owner, List.of(first, second)).getBody();
    assertThat(created).hasSize(2);
    assertThat(recipes.list(owner, null, 0, 20)).hasSize(2);
    assertThatThrownBy(() -> recipes.createBatch(owner, List.of(first, new RecipeController.RecipeRequest(
        null, null, 1, second.ingredients(), second.steps())))).isInstanceOf(Exception.class);
    assertThat(recipes.list(owner, null, 0, 20)).hasSize(2);
  }

  private Jwt jwt(String subject) {
    Instant now = Instant.now();
    return Jwt.withTokenValue("test").header("alg", "none").subject(subject)
        .issuedAt(now).expiresAt(now.plusSeconds(300)).build();
  }
}
