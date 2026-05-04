package com.springboot1.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import com.springboot1.security.LoginSuccessHandler;

@Configuration
@EnableMethodSecurity // ← enables @PreAuthorize on TenantController
public class SecurityConfig {

	private final LoginSuccessHandler loginSuccessHandler;

	public SecurityConfig(LoginSuccessHandler loginSuccessHandler) {
		this.loginSuccessHandler = loginSuccessHandler;
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.authorizeHttpRequests(auth -> auth

				// ── Static assets — always public ───────────────────────────
				.requestMatchers("/login", "/css/**", "/js/**", "/images/**", "/fonts/**", "/webjars/**", "/login.css",
						"/dashboard-admin.css", "/dashboard-admin.js", "/*.css", "/*.js", "/*.ico", "/*.png", "/*.svg")
				.permitAll()

				// ── Tenant REST API — SUPER_ADMIN only ──────────────────────
				// (also enforced at method level via @PreAuthorize)
				.requestMatchers("/super-admin/tenants/**").hasRole("SUPER_ADMIN")

				// ── Role-based page access ───────────────────────────────────
				.requestMatchers("/dashboard/super-admin").hasRole("SUPER_ADMIN").requestMatchers("/dashboard/admin")
				.hasAnyRole("SUPER_ADMIN", "ADMIN").requestMatchers("/admin/**").hasAnyRole("SUPER_ADMIN", "ADMIN")
				.requestMatchers("/dashboard/sales-executive").hasAnyRole("SUPER_ADMIN", "ADMIN", "SALES_EXECUTIVE")
				.requestMatchers("/dashboard/user").hasAnyRole("SUPER_ADMIN", "ADMIN", "USER", "SALES_EXECUTIVE")

				// ── Everything else requires login ───────────────────────────
				.anyRequest().authenticated())

				.formLogin(form -> form.loginPage("/login").usernameParameter("usernameOrEmail")
						.passwordParameter("password").successHandler(loginSuccessHandler).failureUrl("/login?error")
						.permitAll())

				.logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/login?logout")
						.invalidateHttpSession(true).deleteCookies("JSESSIONID").permitAll());

		return http.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	DaoAuthenticationProvider authenticationProvider(UserDetailsService userDetailsService, PasswordEncoder encoder) {
		DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);
		authProvider.setPasswordEncoder(encoder);
		return authProvider;
	}
}