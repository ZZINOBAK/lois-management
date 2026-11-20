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
    document.addEventListener('reservation:feedback', event => notify(event.detail.message, event.detail.success));
    let cancelDialogBusy = false;
    function selectCancelReason(dialog) {
        const buttons = [...dialog.querySelectorAll('button')];
        buttons.forEach(button => { button.disabled = false; });
        dialog.showModal();
        return new Promise(resolve => {
            function finish(reason) {
                dialog.removeEventListener('click', onClick);
                dialog.removeEventListener('cancel', onCancel);
                dialog.removeEventListener('close', onClose);
                if (reason) buttons.forEach(button => { button.disabled = true; });
                resolve(reason);
            }
            function onClick(event) {
                const option = event.target.closest('[data-cancel-reason]');
                if (option && !option.disabled) finish(option.dataset.cancelReason);
                else if (event.target.closest('[data-cancel-dismiss]')) finish(null);
            }
            function onCancel(event) { event.preventDefault(); finish(null); }
            function onClose() { finish(null); }
            dialog.addEventListener('click', onClick);
            dialog.addEventListener('cancel', onCancel);
            dialog.addEventListener('close', onClose);
        });
    }
    // 픽업 30분 전부터 아직 제작 필요인 오늘 예약 행을 강조한다. (제작 완료·픽업 완료는 제외)
    const DUE_SOON_MS = 30 * 60 * 1000;
    function localDate(date) {
        const pad = value => String(value).padStart(2, '0');
        return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate());
    }
    function updateDueSoonRows() {
        const now = new Date();
        const today = localDate(now);
        for (const row of document.querySelectorAll('[data-reservation-row]')) {
            const summary = row.querySelector('[data-reservation-summary]');
            if (!summary) continue;
            const { status, resDate, resTime } = row.dataset;
            const ready = row.querySelector('[data-reservation-pickup]')?.classList.contains('production-ready-btn');
            let dueSoon = false;
            if (status === 'RESERVED' && !ready && resDate === today && resTime) {
                const [hour, minute] = resTime.split(':').map(Number);
                const pickupAt = new Date(now);
                pickupAt.setHours(hour, minute, 0, 0);
                dueSoon = now.getTime() >= pickupAt.getTime() - DUE_SOON_MS;
            }
            summary.classList.toggle('due-soon', dueSoon);
        }
    }
    updateDueSoonRows();
    // 시간이 지나 30분 전이 되는 행을 잡기 위해 5분마다 다시 계산한다. (상태 변경은 이벤트로 즉시 반영)
    setInterval(updateDueSoonRows, 5 * 60 * 1000);
    document.addEventListener('htmx:afterSettle', event => {
        if (event.detail.elt?.matches('[data-ready-reservation-ids]')) {
            const card = document.querySelector('[data-ready-reservation-ids]');
            if (card) {
                const readyIds = new Set((card.dataset.readyReservationIds || '').split(','));
                for (const row of document.querySelectorAll('[data-reservation-row]')) {
                    row.querySelector('[data-reservation-pickup]')?.classList.toggle('production-ready-btn',
                        row.dataset.status === 'RESERVED' && readyIds.has(row.dataset.reservationId));
                    const label = row.querySelector('[data-pickup-label]');
                    if (label) label.textContent = row.dataset.status === 'PICKED_UP' ? '픽업 완료' :
                        row.dataset.status === 'RESERVED' ? (readyIds.has(row.dataset.reservationId) ? '제작 완료' : '제작 필요') : '-';
                }
            }
        }
        // 목록 교체·행 교체·제작 상태 갱신 뒤 강조 여부를 다시 계산한다.
        updateDueSoonRows();
    });
    document.addEventListener('click', async event => {
        const button = event.target.closest('[data-reservation-pickup]');
        if (!button || button.disabled) return;
        const row = button.closest('[data-reservation-row]');
        const id = row.dataset.reservationId;
        if (pendingIds.has(id)) return;
        const cancel = row.dataset.status === 'PICKED_UP';
        if (cancel && cancelDialogBusy) return;
        if (!cancel && !window.confirm('픽업을 완료할까요?')) return;
        pendingIds.add(id);
        button.disabled = true;
        let sent = false;
        let dialog;
        const preventDismiss = event => event.preventDefault();
        try {
            let cancelReason;
            if (cancel) {
                cancelDialogBusy = true;
                dialog = document.querySelector('[data-pickup-cancel-dialog]');
                cancelReason = await selectCancelReason(dialog);
                if (!cancelReason) return;
                dialog.addEventListener('cancel', preventDismiss);
            }
            const token = await ensureToken();
            sent = true;
            const response = await fetch(cancel ? button.dataset.cancelUrl : button.dataset.pickupUrl, {
                method: 'POST', credentials: 'same-origin', redirect: 'error',
                headers: { Authorization: 'Bearer ' + token },
                ...(cancel ? { body: new URLSearchParams({ cancelReason }) } : {})
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
            currentButton.classList.toggle('production-ready-btn', false);
            currentButton.querySelector('[data-pickup-label]').textContent = cancel ? '제작 필요' : '픽업 완료';
            const time = currentRow.querySelector('[data-pickup-time]');
            if (time) time.hidden = true;
            updateDueSoonRows();
            const already = response.headers.get(cancel ? 'Pickup-Cancel-Result' : 'Pickup-Result') === 'already-completed';
            notify(already ? '이미 처리된 요청입니다.' : cancel ? '픽업을 취소했습니다.' : '픽업을 완료했습니다.', true);
            // 카드의 기존 HTMX 갱신 경로를 사용한다. 픽업 변경 요청은 재전송하지 않는다.
            window.htmx.trigger(document.body, 'productionStatusChanged');
        } catch (error) {
            notify(sent ? '처리 결과를 확인할 수 없습니다. 새로고침하여 상태를 확인해 주세요.' : '인증을 준비하지 못했습니다. 로그인 상태를 확인해 주세요.');
        } finally {
            if (cancel) {
                if (dialog?.open) dialog.close();
                dialog?.removeEventListener('cancel', preventDismiss);
                cancelDialogBusy = false;
            }
            pendingIds.delete(id);
            button.disabled = false;
        }
    });
})();
