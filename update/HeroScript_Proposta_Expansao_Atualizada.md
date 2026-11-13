> **Validação de 13/11/2026:** lexluthor foi descartado por decisão do usuário e não será implementado. Inventário, resultados e limites atuais: [validacao-geral-2026-11-13.md](validacao-geral-2026-11-13.md).

> **Atualização de 13/11/2026:** primeira integração de spiderSense implementada no playground e na CLI, por autorização do usuário. Diagnósticos compartilhados, aranhas vermelhas/amarelas e avisos de comandos ignorados pelo hulksmash. Contrato atual: [spider-sense.md](../docs/linguagem/spider-sense.md). As referências ao adiamento abaixo documentam o planejamento anterior. A validação visual em navegador CheerpJ permanece pendente.

# HeroScript — Proposta de expansão (revisão 2)

> `deadpoolAction(chance = N)` está implementado, com A maiúsculo, chance inteira
> literal de 0 a 100 e sem seed. Cada entrada faz novo sorteio. Flashtravel é
> proibido dentro do corpo e não pode apontar para seu interior; saltar para a
> abertura repete o sorteio. Consulte [deadpoolAction-etapa-1.md](deadpoolAction-etapa-1.md)
> para semântica, testes e regras a integrar ao spiderSense somente na etapa final.

> Flashtravel foi reajustado e implementado: destinos são linhas físicas dentro
> do mesmo flashPoint, sem timeline, incluindo saída/reentrada em loops e battle.
> Consulte `flashtravel-etapa-1.md` e `examples/flashtravel.hero`; a proposta antiga
> com marcadores abaixo foi substituída. `spiderSense` será a última etapa de
> implementação, depois dos demais recursos e suas regras estarem estabilizados.
> Seu papel foi revisado para diagnóstico visual durante a edição: aranha amarela
> para aviso e vermelha para erro. Consulte [spiderSense-planejamento.md](spiderSense-planejamento.md).

> Atualização de implementação: a primeira etapa de `supervision` e `kryptonite`
> já está no compilador, com exemplo `examples/vision.hero` e testes em
> `tests/FeatureTests.java`. Consulte `supervision-etapa-1.md` e
> `examples/supervision.hero`: supervision substitui smanvision, com getValue para
> valor/localização da declaração e getMethod para estrutura permitida sem executar
> o método. A exibição de implementação pertence a supervision, não a batinvestigate.
> Consulte também `docs/linguagem/supervision.md`.
> Consulte o contrato
> `kryptonite-etapa-1.md` para o escopo entregue e os limites. A primeira etapa
> de `batcave` também está implementada; consulte `batcave-etapa-1.md` e
> `examples/batcave.hero`. `secret` e `kryptonite` não podem ser combinados,
> por decisão do usuário. A expansão de secret para variáveis locais, campos de
> batcave, métodos e missões também está implementada; consulte `secret-etapa-2.md`.
> Batcaves não podem ser marcadas secret; seus membros comuns continuam investigáveis
> por `batinvestigate`, comando único já implementado; os secret ficam ocultos.
> A divisão find/inspect/trace e a exibição de implementação foram descartadas.
> A busca atual percorre diretórios e mostra todas as origens visíveis, com caminho
> e linha, sem conteúdo; consulte `batinvestigate-etapa-1.md`, que substitui as
> propostas de investigação históricas deste documento.
> Os demais recursos
> novos abaixo continuam propostas. O texto original a seguir
> registra o planejamento e não representa todo o estado atual da implementação.

**Status:** documento para debate e planejamento. **Os recursos novos descritos aqui são propostas, não funcionalidades comprovadamente implementadas.**  
**Base consultada:** `heroscript.zip` e a proposta anterior.  
**Objetivo:** evoluir a HeroScript de uma linguagem voltada sobretudo a personagens e batalhas para uma linguagem temática com recursos úteis de investigação, diagnóstico, proteção e controle de fluxo, aproveitando seu compilador para Java.

## 1. Direção e princípios

