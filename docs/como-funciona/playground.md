# Playground (WebAssembly)

O [playground](../playground/index.html) executa HeroScript no navegador **com o mesmo compilador** da linha de
comando — nada de reimplementação em JavaScript.

```mermaid
flowchart LR
    A[Editor CodeMirror] -->|código| B[playground.js]
    B -->|chamada Java| C[CheerpJ: JVM em WebAssembly]
    C --> D[heroscript.jar<br/>Lexer, Parser, Gerador, Runtime]
    D --> E[ecj.jar<br/>compilador Java]
    E --> D
    D -->|texto da saída| B
```

## Peças

| Peça | Papel |
|---|---|
| [CheerpJ](https://cheerpj.com) 4.3 | JVM compilada para WebAssembly, carregada do CDN da Leaning Technologies |
| `heroscript.jar` | compilador + runtime, compilados com `--release 17` |
| `ecj.jar` | Eclipse Compiler for Java 3.38 — compilador Java escrito em Java, substitui o `javac` |
| `jdk17-base.jar` | API pública do `java.base` do Java 17, extraída do `ct.sym` do JDK por `scripts/make_jdk_stub.py` |
| `Playground.java` | ponte com métodos estáticos `run`, `java` e `tokens` que devolvem texto |
| `playground.js` | inicia o CheerpJ, carrega os jars em modo biblioteca e chama a ponte |

```js
await cheerpjInit({
  version: 17,
  javaProperties: ["java.io.tmpdir=/files", "heroscript.platform=/app/.../jdk17-base.jar"],
});
const lib = await cheerpjRunLibrary("/app/.../heroscript.jar:/app/.../ecj.jar");
const Playground = await lib.heroscript.playground.Playground;
const saida = await Playground.run(codigo);
```

## Detalhes importantes

- **Sem `System.exit`**: `Playground.run` redireciona `System.out` para um buffer e o `JavaRunner` troca o
  encerramento por um sinal interno, então a JVM continua viva entre execuções.
- **Classes do JDK**: fora do navegador o ECJ lê as classes do Java pelo sistema de módulos (`jrt:/`), que o
  CheerpJ não oferece. Por isso o `JavaRunner` entrega ao ECJ o `jdk17-base.jar` como `PLATFORM_CLASS_PATH` e
  esvazia `SYSTEM_MODULES`. Isso só funciona com o gerenciador de arquivos padrão do ECJ, sem embrulho: com um
  `ForwardingJavaFileManager` o ECJ ignora essas configurações e volta a procurar o `jrt:/`.
- **Arquivos temporários**: o ECJ só compila fontes que existem em disco; no navegador o `.java` e os `.class`
  vão para `/files`, o sistema de arquivos virtual do CheerpJ.
- **Bytecode Java 17**: o CheerpJ executa no máximo Java 17, mas o ECJ gera por padrão a versão mais nova que
  conhece. O `JavaRunner` passa `-source 17 -target 17` ao ECJ (e `--release 17` ao `javac`). Sem isso a classe
  gerada é recusada com `UnsupportedClassVersionError`.
- **Nenhum erro pode escapar**: `Playground.run` captura também `Error`. Uma exceção que saísse da chamada deixaria
  a promessa do JavaScript pendente para sempre, e a página pareceria travada.
- **Aquecimento**: a primeira compilação no navegador leva cerca de 40 s, porque o ECJ ainda está sendo
  otimizado pela JVM. Um dos métodos do parser do ECJ é grande demais para o JIT do CheerpJ e roda interpretado. Por
  isso a página compila um programa mínimo ao abrir e mostra um cronômetro; depois disso cada execução leva de 2 a 5 s.
- **Diagnóstico**: com `heroscript.debug=true` (ligado no playground), o `JavaRunner` mostra as etapas e os tempos no
  console do navegador (F12).
- **Compartilhar**: o botão gera um link com o código em `#code=...`; `?example=nome` abre um exemplo.

## Montando localmente

```bash
scripts/build_playground.sh          # build/playground: jars + examples.json + página
python3 scripts/serve.py build 8000  # http://localhost:8000/playground/
```

O servidor precisa suportar *Range requests* (o `scripts/serve.py` suporta; `python -m http.server` não).
