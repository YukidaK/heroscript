# Supervision: values and method structure

`supervision` substitui o antigo comando `smanvision`. As duas operações são
instruções terminadas em ponto e vírgula no programa `.hero`:

```java
hero Worker {
    public int doubleValue(int input) {
        return input * 2;
    }

    public void inspect() {
        supervision.getMethod(doubleValue);
    }
}

battle {
    int count = 3;
    supervision.getValue(count);
    summon Worker as worker;
    worker.inspect();
}
```

## getValue

`supervision.getValue(variable);` mostra o valor **atual** e a localização da
declaração da variável, não a linha da observação. Não altera a variável.
Aceita identificador simples de tipo primitivo ou String explícito: locais,
parâmetros simples e campos suportados do personagem em contexto.
Não pesquisa campos de outras classes ou locais de outros métodos. Um local
só pode ser observado enquanto estiver no escopo; em `battle`, são aceitas
suas próprias variáveis. Parâmetros recebidos pertencem ao método atual,
independentemente de onde veio o valor passado pelo chamador.

```text
[supervision] example.hero:9 count = 3
```

Chamadas, operações como `count++`, acesso por ponto, objetos, arrays e `var`
não são aceitos como argumento nesta etapa. A localização é obtida automaticamente;
não é preciso informar o arquivo. A operação imprime um relatório, não é uma
expressão que retorna o valor para atribuição.

- `secret` e `kryptonite` produzem `[protegido]`, sem ler ou passar o valor ao log.
- Campos antigos `secret ... allow` continuam exigindo missão autorizada.
- Em batcave ou método/missão secret, não há evento, nem mesmo marcador.
- Cópias e retornos não herdam automaticamente a proteção.

## getMethod

`supervision.getMethod(doubleValue);`, escrito dentro de Worker, mostra a
estrutura do método da **própria classe**, incluindo atribuições, condições, laços, chamadas e retornos
permitidos. **Não chama o método, não percorre recursivamente suas chamadas
e não prevê qual ramo será executado.** O relatório é preparado a partir da AST
durante a compilação e impresso quando a instrução de observação é alcançada.

Também aceita seleção textual, útil para sobrecargas:

```java
supervision.getMethod("Worker.doubleValue(int)");
supervision.getMethod(Worker.doubleValue(int));
```

As chamadas acima devem estar escritas dentro de Worker. O conteúdo entre
parênteses identifica um método; não fornece argumentos de execução.
`this.doubleValue`, `Worker.doubleValue` e aliases conhecidos do tipo Worker
são aceitos dentro dessa classe, sem avaliar a instância. A seleção fica sempre
limitada à classe em que a instrução foi escrita. Um nome sem classe é resolvido
somente entre os métodos dessa classe, sem procurar candidatos em outras.

Em `battle`, getMethod retorna indisponível porque não há uma classe de
personagem declarando a chamada. Battle pode chamar um método de Worker que
contenha sua própria inspeção, como no exemplo. A restrição é pelo local onde
a instrução está escrita, enquanto a supressão de batcave/secret continua
acompanhando a execução.

Métodos Java tipados comuns e secret são aceitos em `hero` e `villain`, além
das missões existentes. Uma seleção ambígua solicita classe e tipos dos parâmetros.
Método inexistente, secret, de outra classe, externo ou pertencente a batcave recebe a mesma mensagem:

```text
[supervision.getMethod] method unavailable
```

Referências a campos, locais ou métodos secret não são impressas. Declarações
kryptonite e operações que possam expor seus valores também são omitidas.
O relatório substitui operações afetadas por `/* protected or unresolved operation */`
e indica análise parcial. Referências com receptor conhecido são resolvidas pelo
tipo; referências potencialmente protegidas sem resolução suficiente são omitidas.
Não há análise completa do sistema de tipos Java nem propagação de marcações
para cópias e resultados derivados. Operações temáticas sem análise detalhada
recebem marcador de análise parcial. Comentários originais não são preservados.

A saída é uma reconstrução da estrutura permitida, não uma cópia literal do
arquivo. Relatórios acima de 24 mil caracteres recebem aviso de limite, sem
despejar trechos incompletos. Métodos e missões secret e batcaves suprimem também
getMethod durante sua execução; o contexto é restaurado em `finally`, por thread.
Não há propagação automática para tarefas assíncronas.

## Relação com batinvestigate

`batinvestigate` permanece uma busca de **origens**, com caminho e linha, sem
mostrar conteúdo. Pode localizar membros comuns de batcave. Supervision observa
valores e descreve implementações permitidas; não revela o interior da batcave.
Nenhuma das operações é uma barreira ao acesso direto ao fonte ou ao Java gerado.

Para experimentar:

```powershell
java -cp build/classes heroscript.compiler.Main run examples/supervision.hero
```
