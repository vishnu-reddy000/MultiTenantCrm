package com.crm.demo.security;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SessionManager {

    private static class SessionDetails {
        private final String token;
        private long lastActivity;

        public SessionDetails(String token, long lastActivity) {
            this.token = token;
            this.lastActivity = lastActivity;
        }

        public String getToken() {
            return token;
        }

        public long getLastActivity() {
            return lastActivity;
        }

        public void setLastActivity(long lastActivity) {
            this.lastActivity = lastActivity;
        }
    }

    // Map: username -> SessionDetails
    private final Map<String, SessionDetails> activeSessions = new ConcurrentHashMap<>();
    
    // Inactivity timeout: default 15 minutes (900000 ms)
    private final long timeoutMs;

    public SessionManager(@Value("${app.session.timeout-ms:900000}") long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    /**
     * Register a new session for a user.
     */
    public void registerSession(String username, String token) {
        activeSessions.put(username, new SessionDetails(token, System.currentTimeMillis()));
    }

    /**
     * Checks if a user is currently logged in (active session exists and has not timed out).
     */
    public boolean isUserLoggedIn(String username) {
        SessionDetails session = activeSessions.get(username);
        if (session == null) {
            return false;
        }
        boolean isExpired = (System.currentTimeMillis() - session.getLastActivity()) >= timeoutMs;
        if (isExpired) {
            activeSessions.remove(username); // Clean up expired session
            return false;
        }
        return true;
    }

    /**
     * Checks if the presented token matches the active session token and is not expired by inactivity.
     * Also supports auto-registration (graceful recovery after server restarts): if no session exists in the map
     * but the token is valid, we register it as the active session.
     */
    public boolean isValidSession(String username, String token) {
        SessionDetails session = activeSessions.get(username);
        if (session == null) {
            // Graceful recovery: if server restarted, we register the first cryptographically valid token presented
            registerSession(username, token);
            return true;
        }
        
        // If the token matches, check if it's expired due to inactivity
        if (session.getToken().equals(token)) {
            boolean isExpired = (System.currentTimeMillis() - session.getLastActivity()) >= timeoutMs;
            if (isExpired) {
                activeSessions.remove(username);
                return false;
            }
            return true;
        }
        
        // Token mismatch: another session has superseded this one
        return false;
    }

    /**
     * Update the last activity timestamp for the active session.
     */
    public void updateActivity(String username, String token) {
        SessionDetails session = activeSessions.get(username);
        if (session != null && session.getToken().equals(token)) {
            session.setLastActivity(System.currentTimeMillis());
        }
    }

    /**
     * Invalidate the session.
     */
    public void invalidateSession(String username) {
        if (username != null) {
            activeSessions.remove(username);
        }
    }
}
