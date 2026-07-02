/**
 * Shared notification bell for all dashboards.
 * Uses WebSockets (SockJS + STOMP) with HTTP polling fallback.
 */
(function () {
    'use strict';

    var POLL_MS = 1000;
    var pollTimer = null;
    var isOpen = false;
    var notifications = [];
    var unreadCount = 0;

    var stompClient = null;
    var wsConnected = false;
    var wsRetryCount = 0;
    var maxWsRetries = 5;
    var reconnectTimeout = null;
    var isInitialized = false;

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
                var newNotifs = data.notifications || [];
                var hasNew = false;
                var newTypes = {};

                if (isInitialized) {
                    newNotifs.forEach(function (n) {
                        if (!notifications.some(function (old) { return String(old.id) === String(n.id); })) {
                            hasNew = true;
                            newTypes[n.type] = true;
                        }
                    });
                }

                notifications = newNotifs;
                unreadCount = data.unreadCount || 0;
                updateBadge();
                renderList();
                renderDashboardFeed();

                if (hasNew) {
                    Object.keys(newTypes).forEach(function (type) {
                        refreshPageContentIfRelevant(type);
                    });
                }

                if (typeof cb === 'function') cb();
            })
            .catch(function (err) {
                console.warn('Notifications fetch failed:', err.message);
                if (typeof cb === 'function') cb();
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
                showFeedbackToast('Notification deleted successfully', 'success');
            });
    }

    function showConfirmPopup(message, onConfirm) {
        // Remove any existing confirm popup
        var existing = document.getElementById('crmConfirmPopup');
        if (existing) existing.remove();

        var overlay = document.createElement('div');
        overlay.id = 'crmConfirmPopup';
        overlay.className = 'crm-confirm-overlay';
        overlay.innerHTML =
            '<div class="crm-confirm-box">' +
                '<div class="crm-confirm-icon">' +
                    '<i data-lucide="trash-2"></i>' +
                '</div>' +
                '<div class="crm-confirm-title">Clear All Notifications</div>' +
                '<div class="crm-confirm-message">' + escapeHtml(message) + '</div>' +
                '<div class="crm-confirm-actions">' +
                    '<button type="button" class="crm-confirm-cancel">Cancel</button>' +
                    '<button type="button" class="crm-confirm-ok">Delete All</button>' +
                '</div>' +
            '</div>';

        document.body.appendChild(overlay);
        if (window.lucide) lucide.createIcons();

        // Animate in
        setTimeout(function () { overlay.classList.add('show'); }, 10);

        function close() {
            overlay.classList.remove('show');
            setTimeout(function () {
                if (overlay.parentNode) overlay.parentNode.removeChild(overlay);
            }, 250);
        }

        overlay.querySelector('.crm-confirm-cancel').addEventListener('click', function (e) {
            e.stopPropagation();
            close();
        });

        overlay.querySelector('.crm-confirm-ok').addEventListener('click', function (e) {
            e.stopPropagation();
            close();
            onConfirm();
        });

        // Click backdrop to cancel
        overlay.addEventListener('click', function (e) {
            if (e.target === overlay) close();
        });
    }

    function deleteAll(e) {
        e.stopPropagation();
        if (!notifications.length) return;
        showConfirmPopup('Are you sure you want to delete all notifications? This cannot be undone.', function () {
            window.crmFetch('/api/notifications/clear-all', { method: 'DELETE', contentType: null })
                .then(function (r) { return r.ok ? r.json() : null; })
                .then(function () {
                    notifications = [];
                    unreadCount = 0;
                    updateBadge();
                    renderList();
                    renderDashboardFeed();
                    showFeedbackToast('All notifications cleared', 'success');
                });
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
        if (pollTimer) clearInterval(pollTimer);
        pollTimer = setInterval(fetchNotifications, POLL_MS);
    }

    /* WebSocket integration with Fallback */

    function loadScript(src, callback) {
        var s = document.createElement('script');
        s.src = src;
        s.onload = callback;
        s.onerror = function () {
            console.warn('Failed to load script:', src);
            if (typeof callback === 'function') callback(new Error('Load failed'));
        };
        document.head.appendChild(s);
    }

    function loadLibrariesAndConnect(userId) {
        var sockJsUrl = '/sockjs.min.js';
        var stompUrl = '/stomp.min.js';

        var sockLoaded = false;
        var stompLoaded = false;

        function checkAndConnect() {
            if (sockLoaded && stompLoaded) {
                if (window.SockJS && window.Stomp) {
                    connectWebSocket(userId);
                } else {
                    console.warn('WebSocket libraries loaded incorrectly. Falling back to HTTP polling.');
                    startPolling();
                }
            }
        }

        if (window.SockJS) sockLoaded = true;
        if (window.Stomp) stompLoaded = true;

        if (sockLoaded && stompLoaded) {
            checkAndConnect();
            return;
        }

        var loadFailed = false;
        function handleLoadError() {
            if (loadFailed) return;
            loadFailed = true;
            console.warn('Failed to load WebSocket libraries. Falling back to HTTP polling.');
            startPolling();
        }

        if (!sockLoaded) {
            loadScript(sockJsUrl, function (err) {
                if (err) {
                    handleLoadError();
                    return;
                }
                sockLoaded = true;
                checkAndConnect();
            });
        }
        if (!stompLoaded) {
            loadScript(stompUrl, function (err) {
                if (err) {
                    handleLoadError();
                    return;
                }
                stompLoaded = true;
                checkAndConnect();
            });
        }
    }

    function connectWebSocket(userId) {
        if (wsConnected) return;

        try {
            var socket = new SockJS('/ws', null, {
                transports: ['websocket', 'xhr-streaming', 'xhr-polling']
            });
            stompClient = Stomp.over(socket);
            stompClient.debug = null; // Suppress debug logging in console

            stompClient.connect({}, function () {
                wsConnected = true;
                wsRetryCount = 0;
                
                // If we were polling, clear the interval
                if (pollTimer) {
                    clearInterval(pollTimer);
                    pollTimer = null;
                }

                stompClient.subscribe('/topic/notifications/' + userId, function (msg) {
                    try {
                        var n = JSON.parse(msg.body);
                        
                        // Prevent duplicates for database-backed notifications
                        if (n.id !== -1 && notifications.some(function(item) { return item.id === n.id; })) {
                            return;
                        }

                        notifications.unshift(n);
                        if (notifications.length > 50) {
                            notifications = notifications.slice(0, 50);
                        }
                        if (!n.read) {
                            unreadCount++;
                        }

                        updateBadge();
                        renderList();
                        renderDashboardFeed();
                        playNotificationSound();
                        showToast(n);
                        refreshPageContentIfRelevant(n.type);
                    } catch (e) {
                        console.error('Error handling WebSocket message:', e);
                    }
                });
            }, function (err) {
                console.warn('WebSocket STOMP error, attempting reconnect:', err);
                handleDisconnect(userId);
            });
        } catch (e) {
            console.warn('Failed to build SockJS/STOMP client:', e);
            handleDisconnect(userId);
        }
    }

    function handleDisconnect(userId) {
        wsConnected = false;
        if (stompClient) {
            try { stompClient.disconnect(); } catch (e) {}
            stompClient = null;
        }

        if (wsRetryCount < maxWsRetries) {
            var delay = Math.min(5000, Math.pow(2, wsRetryCount) * 1000);
            wsRetryCount++;
            console.log('Reconnecting WebSocket in ' + (delay / 1000) + 's (attempt ' + wsRetryCount + '/' + maxWsRetries + ')...');
            
            if (reconnectTimeout) clearTimeout(reconnectTimeout);
            reconnectTimeout = setTimeout(function () {
                connectWebSocket(userId);
            }, delay);
        } else {
            console.warn('WebSocket connection retries exhausted. Falling back to HTTP polling.');
            startPolling();
        }
    }

    /* Browser dynamic synthesized sound chime using Web Audio API */
    function playNotificationSound() {
        try {
            var AudioContext = window.AudioContext || window.webkitAudioContext;
            if (!AudioContext) return;
            var ctx = new AudioContext();

            var osc1 = ctx.createOscillator();
            var osc2 = ctx.createOscillator();
            var gain = ctx.createGain();

            osc1.type = 'sine';
            osc1.frequency.setValueAtTime(523.25, ctx.currentTime); // C5
            osc1.frequency.exponentialRampToValueAtTime(880.00, ctx.currentTime + 0.08); // A5

            osc2.type = 'sine';
            osc2.frequency.setValueAtTime(659.25, ctx.currentTime); // E5
            osc2.frequency.exponentialRampToValueAtTime(1046.50, ctx.currentTime + 0.12); // C6

            gain.gain.setValueAtTime(0.1, ctx.currentTime);
            gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.4);

            osc1.connect(gain);
            osc2.connect(gain);
            gain.connect(ctx.destination);

            osc1.start();
            osc2.start();

            osc1.stop(ctx.currentTime + 0.4);
            osc2.stop(ctx.currentTime + 0.4);
        } catch (e) {
            console.warn('Could not play synthesized notification sound:', e);
        }
    }

    function shouldReloadPage(type) {
        var path = window.location.pathname.toLowerCase();
        
        // List of path segments that contain automatic data lists/tables/dashboards
        var activePaths = [
            '/dashboard',
            '/tasks',
            '/meetings',
            '/schedule-meeting',
            '/calendar',
            '/leaves',
            '/leave',
            '/team',
            '/teams',
            '/reports',
            '/performance',
            '/attendance',
            '/employees'
        ];

        for (var i = 0; i < activePaths.length; i++) {
            if (path.indexOf(activePaths[i]) !== -1) {
                return true;
            }
        }

        return false;
    }

    function refreshPageContentIfRelevant(type) {
        if (!shouldReloadPage(type)) return;

        var pageContent = document.querySelector('.page-content');
        if (!pageContent) {
            window.location.reload();
            return;
        }

        var activeEl = document.activeElement;
        if (activeEl) {
            var selectorsToCheck = [
                '#lhList',
                '#lhEmpty',
                '#taskTbody',
                '#taskTable',
                '#tasksCard',
                '#historyCard',
                '#meetingList',
                '#meetingsCard',
                '#pastMeetingsCard',
                '#meetingsTable',
                '#pastMeetingsTable',
                '#historyTable',
                '#panelList',
                '#panelHistory',
                '#employeeTbody',
                '#attendanceTbody',
                '#teamList',
                '#dashboardNotifFeed',
                '#reportsCard',
                '#reportsTable',
                '#reportsGrid',
                '.stats-row',
                '.tasks-scroll'
            ];
            var isInsideReplacedContainer = false;
            selectorsToCheck.forEach(function (sel) {
                var container = document.querySelector(sel);
                if (container && container.contains(activeEl)) {
                    var tagName = (activeEl.tagName || '').toUpperCase();
                    // Only skip if the user is actively typing or selecting inside the container
                    if (tagName === 'INPUT' || tagName === 'SELECT' || tagName === 'TEXTAREA') {
                        isInsideReplacedContainer = true;
                    }
                }
            });
            if (isInsideReplacedContainer) {
                console.log('User is interacting with input elements inside the replaced container, skipping AJAX refresh.');
                return;
            }
        }

        var modalOpen = false;
        document.querySelectorAll('[id*="modal"], [class*="modal"], [id*="overlay"], [class*="overlay"]').forEach(function(el) {
            var id = (el.id || '').toLowerCase();
            var cls = (el.className || '').toLowerCase();
            
            // Ignore sidebar, notification, hamburger, or menu elements
            if (id.indexOf('sidebar') !== -1 || cls.indexOf('sidebar') !== -1 ||
                id.indexOf('notif') !== -1 || cls.indexOf('notif') !== -1 ||
                id.indexOf('hamburger') !== -1 || cls.indexOf('hamburger') !== -1 ||
                id.indexOf('menu') !== -1 || cls.indexOf('menu') !== -1) {
                return;
            }

            // Ignore inner components of modals (like boxes, headers, bodies, close buttons, footers)
            if (cls.indexOf('box') !== -1 || cls.indexOf('header') !== -1 || 
                cls.indexOf('body') !== -1 || cls.indexOf('close') !== -1 || 
                cls.indexOf('footer') !== -1 || cls.indexOf('content') !== -1 ||
                cls.indexOf('list') !== -1 || cls.indexOf('avatar') !== -1) {
                return;
            }

            var style = window.getComputedStyle(el);
            if (style.display !== 'none' && style.visibility !== 'hidden' && el.offsetWidth > 0 && el.offsetHeight > 0) {
                if (style.opacity !== '0') {
                    modalOpen = true;
                }
            }
        });
        if (modalOpen) {
            console.log('Modal/Overlay is currently open, skipping AJAX refresh.');
            return;
        }

        console.log('Refreshing page content via AJAX for type ' + type + '...');
        window.crmFetch(window.location.href, { method: 'GET', contentType: null })
            .then(function (response) {
                if (!response.ok) throw new Error('HTTP ' + response.status);
                return response.text();
            })
            .then(function (htmlText) {
                var parser = new DOMParser();
                var doc = parser.parseFromString(htmlText, 'text/html');
                var selectors = [
                    '#lhList',
                    '#lhEmpty',
                    '#lhTable',
                    '#lfHistory',
                    '#lfEmptyState',
                    '#statPending',
                    '#statApproved',
                    '#statRejected',
                    '.lf-balance-row',
                    '#taskTbody',
                    '#taskTable',
                    '#tasksCard',
                    '#historyCard',
                    '#historyTbody',
                    '#historyTable',
                    '#meetingList',
                    '#meetingsCard',
                    '#pastMeetingsCard',
                    '#meetingsTable',
                    '#pastMeetingsTable',
                    '#meetingCountBadge',
                    '#historyCountBadge',
                    '#panelList',
                    '#panelHistory',
                    '#employeeTbody',
                    '#employeeTable',
                    '#attendanceTbody',
                    '#attendanceTable',
                    '#teamList',
                    '#teamTable',
                    '#reportsCard',
                    '#reportsTable',
                    '#reportsGrid',
                    '#dashboardNotifFeed',
                    '.stats-row',
                    '.tasks-scroll',
                    '.left-col',
                    '.right-col',
                    '.dashboard-grid'
                ];

                var updatedAny = false;
                selectors.forEach(function (selector) {
                    var currentEl = document.querySelector(selector);
                    var newEl = doc.querySelector(selector);
                    if (currentEl && newEl) {
                        currentEl.innerHTML = newEl.innerHTML;
                        updatedAny = true;
                    }
                });

                // Post-refresh page-specific updates
                var path = window.location.pathname.toLowerCase();
                if (path.indexOf('/calendar') !== -1) {
                    if (typeof window.loadHolidays === 'function') {
                        window.loadHolidays();
                    }
                    updatedAny = true;
                }

                if (path.indexOf('/dashboard') !== -1) {
                    try {
                        var scriptElements = doc.querySelectorAll('script');
                        scriptElements.forEach(function (script) {
                            var text = script.textContent || '';
                            if (text.indexOf('const DB =') !== -1 || text.indexOf('window.dashboardAnalytics =') !== -1) {
                                var newScript = document.createElement('script');
                                var cleanedText = text
                                    .replace(/\bconst\s+DB\b/g, 'window.DB')
                                    .replace(/\blet\s+DB\b/g, 'window.DB');
                                newScript.textContent = cleanedText;
                                document.body.appendChild(newScript);
                                document.body.removeChild(newScript);
                            }
                        });

                        if (typeof window.initDashboardCharts === 'function') {
                            window.initDashboardCharts();
                        }

                        if (typeof window.refreshDashboardAnalytics === 'function' && window.dashboardAnalytics && window.dashboardAnalytics.initialData) {
                            window.refreshDashboardAnalytics(window.dashboardAnalytics.initialData);
                        }
                    } catch (e) {
                        console.warn('Failed to dynamically refresh dashboard charts:', e);
                    }
                    updatedAny = true;
                }

                if (updatedAny) {
                    if (window.lucide) {
                        lucide.createIcons();
                    }
                    if (typeof initPaginators === 'function') {
                        initPaginators();
                    }

                    if (path.indexOf('/leaves') !== -1) {
                        if (typeof updateStats === 'function') updateStats();
                        if (typeof applyFilters === 'function') applyFilters();
                        document.querySelectorAll('#lhList .leave-item').forEach(function (el) {
                            var s = el.dataset.status;
                            if (s === 'Approved' || s === 'Rejected') {
                                el.querySelectorAll('.btn-approve, .btn-reject').forEach(function (b) {
                                    b.style.display = 'none';
                                });
                            }
                        });
                    }
                    if (path.indexOf('/tasks') !== -1) {
                        if (typeof filterMyTasks === 'function') filterMyTasks();
                    }

                    console.log('Page content elements updated dynamically.');
                } else {
                    console.log('No specific container selectors found to update on this page.');
                }
            })
            .catch(function (error) {
                console.warn('AJAX page refresh failed:', error);
            });
    }

    /* Dynamic toast popup widget */
    function getToastContainer() {
        var container = document.getElementById('crmToastContainer');
        if (!container) {
            container = document.createElement('div');
            container.id = 'crmToastContainer';
            container.className = 'notif-toast-container';
            document.body.appendChild(container);
        }
        return container;
    }

    function showToast(n) {
        var container = getToastContainer();
        var toast = document.createElement('div');
        toast.className = 'notif-toast';
        toast.innerHTML =
            '<div class="notif-toast-icon">' +
                '<i data-lucide="' + typeIcon(n.type) + '"></i>' +
            '</div>' +
            '<div class="notif-toast-content">' +
                '<span class="notif-toast-title">' + escapeHtml(n.title || '') + '</span>' +
                '<span class="notif-toast-message">' + escapeHtml(n.message || '') + '</span>' +
            '</div>' +
            '<button type="button" class="notif-toast-close" aria-label="Close">' +
                '<i data-lucide="x"></i>' +
            '</button>';

        container.appendChild(toast);
        if (window.lucide) lucide.createIcons();

        // Trigger animation
        setTimeout(function () {
            toast.classList.add('show');
        }, 10);

        // Click redirects to the link if present
        toast.addEventListener('click', function (e) {
            if (e.target.closest('.notif-toast-close')) return;
            if (n.link) {
                window.location.href = n.link;
            }
        });

        // Close button functionality
        toast.querySelector('.notif-toast-close').addEventListener('click', function (e) {
            e.stopPropagation();
            hideToast(toast);
        });

        // Auto dismiss after 5s
        setTimeout(function () {
            hideToast(toast);
        }, 5000);
    }

    function hideToast(toast) {
        toast.classList.remove('show');
        setTimeout(function () {
            if (toast.parentNode) {
                toast.parentNode.removeChild(toast);
            }
        }, 400);
    }

    function showFeedbackToast(message, type) {
        var container = getToastContainer();
        var toast = document.createElement('div');
        toast.className = 'notif-toast';
        var color = '#17455e';
        var icon = 'info';
        var bg = '#dcecf5';
        if (type === 'success') {
            color = '#1b7a46';
            icon = 'check-circle';
            bg = '#dff5ea';
        } else if (type === 'error') {
            color = '#d11a2a';
            icon = 'alert-triangle';
            bg = '#fdecec';
        }
        
        toast.style.borderLeftColor = color;
        toast.innerHTML =
            '<div class="notif-toast-icon" style="background:' + bg + ';color:' + color + ';">' +
                '<i data-lucide="' + icon + '"></i>' +
            '</div>' +
            '<div class="notif-toast-content">' +
                '<span class="notif-toast-title">' + (type === 'success' ? 'Success' : 'Notification') + '</span>' +
                '<span class="notif-toast-message">' + escapeHtml(message || '') + '</span>' +
            '</div>' +
            '<button type="button" class="notif-toast-close" aria-label="Close">' +
                '<i data-lucide="x"></i>' +
            '</button>';

        container.appendChild(toast);
        if (window.lucide) lucide.createIcons();

        setTimeout(function () {
            toast.classList.add('show');
        }, 10);

        toast.querySelector('.notif-toast-close').addEventListener('click', function (e) {
            e.stopPropagation();
            hideToast(toast);
        });

        setTimeout(function () {
            hideToast(toast);
        }, 3000);
    }

    function setupResponsiveHeader() {
        var mobileTopbar = document.querySelector('.mobile-topbar');
        if (!mobileTopbar) return;

        var mobileRight = document.getElementById('mobileTopbarRight');
        if (!mobileRight) {
            mobileRight = document.createElement('div');
            mobileRight.id = 'mobileTopbarRight';
            mobileRight.className = 'mobile-topbar-right';
            mobileTopbar.appendChild(mobileRight);
        }

        var topbarRight = document.querySelector('.topbar-right');
        if (!topbarRight) return;

        // Create or find a wrapper for actions in desktop topbar-right to keep things neat
        var desktopActions = document.getElementById('desktopTopbarActions');
        if (!desktopActions) {
            desktopActions = document.createElement('div');
            desktopActions.id = 'desktopTopbarActions';
            desktopActions.style.display = 'flex';
            desktopActions.style.alignItems = 'center';
            desktopActions.style.gap = '14px';
            
            // Find user badge and notification wrap to move into desktopActions
            var badge = topbarRight.querySelector('.user-badge');
            var notif = document.getElementById('crmNotifWrap');
            
            if (badge) desktopActions.appendChild(badge);
            topbarRight.appendChild(desktopActions);
            if (notif) desktopActions.insertBefore(notif, badge);
        }

        function relocate() {
            var isMobile = window.innerWidth <= 768;
            var badge = document.querySelector('.user-badge');
            var notif = document.getElementById('crmNotifWrap');
            
            if (isMobile) {
                if (notif && notif.parentNode !== mobileRight) {
                    mobileRight.appendChild(notif);
                }
                if (badge && badge.parentNode !== mobileRight) {
                    mobileRight.appendChild(badge);
                }
            } else {
                if (notif && notif.parentNode !== desktopActions) {
                    if (badge) {
                        desktopActions.insertBefore(notif, badge);
                    } else {
                        desktopActions.appendChild(notif);
                    }
                }
                if (badge && badge.parentNode !== desktopActions) {
                    desktopActions.appendChild(badge);
                }
            }
        }

        relocate();
        window.addEventListener('resize', relocate);
    }

    function setupClientPagination(container, itemSelector, pageSize) {
        if (!container) return;
        
        var items = Array.prototype.slice.call(container.querySelectorAll(itemSelector));
        // Filter out empty states or tables with only one cell of "no records"
        items = items.filter(function(item) {
            return !item.classList.contains('empty-state') && 
                   !item.querySelector('td[colspan]') &&
                   item.textContent.trim().toLowerCase().indexOf('no pending') === -1 &&
                   item.textContent.trim().toLowerCase().indexOf('no meetings') === -1 &&
                   item.textContent.trim().toLowerCase().indexOf('no records') === -1 &&
                   item.textContent.trim().toLowerCase().indexOf('no attendance') === -1;
        });

        if (items.length <= pageSize) return; // No pagination needed

        // Clean up old pagination bar of the parent
        var targetParent = container;
        if (container.tagName.toUpperCase() === 'TBODY') {
            var table = container.parentNode;
            if (table && table.tagName.toUpperCase() === 'TABLE') {
                var tableWrap = table.parentNode;
                if (tableWrap && tableWrap.classList.contains('table-wrap')) {
                    targetParent = tableWrap;
                } else {
                    targetParent = table;
                }
            }
        }
        var next = targetParent.nextSibling;
        while (next) {
            var curr = next;
            next = next.nextSibling;
            if (curr.nodeType === 1 && curr.classList.contains('pagination-bar')) {
                curr.remove();
            }
        }

        var currentPage = 1;

        // Create pagination bar
        var pagBar = document.createElement('div');
        pagBar.className = 'pagination-bar';
        pagBar.style.cssText = 'display: flex; align-items: center; justify-content: space-between; padding: 14px 20px; border-top: 1px solid #edf3f6; font-size: 13px; color: #6b7280; margin-top: 15px; flex-wrap: wrap; gap: 10px;';

        var info = document.createElement('div');
        info.className = 'pagination-info';
        info.style.fontWeight = '500';
        pagBar.appendChild(info);

        var buttons = document.createElement('div');
        buttons.className = 'pagination-buttons';
        buttons.style.cssText = 'display: flex; gap: 6px; align-items: center; margin-left: auto;';

        var prevBtn = document.createElement('button');
        prevBtn.type = 'button';
        prevBtn.className = 'btn-pagination-nav';
        prevBtn.innerHTML = '❮';
        prevBtn.style.cssText = 'width: 32px; height: 32px; border-radius: 8px; border: 1.5px solid rgba(23,69,94,0.15); background: white; color: #17455e; cursor: pointer; display: flex; align-items: center; justify-content: center; font-size: 12px; transition: all 0.2s;';
        prevBtn.addEventListener('click', function() {
            if (currentPage > 1) {
                currentPage--;
                update();
            }
        });
        buttons.appendChild(prevBtn);

        var pageNumbers = document.createElement('div');
        pageNumbers.style.cssText = 'display:flex;gap:4px;';
        buttons.appendChild(pageNumbers);

        var nextBtn = document.createElement('button');
        nextBtn.type = 'button';
        nextBtn.className = 'btn-pagination-nav';
        nextBtn.innerHTML = '❯';
        nextBtn.style.cssText = 'width: 32px; height: 32px; border-radius: 8px; border: 1.5px solid rgba(23,69,94,0.15); background: white; color: #17455e; cursor: pointer; display: flex; align-items: center; justify-content: center; font-size: 12px; transition: all 0.2s;';
        nextBtn.addEventListener('click', function() {
            var activeItems = getActiveItems();
            var totalPages = Math.ceil(activeItems.length / pageSize) || 1;
            if (currentPage < totalPages) {
                currentPage++;
                update();
            }
        });
        buttons.appendChild(nextBtn);

        pagBar.appendChild(buttons);

        // Insert pagBar after container (or if it is table, after its wrapper)
        if (targetParent && targetParent.parentNode) {
            if (targetParent.nextSibling) {
                targetParent.parentNode.insertBefore(pagBar, targetParent.nextSibling);
            } else {
                targetParent.parentNode.appendChild(pagBar);
            }
        }

        function getActiveItems() {
            return items.filter(function(item) {
                var isFilterHidden = (item.style.display === 'none' && !item.classList.contains('pag-hidden'));
                return !isFilterHidden;
            });
        }

        function update() {
            var activeItems = getActiveItems();
            var totalItems = activeItems.length;
            var totalPages = Math.ceil(totalItems / pageSize) || 1;

            if (currentPage > totalPages) currentPage = totalPages;
            if (currentPage < 1) currentPage = 1;

            var startIndex = (currentPage - 1) * pageSize;
            var endIndex = Math.min(startIndex + pageSize, totalItems);

            items.forEach(function(item) {
                item.style.display = 'none';
                item.classList.add('pag-hidden');
            });

            activeItems.forEach(function(item, idx) {
                if (idx >= startIndex && idx < endIndex) {
                    item.style.display = '';
                    item.classList.remove('pag-hidden');
                }
            });

            info.textContent = 'Showing ' + (totalItems > 0 ? (startIndex + 1) : 0) + ' to ' + endIndex + ' of ' + totalItems + ' entries';

            prevBtn.disabled = (currentPage === 1);
            prevBtn.style.opacity = (currentPage === 1) ? '0.5' : '1';
            prevBtn.style.cursor = (currentPage === 1) ? 'not-allowed' : 'pointer';

            nextBtn.disabled = (currentPage === totalPages);
            nextBtn.style.opacity = (currentPage === totalPages) ? '0.5' : '1';
            nextBtn.style.cursor = (currentPage === totalPages) ? 'not-allowed' : 'pointer';

            pageNumbers.innerHTML = '';
            var maxPageButtons = 5;
            var startPage = Math.max(1, currentPage - Math.floor(maxPageButtons / 2));
            var endPage = Math.min(totalPages, startPage + maxPageButtons - 1);
            if (endPage - startPage + 1 < maxPageButtons) {
                startPage = Math.max(1, endPage - maxPageButtons + 1);
            }

            for (var i = startPage; i <= endPage; i++) {
                var btn = document.createElement('button');
                btn.type = 'button';
                btn.textContent = i;
                btn.style.cssText = 'width: 32px; height: 32px; border-radius: 8px; border: none; font-weight: 600; cursor: pointer; transition: all 0.2s; display: flex; align-items: center; justify-content: center; font-size: 13px;';
                if (i === currentPage) {
                    btn.style.background = '#17455e';
                    btn.style.color = 'white';
                    btn.style.cursor = 'default';
                } else {
                    btn.style.background = '#f3f7fa';
                    btn.style.color = '#163647';
                    btn.addEventListener('click', (function(p) {
                        return function() {
                            currentPage = p;
                            update();
                        };
                    })(i));
                }
                pageNumbers.appendChild(btn);
            }

            pagBar.style.display = totalItems > 0 ? 'flex' : 'none';
        }

        update();

        var observer = new MutationObserver(function(mutations) {
            observer.disconnect();
            update();
            observe();
        });

        function observe() {
            items.forEach(function(item) {
                observer.observe(item, { attributes: true, attributeFilter: ['style'] });
            });
        }

        observe();
    }

    function initPaginators() {
        var targets = [
            { container: '#attTable tbody', item: 'tr', size: 10 },
            { container: '#meetingsTable tbody', item: 'tr', size: 10 },
            { container: '#historyTable tbody', item: 'tr', size: 10 },
            { container: '#pastMeetingsTable tbody', item: 'tr', size: 10 },
            { container: '#perfTable tbody', item: 'tr', size: 10 },
            { container: '.table-wrap table tbody', item: 'tr', size: 10 },
            { container: '.teamTable tbody', item: 'tr', size: 10 },
            { container: '.team-grid', item: '.team-card', size: 10 },
            { container: '#reportsTable tbody', item: 'tr', size: 10 },
            { container: '.reports-grid', item: '.report-card', size: 10 },
            { container: '#lhList', item: '.leave-item', size: 10 },
            { container: '#lhList', item: '.lh-item', size: 10 },
            { container: '#taskTable tbody', item: 'tr', size: 10 },
            { container: '#usersTable tbody', item: 'tr', size: 10 },
            { container: '#adminsTable tbody', item: 'tr', size: 10 }
        ];

        targets.forEach(function(t) {
            var containers = document.querySelectorAll(t.container);
            containers.forEach(function(container) {
                if (container && !container.dataset.paginated) {
                    container.dataset.paginated = 'true';
                    setupClientPagination(container, t.item, t.size);
                }
            });
        });
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
        setupResponsiveHeader();
        initPaginators();

        // Fetch initial list of notifications
        fetchNotifications(function () {
            isInitialized = true;
        });

        // Fetch user profile and connect WebSocket in parallel
        window.crmFetch('/api/notifications/me', { method: 'GET', contentType: null })
            .then(function (r) { return r.ok ? r.json() : null; })
            .then(function (user) {
                if (user && user.id) {
                    loadLibrariesAndConnect(user.id);
                } else {
                    console.warn('Could not read user info. Falling back to HTTP polling.');
                    startPolling();
                }
            })
            .catch(function (err) {
                console.warn('User details fetch failed. Falling back to HTTP polling:', err.message);
                startPolling();
            });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', function () { init(0); });
    } else {
        init(0);
    }
})();
