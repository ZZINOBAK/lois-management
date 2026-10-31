(() => {
    const root = document.querySelector('[data-production-root]');
    if (!root) return;
    const toast = root.querySelector('[data-production-toast]');
    const buttons = [...root.querySelectorAll('[data-production-action="produce"], [data-production-action="cancel"]')];
    const needsList = root.querySelector('[data-production-needs]');
    const needsWarning = root.querySelector('[data-production-needs-warning]');
    let pending = false;
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

    function updateReservationReadiness(needs) {
        const remaining = new Map();
        for (const need of needs) {
            if (Number(need.cakeSize) === 2) {
                remaining.set(String(need.cakeId) + ':2', Math.max(Number(need.stockQuantity) || 0, 0));
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
            const ready = Number(data.cakeSize) === 2 && data.makeStatus === 'RESERVED'
                && data.pickupStatus !== 'PICKED' && available > 0;
            if (ready) remaining.set(key, available - 1);
            row.classList.toggle('is-production-ready', ready);
            row.classList.toggle('is-production-needed', !ready && data.pickupStatus === 'WAITING');
            const status = row.querySelector('.cake-production-reservation-status');
            status.textContent = ready ? '제작 완료' :
                (data.pickupStatus === 'PICKED' ? '픽업 완료' : (data.pickupStatus === 'WAITING' ? '제작 필요' : '-'));
        }
    }

    updateReservationReadiness([...root.querySelectorAll('[data-production-need]')].map(item => ({
        cakeId: item.dataset.cakeId, cakeSize: item.dataset.cakeSize, stockQuantity: item.dataset.stockQuantity
    })));

    async function refreshNeeds() {
        const response = await window.secureFetch(root.dataset.needsUrl, {
            method: 'GET', redirect: 'error', cache: 'no-store'
        });
        if (response.status !== 200) throw new Error('Quantity refresh failed');
        const needs = await response.json();
        if (!Array.isArray(needs)) throw new Error('Invalid quantity response');
        const fragment = document.createDocumentFragment();
        for (const need of needs) {
            const item = document.createElement('li');
            item.className = 'cake-production-need' + (need.requiredQuantity > 0 ? ' is-needed' : '');
            const name = document.createElement('span');
            name.textContent = need.cakeFlavor;
            const quantity = document.createElement('span');
            quantity.className = 'cake-production-need-quantity';
            quantity.textContent = need.requiredQuantity;
            item.append(name, quantity);
            fragment.append(item);
        }
        updateReservationReadiness(needs);
        needsList.replaceChildren(fragment);
        needsWarning.hidden = true;
    }

    root.addEventListener('click', async event => {
        const button = event.target.closest('[data-production-action]');
        if (!button || !root.contains(button) || pending) return;
        if (button.dataset.productionAction === 'other') {
            notify('기타 케이크 기능은 준비 중입니다.');
            return;
        }
        const cancelling = button.dataset.productionAction === 'cancel';
        pending = true;
        buttons.forEach(item => { item.disabled = true; });
        root.setAttribute('aria-busy', 'true');
        try {
            const body = new URLSearchParams({ cake_id: button.dataset.cakeId, cake_size: '2' });
            const response = await window.secureFetch(cancelling ? root.dataset.cancelUrl : root.dataset.produceUrl, {
                method: 'POST',
                redirect: 'error',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body
            });
            if (response.status === 204) {
                const successMessage = button.dataset.flavor + (cancelling ? ' 2호 1개 제작 취소' : ' 2호 1개 제작 완료');
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
            root.removeAttribute('aria-busy');
        }
    });
})();
