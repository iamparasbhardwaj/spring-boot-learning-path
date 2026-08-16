package com.interview.taskapi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * INTERVIEW: "Explain Spring Security's architecture."
 * It is ONE servlet filter (DelegatingFilterProxy -> FilterChainProxy) that delegates
 * to an ordered chain of security filters. Request flow, roughly:
 *
 *   SecurityContextPersistenceFilter  (load existing auth)
 *     -> Authentication filter        (form login / basic / your JWT filter)
 *          -> AuthenticationManager -> ProviderManager -> AuthenticationProvider
 *              -> UserDetailsService.loadUserByUsername() + PasswordEncoder.matches()
 *     -> ExceptionTranslationFilter   (401 vs 403)
 *     -> AuthorizationFilter          (does this principal have access to this URL?)
 *
 * For JWT you insert your own OncePerRequestFilter BEFORE the username/password filter,
 * validate the token, and set the Authentication on the SecurityContextHolder.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                // Disabled because this is a stateless API consumed by non-browser clients.
                // INTERVIEW: CSRF protects cookie-authenticated browser sessions. A
                // token-in-header API is not vulnerable the same way. Know WHY, do not
                // just say "we disable it".
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/h2-console/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/tasks/**").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .headers(h -> h.frameOptions(f -> f.sameOrigin()))  // so /h2-console renders
                .httpBasic(Customizer.withDefaults())
                .build();
    }

    @Bean
    UserDetailsService users(PasswordEncoder encoder) {
        return new InMemoryUserDetailsManager(
                User.withUsername("user").password(encoder.encode("password")).roles("USER").build(),
                User.withUsername("admin").password(encoder.encode("password")).roles("ADMIN").build()
        );
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        // BCrypt: adaptive, salted per hash. Never MD5/SHA for passwords.
        return new BCryptPasswordEncoder();
    }
}
