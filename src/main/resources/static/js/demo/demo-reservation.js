function toggleDetailRow(event, id) {
  if (event.target.closest("button")) return;
  const detail = document.getElementById("detail-" + id);
  if (detail) detail.classList.toggle("hidden");
}

function updateSortButtons(range) {
  const container = document.getElementById("sorts");
  if (!container) return;

  if (!range || range === "all") {
    container.querySelectorAll("[data-show]").forEach((btn) => btn.classList.add("hidden"));
    return;
  }

  container.querySelectorAll("[data-show]").forEach((btn) => {
    const allow = (btn.dataset.show || "").split(/\s+/).filter(Boolean);
    btn.classList.toggle("hidden", !allow.includes(range));
  });
}

document.body.addEventListener("htmx:beforeRequest", () => {
  updateSortButtons(sessionStorage.getItem("demoReservationRange") || "all");
});

document.addEventListener("DOMContentLoaded", () => {
  if (window.location.pathname === "/demo/reservations") {
    sessionStorage.removeItem("demoReservationRange");
    updateSortButtons("all");
    return;
  }
  updateSortButtons(sessionStorage.getItem("demoReservationRange") || "");
});

document.body.addEventListener("htmx:configRequest", (event) => {
  const element = event.detail.elt;
  const hxGet = element?.getAttribute("hx-get") || element?.getAttribute("data-hx-get") || "";
  let incomingRange = event.detail.parameters?.range;

  if ((!incomingRange || String(incomingRange).trim() === "") && hxGet.includes("range=")) {
    try {
      incomingRange = new URL(hxGet, window.location.origin).searchParams.get("range");
    } catch {}
  }

  if (typeof incomingRange === "string") {
    const range = incomingRange.trim();
    if (range === "all") {
      sessionStorage.removeItem("demoReservationRange");
      sessionStorage.removeItem("demoReservationDate");
    } else if (range) {
      sessionStorage.setItem("demoReservationRange", range);
    }
  }

  if (hxGet.includes("/demo/reservations/list")) {
    const savedRange = sessionStorage.getItem("demoReservationRange") || "all";
    event.detail.parameters.range = savedRange;
    if (savedRange === "date") {
      const savedDate = sessionStorage.getItem("demoReservationDate");
      if (savedDate) event.detail.parameters.date = savedDate;
    }
  }
});

function togglePlanCard() {
  const body = document.getElementById("planCardBody");
  const chevron = document.getElementById("planCardChevron");
  if (!body) return;
  body.classList.toggle("hidden");
  if (chevron) chevron.textContent = body.classList.contains("hidden") ? "▸" : "▾";
  sessionStorage.setItem("demoPlanCardHidden", body.classList.contains("hidden") ? "1" : "0");
}

document.addEventListener("DOMContentLoaded", () => {
  const body = document.getElementById("planCardBody");
  const chevron = document.getElementById("planCardChevron");
  if (!body) return;
  const hidden = sessionStorage.getItem("demoPlanCardHidden") === "1";
  if (hidden) body.classList.add("hidden");
  if (chevron) chevron.textContent = hidden ? "▸" : "▾";
});

function toggleDatePicker() {
  const popover = document.getElementById("date-popover");
  popover.classList.toggle("hidden");

  if (!popover.classList.contains("hidden")) {
    const input = document.getElementById("pickDate");
    requestAnimationFrame(() => {
      if (!input) return;
      input.focus({ preventScroll: true });
      if (typeof input.showPicker === "function") input.showPicker();
      else input.click();
    });
  }
}

document.addEventListener("click", (event) => {
  const popover = document.getElementById("date-popover");
  const button = event.target.closest('[onclick="toggleDatePicker()"]');
  if (!popover || popover.classList.contains("hidden")) return;
  if (!popover.contains(event.target) && !button) popover.classList.add("hidden");
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

document.addEventListener("DOMContentLoaded", () => {
  document.getElementById("confirm-yes")?.addEventListener("click", () => {
    if (pendingDeleteUrl) {
      htmx.ajax("DELETE", pendingDeleteUrl, {
        target: "#list",
        swap: "innerHTML"
      });
    }
    closeConfirmModal();
  });
  document.getElementById("confirm-no")?.addEventListener("click", closeConfirmModal);
});
