/* ═══════════ CHART DATA ═══════════ */
const chartData = {
	weekly:  { labels:['Mon','Tue','Wed','Thu','Fri','Sat','Sun'], revenue:[35,20,50,48,38,28,15], sales:[22,14,30,28,25,18,10], bigNum:'$495K' },
	monthly: { labels:['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'], revenue:[35,20,50,50,35,10,50,38,30,28,8,15], sales:[25,15,30,20,22,8,38,28,22,20,5,10], bigNum:'$1.2M' },
	yearly:  { labels:['2020','2021','2022','2023','2024','2025'], revenue:[28,38,42,50,55,60], sales:[18,25,30,35,40,48], bigNum:'$5.8M' }
};

function buildRevChart(tabKey) {
	const d = chartData[tabKey];
	const area = document.getElementById('revenueChart');
	const xAxis = document.getElementById('xAxisLabels');
	area.innerHTML = ''; xAxis.innerHTML = '';
	document.getElementById('revBigNum').textContent = d.bigNum;

	d.labels.forEach((m, i) => {
		const col = document.createElement('div');
		col.className = 'bar-col';

		const rH = Math.round((d.revenue[i] / 60) * 100);
		const sH = Math.round((d.sales[i] / 60) * 100);

		col.innerHTML = `
			<div class="bar-pair">
				<div class="bar bar-primary" style="--bh:${rH}" data-tip="$${d.revenue[i]}k"></div>
				<div class="bar bar-secondary" style="--bh:${sH}" data-tip="$${d.sales[i]}k"></div>
			</div>
		`;

		area.appendChild(col);

		const lbl = document.createElement('span');
		lbl.textContent = m;
		xAxis.appendChild(lbl);
	});

	setTimeout(() => {
		document.querySelectorAll('.bar').forEach((b, i) => {
			setTimeout(() => b.classList.add('rendered'), i * 30);
		});
	}, 50);
}

function buildProfitChart() {
	const data = [2,4,3,7,4,6,5,9,7,12,10,15];
	const c = document.getElementById('profitBars');

	data.forEach(v => {
		const col = document.createElement('div');
		col.className = 'pb-col';

		col.innerHTML = `<div class="pb-bar" style="--ph:${(v/15)*100}"></div>`;
		c.appendChild(col);
	});

	setTimeout(() => {
		document.querySelectorAll('.pb-bar').forEach((b, i) => {
			setTimeout(() => b.classList.add('rendered'), i * 50 + 500);
		});
	}, 300);
}

/* ═══════════ TAB SWITCHING ═══════════ */
function switchTab(btn, tabKey) {
	document.querySelectorAll('#revTabs .tab-btn').forEach(b => b.classList.remove('active'));
	btn.classList.add('active');
	buildRevChart(tabKey);
}

/* ═══════════ SIDEBAR TOGGLE ═══════════ */
document.getElementById('sidebarToggle').addEventListener('click', function () {
	document.getElementById('sidebar').classList.toggle('collapsed');
	document.getElementById('layout').classList.toggle('sidebar-collapsed');

	const icon = document.getElementById('toggleIcon');
	icon.classList.toggle('fa-chevron-left');
	icon.classList.toggle('fa-chevron-right');
});

/* ═══════════ SUBMENU */
function toggleSub(row) {
	const item = row.closest('.nav-item');
	const isOpen = item.classList.contains('open');

	document.querySelectorAll('.nav-item.has-sub').forEach(i => i.classList.remove('open'));
	if (!isOpen) item.classList.add('open');
}

function setSubActive(li) {
	document.querySelectorAll('.submenu li').forEach(i => i.classList.remove('active'));
	li.classList.add('active');
}

function setNavActive(row) {
	document.querySelectorAll('.nav-item').forEach(i => i.classList.remove('active'));
	row.closest('.nav-item').classList.add('active');
}

/* ═══════════ DROPDOWNS */
function toggleDropdown(btn, dropdown) {
	const isOpen = dropdown.classList.contains('open');
	closeAllDropdowns();
	if (!isOpen) dropdown.classList.add('open');
}

