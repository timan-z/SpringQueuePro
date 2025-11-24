package com.springqprobackend.springqpro.repository;

import com.springqprobackend.springqpro.domain.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

// 2025-11-24-NOTE(S):+DEBUG: JWT INTEGRATION PHASE!!! - Basically TaskRepository but for UserEntity.
public interface UserRepository extends JpaRepository<UserEntity, String> {
}
