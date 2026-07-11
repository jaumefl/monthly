/* Comparison page: one row per category, two clusters side by side.
   LEFT (spending): the category name as a small title above two VERTICAL bars
   (baseline left/faint, current right/strong, euro amount inside each; bars
   animate rising from the bottom), with the delta vs baseline immediately
   right of the bars (red ▼ if you spent MORE, green ▲ if LESS) and the %
   change beneath it.
   RIGHT (budget): this month's budget for the category — a horizontal bar
   filled to percentUsed (category colour; danger colour at 100% when over),
   the limit amount, a pencil that edits the limit inline (PUT
   /api/budgets/{CATEGORY}; empty input clears via DELETE), and a helper line
   with what's left / how far over.
   Depends on common.js for euro, catMeta, $, escapeHtml. */

const MONTHS_BACK = 24;
function ymNow() { const n = new Date(); return `${n.getFullYear()}-${String(n.getMonth() + 1).padStart(2, '0')}`; }
function prevYM(ym) { let [y, m] = ym.split('-').map(Number); if (--m === 0) { m = 12; y--; } return `${y}-${String(m).padStart(2, '0')}`; }
function ymLabel(ym) { const [y, m] = ym.split('-').map(Number); return new Date(y, m - 1, 1).toLocaleDateString('en-GB', { month: 'long', year: 'numeric' }); }
function ymOptions() { const out = []; let [y, m] = ymNow().split('-').map(Number); for (let i = 0; i < MONTHS_BACK; i++) { out.push(`${y}-${String(m).padStart(2, '0')}`); if (--m === 0) { m = 12; y--; } } return out; }
function fillSelect(sel, selected) { sel.innerHTML = ymOptions().map(ym => `<option value="${ym}"${ym === selected ? ' selected' : ''}>${ymLabel(ym)}</option>`).join(''); }

let budgetByCat = new Map();   // category -> /api/budgets/report line for the "this month" select

async function loadComparison() {
  const month = $('cmp-month').value, baseline = $('cmp-baseline').value;
  try {
    const [cmpRes, repRes] = await Promise.all([
      fetch(`/api/comparison?month=${month}&baseline=${baseline}`),
      fetch(`/api/budgets/report?month=${month}`),
    ]);
    if (!cmpRes.ok) throw new Error(await cmpRes.text());
    if (!repRes.ok) throw new Error(await repRes.text());
    const cmp = await cmpRes.json();
    const report = await repRes.json();
    budgetByCat = new Map(report.lines.map(l => [l.category, l]));
    render(cmp);
  } catch (err) { console.error('Failed to load comparison:', err); }
}

function render(cmp) {
  $('cmp-title').textContent   = `${ymLabel(cmp.month)} vs ${ymLabel(cmp.baseline)}`;
  $('cmp-total-a').textContent = euro.format(cmp.monthTotal);
  $('cmp-total-b').textContent = euro.format(cmp.baselineTotal);

  const rows = cmp.rows;
  const list = $('cmp-rows');
  list.innerHTML = '';
  $('cmp-empty').hidden = rows.length > 0;

  for (const r of rows) {
    const { label, color } = catMeta(r.category);
    const rowMax = Math.max(r.amount, r.baselineAmount);
    const hCur  = rowMax > 0 ? (r.amount / rowMax) * 100 : 0;
    const hBase = rowMax > 0 ? (r.baselineAmount / rowMax) * 100 : 0;

    const d = r.delta;                       // >0 = spent more than baseline
    const deltaCls = d > 0 ? 'delta--up' : d < 0 ? 'delta--down' : 'delta--flat';
    const deltaTxt = d === 0 ? '—' : `${euro.format(Math.abs(d))} ${d > 0 ? '▼' : '▲'}`;

    let pctTxt;                              // percentage change vs baseline
    if (r.baselineAmount === 0) pctTxt = r.amount === 0 ? '—' : 'new';
    else { const pct = (d / r.baselineAmount) * 100; pctTxt = `${pct > 0 ? '+' : ''}${pct.toFixed(1)}%`; }

    const li = document.createElement('li');
    li.className = 'cmp-row';
    li.innerHTML = `
      <div class="cmp-spend">
        <span class="cmp-spend__title">${escapeHtml(label)}</span>
        <div class="cmp-spend__body">
          <div class="cmp-col">
            <div class="cmp-vbar cmp-vbar--baseline" style="--h:${hBase}%;background:${color}">
              <span class="cmp-vbar__val">${euro.format(r.baselineAmount)}</span>
            </div>
            <div class="cmp-vbar cmp-vbar--current" style="--h:${hCur}%;background:${color}">
              <span class="cmp-vbar__val">${euro.format(r.amount)}</span>
            </div>
          </div>
          <div class="cmp-delta ${deltaCls}">
            <span class="cmp-delta__eur">${deltaTxt}</span>
            <span class="cmp-delta__pct">${pctTxt}</span>
          </div>
        </div>
      </div>
      <div class="cmp-budget-wrap"><div class="cmp-budget"></div></div>`;
    const budgetEl = li.querySelector('.cmp-budget');
    budgetEl.dataset.category = r.category;
    renderBudget(budgetEl);
    list.appendChild(li);
  }

  // rise-up animation: bars start at height 0 (CSS) and transition to their --h
  requestAnimationFrame(() => {
    for (const bar of list.querySelectorAll('.cmp-vbar'))
      bar.style.height = getComputedStyle(bar).getPropertyValue('--h');
  });
}

