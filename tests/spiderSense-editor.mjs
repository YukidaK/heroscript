import vm from 'node:vm';
import fs from 'node:fs';
import assert from 'node:assert/strict';

class Element {
  children = []; textContent = ''; style = {}; attributes = {};
  setAttribute(name, value) { this.attributes[name] = value; }
  append(child) { this.children.push(child); }
  replaceChildren() { this.children = []; this.textContent = ''; }
  listeners = {};
  addEventListener(type, handler) { this.listeners[type] = handler; }
  after(panel) { this.panel = panel; }
}
const elements = new Map();
const wrapper = new Element();
const timers = new Map();
let serial = 0, change, value = '', response = [], calls = 0, runs = 0;
const markers = new Map();
const editor = {
  getWrapperElement: () => wrapper,
  on: (_, handler) => { change = handler; },
  getValue: () => value,
  setValue: text => { value = text; change(); },
  clearGutter: () => markers.clear(),
  setGutterMarker: (line, gutter, marker) => markers.set(line, marker),
  focus() {}, setCursor() {},
};
const bridge = {
  run: async () => { runs++; return ''; },
  diagnostics: async () => { calls++; return JSON.stringify(await response); },
};
const context = {
  document: {
    getElementById: id => { if (!elements.has(id)) elements.set(id, new Element()); return elements.get(id); },
    createElement: () => new Element(),
  },
  CodeMirror: { defineMIME() {}, fromTextArea: () => editor },
  matchMedia: () => ({ matches: false }),
  location: { href: 'https://local.test/', hash: '', search: '', pathname: '/', origin: 'https://local.test' },
  URL, URLSearchParams, performance,
  setTimeout: fn => { const id = ++serial; timers.set(id, fn); return id; },
  clearTimeout: id => timers.delete(id), setInterval: () => 0, clearInterval() {},
  fetch: async () => ({ json: async () => ({ hello: { title: 'Hello', code: 'battle{}' } }) }),
  cheerpjInit: async () => {}, cheerpjRunLibrary: async () => ({ heroscript: { playground: { Playground: bridge } } }),
};
vm.runInNewContext(fs.readFileSync('playground/playground.js', 'utf8'), context);
const settle = async () => { await new Promise(setImmediate); };
const analyze = async () => {
  const task = [...timers.values()].at(-1); timers.clear();
  assert.ok(task, 'analysis scheduled');
  task(); await settle();
};
await settle();
response = [{ severity: 'warning', line: 2, column: 1, message: 'Ignored' }];
await analyze();
assert.match(markers.get(1).style.cssText, /f0b429/);
assert.match(markers.get(1).attributes['aria-label'], /Aviso/);
assert.match(markers.get(1).innerHTML, /<svg/);
response = [{ severity: 'error', line: 1, column: 2, message: 'Invalid' }];
editor.setValue('bad'); await analyze();
assert.match(markers.get(0).style.cssText, /ff6b5e/);
assert.match(wrapper.panel.children[0].textContent, /Erro/);
let resolveOld;
response = new Promise(resolve => { resolveOld = resolve; });
editor.setValue('old'); await analyze();
editor.setValue('fixed');
assert.equal(markers.size, 0, 'edits immediately remove stale markers');
resolveOld([{ severity: 'error', line: 1, column: 1, message: 'stale' }]);
await settle();
assert.equal(markers.size, 0, 'stale asynchronous results discarded');
response = []; await analyze();
assert.match(wrapper.panel.textContent, /nenhum problema/);
assert.equal(runs, 1, 'only existing startup warmup runs; diagnostics never run user code');
assert.equal(calls, 4);
// Executar durante a análise: o botão continua habilitado e o clique espera a análise em vez de ser ignorado.
let resolveSlow;
response = new Promise(resolve => { resolveSlow = resolve; });
editor.setValue('slow'); await analyze();
assert.equal(elements.get('run').disabled, false, 'analysis does not disable the run button');
elements.get('run').listeners.click(); await settle();
assert.equal(runs, 1, 'run waits for the analysis in flight');
resolveSlow([]); await settle();
assert.equal(runs, 2, 'run requested during analysis is executed, not dropped');
console.log('spiderSense editor lifecycle checks passed');
