# Secret em variáveis e métodos

**Estado:** sintaxe, geração Java, metadados da AST e comportamento de supervision
implementados. O comando único `batinvestigate` também está implementado: filtra essas
declarações e não percorre corpos de métodos ou missões secret. As etapas
Os antigos modos find/inspect/trace foram substituídos pela busca de origens,
sem exibição de conteúdos.

## Decisão consolidada

A batcave continua localizável. Find localiza seus membros comuns, inclusive
privados, mas ignora cada membro marcado `secret`.
Não é permitido marcar a própria batcave com `secret`. `supervision` continua
sem observar o interior de qualquer batcave, independentemente dessas marcações.

| Declaração | Sintaxe suportada | Acesso pelo programa |
|---|---|---|
| Campo de personagem | `secret String identity = "..." allow Read;` | Regra antiga de missões autorizadas |
| Campo de batcave | `secret final String code = "...";` | Privado por padrão; pode ter `public` ou `private` explícito depois de secret |
| Variável local | `secret [final] Tipo nome = valor;` | Escopo Java normal; inicializador obrigatório |
| Método de batcave | `secret [public/private] Tipo nome(...) { ... }` | Visibilidade independente da ocultação; privado por padrão |
| Método tipado de personagem | `secret [public/private] Tipo nome(...) { ... }` | Modificador Java; privado por padrão |
| Missão de personagem | `secret mission Read { ... }` | Continua sendo chamada como missão |

`secret` vem antes dos outros modificadores na declaração. Métodos tipados
novos podem pertencer a `hero` ou `villain`, aceitar parâmetros e retornar valores;
não usam `allow`. Métodos tipados comuns também são aceitos em personagens,
para permitir a análise por `supervision.getMethod`; missões continuam funcionando.

`allow` continua exclusivo de campos secret de personagens e pode citar missões
secret. Um método tipado não ganha permissão para ler esses campos só por ser
secret: a missão autorizada do mesmo personagem ainda precisa estar em execução.
Campos secret de personagens mantêm a regra antiga de leitura via getter protegido.

Parâmetros marcados, classes/batcaves secret, métodos locais, declaração de
múltiplas variáveis por instrução e combinação com `kryptonite` não são suportados.
A combinação `secret` + `kryptonite` é proibida, não um recurso planejado.
Em batcaves permanecem os limites de campos de instância e construtor implícito.

## Observação em execução

- Local secret de tipo primitivo ou String produz `[protegido]` quando observado
  fora de contexto suprimido, sem passar seu valor para o evento.
- Locais secret de outros tipos podem ser usados no programa, mas supervision.getValue(mantém sua restrição de tipos: não inspeciona objetos ou arrays.
- Métodos e missões secret suprimem observações durante sua execução, incluindo
  chamadas síncronas externas. O contexto é restaurado em `finally`, inclusive
  em retornos, aninhamento e exceções.
- Campos e locais dentro de batcave não geram eventos, nem mesmo `[protegido]`.
- `announce`, logs de missão/combate existentes e retornos explícitos continuam
  funcionando. Não há propagação da marcação para cópias ou valores devolvidos.
- A supressão é por thread); tarefas assíncronas não herdam automaticamente o contexto.

## Preparação da investigação

A AST preserva o nome, a posição e a marcação das declarações, para o compilador
resolver e validar seu uso. `FieldDecl`, `CaveMethod` e `MissionDecl` possuem
flag secret; `SecretLocal` identifica uma variável local protegida e `SecretDecl`
continua identificando os campos antigos. Esses dados internos não são um
relatório público e não devem ser apagados para implementar a ocultação.

Regras de investigação (o contrato atual é `batinvestigate-etapa-1.md`):

1. Filtrar campos, métodos, missões e locais secret de buscas e listagens.
2. Ocultar todo o corpo de um método secret, inclusive suas declarações internas.
3. Substituir referências resolvidas por indicação genérica de dependência protegida.
4. Não expor identidade, localização, assinatura ou inicializador protegido em
   grafos, mensagens de erro, contagens ou saídas parciais.
5. Manter visíveis os membros comuns da batcave e sua própria existência.

Não há promessa de ocultação no código-fonte, Java gerado, bytecode ou saídas
explícitas do programa. A filtragem será uma política das ferramentas de investigação.

## Validação

`examples/secret_members.hero` demonstra campo e método secret na batcave,
método secret de personagem e variável local secret fora dela.

Os testes cobrem execução, mascaramento, permissões antigas, locais homônimos,
campos/métodos homônimos, callbacks, exceções, tipos inválidos, combinações
proibidas e preservação dos metadados. `tests/InvestigatorTests.java` cobre
a filtragem em find, incluindo buscas diretas, listagens, erros e execução pela CLI.
