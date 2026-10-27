package fr.toq.nutrition.infrastructure.security;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {
  @Bean JwtDecoder jwtDecoder(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer, @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwks, @Value("${TOQ_OIDC_AUDIENCE:toq-api}") String audience) {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwks).build();
    OAuth2TokenValidator<Jwt> audienceValidator = token -> token.getAudience().contains(audience) ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Audience invalide", null));
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(List.of(JwtValidators.createDefaultWithIssuer(issuer), audienceValidator)));
    return decoder;
  }
  @Bean SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception { return http.csrf(AbstractHttpConfigurer::disable).cors(AbstractHttpConfigurer::disable).authorizeHttpRequests(a -> a.requestMatchers(EndpointRequest.to("health")).permitAll().requestMatchers("/error").permitAll().requestMatchers("/api/v1/nutrition/**").authenticated().anyRequest().denyAll()).oauth2ResourceServer(o -> o.jwt(j -> {})).sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS)).requestCache(AbstractHttpConfigurer::disable).formLogin(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable).logout(AbstractHttpConfigurer::disable).build(); }
}
