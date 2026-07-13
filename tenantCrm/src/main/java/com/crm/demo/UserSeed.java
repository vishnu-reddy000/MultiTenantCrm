package com.crm.demo;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.crm.demo.model.User;
import com.crm.demo.repository.UserRepository;

@Component
public class UserSeed implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(UserSeed.class);

    @org.springframework.beans.factory.annotation.Value("${app.security.superadmin.default-password}")
    private String defaultPassword;

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public UserSeed(UserRepository userRepository, BCryptPasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {

        var user = userRepository.findByUsername("superadmin");

        // IF USER NOT EXISTS, create a new one
        if (user == null) {
            user = new User();
        }

        // Only re-hash if the stored password is NOT already a BCrypt hash.
        // This prevents double-hashing on every restart.
        if (user.getPassword() == null || !user.getPassword().startsWith("$2")) {
            user.setPassword(passwordEncoder.encode(defaultPassword));
        }

        user.setUsername("superadmin");
        user.setEmail("superadmin@crm.com");
        user.setRole("SUPER_ADMIN");

        userRepository.save(user);

        log.info("Super Admin Created/Updated (password BCrypt-hashed)");
    }
}
