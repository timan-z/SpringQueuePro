package com.springqprobackend.springqpro.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/* 2025-11-24-NOTE(S):+DEBUG: JWT INTEGRATION PHASE!!!
Of course, this will be the Model representation for like a "User Account" I guess. (Still not 100% sure how I want to go about this).
*/
@Entity
@Table(name="users")
public class UserEntity {
    @Id
    @Column(nullable=false, unique=true)
    private String email;

    @Column(nullable=false)
    private String passwordHash;

    @Column(nullable=false)
    private String role = "USER"; // USER or ADMIN

    public UserEntity() {}

    public UserEntity(String email, String passwordHash) {
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getRole() { return role; }

    public void setEmail(String email) { this.email = email; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void setRole(String role) { this.role = role; }
}
