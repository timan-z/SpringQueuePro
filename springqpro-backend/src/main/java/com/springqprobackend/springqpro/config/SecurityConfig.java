package com.springqprobackend.springqpro.config;

import com.springqprobackend.springqpro.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

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
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // GraphQL endpoint
                        .requestMatchers("/graphql").authenticated()
                        // REST endpoints (I know that GraphQL is my main thing now -- but I might as well have these, also it's TaskRestController that will replace these).
                        .requestMatchers("/api/tasks/**").authenticated()
                        .anyRequest().permitAll()
                )
                // Attach JWT Filter:
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