- Preservar os programas `.hero` e a semântica existente sempre que possível.
- Manter `hero`, `villain`, `power`, `mission`, `squad` e os demais recursos existentes; não transformar tudo em um jogo de combate.
- Criar recursos que façam algo **útil para o programador**, não apenas trocar o nome de comandos Java.
- Separar **análise estática** (investigar código), **depuração em execução** (observar valores) e **controle de acesso**.
- Implementar em etapas com testes; não assumir que ocultação em ferramentas próprias torna o código Java ou bytecode indecifrável.

## 2. Inventário das ideias

| Recurso | Papel proposto | Categoria | Estado |
|---|---|---|---|
| `batcave` | Módulo/classe protegida; métodos públicos utilizáveis, interior invisível a `supervision`; `villain` não pode entrar | Encapsulamento | Proposta |
| `supervision` | Log/depurador de estado e execução | Depuração | Proposta |
| `spiderSense` | Diagnóstico visual durante a edição: aranha amarela para aviso e vermelha para erro | Editor/diagnóstico | Primeira integração implementada |
| `batinvestigate` | Encontrar origem **e analisar implementação, chamadas e dependências** de símbolos | Investigação estática | Proposta |
| `kryptonite` | Impedir que `supervision` mostre o valor marcado, sem impedir investigação estática | Visibilidade | Proposta |
| `secret` | Além da regra de acesso já existente, ocultar o símbolo dos resultados de `batinvestigate` | Acesso/ocultação | **Existe; expansão proposta** |
| `lexluthor` | Ideia retirada por decisão do usuário | Visibilidade | Descartado; não implementar |
| `batplan` | Desativar uma instância por identidade, incluindo seus aliases | Ciclo de vida | Primeira etapa implementada |
| `hulksmash` | Loop que ignora instruções comuns e executa somente regiões `blackwidow` | Controle de fluxo | Primeira etapa implementada |
| `blackwidow` | Região executável dentro de `hulksmash` | Controle de fluxo | Primeira etapa implementada |
| `hulkbuster` | Encerrar o `hulksmash` mais próximo quando alcançado em blackwidow | Controle de fluxo | Primeira etapa implementada |
| `flashtravel` | Saltar para marcador dentro da mesma função, com restrições | Controle de fluxo | Proposta |
| `deadpoolAction` | Sortear a execução de um bloco a cada entrada, sem seed; impedir saltos que contornem a decisão | Aleatoriedade | Primeira etapa implementada |

**Nomes:** manter `supervision`, `batinvestigate`, `spiderSense`, `kryptonite`, `lexluthor`, `hulksmash` como nomes de trabalho; o grupo pode padronizá-los depois. `batkey` foi **retirado da proposta atual** por não ser necessário ao uso principal de `batinvestigate`.

## 3. Regras consolidadas de investigação e visibilidade

### 3.1. Distinção fundamental

- **`supervision` (Superman):** acompanha a execução: logs, valores e eventos permitidos. **Não enxerga o interior de `batcave`**, nem valores `kryptonite` ou `secret`.
- **`batinvestigate` (Batman):** analisa **código-fonte/AST** para descobrir onde um símbolo está, como foi implementado, que métodos chama, quem o chama e quais dependências possui. **Pode investigar métodos dentro de `batcave`**, mas **não deve localizar nem revelar símbolos marcados `secret`** em seus resultados.
- **`spiderSense` (Homem-Aranha):** camada de diagnóstico do editor, sem comando no código HeroScript. Durante a edição, antes de compilar, marca avisos com aranha amarela e erros confirmados com aranha vermelha, com explicação e localização. Apresentará os diagnósticos de sintaxe e regras da HeroScript compartilhados com o compilador. Avisos não bloqueiam; erros confirmados continuam bloqueantes no compilador. Implementar por último, conforme [spiderSense-planejamento.md](spiderSense-planejamento.md).

### 3.2. Matriz de comportamento esperado

| Elemento | `supervision` | `batinvestigate` |
|---|---|---|
| Método/variável comum | Observa valores e eventos permitidos | Localiza, mostra estrutura, implementação e dependências |
| `batcave` | Pode mostrar a chamada pública, **não** o interior | Pode localizar e analisar métodos internos **não `secret`** |
| Variável `kryptonite` | **Não revela o valor** | Localiza e analisa declaração/usos, sem obter valor de runtime |
| Símbolo `secret` | **Não revela o valor** | **Não localiza, não exibe origem nem implementação** |
| `lexluthor` | A definir | A definir |

