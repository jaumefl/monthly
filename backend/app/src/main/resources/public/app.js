/* ============================================================
   FORMATTERS
   ============================================================ */

/* Date: en-GB gives "14 Jun 2026" */
const dateFmt = new Intl.DateTimeFormat('en-GB', {
  day: 'numeric', month: 'short', year: 'numeric',
});

function formatDate(iso) {
  /* Parse as local midnight to avoid UTC-offset date shifts */
  const [y, m, d] = iso.split('-').map(Number);
  return dateFmt.format(new Date(y, m - 1, d));
}

/* ============================================================
   ASSIGNABLE CATEGORIES + OVERRIDES
   /api/categories is the source of truth for the picker (INCOME
   is filtered out server-side). Fetched once and cached.
   ============================================================ */
let assignableCategories = null;

async function ensureCategories() {
    if (assignableCategories) return;
    const res = await fetch('/api/categories');
    if (!res.ok) throw new Error(`Categories: ${res.status}`);
    assignableCategories = await res.json();   // e.g. ["GROCERIES","EATING_OUT",...]
}

function categoryOptionsHtml(selected) {
    return (assignableCategories || [])
        .map(c => {
            const sel = c === selected ? ' selected' : '';
            return `<option value="${c}"${sel}>${escapeHtml(catMeta(c).label)}</option>`;
        })
        .join('');
}

async function setCategory(fingerprint, category) {
    const res = await fetch(`/api/transactions/${fingerprint}/category`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ category }),
    });
    if (!res.ok) throw new Error(await res.text());
}

async function resetCategory(fingerprint) {
    const res = await fetch(`/api/transactions/${fingerprint}/category`, { method: 'DELETE' });
    if (!res.ok) throw new Error(await res.text());
}

/* ============================================================
   MONTH NAV
   Two modes:
     'this-month' — always loads the current calendar month
     'pick-month' — uses the two dropdowns (month + year)
   YYYY-MM strings are built manually; no <input type="month">.
   ============================================================ */
let navMode = 'this-month';

function currentYM() {
  if (navMode === 'this-month') {
    const now = new Date();
    return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
  }
  const m = $('pick-month-sel').value;
  const y = $('pick-year-sel').value;
  return m && y ? `${y}-${m}` : null;
}

function setNavMode(mode) {
  navMode = mode;
  $('btn-this-month').classList.toggle('active', mode === 'this-month');
  $('btn-pick-month').classList.toggle('active', mode === 'pick-month');
  const pickers = $('month-pickers');
  pickers.hidden = mode !== 'pick-month';
  pickers.setAttribute('aria-hidden', String(mode !== 'pick-month'));
}

function initYearSelect() {
  const sel = $('pick-year-sel');
  const now = new Date();
  const cur = now.getFullYear();
  for (let y = cur; y >= cur - 5; y--) {
    const opt = document.createElement('option');
    opt.value = y;
    opt.textContent = y;
    sel.appendChild(opt);
  }
  sel.value = cur;
  /* Pre-select current month in the month dropdown */
  $('pick-month-sel').value = String(now.getMonth() + 1).padStart(2, '0');
}

$('btn-this-month').addEventListener('click', () => {
  setNavMode('this-month');
  loadMonth();
});

$('btn-pick-month').addEventListener('click', () => {
  setNavMode('pick-month');
  if (currentYM()) loadMonth();
});

$('pick-month-sel').addEventListener('change', () => {
  if (navMode === 'pick-month' && currentYM()) loadMonth();
});

$('pick-year-sel').addEventListener('change', () => {
  if (navMode === 'pick-month' && currentYM()) loadMonth();
});

/* ============================================================
   FILE PICKER — show chosen filename next to the button
   ============================================================ */
$('file').addEventListener('change', () => {
  const f = $('file').files[0];
  $('file-name').textContent = f ? f.name : 'No file chosen';
});

/* Category edits — delegated so they survive table re-renders */
$('tx-body').addEventListener('change', async (e) => {
    const sel = e.target.closest('.cat-select');
    if (!sel) return;
    try {
        await setCategory(sel.dataset.fp, sel.value);
        await loadMonth();               // refresh chart / top expenses / marker
    } catch (err) {
        console.error('Failed to set category:', err);
    }
});

