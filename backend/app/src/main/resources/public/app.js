/* ============================================================
   CATEGORY KEYWORD MAP
   Rules are checked in order; first match wins.
   Keywords are matched case-insensitively against the full
   transaction description string. Only expenses (amount < 0)
   are categorised — income rows are left unlabelled.

   HOW TO EXTEND: add strings to any `keywords` array, or add a
   new { category, keywords } entry before the last "Other"
   catch-all (there is no explicit Other rule — anything
   unmatched falls through to the default).

   Assumptions:
   - Spanish supermarket / utility brand names included since
     this is likely a Spanish-based account (Santander source).
   - Revolut peer-to-peer transfers will usually land in "Other"
     unless the peer name matches a keyword.
   - Adjust freely — the list is intentionally broad rather than
     precise to give a useful first pass without manual tagging.
   ============================================================ */
const CATEGORY_RULES = [
  {
    category: 'Groceries',
    // Supermarkets and food shops common in Spain
    keywords: [
      'mercadona', 'lidl', 'aldi', 'carrefour', 'alcampo', 'eroski',
      'consum', 'hipercor', 'supercor', 'supermercado', 'froiz',
      'bon preu', 'caprabo', 'simply', 'el jamon', 'dia ', 'vidal',
    ],
  },
  {
    category: 'Eating Out',
    // Restaurants, cafés, bars, food-delivery platforms
    keywords: [
      'restaurante', 'restaurant', 'burger', 'mcdonalds', 'kfc', 'subway',
      'pizza', 'sushi', 'cafe ', 'cafeteria', 'coffee', 'tapas',
      'cerveceria', 'glovo', 'deliveroo', 'just eat', 'uber eats',
      'heladeria', 'chocolateria', 'wok', 'bocateria', 'bar ',
    ],
  },
  {
    category: 'Transport',
    // Fuel, tolls, public transit, ride-hailing, airlines
    keywords: [
      'renfe', 'metro ', 'bus ', 'taxi', 'uber', 'cabify', 'blablacar',
      'repsol', 'cepsa', 'bp ', 'shell', 'gasolina', 'combustible',
      'peaje', 'parking', 'autopista', 'aena', 'aeropuerto',
      'ryanair', 'vueling', 'iberia', 'easyjet', 'transporte',
    ],
  },
  {
    category: 'Housing',
    // Rent, mortgage, building community fees
    keywords: [
      'alquiler', 'hipoteca', 'comunidad de', 'administrador finca',
      'inmobiliaria', 'agencia inmobiliaria',
    ],
  },
  {
    category: 'Utilities',
    // Electricity, gas, water, internet, mobile
    keywords: [
      'endesa', 'iberdrola', 'naturgy', 'gas natural', 'holaluz',
      'telefonica', 'movistar', 'vodafone', 'orange ', 'jazztel',
      'masmovil', 'pepephone', 'simyo', 'digi ', 'yoigo',
      'canal isabel', 'aguas de', 'agua ', 'internet',
    ],
  },
  {
    category: 'Shopping',
    // Clothing, electronics, home goods, e-commerce
    keywords: [
      'amazon', 'zara', 'h&m', 'primark', 'el corte', 'fnac',
      'mediamarkt', 'pccomponentes', 'ikea', 'decathlon', 'ebay',
      'aliexpress', 'mango', 'bershka', 'pull&bear', 'stradivarius',
      'leroy merlin', 'bricomart',
    ],
  },
  {
    category: 'Health',
    // Pharmacies, clinics, gyms, health insurance
    keywords: [
      'farmacia', 'pharmacy', 'medico', 'doctor', 'dentista', 'dental',
      'hospital', 'clinica', 'optica', 'sanitas', 'adeslas', 'asisa',
      'mutua', 'seguro medico', 'gimnasio', 'gym ', 'fitness',
    ],
  },
  {
    category: 'Entertainment',
    // Streaming, cinema, gaming, events
    keywords: [
      'netflix', 'spotify', 'hbo', 'prime video', 'disney', 'apple tv',
      'cine', 'teatro', 'concierto', 'estadio', 'ticketmaster',
      'steam', 'playstation', 'xbox', 'nintendo',
    ],
  },
];

/* Colour assigned to each category — consistent across chart + legend + badges */
const CATEGORY_COLORS = {
  'Groceries':     '#10b981',
  'Eating Out':    '#f59e0b',
  'Transport':     '#3b82f6',
  'Housing':       '#8b5cf6',
  'Utilities':     '#06b6d4',
  'Shopping':      '#ec4899',
  'Health':        '#ef4444',
  'Entertainment': '#f97316',
  'Other':         '#94a3b8',
};

/* ============================================================
   FORMATTERS
   ============================================================ */

/* Currency: en-IE gives €1,234.56 regardless of OS locale */
const euro = new Intl.NumberFormat('en-IE', { style: 'currency', currency: 'EUR' });

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
   CATEGORISATION
   ============================================================ */
function categorize(description) {
  const lower = (description ?? '').toLowerCase();
  for (const { category, keywords } of CATEGORY_RULES) {
    if (keywords.some(kw => lower.includes(kw))) return category;
  }
  return 'Other';
}

/* ============================================================
   HELPERS
   ============================================================ */
const $ = id => document.getElementById(id);

function escapeHtml(str) {
  const div = document.createElement('div');
  div.textContent = str ?? '';
  return div.innerHTML;
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

/* ============================================================
   CHART
   ============================================================ */
let categoryChart = null;

function renderCategoryChart(transactions) {
  const expenses = transactions.filter(t => t.amount < 0);

  /* Tally absolute spend per category */
  const totals = {};
  for (const tx of expenses) {
    const cat = categorize(tx.description);
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

  const labels = entries.map(([k]) => k);
  const data   = entries.map(([, v]) => v);
  const colors = labels.map(l => CATEGORY_COLORS[l] ?? CATEGORY_COLORS['Other']);

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
    const color = CATEGORY_COLORS[cat] ?? CATEGORY_COLORS['Other'];
    const li    = document.createElement('li');
    li.className = 'legend-item';
    li.innerHTML = `
      <span class="legend-swatch" style="background:${color}"></span>
      <span class="legend-name">${escapeHtml(cat)}</span>
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
    const pct   = ((Math.abs(tx.amount) / maxAbs) * 100).toFixed(1);
    const cat   = categorize(tx.description);
    const color = CATEGORY_COLORS[cat] ?? CATEGORY_COLORS['Other'];

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
    const cat       = isExpense ? categorize(tx.description) : null;
    const catColor  = cat ? (CATEGORY_COLORS[cat] ?? CATEGORY_COLORS['Other']) : null;

    const catCell = cat
      ? `<span class="cat-badge">
           <span class="cat-dot" style="background:${catColor}"></span>
           ${escapeHtml(cat)}
         </span>`
      : `<span class="cat-badge" style="color:var(--income)">Income</span>`;

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
