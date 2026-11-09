    function toggleDatePicker() {
      const pop = document.getElementById('date-popover');
      pop.classList.toggle('hidden');

      if (!pop.classList.contains('hidden')) {
        const input = document.getElementById('pickDate');

        // 렌더 완료 직후 포커스 + 피커 강제 오픈
        requestAnimationFrame(() => {
          if (!input) return;
          input.focus({ preventScroll: true });
          if (typeof input.showPicker === 'function') {
            input.showPicker();        // 크롬/사파리(16.4+) 등 최신 브라우저
          } else {
            input.click();             // 폴백
          }
        });
      }
    }

    // (선택) 외부 클릭 시 닫기 로직을 쓰고 있다면, 버튼 클릭이 외부로 인식되지 않게 처리
    document.addEventListener('click', function (e) {
      const pop = document.getElementById('date-popover');
      const btn = e.target.closest('[onclick="toggleDatePicker()"]');
      if (!pop || pop.classList.contains('hidden')) return;
      if (!pop.contains(e.target) && !btn) pop.classList.add('hidden');
    });



    let pendingDeleteUrl = null;

    function openConfirmModal(url) {

        pendingDeleteUrl = url;
        document.getElementById("confirm-modal").classList.remove("hidden");
    }

    function closeConfirmModal() {
        pendingDeleteUrl = null;
        document.getElementById("confirm-modal").classList.add("hidden");
    }

    document.getElementById("confirm-yes").addEventListener("click", function() {
        if (pendingDeleteUrl) {
            htmx.ajax('DELETE', pendingDeleteUrl, {
                target: '#list',
                swap: 'innerHTML'
            });
        }
        closeConfirmModal();
    });

    document.getElementById("confirm-no").addEventListener("click", closeConfirmModal);

