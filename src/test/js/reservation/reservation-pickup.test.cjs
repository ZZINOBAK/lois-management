const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const source = fs.readFileSync('src/main/resources/static/js/cake/reservation/reservation-pickup.js', 'utf8');
// 테스트 안의 현재 시각을 고정한다. (실행 시각·자정 경계와 무관하게 동작 확인)
function fixedDate(now) {
    return class extends Date {
        constructor(...args) { if (args.length) super(...args); else super(now.getTime()); }
        static now() { return now.getTime(); }
    };
}
function page({ status = 'RESERVED', response = 204, token = async () => 'token', fetcher, reason = 'MISTAKE', autoSelect = true, cakeSize = '2', resDate, resTime, ready = false, now } = {}) {
    let click;
    let settle;
    const card = { dataset: { readyReservationIds: "" } };
    const calls = [];
    const refreshes = [];
    const feedback = { hidden: true, dataset: {} };
    let timer;
    let interval;
    const label = { textContent: 'original' };
    const classes = (initial = []) => {
        const names = new Set(initial);
        return { toggle(name, force) { const on = force === undefined ? !names.has(name) : force; if (on) names.add(name); else names.delete(name); return on; }, contains: name => names.has(name) };
    };
    const summary = { classList: classes() };
    const button = { disabled: false, dataset: { pickupUrl: '/api/reservations/pickup/7', cancelUrl: '/api/reservations/pickup-cancel/7' }, classList: classes(ready ? ['production-ready-btn'] : []), closest: () => row, querySelector: () => label };
    const row = { dataset: { reservationId: '7', status, cakeSize, ...(resDate ? { resDate } : {}), ...(resTime ? { resTime } : {}) }, querySelector: s => s === '[data-pickup-label]' ? label : s === '[data-reservation-pickup]' ? button : s === '[data-reservation-summary]' ? summary : null };
    const listeners = new Map();
    const options = ['MISTAKE', 'RETURNED'].map(reason => ({ dataset: { cancelReason: reason }, disabled: false }));
    const dismiss = { disabled: false };
    const dialog = {
        open: false,
        querySelectorAll: () => [...options, dismiss],
        addEventListener: (name, fn) => { listeners.set(name, fn); },
        removeEventListener: (name, fn) => { if (listeners.get(name) === fn) listeners.delete(name); },
        showModal() { this.open = true; if (autoSelect) queueMicrotask(() => choose(reason)); },
        close() { this.open = false; listeners.get('close')?.(); }
    };
    function choose(reason) {
        const option = options.find(button => button.dataset.cancelReason === reason);
        listeners.get('click')?.({ target: { closest: selector => selector === '[data-cancel-reason]' ? option : reason === null ? dismiss : null } });
    }
    let renderedRow = row;
    vm.runInNewContext(source, {
        document: { addEventListener: (name, fn) => { if (name === 'click') click = fn; if (name === 'htmx:afterSettle') settle = fn; }, querySelector: selector => selector === '[data-ready-reservation-ids]' ? card : selector === '[data-pickup-cancel-dialog]' ? dialog : feedback, querySelectorAll: () => [renderedRow], getElementById: () => renderedRow },
        URLSearchParams,
        ...(now ? { Date: fixedDate(now) } : {}),
        setTimeout: fn => { timer = fn; return 1; }, clearTimeout: () => { timer = null; }, setInterval: fn => { interval = fn; return 2; },
        window: { confirm: () => true, htmx: { trigger: (target, event) => refreshes.push(event) } }, ensureToken: token, clearToken() {},
        fetch: async (...args) => { calls.push(args); return fetcher ? fetcher() : { status: response, headers: { get: () => 'completed' } }; }
    });
    return { click: () => click({ target: { closest: () => button } }), replace: newRow => { renderedRow = newRow; }, settle: ids => { card.dataset.readyReservationIds = ids; settle({ detail: { elt: { matches: () => true } } }); }, calls, refreshes, row, button, summary, feedback, label, dialog, options, choose, expire: () => timer?.(), tick: () => interval?.() };
}
test('RESERVED requests pickup and updates only after 204', async () => {
    const p = page(); await p.click();
    assert.equal(p.calls[0][0], '/api/reservations/pickup/7');
    assert.equal(p.calls[0][1].headers.Authorization, 'Bearer token');
    assert.equal(p.row.dataset.status, 'PICKED_UP');
    assert.equal(p.label.textContent, '픽업 완료');
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
    assert.equal(replacement.label.textContent, '픽업 완료');
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

test('return selection sends RETURNED reason', async () => {
    const p = page({ status: 'PICKED_UP', reason: 'RETURNED' }); await p.click();
    assert.equal(p.calls[0][1].body.get('cancelReason'), 'RETURNED');
});
test('mistake selection sends MISTAKE reason', async () => {
    const p = page({ status: 'PICKED_UP' }); await p.click();
    assert.equal(p.calls[0][1].body.get('cancelReason'), 'MISTAKE');
});
test('closing the choice dialog sends no request', async () => {
    const p = page({ status: 'PICKED_UP', autoSelect: false });
    const pending = p.click(); assert.equal(p.dialog.open, true); assert.equal(p.calls.length, 0);
    p.choose(null); await pending;
    assert.equal(p.calls.length, 0); assert.equal(p.button.disabled, false); assert.equal(p.dialog.open, false);
});
test('choices remain disabled during cancellation request', async () => {
    let release; const response = new Promise(resolve => { release = resolve; });
    const p = page({ status: 'PICKED_UP', fetcher: () => response });
    const pending = p.click(); await Promise.resolve(); await Promise.resolve(); await Promise.resolve();
    assert.equal(p.options.every(option => option.disabled), true); await p.click(); assert.equal(p.calls.length, 1);
    release({ status: 204, headers: { get: () => 'completed' } }); await pending;
    assert.equal(p.dialog.open, false);
});
test('failed cancellation preserves pickup state', async () => {
    const p = page({ status: 'PICKED_UP', response: 409 }); await p.click();
    assert.equal(p.row.dataset.status, 'PICKED_UP'); assert.equal(p.calls.length, 1);
});

test('successful pickup refreshes production cards once', async () => {
    const p = page(); await p.click();
    assert.deepEqual(p.refreshes, ['productionStatusChanged']);
});
test('successful cancellation refreshes production cards once', async () => {
    const p = page({ status: 'PICKED_UP' }); await p.click();
    assert.deepEqual(p.refreshes, ['productionStatusChanged']);
});
test('failed mutation does not refresh production cards', async () => {
    const p = page({ response: 409 }); await p.click(); assert.deepEqual(p.refreshes, []);
});

test('refreshed card allocation updates reserved row label to ready and back', () => {
    const p = page(); p.settle('7'); assert.equal(p.label.textContent, '제작 완료');
    p.settle('8'); assert.equal(p.label.textContent, '제작 필요');
});
test('refreshed card allocation updates size one reserved row label like size two', () => {
    const p = page({ cakeSize: '1' }); p.settle('7'); assert.equal(p.label.textContent, '제작 완료');
    p.settle('8'); assert.equal(p.label.textContent, '제작 필요');
});
test('refreshed stock never changes picked up label to ready', () => {
    const p = page({ status: 'PICKED_UP' }); p.settle('7'); assert.equal(p.label.textContent, '픽업 완료');
});

// 현재 시각을 오늘 오전 11:00으로 고정하고, 픽업 시간을 HH:mm으로 지정한다.
const ELEVEN = (() => { const at = new Date(); at.setHours(11, 0, 0, 0); return at; })();
const TODAY = (() => { const pad = value => String(value).padStart(2, '0'); return ELEVEN.getFullYear() + '-' + pad(ELEVEN.getMonth() + 1) + '-' + pad(ELEVEN.getDate()); })();
function pickupAt(resTime) { return { now: ELEVEN, resDate: TODAY, resTime }; }
test('production-needed row is highlighted from 30 minutes before pickup', () => {
    assert.equal(page(pickupAt('11:20')).summary.classList.contains('due-soon'), true);
    assert.equal(page(pickupAt('10:50')).summary.classList.contains('due-soon'), true);
});
test('row more than 30 minutes before pickup is not highlighted', () => {
    assert.equal(page(pickupAt('11:31')).summary.classList.contains('due-soon'), false);
    assert.equal(page(pickupAt('11:30')).summary.classList.contains('due-soon'), true);
});
test('reservation on another day is not highlighted', () => {
    assert.equal(page({ ...pickupAt('11:10'), resDate: '2000-01-01' }).summary.classList.contains('due-soon'), false);
});
test('production-ready or picked-up rows are not highlighted', () => {
    assert.equal(page({ ...pickupAt('11:10'), ready: true }).summary.classList.contains('due-soon'), false);
    assert.equal(page({ ...pickupAt('11:10'), status: 'PICKED_UP' }).summary.classList.contains('due-soon'), false);
});
test('highlight clears after pickup and when stock allocation makes the row ready', async () => {
    const picked = page(pickupAt('11:10'));
    await picked.click();
    assert.equal(picked.summary.classList.contains('due-soon'), false);
    const allocated = page(pickupAt('11:10'));
    allocated.settle('7');
    assert.equal(allocated.summary.classList.contains('due-soon'), false);
    allocated.settle('8');
    assert.equal(allocated.summary.classList.contains('due-soon'), true);
});
test('highlight is rechecked periodically as time passes', () => {
    const p = page(pickupAt('11:10'));
    p.summary.classList.toggle('due-soon', false);
    p.tick();
    assert.equal(p.summary.classList.contains('due-soon'), true);
});
