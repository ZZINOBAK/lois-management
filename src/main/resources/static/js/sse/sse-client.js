(() => {
    if (typeof EventSource === 'undefined') return;
    const DEBOUNCE_MS = 300;
    let timer = null;
    let disconnected = false;

    // 짧은 시간에 몰린 알림은 한 번만 전달한다.
    function notifyChanged() {
        clearTimeout(timer);
        timer = setTimeout(() => {
            timer = null;
            document.dispatchEvent(new CustomEvent('sse:changed'));
        }, DEBOUNCE_MS);
    }

    const source = new EventSource('/sse/subscribe');
    source.addEventListener('changed', notifyChanged);
    // 연결이 끊긴 동안 놓친 변경을 재연결 직후 한 번 반영한다.
    source.addEventListener('open', () => {
        if (!disconnected) return;
        disconnected = false;
        notifyChanged();
    });
    // 실패가 확정되어도(CLOSED) 별도 안내 없이 멈춘다.
    source.addEventListener('error', () => { disconnected = true; });

    // 탭 복귀·화면 켜짐 시 백그라운드 중 놓친 변경을 반영한다.
    document.addEventListener('visibilitychange', () => {
        if (document.visibilityState === 'visible') notifyChanged();
    });
})();