**Atenção à interação entre recursos:** `batcave` por si só **não** impede a investigação estática. Se uma declaração interna estiver marcada `secret`, **essa declaração específica** deve ficar oculta em `batinvestigate`.

### 3.3. O que significa ocultar `secret`?

- O compilador **continua reconhecendo e validando** símbolos `secret` internamente, inclusive tipos e permissões.
- O comando `batinvestigate` **filtra seus relatórios**: não deve listar o nome, arquivo, linha, assinatura, corpo ou dependências internas de símbolos `secret`.
- Ao analisar um método público que usa um símbolo `secret`, o relatório pode dizer **“há uma dependência protegida”**, sem identificar qual é.
- Uma busca direta por um nome `secret` deve retornar resposta genérica como **“símbolo não disponível para investigação”**, evitando confirmar sua existência desnecessariamente.
- A semântica já existente de `secret ... allow Mission` **deve ser preservada**: ocultação em relatórios é uma regra **adicional**, não substitui o controle de leitura por missão.
- **Limite de segurança:** quem possui acesso direto ao código-fonte ainda pode ler declarações e literais presentes nele. Esta é uma política do `batinvestigate`, não uma promessa de sigilo criptográfico. Não colocar senhas reais no fonte.

### 3.4. `batinvestigate` como ferramenta de manutenção

> Revisão atual: consulte `batinvestigate-etapa-1.md` para a interface com arquivo
> explícito, etapas, filtragem de secret e critérios de teste. O recurso ainda
> tem find implementado; inspect e trace ainda não estão disponíveis.
> O exemplo conceitual abaixo usa `allow Audit` em campo
> de batcave, forma ainda inválida: `allow` permanece exclusivo de campos de personagens.

Preferência: comando de CLI que investiga projetos sem exigir alterações no `.hero`:

```bash
heroscript batinvestigate find authenticate
heroscript batinvestigate trace authenticate
heroscript batinvestigate inspect authenticate
```

- `find`: localização, assinatura, classe/módulo, arquivo e linha.
- `trace`: métodos chamados, referências/chamadores e dependências, quando resolvíveis.
- `inspect`: estrutura da implementação, parâmetros, retorno, operações e possíveis efeitos identificáveis estaticamente.
- Quando houver sobrecarga, oferecer qualificação do símbolo (ex.: `WayneSecurity.authenticate(String)`).
- Para código Java externo sem fonte ou símbolos resolvidos, informar **limitação da análise**, sem inventar chamadas ou explicações.
- A descrição em linguagem natural pode ser uma evolução futura; **não depende obrigatoriamente de IA**. Uma primeira versão pode produzir relatório estruturado pela AST.

**Exemplo conceitual:**

```java
batcave WayneSecurity {
    secret String accessCode = "EXEMPLO_NAO_REAL" allow Audit;

    public boolean authenticate(String code) {
        return validate(code);
    }

    secret boolean validate(String code) {
        return code != null;
    }
}
```

`batinvestigate inspect WayneSecurity.authenticate` poderia identificar assinatura e lógica pública, mas mostraria a chamada à rotina protegida apenas como **dependência protegida**, sem expor `validate`, sua localização ou seu corpo. `supervision` não mostraria o interior da `batcave`.

### 3.5. `batcave` e vilões

- Métodos públicos podem ser usados por código externo autorizado.
- `villain` **não pode ser declarado dentro** de `batcave` (regra estática proposta).
- A ideia de impedir que instâncias vilãs **acessem** a Batcaverna permanece desejada, mas exige decidir como identificar o chamador e como verificar autorização em runtime. Não prometer essa garantia sem implementação.
- Não há `batkey` nesta versão. Investigar código de `batcave` não equivale a obter acesso a valores de execução ou privilégios de escrita.

## 4. Controle de fluxo e comportamento especial

### 4.1. `hulksmash`, `blackwidow`, `hulkbuster` — primeira etapa implementada

