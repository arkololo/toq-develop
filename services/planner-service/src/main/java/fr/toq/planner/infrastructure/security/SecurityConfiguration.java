package fr.toq.planner.infrastructure.security;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
@Configuration(proxyBeanMethods=false) class SecurityConfiguration {
 @Bean SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception { return http.csrf(AbstractHttpConfigurer::disable).cors(AbstractHttpConfigurer::disable).authorizeHttpRequests(a->a.requestMatchers(EndpointRequest.to("health")).permitAll().requestMatchers("/error").permitAll().requestMatchers("/api/v1/plans/**","/api/v1/shopping-lists/**").authenticated().anyRequest().denyAll()).oauth2ResourceServer(o->o.jwt(j->{})).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS)).requestCache(AbstractHttpConfigurer::disable).formLogin(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable).build(); }
}
