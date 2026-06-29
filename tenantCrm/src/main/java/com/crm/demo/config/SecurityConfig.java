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
import org.springframework.http.HttpMethod;

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
                // ── Public assets & auth pages ──────────────────────────────
                .requestMatchers(
                    "/login",
                    "/forgot-password",
                    "/reset-password",
                    "/api/auth/**",
                    "/error",
                    "/error/**",
                    "/*.css",
                    "/*.js",
                    "/*.png",
                    "/*.jpg",
                    "/*.ico",
                    "/*.woff",
                    "/*.woff2",
                    "/*.svg",
                    "/css/**",
                    "/js/**",
                    "/images/**",
                    "/static/**",
                    "/ws",
                    "/ws/**"
                ).permitAll()
                // GET  /api/holidays   — any authenticated user (all roles see their tenant's holidays)
                // POST /api/holidays   — ADMIN or HR only
                // PUT  /api/holidays/* — ADMIN or HR only
                // DELETE /api/holidays/* — ADMIN or HR only
                .requestMatchers(HttpMethod.GET, "/api/holidays").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/holidays/**").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/holidays").hasAnyRole("ADMIN", "HR")
                .requestMatchers(HttpMethod.PUT, "/api/holidays/**").hasAnyRole("ADMIN", "HR")
                .requestMatchers(HttpMethod.DELETE, "/api/holidays/**").hasAnyRole("ADMIN", "HR")
                .requestMatchers(HttpMethod.GET, "/api/notifications").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/notifications/**").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/notifications/**").authenticated()
                .requestMatchers(HttpMethod.DELETE, "/api/notifications/**").authenticated()
                // ── Role-scoped pages ────────────────────────────────────────
                .requestMatchers("/superadmin/**").hasRole("SUPER_ADMIN")
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/manager/**").hasRole("MANAGER")
                .requestMatchers("/hr/**").hasRole("HR")
                .requestMatchers("/employee/**").hasRole("EMPLOYEE")
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    String accept = request.getHeader("Accept");
                    if (accept != null && accept.contains("application/json")) {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType("application/json");
                        if (Boolean.TRUE.equals(request.getAttribute("session_superseded"))) {
                            response.getWriter().write("{\"error\":\"superseded\"}");
                        } else {
                            response.getWriter().write("{\"error\":\"Unauthorized\"}");
                        }
                    } else {
                        if (Boolean.TRUE.equals(request.getAttribute("session_superseded"))) {
                            response.sendRedirect("/login?error=superseded");
                        } else {
                            response.sendRedirect("/login");
                        }
                    }
                })
            )
            .formLogin(fl -> fl.disable())
            .httpBasic(hb -> hb.disable())
            .logout(logout -> logout.disable())
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
