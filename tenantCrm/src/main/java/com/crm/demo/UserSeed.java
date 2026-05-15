package com.crm.demo;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import com.crm.demo.model.User;
import com.crm.demo.repository.UserRepository;

@Component
public class UserSeed implements CommandLineRunner {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public UserSeed(UserRepository userRepository, BCryptPasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {

        User user = userRepository.findByUsername("superadmin");

        // IF USER NOT EXISTS, create a new one
        if (user == null) {
            user = new User();
        }

        // Only re-hash if the stored password is NOT already a BCrypt hash.
        // This prevents double-hashing on every restart.
        String rawPassword = "superadmin123";
        if (user.getPassword() == null || !user.getPassword().startsWith("$2")) {
            user.setPassword(passwordEncoder.encode(rawPassword));
        }

        user.setUsername("superadmin");
        user.setEmail("superadmin@crm.com");
        user.setRole("SUPER_ADMIN");

        userRepository.save(user);

        System.out.println("Super Admin Created/Updated (password BCrypt-hashed)");
    }
}
