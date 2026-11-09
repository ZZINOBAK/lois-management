const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = name => fs.readFileSync('src/main/resources/static/js/' + name, 'utf8');
const sample = [
    { cakeId: 7, cakeFlavor: '가나슈', cakeSize: 2, stockQuantity: 3 },
    { cakeId: 8, cakeFlavor: '모카', cakeSize: 1, stockQuantity: 9 },
    { cakeId: 9, cakeFlavor: '기타', cakeSize: 2, stockQuantity: 0 }
];
function page({ get = async () => ({ status: 200, json: async () => sample }), post = async () => ({ status: 204 }) } = {}) {
    const calls = [], feedback = [], triggers = [];
    let beforeRequest, toggleClick, panelClick;
    function node(tag) {
        return { tag, dataset: {}, children: [], disabled: false, attributes: {},
            append(...items) { this.children.push(...items); },
            replaceChildren(fragment) { this.children = fragment.children; },
            setAttribute(name, value) { this.attributes[name] = value; },
            closest() { return this.dataset.stockAction ? this : null; }
        };
    }
    const items = node('items'), status = node('status');
    const descendants = node => node.children.flatMap(child => [child, ...descendants(child)]);
    const buttons = () => descendants(items).filter(node => node.dataset.stockAction);
    const toggle = { setAttribute(name, value) { this[name] = value; }, addEventListener(name, fn) { toggleClick = fn; } };
    const panel = {
        hidden: true, dataset: { needsUrl: '/cake-production/production-needs', produceUrl: '/cake-stocks/produce', cancelUrl: '/cake-stocks/produce-cancel' },
        querySelector: selector => selector === '[data-stock-panel-items]' ? items : status,
        querySelectorAll: buttons,
        contains: button => buttons().includes(button),
        addEventListener(name, fn) { panelClick = fn; }
    };
    const document = {
        querySelector(selector) {
            if (selector === '[data-stock-panel]') return panel;
            if (selector === '[data-stock-panel-toggle]') return toggle;
            if (selector === 'meta[name="_csrf"]') return { content: 'csrf' };
            if (selector === 'meta[name="_csrf_header"]') return { content: 'X-CSRF-TOKEN' };
        },
        createElement: node, createDocumentFragment: () => node('fragment'),
        addEventListener() {}, dispatchEvent: event => feedback.push(event.detail),
        body: { addEventListener(name, fn) { beforeRequest = fn; } }
    };
    const context = vm.createContext({ document, window: { htmx: { trigger: (target, event) => triggers.push(event) } },
        CustomEvent: class { constructor(name, options) { this.detail = options.detail; } },
        Headers, URLSearchParams,
        fetch: async (url, options) => { calls.push({ url, options }); return options.method === 'GET' ? get() : post(); }
    });
    vm.runInContext(source('/auth/auth-common.js'), context);
    vm.runInContext(source('/cake/reservation/reservation-stock-panel.js'), context);
    return { panel, toggle, items, status, calls, feedback, triggers, buttons,
        open: () => toggleClick(), click: button => panelClick({ target: button }),
        request: id => beforeRequest({ detail: { target: { id } } }) };
}
test('starts closed and does not fetch until opened', async () => {
    const p = page(); assert.equal(p.panel.hidden, true); assert.equal(p.calls.length, 0);
    await p.open(); assert.equal(p.panel.hidden, false); assert.equal(p.toggle['aria-expanded'], 'true');
    assert.equal(p.calls[0].options.cache, 'no-store'); assert.equal(p.items.children.length, 2);
    assert.equal(p.buttons().length, 2); assert.equal(p.items.children[0].children[1].children[1].textContent, 3);
});
test('closing and reopening always reads fresh stock', async () => {
    const p = page(); await p.open(); await p.open(); assert.equal(p.panel.hidden, true);
    await p.open(); assert.equal(p.calls.length, 2);
});
for (const action of ['produce', 'cancel']) test(action + ' posts size two with session CSRF and refreshes cards and stock', async () => {
    const p = page(); await p.open();
    const button = p.buttons().find(button => button.dataset.stockAction === action); await p.click(button);
    const request = p.calls.find(call => call.options.method === 'POST');
    assert.equal(request.url, action === 'produce' ? '/cake-stocks/produce' : '/cake-stocks/produce-cancel');
    assert.equal(request.options.body.get('cake_id'), '7'); assert.equal(request.options.body.get('cake_size'), '2');
    assert.equal(request.options.headers.get('X-CSRF-TOKEN'), 'csrf'); assert.equal(request.options.credentials, 'same-origin');
    assert.equal(p.calls.filter(call => call.options.method === 'GET').length, 2);
    assert.deepEqual(p.triggers, ['productionStatusChanged']); assert.equal(p.feedback[0].success, true);
});
test('other has no production controls', async () => {
    const p = page(); await p.open(); assert.equal(p.items.children[1].children[1].textContent, '준비 중');
    assert.ok(p.buttons().every(button => button.dataset.cakeId === '7'));
});
test('search and sorting close the panel while card refresh does not', async () => {
    const p = page(); await p.open(); p.request('production-status'); assert.equal(p.panel.hidden, false);
    p.request('list'); assert.equal(p.panel.hidden, true); assert.equal(p.toggle['aria-expanded'], 'false');
});
test('response from a closed panel cannot overwrite a newly opened panel', async () => {
    let resolve; let count = 0;
    const p = page({ get: () => ++count === 1 ? new Promise(done => { resolve = done; }) : Promise.resolve({ status: 200, json: async () => sample }) });
    const first = p.open(); p.request('list'); await p.open();
    resolve({ status: 200, json: async () => [] }); await first;
    assert.equal(p.items.children.length, 2); assert.equal(p.panel.hidden, false);
});
test('pending write blocks repeated and different stock buttons', async () => {
    let resolve;
    const p = page({ post: () => new Promise(done => { resolve = done; }) }); await p.open();
    const first = p.click(p.buttons()[0]); await p.click(p.buttons()[1]);
    assert.equal(p.calls.filter(call => call.options.method === 'POST').length, 1);
    assert.ok(p.buttons().every(button => button.disabled));
    resolve({ status: 204 }); await first; assert.ok(p.buttons().every(button => !button.disabled));
});
for (const response of [200, 400, 401, 403, 409, 500]) test(response + ' does not retry mutation or refresh cards', async () => {
    const p = page({ post: async () => ({ status: response }) }); await p.open(); await p.click(p.buttons()[0]);
    assert.equal(p.calls.filter(call => call.options.method === 'POST').length, 1); assert.equal(p.triggers.length, 0);
    assert.equal(p.feedback[0].success, false);
});
test('network failure reports uncertain result without retry', async () => {
    const p = page({ post: async () => { throw Error(); } }); await p.open(); await p.click(p.buttons()[0]);
    assert.match(p.feedback[0].message, /새로고침/); assert.equal(p.calls.filter(call => call.options.method === 'POST').length, 1);
});
test('failed lookup prevents writes until reopened', async () => {
    const p = page({ get: async () => ({ status: 500 }) }); await p.open();
    assert.match(p.status.textContent, /다시 열어/); assert.equal(p.buttons().length, 0);
});
test('successful write with failed stock refresh keeps controls disabled', async () => {
    let count = 0;
    const p = page({ get: async () => ++count === 1 ? { status: 200, json: async () => sample } : { status: 500 } });
    await p.open(); await p.click(p.buttons()[0]);
    assert.deepEqual(p.triggers, ['productionStatusChanged']); assert.ok(p.buttons().every(button => button.disabled));
    assert.equal(p.feedback.at(-1).success, false);
});
test('search during a write stays closed even after success', async () => {
    let resolve;
    const p = page({ post: () => new Promise(done => { resolve = done; }) }); await p.open();
    const pending = p.click(p.buttons()[0]); p.request('list'); resolve({ status: 204 }); await pending;
    assert.equal(p.panel.hidden, true); assert.equal(p.calls.filter(call => call.options.method === 'GET').length, 1);
    assert.deepEqual(p.triggers, ['productionStatusChanged']);
});
