(() => {
    const pendingIds = new Set();
    let toastTimer;
    function notify(message, success = false) {
        clearTimeout(toastTimer);
        const feedback = document.querySelector('[data-pickup-feedback]');
        if (feedback) {
            feedback.textContent = message;
            feedback.hidden = false;
            feedback.dataset.kind = success ? "success" : "error";
            if (success) toastTimer = setTimeout(() => { feedback.hidden = true; }, 2500);
        }
    }
    document.addEventListener('click', async event => {
        const button = event.target.closest('[data-reservation-pickup]');
        if (!button || button.disabled) return;
        const row = button.closest('[data-reservation-row]');
        const id = row.dataset.reservationId;
        if (pendingIds.has(id)) return;
        const cancel = row.dataset.status === 'PICKED_UP';
        if (!window.confirm(cancel ? '픽업을 취소할까요?' : '픽업을 완료할까요?')) return;
        pendingIds.add(id);
        button.disabled = true;
        let sent = false;
        try {
            const token = await ensureToken();
            sent = true;
            const response = await fetch(cancel ? button.dataset.cancelUrl : button.dataset.pickupUrl, {
                method: 'POST', credentials: 'same-origin', redirect: 'error',
                headers: { Authorization: 'Bearer ' + token }
            });
            if (response.status !== 204) {
                if (response.status === 401) clearToken();
                notify(response.status === 401 ? '인증이 만료되었습니다. 다시 요청해 주세요.' :
                    response.status === 403 ? '픽업 처리 권한이 없습니다.' :
                    [400, 404, 409].includes(response.status) ? '예약 상태를 확인한 뒤 다시 요청해 주세요.' :
                    '처리 결과를 확인할 수 없습니다. 새로고침하여 상태를 확인해 주세요.');
                return;
            }
            const currentRow = document.getElementById('row-block-' + id) || row;
            const currentButton = currentRow.querySelector('[data-reservation-pickup]');
            currentRow.dataset.status = cancel ? 'RESERVED' : 'PICKED_UP';
            currentRow.querySelector('[data-reservation-summary]').classList.toggle('picked', !cancel);
            currentButton.classList.toggle('picked-btn', !cancel);
            currentButton.querySelector('[data-pickup-label]').textContent = cancel ? '픽업예정' : '픽업완료';
            const time = currentRow.querySelector('[data-pickup-time]');
            if (time) time.hidden = true;
            const already = response.headers.get(cancel ? 'Pickup-Cancel-Result' : 'Pickup-Result') === 'already-completed';
            notify(already ? '이미 처리된 요청입니다.' : cancel ? '픽업을 취소했습니다.' : '픽업을 완료했습니다.', true);
        } catch (error) {
            notify(sent ? '처리 결과를 확인할 수 없습니다. 새로고침하여 상태를 확인해 주세요.' : '인증을 준비하지 못했습니다. 로그인 상태를 확인해 주세요.');
        } finally {
            pendingIds.delete(id);
            button.disabled = false;
        }
    });
})();
