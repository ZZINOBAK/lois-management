(() => {
    const root = document.querySelector('[data-production-root]');
    if (!root) return;
    const toast = root.querySelector('[data-production-toast]');
    const buttons = [...root.querySelectorAll('[data-production-action="produce"], [data-production-action="cancel"]')];
    const needsList = root.querySelector('[data-production-needs]');
    const needsOneList = root.querySelector('[data-production-needs-one]');
    const needsOneGroup = root.querySelector('[data-production-needs-one-group]');
    const needsWarning = root.querySelector('[data-production-needs-warning]');
    const sizeButtons = [...root.querySelectorAll('[data-production-size]')];
    // 선택된 제작 호수. 새로고침 시 항상 2호로 시작한다.
    let selectedSize = '2';
    let latestNeeds = [];
    let pending = false;
    let sseRefreshQueued = false;
    let toastTimer;

    function notify(message, kind = 'success') {
        clearTimeout(toastTimer);
        toast.textContent = message;
        toast.dataset.kind = kind;
        toast.hidden = false;
        if (kind !== 'error') {
            toastTimer = setTimeout(() => { toast.hidden = true; }, 2000);
        }
    }

    function updateCardStocks(needs) {
        latestNeeds = needs;
        const quantities = new Map(needs.map(need => [
            String(need.cakeId) + ':' + String(need.cakeSize), need.stockQuantity
        ]));
        for (const element of root.querySelectorAll('[data-card-stock]')) {
            const key = element.dataset.cakeId + ':' + element.dataset.cakeSize;
            element.textContent = String(quantities.get(key) ?? 0);
        }
    }

    function updateReservationReadiness(needs) {
        const remaining = new Map();
        for (const need of needs) {
            if ([1, 2].includes(Number(need.cakeSize))) {
                remaining.set(String(need.cakeId) + ':' + String(need.cakeSize), Math.max(Number(need.stockQuantity) || 0, 0));
            }
        }
        const rows = [...root.querySelectorAll('[data-production-reservation]')];
        rows.sort((a, b) => {
            const timeOrder = (a.dataset.pickupTime || '').localeCompare(b.dataset.pickupTime || '');
            return timeOrder || Number(a.dataset.reservationId) - Number(b.dataset.reservationId);
        });
        for (const row of rows) {
            const data = row.dataset;
            const key = data.cakeId + ':' + data.cakeSize;
            const available = remaining.get(key) || 0;
            const ready = [1, 2].includes(Number(data.cakeSize)) && data.status === 'RESERVED' && available > 0;
            if (ready) remaining.set(key, available - 1);
            row.classList.toggle('is-picked-up', data.status === 'PICKED_UP');
            row.classList.toggle('is-production-ready', ready);
            row.classList.toggle('is-production-needed', !ready && data.status === 'RESERVED');
            const status = row.querySelector('.cake-production-reservation-status');
            status.textContent = ready ? '제작 완료' :
                (data.status === 'PICKED_UP' ? '픽업 완료' : (data.status === 'RESERVED' ? '제작 필요' : '-'));
        }
    }

    const initialNeeds = [...root.querySelectorAll('[data-production-need]')].map(item => ({
        cakeId: item.dataset.cakeId, cakeSize: item.dataset.cakeSize, stockQuantity: item.dataset.stockQuantity
    }));
    // 제작 필요 수량에 없는 맛(1호 예약이 없는 맛)도 카드에는 재고를 보여 주기 위해 카드의 호수별 재고를 함께 쓴다.
    for (const element of root.querySelectorAll('[data-card-stock]')) {
        if (element.dataset.stockOne !== undefined) {
            initialNeeds.push({ cakeId: element.dataset.cakeId, cakeSize: '1', stockQuantity: element.dataset.stockOne });
        }
        if (element.dataset.stockTwo !== undefined) {
            initialNeeds.push({ cakeId: element.dataset.cakeId, cakeSize: '2', stockQuantity: element.dataset.stockTwo });
        }
    }
    updateCardStocks(initialNeeds);
    updateReservationReadiness(initialNeeds);

    // 선택된 호수를 버튼 색, 카드 재고, 취소 버튼 라벨에 반영한다.
    function renderSelectedSize() {
        for (const button of sizeButtons) {
            button.setAttribute('aria-pressed', String(button.dataset.productionSize === selectedSize));
        }
        for (const element of root.querySelectorAll('[data-card-stock]')) {
            element.dataset.cakeSize = selectedSize;
        }
        for (const button of root.querySelectorAll('[data-production-action="cancel"]')) {
            button.setAttribute('aria-label', button.dataset.flavor + ' ' + selectedSize + '호 1개 제작 취소');
        }
        updateCardStocks(latestNeeds);
    }

    async function refreshNeeds() {
        const response = await window.secureFetch(root.dataset.needsUrl, {
            method: 'GET', redirect: 'error', cache: 'no-store'
        });
        if (response.status !== 200) throw new Error('Quantity refresh failed');
        const needs = await response.json();
        if (!Array.isArray(needs)) throw new Error('Invalid quantity response');
        const fragment = document.createDocumentFragment();
        const oneFragment = document.createDocumentFragment();
        for (const need of needs) {
            const item = document.createElement('li');
            item.className = 'cake-production-need' + (need.requiredQuantity > 0 ? ' is-needed' : '');
            const name = document.createElement('span');
            name.textContent = need.cakeFlavor;
            const quantity = document.createElement('span');
            quantity.className = 'cake-production-need-quantity';
            quantity.textContent = need.requiredQuantity;
            item.append(name, quantity);
            if (Number(need.cakeSize) === 1) {
                // 1호 제작 필요 수량은 오늘 1호 예약이 있는 맛만 보여 준다.
                if (need.todayReserved) oneFragment.append(item);
            } else if (Number(need.cakeSize) === 2) fragment.append(item);
        }
        updateCardStocks(needs);
        updateReservationReadiness(needs);
        needsList.replaceChildren(fragment);
        needsOneList.replaceChildren(oneFragment);
        needsOneGroup.hidden = !needs.some(need => Number(need.cakeSize) === 1 && need.todayReserved);
        needsWarning.hidden = true;
    }

    async function refreshReservations() {
        const response = await window.secureFetch(root.dataset.reservationsUrl, {
            method: 'GET', redirect: 'error', cache: 'no-store'
        });
        if (response.status !== 200) throw new Error('Reservation refresh failed');
        const template = document.createElement('template');
        template.innerHTML = await response.text();
        const table = template.content.querySelector('[data-production-reservation-table]');
        if (!table) throw new Error('Invalid reservation response');
        root.querySelector('[data-production-reservation-table]').replaceWith(table);
    }

    // 실시간 변경 알림 시 예약 표를 교체한 뒤 수량과 제작 상태를 다시 계산한다.
    async function refreshFromSse() {
        if (pending) {
            sseRefreshQueued = true;
            return;
        }
        try {
            await refreshReservations();
            await refreshNeeds();
        } catch {
            needsWarning.hidden = false;
        }
    }

    document.addEventListener('sse:changed', refreshFromSse);

    root.addEventListener('click', async event => {
        const sizeButton = event.target.closest('[data-production-size]');
        if (sizeButton?.dataset.productionSize) {
            // 처리 중에는 호수를 바꾸지 않는다. (요청 호수와 화면 호수가 어긋나지 않도록)
            if (pending || sizeButton.dataset.productionSize === selectedSize) return;
            selectedSize = sizeButton.dataset.productionSize;
            renderSelectedSize();
            return;
        }
        const button = event.target.closest('[data-production-action]');
        if (!button || !root.contains(button) || pending) return;
        if (button.dataset.productionAction === 'other') {
            notify('기타 케이크 기능은 준비 중입니다.');
            return;
        }
        const cancelling = button.dataset.productionAction === 'cancel';
        const size = selectedSize;
        pending = true;
        buttons.forEach(item => { item.disabled = true; });
        sizeButtons.forEach(item => { item.disabled = true; });
        root.setAttribute('aria-busy', 'true');
        try {
            const body = new URLSearchParams({ cake_id: button.dataset.cakeId, cake_size: size });
            const response = await window.secureFetch(cancelling ? root.dataset.cancelUrl : root.dataset.produceUrl, {
                method: 'POST',
                redirect: 'error',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body
            });
            if (response.status === 204) {
                const successMessage = button.dataset.flavor + ' ' + size + (cancelling ? '호 1개 제작 취소' : '호 1개 제작 완료');
                notify(successMessage);
                try {
                    await refreshNeeds();
                } catch {
                    needsWarning.hidden = false;
                    notify(successMessage + ' 처리됐지만 수량을 갱신하지 못했습니다. 페이지를 새로고침해 주세요.', 'error');
                }
            } else if (cancelling && response.status === 409) {
                notify('재고가 없어 제작을 취소할 수 없습니다.', 'error');
            } else if (response.status === 401) {
                notify('로그인이 필요합니다. 로그인 상태를 확인해 주세요.', 'error');
            } else if (response.status === 403) {
                notify('권한 또는 보안 토큰을 확인해 주세요. 페이지를 새로고침해 주세요.', 'error');
            } else {
                notify(cancelling ? '제작 취소에 실패했습니다. 처리 상태를 확인해 주세요.' : '제작 등록에 실패했습니다. 처리 상태를 확인해 주세요.', 'error');
            }
        } catch {
            notify('처리 결과를 확인하지 못했습니다. 로그인 상태와 제작 기록을 확인한 뒤 다시 시도해 주세요.', 'error');
        } finally {
            pending = false;
            buttons.forEach(item => { item.disabled = false; });
            sizeButtons.forEach(item => { item.disabled = false; });
            root.removeAttribute('aria-busy');
            if (sseRefreshQueued) {
                sseRefreshQueued = false;
                refreshFromSse();
            }
        }
    });
})();
