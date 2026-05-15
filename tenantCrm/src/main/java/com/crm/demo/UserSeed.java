package com.crm.demo;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.crm.demo.model.User;
import com.crm.demo.repository.UserRepository;

@Component
public class UserSeed implements CommandLineRunner {

    private final UserRepository userRepository;

    public UserSeed(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void run(String... args) throws Exception {

        User user = userRepository.findByUsername("superadmin");

        // IF USER NOT EXISTS
        if (user == null) {

            user = new User();
        }

        // ALWAYS UPDATE VALUES
        user.setUsername("superadmin");
        user.setEmail("superadmin@crm.com");
        user.setPassword("superadmin123");
        user.setRole("SUPER_ADMIN");

        userRepository.save(user);

        System.out.println("Super Admin Created/Updated");
    }
}