package com.springqprobackend.springqpro.security;

import com.springqprobackend.springqpro.service.TaskService;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;
    public JwtAuthenticationFilter(JwtUtil jwtUtil, UserDetailsService uds) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = uds;
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if(authHeader == null || !authHeader.startsWith("Bearer ")) {
            // No JWT – let the request continue unauthenticated
            chain.doFilter(request, response);
            return;
        }
        String token = authHeader.substring(7);
        try {
            // 2025-11-27-NOTE: ADDITION BELOW TO FIX DESIGN FLAW W/ REFRESH TOKEN BEING APPROVED:
            String type = jwtUtil.getTokenType(token);
            if (!"access".equals(type)) {
                // This includes: null, "refresh", malformed, missing type claim.
                logger.info("[JwtAuthenticationFilter] Refresh Token rejected for API Authentication attempt.");
                SecurityContextHolder.clearContext();
                chain.doFilter(request, response);
                return;
            }
            // 2025-11-27-NOTE: ADDITION ABOVE TO FIX DESIGN FLAW W/ REFRESH TOKEN BEING APPROVED.
            String email = jwtUtil.validateAndGetSubject(token);
            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                );
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                logger.info("[JwtAuthenticationFilter] Authorities for {} => {}", email, auth.getAuthorities());

                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        } catch (Exception ex) {
            // Token invalid/expired -> do NOT authenticate, just continue.
            // SecurityContext remains empty -> downstream sees request as anonymous.
            SecurityContextHolder.clearContext();
            if(ex instanceof ExpiredJwtException) {
                logger.warn("JWT expired: {}");
            }
            if(ex instanceof JwtException) {
                logger.warn("Invalid JWT: {}");
            }
        }
        chain.doFilter(request, response);
    }
}
