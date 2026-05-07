/* ============================================================
   CRMS MANAGER DASHBOARD — dashboard-manager.js
   ============================================================ */

/* ═══════════════════════════════════════════
   DATA STORE — In-memory lead & deal records
═══════════════════════════════════════════ */
let leads = [
	{ id: 1, name: 'TechCorp Solutions', company: 'TechCorp Pvt Ltd', email: 'cto@techcorp.in', phone: '+91 98001 11111', value: 850000, status: 'approved', assignee: 'Rahul Sharma', priority: 'high',   notes: 'Interested in enterprise plan', date: '2026-04-02' },
	{ id: 2, name: 'DataVault Inc',       company: 'DataVault Systems', email: 'bd@datavault.io', phone: '+91 98002 22222', value: 320000, status: 'pending',  assignee: 'Priya Patel',   priority: 'medium', notes: 'Needs custom integration', date: '2026-04-05' },
	{ id: 3, name: 'Nexus Retail Chain',  company: 'Nexus Retail',     email: 'ops@nexus.co', phone: '+91 98003 33333',  value: 1200000, status: 'deal',     assignee: 'Rahul Sharma', priority: 'high',   notes: 'Converted — 40 stores', date: '2026-03-28' },
	{ id: 4, name: 'CloudBridge AI',      company: 'CloudBridge',      email: 'hi@cloudbridge.ai', phone: '+91 98004 44444', value: 560000, status: 'approved', assignee: 'Arjun Menon',   priority: 'high',   notes: 'AI analytics package', date: '2026-04-08' },
	{ id: 5, name: 'UrbanEdge Constructs', company: 'UrbanEdge',       email: 'pm@urbanedge.in', phone: '+91 98005 55555',  value: 240000, status: 'rejected', assignee: 'Kavya Reddy',  priority: 'low',    notes: 'Budget mismatch', date: '2026-04-01' },
	{ id: 6, name: 'SwiftPay Fintech',    company: 'SwiftPay',         email: 'b2b@swiftpay.in', phone: '+91 98006 66666', value: 780000, status: 'pending',  assignee: 'Priya Patel',   priority: 'medium', notes: 'Awaiting legal review', date: '2026-04-10' },
	{ id: 7, name: 'HealthFirst Labs',    company: 'HealthFirst',      email: 'crm@hflabs.in',  phone: '+91 98007 77777', value: 430000, status: 'approved', assignee: 'Kavya Reddy',  priority: 'medium', notes: 'Pilot program started', date: '2026-04-03' },
	{ id: 8, name: 'EduPlus Academy',     company: 'EduPlus',          email: 'it@eduplus.edu',  phone: '+91 98008 88888', value: 190000, status: 'pending',  assignee: 'Arjun Menon',   priority: 'low',    notes: 'Multiple campuses', date: '2026-04-11' },
];

let deals = [
	{ id: 1, name: 'Nexus Retail Chain', company: 'Nexus Retail',    value: 1200000, stage: 'closing',     exec: 'Rahul Sharma', closeDate: '2026-04-20', probability: 85 },
	{ id: 2, name: 'CloudBridge AI',     company: 'CloudBridge',     value: 560000,  stage: 'negotiation', exec: 'Arjun Menon',  closeDate: '2026-04-28', probability: 65 },
	{ id: 3, name: 'TechCorp Solutions', company: 'TechCorp Pvt Ltd', value: 850000,  stage: 'proposal',    exec: 'Rahul Sharma', closeDate: '2026-05-05', probability: 45 },
	{ id: 4, name: 'HealthFirst Labs',   company: 'HealthFirst',     value: 430000,  stage: 'discovery',   exec: 'Kavya Reddy', closeDate: '2026-05-12', probability: 30 },
];

