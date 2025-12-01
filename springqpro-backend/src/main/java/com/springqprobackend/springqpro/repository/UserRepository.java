package com.springqprobackend.springqpro.repository;

import com.springqprobackend.springqpro.domain.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/* UserRepository.java
--------------------------------------------------------------------------------------------------
This is the JPA (Java Persistence API) repository for UserEntity.
- Since it extends JpaRepository, it'll have all the basic CRUD for authentication and
user management.
--------------------------------------------------------------------------------------------------
*/

public interface UserRepository extends JpaRepository<UserEntity, String> { }