/* Budget cluster for one category, driven by budgetByCat. */
function renderBudget(el) {
  const category = el.dataset.category;
  const line = budgetByCat.get(category);
  const { label, color } = catMeta(category);
  const pencil = `<button type="button" class="cmp-budget__edit" title="Edit budget"
      aria-label="Edit budget for ${escapeHtml(label)}">✎</button>`;

  let row, hint = '';
  if (line) {
    const pct = Math.min(Number(line.percentUsed), 100);
    const fill = line.overBudget
      ? `<div class="bar-fill cmp-budget__fill cmp-budget__fill--over" style="--w:100%"></div>`
      : `<div class="bar-fill cmp-budget__fill" style="--w:${pct}%;background:${color}"></div>`;
    row = `<div class="bar-track cmp-budget__track">${fill}</div>
      <span class="cmp-budget__amt">${euro.format(line.limit)}</span>${pencil}`;
    hint = line.overBudget
      ? `<span class="cmp-budget__hint cmp-budget__hint--over">${euro.format(Math.abs(line.remaining))} over budget</span>`
      : `<span class="cmp-budget__hint">${euro.format(line.remaining)} left to reach budget</span>`;
  } else {
    row = `<span class="cmp-budget__none">No budget set</span>${pencil}`;
  }

  el.innerHTML = `
    <span class="cmp-budget__label">Budget</span>
    <div class="cmp-budget__row">${row}</div>
    ${hint}`;

  el.querySelector('.cmp-budget__edit').addEventListener('click', () => openEditor(el, line));

  // same rise pattern as the vertical bars, horizontally: width 0 (CSS) -> --w
  const fillEl = el.querySelector('.cmp-budget__fill');
  if (fillEl) requestAnimationFrame(() => {
    fillEl.style.width = getComputedStyle(fillEl).getPropertyValue('--w');
  });
}

/* Inline editor: number input; Enter/Save PUTs, empty clears (DELETE), Esc cancels. */
function openEditor(el, line) {
  const category = el.dataset.category;
  const { label } = catMeta(category);
  el.innerHTML = `
    <span class="cmp-budget__label">Budget</span>
    <form class="cmp-budget__form">
      <input class="cmp-budget__input" type="number" min="0.01" step="0.01" placeholder="No budget"
        value="${line ? line.limit : ''}" aria-label="Monthly budget for ${escapeHtml(label)} in euro" />
      <button type="submit" class="cmp-budget__btn cmp-budget__btn--save">Save</button>
      <button type="button" class="cmp-budget__btn" data-cancel aria-label="Cancel">✕</button>
    </form>
    <span class="cmp-budget__hint" aria-live="polite">${line ? 'Leave empty to remove the budget' : 'Set a monthly limit in euro'}</span>`;

  const input = el.querySelector('input');
  const hint  = el.querySelector('.cmp-budget__hint');
  const close = () => { renderBudget(el); el.querySelector('.cmp-budget__edit').focus(); };
  el.querySelector('[data-cancel]').addEventListener('click', close);
  input.addEventListener('keydown', e => { if (e.key === 'Escape') close(); });

  el.querySelector('form').addEventListener('submit', async e => {
    e.preventDefault();                    // min="0.01" already rejected 0/negatives natively
    const raw = input.value.trim();
    const url = `/api/budgets/${encodeURIComponent(category)}`;
    try {
      const res = raw === ''
        ? await fetch(url, { method: 'DELETE' })
        : await fetch(url, { method: 'PUT', headers: { 'Content-Type': 'application/json' },
                             body: JSON.stringify({ amount: Number(raw) }) });
      if (!res.ok) throw new Error(await res.text());
      await refreshBudgets();
      el.querySelector('.cmp-budget__edit').focus();
    } catch (err) {
      console.error('Failed to save budget:', err);
      hint.textContent = 'Could not save budget';
      hint.classList.add('cmp-budget__hint--over');
    }
  });
  input.focus();
  input.select();
}

/* Re-fetch the report for the selected month and repaint every budget cluster. */
async function refreshBudgets() {
  const res = await fetch(`/api/budgets/report?month=${$('cmp-month').value}`);
  if (!res.ok) throw new Error(await res.text());
  const report = await res.json();
  budgetByCat = new Map(report.lines.map(l => [l.category, l]));
  for (const cell of document.querySelectorAll('#cmp-rows .cmp-budget')) renderBudget(cell);
}

const initialMonth = ymNow();
fillSelect($('cmp-month'), initialMonth);
fillSelect($('cmp-baseline'), prevYM(initialMonth));
$('cmp-month').addEventListener('change', loadComparison);
$('cmp-baseline').addEventListener('change', loadComparison);
loadComparison();
