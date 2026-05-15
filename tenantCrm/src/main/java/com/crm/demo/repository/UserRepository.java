package com.crm.demo.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.crm.demo.model.User;

public interface UserRepository extends JpaRepository<User, Long> {

    // LOGIN WITH USERNAME
    User findByUsername(String username);

    // LOGIN WITH EMAIL
    User findByEmail(String email);

    // LOGIN WITH USERNAME OR EMAIL
    User findByUsernameOrEmail(String username, String email);

}