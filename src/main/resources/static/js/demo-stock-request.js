function sendStockRequestsSms(btn) {
  const wrapper = btn.closest(".request-actions");
  const count = Number(wrapper?.dataset?.count ?? 0);
  if (count <= 0) {
    alert("보낼 내용이 없습니다.");
    return;
  }
  alert(`Demo에서는 문자 발송 없이 ${count}건의 발주 목록만 확인합니다.`);
}

function openStockRequestsPrint(btn) {
  const wrapper = btn.closest(".request-actions");
  const count = Number(wrapper?.dataset?.count ?? 0);
  if (count <= 0) {
    alert("프린트할 주문 요청이 없습니다.");
    return;
  }
  if (confirm("프린트 하시겠습니까?")) {
    window.open("/demo/stock-requests/print", "_blank", "noopener,noreferrer");
  }
}
