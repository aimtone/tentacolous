const themeStorageKey = "tentacolous-theme";
const themeMeta = document.querySelector('meta[name="theme-color"]');
const themeToggles = document.querySelectorAll("[data-theme-toggle]");
const menuToggles = document.querySelectorAll("[data-menu-toggle]");

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

menuToggles.forEach((toggle) => {
  const menu = document.getElementById(toggle.getAttribute("aria-controls"));

  if (!menu) {
    return;
  }

  const closeMenu = () => {
    toggle.setAttribute("aria-expanded", "false");
    toggle.setAttribute("aria-label", document.documentElement.lang === "es" ? "Abrir menu" : "Open menu");
    menu.classList.remove("open");
  };

  toggle.addEventListener("click", () => {
    const isOpen = toggle.getAttribute("aria-expanded") === "true";
    toggle.setAttribute("aria-expanded", String(!isOpen));
    toggle.setAttribute(
      "aria-label",
      document.documentElement.lang === "es"
        ? (!isOpen ? "Cerrar menu" : "Abrir menu")
        : (!isOpen ? "Close menu" : "Open menu")
    );
    menu.classList.toggle("open", !isOpen);
  });

  menu.querySelectorAll("a").forEach((link) => {
    link.addEventListener("click", closeMenu);
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

const escapeCode = (value) => value
  .replaceAll("&", "&amp;")
  .replaceAll("<", "&lt;")
  .replaceAll(">", "&gt;");

const detectLanguage = (code) => {
  const value = code.textContent.trim();
  if (/^<[^>]+>|<dependency>/m.test(value)) return "xml";
  if (/^\s*(?:@\w+|(?:public|private|protected)\s+|import\s+|package\s+)/m.test(value)) return "java";
  if (/^\s*(?:implementation|plugins\s*\{|dependencies\s*\{)/m.test(value)) return "groovy";
  if (/^\s*[\w.-]+:\s*(?:$|\S)/m.test(value)) return "yaml";
  if (/^\s*[\w.-]+\s*=\s*.+/m.test(value)) return "properties";
  if (/^\s*(?:select|insert|update|delete|create|alter|drop)\b/im.test(value)) return "sql";
  return "plain";
};

const highlightCode = (source, language) => {
  const tokenPattern = language === "xml"
    ? /<!--[\s\S]*?-->|<\/?[\w:-]+(?:\s+[\w:-]+(?:\s*=\s*(?:"[^"]*"|'[^']*'))?)*\s*\/?>/g
    : /\/\*[\s\S]*?\*\/|\/\/[^\n]*|#[^\n]*|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|@[A-Za-z_]\w*|\b(?:public|private|protected|static|final|class|interface|enum|extends|implements|return|new|void|if|else|for|while|switch|case|break|continue|try|catch|finally|throw|throws|true|false|null|import|package|this|super|instanceof|var|def|SELECT|FROM|WHERE|INSERT|INTO|UPDATE|DELETE|CREATE|ALTER|DROP|TABLE|VALUES|SET|AND|OR|NOT|NULL)\b|\b\d+(?:\.\d+)?(?:[A-Za-z]+)?\b|\b[A-Z][A-Za-z0-9_]*\b/g;
  let output = "";
  let cursor = 0;

  for (const match of source.matchAll(tokenPattern)) {
    const token = match[0];
    output += escapeCode(source.slice(cursor, match.index));
    let kind = "type";
    if (/^(?:\/\/|\/\*|#|<!--)/.test(token)) kind = "comment";
    else if (/^["']/.test(token)) kind = "string";
    else if (/^@/.test(token)) kind = "annotation";
    else if (/^\d/.test(token)) kind = "number";
    else if (/^</.test(token)) kind = "tag";
    else if (/^(?:public|private|protected|static|final|class|interface|enum|extends|implements|return|new|void|if|else|for|while|switch|case|break|continue|try|catch|finally|throw|throws|true|false|null|import|package|this|super|instanceof|var|def|SELECT|FROM|WHERE|INSERT|INTO|UPDATE|DELETE|CREATE|ALTER|DROP|TABLE|VALUES|SET|AND|OR|NOT|NULL)$/i.test(token)) kind = "keyword";
    output += `<span class="syntax-${kind}">${escapeCode(token)}</span>`;
    cursor = match.index + token.length;
  }
  return output + escapeCode(source.slice(cursor));
};

document.querySelectorAll("pre > code").forEach((code) => {
  const language = detectLanguage(code);
  code.classList.add(`language-${language}`);
  if (language !== "plain") code.innerHTML = highlightCode(code.textContent, language);
});
