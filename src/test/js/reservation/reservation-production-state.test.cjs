const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

const source = fs.readFileSync(path.join(__dirname, '../../../main/resources/static/js/cake/reservation/reservation.js'), 'utf8');
const productionSelector = 'section.plan-card[hx-get="/reservations/production-status"]';

function page() {
    const handlers = {};
    let elements;
    const replaceProduction = () => {
        elements = {};
        for (const size of ['One', 'Two']) {
            elements[`size${size}Body`] = { style: { display: size === 'Two' ? 'grid' : 'none' } };
            elements[`size${size}Chevron`] = { textContent: size === 'Two' ? '▴' : '▾' };
        }
    };
    replaceProduction();
    const context = vm.createContext({
        document: {
            getElementById: id => elements[id],
            addEventListener() {},
            body: { addEventListener(name, handler) { (handlers[name] ??= []).push(handler); } }
        }
    });
    vm.runInContext(source, context);
    return {
        context,
        replaceProduction,
        swap(production) {
            const event = { detail: { elt: { matches: selector => production && selector === productionSelector } } };
            // HTMX는 afterSwap 이후 settle 단계에서 응답의 style을 다시 적용한다.
            for (const handler of handlers['htmx:afterSwap'] ?? []) handler(event);
            if (production) replaceProduction();
            for (const handler of handlers['htmx:afterSettle'] ?? []) handler(event);
        },
        check(one, two) {
            for (const [size, expanded] of [['One', one], ['Two', two]]) {
                assert.equal(elements[`size${size}Body`].style.display, expanded ? 'grid' : 'none');
                assert.equal(elements[`size${size}Chevron`].textContent, expanded ? '▴' : '▾');
            }
        }
    };
}

for (const one of [false, true]) {
    for (const two of [false, true]) {
        test(`부분 갱신 후 상태 보존: 1호=${one}, 2호=${two}`, () => {
            const ui = page();
            if (one) ui.context.toggleSizeOne();
            if (!two) ui.context.toggleSizeTwo();
            ui.check(one, two);
            // 픽업/제작 응답의 행 교체는 제작 현황 복원을 실행하지 않는다.
            for (const action of ['pickup', 'produce']) {
                ui.swap(false);
                ui.check(one, two);
                // productionStatusChanged가 유발한 제작 현황 outerHTML 교체
                ui.replaceProduction();
                ui.swap(true);
                ui.check(one, two);
            }
            ui.context.toggleSizeOne();
            ui.check(!one, two);
            ui.context.toggleSizeTwo();
            ui.check(!one, !two);
        });
    }
}

test('목록 및 자식 요소의 swap은 복원을 실행하지 않는다', () => {
    const ui = page();
    ui.context.toggleSizeTwo();
    ui.replaceProduction();
    ui.swap(false);
    ui.check(false, true);
    ui.swap(true);
    ui.check(false, false);
});

test('최초 진입 및 새로고침은 1호 닫힘과 2호 열림으로 시작한다', () => {
    const oldPage = page();
    oldPage.check(false, true);
    oldPage.context.toggleSizeOne();
    oldPage.context.toggleSizeTwo();
    const newPage = page();
    newPage.check(false, true);
    newPage.replaceProduction();
    newPage.swap(true);
    newPage.check(false, true);
});

test('행 클릭은 상세를 토글하며 버튼 클릭은 상세를 토글하지 않는다', () => {
    let toggles = 0;
    const context = vm.createContext({ document: {
        getElementById: id => id === 'detail-7' ? {classList: {toggle(name) { assert.equal(name, 'hidden'); toggles++; }}} : null,
        addEventListener() {}, body: {addEventListener() {}}
    }});
    vm.runInContext(source, context);
    context.toggleDetailRow({target: {closest: () => null}}, 7);
    assert.equal(toggles, 1);
    context.toggleDetailRow({target: {closest: () => ({})}}, 7);
    assert.equal(toggles, 1);
});

test('기본 진입은 오늘이며 전체 전환 후 정렬 요청도 전체를 유지한다', () => {
    const handlers = {};
    const storage = new Map([['reservationRange', 'date'], ['reservationDate', '2026-10-24']]);
    const buttons = ['today', 'from-today', 'date', 'all'].map(range => ({
        dataset: {range}, setAttribute(name, value) { this[name] = value; }
    }));
    const context = vm.createContext({
        window: {location: {pathname: '/reservations'}},
        sessionStorage: {
            getItem: key => storage.get(key) ?? null,
            setItem: (key, value) => storage.set(key, value),
            removeItem: key => storage.delete(key)
        },
        document: {
            querySelectorAll: () => buttons,
            getElementById: () => null,
            addEventListener(name, handler) { (handlers[name] ??= []).push(handler); },
            body: {addEventListener(name, handler) { (handlers[name] ??= []).push(handler); }}
        }
    });
    vm.runInContext(source, context);
    handlers.DOMContentLoaded.forEach(handler => handler());
    assert.equal(storage.get('reservationRange'), 'today');
    assert.equal(storage.has('reservationDate'), false);
    assert.equal(buttons[0]['aria-pressed'], 'true');
    const request = parameters => {
        const event = {detail: {elt: {getAttribute: () => '/reservations/list'}, parameters}};
        handlers['htmx:configRequest'].forEach(handler => handler(event));
        return event.detail.parameters;
    };
    assert.equal(request({sort: 'created-at'}).range, 'today');
    for (const range of ['from-today', 'date', 'all']) {
        assert.equal(request({range}).range, range);
        assert.equal(buttons.find(button => button.dataset.range === range)['aria-pressed'], 'true');
    }
    assert.equal(request({sort: 'created-at'}).range, 'all');
});

