# Batcave

`batcave Nome { ... }` declara uma classe própria, com campos e métodos privados por padrão. Instâncias são criadas com `new Nome()`. A própria batcave não pode receber `secret`; seus campos, métodos e locais podem.

## Acesso de personagens

Vilões não podem construir batcaves, chamar seus métodos (inclusive estáticos) nem ler ou alterar seus campos nas operações instrumentadas da HeroScript. Uma tentativa gera `HeroError` de tipo `BatcaveAccessDenied`, antes de executar o corpo do método ou alterar o campo.

A autorização considera toda a cadeia síncrona de chamadas. Um vilão chamar um herói ou outro auxiliar para acessar a batcave também é negado. Métodos estáticos, missões e aliases por arrays passam pelas mesmas verificações. A negação não desativa a batcave: após tratar o erro, código comum autorizado continua podendo usá-la.

```java
batcave Vault {
    public int read() { return 42; }
}
villain Intruder {
    public int attempt(Vault vault) { return vault.read(); }
}
battle {
    Vault vault = new Vault();
    summon Intruder as intruder;
    try {
        announce intruder.attempt(vault);
    } catch (HeroError error) {
        announce error.kind(); // BatcaveAccessDenied
    }
    announce vault.read(); // 42
}
```

`check` valida sem executar: uma chamada pode compilar e ser negada em execução conforme o chamador real. Declarar um `villain` dentro da batcave continua sendo erro de sintaxe. `public` não concede uma exceção a vilões, e `allow` não é uma lista de autorização de batcave.

A verificação usa a cadeia ativa na thread atual. Não rastreia a origem de trabalhos assíncronos executados depois em outra thread. Reflexão e acessos internos de bibliotecas Java externas não são isolamento de segurança da JVM. No playground (CheerpJ) a verificação funciona da mesma forma: o exemplo `batcave` e a negação a vilões foram executados no navegador.

## Observação e investigação

Supervision fica suprimido durante métodos e inicializadores, inclusive em chamadas síncronas externas; o contexto é restaurado em `finally`. `announce` continua explícito. Batinvestigate localiza membros comuns e locais Java, inclusive privados, mas omite declarações `secret` e descendentes de métodos secret. Localizar a origem não concede acesso em execução.

Nesta etapa, campos são de instância e uma declaração por instrução; construtores explícitos, campos static/protected, classes aninhadas e throws na assinatura não são suportados.
