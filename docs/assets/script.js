const themeStorageKey = "tentacolous-theme";
const themeMeta = document.querySelector('meta[name="theme-color"]');
const themeToggles = document.querySelectorAll("[data-theme-toggle]");

const getCurrentTheme = () => document.documentElement.dataset.theme === "light" ? "light" : "dark";

const applyTheme = (theme) => {
  const isLight = theme === "light";

  document.documentElement.dataset.theme = isLight ? "light" : "dark";
  themeMeta?.setAttribute("content", isLight ? "#f4f8fb" : "#061016");

  themeToggles.forEach((toggle) => {
    const isSpanish = document.documentElement.lang === "es";
    toggle.setAttribute("aria-pressed", String(isLight));
    toggle.setAttribute(
      "aria-label",
      isSpanish
        ? (isLight ? "Cambiar a tema oscuro" : "Cambiar a tema claro")
        : (isLight ? "Switch to dark theme" : "Switch to light theme")
    );
  });
};

applyTheme(localStorage.getItem(themeStorageKey) === "light" ? "light" : "dark");

themeToggles.forEach((toggle) => {
  toggle.addEventListener("click", () => {
    const nextTheme = getCurrentTheme() === "light" ? "dark" : "light";
    localStorage.setItem(themeStorageKey, nextTheme);
    applyTheme(nextTheme);
  });
});

const observer = new IntersectionObserver((entries) => {
  entries.forEach((entry) => {
    if (entry.isIntersecting) {
      entry.target.classList.add("visible");
      observer.unobserve(entry.target);
    }
  });
}, { threshold: 0.12 });

document.querySelectorAll(".reveal").forEach((element) => observer.observe(element));

document.querySelectorAll('a[href^="#"]').forEach((link) => {
  link.addEventListener("click", (event) => {
    const target = document.querySelector(link.getAttribute("href"));
    if (!target) {
      return;
    }

    event.preventDefault();
    target.scrollIntoView({ behavior: "smooth", block: "start" });
  });
});

document.querySelectorAll(".code-tabs").forEach((tabs) => {
  const buttons = tabs.querySelectorAll(".code-tab");

  buttons.forEach((button) => {
    button.addEventListener("click", () => {
      const targetId = button.dataset.tabTarget;
      const block = button.closest(".code-block");
      const target = block?.querySelector(`#${targetId}`);

      if (!target) {
        return;
      }

      buttons.forEach((item) => {
        item.classList.toggle("active", item === button);
        item.setAttribute("aria-selected", String(item === button));
      });

      block.querySelectorAll(".tab-panel").forEach((panel) => {
        const isActive = panel === target;
        panel.classList.toggle("active", isActive);
        panel.hidden = !isActive;
      });
    });
  });
});
