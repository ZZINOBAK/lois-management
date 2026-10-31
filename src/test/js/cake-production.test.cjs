const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const load = name => fs.readFileSync(path.join(__dirname, '../../main/resources/static/js', name), 'utf8');

function page(fetcher = async () => ({ status: 204 }), readFetcher = async () => ({ status: 200, json: async () => [] }), state = {}) {
    const calls = [];
    const readCalls = [];
    const needsList = { children: ['initial'], replaceChildren(fragment) { this.children = fragment.children; } };
    const needsWarning = { hidden: true };
    const timers = new Map();
    let timerId = 0;
    const toast = { dataset: {}, hidden: true, textContent: '' };
    const flavors = ['가나슈', '무화과', '모카', '레몬', '바닐라'];
    const buttons = flavors.map((flavor, i) => ({
        dataset: { cakeId: String(11 + i), flavor, productionAction: 'produce' },
        disabled: false,
        closest() { return this; }
    }));
    const cancelButtons = flavors.map((flavor, i) => ({
        dataset: { cakeId: String(11 + i), flavor, productionAction: 'cancel' },
        disabled: false, closest() { return this; }
    }));
    const other = { dataset: { productionAction: 'other' }, closest() { return this; } };
    let click;
    const attrs = {};
    const root = {
        dataset: { produceUrl: '/cake-stocks/produce', cancelUrl: '/cake-stocks/produce-cancel', needsUrl: '/cake-movements/production-needs' },
        querySelector: selector => selector === '[data-production-needs]' ? needsList
            : selector === '[data-production-needs-warning]' ? needsWarning : toast,
        querySelectorAll: selector => {
            if (selector === '[data-production-reservation]') return state.rows || [];
            if (selector === '[data-production-need]') return (state.needs || []).map(need => ({
                dataset: { cakeId: String(need.cakeId), cakeSize: String(need.cakeSize), stockQuantity: String(need.stockQuantity) }
            }));
            return [...buttons, ...cancelButtons];
        },
        contains: item => buttons.includes(item) || cancelButtons.includes(item) || item === other,
        setAttribute: (name, value) => { attrs[name] = value; },
        removeAttribute: name => { delete attrs[name]; },
        addEventListener(name, handler) { if (name === 'click') click = handler; }
    };
    const element = tag => ({ tag, children: [], append(...items) { this.children.push(...items); } });
    const document = {
        createElement: element,
        createDocumentFragment: () => element('fragment'),
        querySelector(selector) {
            if (selector === '[data-production-root]') return root;
            if (selector === 'meta[name="_csrf"]') return { content: 'csrf-value' };
            if (selector === 'meta[name="_csrf_header"]') return { content: 'X-CSRF-TOKEN' };
            return null;
        },
        addEventListener() {}
    };
    const context = vm.createContext({
        document, window: {}, URLSearchParams, Headers,
        fetch: async (url, options) => {
            if (options.method === 'GET') { readCalls.push({ url, options }); return readFetcher(url, options); }
            calls.push({ url, options }); return fetcher(url, options);
        },
        setTimeout(handler) { const id = ++timerId; timers.set(id, handler); return id; },
        clearTimeout(id) { timers.delete(id); }
    });
    vm.runInContext(load('auth-common.js'), context);
    vm.runInContext(load('cake-production.js'), context);
    return { calls, readCalls, needsList, needsWarning, buttons, cancelButtons, other, toast, timers, attrs, click: button => click({ target: button }) };
}

for (let i = 0; i < 5; i++) {
    test('flavor button ' + i + ' posts its own ID and fixed size with CSRF', async () => {
        const p = page();
        await p.click(p.buttons[i]);
        assert.equal(p.calls.length, 1);
        const { url, options } = p.calls[0];
        assert.equal(url, '/cake-stocks/produce');
        assert.equal(options.method, 'POST');
        assert.equal(options.body.get('cake_id'), String(11 + i));
        assert.equal(options.body.get('cake_size'), '2');
        assert.equal(options.headers.get('X-CSRF-TOKEN'), 'csrf-value');
        assert.equal(options.credentials, 'same-origin');
        assert.equal(options.redirect, 'error');
        assert.equal(p.toast.textContent, p.buttons[i].dataset.flavor + ' 2호 1개 제작 완료');
        assert.equal(p.toast.dataset.kind, 'success');
    });
}

test('other never sends a production request', async () => {
    const p = page();
    await p.click(p.other);
    assert.equal(p.calls.length, 0);
    assert.match(p.toast.textContent, /준비 중/);
});

