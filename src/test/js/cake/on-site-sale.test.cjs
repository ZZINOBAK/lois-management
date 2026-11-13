const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
const source = name => fs.readFileSync(path.join(__dirname, '../../../main/resources/static/js', name), 'utf8');

function page(fetcher = async () => ({ status: 204 })) {
    const calls = [];
    let handler;
    let reset = 0;
    let valid = true;
    const feedback = { hidden: true, textContent: '' };
    const button = { disabled: false };
    const values = new Map([['cake_id', '7'], ['cake_size', '1']]);
    const form = {
        action: '/cake-stocks/on-site-sales',
        querySelector: selector => selector === 'button[type="submit"]' ? button : feedback,
        reportValidity: () => valid,
        reset() { reset++; },
        setAttribute() {}, removeAttribute() {},
        addEventListener(name, callback) { handler = callback; }
    };
    const context = vm.createContext({
        window: {}, URLSearchParams, Headers,
        FormData: class { get(key) { return values.get(key); } },
        document: {
            querySelector(selector) {
                if (selector === '[data-on-site-sale]') return form;
                if (selector === 'meta[name="_csrf"]') return { content: 'csrf-value' };
                if (selector === 'meta[name="_csrf_header"]') return { content: 'X-CSRF-TOKEN' };
                return null;
            },
            addEventListener() {}
        },
        fetch: async (url, options) => { calls.push({ url, options }); return fetcher(); }
    });
    vm.runInContext(source('/auth/auth-common.js'), context);
    vm.runInContext(source('/cake/on-site-sale.js'), context);
    return { calls, button, feedback, values, setValid(value) { valid = value; },
        get reset() { return reset; }, submit: () => handler({ preventDefault() {} }) };
}

for (const size of ['1', '2']) {
    test('sale sends selected size ' + size + ' with CSRF and handles 204', async () => {
        const p = page();
        p.values.set('cake_size', size);
        await p.submit();
        assert.equal(p.calls.length, 1);
        assert.equal(p.calls[0].url, '/cake-stocks/on-site-sales');
        assert.equal(p.calls[0].options.body.get('cake_id'), '7');
        assert.equal(p.calls[0].options.body.get('cake_size'), size);
        assert.equal(p.calls[0].options.body.has('note'), false);
        assert.equal(p.calls[0].options.headers.get('X-CSRF-TOKEN'), 'csrf-value');
        assert.equal(p.calls[0].options.credentials, 'same-origin');
        assert.equal(p.calls[0].options.redirect, 'error');
        assert.match(p.feedback.textContent, /현장 판매 1건/);
        assert.equal(p.reset, 1);
        assert.equal(p.button.disabled, false);
    });
}

test('pending request prevents repeated submission', async () => {
    let resolve;
    const p = page(() => new Promise(done => { resolve = done; }));
    const first = p.submit();
    assert.equal(p.button.disabled, true);
    await p.submit();
    assert.equal(p.calls.length, 1);
    resolve({ status: 204 });
    await first;
    assert.equal(p.button.disabled, false);
});
test('invalid form never sends a request', async () => {
    const p = page();
    p.setValid(false);
    await p.submit();
    assert.equal(p.calls.length, 0);
});
for (const status of [400, 401, 403, 500, 200]) {
    test('HTTP ' + status + ' never claims sale success or clears selection', async () => {
        const p = page(async () => ({ status }));
        await p.submit();
        assert.equal(p.reset, 0);
        assert.equal(p.button.disabled, false);
        assert.doesNotMatch(p.feedback.textContent, /처리했습니다/);
        assert.equal(p.calls.length, 1);
        if (status >= 500 || status === 200) assert.match(p.feedback.textContent, /결과를 확인하지 못했습니다/);
    });
}
test('network or redirect failure warns about uncertain result without retry', async () => {
    const p = page(async () => { throw new TypeError('network'); });
    await p.submit();
    assert.match(p.feedback.textContent, /결과를 확인하지 못했습니다/);
    assert.equal(p.calls.length, 1);
    assert.equal(p.reset, 0);
    assert.equal(p.button.disabled, false);
});