let teamMembers = [
	{ name: 'Rahul Sharma', role: 'Sales Executive', leads: 8, color: 'linear-gradient(135deg,#0d9488,#0891b2)', initials: 'RS', online: true },
	{ name: 'Priya Patel',  role: 'Sales Executive', leads: 6, color: 'linear-gradient(135deg,#8b5cf6,#0d9488)', initials: 'PP', online: true },
	{ name: 'Arjun Menon',  role: 'Sales Executive', leads: 5, color: 'linear-gradient(135deg,#f97316,#fbbf24)', initials: 'AM', online: false },
	{ name: 'Kavya Reddy',  role: 'Sales Executive', leads: 7, color: 'linear-gradient(135deg,#ef4444,#f97316)', initials: 'KR', online: true },
];

let activityFeed = [
	{ type: 'approved', title: 'Lead Approved',      sub: 'TechCorp Solutions approved by Admin',   time: '5m', color: '#10b981' },
	{ type: 'added',    title: 'New Lead Added',      sub: 'EduPlus Academy submitted for review',   time: '1h', color: '#0d9488' },
	{ type: 'deal',     title: 'Deal Converted',      sub: 'Nexus Retail moved to Deals',            time: '3h', color: '#3b82f6' },
	{ type: 'rejected', title: 'Lead Rejected',       sub: 'UrbanEdge Constructs rejected',          time: '5h', color: '#ef4444' },
	{ type: 'call',     title: 'Call Scheduled',      sub: 'SwiftPay Fintech — Rahul Sharma',        time: '1d', color: '#f97316' },
	{ type: 'edited',   title: 'Lead Updated',        sub: 'CloudBridge AI details revised',         time: '1d', color: '#8b5cf6' },
];

let editingLeadId = null;
let deletingLeadId = null;
let currentFilter   = 'all';

/* ═══════════
   RENDER LEADS TABLE
═══════════ */
function renderLeads(filter) {
	currentFilter = filter || currentFilter;
	const tbody = document.getElementById('leadsTableBody');
	const filtered = currentFilter === 'all'
		? leads
		: leads.filter(l => l.status === currentFilter);

	tbody.innerHTML = '';

	if (filtered.length === 0) {
		tbody.innerHTML = `<tr><td colspan="7" style="text-align:center;padding:2rem;color:var(--muted);font-size:.82rem">No leads found</td></tr>`;
		return;
	}

	filtered.forEach((lead, i) => {
		const row = document.createElement('tr');
		row.style.animationDelay = `${i * 0.04}s`;
		row.classList.add('row-in');
		row.innerHTML = `
			<td>
				<div class="cell-name">${lead.name}</div>
				<div style="font-size:.67rem;color:var(--muted);margin-top:1px">${formatCurrency(lead.value)}</div>
			</td>
			<td>${lead.company}</td>
			<td>
				<div style="font-size:.74rem">${lead.email}</div>
				<div style="font-size:.67rem;color:var(--muted)">${lead.phone}</div>
			</td>
			<td style="font-weight:700;color:var(--text)">${formatCurrency(lead.value)}</td>
			<td><span class="status-badge ${statusClass(lead.status)}">${capitalise(lead.status)}</span></td>
			<td>
				<div style="display:flex;align-items:center;gap:.38rem">
					<div style="width:22px;height:22px;border-radius:50%;background:linear-gradient(135deg,var(--primary),var(--secondary));flex-shrink:0"></div>
					<span style="font-size:.75rem">${lead.assignee || '—'}</span>
				</div>
			</td>
			<td>
				<div class="action-btns">
					<button class="act-btn view"   onclick="viewLead(${lead.id})" title="View"><i class="fas fa-eye"></i></button>
					<button class="act-btn edit"   onclick="editLead(${lead.id})" title="Edit" ${lead.status === 'approved' || lead.status === 'deal' ? 'style="opacity:.45;pointer-events:none"' : ''}><i class="fas fa-pen"></i></button>
					<button class="act-btn delete" onclick="deleteLead(${lead.id})" title="Delete"><i class="fas fa-trash"></i></button>
				</div>
			</td>
		`;
		tbody.appendChild(row);
	});
}

