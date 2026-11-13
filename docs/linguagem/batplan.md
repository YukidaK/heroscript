# Batplan

`batplan(objeto);` desativa uma instância pela identidade. Todas as referências ao mesmo objeto passam a apontar para uma instância inativa; outros objetos continuam funcionando.

```java
hero Worker {
    public int read() { return health; }
}
battle {
    summon Worker as original;
    Worker alias = original;
    batplan(original);
    try {
        announce alias.read();
    } catch (HeroError error) {
        announce error.kind(); // ObjectInactive
    }
    original = new Worker();
    announce original.read(); // 100; alias continua inativo
}
```

## Regras

- O alvo deve ser uma referência simples com tipo explícito, acessível no escopo. `this` também é aceito em contexto de instância.
- Objetos convencionais, personagens, squads, instâncias de batcave e arrays podem ser desativados. Primitivos, métodos e classes não são alvos. `var` não é aceito como tipo do alvo nesta etapa.
- `String` e tipos encapsulados, como `Integer`, são objetos: referências compartilhadas por internamento ou cache representam a mesma identidade. Valores primitivos permanecem independentes.
- Repetir `batplan` no mesmo objeto é permitido. Um alvo `null` gera `BatplanNullTarget`.
- Usar uma referência inativa em uma operação verificada gera `HeroError` de tipo `ObjectInactive`. O erro pode ser tratado com `catch`; sem tratamento, a execução para normalmente como nos demais erros.
- O comando não apaga variáveis, métodos ou classes, não chama limpeza automática e não força liberação de memória pela JVM.
- Listas e arrays conservam suas entradas, posições e tamanho. Desativar um elemento não desativa o recipiente nem seus outros elementos. A API gerenciada `Squad.members()` apresenta apenas membros ativos.
- `flashtravel` não reativa objetos. Reexecutar `new` ou `summon` cria uma instância independente; aliases antigos continuam inativos.
- Um método já em andamento pode continuar computando valores locais e usando outros objetos. Novas chamadas e acessos ao estado da instância inativa são bloqueados.
- A execução iniciada pelo compilador mantém seu próprio registro de objetos inativos; uma nova execução começa com outro registro.

## Alcance desta etapa

A verificação é feita no código gerado da HeroScript e nas operações instrumentadas do runtime. Ela não altera a JVM: código interno de bibliotecas Java externas e reflexão podem conservar e utilizar referências sem passar por essas verificações. `batplan` não funciona como isolamento de segurança para Java arbitrário.

Programas que usam `batplan` rejeitam lambdas, referências de métodos e classes Java locais ou anônimas, cujos acessos ainda não são instrumentados consistentemente. Mutações de referências por operadores compostos e incrementos são verificadas em instruções simples; formas complexas, mutações compostas de elementos de arrays e incrementos de referências em cabeçalhos ou expressões devem ser reescritos em instruções separadas, conforme o diagnóstico do compilador.

`supervision.getMethod` apenas inspeciona o código: não executa `batplan`. A investigação estática de origens também não depende do estado dos objetos em execução. As regras de acesso de `secret` continuam valendo.

Exemplo executável: `./heroscript run examples/batplan.hero`.
