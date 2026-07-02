/* Comparison page: per category, two VERTICAL bars (baseline left/faint,
   current right/strong) with the euro amount inside each bar; to the right,
   the euro delta (red + ▼ if you spent MORE, green + ▲ if LESS) and the
   percentage change vs baseline. Bars animate rising from the bottom.
   Depends on common.js for euro, catMeta, $, escapeHtml. */

const MONTHS_BACK = 24;
function ymNow() { const n = new Date(); return `${n.getFullYear()}-${String(n.getMonth() + 1).padStart(2, '0')}`; }
function prevYM(ym) { let [y, m] = ym.split('-').map(Number); if (--m === 0) { m = 12; y--; } return `${y}-${String(m).padStart(2, '0')}`; }
function ymLabel(ym) { const [y, m] = ym.split('-').map(Number); return new Date(y, m - 1, 1).toLocaleDateString('en-GB', { month: 'long', year: 'numeric' }); }
function ymOptions() { const out = []; let [y, m] = ymNow().split('-').map(Number); for (let i = 0; i < MONTHS_BACK; i++) { out.push(`${y}-${String(m).padStart(2, '0')}`); if (--m === 0) { m = 12; y--; } } return out; }
function fillSelect(sel, selected) { sel.innerHTML = ymOptions().map(ym => `<option value="${ym}"${ym === selected ? ' selected' : ''}>${ymLabel(ym)}</option>`).join(''); }

async function loadComparison() {
  const month = $('cmp-month').value, baseline = $('cmp-baseline').value;
  try {
    const res = await fetch(`/api/comparison?month=${month}&baseline=${baseline}`);
    if (!res.ok) throw new Error(await res.text());
    render(await res.json());
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
      <div class="cmp-chart">
        <div class="cmp-col">
          <div class="cmp-vbar cmp-vbar--baseline" style="--h:${hBase}%;background:${color}">
            <span class="cmp-vbar__val">${euro.format(r.baselineAmount)}</span>
          </div>
          <div class="cmp-vbar cmp-vbar--current" style="--h:${hCur}%;background:${color}">
            <span class="cmp-vbar__val">${euro.format(r.amount)}</span>
          </div>
        </div>
        <div class="cmp-cat"><span class="cat-dot" style="background:${color}"></span>
          <span class="cmp-cat__name">${escapeHtml(label)}</span></div>
      </div>
      <div class="cmp-delta ${deltaCls}">
        <span class="cmp-delta__eur">${deltaTxt}</span>
        <span class="cmp-delta__pct">${pctTxt}</span>
      </div>`;
    list.appendChild(li);
  }

  // rise-up animation: bars start at height 0 (CSS) and transition to their --h
  requestAnimationFrame(() => {
    for (const bar of list.querySelectorAll('.cmp-vbar'))
      bar.style.height = getComputedStyle(bar).getPropertyValue('--h');
  });
}

const initialMonth = ymNow();
fillSelect($('cmp-month'), initialMonth);
fillSelect($('cmp-baseline'), prevYM(initialMonth));
$('cmp-month').addEventListener('change', loadComparison);
$('cmp-baseline').addEventListener('change', loadComparison);
loadComparison();
