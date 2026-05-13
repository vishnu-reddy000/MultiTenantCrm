/* ============================================================
   CRMS MANAGER DASHBOARD — dashboard-manager.js
   All lead data is server-rendered via Thymeleaf.
   This file handles only UI interactions.
   ============================================================ */

/* ═══════════
   MODAL HELPERS
═══════════ */
function openModal(id)  { document.getElementById(id).classList.remove('hidden'); }
function closeModal(id) { document.getElementById(id).classList.add('hidden'); }

function openAddLeadModal() {
	closeAllDropdowns();
	openModal('leadModal');
}

/* Open the edit modal and populate fields from data-* attributes */
function openEditLeadModal(el) {
	var id          = el.getAttribute('data-id');
	var customerName= el.getAttribute('data-name')    || '';
	var company     = el.getAttribute('data-company') || '';
	var dealValue   = el.getAttribute('data-value')   || 0;
	var email       = el.getAttribute('data-email')   || '';
	var phone       = el.getAttribute('data-phone')   || '';
	var notes       = el.getAttribute('data-notes')   || '';

	const form = document.getElementById('editLeadForm');
	form.action = '/manager/leads/' + id + '/edit';
	document.getElementById('editCustomerName').value = customerName;
	document.getElementById('editCompany').value      = company;
	document.getElementById('editDealValue').value    = dealValue;
	document.getElementById('editEmail').value        = email;
	document.getElementById('editPhone').value        = phone;
	document.getElementById('editNotes').value        = notes;
	closeAllDropdowns();
	openModal('editLeadModal');
}

/* ═══════════
   SIDEBAR TOGGLE
═══════════ */
document.addEventListener('DOMContentLoaded', function () {

	const sidebarToggle = document.getElementById('sidebarToggle');
	if (sidebarToggle) {
		sidebarToggle.addEventListener('click', function () {
			document.getElementById('sidebar').classList.toggle('collapsed');
			document.getElementById('layout').classList.toggle('sidebar-collapsed');
			const icon = document.getElementById('toggleIcon');
			if (icon) {
				icon.classList.toggle('fa-chevron-left');
				icon.classList.toggle('fa-chevron-right');
			}
		});
	}

	/* ── Dropdowns ── */
	const notifBtn = document.getElementById('notifBtn');
	if (notifBtn) notifBtn.onclick = e => { e.stopPropagation(); toggleDropdown(e.currentTarget, document.getElementById('notifDropdown')); };

	const msgBtn = document.getElementById('msgBtn');
	if (msgBtn) msgBtn.onclick = e => { e.stopPropagation(); toggleDropdown(e.currentTarget, document.getElementById('msgDropdown')); };

	const appsBtn = document.getElementById('appsBtn');
	if (appsBtn) appsBtn.onclick = e => { e.stopPropagation(); toggleDropdown(e.currentTarget, document.getElementById('appsDropdown')); };

	const userChip = document.getElementById('userChip');
	if (userChip) userChip.onclick = e => { e.stopPropagation(); toggleDropdown(e.currentTarget, document.getElementById('userDropdown')); };

	/* ── Mark all read ── */
	const markAllRead = document.getElementById('markAllRead');
	if (markAllRead) {
		markAllRead.addEventListener('click', () => {
			document.querySelectorAll('.notif-item.unread').forEach(i => i.classList.remove('unread'));
			const badge = document.getElementById('notifBadge');
			if (badge) badge.style.display = 'none';
			showToast('All notifications marked as read', 'success');
		});
	}

	/* ── Logout ── */
	const logoutBtn = document.getElementById('logoutBtn');
	if (logoutBtn) {
		logoutBtn.addEventListener('click', e => {
			e.stopPropagation();
			confirmLogout();
		});
	}

	/* ── Fullscreen ── */
	const fullscreenBtn = document.getElementById('fullscreenBtn');
	if (fullscreenBtn) {
		fullscreenBtn.addEventListener('click', function () {
			const icon = document.getElementById('fullscreenIcon');
			if (!document.fullscreenElement) {
				document.documentElement.requestFullscreen().catch(() => {});
				if (icon) icon.classList.replace('fa-expand', 'fa-compress');
			} else {
				document.exitFullscreen().catch(() => {});
				if (icon) icon.classList.replace('fa-compress', 'fa-expand');
			}
		});
	}

	/* ── Dark mode ── */
	const darkModeBtn = document.getElementById('darkModeBtn');
	if (darkModeBtn) {
		darkModeBtn.addEventListener('click', function () {
			document.body.classList.toggle('dark-mode');
			const icon = this.querySelector('i');
			if (icon) {
				icon.classList.toggle('fa-moon');
				icon.classList.toggle('fa-sun');
			}
			showToast(document.body.classList.contains('dark-mode') ? 'Dark mode on' : 'Dark mode off', 'info');
		});
	}

	/* ── Search ── */
	const searchInput    = document.getElementById('searchInput');
	const searchDropdown = document.getElementById('searchDropdown');
	if (searchInput && searchDropdown) {
		searchInput.addEventListener('focus', () => searchDropdown.classList.add('open'));
		searchInput.addEventListener('blur',  () => setTimeout(() => searchDropdown.classList.remove('open'), 180));
	}

	/* ── Keyboard shortcuts ── */
	document.addEventListener('keydown', e => {
		if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
			e.preventDefault();
			if (searchInput) searchInput.focus();
		}
		if (e.key === 'Escape') {
			closeAllDropdowns();
			document.querySelectorAll('.modal-overlay').forEach(m => m.classList.add('hidden'));
		}
	});

	/* ── Close modals on overlay click ── */
	document.querySelectorAll('.modal-overlay').forEach(overlay => {
		overlay.addEventListener('click', function (e) {
			if (e.target === this) this.classList.add('hidden');
		});
	});

	/* ── Close dropdowns on outside click ── */
	document.addEventListener('click', closeAllDropdowns);

	/* ── Animate pipeline bars ── */
	setTimeout(() => {
		document.querySelectorAll('.ps-bar-fill').forEach(bar => {
			bar.style.transition = 'width .8s ease';
		});
	}, 300);

	/* ── Donut chart from server-rendered values ── */
	initDonut();
});

