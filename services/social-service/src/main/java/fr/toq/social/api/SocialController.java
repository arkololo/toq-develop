package fr.toq.social.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/social")
public class SocialController {
  private final JdbcClient jdbc;
  private final String recipeBaseUrl;
  private final String userBaseUrl;
  private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
  private final ObjectMapper mapper = new ObjectMapper();

  public SocialController(JdbcClient jdbc, @Value("${toq.recipe-base-url:http://recipe-service:8080}") String recipeBaseUrl,
                          @Value("${toq.user-base-url:http://user-service:8080}") String userBaseUrl) {
    this.jdbc = jdbc; this.recipeBaseUrl = recipeBaseUrl; this.userBaseUrl = userBaseUrl;
  }

  @PostMapping("/publications")
  @Transactional
  public Publication publish(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PublishInput input) {
    RecipeInfo recipe = authorizedRecipe(input.recipeId(), jwt.getTokenValue());
    UUID id = UUID.randomUUID();
    String author = currentName(jwt.getSubject(), jwt.getTokenValue(), displayName(jwt));
    jdbc.sql("INSERT INTO publication(id,author_sub,author_name,recipe_id,recipe_title,caption) VALUES(:id,:sub,:name,:recipe,:title,:caption)")
        .param("id", id).param("sub", jwt.getSubject()).param("name", author).param("recipe", input.recipeId())
        .param("title", recipe.title()).param("caption", input.caption()).update();
    if (recipe.hasImage()) copyRecipeImage(id, input.recipeId(), jwt.getTokenValue());
    return detail(id, jwt);
  }

