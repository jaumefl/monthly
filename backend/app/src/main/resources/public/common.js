/* Shared by app.js (dashboard) and compare.js (comparison). Loaded first. */
const CATEGORY_META = {
    GROCERIES:    { label: 'Groceries',    color: '#10b981' },
    EATING_OUT:   { label: 'Eating Out',   color: '#f59e0b' },
    TRANSPORT:    { label: 'Transport',    color: '#3b82f6' },
    HOUSING:      { label: 'Housing',      color: '#8b5cf6' },
    UTILITIES:    { label: 'Utilities',    color: '#06b6d4' },
    SHOPPING:     { label: 'Shopping',     color: '#ec4899' },
    HEALTH:       { label: 'Health',       color: '#ef4444' },
    INVESTMENT:   { label: 'Investment',   color: '#6366f1' },
    INCOME:       { label: 'Income',       color: '#22c55e' },
    SUBSCRIPTION: { label: 'Subscription', color: '#fffb26' },
    OTHER:        { label: 'Other',        color: '#94a3b8' },
};
const euro = new Intl.NumberFormat('en-IE', { style: 'currency', currency: 'EUR' });
function catMeta(category) { return CATEGORY_META[category] ?? CATEGORY_META.OTHER; }
const $ = id => document.getElementById(id);
function escapeHtml(str) { const div = document.createElement('div'); div.textContent = str ?? ''; return div.innerHTML; }