$('tx-body').addEventListener('click', async (e) => {
    const btn = e.target.closest('.cat-reset');
    if (!btn) return;
    try {
        await resetCategory(btn.dataset.fp);
        await loadMonth();
    } catch (err) {
        console.error('Failed to reset category:', err);
    }
});

/* ============================================================
   CHART
   ============================================================ */
let categoryChart = null;

function renderCategoryChart(transactions) {
  const expenses = transactions.filter(t => t.amount < 0);

  /* Tally absolute spend per category */
  const totals = {};
  for (const tx of expenses) {
      const cat = tx.category ?? 'OTHER';
      totals[cat] = (totals[cat] || 0) + Math.abs(tx.amount);
  }

  const entries = Object.entries(totals).sort((a, b) => b[1] - a[1]);
  const grandTotal = entries.reduce((s, [, v]) => s + v, 0);

  if (entries.length === 0) {
    $('chart-body').hidden = true;
    $('chart-empty').hidden = false;
    if (categoryChart) { categoryChart.destroy(); categoryChart = null; }
    $('chart-subtitle').textContent = 'No expenses for this period';
    return;
  }

  $('chart-body').hidden = false;
  $('chart-empty').hidden = true;
  $('chart-total').textContent = euro.format(grandTotal);
  $('chart-subtitle').textContent =
    `${entries.length} categor${entries.length === 1 ? 'y' : 'ies'} · ${euro.format(grandTotal)} total`;

  const labels = entries.map(([k]) => catMeta(k).label);
  const data   = entries.map(([, v]) => v);
  const colors = entries.map(([k]) => catMeta(k).color);

  if (categoryChart) {
    /* Update existing chart in-place to keep the animation smooth */
    categoryChart.data.labels                      = labels;
    categoryChart.data.datasets[0].data            = data;
    categoryChart.data.datasets[0].backgroundColor = colors;
    /* Capture grandTotal for the tooltip closure below */
    categoryChart._grandTotal = grandTotal;
    categoryChart.update('active');
  } else {
    const ctx = $('category-chart').getContext('2d');
    categoryChart = new Chart(ctx, {
      type: 'doughnut',
      data: {
        labels,
        datasets: [{
          data,
          backgroundColor: colors,
          borderWidth: 3,
          borderColor: '#ffffff',
          hoverOffset: 10,
        }],
      },
      options: {
        cutout: '70%',
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { display: false }, /* custom legend below */
          tooltip: {
            callbacks: {
              label(ctx) {
                const total = categoryChart._grandTotal;
                const pct = total > 0 ? ((ctx.raw / total) * 100).toFixed(1) : '0.0';
                return `  ${euro.format(ctx.raw)}  (${pct}%)`;
              },
            },
          },
        },
        animation: { duration: 550 },
      },
    });
    categoryChart._grandTotal = grandTotal;
  }

  renderChartLegend(entries, grandTotal);
}

function renderChartLegend(entries, total) {
  const legend = $('chart-legend');
  legend.innerHTML = '';
  for (const [cat, amount] of entries) {
    const pct   = total > 0 ? ((amount / total) * 100).toFixed(1) : '0.0';
    const { label, color } = catMeta(cat);
    const li    = document.createElement('li');
    li.className = 'legend-item';
    li.innerHTML = `
      <span class="legend-swatch" style="background:${color}"></span>
      <span class="legend-name">${escapeHtml(label)}</span>
      <span class="legend-pct">${pct}%</span>
      <span class="legend-amount">${euro.format(amount)}</span>
    `;
    legend.appendChild(li);
  }
}

/* ============================================================
   SUMMARY
   ============================================================ */
function renderSummary(s) {
  $('income').textContent   = euro.format(s.income);
  $('expenses').textContent = euro.format(s.expenses);
  $('net').textContent      = euro.format(s.net);
}

/* ============================================================
   TOP EXPENSES
   ============================================================ */
