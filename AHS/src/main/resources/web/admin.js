(() => {
  "use strict";

  const loginPanel = document.querySelector("#login-panel");
  const inquiriesPanel = document.querySelector("#inquiries-panel");
  const loginForm = document.querySelector("#login-form");
  const loginFeedback = document.querySelector("#login-feedback");
  const inquiriesFeedback = document.querySelector("#inquiries-feedback");
  const csrfHeader = "X-CSRF-Token";
  let csrfToken = "";
  let currentRole = "";

  async function request(path, options = {}) {
    const response = await fetch(path, {
      credentials: "same-origin",
      cache: "no-store",
      ...options,
      headers: {
        ...(options.headers || {}),
      },
    });
    const result = await response.json().catch(() => ({}));
    if (!response.ok) {
      throw new Error(result.error || `Päring ebaõnnestus (${response.status}).`);
    }
    return result;
  }

  function showLogin(message = "") {
    csrfToken = "";
    currentRole = "";
    inquiriesPanel.hidden = true;
    document.querySelector("#inquiries-head").replaceChildren();
    document.querySelector("#inquiries-body").replaceChildren();
    document.querySelector("#account-name").textContent = "";
    document.querySelector("#account-role").textContent = "";
    inquiriesFeedback.textContent = "";
    loginPanel.hidden = false;
    loginFeedback.textContent = message;
    loginForm.reset();
  }

  async function showInquiries(session) {
    csrfToken = session.csrfToken;
    currentRole = session.role;
    loginPanel.hidden = true;
    inquiriesPanel.hidden = false;
    document.querySelector("#account-name").textContent = session.username;
    document.querySelector("#account-role").textContent =
      currentRole === "ADMIN" ? "Täieliku ligipääsuga administraator" : "Piiratud vaatleja";
    inquiriesFeedback.textContent = "";
    await loadInquiries();
  }

  function addCell(row, value, className = "") {
    const cell = document.createElement("td");
    if (className) cell.className = className;
    cell.textContent = value == null || value === "" ? "—" : String(value);
    row.append(cell);
  }

  function displayDate(value) {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return value || "—";
    return new Intl.DateTimeFormat("et-EE", {
      dateStyle: "medium",
      timeStyle: "short",
    }).format(date);
  }

  function renderInquiries(inquiries) {
    const admin = currentRole === "ADMIN";
    const headings = admin
      ? ["Saabunud", "Nimi", "E-post", "Telefon", "Huvipakkuv toode", "Sõnum"]
      : ["Saabunud", "Huvipakkuv toode"];
    const head = document.querySelector("#inquiries-head");
    const body = document.querySelector("#inquiries-body");
    head.replaceChildren();
    body.replaceChildren();

    const headingRow = document.createElement("tr");
    for (const heading of headings) {
      const cell = document.createElement("th");
      cell.scope = "col";
      cell.textContent = heading;
      headingRow.append(cell);
    }
    head.append(headingRow);

    if (!inquiries.length) {
      const row = document.createElement("tr");
      const cell = document.createElement("td");
      cell.className = "empty-cell";
      cell.colSpan = headings.length;
      cell.textContent = "Päringuid pole veel saabunud.";
      row.append(cell);
      body.append(row);
      return;
    }

    for (const inquiry of inquiries) {
      const row = document.createElement("tr");
      addCell(row, displayDate(inquiry.createdAt));
      if (admin) {
        addCell(row, inquiry.name);
        addCell(row, inquiry.email);
        addCell(row, inquiry.phone);
        addCell(row, inquiry.interest);
        addCell(row, inquiry.message, "message-cell");
      } else {
        addCell(row, inquiry.interest);
      }
      body.append(row);
    }
  }

  async function loadInquiries() {
    try {
      const result = await request("/api/admin/inquiries");
      renderInquiries(result);
    } catch (error) {
      inquiriesFeedback.textContent = error.message;
      if (error.message.includes("Authentication required")) showLogin("Sinu sessioon on aegunud. Logi uuesti sisse.");
    }
  }

  loginForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    const button = loginForm.querySelector("button[type=submit]");
    button.disabled = true;
    loginFeedback.textContent = "";
    try {
      const result = await request("/api/admin/login", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8" },
        body: new URLSearchParams(new FormData(loginForm)),
      });
      await showInquiries(result);
    } catch (error) {
      loginFeedback.textContent = error.message;
    } finally {
      button.disabled = false;
    }
  });

  document.querySelector("#logout-button").addEventListener("click", async (event) => {
    const button = event.currentTarget;
    button.disabled = true;
    try {
      await request("/api/admin/logout", {
        method: "POST",
        headers: { [csrfHeader]: csrfToken },
      });
      showLogin("Oled turvaliselt välja logitud.");
    } catch (error) {
      inquiriesFeedback.textContent = error.message;
    } finally {
      button.disabled = false;
    }
  });

  request("/api/admin/session")
    .then((session) => {
      if (session.authenticated) return showInquiries(session);
      showLogin();
    })
    .catch((error) => showLogin(error.message));
})();
