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
  document.querySelectorAll('.filters [data-range]').forEach(btn => {
    btn.setAttribute('aria-pressed', String(btn.dataset.range === range));
  });
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
   // 기본 진입 화면과 정렬 요청의 범위를 오늘로 맞춘다.
    if (window.location.pathname === '/reservations') {
      sessionStorage.setItem('reservationRange', 'today');
      sessionStorage.removeItem('reservationDate');
      updateSortButtons('today');
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

    updateSortButtons(sessionStorage.getItem("reservationRange") || "all");

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


function togglePlanCard() {
  const body = document.getElementById("planCardBody");
  const chevron = document.getElementById("planCardChevron");
  if (!body) return;

  body.classList.toggle("hidden");

  // 화살표 방향만 바꾸기(선택)
  if (chevron) {
    chevron.textContent = body.classList.contains("hidden") ? "▸" : "▾";
  }

  // 접힘 상태 저장(선택)
  sessionStorage.setItem("planCardHidden", body.classList.contains("hidden") ? "1" : "0");
}

// 페이지 로드 시 접힘 상태 복원(선택)
document.addEventListener("DOMContentLoaded", () => {
  const body = document.getElementById("planCardBody");
  const chevron = document.getElementById("planCardChevron");
  if (!body) return;

  const hidden = sessionStorage.getItem("planCardHidden") === "1";
  if (hidden) body.classList.add("hidden");
  if (chevron) chevron.textContent = hidden ? "▸" : "▾";
});


// 현재 페이지에서만 유지하며, 새로고침 시 모두 접힌 상태로 시작한다.
const productionSizeExpanded = { One: false, Two: true }

function setProductionSizeExpanded(size, expanded) {
    const body = document.getElementById(`size${size}Body`)
    const chevron = document.getElementById(`size${size}Chevron`)
    if (!body || !chevron) return

    productionSizeExpanded[size] = expanded
    body.style.display = expanded ? 'grid' : 'none'
    chevron.textContent = expanded ? '▴' : '▾'
}

function toggleSizeTwo() {
    setProductionSizeExpanded('Two', !productionSizeExpanded.Two)
}

function toggleSizeOne() {
    setProductionSizeExpanded('One', !productionSizeExpanded.One)
}

document.body.addEventListener('htmx:afterSettle', (e) => {
    // outerHTML 교체의 style 정리가 끝난 제작 현황 section만 복원한다.
    if (!e.detail.elt?.matches('section.plan-card[hx-get="/reservations/production-status"]')) return

    setProductionSizeExpanded('One', productionSizeExpanded.One)
    setProductionSizeExpanded('Two', productionSizeExpanded.Two)
})


// 실시간 변경 알림(sse:changed) 시 마지막 목록 조회와 제작 현황을 다시 불러온다.
const SSE_REFRESH_HEADER = 'X-SSE-Refresh'
const SSE_RETRY_MS = 500
let lastListRequest = null
let inFlightRequests = 0
let sseRetryTimer = null
let sseRestoreDetailIds = []

document.body.addEventListener('htmx:configRequest', (e) => {
    // 위 핸들러가 range/date를 주입한 뒤의 목록 조회만 기억한다.
    if (e.detail.target?.id !== 'list' || String(e.detail.verb || '').toUpperCase() !== 'GET') return
    lastListRequest = { path: e.detail.path, parameters: { ...e.detail.parameters } }
})
document.body.addEventListener('htmx:beforeSend', () => { inFlightRequests++ })
document.body.addEventListener('htmx:afterRequest', () => { inFlightRequests = Math.max(inFlightRequests - 1, 0) })

function defaultListRequest() {
    const range = sessionStorage.getItem('reservationRange') || 'all'
    const parameters = { range }
    const date = sessionStorage.getItem('reservationDate')
    if (range === 'date' && date) parameters.date = date
    return { path: '/reservations/list', parameters }
}

// 모달·다이얼로그·날짜 선택 중이거나 요청 처리 중이면 갱신을 미룬다.
function isDashboardBusy() {
    if (inFlightRequests > 0) return true
    if (document.getElementById('confirm-modal')?.classList.contains('hidden') === false) return true
    if (document.getElementById('date-popover')?.classList.contains('hidden') === false) return true
    return Boolean(document.querySelector('[data-pickup-cancel-dialog]')?.open)
}

function refreshDashboardFromSse() {
    if (!window.htmx || !document.getElementById('list')) return
    clearTimeout(sseRetryTimer)
    if (isDashboardBusy()) {
        sseRetryTimer = setTimeout(refreshDashboardFromSse, SSE_RETRY_MS)
        return
    }
    sseRestoreDetailIds = [...document.querySelectorAll('#list .detail-row:not(.hidden)')].map(row => row.id)
    const request = lastListRequest || defaultListRequest()
    htmx.ajax('GET', request.path, {
        target: '#list', swap: 'innerHTML', values: request.parameters,
        headers: { [SSE_REFRESH_HEADER]: 'true' }
    })
    htmx.trigger(document.body, 'productionStatusChanged')
}

document.body.addEventListener('htmx:afterSwap', (e) => {
    // 실시간 갱신 전에 펼쳐 두었던 상세 행을 다시 펼친다.
    if (e.detail.target?.id !== 'list' || !e.detail.requestConfig?.headers?.[SSE_REFRESH_HEADER]) return
    for (const id of sseRestoreDetailIds) document.getElementById(id)?.classList.remove('hidden')
    sseRestoreDetailIds = []
})

document.addEventListener('sse:changed', refreshDashboardFromSse)
