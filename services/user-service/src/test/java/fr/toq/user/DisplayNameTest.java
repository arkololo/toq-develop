package fr.toq.user;

import static org.assertj.core.api.Assertions.assertThat;

import fr.toq.user.api.UserProfileController.ProfileRequest;
import fr.toq.user.application.UserProfileService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:user;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "TOQ_OIDC_ISSUER_URI=http://issuer.test/realms/TOQ",
    "TOQ_OIDC_JWK_SET_URI=http://127.0.0.1:65534/jwks"
})
class DisplayNameTest {
  @Autowired UserProfileService profiles;

  @Test void returns_the_latest_nickname_for_an_existing_subject() {
    Jwt user = Jwt.withTokenValue("test").header("alg", "none").subject("name-change-test")
        .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
    profiles.saveProfile(user, new ProfileRequest("Ancien pseudo"));
    profiles.saveProfile(user, new ProfileRequest("Nouveau pseudo"));
    assertThat(profiles.displayNames(java.util.List.of("name-change-test", "unknown")))
        .containsEntry("name-change-test", "Nouveau pseudo").doesNotContainKey("unknown");
  }
}
