package com.crm.demo.config;

import com.crm.demo.security.JwtAuthFilter;
import jakarta.servlet.http.HttpServletResponse;
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

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // ── Disable CSRF — safe for stateless JWT APIs ──────────────────
            .csrf(csrf -> csrf.disable())

            // ── STATELESS: never create or use an HTTP session ───────────────
            .sessionManagement(sm ->
                sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // ── Disable request cache (prevents session creation for saving
            //    the pre-authentication request) ─────────────────────────────
            .requestCache(rc -> rc.disable())

            // ── Route permissions ────────────────────────────────────────────
            .authorizeHttpRequests(auth -> auth
                // Public: login page, auth API, static assets
                // NOTE: Spring Security 6 does not allow patterns like /**/*.css
                // Use AntPathRequestMatcher for wildcard extension matching.
                .requestMatchers(
                    new AntPathRequestMatcher("/login"),
                    new AntPathRequestMatcher("/api/auth/**"),
                    new AntPathRequestMatcher("/error"),
                    new AntPathRequestMatcher("/**/*.css"),
                    new AntPathRequestMatcher("/**/*.js"),
                    new AntPathRequestMatcher("/**/*.png"),
                    new AntPathRequestMatcher("/**/*.jpg"),
                    new AntPathRequestMatcher("/**/*.ico"),
                    new AntPathRequestMatcher("/**/*.woff"),
                    new AntPathRequestMatcher("/**/*.woff2")
                ).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/superadmin/**")).hasRole("SUPER_ADMIN")
                .requestMatchers(new AntPathRequestMatcher("/admin/**")).hasRole("ADMIN")
                .requestMatchers(new AntPathRequestMatcher("/manager/**")).hasRole("MANAGER")
                .requestMatchers(new AntPathRequestMatcher("/hr/**")).hasRole("HR")
                .requestMatchers(new AntPathRequestMatcher("/employee/**")).hasRole("EMPLOYEE")
                // Everything else requires authentication
                .anyRequest().authenticated()
            )

            // ── Return 401 JSON for API calls, redirect to /login for browsers
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

            // ── Disable Spring Security's default form login & basic auth ────
            .formLogin(fl -> fl.disable())
            .httpBasic(hb -> hb.disable())

            // ── Plug in our JWT filter before the username/password filter ───
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Strict firewall that REJECTS any URL containing a semicolon.
     * Blocks requests like /admin/dashboard;jsessionid=XXXX with a 400.
     */
    @Bean
    public HttpFirewall httpFirewall() {
        StrictHttpFirewall firewall = new StrictHttpFirewall();
        firewall.setAllowSemicolon(false);
        return firewall;
    }

    /** BCryptPasswordEncoder bean — used by LoginController and all user-creation flows. */
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