/* ═══════════
   RENDER DEALS TABLE
═══════════ */
function renderDeals() {
	const tbody = document.getElementById('dealsTableBody');
	tbody.innerHTML = '';
	deals.forEach((deal, i) => {
		const row = document.createElement('tr');
		row.style.animationDelay = `${i * 0.06}s`;
		row.classList.add('row-in');
		row.innerHTML = `
			<td><div class="cell-name">${deal.name}</div></td>
			<td>${deal.company}</td>
			<td style="font-weight:700;color:var(--text)">${formatCurrency(deal.value)}</td>
			<td><span class="stage-tag ${stageClass(deal.stage)}">${capitalise(deal.stage)}</span></td>
			<td>
				<div style="display:flex;align-items:center;gap:.38rem">
					<div style="width:22px;height:22px;border-radius:50%;background:linear-gradient(135deg,var(--primary),var(--secondary));flex-shrink:0"></div>
					<span style="font-size:.75rem">${deal.exec}</span>
				</div>
			</td>
			<td style="font-size:.75rem;color:var(--muted)">${deal.closeDate}</td>
			<td>
				<div class="prob-wrap">
					<div class="prob-bar-bg"><div class="prob-bar-fill" style="width:${deal.probability}%"></div></div>
					<span class="prob-txt">${deal.probability}%</span>
				</div>
			</td>
		`;
		tbody.appendChild(row);
	});
}

/* ═══════════
   RENDER PIPELINE
═══════════ */
function renderPipeline() {
	const counts = { approved: 0, pending: 0, rejected: 0, deal: 0 };
	leads.forEach(l => { if (counts[l.status] !== undefined) counts[l.status]++; });

	const stages = [
		{ label: 'Approved', key: 'approved', color: '#10b981', max: leads.length },
		{ label: 'Pending Approval', key: 'pending',  color: '#f59e0b', max: leads.length },
		{ label: 'Converted to Deal', key: 'deal',   color: '#3b82f6', max: leads.length },
		{ label: 'Rejected',  key: 'rejected', color: '#ef4444', max: leads.length },
	];

	const container = document.getElementById('pipelineStages');
	container.innerHTML = '';
	stages.forEach(s => {
		const pct = leads.length ? Math.round((counts[s.key] / leads.length) * 100) : 0;
		const div = document.createElement('div');
		div.className = 'pipeline-stage';
		div.innerHTML = `
			<div class="ps-header">
				<span class="ps-label" style="display:flex;align-items:center;gap:.38rem">
					<span style="width:7px;height:7px;border-radius:50%;background:${s.color};display:inline-block"></span>
					${s.label}
				</span>
				<span class="ps-count">${counts[s.key]} <span style="font-size:.65rem;color:var(--muted);font-weight:400">(${pct}%)</span></span>
			</div>
			<div class="ps-bar-bg">
				<div class="ps-bar-fill" style="width:0;background:${s.color};--target-w:${pct}%"></div>
			</div>
		`;
		container.appendChild(div);
	});

	// Animate bars
	setTimeout(() => {
		document.querySelectorAll('.ps-bar-fill').forEach(bar => {
			bar.style.width = bar.style.getPropertyValue('--target-w') || bar.style.cssText.match(/--target-w:([^;]+)/)?.[1] || '0%';
		});
	}, 300);
}

/* ═══════════
   RENDER ACTIVITY
═══════════ */
function renderActivity() {
	const list = document.getElementById('activityList');
	list.innerHTML = '';
	activityFeed.forEach(a => {
		const div = document.createElement('div');
		div.className = 'activity-item';
		div.innerHTML = `
			<div class="act-dot" style="background:${a.color}"></div>
			<div class="act-content">
				<div class="act-title">${a.title}</div>
				<div class="act-sub">${a.sub}</div>
			</div>
			<span class="act-time">${a.time}</span>
		`;
		list.appendChild(div);
	});
}

