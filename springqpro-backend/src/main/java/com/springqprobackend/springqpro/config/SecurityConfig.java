package com.springqprobackend.springqpro.config;

import com.springqprobackend.springqpro.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/* SecurityConfig.java
--------------------------------------------------------------------------------------------------
[HISTORY]:
Originally, the project had no security at all. Endpoints were completely open.
Once the system became stateful (PostgreSQL, Redis), authentication and proper
authorization became mandatory.
SecurityConfig now serves as the top-level Spring Security configuration.

[CURRENT ROLE]:
Configures:
  - stateless JWT authentication
  - JwtAuthenticationFilter chain
  - route protection for REST + GraphQL
  - CSRF disabling (since the system is token-based)
  - session policy (STATELESS)

Also integrates seamlessly with:
  - AuthenticationController
  - CustomUserDetailsService
  - GraphQL security checks

[FUTURE WORK]:
CloudQueue may adopt:
  - CORS tightening
  - role-based access control
  - AWS Cognito JWT verification
--------------------------------------------------------------------------------------------------
*/
// 2025-12-05-NOTE:+DEBUG: Additions today are related to configuring CORS on the backend so my frontend can send API requests.

@EnableWebSecurity
@EnableMethodSecurity
@Configuration
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtFilter;
    public SecurityConfig(JwtAuthenticationFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }
    /* Supported routes:
    PUBLIC: /auth/**, /actuator/health
    PRIVATE: /api/tasks/**, /graphql
    */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        /* IMPORTANT-NOTE (line of code below this comment block) - Enabling CORS so that:
        - GraphiQL (my Browser client) can call POST /graphql
        - Future REACT frontend / Cloud deployment / API Gateway can access the API (obv important for deployment).
        - Modern Spring Security requires explicit CORS in stateless JWT apps.
        withDefaults() basically means:
        - Use Spring Boot's auto CORS config (from application.yml)
        - Do not block browser to backend API calls.
        */
        http.cors(Customizer.withDefaults());
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/auth/register").permitAll()
                        .requestMatchers("/auth/login").permitAll()
                        .requestMatchers("/auth/refresh").permitAll()
                        // GraphQL endpoint - EDIT: Adjusted so secured only for POST since GraphQL ops only use POST. (GET shouldn't req authentication).
                        .requestMatchers(HttpMethod.POST,"/graphql").authenticated()
                        // REST endpoints (I know that GraphQL is my main thing now -- but I might as well have these, also it's TaskRestController that will replace these).
                        .requestMatchers("/api/tasks/**").authenticated()
                        .anyRequest().permitAll()
                )
                // Attach JWT Filter (run BEFORE UsernamePasswordAuthenticationFilter):
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
    @Bean
    public PasswordEncoder encoder() {
        return new BCryptPasswordEncoder();
    }
    // 2025-12-05-NOTE: ADDED BELOW FOR CORS STUFF.
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // Allowed origins for frontend
        config.setAllowedOrigins(List.of(
                "http://localhost:5173",                // local dev (TO-DO: append Netlify link later when frontend is deployed).
                "https://springqueuepro-production.up.railway.app"
        ));
        // Important for JWT/Auth
        config.setAllowCredentials(true);
        config.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "DELETE", "OPTIONS"
        ));
        config.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type"
        ));
        // Optional: Expose Authorization header if needed
        config.setExposedHeaders(List.of("Authorization"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
