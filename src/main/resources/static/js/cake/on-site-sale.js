(() => {
    const form = document.querySelector('[data-on-site-sale]');
    if (!form) return;
    const submit = form.querySelector('button[type="submit"]');
    const feedback = form.querySelector('[data-sale-feedback]');
    let pending = false;

    function inform(message) {
        feedback.textContent = message;
        feedback.hidden = false;
    }

    form.addEventListener('submit', async event => {
        event.preventDefault();
        if (pending || !form.reportValidity()) return;
        pending = true;
        submit.disabled = true;
        form.setAttribute('aria-busy', 'true');
        try {
            const fields = new FormData(form);
            const body = new URLSearchParams({
                cake_id: fields.get('cake_id'),
                cake_size: fields.get('cake_size')
            });
            const response = await window.secureFetch(form.action, {
                method: 'POST', redirect: 'error',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body
            });
            if (response.status === 204) {
                inform('현장 판매 1건을 처리했습니다.');
                form.reset();
            } else if (response.status === 400) {
                inform('케이크와 호수를 확인해 주세요.');
            } else if (response.status === 401) {
                inform('로그인이 필요합니다.');
            } else if (response.status === 403) {
                inform('권한 또는 보안 토큰을 확인해 주세요. 페이지를 새로고침해 주세요.');
            } else {
                inform('판매 처리 결과를 확인하지 못했습니다. 판매 기록을 확인한 뒤 다시 시도해 주세요.');
            }
        } catch {
            inform('판매 처리 결과를 확인하지 못했습니다. 로그인 상태와 판매 기록을 확인한 뒤 다시 시도해 주세요.');
        } finally {
            pending = false;
            submit.disabled = false;
            form.removeAttribute('aria-busy');
        }
    });
})();
