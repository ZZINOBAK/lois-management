const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

const source = fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/reservation.js'), 'utf8');
const productionSelector = 'section.plan-card[hx-get="/reservations/production-status"]';

function page() {
    const handlers = {};
    let elements;
    const replaceProduction = () => {
        elements = {};
        for (const size of ['One', 'Two']) {
            elements[`size${size}Body`] = { style: { display: 'none' } };
            elements[`size${size}Chevron`] = { textContent: '▾' };
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
            if (two) ui.context.toggleSizeTwo();
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
    ui.check(false, false);
    ui.swap(true);
    ui.check(false, true);
});

test('최초 진입 및 새로고침은 이전 페이지 메모리와 독립적으로 접힌다', () => {
    const oldPage = page();
    oldPage.check(false, false);
    oldPage.context.toggleSizeOne();
    oldPage.context.toggleSizeTwo();
    const newPage = page();
    newPage.check(false, false);
    newPage.replaceProduction();
    newPage.swap(true);
    newPage.check(false, false);
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
