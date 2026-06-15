(function () {
	'use strict';

	var cfg = window.dashboardAnalytics || {};
	var data = cfg.initialData || {};
	var charts = {};
	var pollMs = cfg.pollMs || 10000;

	if (!window.Chart) return;

	Chart.defaults.font.family = "'Poppins', sans-serif";
	Chart.defaults.font.size = 12;
	Chart.defaults.color = '#6b7280';
	Chart.defaults.plugins.legend.labels.usePointStyle = true;
	Chart.defaults.plugins.legend.labels.pointStyleWidth = 10;
	Chart.defaults.plugins.legend.labels.padding = 18;
	Chart.defaults.plugins.tooltip.padding = 10;
	Chart.defaults.plugins.tooltip.cornerRadius = 10;
	Chart.defaults.plugins.tooltip.titleFont = { weight: '700' };

	var PRIMARY = '#17455e';

	function getNumber(value) {
		var number = Number(value);
		return Number.isFinite(number) ? number : 0;
	}

	function ensureArray(value) {
		return Array.isArray(value) ? value : [];
	}

	function pctLabel(ctx) {
		var total = ctx.dataset.data.reduce(function (sum, value) {
			return sum + getNumber(value);
		}, 0);
		var pct = total > 0 ? Math.round(ctx.parsed / total * 100) : 0;
		return ' ' + ctx.label + ': ' + ctx.parsed + ' (' + pct + '%)';
	}

	function setText(id, value) {
		var el = document.getElementById(id);
		if (el) el.textContent = value;
	}

	function updateChips(next) {
		setText('chip-done', getNumber(next.statusDone) + ' Done');
		setText('chip-inprog', getNumber(next.statusInProgress) + ' In Progress');
		setText('chip-pending', getNumber(next.statusPending) + ' Pending');
		setText('chip-review', getNumber(next.statusReview) + ' Review');
		setText('chip-high', getNumber(next.priorityHigh) + ' High');
		setText('chip-medium', getNumber(next.priorityMedium) + ' Medium');
		setText('chip-low', getNumber(next.priorityLow) + ' Low');
		setText('chip-active-team', getNumber(next.activeTeam) + ' Active');
		setText('chip-inactive-team', getNumber(next.inactiveTeam) + ' Inactive');
		setText('chip-approved', getNumber(next.verified) + ' Approved');
		setText('chip-rejected', getNumber(next.rejected) + ' Rejected');
		setText('chip-waiting', getNumber(next.waiting) + ' Waiting');
		setText('chip-unverified', getNumber(next.unverified) + ' Open');
	}

	function updateStats(next) {
		var bindings = cfg.statBindings || {};
		Object.keys(bindings).forEach(function (key) {
			var el = document.querySelector('[data-analytics-stat="' + key + '"]');
			if (el && Object.prototype.hasOwnProperty.call(next, bindings[key])) {
				el.textContent = next[bindings[key]];
			}
		});
	}

	function replaceDataset(chart, labels, values) {
		if (!chart) return;
		chart.data.labels = labels;
		chart.data.datasets[0].data = values;
		chart.update('none');
	}

	function createChart(id, options) {
		var el = document.getElementById(id);
		if (!el) return null;
		return new Chart(el, options);
	}

	function render(next) {
		data = Object.assign({}, data, next || {});
		updateChips(data);
		updateStats(data);

		replaceDataset(charts.status,
			['Done', 'In Progress', 'Pending', 'Waiting Review'],
			[getNumber(data.statusDone), getNumber(data.statusInProgress), getNumber(data.statusPending), getNumber(data.statusReview)]);

		replaceDataset(charts.priority,
			['High', 'Medium', 'Low'],
			[getNumber(data.priorityHigh), getNumber(data.priorityMedium), getNumber(data.priorityLow)]);

		var memberLabels = ensureArray(data.memberLabels);
		var memberCounts = ensureArray(data.memberTaskCounts);
		replaceDataset(charts.member,
			memberLabels.length ? memberLabels : ['No Data'],
			memberCounts.length ? memberCounts : [0]);

		replaceDataset(charts.team,
			cfg.peopleChartLabels || ['Active', 'Inactive'],
			[getNumber(data.activeTeam), getNumber(data.inactiveTeam)]);

		replaceDataset(charts.verification,
			['Approved', 'Rejected', 'Waiting Review', 'Open'],
			[getNumber(data.verified), getNumber(data.rejected), getNumber(data.waiting), getNumber(data.unverified)]);
	}

	charts.status = createChart('taskStatusChart', {
		type: 'doughnut',
		data: {
			labels: ['Done', 'In Progress', 'Pending', 'Waiting Review'],
			datasets: [{
				data: [0, 0, 0, 0],
				backgroundColor: ['rgba(27,122,70,.85)', 'rgba(37,99,235,.85)', 'rgba(192,86,33,.85)', 'rgba(107,70,193,.85)'],
				borderColor: '#fff',
				borderWidth: 3,
				hoverOffset: 10
			}]
		},
		options: {
			responsive: true,
			maintainAspectRatio: false,
			cutout: '60%',
			plugins: { legend: { position: 'bottom' }, tooltip: { callbacks: { label: pctLabel } } }
		}
	});

	charts.priority = createChart('taskPriorityChart', {
		type: 'pie',
		data: {
			labels: ['High', 'Medium', 'Low'],
			datasets: [{
				data: [0, 0, 0],
				backgroundColor: ['rgba(209,26,42,.85)', 'rgba(192,86,33,.80)', 'rgba(27,122,70,.80)'],
				borderColor: '#fff',
				borderWidth: 3,
				hoverOffset: 10
			}]
		},
		options: {
			responsive: true,
			maintainAspectRatio: false,
			plugins: { legend: { position: 'bottom' }, tooltip: { callbacks: { label: pctLabel } } }
		}
	});

	charts.member = createChart('memberTaskChart', {
		type: 'bar',
		data: {
			labels: ['No Data'],
			datasets: [{
				label: cfg.memberDatasetLabel || 'Tasks',
				data: [0],
				backgroundColor: 'rgba(23,69,94,.18)',
				borderColor: PRIMARY,
				borderWidth: 2,
				borderRadius: 8,
				borderSkipped: false
			}]
		},
		options: {
			responsive: true,
			maintainAspectRatio: false,
			plugins: { legend: { display: false } },
			scales: {
				y: { beginAtZero: true, ticks: { stepSize: 1, precision: 0 }, grid: { color: '#f0f6f9' } },
				x: { grid: { display: false } }
			}
		}
	});

	charts.team = createChart('teamStatusChart', {
		type: 'pie',
		data: {
			labels: cfg.peopleChartLabels || ['Active', 'Inactive'],
			datasets: [{
				data: [0, 0],
				backgroundColor: ['rgba(27,122,70,.85)', 'rgba(209,26,42,.80)'],
				borderColor: '#fff',
				borderWidth: 3,
				hoverOffset: 10
			}]
		},
		options: {
			responsive: true,
			maintainAspectRatio: false,
			plugins: { legend: { position: 'bottom' }, tooltip: { callbacks: { label: pctLabel } } }
		}
	});

	charts.verification = createChart('verificationChart', {
		type: 'doughnut',
		data: {
			labels: ['Approved', 'Rejected', 'Waiting Review', 'Open'],
			datasets: [{
				data: [0, 0, 0, 0],
				backgroundColor: ['rgba(27,122,70,.85)', 'rgba(209,26,42,.80)', 'rgba(192,86,33,.80)', 'rgba(37,99,235,.75)'],
				borderColor: '#fff',
				borderWidth: 3,
				hoverOffset: 10
			}]
		},
		options: {
			responsive: true,
			maintainAspectRatio: false,
			cutout: '60%',
			plugins: { legend: { position: 'bottom' }, tooltip: { callbacks: { label: pctLabel } } }
		}
	});

	render(data);

	function fetchLatest() {
		if (!cfg.endpoint) return;
		fetch(cfg.endpoint, { headers: { Accept: 'application/json' }, cache: 'no-store' })
			.then(function (response) {
				if (!response.ok) throw new Error('Analytics refresh failed');
				return response.json();
			})
			.then(render)
			.catch(function () {});
	}

	fetchLatest();
	window.setInterval(fetchLatest, pollMs);
})();
