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
  alert("Demo에서는 예약과 발주 흐름만 둘러볼 수 있습니다.");
}
