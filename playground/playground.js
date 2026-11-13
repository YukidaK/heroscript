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
      "hero villain power gadget form secret allow mission squad members combo battle batcave kryptonite supervision flashPoint flashtravel deadpoolAction batplan hulksmash blackwidow hulkbuster " +
      "unleash on transform revert evolve by summon as recruit into assemble announce " +
      "if else for while do switch case default break continue return try catch finally throw new this " +
      "true false null"
    ),
    types: words("int long double float boolean char String void var Fighter Hero Villain Squad HeroError"),
    builtin: words("shield recharge status isDefeated maxEnergy maxHealth members kind getValue getMethod"),
    atoms: words("true false null"),
  });

  const editor = CodeMirror.fromTextArea($("code"), {
    mode: "text/x-heroscript",
    theme: matchMedia("(prefers-color-scheme: dark)").matches ? "material-darker" : "default",
    lineNumbers: true,
    gutters: ["CodeMirror-linenumbers", "spiderSense"],
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
  let diagnosticTimer = null;
  let revision = 0;
  let analysis = null; // promessa da análise em curso; a JVM atende uma chamada por vez
  const diagnosticPanel = document.createElement("div");
  diagnosticPanel.setAttribute("aria-live", "polite");
  diagnosticPanel.style.cssText = "padding:8px;max-height:160px;overflow:auto;font-size:13px";
  editor.getWrapperElement().after(diagnosticPanel);

  function scheduleDiagnostics() {
    clearTimeout(diagnosticTimer);
    diagnosticTimer = setTimeout(analyzeEditor, 700);
  }

  editor.on("change", () => {
    revision++;
    editor.clearGutter("spiderSense");
    diagnosticPanel.textContent = "spiderSense: análise pendente…";
    scheduleDiagnostics();
  });

  // A análise roda em segundo plano: não desabilita os botões nem mexe na barra de status. Se o usuário
  // executar durante a análise, action() espera ela terminar em vez de ignorar o clique.
  async function analyzeEditor() {
    if (!playground || busy || analysis) { scheduleDiagnostics(); return; }
    const version = revision;
    analysis = runAnalysis(version, editor.getValue());
    try {
      await analysis;
    } finally {
      analysis = null;
      if (version !== revision) scheduleDiagnostics();
    }
  }

  async function runAnalysis(version, source) {
    diagnosticPanel.textContent = "spiderSense: analisando…";
    try {
      const issues = JSON.parse(String(await playground.diagnostics(source)));
      if (version !== revision) return;
      editor.clearGutter("spiderSense");
      diagnosticPanel.replaceChildren();
      const byLine = new Map();
      for (const issue of issues) {
        const label = issue.severity === "error" ? "Erro" : "Aviso";
        const color = issue.severity === "error" ? "#e53935" : "#b88600";
        const description = `${label} — linha ${issue.line}: ${issue.message}`;
        const row = document.createElement("button");
        row.type = "button";
        row.textContent = `🕷 ${description}`;
        row.style.cssText = `display:block;text-align:left;color:${color};background:transparent;border:0;cursor:pointer`;
        row.onclick = () => { editor.focus(); editor.setCursor(issue.line - 1, issue.column - 1); };
        diagnosticPanel.append(row);
        if (!byLine.has(issue.line)) byLine.set(issue.line, []);
        byLine.get(issue.line).push({ issue, description });
      }
      for (const [line, entries] of byLine) {
        const marker = document.createElement("span");
        marker.style.cssText = `color:${entries.some(e => e.issue.severity === "error") ? "#e53935" : "#b88600"};display:block;width:18px;cursor:help`;
        marker.title = entries.map(e => e.description).join("\n");
        marker.setAttribute("role", "img");
        marker.setAttribute("aria-label", marker.title);
        marker.innerHTML = '<svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true"><g fill="currentColor" stroke="currentColor" stroke-width="1.6"><ellipse cx="12" cy="14" rx="4" ry="5"/><circle cx="12" cy="7" r="2"/><path fill="none" d="M9 10L4 6 2 2M8 12L3 10 1 7M8 15L3 16 1 20M9 17L6 20 6 23M15 10L20 6 22 2M16 12L21 10 23 7M16 15L21 16 23 20M15 17L18 20 18 23"/></g></svg>';
        editor.setGutterMarker(line - 1, "spiderSense", marker);
      }
      if (!issues.length) diagnosticPanel.textContent = "spiderSense: nenhum problema estático encontrado.";
    } catch (error) {
      if (version === revision) diagnosticPanel.textContent = `spiderSense indisponível: ${error}`;
    }
  }

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
    try {
      if (analysis) await analysis;
      const started = performance.now();
      const result = await playground[kind](editor.getValue());
      render(String(result), kind === "run");
      $("timing").textContent = `${Math.round(performance.now() - started)} ms`;
      setBusy(false, "Pronto. Ctrl+Enter executa.");
    } catch (e) {
      render(`Erro no playground: ${e}`, true);
      setBusy(false, "Falhou — veja a saída.");
    } finally {
      scheduleDiagnostics();
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
      scheduleDiagnostics();
    } catch (e) {
      setBusy(false, "Não foi possível iniciar a JVM no navegador.");
      render(`Falha ao iniciar o CheerpJ: ${e}`, true);
    }
  })();
})();
