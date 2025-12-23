function toggleDetailRow(event, id) {
    // 버튼이나 버튼 안쪽을 클릭한 경우는 상세 토글 안함
    if (event.target.closest('button')) {
        return;
    }
    const detail = document.getElementById('detail-' + id);
    if (detail) {
    detail.classList.toggle('hidden');
    }
}


function updateSortButtons(range) {
  const container = document.getElementById('sorts');
    if (!container) return;

    // ✅ 전체(all) 또는 비어있으면 전부 숨김
    if (!range || range === 'all') {
      container.querySelectorAll('[data-show]').forEach(btn => btn.classList.add('hidden'));
      return;
    }

    container.querySelectorAll('[data-show]').forEach(btn => {
      const allow = (btn.dataset.show || "").split(/\s+/).filter(Boolean);
      btn.classList.toggle('hidden', !allow.includes(range));
    });
}

document.body.addEventListener('htmx:beforeRequest', (e) => {
  const current = sessionStorage.getItem('reservationRange') || "all";
    updateSortButtons(current);
});

document.addEventListener('DOMContentLoaded', () => {
   // ✅ 현재 페이지가 /reservations이면 무조건 전체 초기화
    if (window.location.pathname === '/reservations') {
      sessionStorage.removeItem('reservationRange');
      updateSortButtons('all');
      return;
    }

    updateSortButtons(sessionStorage.getItem('reservationRange') || "");
});

document.body.addEventListener('htmx:configRequest', (e) => {
  const elt = e.detail.elt;
    const hxGet = elt?.getAttribute('hx-get') || elt?.getAttribute('data-hx-get') || "";

    // 1) 이번 요청의 range를 "parameters" 또는 "hx-get 쿼리"에서 추출
    let incomingRange = e.detail.parameters?.range;

    if ((!incomingRange || String(incomingRange).trim() === "") && hxGet.includes("range=")) {
      try {
        const u = new URL(hxGet, window.location.origin);
        incomingRange = u.searchParams.get("range");
      } catch {}
    }

    // 2) 들어온 range로 sessionStorage 갱신 (전체면 초기화)
    if (typeof incomingRange === "string") {
      const r = incomingRange.trim();

      if (r === "all") {
        sessionStorage.removeItem("reservationRange");
        sessionStorage.removeItem("reservationDate");
      } else if (r) {
        sessionStorage.setItem("reservationRange", r);
      }
    }

    // 3) /reservations/list 요청이면 저장된 range/date를 강제 주입
    if (hxGet.includes("/reservations/list")) {
      const savedRange = sessionStorage.getItem("reservationRange") || "all";
      e.detail.parameters["range"] = savedRange;

      if (savedRange === "date") {
        const savedDate = sessionStorage.getItem("reservationDate");
        if (savedDate) e.detail.parameters["date"] = savedDate;
      }
    }
});