  @GetMapping("/publications")
  public List<Publication> feed(@RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="12") int size, @AuthenticationPrincipal Jwt jwt) {
    if (page < 0 || page > 10000 || size < 1 || size > 30) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    return jdbc.sql("SELECT id FROM publication ORDER BY created_at DESC,id DESC LIMIT :limit OFFSET :offset")
        .param("limit", size).param("offset", page * size).query((rs, n) -> rs.getObject(1, UUID.class)).list().stream().map(id -> detail(id, jwt)).toList();
  }

  @GetMapping("/publications/favorites")
  public List<Publication> favorites(@RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="12") int size, @AuthenticationPrincipal Jwt jwt) {
    if (page < 0 || page > 10000 || size < 1 || size > 30) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    return jdbc.sql("SELECT p.id FROM publication p JOIN publication_favorite f ON f.publication_id=p.id WHERE f.user_sub=:sub ORDER BY p.created_at DESC,p.id DESC LIMIT :limit OFFSET :offset")
        .param("sub", jwt.getSubject()).param("limit", size).param("offset", page * size)
        .query((rs, n) -> rs.getObject(1, UUID.class)).list().stream().map(id -> detail(id, jwt)).toList();
  }

  @GetMapping("/publications/{id}")
  public Publication detail(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    var rows = jdbc.sql("SELECT id,author_sub,author_name,recipe_id,recipe_title,caption,created_at FROM publication WHERE id=:id")
        .param("id", id).query((rs,n) -> new Publication(rs.getObject("id", UUID.class), currentName(rs.getString("author_sub"), jwt.getTokenValue(), rs.getString("author_name")), rs.getObject("recipe_id", UUID.class),
            rs.getString("recipe_title"), rs.getString("caption"), rs.getTimestamp("created_at").toInstant(),
            count("publication_like", id), count("publication_comment", id), exists("publication_like", id, jwt.getSubject()),
            exists("publication_favorite", id, jwt.getSubject()), rs.getString("author_sub").equals(jwt.getSubject()), imageExists(id))).list();
    if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    return rows.getFirst();
  }

  @DeleteMapping("/publications/{id}")
  public void delete(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    int changed = jdbc.sql("DELETE FROM publication WHERE id=:id AND author_sub=:sub").param("id", id).param("sub", jwt.getSubject()).update();
    if (changed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
  }

  @PostMapping("/publications/{id}/likes")
  public Publication like(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) { return react(id, jwt, "publication_like", true); }
  @DeleteMapping("/publications/{id}/likes")
  public Publication unlike(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) { return react(id, jwt, "publication_like", false); }
  @PostMapping("/publications/{id}/favorites")
  public Publication favorite(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) { return react(id, jwt, "publication_favorite", true); }
  @DeleteMapping("/publications/{id}/favorites")
  public Publication unfavorite(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) { return react(id, jwt, "publication_favorite", false); }

  private Publication react(UUID id, Jwt jwt, String table, boolean enabled) {
    detail(id, jwt);
    if (enabled) jdbc.sql("INSERT INTO " + table + "(publication_id,user_sub) VALUES(:id,:sub) ON CONFLICT DO NOTHING")
        .param("id", id).param("sub", jwt.getSubject()).update();
    else jdbc.sql("DELETE FROM " + table + " WHERE publication_id=:id AND user_sub=:sub")
        .param("id", id).param("sub", jwt.getSubject()).update();
    return detail(id, jwt);
  }

  @GetMapping("/publications/{id}/comments")
  public List<Comment> comments(@PathVariable UUID id, @RequestParam(defaultValue="0") int page, @AuthenticationPrincipal Jwt jwt) {
    detail(id, jwt);
    if (page < 0 || page > 10000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    return jdbc.sql("SELECT id,author_sub,author_name,body,created_at FROM publication_comment WHERE publication_id=:id ORDER BY created_at ASC,id ASC LIMIT 30 OFFSET :offset")
        .param("id", id).param("offset", page * 30).query((rs,n) -> new Comment(rs.getObject("id", UUID.class), currentName(rs.getString("author_sub"), jwt.getTokenValue(), rs.getString("author_name")),
            rs.getString("body"), rs.getTimestamp("created_at").toInstant())).list();
  }

  @PostMapping("/publications/{id}/comments")
  public Comment comment(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CommentInput input) {
    detail(id, jwt);
    UUID commentId = UUID.randomUUID();
    String body = input.body().trim();
    if (body.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    String author = currentName(jwt.getSubject(), jwt.getTokenValue(), displayName(jwt));
    jdbc.sql("INSERT INTO publication_comment(id,publication_id,author_sub,author_name,body) VALUES(:id,:publication,:sub,:name,:body)")
        .param("id", commentId).param("publication", id).param("sub", jwt.getSubject()).param("name", author).param("body", body).update();
    return new Comment(commentId, author, body, Instant.now());
  }

  private long count(String table, UUID id) {
    return jdbc.sql("SELECT COUNT(*) FROM " + table + " WHERE publication_id=:id").param("id", id).query(Long.class).single();
  }
  private boolean exists(String table, UUID id, String sub) {
    return jdbc.sql("SELECT COUNT(*) FROM " + table + " WHERE publication_id=:id AND user_sub=:sub").param("id", id).param("sub", sub).query(Long.class).single() > 0;
  }
  private boolean imageExists(UUID id) { return jdbc.sql("SELECT COUNT(*) FROM publication_image WHERE publication_id=:id").param("id", id).query(Long.class).single() > 0; }
  private String displayName(Jwt jwt) {
    String name = jwt.getClaimAsString("preferred_username");
    if (name == null || name.isBlank()) name = "Membre TOQ";
    return name.length() > 120 ? name.substring(0, 120) : name;
  }
  private String currentName(String subject, String token, String fallback) {
    try {
      String body = mapper.writeValueAsString(List.of(subject));
      HttpRequest request = HttpRequest.newBuilder(URI.create(userBaseUrl + "/api/v1/users/display-names"))
          .timeout(Duration.ofSeconds(2)).header("Authorization", "Bearer " + token)
          .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() == 200) return mapper.readTree(response.body()).path(subject).asText(fallback);
    } catch (Exception ignored) { /* Keep the last known name if User Service is briefly unavailable. */ }
    return fallback;
  }
  private RecipeInfo authorizedRecipe(UUID id, String token) {
    try {
      HttpRequest request = HttpRequest.newBuilder(URI.create(recipeBaseUrl + "/api/v1/recipes/" + id))
          .timeout(Duration.ofSeconds(5)).header("Authorization", "Bearer " + token).GET().build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() == 404 || response.statusCode() == 403) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recette introuvable.");
      if (response.statusCode() != 200) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Service recettes indisponible.");
      JsonNode recipe = mapper.readTree(response.body());
      String title = recipe.path("title").asText("");
      if (title.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Recette incomplète.");
      return new RecipeInfo(title, recipe.path("hasImage").asBoolean(false));
    } catch (ResponseStatusException e) { throw e; }
      catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Service recettes indisponible."); }
  }

  private void copyRecipeImage(UUID publicationId, UUID recipeId, String token) {
    try {
      HttpRequest request = HttpRequest.newBuilder(URI.create(recipeBaseUrl + "/api/v1/recipes/" + recipeId + "/image"))
          .timeout(Duration.ofSeconds(5)).header("Authorization", "Bearer " + token).GET().build();
      HttpResponse<java.io.InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
      try (var stream = response.body()) {
        if (response.statusCode() != 200) return;
        ImageValidator.ImageData image = ImageValidator.readBytes(stream.readNBytes(3_000_001));
        jdbc.sql("INSERT INTO publication_image(publication_id,mime_type,data) VALUES(:id,:mime,:data)")
            .param("id", publicationId).param("mime", image.mimeType()).param("data", image.bytes()).update();
      }
    } catch (Exception ignored) { /* A publication remains possible if a recipe photo becomes unavailable. */ }
  }

  private record RecipeInfo(String title, boolean hasImage) {}

  public record PublishInput(@NotNull UUID recipeId, @Size(max=1000) String caption) {}
  public record CommentInput(@NotBlank @Size(max=1000) String body) {}
  public record Publication(UUID id, String authorName, UUID recipeId, String recipeTitle, String caption, Instant createdAt,
                            long likes, long comments, boolean liked, boolean favorite, boolean mine, boolean hasImage) {}
  public record Comment(UUID id, String authorName, String body, Instant createdAt) {}
}
