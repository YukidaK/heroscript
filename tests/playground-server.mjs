// Playground com servidor: usa /api/ sem iniciar o CheerpJ e cai para o CheerpJ quando o servidor falha.
import vm from 'node:vm';
import fs from 'node:fs';
import assert from 'node:assert/strict';

class Element {
  children = []; textContent = ''; innerHTML = ''; style = {}; attributes = {}; listeners = {}; disabled = true;
  setAttribute(name, value) { this.attributes[name] = value; }
  append(child) { this.children.push(child); }
  replaceChildren() { this.children = []; this.textContent = ''; }
  addEventListener(type, handler) { this.listeners[type] = handler; }
  after() {}
}

/** Carrega o playground.js num DOM simulado; `api(kind, body)` responde às chamadas a /api/. */
function load(api) {
  const elements = new Map();
  const timers = [];
  const state = { jvmStarts: 0, bridgeCalls: [], requests: [] };
  let value = '', change = () => {};
  const editor = {
    getWrapperElement: () => new Element(),
    on: (_, handler) => { change = handler; },
    getValue: () => value,
    setValue: text => { value = text; change(); },
    clearGutter() {}, setGutterMarker() {}, focus() {}, setCursor() {},
  };
  const bridge = new Proxy({}, {
    get: (_, kind) => kind === 'then' ? undefined : async () => { state.bridgeCalls.push(kind); return kind === 'diagnostics' ? '[]' : `navegador:${kind}`; },
  });
  const context = {
    document: {
      getElementById: id => { if (!elements.has(id)) elements.set(id, new Element()); return elements.get(id); },
      createElement: () => new Element(),
    },
    CodeMirror: { defineMIME() {}, fromTextArea: () => editor },
    matchMedia: () => ({ matches: false }),
    location: { href: 'https://local.test/playground/', hash: '', search: '', pathname: '/playground/', origin: 'https://local.test' },
    URL, URLSearchParams, performance, console: { warn() {} },
    setTimeout: fn => timers.push(fn), clearTimeout() {}, setInterval: () => 0, clearInterval() {},
    fetch: async (url, options) => {
      if (url === 'examples.json') return { json: async () => ({ hello: { title: 'Hello', code: 'battle{}' } }) };
      const kind = url.replace('/api/', '');
      state.requests.push(kind);
      return api(kind, options?.body);
    },
    cheerpjInit: async () => { state.jvmStarts++; },
    cheerpjRunLibrary: async () => ({ heroscript: { playground: { Playground: bridge } } }),
  };
  vm.runInNewContext(fs.readFileSync('playground/playground.js', 'utf8'), context);
  return { state, element: id => elements.get(id), editor, timers };
}
const settle = async () => { for (let i = 0; i < 5; i++) await new Promise(setImmediate); };
const ok = text => ({ ok: true, status: 200, text: async () => text });

// 1. Servidor disponível: pronto na hora, sem CheerpJ.
{
  const page = load(async (kind, body) => kind === 'health' ? ok('ok')
    : kind === 'diagnostics' ? ok('[]') : ok(`>> servidor recebeu ${body}\n[exit 0]`));
  await settle();
  assert.equal(page.state.jvmStarts, 0, 'CheerpJ is not started when the server answers');
  assert.equal(page.element('run').disabled, false, 'ready without warmup');
  assert.match(page.element('hint').textContent, /servidor/);
  page.editor.setValue('battle { announce 1; }');
  page.element('run').listeners.click(); await settle();
  assert.match(page.element('output').innerHTML, /servidor recebeu battle \{ announce 1; \}/);
  assert.deepEqual(page.state.requests, ['health', 'run']);
  page.timers.at(-1)(); await settle();
  assert.equal(page.state.requests.at(-1), 'diagnostics', 'spiderSense also uses the server');
  assert.equal(page.state.jvmStarts, 0);
  assert.deepEqual(page.state.bridgeCalls, []);
}

// 2. Servidor cai depois de pronto: a mesma execução é refeita no CheerpJ.
{
  let healthy = true;
  const page = load(async kind => kind === 'health' ? ok('ok')
    : healthy ? ok('>> servidor\n[exit 0]') : { ok: false, status: 503, text: async () => 'servidor ocupado' });
  await settle();
  healthy = false;
  page.element('run').listeners.click(); await settle();
  assert.equal(page.state.jvmStarts, 1, 'CheerpJ started on demand');
  assert.deepEqual(page.state.bridgeCalls, ['run', 'run'], 'warmup, then the requested run');
  assert.match(page.element('output').innerHTML, /navegador:run/);
  assert.match(page.element('hint').textContent, /navegador/);
  assert.equal(page.element('run').disabled, false);
  page.element('java').listeners.click(); await settle();
  assert.equal(page.state.jvmStarts, 1, 'CheerpJ is started only once');
  assert.equal(page.state.requests.filter(k => k === 'java').length, 0, 'failed server is not retried on every call');
}

// 3. Erro de rede na execução também cai para o CheerpJ.
{
  let healthy = true;
  const page = load(async kind => { if (kind !== 'health' && !healthy) throw new TypeError('Failed to fetch'); return ok('ok'); });
  await settle();
  healthy = false;
  page.element('tokens').listeners.click(); await settle();
  assert.equal(page.state.jvmStarts, 1);
  assert.match(page.element('output').textContent, /navegador:tokens/);
}

// 4. Sem servidor (ex.: scripts/serve.py): comportamento antigo, CheerpJ ao abrir.
{
  const page = load(async () => ({ ok: false, status: 404, text: async () => '' }));
  await settle();
  assert.equal(page.state.jvmStarts, 1);
  assert.deepEqual(page.state.bridgeCalls, ['run'], 'warmup');
  assert.equal(page.element('run').disabled, false);
  assert.match(page.element('hint').textContent, /navegador/);
}
console.log('playground server/fallback checks passed');
