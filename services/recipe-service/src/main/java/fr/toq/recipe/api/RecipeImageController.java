package fr.toq.recipe.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/recipes/{id}/image")
public class RecipeImageController {
  private final JdbcClient jdbc;
  private final RecipeController recipes;
  private final RecipeTemplateController templates;
  private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
  public RecipeImageController(JdbcClient jdbc, RecipeController recipes, RecipeTemplateController templates) { this.jdbc = jdbc; this.recipes = recipes; this.templates = templates; }

  @GetMapping
  public ResponseEntity<byte[]> get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    recipes.get(id, jwt);
    ImageData image = jdbc.sql("SELECT mime_type,data FROM recipe_image WHERE recipe_id=:id").param("id", id)
        .query((rs, row) -> new ImageData(rs.getString("mime_type"), rs.getBytes("data"))).optional()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.mime())).cacheControl(CacheControl.noStore()).body(image.bytes());
  }

  @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Transactional
  public ResponseEntity<Void> upload(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt, @RequestParam("file") MultipartFile file) {
    recipes.get(id, jwt);
    ImageValidator.ImageData image = ImageValidator.read(file);
    saveImage(id, image);
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/template/{templateId}")
  @Transactional
  public ResponseEntity<Void> useTemplateImage(@PathVariable UUID id, @PathVariable String templateId, @AuthenticationPrincipal Jwt jwt) {
    RecipeController.RecipeResponse recipe = recipes.get(id, jwt);
    if (!templateId.equals(recipe.sourceTemplateId())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Modèle différent de la recette.");
    String imageUrl = templates.imageUrlFor(templateId);
    if (imageUrl == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ce modèle n'a pas de photo.");
    try {
      HttpRequest request = HttpRequest.newBuilder(URI.create(imageUrl)).timeout(Duration.ofSeconds(7)).GET().build();
      HttpResponse<java.io.InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
      if (response.statusCode() != 200) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Photo du modèle indisponible.");
      try (var stream = response.body()) {
        saveImage(id, ImageValidator.readBytes(stream.readNBytes(3_000_001)));
      }
      return ResponseEntity.noContent().build();
    } catch (ResponseStatusException e) { throw e; }
      catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Photo du modèle indisponible."); }
  }

  private void saveImage(UUID id, ImageValidator.ImageData image) {
    int changed = jdbc.sql("UPDATE recipe_image SET mime_type=:mime,data=:data WHERE recipe_id=:id")
        .param("id", id).param("mime", image.mimeType()).param("data", image.bytes()).update();
    if (changed == 0) jdbc.sql("INSERT INTO recipe_image(recipe_id,mime_type,data) VALUES(:id,:mime,:data)")
        .param("id", id).param("mime", image.mimeType()).param("data", image.bytes()).update();
  }

  @DeleteMapping
  public ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    recipes.get(id, jwt);
    jdbc.sql("DELETE FROM recipe_image WHERE recipe_id=:id").param("id", id).update();
    return ResponseEntity.noContent().build();
  }
  private record ImageData(String mime, byte[] bytes) {}
}
