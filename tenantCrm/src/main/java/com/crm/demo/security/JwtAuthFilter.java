package com.crm.demo.security;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Stateless JWT authentication filter — runs once per request.
 *
 * Token resolution order:
 *  1. Authorization: Bearer <token>  header  (used by AJAX / fetch calls)
 *  2. jwt_token cookie               (used by browser page navigations / Thymeleaf)
 *
 * No HttpSession is ever created or consulted. The server is fully stateless.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String COOKIE_NAME = "jwt_token";

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private SessionManager sessionManager;

    @Override
    protected void doFilterInternal(HttpServletRequest  request,
                                    HttpServletResponse response,
                                    FilterChain         filterChain)
            throws ServletException, IOException {

        String token = resolveToken(request);

        if (token != null && jwtUtil.isValid(token)) {
            String username = jwtUtil.extractUsername(token);
            String role     = jwtUtil.extractRole(token);

            if (sessionManager.isValidSession(username, token)) {
                // Update activity time
                sessionManager.updateActivity(username, token);

                // Expose as request attributes so Thymeleaf controllers can read them
                request.setAttribute("loggedInUser", username);
                request.setAttribute("loggedInRole", role);
                request.setAttribute("JWT_TOKEN",    token);

                // Build Spring Security authentication — no session, no UserDetailsService
                var authority = new SimpleGrantedAuthority("ROLE_" + role.toUpperCase());
                var auth = new UsernamePasswordAuthenticationToken(
                        username, null, List.of(authority));
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(auth);
            } else {
                // Clear the superseded cookie immediately
                jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("jwt_token", "");
                cookie.setPath("/");
                cookie.setMaxAge(0);
                response.addCookie(cookie);

                // Flag that the session was superseded
                request.setAttribute("session_superseded", true);
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Resolve the JWT from the request.
     * Checks Authorization header first, then falls back to the jwt_token cookie.
     */
    private String resolveToken(HttpServletRequest request) {
        // 1. Authorization: Bearer <token>
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        // 2. Cookie: jwt_token=<token>
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (COOKIE_NAME.equals(cookie.getName())) {
                    String value = cookie.getValue();
                    if (value != null && !value.isBlank()) {
                        return value;
                    }
                }
            }
        }

        return null;
    }
}
