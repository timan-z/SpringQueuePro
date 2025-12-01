package com.springqprobackend.springqpro.security;

import com.springqprobackend.springqpro.domain.UserEntity;
import com.springqprobackend.springqpro.repository.UserRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/* CustomUserDetailsService.java
--------------------------------------------------------------------------------------------------
This is the file that bridges UserEntity with Spring Security's UserDetails system to support
authentication, authorization filters, and JWT validation. Its role in the SpringQueuePro system
is to basically look up users in the PostgreSQL storage and adapt them into Spring Security's model.
- Before its addition, in the initial stages of the JWT phase, all login logic was in the controllers.
--------------------------------------------------------------------------------------------------
*/

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
