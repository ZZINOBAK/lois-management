const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const source = fs.readFileSync('src/main/resources/static/js/reservation-pickup.js', 'utf8');
function page({ status = 'RESERVED', response = 204, token = async () => 'token', fetcher } = {}) {
    let click;
    const calls = [];
    const feedback = { hidden: true, dataset: {} };
    let timer;
    const label = { textContent: 'original' };
    const classes = () => ({ toggle() {} });
    const button = { disabled: false, dataset: { pickupUrl: '/api/reservations/pickup/7', cancelUrl: '/api/reservations/pickup-cancel/7' }, classList: classes(), closest: () => row, querySelector: () => label };
    const row = { dataset: { reservationId: '7', status }, querySelector: s => s === '[data-reservation-pickup]' ? button : s === '[data-reservation-summary]' ? { classList: classes() } : null };
    let renderedRow = row;
    vm.runInNewContext(source, {
        document: { addEventListener: (_, fn) => { click = fn; }, querySelector: () => feedback, getElementById: () => renderedRow },
        setTimeout: fn => { timer = fn; return 1; }, clearTimeout: () => { timer = null; },
        window: { confirm: () => true }, ensureToken: token, clearToken() {},
        fetch: async (...args) => { calls.push(args); return fetcher ? fetcher() : { status: response, headers: { get: () => 'completed' } }; }
    });
    return { click: () => click({ target: { closest: () => button } }), replace: newRow => { renderedRow = newRow; }, calls, row, button, feedback, label, expire: () => timer?.() };
}
test('RESERVED requests pickup and updates only after 204', async () => {
    const p = page(); await p.click();
    assert.equal(p.calls[0][0], '/api/reservations/pickup/7');
    assert.equal(p.calls[0][1].headers.Authorization, 'Bearer token');
    assert.equal(p.row.dataset.status, 'PICKED_UP');
    assert.equal(p.label.textContent, '픽업완료');
});
test('PICKED_UP requests cancellation', async () => {
    const p = page({ status: 'PICKED_UP' }); await p.click();
    assert.equal(p.calls[0][0], '/api/reservations/pickup-cancel/7');
    assert.equal(p.row.dataset.status, 'RESERVED');
});
for (const response of [200, 400, 401, 403, 404, 409, 500]) test(`${response} preserves row and never retries`, async () => {
    const p = page({ response }); await p.click();
    assert.equal(p.row.dataset.status, 'RESERVED'); assert.equal(p.calls.length, 1); assert.equal(p.button.disabled, false);
});
test('locks during token preparation and prevents duplicate requests', async () => {
    let release; const token = new Promise(resolve => { release = resolve; });
    const p = page({ token: () => token }); const first = p.click();
    assert.equal(p.button.disabled, true); await p.click(); assert.equal(p.calls.length, 0);
    release('new-token'); await first; assert.equal(p.calls.length, 1); assert.equal(p.button.disabled, false);
});
test('token issuance failure does not send mutation', async () => {
    const p = page({ token: async () => { throw Error(); } }); await p.click(); assert.equal(p.calls.length, 0);
});
test('network error preserves row and reports uncertain outcome', async () => {
    const p = page({ fetcher: () => { throw Error(); } }); await p.click();
    assert.equal(p.row.dataset.status, 'RESERVED'); assert.match(p.feedback.textContent, /새로고침/); assert.equal(p.calls.length, 1);
});

test('response updates the row replaced by search while request was pending', async () => {
    let release; const response = new Promise(resolve => { release = resolve; });
    const p = page({ fetcher: () => response });
    const first = p.click(); await Promise.resolve();
    const replacement = page(); p.replace(replacement.row);
    release({ status: 204, headers: { get: () => 'completed' } }); await first;
    assert.equal(replacement.row.dataset.status, 'PICKED_UP');
    assert.equal(replacement.label.textContent, '픽업완료');
});

test('success toast disappears after timeout', async () => {
    const p = page(); await p.click();
    assert.equal(p.feedback.hidden, false); assert.equal(p.feedback.dataset.kind, 'success');
    p.expire(); assert.equal(p.feedback.hidden, true);
});
test('failure toast stays visible for checking the result', async () => {
    const p = page({ response: 500 }); await p.click(); p.expire();
    assert.equal(p.feedback.hidden, false); assert.equal(p.feedback.dataset.kind, 'error');
});
