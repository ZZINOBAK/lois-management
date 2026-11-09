function sendStockRequestsSms(btn) {
    // 버튼 부모(.request-actions)에 심어둔 count 읽기
    const wrapper = btn.closest('.request-actions');
    const count = Number(wrapper?.dataset?.count ?? 0);

    if (count <= 0) {
      alert("보낼 내용이 없습니다.");
      return;
    }

    const ok = confirm(`비품 발주 요청 ${count}건을 문자로 전송하시겠습니까?`);
    if (!ok) return;

    secureFetch("/sms/send-from-stock-requests", { method: "POST" })
      .then(res => {
        if (!res.ok) throw new Error("서버 오류: " + res.status);
        return res.json();
      })
      .then(data => {
        // 서버가 status를 내려주면 그걸 기준으로 UX 개선 가능
        if (data.status === "SKIPPED") {
          alert(data.message || "보낼 내용이 없습니다.");
          return;
        }
        alert("문자 전송 완료!");
      })
      .catch(err => {
        console.error(err);
        alert("문자 전송 실패");
      });
}

        function openStockRequestsPrint(btn) {
            const wrapper = btn.closest('.request-actions');
            const count = Number(wrapper?.dataset?.count ?? 0);

            if (count <= 0) {
              alert("프린트할 주문 요청이 없습니다.");
              return;
            }

            const ok = confirm(`프린트 하시겠습니까?`);
            if (!ok) return;

            // 새 탭으로 프린트 전용 페이지
            window.open("/stock-requests/print", "_blank", "noopener,noreferrer");
          }
// 이미 요청한 아이템 안내
document.body.addEventListener("alreadyRequested", function () {
    alert("이미 요청된 아이템입니다!");
});

// 발주 요청 상세 접기
document.body.addEventListener("htmx:beforeRequest", function (event) {
    const btn = event.target;
    if (!btn.matches(".request-main")) return;

    const row = btn.closest(".request-row");
    const next = row.nextElementSibling;

    if (next && next.classList.contains("request-detail-card")) {
        next.remove();
        event.preventDefault();
    }
});