/* ═══════════
   RENDER TEAM
═══════════ */
function renderTeam() {
	const grid = document.getElementById('teamGrid');
	grid.innerHTML = '';
	teamMembers.forEach((m, i) => {
		const card = document.createElement('div');
		card.className = 'team-card';
		card.style.animationDelay = `${0.1 + i * 0.07}s`;
		card.innerHTML = `
			<div style="position:relative">
				<div class="team-av" style="background:${m.color}">${m.initials}</div>
				${m.online ? '<div class="team-status" style="position:absolute;bottom:2px;right:2px"></div>' : ''}
			</div>
			<div class="team-name">${m.name}</div>
			<div class="team-role">${m.role}</div>
			<div class="team-leads"><i class="fas fa-funnel-dollar"></i> ${m.leads} leads assigned</div>
		`;
		card.onclick = () => showToast(`${m.name} — ${m.leads} leads, ${m.online ? 'Online' : 'Offline'}`, 'info');
		grid.appendChild(card);
	});
}

/* ═══════════
   DONUT CHART
═══════════ */
function initDonut() {
	const total = leads.length;
	if (!total) return;

	const approved = leads.filter(l => l.status === 'approved').length;
	const rejected = leads.filter(l => l.status === 'rejected').length;
	const pending  = leads.filter(l => l.status === 'pending').length;
	const deal     = leads.filter(l => l.status === 'deal').length;

	const circumference = 2 * Math.PI * 58; // r=58 → ~364
	const C = circumference;

	function seg(count) {
		const dash = (count / total) * C;
		return `${dash} ${C - dash}`;
	}

	let offset = 0;
	const segs = [
		{ id: 'seg1', count: approved, color: 'var(--primary)', off: 0 },
		{ id: 'seg2', count: rejected, color: 'var(--orange)',  off: (approved / total) * C },
		{ id: 'seg3', count: pending,  color: 'var(--peach)',   off: ((approved + rejected) / total) * C },
		{ id: 'seg4', count: deal,     color: 'var(--secondary)', off: ((approved + rejected + pending) / total) * C },
	];

	segs.forEach(s => {
		const el = document.getElementById(s.id);
		if (!el) return;
		const dash = (s.count / total) * C;
		el.style.strokeDasharray = `${dash} ${C - dash}`;
		el.style.strokeDashoffset = -s.off;
		el.setAttribute('stroke', s.color);
	});
}

/* ═══════════
   FILTER LEADS
═══════════ */
function filterLeads(filter, btn) {
	currentFilter = filter;
	document.querySelectorAll('.ftab').forEach(b => b.classList.remove('active'));
	if (btn) btn.classList.add('active');
	renderLeads(filter);
}

/* ═══════════
   OPEN / CLOSE LEAD MODAL
═══════════ */
function openAddLeadModal() {
	editingLeadId = null;
	document.getElementById('leadModalTitle').textContent = 'Add New Lead';
	clearLeadForm();
	closeAllDropdowns();
	openModal('leadModal');
}

function clearLeadForm() {
	['leadName','leadCompany','leadValue','leadEmail','leadPhone','leadNotes'].forEach(id => {
		const el = document.getElementById(id);
		if (el) el.value = '';
	});
	const assignee = document.getElementById('leadAssignee');
	const priority = document.getElementById('leadPriority');
	if (assignee) assignee.value = '';
	if (priority) priority.value = 'medium';
}

function editLead(id) {
	const lead = leads.find(l => l.id === id);
	if (!lead) return;
	editingLeadId = id;
	document.getElementById('leadModalTitle').textContent = 'Edit Lead';
	document.getElementById('leadName').value    = lead.name;
	document.getElementById('leadCompany').value = lead.company;
	document.getElementById('leadValue').value   = lead.value;
	document.getElementById('leadEmail').value   = lead.email;
	document.getElementById('leadPhone').value   = lead.phone;
	document.getElementById('leadNotes').value   = lead.notes;
	const assignee = document.getElementById('leadAssignee');
	if (assignee) {
		Array.from(assignee.options).forEach(o => {
			if (o.text === lead.assignee) o.selected = true;
		});
	}
	const priority = document.getElementById('leadPriority');
	if (priority) priority.value = lead.priority;
	openModal('leadModal');
}