function renderTopExpenses(txs) {
  const expenses = txs
    .filter(t => t.amount < 0)
    .sort((a, b) => a.amount - b.amount)
    .slice(0, 5);

  const list = $('top-list');
  list.innerHTML = '';
  $('top-empty').hidden = expenses.length > 0;
  if (!expenses.length) return;

  const maxAbs = Math.abs(expenses[0].amount);

  for (const tx of expenses) {
      const pct = ((Math.abs(tx.amount) / maxAbs) * 100).toFixed(1);
      const { label: cat, color } = catMeta(tx.category);

    const li = document.createElement('li');
    li.className = 'top-item';
    li.innerHTML = `
      <div class="top-item__row">
        <span class="top-item__desc">${escapeHtml(tx.description)}</span>
        <span class="top-item__amount">${euro.format(tx.amount)}</span>
      </div>
      <div class="bar-track">
        <div class="bar-fill" style="width:${pct}%;background:${color}"></div>
      </div>
      <div class="top-item__meta">
        <span class="cat-dot" style="background:${color}"></span>
        <span>${escapeHtml(cat)}</span>
        <span>·</span>
        <span>${formatDate(tx.operationDate)}</span>
        <span>·</span>
        <span>${escapeHtml(tx.source)}</span>
      </div>
    `;
    list.appendChild(li);
  }
}

/* ============================================================
   TRANSACTIONS TABLE
   ============================================================ */
function renderTransactions(txs) {
    const body = $('tx-body');
    body.innerHTML = '';
    $('tx-empty').hidden = txs.length > 0;
    $('tx-count').textContent = txs.length;

    for (const tx of txs) {
        const isExpense = tx.amount < 0;
        const { label, color } = catMeta(tx.category);

        let catCell;
        if (isExpense) {
            const manualClass = tx.manual ? ' cat-select--manual' : '';
            const resetBtn = tx.manual
                ? `<button class="cat-reset" data-fp="${escapeHtml(tx.fingerprint)}"
                   title="Reset to automatic" aria-label="Reset to automatic">↺</button>`
                : '';
            catCell = `
        <div class="cat-edit">
          <span class="cat-dot" style="background:${color}"></span>
          <select class="cat-select${manualClass}" data-fp="${escapeHtml(tx.fingerprint)}"
                  aria-label="Category">
            ${categoryOptionsHtml(tx.category)}
          </select>
          ${resetBtn}
        </div>`;
        } else {
            catCell =
                `<span class="cat-badge">
           <span class="cat-dot" style="background:${color}"></span>
           ${escapeHtml(label)}
         </span>`;
        }

        const row = document.createElement('tr');
        row.innerHTML = `
      <td style="white-space:nowrap">${formatDate(tx.operationDate)}</td>
      <td>${escapeHtml(tx.description)}</td>
      <td>${catCell}</td>
      <td><span class="source-tag">${escapeHtml(tx.source)}</span></td>
      <td class="${isExpense ? 'amount-neg' : 'amount-pos'}">${euro.format(tx.amount)}</td>
    `;
        body.appendChild(row);
    }
}

/* ============================================================
   DATA LOADING
   ============================================================ */
async function loadMonth() {
  const ym = currentYM();
  if (!ym) return;

  try {
    await ensureCategories();
    const [summaryRes, txRes] = await Promise.all([
      fetch(`/api/months/${ym}/summary`),
      fetch(`/api/months/${ym}`),
    ]);
    if (!summaryRes.ok) throw new Error(`Summary: ${summaryRes.status}`);
    if (!txRes.ok)      throw new Error(`Transactions: ${txRes.status}`);

    const [summary, transactions] = await Promise.all([
      summaryRes.json(),
      txRes.json(),
    ]);

    renderSummary(summary);
    renderCategoryChart(transactions);
    renderTopExpenses(transactions);
    renderTransactions(transactions);
  } catch (err) {
    console.error('Failed to load month data:', err);
  }
}

/* ============================================================
   IMPORT
   POST the raw File object as the request body (no multipart).
   ============================================================ */
async function importStatement() {
  const file   = $('file').files[0];
  const bank   = $('bank').value;
  const status = $('import-status');

  if (!file) { status.textContent = 'Pick a file first.'; return; }

  status.textContent = 'Importing…';
  try {
    const res = await fetch(`/api/imports/${bank}`, { method: 'POST', body: file });
    if (!res.ok) throw new Error(await res.text());
    status.textContent = `Imported successfully into ${bank}.`;
    /* Reset file picker label */
    $('file').value    = '';
    $('file-name').textContent = 'No file chosen';
    await loadMonth();
  } catch (err) {
    status.textContent = `Import failed: ${err.message}`;
  }
}

$('import-btn').addEventListener('click', importStatement);

/* ============================================================
   INIT
   ============================================================ */
initYearSelect();
loadMonth();
