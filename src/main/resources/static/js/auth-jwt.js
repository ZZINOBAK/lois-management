// auth-jwt.js
const KEY = "lois_access_token";
let refreshing = null;

function isAuthenticated() {
  return document.querySelector('meta[name="is-authenticated"]')?.content === 'true';
}

function csrfHeaders() {
  const token = document.querySelector('meta[name="_csrf"]')?.content;
  const header = document.querySelector('meta[name="_csrf_header"]')?.content;
  return (token && header) ? { [header]: token } : {};
}

async function issueToken() {
  console.log("[이슈토큰]");

  const res = await fetch("/auth/token", {
    method: "POST",
    credentials: "same-origin",
    headers: csrfHeaders()
  });

  if (!res.ok) throw new Error("issue token failed: " + res.status);

  const data = await res.json();
  sessionStorage.setItem(KEY, data.accessToken);
  return data.accessToken;
}

function getToken() {
                console.log("[겟토큰]");

  return sessionStorage.getItem(KEY);
}

function clearToken() {
  sessionStorage.removeItem(KEY);
}

async function ensureToken() {
  console.log("[인슈어토큰]");

  if (!isAuthenticated()) {
    clearToken();
    throw new Error("NOT_AUTHENTICATED");
  }

  const existing = getToken();
  if (existing) return existing;

  // 동시 호출 방지
  if (!refreshing) {
    // 토큰 재발급
    refreshing = issueToken().finally(() => (refreshing = null));
  }
  return refreshing;
}

/* ✅ 0) 최초 진입 시: 로그인 아니면 토큰만 정리 (발급 X) */
document.addEventListener("DOMContentLoaded", () => {
  if (!isAuthenticated()) clearToken();
});

/* ✅ 1) HTMX 요청에 Authorization 자동 주입 */
document.addEventListener("DOMContentLoaded", () => {

  // 1-A) 토큰이 있으면 그냥 주입
  document.body.addEventListener("htmx:configRequest", function (evt) {
    const token = getToken();
    if (token) {
      evt.detail.headers["Authorization"] = "Bearer " + token;
    }

    // 2) CSRF (추가)
      const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
      const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;
      if (csrfToken && csrfHeader) {
        evt.detail.headers[csrfHeader] = csrfToken;
      }
  });

  // 1-B) 요청 직전: 토큰이 없으면 발급하고 1회 재요청
  document.body.addEventListener("htmx:beforeRequest", async function (evt) {
    // 이미 토큰 있으면 통과
    if (getToken()) return;

    // 로그인도 아닌데 HTMX 요청이면 그냥 로그인으로
    if (!isAuthenticated()) {
      clearToken();
      location.href = "/login?auth=required";
      return;
    }

    // 무한루프 방지 (같은 요청 2번 막기)
    // 2. 아래 1번 코드를 지난 후 다시 온 경우 evt.detail.xhr.__issuedBefore의 값이 true임으로 return해버림.
    if (evt.detail.xhr && evt.detail.xhr.__issuedBefore) return;
    // 1. 현재 HTMX 객체가 있다면, evt.detail.xhr.__issuedBefore의 값을 true로.
    if (evt.detail.xhr) evt.detail.xhr.__issuedBefore = true;

    // 지금 요청은 취소하고, 토큰 확보 후 동일 요청을 다시 보냄
    // HTMX야, 네가 지금 하려던 ‘자동 요청 보내기’는 하지 마
    evt.preventDefault();

    try {
      await ensureToken();

      const req = evt.detail.requestConfig;
      // 동일 요청 재실행
      htmx.ajax(req.verb, req.path, {
        target: req.target,
        swap: req.swap || "outerHTML",
        values: req.parameters, // form values
        headers: req.headers    // configRequest에서 Authorization이 붙음
      });
    } catch (e) {
      // 토큰 지우기
      clearToken();
      // 로그인 페이지로 이동
      location.href = "/login?auth=required";
    }
  });

    /* ✅ 2) 401 → 토큰 재발급 → 1회 재시도 */
    // 응답 이후 스왑하기 전 응답코드가 401(인증안됨)인 경우, 토큰 재발급
    // 여기로 오는 요청에 대한 응답은 jwt 인증이라서 세션이랑 상관없이 토큰 인증 안되면 401 뜸
    document.body.addEventListener("htmx:beforeSwap", async function (evt) {
      const xhr = evt.detail.xhr;
      if (xhr.status !== 401) return;

      evt.detail.shouldSwap = false;
      evt.detail.isError = false;

      // 2. 다시 여기로 왔을때, xhr.__retried의 값이 true임으로 if문 안으로 들어감.
      if (xhr.__retried) {
        // 토큰 지우기
        clearToken();
        // 로그인 페이지로 이동
        location.href = "/login?auth=required";
        return;
      }
      // 1. 여기서 xhr.__retried의 값을 true로
      xhr.__retried = true;
      // 토큰 지우기
      clearToken();

      try {
        console.log("[401 HANDLER] 트라이 진입");
        console.log("[401 HANDLER] before ensureToken");

        const newToken = await ensureToken();
        console.log("[401 HANDLER] ensureToken OK", newToken);

        // ✅ Safari에선 xhr.method가 undefined일 수 있음 → requestConfig에서 가져오기
        const cfg = evt.detail.requestConfig || {};
        const method = (cfg.verb || cfg.method || "GET").toUpperCase();
        const path   = cfg.path || xhr.responseURL; // 가능하면 cfg.path 우선

        // ✅ 핵심: 원래 요청을 발생시킨 요소(버튼/폼)를 잡아서 폼데이터 다시 수집
        const elt = evt.detail.elt || cfg.elt; // HTMX가 제공하는 트리거 element
        const sourceForm = elt?.closest?.("form") || elt;

        // ✅ 원래 swap/target 복구 (여기가 제일 중요)
        const target = cfg.target; // 원래 hx-target
        const swap   = cfg.swapStyle || cfg.swap || "outerHTML"; // 원래 hx-swap

        // source를 못 잡으면 재시도 의미 없음 → 로그인으로
        if (!sourceForm) {
           clearToken();
           location.href = "/login?auth=required";
           return;
        }

        // ✅ headers는 기존 + Authorization 갱신
        const headers = Object.assign({}, cfg.headers || {});
        headers["Authorization"] = "Bearer " + newToken;

        // ✅ 재요청: source를 주면 HTMX가 form 값(itemId 등)을 다시 모아서 보냄
        htmx.ajax(method, path, {
             source: sourceForm,     // 🔥 이게 바디 복구 핵심
             target,                 // 🔥 DOM 교체 위치
             swap,                   // 🔥 DOM 교체 방식
             values: cfg.parameters, // 필요 시(특히 hx-vals/form) 전달
             headers
        });
        console.log("[401 HANDLER] retry request sent successfully");

      } catch (e) {
        console.log("[401 HANDLER] FAILED", e);

        clearToken();
        location.href = "/login?auth=required";
      }
    });

    document.body.addEventListener("htmx:responseError", async (evt) => {
      const xhr = evt.detail.xhr;

      // 401: 토큰 재발급 로직(기존)
      if (xhr.status === 401) {
        // 너의 refresh + htmx.ajax 재시도 로직 호출
        return;
      }

      // 403: 보통 1) CSRF 2) 권한 3) 세션만료
      if (xhr.status === 403) {
        alert("다시 로그인 해주세요.");
        location.href = "/login?auth=required";
      }
    });
});

