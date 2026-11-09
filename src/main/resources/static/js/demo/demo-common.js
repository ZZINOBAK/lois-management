document.addEventListener("DOMContentLoaded", () => {
  document.body.addEventListener("htmx:configRequest", (event) => {
    const token = document.querySelector('meta[name="_csrf"]')?.content;
    const header = document.querySelector('meta[name="_csrf_header"]')?.content;
    if (token && header) {
      event.detail.headers[header] = token;
    }
  });
});

function showPreparing() {
  alert("준비 중입니다.");
}