function closeAllDropdowns() {
	document.querySelectorAll('.notif-dropdown,.msg-dropdown,.apps-dropdown,.user-dropdown,.search-dropdown')
		.forEach(d => d.classList.remove('open'));
}

/* ═══════════ MODAL HELPERS ═══════════ */
function openModal(id) {
	document.getElementById(id).classList.remove('hidden');
}

function closeModal(id) {
	document.getElementById(id).classList.add('hidden');
}

/* ═══════════ LOGOUT ═══════════ */
function confirmLogout() {
	closeAllDropdowns();
	openModal('logoutModal');
}

/*
 * doLogout() — called by the red Logout button inside the confirm modal.
 * 1. Closes the modal
 * 2. Shows the success toast
 * 3. Redirects to the login page after 1.2 s so the toast is visible
 *
 * ⚠️  Change '/login' below to match your actual Spring Boot login URL.
 */
function doLogout() {
	closeModal('logoutModal');
	showToast('Logging out…', 'info');
	// Submit a hidden POST form so Spring Security processes the logout properly
	setTimeout(() => {
		const form = document.createElement('form');
		form.method = 'POST';
		form.action = '/logout';
		const csrf = document.querySelector('meta[name="_csrf"]');
		const csrfHeader = document.querySelector('meta[name="_csrf_header"]');
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

/* ═══════════ SETTINGS / PROFILE / DATE / MEMBER MODALS ═══════════ */
function openSettingsModal() {
	closeAllDropdowns();
	openModal('settingsModal');
}

function openProfileModal() {
	closeAllDropdowns();
	openModal('profileModal');
}

function openDateModal() {
	openModal('dateModal');
}

function openAddMemberModal() {
	openModal('addMemberModal');
}

/* ═══════════ DEAL MODAL ═══════════ */
function openDealModal(company, value, growth, region) {
	document.getElementById('dealCompany').textContent = company;
	document.getElementById('dealValue').textContent = value;
	document.getElementById('dealGrowth').textContent = growth;
	document.getElementById('dealRegion').textContent = region;
	document.getElementById('dealModalTitle').textContent = company + ' — Deal Details';
	openModal('dealModal');
}

/* ═══════════ DATE RANGE QUICK SELECT ═══════════ */
function setDateRange(label) {
	document.getElementById('dateRange').textContent = label;
	showToast('Date range set to: ' + label, 'success');
}

/* ═══════════ DOWNLOAD / REFRESH ═══════════ */
function downloadReport() {
	showToast('Report download started…', 'info');
}

function refreshDashboard() {
	const icon = document.getElementById('refreshIcon');
	icon.style.animation = 'spinSlow 0.7s linear';
	showToast('Dashboard refreshed!', 'success');
	setTimeout(() => { icon.style.animation = ''; }, 750);
}

/* ═══════════ DOM READY ═══════════ */
document.addEventListener('DOMContentLoaded', () => {

	/* ── Notification / Message / Apps / User dropdowns ── */
	document.getElementById('notifBtn').onclick = e => {
		e.stopPropagation();
		toggleDropdown(e.target, document.getElementById('notifDropdown'));
	};

	document.getElementById('msgBtn').onclick = e => {
		e.stopPropagation();
		toggleDropdown(e.target, document.getElementById('msgDropdown'));
	};

	document.getElementById('appsBtn').onclick = e => {
		e.stopPropagation();
		toggleDropdown(e.target, document.getElementById('appsDropdown'));
	};

	document.getElementById('userChip').onclick = e => {
		e.stopPropagation();
		toggleDropdown(e.target, document.getElementById('userDropdown'));
	};

	/*
	 * Wire the red "Logout" button inside #logoutModal to doLogout().
	 * We use addEventListener here and strip the inline onclick from HTML
	 * so there is only ONE handler and no double-fire.
	 */
	const logoutConfirmBtn = document.querySelector('#logoutModal .btn-danger');
	if (logoutConfirmBtn) {
		logoutConfirmBtn.removeAttribute('onclick');
		logoutConfirmBtn.addEventListener('click', doLogout);
	}

	/* ── Mark all notifications read ── */
	document.getElementById('markAllRead').addEventListener('click', function () {
		document.querySelectorAll('.notif-item.unread').forEach(item => item.classList.remove('unread'));
		const badge = document.getElementById('notifBadge');
		if (badge) badge.style.display = 'none';
		showToast('All notifications marked as read', 'success');
	});

	/* ── Topbar logout button (user dropdown) ── */
	document.getElementById('logoutBtn').addEventListener('click', function (e) {
		e.stopPropagation();
		confirmLogout();
	});

	/* ── Fullscreen toggle ── */
	document.getElementById('fullscreenBtn').addEventListener('click', function () {
		const icon = document.getElementById('fullscreenIcon');
		if (!document.fullscreenElement) {
			document.documentElement.requestFullscreen().catch(() => {});
			icon.classList.replace('fa-expand', 'fa-compress');
		} else {
			document.exitFullscreen().catch(() => {});
			icon.classList.replace('fa-compress', 'fa-expand');
		}
	});

	/* ── Dark mode toggle ── */
	document.getElementById('darkModeBtn').addEventListener('click', function () {
		document.body.classList.toggle('dark-mode');
		const icon = this.querySelector('i');
		icon.classList.toggle('fa-moon');
		icon.classList.toggle('fa-sun');
		showToast(document.body.classList.contains('dark-mode') ? 'Dark mode on' : 'Dark mode off', 'info');
	});

	/* ── Search box focus / shortcut ── */
	const searchInput = document.getElementById('searchInput');
	const searchDropdown = document.getElementById('searchDropdown');

	searchInput.addEventListener('focus', () => searchDropdown.classList.add('open'));
	searchInput.addEventListener('blur', () => setTimeout(() => searchDropdown.classList.remove('open'), 180));

	document.addEventListener('keydown', e => {
		if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
			e.preventDefault();
			searchInput.focus();
		}
		if (e.key === 'Escape') {
			closeAllDropdowns();
			document.querySelectorAll('.modal-overlay').forEach(m => m.classList.add('hidden'));
		}
	});

	/* ── Close modals on overlay click ── */
	document.querySelectorAll('.modal-overlay').forEach(overlay => {
		overlay.addEventListener('click', function (e) {
			if (e.target === this) {
				this.classList.add('hidden');
			}
		});
	});

	/* ── Close dropdowns on outside click ── */
	document.addEventListener('click', closeAllDropdowns);

	/* ── Sparkline animation ── */
	setTimeout(() => {
		document.querySelectorAll('.sp-bar').forEach((bar, i) => {
			setTimeout(() => bar.classList.add('lit'), i * 80);
		});
	}, 600);

	/* ── Overview segments animate in ── */
	setTimeout(() => {
		document.querySelectorAll('.ov-seg').forEach(seg => seg.classList.add('go'));
	}, 400);

	/* ── Donut chart animate in ── */
	setTimeout(() => {
		document.querySelectorAll('.donut-seg').forEach(seg => seg.classList.add('go'));
	}, 500);

	/* ── INIT charts ── */
	buildRevChart('weekly');
	buildProfitChart();

	setTimeout(() => showToast('Welcome back, Admin! Dashboard loaded.', 'success'), 1000);
});

/* ═══════════ TOAST ═══════════ */
function showToast(msg, type = 'info') {
	const icons = { success:'fa-circle-check', info:'fa-circle-info', warning:'fa-triangle-exclamation' };

	const container = document.getElementById('toastContainer');
	const toast = document.createElement('div');

	toast.className = `toast toast-${type}`;
	toast.innerHTML = `<i class="fas ${icons[type]}"></i><span>${msg}</span>`;

	container.appendChild(toast);

	setTimeout(() => {
		toast.style.opacity = '0';
		setTimeout(() => toast.remove(), 300);
	}, 2800);
}