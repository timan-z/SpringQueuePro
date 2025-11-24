package com.springqprobackend.springqpro.controller;

// 2025-11-24-NOTE(S):+DEBUG: This is the Controller file for registering and making an account.

import com.springqprobackend.springqpro.domain.UserEntity;
import com.springqprobackend.springqpro.repository.UserRepository;
import com.springqprobackend.springqpro.security.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthenticationController {
    private final UserRepository userRepo;
    private final PasswordEncoder encoder;
    private final JwtUtil jwt;
    public AuthenticationController(UserRepository userRepo, PasswordEncoder encoder, JwtUtil jwt) {
        this.userRepo = userRepo;
        this.encoder = encoder;
        this.jwt = jwt;
    }
    record LoginReq(String email, String password) {}
    record RegisterReq(String email, String password) {}

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody RegisterReq req) {
        if(userRepo.existsById(req.email())) throw new RuntimeException("Email is already registered to an account.");
        String hash = encoder.encode(req.password());
        userRepo.save(new UserEntity(req.email(), hash));
        return Map.of("status", "registered");
    }
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginReq req) {
        UserEntity user = userRepo.findById(req.email()).orElseThrow(() -> new RuntimeException("Invalid credentials."));
        if(!encoder.matches(req.password(), user.getPasswordHash())) throw new RuntimeException("Invalid credentials");
        String token = jwt.generateToken(user.getEmail());
        return Map.of("token", token);
    }
}
