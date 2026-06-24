/**
 * auth.js — loaded by every protected dashboard page.
 *
 * HOW JWT WORKS IN THIS APP (Thymeleaf + JWT hybrid):
 * ─────────────────────────────────────────────────────
 * 1. Login: POST /api/auth/login → server returns { token, role, username, redirect }
 * 2. Token is stored in localStorage (primary) AND a short-lived cookie (for page navigation).
 * 3. Page navigations (browser GET requests) send the cookie automatically.
 *    JwtAuthFilter reads the token from the cookie on page loads.
 * 4. AJAX/fetch calls use the Authorization: Bearer <token> header via crmFetch().
 * 5. Logout: clears localStorage + cookie, redirects to /login.
 *
 * WHY A COOKIE FOR PAGE NAVIGATION?
 * ─────────────────────────────────────────────────────
 * Browsers cannot set custom headers on normal page navigations (clicking links,
 * typing URLs). So we use a non-HttpOnly cookie as a transport for the JWT on
 * initial page loads only. The server reads it in JwtAuthFilter.
 * This is NOT a session cookie — it carries the JWT itself, not a session ID.
 * The server remains completely stateless (no HttpSession is created).
 */

(function () {
    'use strict';

    const TOKEN_KEY    = 'jwt_token';
    const ROLE_KEY     = 'jwt_role';
    const USERNAME_KEY = 'jwt_username';
    const COOKIE_NAME  = 'jwt_token';

    // ── Guard: redirect to login if no valid token ────────────────────────────
    function guardPage() {
        const token = localStorage.getItem(TOKEN_KEY);
        if (!token) {
            redirectToLogin();
            return false;
        }
        try {
            const payload = JSON.parse(atob(token.split('.')[1]));
            if (payload.exp * 1000 <= Date.now()) {
                clearAuth();
                redirectToLogin();
                return false;
            }
        } catch (e) {
            clearAuth();
            redirectToLogin();
            return false;
        }
        // Ensure cookie is in sync (e.g. after a page refresh)
        syncCookie(token);
        return true;
    }

    // ── Authenticated fetch wrapper ───────────────────────────────────────────
    // Use this for all AJAX calls to protected API endpoints.
    window.crmFetch = function (url, options = {}) {
        const token = localStorage.getItem(TOKEN_KEY);
        const headers = Object.assign({}, options.headers || {}, {
            'Authorization': token ? 'Bearer ' + token : ''
        });
        if (options.contentType !== null) {
            headers['Content-Type'] = options.contentType || 'application/json';
        }
        const fetchOptions = Object.assign({}, options, { headers });
        delete fetchOptions.contentType;
        return fetch(url, fetchOptions).then(async response => {
            if (response.status === 401) {
                try {
                    const data = await response.clone().json();
                    if (data && data.error === 'superseded') {
                        clearAuth();
                        window.location.href = '/login?error=superseded';
                        return response;
                    }
                } catch (e) {
                    // Ignore JSON parsing errors
                }
                clearAuth();
                window.location.href = '/login';
            }
            return response;
        });
    };

    // ── Logout ────────────────────────────────────────────────────────────────
    window.crmLogout = async function () {
        const token = localStorage.getItem(TOKEN_KEY);
        if (token) {
            const controller = new AbortController();
            const timeoutId = setTimeout(() => controller.abort(), 1000);
            try {
                await fetch('/api/auth/logout', {
                    method: 'POST',
                    headers: {
                        'Authorization': 'Bearer ' + token
                    },
                    signal: controller.signal
                });
            } catch (e) {
                console.error("Logout API request failed or timed out", e);
            } finally {
                clearTimeout(timeoutId);
            }
        }
        clearAuth();
        window.location.href = '/login';
    };

    // ── Expose current user info ──────────────────────────────────────────────
    window.crmUser = {
        token:    () => localStorage.getItem(TOKEN_KEY),
        role:     () => localStorage.getItem(ROLE_KEY),
        username: () => localStorage.getItem(USERNAME_KEY)
    };

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Write the JWT into a browser cookie so it is sent automatically on
     * every page navigation (GET request). The cookie is:
     *  - NOT HttpOnly  → JavaScript can read/delete it for logout
     *  - SameSite=Strict → not sent on cross-site requests (CSRF protection)
     *  - Path=/         → sent to all paths
     *  - Expires set to match the JWT expiry decoded from the token payload
     */
    function syncCookie(token) {
        try {
            const payload = JSON.parse(atob(token.split('.')[1]));
            const expires = new Date(payload.exp * 1000).toUTCString();
            document.cookie = COOKIE_NAME + '=' + token
                + '; expires=' + expires
                + '; path=/'
                + '; SameSite=Strict';
        } catch (e) {
            // Malformed token — set a session cookie as fallback
            document.cookie = COOKIE_NAME + '=' + token + '; path=/; SameSite=Strict';
        }
    }

    function clearAuth() {
        localStorage.removeItem(TOKEN_KEY);
        localStorage.removeItem(ROLE_KEY);
        localStorage.removeItem(USERNAME_KEY);
        // Expire the cookie immediately
        document.cookie = COOKIE_NAME + '=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/; SameSite=Strict';
    }

    function redirectToLogin() {
        if (window.location.pathname !== '/login') {
            window.location.href = '/login';
        }
    }

    // ── Run guard on every protected page load ────────────────────────────────
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', guardPage);
    } else {
        guardPage();
    }

    // ── Wire up data-logout and convert sidebar links ─────────────────────────
    document.addEventListener('DOMContentLoaded', function () {
        // Wire up logout buttons
        document.querySelectorAll('[data-logout]').forEach(function (el) {
            el.addEventListener('click', function (e) {
                e.preventDefault();
                window.crmLogout();
            });
        });

        // Convert sidebar links to click handlers to hide URLs and prevent opening in new tabs
        document.querySelectorAll('.sidebar a, .sidebar-menu a').forEach(function (el) {
            const href = el.getAttribute('href');
            if (href && href !== '#' && !href.startsWith('javascript:')) {
                el.removeAttribute('href');
                el.style.cursor = 'pointer';
                el.addEventListener('click', function (e) {
                    e.preventDefault();
                    window.location.href = href;
                });
            }
        });
    });

})();
