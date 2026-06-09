package com.crm.demo.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import com.crm.demo.security.JwtAuthFilter;

import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .requestCache(rc -> rc.disable())
            .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.sameOrigin()))
            .authorizeHttpRequests(auth -> auth
                .shouldFilterAllDispatcherTypes(false)
                // ── Public assets & auth pages ──────────────────────────────
                .requestMatchers(
                    new AntPathRequestMatcher("/login"),
                    new AntPathRequestMatcher("/forgot-password"),
                    new AntPathRequestMatcher("/reset-password"),
                    new AntPathRequestMatcher("/api/auth/**"),
                    new AntPathRequestMatcher("/error"),
                    new AntPathRequestMatcher("/error/**"),
                    new AntPathRequestMatcher("/**/*.css"),
                    new AntPathRequestMatcher("/**/*.js"),
                    new AntPathRequestMatcher("/**/*.png"),
                    new AntPathRequestMatcher("/**/*.jpg"),
                    new AntPathRequestMatcher("/**/*.ico"),
                    new AntPathRequestMatcher("/**/*.woff"),
                    new AntPathRequestMatcher("/**/*.woff2")
                ).permitAll()
                // GET  /api/holidays   — any authenticated user (all roles see their tenant's holidays)
                // POST /api/holidays   — ADMIN or HR only
                // PUT  /api/holidays/* — ADMIN or HR only
                // DELETE /api/holidays/* — ADMIN or HR only
                .requestMatchers(new AntPathRequestMatcher("/api/holidays", "GET")).authenticated()
                .requestMatchers(new AntPathRequestMatcher("/api/holidays/**", "GET")).authenticated()
                .requestMatchers(new AntPathRequestMatcher("/api/holidays", "POST")).hasAnyRole("ADMIN", "HR")
                .requestMatchers(new AntPathRequestMatcher("/api/holidays/**", "PUT")).hasAnyRole("ADMIN", "HR")
                .requestMatchers(new AntPathRequestMatcher("/api/holidays/**", "DELETE")).hasAnyRole("ADMIN", "HR")
                .requestMatchers(new AntPathRequestMatcher("/api/notifications", "GET")).authenticated()
                .requestMatchers(new AntPathRequestMatcher("/api/notifications/**", "GET")).authenticated()
                .requestMatchers(new AntPathRequestMatcher("/api/notifications/**", "POST")).authenticated()
                .requestMatchers(new AntPathRequestMatcher("/api/notifications/**", "DELETE")).authenticated()
                // ── Role-scoped pages ────────────────────────────────────────
                .requestMatchers(new AntPathRequestMatcher("/superadmin/**")).hasRole("SUPER_ADMIN")
                .requestMatchers(new AntPathRequestMatcher("/admin/**")).hasRole("ADMIN")
                .requestMatchers(new AntPathRequestMatcher("/manager/**")).hasRole("MANAGER")
                .requestMatchers(new AntPathRequestMatcher("/hr/**")).hasRole("HR")
                .requestMatchers(new AntPathRequestMatcher("/employee/**")).hasRole("EMPLOYEE")
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    String accept = request.getHeader("Accept");
                    if (accept != null && accept.contains("application/json")) {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType("application/json");
                        response.getWriter().write("{\"error\":\"Unauthorized\"}");
                    } else {
                        response.sendRedirect("/login");
                    }
                })
            )
            .formLogin(fl -> fl.disable())
            .httpBasic(hb -> hb.disable())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public HttpFirewall httpFirewall() {
        StrictHttpFirewall firewall = new StrictHttpFirewall();
        firewall.setAllowSemicolon(false);
        return firewall;
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