/* ═══════════
   DONUT CHART (reads from Thymeleaf-rendered KPI values)
═══════════ */
function initDonut() {
	const totalEl    = document.querySelector('.kpi-card:nth-child(1) .kpi-value');
	const approvedEl = document.querySelector('.kpi-card:nth-child(2) .kpi-value');
	const pendingEl  = document.querySelector('.kpi-card:nth-child(3) .kpi-value');
	const rejectedEl = document.querySelector('.kpi-card:nth-child(4) .kpi-value');

	const total    = parseInt(totalEl?.textContent)    || 0;
	const approved = parseInt(approvedEl?.textContent) || 0;
	const pending  = parseInt(pendingEl?.textContent)  || 0;
	const rejected = parseInt(rejectedEl?.textContent) || 0;

	if (!total) return;

	const C = 2 * Math.PI * 58; // circumference for r=58

	const segs = [
		{ id: 'seg1', count: approved, off: 0 },
		{ id: 'seg2', count: rejected, off: (approved / total) * C },
		{ id: 'seg3', count: pending,  off: ((approved + rejected) / total) * C },
	];

	segs.forEach(s => {
		const el = document.getElementById(s.id);
		if (!el) return;
		const dash = (s.count / total) * C;
		el.style.strokeDasharray  = `${dash} ${C - dash}`;
		el.style.strokeDashoffset = -s.off;
	});
}

/* ═══════════
   DROPDOWN HELPERS
═══════════ */
function toggleDropdown(_, dropdown) {
	const isOpen = dropdown.classList.contains('open');
	closeAllDropdowns();
	if (!isOpen) dropdown.classList.add('open');
}

function closeAllDropdowns() {
	document.querySelectorAll('.notif-dropdown,.msg-dropdown,.apps-dropdown,.user-dropdown,.search-dropdown')
		.forEach(d => d.classList.remove('open'));
}

/* ═══════════
   LOGOUT
═══════════ */
function confirmLogout() {
	closeAllDropdowns();
	openModal('logoutModal');
}

function doLogout() {
	closeModal('logoutModal');
	showToast('Logging out…', 'info');
	setTimeout(() => {
		const form = document.createElement('form');
		form.method = 'POST';
		form.action = '/logout';
		const csrf = document.querySelector('meta[name="_csrf"]');
		if (csrf) {
			const input = document.createElement('input');
			input.type  = 'hidden';
			input.name  = '_csrf';
			input.value = csrf.getAttribute('content');
			form.appendChild(input);
		}
		document.body.appendChild(form);
		form.submit();
	}, 800);
}

/* ═══════════
   DATE MODAL
═══════════ */
function openDateModal() { openModal('dateModal'); }

function setDateRange(label) {
	const el = document.getElementById('dateRange');
	if (el) el.textContent = label;
	showToast('Date range set to: ' + label, 'success');
	closeModal('dateModal');
}

function downloadReport() { showToast('Report download started…', 'info'); }

function refreshDashboard() {
	const icon = document.getElementById('refreshIcon');
	if (icon) icon.style.animation = 'spinSlow 0.7s linear';
	setTimeout(() => { if (icon) icon.style.animation = ''; }, 750);
	showToast('Refreshing…', 'info');
	setTimeout(() => window.location.reload(), 500);
}

/* ═══════════
   TOAST
═══════════ */
function showToast(msg, type = 'info') {
	const icons = { success: 'fa-circle-check', info: 'fa-circle-info', warning: 'fa-triangle-exclamation', error: 'fa-circle-xmark' };
	const container = document.getElementById('toastContainer');
	if (!container) return;
	const toast = document.createElement('div');
	toast.className = `toast toast-${type}`;
	toast.innerHTML = `<i class="fas ${icons[type] || icons.info}"></i><span>${msg}</span>`;
	container.appendChild(toast);
	setTimeout(() => {
		toast.style.opacity = '0';
		toast.style.transition = 'opacity .3s';
		setTimeout(() => toast.remove(), 300);
	}, 2800);
}
