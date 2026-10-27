package fr.toq.social;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import fr.toq.social.api.SocialController;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.Jwt;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:social;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "TOQ_OIDC_ISSUER_URI=http://issuer.test/realms/TOQ",
    "TOQ_OIDC_JWK_SET_URI=http://127.0.0.1:65534/jwks",
    "toq.user-base-url=http://127.0.0.1:65534"
})
class SocialDisplayNameTest {
  @Autowired JdbcClient jdbc;

  @Test void old_publications_and_comments_show_current_nickname() throws Exception {
    UUID publication = UUID.randomUUID();
    UUID comment = UUID.randomUUID();
    String subject = "user-" + UUID.randomUUID();
    jdbc.sql("INSERT INTO publication(id,author_sub,author_name,recipe_id,recipe_title) VALUES(:id,:sub,'Ancien nom',:recipe,'Riz')")
        .param("id", publication).param("sub", subject).param("recipe", UUID.randomUUID()).update();
    jdbc.sql("INSERT INTO publication_comment(id,publication_id,author_sub,author_name,body) VALUES(:id,:publication,:sub,'Ancien nom','Bonjour')")
        .param("id", comment).param("publication", publication).param("sub", subject).update();

    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/api/v1/users/display-names", exchange -> {
      byte[] result = ("{\"" + subject + "\":\"Nouveau pseudo\"}").getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, result.length);
      try (var body = exchange.getResponseBody()) { body.write(result); }
    });
    server.start();
    try {
      SocialController social = new SocialController(jdbc, "http://127.0.0.1:65534", "http://127.0.0.1:" + server.getAddress().getPort());
      Jwt viewer = Jwt.withTokenValue("test").header("alg", "none").subject("viewer")
          .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
      assertThat(social.detail(publication, viewer).authorName()).isEqualTo("Nouveau pseudo");
      assertThat(social.comments(publication, 0, viewer)).extracting(SocialController.Comment::authorName).contains("Nouveau pseudo");
    } finally { server.stop(0); }
  }
}
