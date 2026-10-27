package fr.toq.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import fr.toq.social.api.SocialController;
import fr.toq.social.api.SocialController.CommentInput;
import fr.toq.social.api.SocialController.PublishInput;
import fr.toq.social.api.PublicationImageController;
import com.sun.net.httpserver.HttpServer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:social;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "TOQ_OIDC_ISSUER_URI=http://issuer.test/realms/TOQ",
    "TOQ_OIDC_JWK_SET_URI=http://127.0.0.1:65534/jwks",
    "toq.user-base-url=http://127.0.0.1:65534"
})
class SocialInteractionTest {
  @Autowired SocialController social;
  @Autowired JdbcClient jdbc;
  @Autowired PublicationImageController images;

  @Test void second_user_can_like_comment_and_favorite_publication() {
    UUID id = UUID.randomUUID();
    jdbc.sql("INSERT INTO publication(id,author_sub,author_name,recipe_id,recipe_title,caption) VALUES(:id,'alice','Alice',:recipe,'Riz aux légumes','Mon plat préféré')")
        .param("id", id).param("recipe", UUID.randomUUID()).update();
    Jwt bob = jwt("bob");
    assertThat(social.feed(0, 12, bob)).anyMatch(item -> item.id().equals(id));
    assertThat(social.like(id, bob).likes()).isEqualTo(1);
    assertThat(social.like(id, bob).likes()).isEqualTo(1);
    assertThat(social.favorite(id, bob).favorite()).isTrue();
    assertThat(social.favorites(0, 12, bob)).extracting(SocialController.Publication::id).contains(id);
    assertThat(social.favorites(0, 12, jwt("alice"))).extracting(SocialController.Publication::id).doesNotContain(id);
    social.unfavorite(id, bob);
    assertThat(social.favorites(0, 12, bob)).extracting(SocialController.Publication::id).doesNotContain(id);
    social.comment(id, bob, new CommentInput("Très bon !"));
    assertThat(social.comments(id, 0, jwt("alice"))).anyMatch(item -> item.body().equals("Très bon !"));
    assertThat(social.detail(id, jwt("alice")).comments()).isEqualTo(1);
    assertThat(social.detail(id, jwt("alice")).liked()).isFalse();
  }

  @Test void only_author_can_upload_a_publication_photo() throws Exception {
    UUID id = UUID.randomUUID();
    jdbc.sql("INSERT INTO publication(id,author_sub,author_name,recipe_id,recipe_title) VALUES(:id,'photo-owner','Auteur',:recipe,'Soupe')")
        .param("id", id).param("recipe", UUID.randomUUID()).update();
    ByteArrayOutputStream png = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png", png);
    var file = new MockMultipartFile("file", "soupe.png", "image/png", png.toByteArray());
    assertThatThrownBy(() -> images.upload(id, jwt("intruder"), file)).isInstanceOf(ResponseStatusException.class);
    images.upload(id, jwt("photo-owner"), file);
    assertThat(social.detail(id, jwt("photo-owner")).hasImage()).isTrue();
    assertThat(images.get(id, jwt("intruder")).getBody()).isEqualTo(png.toByteArray());
  }

  @Test void publishing_a_recipe_copies_its_existing_photo() throws Exception {
    ByteArrayOutputStream png = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png", png);
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/api/v1/recipes/", exchange -> {
      byte[] body = exchange.getRequestURI().getPath().endsWith("/image") ? png.toByteArray()
          : "{\"title\":\"Plat illustré\",\"hasImage\":true}".getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, body.length);
      try (var stream = exchange.getResponseBody()) { stream.write(body); }
    });
    server.start();
    try {
      var localSocial = new SocialController(jdbc, "http://127.0.0.1:" + server.getAddress().getPort(), "http://127.0.0.1:65534");
      var published = localSocial.publish(jwt("image-author"), new PublishInput(UUID.randomUUID(), "Mon plat"));
      assertThat(published.hasImage()).isTrue();
      assertThat(images.get(published.id(), jwt("image-author")).getBody()).isEqualTo(png.toByteArray());
    } finally { server.stop(0); }
  }

  private Jwt jwt(String subject) {
    Instant now = Instant.now();
    return Jwt.withTokenValue("test").header("alg", "none").subject(subject).claim("preferred_username", subject)
        .issuedAt(now).expiresAt(now.plusSeconds(300)).build();
  }
}