test('pending request blocks repeated and different flavor clicks and restores all buttons', async () => {
    let resolve;
    const p = page(() => new Promise(done => { resolve = done; }));
    const pending = p.click(p.buttons[0]);
    assert.ok(p.buttons.every(button => button.disabled));
    assert.equal(p.attrs['aria-busy'], 'true');
    await p.click(p.buttons[0]);
    await p.click(p.buttons[1]);
    assert.equal(p.calls.length, 1);
    resolve({ status: 204 });
    await pending;
    assert.ok(p.buttons.every(button => !button.disabled));
    assert.equal(p.attrs['aria-busy'], undefined);
});

for (const status of [200, 400, 401, 403, 500]) {
    test('HTTP ' + status + ' shows persistent failure and restores buttons', async () => {
        const p = page(async () => ({ status }));
        await p.click(p.buttons[0]);
        assert.equal(p.toast.dataset.kind, 'error');
        assert.equal(p.toast.hidden, false);
        assert.equal(p.timers.size, 0);
        assert.ok(p.buttons.every(button => !button.disabled));
        if (status === 401) assert.match(p.toast.textContent, /로그인/);
        if (status === 403) assert.match(p.toast.textContent, /권한/);
    });
}

test('network or redirect failure reports unknown result without retry', async () => {
    const p = page(async () => { throw new TypeError('network or redirect'); });
    await p.click(p.buttons[0]);
    assert.equal(p.calls.length, 1);
    assert.match(p.toast.textContent, /처리 결과를 확인하지 못했습니다/);
    assert.equal(p.toast.dataset.kind, 'error');
    assert.ok(p.buttons.every(button => !button.disabled));
});

test('new toast replaces old timeout so it cannot hide the new message', async () => {
    const p = page();
    await p.click(p.buttons[0]);
    const first = [...p.timers.keys()][0];
    await p.click(p.buttons[1]);
    assert.equal(p.timers.has(first), false);
    assert.equal(p.timers.size, 1);
    assert.equal(p.toast.hidden, false);
    [...p.timers.values()][0]();
    assert.equal(p.toast.hidden, true);
});

test('failure cancels a previous success timeout', async () => {
    let status = 204;
    const p = page(async () => ({ status }));
    await p.click(p.buttons[0]);
    status = 500;
    await p.click(p.buttons[1]);
    assert.equal(p.timers.size, 0);
    assert.equal(p.toast.hidden, false);
    assert.equal(p.toast.dataset.kind, 'error');
});

for (let i = 0; i < 5; i++) {
    test('minus button ' + i + ' sends only cancellation with correct ID and size', async () => {
        const p = page();
        await p.click(p.cancelButtons[i]);
        assert.equal(p.calls.length, 1);
        assert.equal(p.calls[0].url, '/cake-stocks/produce-cancel');
        assert.equal(p.calls[0].options.body.get('cake_id'), String(11 + i));
        assert.equal(p.calls[0].options.body.get('cake_size'), '2');
        assert.equal(p.calls[0].options.headers.get('X-CSRF-TOKEN'), 'csrf-value');
        assert.match(p.toast.textContent, /2호 1개 제작 취소/);
    });
}
test('cancellation blocks both actions while pending and restores buttons', async () => {
    let resolve;
    const p = page(() => new Promise(done => { resolve = done; }));
    const pending = p.click(p.cancelButtons[0]);
    assert.ok([...p.buttons, ...p.cancelButtons].every(b => b.disabled));
    await p.click(p.buttons[0]);
    await p.click(p.cancelButtons[1]);
    assert.equal(p.calls.length, 1);
    resolve({ status: 204 });
    await pending;
    assert.ok([...p.buttons, ...p.cancelButtons].every(b => !b.disabled));
});
test('production blocks cancellation while pending', async () => {
    let resolve;
    const p = page(() => new Promise(done => { resolve = done; }));
    const pending = p.click(p.buttons[0]);
    await p.click(p.cancelButtons[0]);
    assert.equal(p.calls.length, 1);
    resolve({ status: 204 });
    await pending;
});
for (const status of [409, 500]) {
    test('cancellation failure ' + status + ' shows error and restores buttons', async () => {
        const p = page(async () => ({ status }));
        await p.click(p.cancelButtons[0]);
        assert.equal(p.toast.dataset.kind, 'error');
        assert.match(p.toast.textContent, status === 409 ? /재고가 없어/ : /제작 취소에 실패/);
        assert.ok([...p.buttons, ...p.cancelButtons].every(b => !b.disabled));
    });
}
test('cancellation network failure does not retry and restores buttons', async () => {
    const p = page(async () => { throw new TypeError('network'); });
    await p.click(p.cancelButtons[0]);
    assert.equal(p.calls.length, 1);
    assert.match(p.toast.textContent, /처리 결과를 확인하지 못했습니다/);
    assert.ok([...p.buttons, ...p.cancelButtons].every(b => !b.disabled));
});