function saveLead() {
	const name     = document.getElementById('leadName').value.trim();
	const company  = document.getElementById('leadCompany').value.trim();
	const value    = parseFloat(document.getElementById('leadValue').value) || 0;
	const email    = document.getElementById('leadEmail').value.trim();
	const phone    = document.getElementById('leadPhone').value.trim();
	const notes    = document.getElementById('leadNotes').value.trim();
	const assignee = document.getElementById('leadAssignee').value;
	const priority = document.getElementById('leadPriority').value;

	if (!name || !company || !value) {
		showToast('Please fill in all required fields', 'warning');
		return;
	}

	if (editingLeadId) {
		const idx = leads.findIndex(l => l.id === editingLeadId);
		if (idx >= 0) {
			leads[idx] = { ...leads[idx], name, company, value, email, phone, notes, assignee, priority };
			showToast(`Lead "${name}" updated successfully`, 'success');
			// Add to activity
			activityFeed.unshift({ type: 'edited', title: 'Lead Updated', sub: `${name} details revised`, time: 'just now', color: '#8b5cf6' });
		}
	} else {
		const newLead = {
			id: Date.now(), name, company, value, email, phone, notes, assignee, priority,
			status: 'pending', date: new Date().toISOString().split('T')[0]
		};
		leads.unshift(newLead);
		showToast(`Lead "${name}" submitted for admin approval`, 'success');
		activityFeed.unshift({ type: 'added', title: 'New Lead Added', sub: `${name} submitted for review`, time: 'just now', color: '#0d9488' });

		// Update KPI
		const kpiEl = document.querySelector('.kpi-card:nth-child(1) .kpi-value');
		if (kpiEl) kpiEl.textContent = leads.length;
		const pendingEl = document.querySelector('.kpi-card:nth-child(3) .kpi-value');
		if (pendingEl) pendingEl.textContent = leads.filter(l => l.status === 'pending').length;
	}

	closeModal('leadModal');
	renderLeads();
	renderPipeline();
	renderActivity();
	initDonut();
}

/* ═══════════
   VIEW LEAD
═══════════ */
function viewLead(id) {
	const lead = leads.find(l => l.id === id);
	if (!lead) return;

	document.getElementById('detailTitle').textContent = lead.name;
	document.getElementById('leadDetailBody').innerHTML = `
		<div class="detail-grid">
			<div class="detail-field"><div class="dl">Company</div><div class="dv">${lead.company}</div></div>
			<div class="detail-field"><div class="dl">Value</div><div class="dv" style="color:var(--primary)">${formatCurrency(lead.value)}</div></div>
			<div class="detail-field"><div class="dl">Email</div><div class="dv" style="font-size:.8rem">${lead.email || '—'}</div></div>
			<div class="detail-field"><div class="dl">Phone</div><div class="dv" style="font-size:.8rem">${lead.phone || '—'}</div></div>
			<div class="detail-field"><div class="dl">Status</div><div class="dv"><span class="status-badge ${statusClass(lead.status)}">${capitalise(lead.status)}</span></div></div>
			<div class="detail-field"><div class="dl">Priority</div><div class="dv"><span class="priority-badge pb-${lead.priority}">${capitalise(lead.priority)}</span></div></div>
			<div class="detail-field"><div class="dl">Assigned To</div><div class="dv">${lead.assignee || '—'}</div></div>
			<div class="detail-field"><div class="dl">Date Added</div><div class="dv">${lead.date}</div></div>
		</div>
		${lead.notes ? `<div style="background:var(--bg);border-radius:var(--r-xs);padding:.85rem;border:1px solid var(--border)"><div class="dl" style="font-size:.65rem;font-weight:600;text-transform:uppercase;letter-spacing:.06em;color:var(--muted);margin-bottom:.38rem">Notes</div><p style="font-size:.8rem;color:var(--text-2);line-height:1.6">${lead.notes}</p></div>` : ''}
	`;

	document.getElementById('editFromDetailBtn').onclick = () => {
		closeModal('leadDetailModal');
		editLead(id);
	};

	// Disable edit for approved/deal
	const editBtn = document.getElementById('editFromDetailBtn');
	if (lead.status === 'approved' || lead.status === 'deal') {
		editBtn.style.opacity = '0.5';
		editBtn.style.pointerEvents = 'none';
		editBtn.title = 'Cannot edit approved/deal leads';
	} else {
		editBtn.style.opacity = '1';
		editBtn.style.pointerEvents = 'auto';
	}

	openModal('leadDetailModal');
}