function ssePage({ storage = new Map(), modalOpen = false, openDetails = [] } = {}) {
    const handlers = {};
    const timers = new Map();
    let timerId = 0;
    const ajaxCalls = [], triggers = [];
    const modal = { classList: { contains: name => name === 'hidden' && !modalOpen } };
    const details = Object.fromEntries(openDetails.map(id => [id, { id, removed: [], classList: { remove(name) { details[id].removed.push(name); } } }]));
    const on = (name, handler) => { (handlers[name] ??= []).push(handler); };
    const context = vm.createContext({
        window: { htmx: true, location: { pathname: '/reservations' } },
        htmx: { ajax: (verb, path, options) => ajaxCalls.push({ verb, path, options }), trigger: (target, name) => triggers.push(name) },
        sessionStorage: {
            getItem: key => storage.get(key) ?? null,
            setItem: (key, value) => storage.set(key, value),
            removeItem: key => storage.delete(key)
        },
        setTimeout(handler) { const id = ++timerId; timers.set(id, handler); return id; },
        clearTimeout(id) { timers.delete(id); },
        document: {
            getElementById: id => id === 'list' ? {} : id === 'confirm-modal' ? modal : details[id] ?? null,
            querySelector: () => null,
            querySelectorAll: selector => selector === '#list .detail-row:not(.hidden)' ? Object.values(details) : [],
            addEventListener: on,
            body: { addEventListener: on }
        }
    });
    vm.runInContext(source, context);
    const emit = (name, detail) => (handlers[name] ?? []).forEach(handler => handler({ detail }));
    return {
        ajaxCalls, triggers, details, timers,
        closeModal() { modalOpen = false; },
        runTimers() { const pending = [...timers.values()]; timers.clear(); pending.forEach(handler => handler()); },
        listRequest: (path, parameters) => emit('htmx:configRequest',
            { target: { id: 'list' }, verb: 'get', path, parameters, elt: { getAttribute: () => null } }),
        sse: () => emit('sse:changed'),
        swap: headers => emit('htmx:afterSwap', { target: { id: 'list' }, requestConfig: { headers } })
    };
}

test('실시간 알림은 마지막 목록 조회를 SSE 헤더와 함께 다시 요청한다', () => {
    const p = ssePage();
    p.listRequest('/reservations/search', { contactSuffix: '1234' });
    p.sse();
    assert.equal(p.ajaxCalls.length, 1);
    const { verb, path: requestPath, options } = p.ajaxCalls[0];
    assert.equal(verb, 'GET');
    assert.equal(requestPath, '/reservations/search');
    assert.equal(options.target, '#list');
    assert.equal(options.values.contactSuffix, '1234');
    assert.equal(options.headers['X-SSE-Refresh'], 'true');
    assert.deepEqual(p.triggers, ['productionStatusChanged']);
});

test('목록 조회 기록이 없으면 저장된 범위와 날짜로 다시 요청한다', () => {
    const p = ssePage({ storage: new Map([['reservationRange', 'date'], ['reservationDate', '2026-11-13']]) });
    p.sse();
    assert.equal(p.ajaxCalls[0].path, '/reservations/list');
    assert.equal(p.ajaxCalls[0].options.values.range, 'date');
    assert.equal(p.ajaxCalls[0].options.values.date, '2026-11-13');
});

test('삭제 확인 모달이 열려 있으면 닫힐 때까지 갱신을 미룬다', () => {
    const p = ssePage({ modalOpen: true });
    p.sse();
    assert.equal(p.ajaxCalls.length, 0);
    p.runTimers();
    assert.equal(p.ajaxCalls.length, 0);
    p.closeModal();
    p.runTimers();
    assert.equal(p.ajaxCalls.length, 1);
    assert.deepEqual(p.triggers, ['productionStatusChanged']);
});

test('실시간 갱신 후 펼쳐 두었던 상세 행을 다시 펼친다', () => {
    const p = ssePage({ openDetails: ['detail-7'] });
    p.sse();
    p.swap({});
    assert.deepEqual(p.details['detail-7'].removed, []);
    p.swap({ 'X-SSE-Refresh': 'true' });
    assert.deepEqual(p.details['detail-7'].removed, ['hidden']);
});
