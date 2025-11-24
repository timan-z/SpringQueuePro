package com.springqprobackend.springqpro.security;

import com.springqprobackend.springqpro.domain.UserEntity;
import com.springqprobackend.springqpro.repository.UserRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {
    private final UserRepository userRepo;
    public CustomUserDetailsService(UserRepository userRepo) {
        this.userRepo = userRepo;
    }
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        UserEntity user = userRepo.findById(email).orElseThrow(() -> new UsernameNotFoundException(email));

        return User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash())   // BCrypt-hashed password
                .roles(user.getRole())
                .build();
    }
}
