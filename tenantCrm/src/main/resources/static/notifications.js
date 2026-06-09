/**
 * Shared notification bell for all dashboards.
 * Each user only sees their own notifications from /api/notifications.
 */
(function () {
    'use strict';

    var POLL_MS = 10000;
    var pollTimer = null;
    var isOpen = false;
    var notifications = [];
    var unreadCount = 0;

    function isAppPage() {
        var path = window.location.pathname;
        return path !== '/login'
            && path !== '/forgot-password'
            && path.indexOf('/reset-password') !== 0;
    }

    function ensureUi() {
        var topbarRight = document.querySelector('.topbar-right');
        if (!topbarRight) return null;

        topbarRight.querySelectorAll('.notif-btn').forEach(function (el) {
            if (!el.closest('#crmNotifWrap')) {
                el.remove();
            }
        });

        if (document.getElementById('crmNotifWrap')) return topbarRight;

        var wrap = document.createElement('div');
        wrap.id = 'crmNotifWrap';
        wrap.className = 'notif-wrap';
        wrap.innerHTML =
            '<button type="button" class="notif-btn" id="crmNotifBtn" aria-label="Notifications" title="Notifications">' +
                '<i data-lucide="bell"></i>' +
                '<span class="notif-badge" id="crmNotifBadge" style="display:none;">0</span>' +
            '</button>' +
            '<div class="notif-dropdown" id="crmNotifDropdown" style="display:none;">' +
                '<div class="notif-dropdown-header">' +
                    '<span>Notifications</span>' +
                    '<div class="notif-header-actions">' +
                        '<button type="button" class="notif-mark-all" id="crmNotifMarkAll">Mark all read</button>' +
                        '<button type="button" class="notif-clear-all" id="crmNotifClearAll">Clear all</button>' +
                    '</div>' +
                '</div>' +
                '<div class="notif-list" id="crmNotifList"></div>' +
            '</div>';

        var userBadge = topbarRight.querySelector('.user-badge');
        if (userBadge) {
            topbarRight.insertBefore(wrap, userBadge);
        } else {
            topbarRight.appendChild(wrap);
        }

        if (window.lucide) lucide.createIcons();

        document.getElementById('crmNotifBtn').addEventListener('click', toggleDropdown);
        document.getElementById('crmNotifMarkAll').addEventListener('click', markAllRead);
        document.getElementById('crmNotifClearAll').addEventListener('click', deleteAll);
        document.addEventListener('click', function (e) {
            if (!wrap.contains(e.target)) closeDropdown();
        });

        return topbarRight;
    }

    function typeIcon(type) {
        switch ((type || '').toUpperCase()) {
            case 'TASK': return 'check-square';
            case 'LEAVE': return 'file-text';
            case 'MEETING': return 'calendar-clock';
            case 'TEAM': return 'users';
            case 'PERFORMANCE': return 'bar-chart-2';
            case 'REPORT': return 'file-bar-chart';
            case 'HOLIDAY': return 'calendar';
            default: return 'bell';
        }
    }

    function timeAgo(iso) {
        if (!iso) return '';
        var d = new Date(iso);
        if (isNaN(d.getTime())) return '';
        var sec = Math.floor((Date.now() - d.getTime()) / 1000);
        if (sec < 60) return 'Just now';
        if (sec < 3600) return Math.floor(sec / 60) + 'm ago';
        if (sec < 86400) return Math.floor(sec / 3600) + 'h ago';
        return Math.floor(sec / 86400) + 'd ago';
    }

    function updateBadge() {
        var badge = document.getElementById('crmNotifBadge');
        if (!badge) return;
        if (unreadCount > 0) {
            badge.style.display = 'flex';
            badge.textContent = unreadCount > 9 ? '9+' : String(unreadCount);
        } else {
            badge.style.display = 'none';
        }
    }

    function renderList() {
        var list = document.getElementById('crmNotifList');
        if (!list) return;

        if (!notifications.length) {
            list.innerHTML = '<div class="notif-empty">No notifications yet</div>';
            return;
        }

        list.innerHTML = notifications.map(function (n) {
            var cls = 'notif-item' + (n.read ? '' : ' unread');
            return '<div class="' + cls + '" data-id="' + n.id + '">' +
                '<button type="button" class="notif-item-main" data-id="' + n.id + '" data-link="' + escapeHtml(n.link || '') + '">' +
                    '<span class="notif-item-icon"><i data-lucide="' + typeIcon(n.type) + '"></i></span>' +
                    '<span class="notif-item-body">' +
                        '<strong>' + escapeHtml(n.title || '') + '</strong>' +
                        '<span>' + escapeHtml(n.message || '') + '</span>' +
                        '<em>' + timeAgo(n.createdAt) + '</em>' +
                    '</span>' +
                '</button>' +
                '<button type="button" class="notif-delete" data-id="' + n.id + '" title="Delete notification" aria-label="Delete notification">' +
                    '<i data-lucide="trash-2"></i>' +
                '</button>' +
            '</div>';
        }).join('');

        list.querySelectorAll('.notif-item-main').forEach(function (btn) {
            btn.addEventListener('click', function () {
                var id = btn.getAttribute('data-id');
                var link = btn.getAttribute('data-link');
                markRead(id, function () {
                    if (link) window.location.href = link;
                });
            });
        });

        list.querySelectorAll('.notif-delete').forEach(function (btn) {
            btn.addEventListener('click', function (e) {
                e.stopPropagation();
                deleteNotification(btn.getAttribute('data-id'));
            });
        });

        if (window.lucide) lucide.createIcons();
    }

    function escapeHtml(str) {
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;');
    }

    function fetchNotifications(cb) {
        if (typeof window.crmFetch !== 'function') return;
        window.crmFetch('/api/notifications', { method: 'GET', contentType: null })
            .then(function (r) {
                if (!r.ok) throw new Error('HTTP ' + r.status);
                return r.json();
            })
            .then(function (data) {
                notifications = data.notifications || [];
                unreadCount = data.unreadCount || 0;
                updateBadge();
                renderList();
                renderDashboardFeed();
                if (typeof cb === 'function') cb();
            })
            .catch(function (err) {
                console.warn('Notifications fetch failed:', err.message);
            });
    }

    function markRead(id, cb) {
        window.crmFetch('/api/notifications/' + id + '/read', { method: 'POST', body: '{}' })
            .then(function (r) { return r.ok ? r.json() : null; })
            .then(function (data) {
                if (data) unreadCount = data.unreadCount || 0;
                notifications = notifications.map(function (n) {
                    if (String(n.id) === String(id)) n.read = true;
                    return n;
                });
                updateBadge();
                renderList();
                renderDashboardFeed();
                if (typeof cb === 'function') cb();
            });
    }

    function markAllRead(e) {
        e.stopPropagation();
        window.crmFetch('/api/notifications/read-all', { method: 'POST', body: '{}' })
            .then(function () {
                unreadCount = 0;
                notifications = notifications.map(function (n) { n.read = true; return n; });
                updateBadge();
                renderList();
                renderDashboardFeed();
            });
    }

    function deleteNotification(id) {
        window.crmFetch('/api/notifications/' + id, { method: 'DELETE', contentType: null })
            .then(function (r) { return r.ok ? r.json() : null; })
            .then(function (data) {
                if (!data) return;
                notifications = notifications.filter(function (n) {
                    return String(n.id) !== String(id);
                });
                unreadCount = data.unreadCount || 0;
                updateBadge();
                renderList();
                renderDashboardFeed();
            });
    }

    function deleteAll(e) {
        e.stopPropagation();
        if (!notifications.length) return;
        if (!window.confirm('Delete all notifications?')) return;
        window.crmFetch('/api/notifications/clear-all', { method: 'DELETE', contentType: null })
            .then(function (r) { return r.ok ? r.json() : null; })
            .then(function () {
                notifications = [];
                unreadCount = 0;
                updateBadge();
                renderList();
                renderDashboardFeed();
            });
    }

    function toggleDropdown(e) {
        e.stopPropagation();
        var dd = document.getElementById('crmNotifDropdown');
        if (!dd) return;
        isOpen = !isOpen;
        dd.style.display = isOpen ? 'block' : 'none';
        if (isOpen) fetchNotifications();
    }

    function closeDropdown() {
        var dd = document.getElementById('crmNotifDropdown');
        if (dd) dd.style.display = 'none';
        isOpen = false;
    }

    function renderDashboardFeed() {
        var feed = document.getElementById('dashboardNotifFeed');
        if (!feed) return;
        if (!notifications.length) {
            feed.innerHTML = '<div class="notif-feed-empty"><i data-lucide="bell-off"></i><p>No notifications yet</p></div>';
        } else {
            feed.innerHTML = notifications.slice(0, 8).map(function (n) {
                return '<div class="notif-feed-item' + (n.read ? '' : ' unread') + '">' +
                    '<a class="notif-feed-link" href="' + escapeHtml(n.link || '#') + '">' +
                        '<span class="notif-feed-icon"><i data-lucide="' + typeIcon(n.type) + '"></i></span>' +
                        '<span><strong>' + escapeHtml(n.title || '') + '</strong>' +
                        '<span>' + escapeHtml(n.message || '') + '</span></span>' +
                        '<em>' + timeAgo(n.createdAt) + '</em>' +
                    '</a>' +
                    '<button type="button" class="notif-feed-delete" data-id="' + n.id + '" title="Delete" aria-label="Delete notification">' +
                        '<i data-lucide="trash-2"></i>' +
                    '</button>' +
                '</div>';
            }).join('');
            feed.querySelectorAll('.notif-feed-delete').forEach(function (btn) {
                btn.addEventListener('click', function (e) {
                    e.preventDefault();
                    e.stopPropagation();
                    deleteNotification(btn.getAttribute('data-id'));
                });
            });
        }
        if (window.lucide) lucide.createIcons();
    }

    function startPolling() {
        fetchNotifications();
        if (pollTimer) clearInterval(pollTimer);
        pollTimer = setInterval(fetchNotifications, POLL_MS);
    }

    function init(attempt) {
        if (!isAppPage()) return;

        if (typeof window.crmFetch !== 'function' || !localStorage.getItem('jwt_token')) {
            if (attempt < 30) {
                setTimeout(function () { init(attempt + 1); }, 100);
            }
            return;
        }

        ensureUi();
        startPolling();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', function () { init(0); });
    } else {
        init(0);
    }
})();
