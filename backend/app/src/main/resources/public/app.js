const euro = new Intl.NumberFormat("es-ES", {
    style: "currency",
    currency: "EUR",
});

const $ = (id) => document.getElementById(id);

// Default the picker to the current month (YYYY-MM).
const monthInput = $("month");
monthInput.value = new Date().toISOString().slice(0, 7);

async function loadMonth() {
    const ym = monthInput.value;
    if (!ym) return;

    const [summaryRes, txRes] = await Promise.all([
        fetch(`/api/months/${ym}/summary`),
        fetch(`/api/months/${ym}`),
    ]);

    const summary = await summaryRes.json();
    const transactions = await txRes.json();

    renderSummary(summary);
    renderTransactions(transactions);
}

function renderSummary(s) {
    $("income").textContent = euro.format(s.income);
    $("expenses").textContent = euro.format(s.expenses);
    $("net").textContent = euro.format(s.net);
}

function renderTransactions(txs) {
    const body = $("tx-body");
    body.innerHTML = "";
    $("tx-empty").hidden = txs.length > 0;

    for (const tx of txs) {
        const row = document.createElement("tr");
        const positive = tx.amount >= 0;
        row.innerHTML = `
      <td>${tx.operationDate}</td>
      <td>${escapeHtml(tx.description)}</td>
      <td>${tx.source}</td>
      <td class="${positive ? "amount-pos" : "amount-neg"}">${euro.format(tx.amount)}</td>
    `;
        body.appendChild(row);
    }
}

// Basic guard against odd characters in descriptions.
function escapeHtml(str) {
    const div = document.createElement("div");
    div.textContent = str ?? "";
    return div.innerHTML;
}

async function importStatement() {
    const file = $("file").files[0];
    const bank = $("bank").value;
    const status = $("import-status");

    if (!file) {
        status.textContent = "Pick a file first.";
        return;
    }

    status.textContent = "Importing…";
    try {
        // The API reads the raw request body, so send the file as-is.
        const res = await fetch(`/api/imports/${bank}`, {
            method: "POST",
            body: file,
        });
        if (!res.ok) throw new Error(await res.text());
        status.textContent = `Imported into ${bank}.`;
        await loadMonth(); // refresh the view
    } catch (err) {
        status.textContent = `Import failed: ${err.message}`;
    }
}

monthInput.addEventListener("change", loadMonth);
$("import-btn").addEventListener("click", importStatement);

loadMonth();