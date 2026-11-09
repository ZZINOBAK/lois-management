(function () {
  function getCsrf() {
    const token = document.querySelector('meta[name="_csrf"]')?.content;
    const header = document.querySelector('meta[name="_csrf_header"]')?.content;
    return (token && header) ? { token, header } : null;
  }

  function needsCsrf(method) {
    const m = (method || "GET").toUpperCase();
    return ["POST", "PUT", "PATCH", "DELETE"].includes(m);
  }

  // 전역 래퍼
  window.secureFetch = async function (url, options = {}) {
    const method = (options.method || "GET").toUpperCase();
    const headers = new Headers(options.headers || {});
    const credentials = options.credentials || "same-origin";

    if (needsCsrf(method)) {
      const csrf = getCsrf();
      if (csrf) headers.set(csrf.header, csrf.token);
    }

    return fetch(url, { ...options, method, headers, credentials });
  };

  // HTMX에도 CSRF 자동 주입
  document.addEventListener("DOMContentLoaded", () => {
    if (!window.htmx) return;

    document.body.addEventListener("htmx:configRequest", (evt) => {
      const method = (evt.detail.verb || "GET").toUpperCase();
      if (!needsCsrf(method)) return;

      const csrf = getCsrf();
      if (csrf) evt.detail.headers[csrf.header] = csrf.token;
    });
  });
})();