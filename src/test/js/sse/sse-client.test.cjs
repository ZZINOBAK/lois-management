const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

const source = fs.readFileSync(path.join(__dirname, '../../../main/resources/static/js/sse/sse-client.js'), 'utf8');

function page() {
    const dispatched = [];
    const timers = new Map();
    const documentHandlers = {};
    let timerId = 0;
    let eventSource;
    class EventSource {
        constructor(url) { this.url = url; this.handlers = {}; eventSource = this; }
        addEventListener(name, handler) { this.handlers[name] = handler; }
        emit(name) { this.handlers[name]?.({}); }
    }
    const document = {
        visibilityState: 'visible',
        dispatchEvent: event => dispatched.push(event.type),
        addEventListener(name, handler) { documentHandlers[name] = handler; }
    };
    const context = vm.createContext({
        document, EventSource,
        CustomEvent: class { constructor(type) { this.type = type; } },
        setTimeout(handler) { const id = ++timerId; timers.set(id, handler); return id; },
        clearTimeout(id) { timers.delete(id); }
    });
    vm.runInContext(source, context);
    return {
        document, dispatched, source: eventSource,
        flush() { const handlers = [...timers.values()]; timers.clear(); handlers.forEach(handler => handler()); },
        visible(state) { document.visibilityState = state; documentHandlers.visibilitychange(); }
    };
}

test('subscribes to the sse endpoint', () => {
    assert.equal(page().source.url, '/sse/subscribe');
});

test('changed notification dispatches sse:changed after debounce', () => {
    const p = page();
    p.source.emit('changed');
    assert.deepEqual(p.dispatched, []);
    p.flush();
    assert.deepEqual(p.dispatched, ['sse:changed']);
});

test('notifications within the debounce window dispatch once', () => {
    const p = page();
    p.source.emit('changed');
    p.source.emit('changed');
    p.source.emit('changed');
    p.flush();
    assert.deepEqual(p.dispatched, ['sse:changed']);
});

test('first connection does not dispatch a refresh', () => {
    const p = page();
    p.source.emit('open');
    p.flush();
    assert.deepEqual(p.dispatched, []);
});

test('reconnection after an error dispatches one refresh', () => {
    const p = page();
    p.source.emit('open');
    p.source.emit('error');
    p.source.emit('open');
    p.flush();
    assert.deepEqual(p.dispatched, ['sse:changed']);
});

test('returning to a visible tab dispatches a refresh while hiding does not', () => {
    const p = page();
    p.visible('hidden');
    p.flush();
    assert.deepEqual(p.dispatched, []);
    p.visible('visible');
    p.flush();
    assert.deepEqual(p.dispatched, ['sse:changed']);
});
