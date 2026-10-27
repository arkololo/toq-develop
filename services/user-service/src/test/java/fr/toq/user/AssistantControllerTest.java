package fr.toq.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import fr.toq.user.api.AssistantController;
import fr.toq.user.api.AssistantController.ChatInput;
import fr.toq.user.api.UserProfileController.ProfileRequest;
import fr.toq.user.application.UserProfileService;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:assistant;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "TOQ_OIDC_ISSUER_URI=http://issuer.test/realms/TOQ",
    "TOQ_OIDC_JWK_SET_URI=http://127.0.0.1:65534/jwks"
})
class AssistantControllerTest {
  @Autowired UserProfileService profiles;

  @Test void a_goal_proposal_needs_confirmation_and_is_bound_to_one_user() throws Exception {
    Jwt alice = jwt("assistant-alice"), bob = jwt("assistant-bob");
    profiles.saveProfile(alice, new ProfileRequest("Alice"));
    LocalDate today = LocalDate.now(ZoneId.of("Europe/Paris"));
    AtomicReference<String> answer = new AtomicReference<>("{\"reply\":\"Je propose un objectif.\",\"action\":{\"type\":\"SET_GOAL\",\"validFrom\":\"" + today + "\",\"kcal\":2400}}");
    AtomicInteger createdRecipes = new AtomicInteger(), plannedMeals = new AtomicInteger(), batches = new AtomicInteger(), updates = new AtomicInteger(), weekReplacements = new AtomicInteger();
    UUID existingRecipeId = UUID.randomUUID();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      String body;
      if (exchange.getRequestURI().getPath().endsWith(":generateContent")) {
        assertThat(exchange.getRequestHeaders().getFirst("x-goog-api-key")).isEqualTo("test-only-key");
        var wrapper = new ObjectMapper().createObjectNode();
        wrapper.putArray("candidates").addObject().putObject("content")
            .putArray("parts").addObject().put("text", answer.get());
        body = wrapper.toString();
      } else if (exchange.getRequestMethod().equals("POST") && exchange.getRequestURI().getPath().equals("/api/v1/recipes/batch")) {
        batches.incrementAndGet();
        body = "[{\"id\":\"" + UUID.randomUUID() + "\"},{\"id\":\"" + UUID.randomUUID() + "\"}]";
      } else if (exchange.getRequestMethod().equals("GET") && exchange.getRequestURI().getPath().equals("/api/v1/recipes/" + existingRecipeId)) {
        body = "{\"id\":\"" + existingRecipeId + "\",\"title\":\"Pasta salad\",\"description\":\"Easy\",\"baseServings\":2,\"version\":0,\"ingredients\":[{\"name\":\"Tomato\",\"quantity\":2,\"unit\":\"piece\"}],\"steps\":[\"Cut tomatoes.\"]}";
      } else if (exchange.getRequestMethod().equals("GET") && exchange.getRequestURI().getPath().equals("/api/v1/recipes")) {
        body = "[{\"id\":\"" + existingRecipeId + "\",\"title\":\"Pasta salad\"}]";
      } else if (exchange.getRequestMethod().equals("GET") && exchange.getRequestURI().getPath().equals("/api/v1/recipes/templates/42")) {
        body = "{\"id\":\"42\",\"title\":\"Pasta salad\",\"originalTitle\":\"Pasta salad\",\"category\":\"Vegetarian\",\"area\":\"Italian\",\"ingredients\":[{\"name\":\"Tomato\",\"measure\":\"2 pieces\"}],\"steps\":[\"Cut tomatoes.\"],\"translated\":false}";
      } else if (exchange.getRequestMethod().equals("PUT") && exchange.getRequestURI().getPath().equals("/api/v1/recipes/" + existingRecipeId)) {
        updates.incrementAndGet();
        body = "{\"id\":\"" + existingRecipeId + "\",\"title\":\"Salade de pâtes\"}";
      } else if (exchange.getRequestMethod().equals("POST") && exchange.getRequestURI().getPath().equals("/api/v1/recipes")) {
        createdRecipes.incrementAndGet();
        assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer test-token");
        body = "{\"id\":\"" + UUID.randomUUID() + "\",\"title\":\"Soupe maison\"}";
      } else if (exchange.getRequestMethod().equals("POST") && exchange.getRequestURI().getPath().contains("/meals")) {
        plannedMeals.incrementAndGet();
        body = "{\"id\":\"" + UUID.randomUUID() + "\"}";
      } else if (exchange.getRequestMethod().equals("PUT") && exchange.getRequestURI().getPath().endsWith("/meals")) {
        weekReplacements.incrementAndGet();
        body = "[{},{}]";
      } else body = "[]";
      byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, bytes.length);
      try (var stream = exchange.getResponseBody()) { stream.write(bytes); }
    });
    server.start();
    try {
      String base = "http://127.0.0.1:" + server.getAddress().getPort();
      var assistant = new AssistantController(profiles, "test-only-key", "gemini-3.5-flash-lite", base, base, base);
      var response = assistant.chat(alice, new ChatInput("Je veux un objectif de 2400 kcal", List.of()));
      assertThat(response.proposal()).isNotNull();
      assertThat(profiles.getGoals(alice.getSubject())).isEmpty();
      assertThatThrownBy(() -> assistant.confirm(response.proposal().id(), bob)).isInstanceOf(ResponseStatusException.class);
      assertThat(assistant.confirm(response.proposal().id(), alice).type()).isEqualTo("SET_GOAL");
      assertThat(profiles.getGoals(alice.getSubject()).getFirst().kcal()).isEqualByComparingTo("2400");
      assertThatThrownBy(() -> assistant.confirm(response.proposal().id(), alice)).isInstanceOf(ResponseStatusException.class);

      answer.set("{\"reply\":\"Je propose une soupe.\",\"action\":{\"type\":\"CREATE_RECIPE\",\"title\":\"Soupe maison\",\"baseServings\":2,\"ingredients\":[{\"name\":\"Carotte\",\"quantity\":200,\"unit\":\"g\"}],\"steps\":[\"Cuire les carottes.\"]}}");
      var recipe = assistant.chat(alice, new ChatInput("Crée une soupe", List.of()));
      assertThat(createdRecipes.get()).isZero();
      assertThat(assistant.confirm(recipe.proposal().id(), alice).type()).isEqualTo("CREATE_RECIPE");
      assertThat(createdRecipes.get()).isEqualTo(1);

      answer.set("{\"reply\":\"Je propose ce repas.\",\"action\":{\"type\":\"ADD_MEAL\",\"recipeId\":\"" + UUID.randomUUID() + "\",\"date\":\"" + today + "\",\"slot\":\"DINNER\",\"portions\":2}}");
      var meal = assistant.chat(alice, new ChatInput("Ajoute un repas ce soir", List.of()));
      assertThat(plannedMeals.get()).isZero();
      assertThat(assistant.confirm(meal.proposal().id(), alice).type()).isEqualTo("ADD_MEAL");
      assertThat(plannedMeals.get()).isEqualTo(1);

      LocalDate monday = today.minusDays(today.getDayOfWeek().getValue() - 1).plusWeeks(1);
      StringBuilder days = new StringBuilder();
      for (int day = 0; day < 7; day++) for (String slot : List.of("BREAKFAST", "LUNCH", "DINNER")) {
        if (!days.isEmpty()) days.append(',');
        days.append("{\"recipeId\":\"").append(existingRecipeId).append("\",\"date\":\"")
            .append(monday.plusDays(day)).append("\",\"slot\":\"").append(slot).append("\",\"portions\":1}");
      }
      answer.set("{\"reply\":\"Voici votre semaine.\",\"action\":{\"type\":\"REPLACE_WEEK\",\"weekStart\":\"" + monday + "\",\"meals\":[" + days + "]}}");
      var weekProposal = assistant.chat(alice, new ChatInput("Prépare toute ma semaine", List.of()));
      assertThat(weekProposal.proposal().preview().path("meals")).hasSize(21);
      assertThat(weekProposal.proposal().preview().path("meals").get(0).path("recipeTitle").asText()).isEqualTo("Pasta salad");
      assertThat(weekReplacements.get()).isZero();
      assertThatThrownBy(() -> assistant.confirm(weekProposal.proposal().id(), bob)).isInstanceOf(ResponseStatusException.class);
      assertThat(assistant.confirm(weekProposal.proposal().id(), alice).type()).isEqualTo("REPLACE_WEEK");
      assertThat(weekReplacements.get()).isEqualTo(1);

      answer.set("{\"reply\":\"Voici deux recettes.\",\"action\":{\"type\":\"CREATE_RECIPES\",\"recipes\":[{\"title\":\"Porridge\",\"baseServings\":1,\"ingredients\":[{\"name\":\"Avoine\",\"quantity\":50,\"unit\":\"g\"}],\"steps\":[\"Cuire l'avoine.\"]},{\"title\":\"Salade\",\"baseServings\":2,\"ingredients\":[{\"name\":\"Tomate\",\"quantity\":2,\"unit\":\"pièce\"}],\"steps\":[\"Couper les tomates.\"]}]}}");
      var batch = assistant.chat(alice, new ChatInput("Crée deux recettes", List.of()));
      assertThat(batches.get()).isZero();
      assertThat(batch.proposal().type()).isEqualTo("CREATE_RECIPES");
      assertThat(batch.proposal().preview().path("recipes").size()).isEqualTo(2);
      assertThat(assistant.confirm(batch.proposal().id(), alice).message()).contains("2 recettes");
      assertThat(batches.get()).isEqualTo(1);

      answer.set("{\"title\":\"Salade de pâtes\",\"description\":\"Facile\",\"ingredients\":[{\"name\":\"Tomate\",\"unit\":\"pièce\"}],\"steps\":[\"Couper les tomates.\"]}");
      var translated = assistant.translateOwnedRecipe(existingRecipeId, alice);
      assertThat(updates.get()).isZero();
      assertThatThrownBy(() -> assistant.confirm(translated.id(), bob)).isInstanceOf(ResponseStatusException.class);
      assertThat(assistant.confirm(translated.id(), alice).type()).isEqualTo("UPDATE_RECIPE");
      assertThat(updates.get()).isEqualTo(1);

      answer.set("{\"title\":\"Salade de pâtes\",\"category\":\"Végétarien\",\"area\":\"Italien\",\"ingredients\":[{\"name\":\"Tomate\",\"measure\":\"3 pièces\"}],\"steps\":[\"Couper les tomates.\"]}");
      var template = assistant.translateTemplate("42", alice);
      assertThat(template.path("translated").asBoolean()).isTrue();
      assertThat(template.path("ingredients").get(0).path("measure").asText()).isEqualTo("2 pieces");
    } finally { server.stop(0); }
  }

  private Jwt jwt(String subject) {
    return Jwt.withTokenValue("test-token").header("alg", "none").subject(subject)
        .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
  }
}
