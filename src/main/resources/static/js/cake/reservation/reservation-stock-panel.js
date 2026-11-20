(() => {
    const panel = document.querySelector('[data-stock-panel]');
    const toggle = document.querySelector('[data-stock-panel-toggle]');
    if (!panel || !toggle) return;
    const items = panel.querySelector('[data-stock-panel-items]');
    const status = panel.querySelector('[data-stock-panel-status]');
    const sizeButtons = [...panel.querySelectorAll('[data-stock-size]')];
    let generation = 0;
    let writing = false;
    let loaded = false;
    // 선택된 호수. 새로고침·패널 다시 열기 시 항상 2호로 시작한다.
    let selectedSize = '2';
    let latestNeeds = [];
    function notify(message, success = false) {
        document.dispatchEvent(new CustomEvent('reservation:feedback', { detail: { message, success } }));
    }
    function disableButtons(disabled) {
        panel.querySelectorAll('[data-stock-action]').forEach(button => { button.disabled = disabled; });
        // 불러오는 중·처리 중에는 호수도 바꿀 수 없다.
        sizeButtons.forEach(button => { button.disabled = disabled; });
    }
    function renderSizeButtons() {
        sizeButtons.forEach(button => button.setAttribute('aria-pressed', String(button.dataset.stockSize === selectedSize)));
    }
    function close() {
        generation++;
        loaded = false;
        panel.hidden = true;
        toggle.setAttribute('aria-expanded', 'false');
        selectedSize = '2';
        renderSizeButtons();
    }
    function render(needs) {
        latestNeeds = needs;
        const fragment = document.createDocumentFragment();
        // 선택한 호수의 맛 전체 재고를 보여 준다. (오늘 예약 여부와 무관)
        for (const need of needs.filter(need => String(need.cakeSize) === selectedSize)) {
            const card = document.createElement('div');
            card.className = 'reservation-stock-item';
            card.dataset.flavor = need.cakeFlavor;
            const name = document.createElement('strong');
            name.textContent = need.cakeFlavor;
            card.append(name);
            if (need.cakeFlavor === '기타') {
                const note = document.createElement('p'); note.textContent = '준비 중'; card.append(note);
            } else {
                const controls = document.createElement('div'); controls.className = 'reservation-stock-controls';
                const count = document.createElement('strong'); count.textContent = need.stockQuantity;
                const createButton = (action, text) => {
                    const button = document.createElement('button');
                    button.type = 'button'; button.textContent = text;
                    button.dataset.stockAction = action; button.dataset.cakeId = String(need.cakeId);
                    button.dataset.flavor = need.cakeFlavor;
                    button.setAttribute('aria-label', need.cakeFlavor + ' ' + selectedSize + '호 1개 ' + (action === 'produce' ? '제작 완료' : '제작 취소'));
                    button.disabled = writing;
                    return button;
                };
                controls.append(createButton('cancel', '−'), count, createButton('produce', '+'));
                card.append(controls);
            }
            fragment.append(card);
        }
        items.replaceChildren(fragment);
    }
    async function load() {
        const requestGeneration = ++generation;
        loaded = false;
        disableButtons(true);
        status.textContent = '현재 수량을 불러오는 중입니다.';
        try {
            const response = await window.secureFetch(panel.dataset.needsUrl, { method: 'GET', cache: 'no-store', redirect: 'error' });
            if (response.status !== 200) throw new Error('Stock lookup failed');
            const needs = await response.json();
            if (!Array.isArray(needs)) throw new Error('Invalid stock data');
            if (requestGeneration !== generation || panel.hidden) return;
            render(needs); loaded = true; status.textContent = ''; disableButtons(false);
        } catch (error) {
            if (requestGeneration !== generation || panel.hidden) return;
            status.textContent = '수량을 확인할 수 없습니다. 패널을 닫았다 다시 열어 주세요.';
            notify('현재 수량을 갱신하지 못했습니다. 패널을 닫았다 다시 열어 확인해 주세요.');
        }
    }
    toggle.addEventListener('click', async () => {
        if (writing) return;
        if (!panel.hidden) { close(); return; }
        panel.hidden = false;
        toggle.setAttribute('aria-expanded', 'true');
        await load();
    });
    document.body.addEventListener('htmx:beforeRequest', event => {
        // 실시간 갱신으로 인한 목록 조회는 패널을 닫지 않는다.
        if (event.detail.requestConfig?.headers?.['X-SSE-Refresh']) return;
        if (event.detail.target?.id === 'list') close();
    });
    // 실시간 변경 알림 시 열린 패널만 다시 조회한다. 처리 중이면 처리 완료 후 조회된다.
    document.addEventListener('sse:changed', () => {
        if (!panel.hidden && !writing) load();
    });
    panel.addEventListener('click', async event => {
        const sizeButton = event.target.closest('[data-stock-size]');
        if (sizeButton?.dataset.stockSize) {
            // 호수 전환은 다시 조회하지 않고 열 때 받은 재고로 다시 그린다.
            if (writing || !loaded || panel.hidden || sizeButton.dataset.stockSize === selectedSize) return;
            selectedSize = sizeButton.dataset.stockSize;
            renderSizeButtons();
            render(latestNeeds);
            return;
        }
        const button = event.target.closest('[data-stock-action]');
        if (!button || !panel.contains(button) || button.disabled || writing || !loaded || panel.hidden) return;
        writing = true;
        disableButtons(true);
        const size = selectedSize;
        try {
            const cancelling = button.dataset.stockAction === 'cancel';
            const response = await window.secureFetch(cancelling ? panel.dataset.cancelUrl : panel.dataset.produceUrl, {
                method: 'POST', redirect: 'error',
                body: new URLSearchParams({ cake_id: button.dataset.cakeId, cake_size: size })
            });
            if (response.status === 204) {
                notify(button.dataset.flavor + ' ' + size + '호 1개 ' + (cancelling ? '제작 취소' : '제작 완료'), true);
                window.htmx.trigger(document.body, 'productionStatusChanged');
                if (!panel.hidden) await load();
            } else if (cancelling && response.status === 409) {
                notify('재고가 없어 제작을 취소할 수 없습니다.');
            } else if (response.status === 401 || response.status === 403) {
                notify('로그인 상태와 권한을 확인해 주세요.');
            } else {
                loaded = false;
                notify('처리 결과를 확인할 수 없습니다. 새로고침하여 상태를 확인해 주세요.');
            }
        } catch (error) {
            loaded = false;
            notify('처리 결과를 확인할 수 없습니다. 새로고침하여 상태를 확인해 주세요.');
        } finally {
            writing = false;
            disableButtons(!loaded);
        }
    });
})();