```java
int rage = 0;
hulksmash (rage < 5) {
    if (false) {
        blackwidow {
            rage++;
            if (rage == 3) {
                hulkbuster;
            }
        }
    }
    hulkbuster; // Ignorado fora de blackwidow.
}
```

A condição do hulksmash é avaliada em cada repetição. Somente regiões blackwidow executam; condições e controles externos são ignorados, inclusive o if falso do exemplo. Dentro de blackwidow, o fluxo é normal. Hulkbuster alcançado encerra o hulksmash mais próximo. Blackwidow e hulkbuster fora de qualquer hulksmash são erros de compilação.

Hulksmash sem blackwidow e hulkbuster ignorado são válidos, reservados para avisos amarelos futuros. SpiderSense permanece adiado. Contrato, integração com os demais recursos, testes e catálogo: [hulksmash-etapa-1.md](hulksmash-etapa-1.md).

### 4.2. `flashtravel`

```java
int attempts = 0;
timeline retry;
attempts++;
when (attempts < 3) {
    flashtravel retry;
}
```

Salto controlado para um marcador, apenas na mesma função/missão; sem entrar indevidamente em escopos, pular inicializações obrigatórias ou atravessar blocos com garantias de limpeza. **É uma proposta inspirada no `goto`, não uma recomendação de saltos irrestritos.**

### 4.3. `deadpoolAction`

```java
deadpoolAction(chance = 50) {
    announce "Hoje eu resolvi trabalhar!";
}
```

- Chance obrigatória, como inteiro literal de 0 a 100; seed não é aceito.
- Novo sorteio em cada entrada, inclusive nos limites 0 e 100, que nunca/sempre executam.
- Se ignorar, continua normalmente sem efeitos do corpo e sem mensagem automática.
- **Não engole exceções reais** de código que efetivamente executou.
- Flashtravel não pode estar escrito dentro do bloco ou de seus blocos internos.
- Saltos externos não podem apontar para o interior do corpo. Apontar para a linha da instrução de abertura é permitido e repete o sorteio.
- O corpo continua sujeito à validação de sintaxe, tipos e escopo, mesmo com chance 0.
- Implementação, testes e catálogo para spiderSense: [deadpoolAction-etapa-1.md](deadpoolAction-etapa-1.md).
- Um aviso de `spiderSense` sobre não determinismo poderá ser avaliado na etapa final; não é dependência da implementação deste recurso.

### 4.4. `batplan` — primeira etapa implementada

`batplan(target);` desativa a identidade de um objeto. Todos os aliases continuam apontando para a mesma instância inativa; os demais objetos permanecem ativos. Não remove declarações, entradas de listas ou memória física, nem recebe callback de limpeza.

Usos instrumentados de uma instância inativa geram `ObjectInactive`; alvo nulo gera `BatplanNullTarget`. Repetir a desativação é permitido. `flashtravel` não reativa objetos; executar `new` ou `summon` cria outra instância, sem recuperar aliases antigos.

O alvo é uma referência simples com tipo explícito. O alcance é o código gerado e o runtime instrumentado, não a JVM inteira. Contrato, limitações de Java embutido e testes: [batplan-etapa-1.md](batplan-etapa-1.md).

### 4.5. `lexluthor`

**Descartado em 13/11/2026 por decisão do usuário.** Não implementar; a proposta foi considerada simples e sem utilidade suficiente para justificar o recurso.

## 5. Impacto no compilador e runtime

| Componente | Trabalho esperado |
|---|---|
| Lexer | Tokens para novas construções realmente sintáticas |
| Parser | Gramática para `batcave`, blocos, modificadores e comandos de fluxo |
| AST | Nós e metadados de origem/visibilidade para análise de símbolos |
| Análise semântica | Regras `secret`, escopos, acesso, `villain`/`batcave`, alvos de saltos |
| Gerador Java | Tradução dos blocos e operações para Java válido |
| Runtime | Logs filtrados, aleatoriedade controlada, registro de instâncias |
| CLI de investigação | Índice de símbolos, busca, grafo de chamadas e filtragem `secret` |
| Testes | Casos positivos, negativos, integração e regressão dos `.hero` atuais |

