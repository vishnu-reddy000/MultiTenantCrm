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

        User existingUser = userRepository.findByUsername("superadmin");

        if (existingUser == null) {

            User user = new User();

            user.setUsername("superadmin");
            user.setEmail("superadmin@gmail.com");
            user.setPassword("admin123");
            user.setRole("SUPER_ADMIN");

            userRepository.save(user);

            System.out.println("Super Admin Created");
        }

    }

}