const KEY = "lois_access_token";

function csrfHeaders() {
  const token = document.querySelector('meta[name="_csrf"]')?.content;
  const header = document.querySelector('meta[name="_csrf_header"]')?.content;
  return (token && header) ? { [header]: token } : {};
}

async function issueTokenOnce() {
  const res = await fetch("/auth/token", {
    method: "POST",
    credentials: "same-origin",
    headers: csrfHeaders()
  });
  if (!res.ok) throw new Error("issue token failed: " + res.status);
  const data = await res.json();
  sessionStorage.setItem(KEY, data.accessToken);
}
(async () => {
  const params = new URLSearchParams(location.search);
  const next = params.get("next") || "/";

  try {
    await issueTokenOnce();
    location.replace(next);
  } catch (e) {
    sessionStorage.removeItem(KEY);
    location.replace("/login?auth=required");
  }
})();