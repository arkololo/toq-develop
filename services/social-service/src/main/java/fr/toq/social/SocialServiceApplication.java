package fr.toq.social;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class SocialServiceApplication {
    public static void main(String[] args) { SpringApplication.run(SocialServiceApplication.class, args); }
}
