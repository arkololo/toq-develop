package fr.toq.recipe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// Keycloak authentication is added in Phase 2; no generated local account.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class RecipeServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RecipeServiceApplication.class, args);
    }
}

