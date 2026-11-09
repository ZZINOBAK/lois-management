const KEY = "lois_access_token";
let refreshing = null;

//인증된 사용자 확인
function isAuthenticated() {
  return document.querySelector('meta[name="is-authenticated"]')?.content === 'true';
}

//csrf 토큰 여부 확인
function csrfHeaders() {
  const token = document.querySelector('meta[name="_csrf"]')?.content;
  const header = document.querySelector('meta[name="_csrf_header"]')?.content;
  return (token && header) ? { [header]: token } : {};
}

//토큰 발급: 비동기
async function issueToken() {
  console.log("[토큰 발급 시작]");
  const res = await fetch("/auth/token", {
    method: "POST",
    headers: csrfHeaders()
  });

  if (!res.ok) throw new Error("토큰 발급 실패: " + res.status);

  const data = await res.json();
  sessionStorage.setItem(KEY, data.accessToken);

  return data.accessToken;
}

//세션스토리지에서 토큰값 가져오기
function getToken() {
  console.log("[토큰값 가져오기]");

  return sessionStorage.getItem(KEY);
}

//토큰 삭제하기
function clearToken() {
  sessionStorage.removeItem(KEY);
}

//jwt 토큰 조회 및 발급
async function ensureToken() {
  console.log("[jwt 토큰 조회 및 발급]");

  //인증되지 않은 사용자 접근 시 토큰 삭제하기
  if (!isAuthenticated()) {
    clearToken();
    throw new Error("인증되지 않은 사용자: 토큰 삭제");
  }

  //이미 토큰이 있으면 함수 종료
  const existing = getToken();
  if (existing) return existing;

  //동시 호출 방지: 리프레싱에 값이 없는 경우 실행
  if (!refreshing) {
    //토큰 발급
    refreshing = issueToken().finally(() => (refreshing = null));
  }
  return refreshing;
}

//HTMX용 함수 4가지 이벤트 등록
function initHtmxAuth() {
  document.body.addEventListener("htmx:configRequest", handleConfigRequest);
  document.body.addEventListener("htmx:beforeRequest", handleBeforeRequest);
  document.body.addEventListener("htmx:beforeSwap", handleBeforeSwap);
  document.body.addEventListener("htmx:responseError", handleResponseError);
}

//HTMX 요청 발생 시 실행
function handleConfigRequest(evt) {
  //jwt 토큰 있으면 헤더에 싣기
  const token = getToken();
  if (token) {
    evt.detail.headers["Authorization"] = "Bearer " + token;
  }
  // csrf 토큰 있으면 헤더에 싣기
  const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;
  if (csrfToken && csrfHeader) {
    evt.detail.headers[csrfHeader] = csrfToken;
  }
}

//HTMX 요청 직전 실행: jwt 토큰 없으면 발급하고 1회 재요청
async function handleBeforeRequest(evt) {
  //이미 jwt 토큰 있으면 함수 종료
  if (getToken()) return;

  //인증 안된 사용자 접근 시 jwt 토큰 삭제 후 로그인 화면으로 이동
  if (!isAuthenticated()) {
    clearToken();
    location.href = "/login?auth=required";
    return;
  }

  //무한루프 방지 (같은 요청 2번 막기)
  //HTMX 요청의 xhr 객체가 있고, __issuedBefore 값이 true면 함수 종료
  if (evt.detail.xhr && evt.detail.xhr.__issuedBefore) return;
  //HTMX 요청의 xhr 객체가 있다면, __issuedBefore 값을 true로 대입
  if (evt.detail.xhr) evt.detail.xhr.__issuedBefore = true;


  //HTMX 요청 취소: jwt 토큰이 없고, 인증된 사용자이고, __issuedBefore 값이 없을 때 실행
  evt.preventDefault();

  try {
    //jwt 토큰 여부 확인 후 jwt 토큰 발급하기
    await ensureToken();

    const req = evt.detail.requestConfig;
    //동일 요청 재실행
    htmx.ajax(req.verb, req.path, {
      target: req.target,
      swap: req.swap || "outerHTML",
      values: req.parameters, // form values
      headers: req.headers    // configRequest에서 Authorization이 붙음
    });
  } catch (e) {
    //토큰 삭제
    clearToken();
    //로그인 페이지로 이동
    location.href = "/login?auth=required";
  }
}

//HTMX 요청에 대한 서버 응답 후 실행: 응답 코드가 401(인증 안됨)이면 jwt 토큰 발급하고 1회 재요청
async function handleBeforeSwap(evt) {
  const xhr = evt.detail.xhr;
  if (xhr.status !== 401) return;

  evt.detail.shouldSwap = false;
  evt.detail.isError = false;

  if (xhr.__retried) {
    //jwt 토큰 삭제
    clearToken();
    // 로그인 페이지로 이동
    location.href = "/login?auth=required";
    return;
  }
  xhr.__retried = true;

  //jwt 토큰 삭제
  clearToken();

  try {
    console.log("[HTMX 응답: 401 HANDLER]");
    const newToken = await ensureToken();
    console.log("jwt 토큰 재발급 완료", newToken);

    //Safari에선 xhr.method가 undefined일 수 있음 → requestConfig에서 가져오기
    const cfg = evt.detail.requestConfig || {};
    const method = (cfg.verb || cfg.method || "GET").toUpperCase();
    const path = cfg.path || xhr.responseURL; // 가능하면 cfg.path 우선

    //핵심: 원래 요청을 발생시킨 요소(버튼/폼)를 잡아서 폼데이터 다시 수집
    const elt = evt.detail.elt || cfg.elt; // HTMX가 제공하는 트리거 element
    const sourceForm = elt?.closest?.("form") || elt;

    //원래 swap/target 복구
    const target = cfg.target; // 원래 hx-target
    const swap = cfg.swapStyle || cfg.swap || "outerHTML"; // 원래 hx-swap

    //source를 못 잡으면 재시도 의미 없음 → 로그인으로
    if (!sourceForm) {
      clearToken();
      location.href = "/login?auth=required";
      return;
    }

    //headers는 기존 + Authorization 갱신
    const headers = Object.assign({}, cfg.headers || {});
    headers["Authorization"] = "Bearer " + newToken;

    //재요청: source를 주면 HTMX가 form 값(itemId 등)을 다시 모아서 보냄
    htmx.ajax(method, path, {
      source: sourceForm,
      target,
      swap,
      values: cfg.parameters,
      headers
    });
    console.log("[401 HANDLER] 재요청 전송 완료");

  } catch (e) {
    console.log("[401 HANDLER] 재요청 전송 실패", e);

    clearToken();
    location.href = "/login?auth=required";
  }
}

//HTMX 요청 에러 발생 시 실행
async function handleResponseError(evt) {
  const xhr = evt.detail.xhr;

  // 401: 토큰 재발급 로직
  if (xhr.status === 401) {
    return;
  }

  // 403: CSRF, 권한, 세션만료
  if (xhr.status === 403) {
    alert("다시 로그인 해주세요.");
    location.href = "/login?auth=required";
  }
}

//이벤트 등록: 인증된 사용자가 아닌 경우 토큰 삭제
document.addEventListener("DOMContentLoaded", () => {
  if (!isAuthenticated()) clearToken();
});

//이벤트 등록: HTMX용 함수
document.addEventListener("DOMContentLoaded", initHtmxAuth);