/* ═══════════
   DELETE LEAD
═══════════ */
function deleteLead(id) {
	const lead = leads.find(l => l.id === id);
	if (!lead) return;
	deletingLeadId = id;
	document.getElementById('deleteLeadName').textContent = `"${lead.name}"`;
	openModal('deleteModal');
}

function confirmDelete() {
	if (!deletingLeadId) return;
	const lead = leads.find(l => l.id === deletingLeadId);
	leads = leads.filter(l => l.id !== deletingLeadId);
	closeModal('deleteModal');
	showToast(`Lead "${lead?.name}" deleted`, 'info');
	activityFeed.unshift({ type: 'deleted', title: 'Lead Deleted', sub: `${lead?.name} was removed`, time: 'just now', color: '#ef4444' });
	renderLeads();
	renderPipeline();
	renderActivity();
	initDonut();
	deletingLeadId = null;
}

/* ═══════════
   SIDEBAR TOGGLE
═══════════ */
document.getElementById('sidebarToggle').addEventListener('click', function () {
	document.getElementById('sidebar').classList.toggle('collapsed');
	document.getElementById('layout').classList.toggle('sidebar-collapsed');
	const icon = document.getElementById('toggleIcon');
	icon.classList.toggle('fa-chevron-left');
	icon.classList.toggle('fa-chevron-right');
});

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
   MODAL HELPERS
═══════════ */
function openModal(id)  { document.getElementById(id).classList.remove('hidden') }
function closeModal(id) { document.getElementById(id).classList.add('hidden') }

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
	// Submit a hidden POST form so Spring Security processes the logout properly
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
   UTILITY
═══════════ */
function formatCurrency(val) {
	if (val >= 100000) return '₹' + (val / 100000).toFixed(1) + 'L';
	if (val >= 1000)   return '₹' + (val / 1000).toFixed(0) + 'K';
	return '₹' + val;
}

function capitalise(str) {
	if (!str) return '';
	return str.charAt(0).toUpperCase() + str.slice(1);
}

function statusClass(status) {
	return { approved: 'sb-approved', pending: 'sb-pending', rejected: 'sb-rejected', deal: 'sb-deal' }[status] || 'sb-pending';
}

function stageClass(stage) {
	return { proposal: 'st-proposal', negotiation: 'st-negotiation', closing: 'st-closing', discovery: 'st-discovery' }[stage] || '';
}

function setDateRange(label) {
	document.getElementById('dateRange').textContent = label;
	showToast('Date range set to: ' + label, 'success');
	closeModal('dateModal');
}

function downloadReport() { showToast('Report download started…', 'info') }

function refreshDashboard() {
	const icon = document.getElementById('refreshIcon');
	icon.style.animation = 'spinSlow 0.7s linear';
	setTimeout(() => { icon.style.animation = ''; }, 750);
	renderLeads(); renderDeals(); renderPipeline(); renderActivity(); renderTeam(); initDonut();
	showToast('Dashboard refreshed!', 'success');
}

function openDateModal() { openModal('dateModal') }

