// Playground da HeroScript: o mesmo compilador da linha de comando, rodando numa JVM em WebAssembly (CheerpJ).
(() => {
  "use strict";

  const $ = (id) => document.getElementById(id);
  const buttons = ["run", "java", "tokens"].map($);
  const output = $("output");
  const status = $("status");
  const select = $("example");

  CodeMirror.defineMIME("text/x-heroscript", {
    name: "clike",
    keywords: words(
      "hero villain power gadget form secret allow mission squad members combo battle " +
      "unleash on transform revert evolve by summon as recruit into assemble announce " +
      "if else for while do switch case default break continue return try catch finally throw new this " +
      "true false null"
    ),
    types: words("int long double float boolean char String void var Fighter Hero Villain Squad HeroError"),
    builtin: words("shield recharge status isDefeated maxEnergy maxHealth members kind"),
    atoms: words("true false null"),
  });

  const editor = CodeMirror.fromTextArea($("code"), {
    mode: "text/x-heroscript",
    theme: matchMedia("(prefers-color-scheme: dark)").matches ? "material-darker" : "default",
    lineNumbers: true,
    indentUnit: 4,
    matchBrackets: true,
    autoCloseBrackets: true,
    extraKeys: {
      "Ctrl-Enter": () => action("run"),
      "Cmd-Enter": () => action("run"),
      Tab: (cm) => cm.replaceSelection("    "),
    },
  });

  let playground = null;
  let examples = {};
  let busy = false;
  let timer = null;

  // Programa mínimo compilado ao abrir a página: a primeira compilação no navegador é lenta (~40 s) porque o
  // compilador Java ainda está sendo otimizado pela JVM; depois dela cada execução leva poucos segundos.
  const WARMUP = "hero Warmup { power P { cost = 1; } }\nbattle { summon Warmup as w; unleash w.P; }";

  function words(list) {
    return Object.fromEntries(list.split(" ").map((w) => [w, true]));
  }

  /** Liga/desliga os botões; enquanto ocupado, mostra um cronômetro para nunca parecer travado. */
  function setBusy(value, message) {
    busy = value;
    buttons.forEach((b) => (b.disabled = value || !playground));
    clearInterval(timer);
    if (!message) return;
    status.textContent = message;
    if (value) {
      const started = performance.now();
      timer = setInterval(() => {
        status.textContent = `${message} ${Math.round((performance.now() - started) / 1000)} s`;
      }, 1000);
    }
  }

  function escapeHtml(s) {
    return s.replace(/[&<>]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;" })[c]);
  }

  function render(text, colorize) {
    if (!colorize) {
      output.textContent = text;
      return;
    }
    output.innerHTML = text
      .split("\n")
      .map((line) => {
        const html = escapeHtml(line);
        if (/^(HeroError|HeroSyntaxError|HeroCompileError|Erro)/.test(line)) return `<span class="err">${html}</span>`;
        if (line.startsWith(">> HeroError")) return `<span class="err">${html}</span>`;
        if (line.startsWith(">>")) return `<span class="say">${html}</span>`;
        if (/^\[(hit|down)\]/.test(line)) return `<span class="hit">${html}</span>`;
        if (/^\[exit \d+\]$/.test(line)) return `<span class="exit">${html}</span>`;
        if (line.startsWith("[")) return html.replace(/^\[[a-z]+\]/, (t) => `<span class="tag">${t}</span>`);
        return html;
      })
      .join("\n");
  }

  async function action(kind) {
    if (busy || !playground) return;
    const titles = { run: "Saída", java: "Java gerado", tokens: "Tokens" };
    setBusy(true, kind === "run" ? "Compilando e executando…" : "Processando…");
    $("output-title").textContent = titles[kind];
    const started = performance.now();
    try {
      const result = await playground[kind](editor.getValue());
      render(String(result), kind === "run");
      $("timing").textContent = `${Math.round(performance.now() - started)} ms`;
      setBusy(false, "Pronto. Ctrl+Enter executa.");
    } catch (e) {
      render(`Erro no playground: ${e}`, true);
      setBusy(false, "Falhou — veja a saída.");
    }
  }

  function encode(text) {
    return btoa(String.fromCharCode(...new TextEncoder().encode(text)));
  }

  function decode(b64) {
    return new TextDecoder().decode(Uint8Array.from(atob(b64), (c) => c.charCodeAt(0)));
  }

  async function share() {
    const url = `${location.origin}${location.pathname}#code=${encodeURIComponent(encode(editor.getValue()))}`;
    history.replaceState(null, "", url);
    try {
      await navigator.clipboard.writeText(url);
      status.textContent = "Link copiado para a área de transferência.";
    } catch {
      status.textContent = "Link atualizado na barra de endereço.";
    }
  }

  async function loadExamples() {
    examples = await (await fetch("examples.json")).json();
    for (const [name, ex] of Object.entries(examples)) {
      const opt = document.createElement("option");
      opt.value = name;
      opt.textContent = `${name}.hero — ${ex.title}`;
      select.append(opt);
    }
    const hash = new URLSearchParams(location.hash.slice(1));
    const wanted = new URLSearchParams(location.search).get("example");
    if (hash.get("code")) {
      editor.setValue(decode(hash.get("code")));
      select.value = "";
    } else {
      const name = wanted in examples ? wanted : Object.keys(examples)[0];
      select.value = name;
      editor.setValue(examples[name].code);
    }
  }

  async function startJvm() {
    const base = new URL(".", location.href).pathname;
    await cheerpjInit({
      version: 17,
      status: "none",
      javaProperties: [
        "java.io.tmpdir=/files",
        `heroscript.platform=/app${base}jdk17-base.jar`,
        "heroscript.debug=true",
      ],
    });
    const lib = await cheerpjRunLibrary(`/app${base}heroscript.jar:/app${base}ecj.jar`);
    playground = await lib.heroscript.playground.Playground;
  }

  select.addEventListener("change", () => {
    if (examples[select.value]) {
      editor.setValue(examples[select.value].code);
      history.replaceState(null, "", `${location.pathname}?example=${select.value}`);
    }
  });
  $("run").addEventListener("click", () => action("run"));
  $("java").addEventListener("click", () => action("java"));
  $("tokens").addEventListener("click", () => action("tokens"));
  $("share").addEventListener("click", share);

  (async () => {
    setBusy(true, "Carregando a JVM (CheerpJ)…");
    try {
      await loadExamples();
    } catch (e) {
      render(`Não foi possível carregar os exemplos: ${e}`, true);
    }
    try {
      const started = performance.now();
      await startJvm();
      render("Preparando o compilador Java no navegador. Isso só acontece ao abrir a página.", false);
      setBusy(true, "Preparando o compilador Java (só na primeira vez, cerca de 40 s)…");
      await playground.run(WARMUP);
      setBusy(false, `Pronto em ${((performance.now() - started) / 1000).toFixed(0)} s. Ctrl+Enter executa.`);
      render("Clique em ▶ Executar (ou Ctrl+Enter).", false);
    } catch (e) {
      setBusy(false, "Não foi possível iniciar a JVM no navegador.");
      render(`Falha ao iniciar o CheerpJ: ${e}`, true);
    }
  })();
})();
