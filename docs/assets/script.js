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