test('card layout preserves server ID order without CSS reordering', () => {
    const css = fs.readFileSync(path.join(__dirname, '../../main/resources/static/css/cake-production.css'), 'utf8');
    assert.doesNotMatch(css, /(?:^|[;{])\s*order\s*:/m);
});

test('card and minus button retain rounded borderless styles', () => {
    const css = fs.readFileSync(path.join(__dirname, '../../main/resources/static/css/cake-production.css'), 'utf8');
    assert.doesNotMatch(css, /\bb\s+border-radius\s*:/);
    for (const [selector, radius] of [
        ['.cake-production .cake-production-button', '20px'],
        ['.cake-production .cake-production-cancel', '50%']
    ]) {
        const start = css.indexOf(selector + ' {');
        assert.notEqual(start, -1);
        const rule = css.slice(start, css.indexOf('}', start));
        assert.match(rule, /(?:^|[;{])\s*border:\s*0\s*;/);
        assert.ok(rule.includes('border-radius: ' + radius + ';'));
    }
});

for (const action of ['produce', 'cancel']) {
    test(action + ' success refreshes shortage with emphasis and safe text', async () => {
        const p = page(undefined, async () => ({ status: 200, json: async () => [
            { cakeFlavor: '가나슈', requiredQuantity: 2 }, { cakeFlavor: '<img onerror=evil()>', requiredQuantity: 0 }
        ] }));
        await p.click(action === 'produce' ? p.buttons[0] : p.cancelButtons[0]);
        assert.equal(p.readCalls.length, 1);
        assert.equal(p.readCalls[0].url, '/cake-movements/production-needs');
        assert.equal(p.readCalls[0].options.cache, 'no-store');
        assert.equal(p.needsList.children[0].className, 'cake-production-need is-needed');
        assert.equal(p.needsList.children[1].className, 'cake-production-need');
        assert.equal(p.needsList.children[1].children[0].textContent, '<img onerror=evil()>');
        assert.equal(p.needsWarning.hidden, true);
    });
}
test('write failure does not refresh quantities', async () => {
    const p = page(async () => ({ status: 409 }));
    await p.click(p.cancelButtons[0]);
    assert.equal(p.readCalls.length, 0);
});
for (const mode of ['HTTP', 'network', 'JSON']) {
    test('refresh ' + mode + ' failure preserves old values and never repeats production', async () => {
        const p = page(undefined, async () => {
            if (mode === 'network') throw new TypeError('network');
            if (mode === 'JSON') return { status: 200, json: async () => { throw new SyntaxError('json'); } };
            return { status: 500 };
        });
        await p.click(p.buttons[0]);
        assert.equal(p.calls.length, 1);
        assert.equal(p.readCalls.length, 1);
        assert.deepEqual(p.needsList.children, ['initial']);
        assert.equal(p.needsWarning.hidden, false);
        assert.match(p.toast.textContent, /제작 완료 처리됐지만 수량을 갱신하지 못했습니다/);
        assert.equal(p.toast.dataset.kind, 'error');
        assert.ok([...p.buttons, ...p.cancelButtons].every(b => !b.disabled));
    });
}
test('successful refresh removes stale warning after a previous refresh failure', async () => {
    let status = 500;
    const p = page(undefined, async () => ({ status, json: async () => [] }));
    await p.click(p.buttons[0]);
    assert.equal(p.needsWarning.hidden, false);
    status = 200;
    await p.click(p.buttons[0]);
    assert.equal(p.needsWarning.hidden, true);
    assert.equal(p.calls.length, 2);
});

function reservationRow(id, time, { cakeId = 11, size = 2, make = 'RESERVED', pickup = 'WAITING' } = {}) {
    const status = { textContent: '' };
    const row = {
        dataset: { reservationId: String(id), pickupTime: time, cakeId: String(cakeId),
            cakeSize: String(size), makeStatus: make, pickupStatus: pickup },
        classList: { toggle(name, value) {
            if (name === 'is-production-ready') row.ready = value;
            if (name === 'is-production-needed') row.needsProduction = value;
        } },
        querySelector: () => status, status
    };
    return row;
}
test('initial stock covers earliest unpicked reservation and skips picked rows', () => {
    const late = reservationRow(3, '14:00');
    const picked = reservationRow(1, '12:00', { pickup: 'PICKED' });
    const early = reservationRow(2, '13:00');
    page(undefined, undefined, { rows: [late, picked, early], needs: [{ cakeId: 11, cakeSize: 2, stockQuantity: 1 }] });
    assert.equal(early.ready, true);
    assert.equal(early.status.textContent, '제작 완료');
    assert.equal(late.ready, false);
    assert.equal(late.status.textContent, '제작 필요');
    assert.equal(picked.ready, false);
    assert.equal(picked.status.textContent, '픽업 완료');
});
test('same pickup time uses reservation ID tie-breaker', () => {
    const laterId = reservationRow(10, '13:00');
    const earlierId = reservationRow(2, '13:00');
    page(undefined, undefined, { rows: [laterId, earlierId], needs: [{ cakeId: 11, cakeSize: 2, stockQuantity: 1 }] });
    assert.equal(earlierId.ready, true);
    assert.equal(laterId.ready, false);
});
test('allocation separates flavors and size and excludes nonreserved rows', () => {
    const wrongSize = reservationRow(1, '09:00', { size: 1 });
    const wrongState = reservationRow(2, '10:00', { make: 'READY' });
    const anotherFlavor = reservationRow(3, '11:00', { cakeId: 12 });
    const eligible = reservationRow(4, '12:00');
    page(undefined, undefined, { rows: [wrongSize, wrongState, anotherFlavor, eligible],
        needs: [{ cakeId: 11, cakeSize: 2, stockQuantity: 1 }, { cakeId: 12, cakeSize: 1, stockQuantity: 5 }] });
    assert.equal(eligible.ready, true);
    for (const row of [wrongSize, wrongState, anotherFlavor]) assert.equal(row.ready, false);
});
for (const stock of [0, 1, 2, 5]) {
    test('current quantity ' + stock + ' covers only that many eligible rows', () => {
        const rows = [reservationRow(1, '12:00'), reservationRow(2, '13:00')];
        page(undefined, undefined, { rows, needs: [{ cakeId: 11, cakeSize: 2, stockQuantity: stock }] });
        assert.equal(rows.filter(r => r.ready).length, Math.min(stock, 2));
    });
}
test('production and cancellation refresh recompute readiness in both directions', async () => {
    const rows = [reservationRow(1, '12:00'), reservationRow(2, '13:00')];
    let stock = 2;
    const p = page(undefined, async () => ({ status: 200, json: async () => [
        { cakeId: 11, cakeSize: 2, stockQuantity: stock, cakeFlavor: '가나슈', requiredQuantity: 0 }
    ] }), { rows, needs: [{ cakeId: 11, cakeSize: 2, stockQuantity: 1 }] });
    await p.click(p.buttons[0]);
    assert.ok(rows.every(r => r.ready));
    stock = 1;
    await p.click(p.cancelButtons[0]);
    assert.equal(rows[0].ready, true);
    assert.equal(rows[1].ready, false);
    assert.equal(rows[1].status.textContent, '제작 필요');
    assert.equal(p.calls.length, 2);
});
test('missing stock leaves reservation unallocated', () => {
    const row = reservationRow(1, '12:00');
    page(undefined, undefined, { rows: [row] });
    assert.equal(row.ready, false);
});
test('failed refresh preserves previous readiness instead of guessing', async () => {
    const row = reservationRow(1, '12:00');
    const p = page(undefined, async () => ({ status: 500 }), {
        rows: [row], needs: [{ cakeId: 11, cakeSize: 2, stockQuantity: 1 }]
    });
    await p.click(p.cancelButtons[0]);
    assert.equal(row.ready, true);
    assert.equal(p.needsWarning.hidden, false);
    assert.equal(p.calls.length, 1);
});

test('production-needed display is emphasized without changing reservation state', () => {
    const row = reservationRow(1, '12:00');
    const original = { ...row.dataset };
    page(undefined, undefined, { rows: [row] });
    assert.equal(row.status.textContent, '제작 필요');
    assert.equal(row.needsProduction, true);
    assert.deepEqual(row.dataset, original);
    const css = fs.readFileSync(path.join(__dirname, '../../main/resources/static/css/cake-production.css'), 'utf8');
    const start = css.indexOf('.cake-production .is-production-needed .cake-production-reservation-status {');
    assert.notEqual(start, -1);
    const rule = css.slice(start, css.indexOf('}', start));
    assert.match(rule, /font-size: 18px/);
    assert.match(rule, /font-weight: 750/);
    assert.doesNotMatch(rule, /color\s*:/);
});
test('readiness removes production-needed emphasis and cancellation restores it', async () => {
    const row = reservationRow(1, '12:00');
    let stock = 1;
    const p = page(undefined, async () => ({ status: 200, json: async () => [
        { cakeId: 11, cakeSize: 2, stockQuantity: stock, cakeFlavor: '가나슈', requiredQuantity: 0 }
    ] }), { rows: [row] });
    await p.click(p.buttons[0]);
    assert.equal(row.status.textContent, '제작 완료');
    assert.equal(row.needsProduction, false);
    stock = 0;
    await p.click(p.cancelButtons[0]);
    assert.equal(row.status.textContent, '제작 필요');
    assert.equal(row.needsProduction, true);
});
test('picked-up reservation does not receive production-needed emphasis', () => {
    const row = reservationRow(1, '12:00', { pickup: 'PICKED' });
    page(undefined, undefined, { rows: [row] });
    assert.equal(row.status.textContent, '픽업 완료');
    assert.equal(row.needsProduction, false);
});
