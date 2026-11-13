# Supervision: contrato implementado

Supervision substitui smanvision, com nomes corretos em inglês:

```hero
supervision.getValue(variable);
supervision.getMethod(Worker.calculate);
supervision.getMethod("Worker.calculate(int)");
```

## getValue

Mostra o valor atual de uma variável acessível, com arquivo e linha da sua
declaração. Aceita identificador simples de tipo primitivo ou String explícito,
incluindo locais, parâmetros simples e campos suportados em contexto.
O escopo é o da chamada: não consulta campos externos, locais de outro método
ou variáveis de blocos encerrados. Em battle, observa apenas suas variáveis.

Secret e kryptonite produzem marcador sem leitura do valor. Campos antigos
secret de personagens preservam allow. Não aceita expressões, chamadas,
objetos, arrays, var ou acesso por ponto nesta etapa.

## getMethod

Descreve a estrutura de métodos tipados e missões da própria classe, sem executar
o alvo. Métodos tipados comuns agora também são aceitos em hero e villain.
Condições, laços, atribuições, chamadas e retornos suportados são reconstruídos
a partir da AST; comentários originais não são preservados.

Pode receber nome simples, this, identificação da própria classe, alias conhecido
desse mesmo tipo ou seleção textual com tipos dos parâmetros para resolver
sobrecargas. A classe em que a instrução está escrita é a única autorizada;
não há busca global nem fallback em outra classe. Em battle, não inspeciona
classes; a operação retorna indisponível. Ambiguidade pede seleção mais específica.
Método secret, de outra classe, externo, inexistente ou de batcave
recebe a mesma mensagem genérica de indisponibilidade.

Referências resolvidas a secret e operações que possam expor kryptonite são
omitidas. Receptores conhecidos são diferenciados por tipo, para que um campo
público não seja ocultado somente por ter nome igual a um secret de outra classe.
Em referência potencialmente protegida sem resolução suficiente, omitir a operação
e indicar análise parcial. Não há resolução completa de Java, expansão de chamadas
ou rastreamento de valores derivados. Operações temáticas sem representação
detalhada recebem marcador de análise parcial. Limite de relatório: 24 mil caracteres.

## Fronteiras comuns

Ambas as operações são suprimidas dentro de batcave e durante execução de
métodos/missões secret, inclusive em chamadas síncronas externas. O contexto
por thread é restaurado em finally. Tarefas assíncronas não herdam a supressão.
Announce e logs existentes continuam explícitos; cópias e retornos não herdam
automaticamente as marcações. A combinação secret + kryptonite permanece proibida.

Batinvestigate continua separado: localiza todas as origens visíveis com arquivo
e linha, sem mostrar implementações. Pode localizar membros comuns de batcave;
supervision não mostra seu interior. As ferramentas não ocultam o fonte de quem
tem acesso direto ao arquivo ou ao Java gerado.

## Migração e validação

O comando antigo smanvision foi removido. Os exemplos, saídas esperadas, testes
e documentação foram migrados para supervision.getValue. A localização agora
é a declaração, não o ponto em que se faz a observação.

`examples/supervision.hero` demonstra as duas operações. `tests/SupervisionTests.java`
verifica seleção, estrutura permitida, ausência de execução, sobrecargas,
mascaramento, homônimos, receptores, análise parcial e contextos protegidos.
As suítes anteriores continuam verificando permissões, exceções e regressões.

Referência de uso: `docs/linguagem/supervision.md`.
