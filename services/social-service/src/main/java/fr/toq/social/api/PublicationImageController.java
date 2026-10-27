package fr.toq.social.api;

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
@RequestMapping("/api/v1/social/publications/{id}/image")
public class PublicationImageController {
  private final JdbcClient jdbc;
  private final SocialController social;
  public PublicationImageController(JdbcClient jdbc, SocialController social) { this.jdbc = jdbc; this.social = social; }

  @GetMapping
  public ResponseEntity<byte[]> get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    social.detail(id, jwt);
    ImageData image = jdbc.sql("SELECT mime_type,data FROM publication_image WHERE publication_id=:id").param("id", id)
        .query((rs, row) -> new ImageData(rs.getString("mime_type"), rs.getBytes("data"))).optional()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.mime())).cacheControl(CacheControl.noStore()).body(image.bytes());
  }
  @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Transactional
  public ResponseEntity<Void> upload(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt, @RequestParam("file") MultipartFile file) {
    requireOwner(id, jwt);
    ImageValidator.ImageData image = ImageValidator.read(file);
    int changed = jdbc.sql("UPDATE publication_image SET mime_type=:mime,data=:data WHERE publication_id=:id")
        .param("id", id).param("mime", image.mimeType()).param("data", image.bytes()).update();
    if (changed == 0) jdbc.sql("INSERT INTO publication_image(publication_id,mime_type,data) VALUES(:id,:mime,:data)")
        .param("id", id).param("mime", image.mimeType()).param("data", image.bytes()).update();
    return ResponseEntity.noContent().build();
  }
  @DeleteMapping
  public ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    requireOwner(id, jwt);
    jdbc.sql("DELETE FROM publication_image WHERE publication_id=:id").param("id", id).update();
    return ResponseEntity.noContent().build();
  }
  private void requireOwner(UUID id, Jwt jwt) { if (!social.detail(id, jwt).mine()) throw new ResponseStatusException(HttpStatus.NOT_FOUND); }
  private record ImageData(String mime, byte[] bytes) {}
}