/* ═══════════
   TOAST
═══════════ */
function showToast(msg, type = 'info') {
	const icons = { success: 'fa-circle-check', info: 'fa-circle-info', warning: 'fa-triangle-exclamation', error: 'fa-circle-xmark' };
	const container = document.getElementById('toastContainer');
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

/* ═══════════
   DOM READY
═══════════ */
document.addEventListener('DOMContentLoaded', () => {

	// Render all sections
	renderLeads();
	renderDeals();
	renderPipeline();
	renderActivity();
	renderTeam();

	// Donut after a short delay for animation
	setTimeout(initDonut, 400);

	// Notification button
	document.getElementById('notifBtn').onclick = e => {
		e.stopPropagation();
		toggleDropdown(e.currentTarget, document.getElementById('notifDropdown'));
	};
	document.getElementById('msgBtn').onclick = e => {
		e.stopPropagation();
		toggleDropdown(e.currentTarget, document.getElementById('msgDropdown'));
	};
	document.getElementById('appsBtn').onclick = e => {
		e.stopPropagation();
		toggleDropdown(e.currentTarget, document.getElementById('appsDropdown'));
	};
	document.getElementById('userChip').onclick = e => {
		e.stopPropagation();
		toggleDropdown(e.currentTarget, document.getElementById('userDropdown'));
	};

	// Mark all read
	document.getElementById('markAllRead').addEventListener('click', () => {
		document.querySelectorAll('.notif-item.unread').forEach(i => i.classList.remove('unread'));
		const badge = document.getElementById('notifBadge');
		if (badge) badge.style.display = 'none';
		showToast('All notifications marked as read', 'success');
	});

	// Logout from user dropdown
	document.getElementById('logoutBtn').addEventListener('click', e => {
		e.stopPropagation();
		confirmLogout();
	});

	// Delete confirmation button
	document.getElementById('confirmDeleteBtn').addEventListener('click', confirmDelete);

	// Fullscreen
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

	// Dark mode
	document.getElementById('darkModeBtn').addEventListener('click', function () {
		document.body.classList.toggle('dark-mode');
		const icon = this.querySelector('i');
		icon.classList.toggle('fa-moon');
		icon.classList.toggle('fa-sun');
		showToast(document.body.classList.contains('dark-mode') ? 'Dark mode on' : 'Dark mode off', 'info');
	});

	// Search
	const searchInput    = document.getElementById('searchInput');
	const searchDropdown = document.getElementById('searchDropdown');
	searchInput.addEventListener('focus', () => searchDropdown.classList.add('open'));
	searchInput.addEventListener('blur',  () => setTimeout(() => searchDropdown.classList.remove('open'), 180));
	searchInput.addEventListener('input', function () {
		const q = this.value.toLowerCase();
		if (!q) { renderLeads(); return; }
		const filtered = leads.filter(l =>
			l.name.toLowerCase().includes(q) ||
			l.company.toLowerCase().includes(q) ||
			(l.assignee && l.assignee.toLowerCase().includes(q))
		);
		const tbody = document.getElementById('leadsTableBody');
		tbody.innerHTML = '';
		filtered.forEach(lead => {
			const row = document.createElement('tr');
			row.innerHTML = `
				<td><div class="cell-name">${lead.name}</div></td>
				<td>${lead.company}</td>
				<td>${lead.email}</td>
				<td style="font-weight:700">${formatCurrency(lead.value)}</td>
				<td><span class="status-badge ${statusClass(lead.status)}">${capitalise(lead.status)}</span></td>
				<td>${lead.assignee || '—'}</td>
				<td>
					<div class="action-btns">
						<button class="act-btn view" onclick="viewLead(${lead.id})"><i class="fas fa-eye"></i></button>
						<button class="act-btn edit" onclick="editLead(${lead.id})"><i class="fas fa-pen"></i></button>
						<button class="act-btn delete" onclick="deleteLead(${lead.id})"><i class="fas fa-trash"></i></button>
					</div>
				</td>
			`;
			tbody.appendChild(row);
		});
	});

	document.addEventListener('keydown', e => {
		if ((e.metaKey || e.ctrlKey) && e.key === 'k') { e.preventDefault(); searchInput.focus(); }
		if (e.key === 'Escape') {
			closeAllDropdowns();
			document.querySelectorAll('.modal-overlay').forEach(m => m.classList.add('hidden'));
		}
	});

	// Close modals on overlay click
	document.querySelectorAll('.modal-overlay').forEach(overlay => {
		overlay.addEventListener('click', function (e) {
			if (e.target === this) this.classList.add('hidden');
		});
	});

	// Close dropdowns on outside click
	document.addEventListener('click', closeAllDropdowns);

	// Welcome toast
	setTimeout(() => showToast('Welcome back, Sarah! You have 3 leads pending approval.', 'info'), 900);
});