**Observação:** o ZIP contém implementação de compilador, runtime e exemplos, inclusive um exemplo de `secret ... allow`. Antes de editar, verificar o comportamento real de cada construção e a compatibilidade dos testes no ambiente usado.

## 6. Ordem atual de implementação

1. **Base já entregue:** manter e corrigir `batcave`, `supervision`, `kryptonite`, `secret`, o comando único `batinvestigate`, `flashPoint`/`flashtravel`, `deadpoolAction`, `batplan` e `hulksmash`/`blackwidow`/`hulkbuster`, conforme seus contratos específicos. A divisão find/inspect/trace foi descartada.
2. **Demais recursos da linguagem:** revisar e implementar, em etapas combinadas com o usuário, os recursos restantes aprovados. `lexluthor` foi descartado.
3. **Consolidação:** estabilizar sintaxe, semântica, testes e diagnósticos do compilador para todas as construções previstas. Manter o catálogo de regras e casos de erro atualizado a cada recurso, sem implementar a interface de spiderSense ainda.
4. **Última etapa — `spiderSense`:** integrar ao editor os diagnósticos compartilhados do compilador e os avisos aprovados, com aranhas amarela/vermelha. Conferir a cobertura de cada recurso do catálogo antes de considerar essa etapa concluída.

O adiamento vale para a interface e integração de `spiderSense`, não para as validações do compilador: cada recurso novo deve continuar sendo validado e testado quando for implementado. Isso reduz retrabalho e ajuda a evitar esquecimentos na integração final. Recursos futuros após essa entrega deverão atualizar o mesmo catálogo e seus testes.

Cada etapa deve manter os exemplos antigos funcionando e incluir testes específicos para os novos recursos. O editor inicial (playground ou outro) ainda será escolhido.

## 7. Decisões já alinhadas na conversa

- A HeroScript deve servir também a **programas práticos**, não só combates.
- `batinvestigate` precisa ajudar **manutenção de código grande**, mostrando origem **e implementação**.
- `batinvestigate` **pode investigar `batcave`**, ao contrário de `supervision`.
- `secret` **não deve ser encontrado/exposto pelo `batinvestigate`**, preservando o funcionamento interno do compilador.
- `batkey` **não é necessária** para o fluxo principal de investigação; retirar por ora.
- `kryptonite` bloqueia observação pelo Superman, **não** investigação pelo Batman.
- `batplan` aplica-se a **qualquer instância gerenciada**, não apenas heróis.
- `villain` continua proibido dentro de `batcave`.
- `spiderSense` será implementado por último. Será diagnóstico visual durante a edição, com aranha amarela para aviso e vermelha para erro confirmado, compartilhando as regras do compilador.

## 8. Pendências para discussão em grupo

- `batcave` será classe especial ou módulo? Como se definem os métodos públicos?
- Como a regra de “vilões não entram” será aplicada a chamadas em tempo de execução?
- Como tratar referências a `secret` em grafos de dependências sem revelar sua identidade?
- `supervision` observará apenas `.hero` ou também bibliotecas Java externas?
- `batplan`: avaliar futuras ampliações da instrumentação de Java embutido; a primeira etapa sinaliza usos de objetos inativos com `ObjectInactive`.
- `flashtravel` realmente agrega utilidade suficiente para justificar sua complexidade?
- `lexluthor`: retirado do escopo; não é pendência de implementação.
- Padronizar grafia dos comandos e decidir se `when` será um alias opcional de `if`.

---

## Resumo para apresentar ao grupo

A expansão transforma a HeroScript em uma linguagem temática com **ferramentas reais de navegação e manutenção de código**, diagnósticos, controle de visibilidade e estruturas especiais de execução. `batinvestigate` atua como investigador estático do projeto; `supervision`, como observador de execução; `batcave`, como fronteira de depuração; `kryptonite`, como filtro de valores; e `secret`, como proteção mais restritiva **nos relatórios da HeroScript**. Os recursos de Hulk, Flash, Deadpool e Batman acrescentam comportamentos específicos, desde que suas regras sejam claras e testáveis.

**Todos os exemplos novos são ilustrativos e ainda exigem implementação e validação.**
