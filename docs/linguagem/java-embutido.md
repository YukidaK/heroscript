# Java embutido

Tudo que não é um comando HeroScript é copiado para o Java gerado. Isso inclui variáveis, laços, condicionais,
`switch`, `try/catch`, arrays e chamadas de método.

```java
battle {
    summon Champion as champ;
    summon Brute as brute;
    summon Ninja as ninja;

    Villain[] rivals = { brute, ninja };
    int wins = 0;

    for (Villain rival : rivals) {
        while (!rival.isDefeated()) {
            unleash champ.Combo on rival;      // comando HeroScript dentro de laço Java
        }
        wins++;
    }
    announce "Vitórias: " + wins;
}
```

## Tratando erros

`HeroError` é uma exceção Java. `e.kind()` devolve o nome do erro e `e.getMessage()` a mensagem completa:

```java
try {
    unleash gambler.AllIn on vault;
} catch (HeroError e) {
    switch (e.kind()) {
        case "PowerOnCooldown" -> {
            unleash gambler.Bluff on vault;
        }
        default -> {
            announce e.getMessage();
        }
    }
}
```

## Limitações

- Comandos HeroScript dentro de `if`, `for`, `case` etc. precisam estar em um bloco `{ ... }`.
- Não há `import`: use nomes completos (`java.util.List<Hero> lista = new java.util.ArrayList<>();`).
- Dentro de lambdas (`x -> { ... }`) só Java puro é permitido.
- Erros no Java embutido são apontados na linha do `.hero`:

```text
HeroCompileError em java_error.hero:8 — incompatible types: java.lang.String cannot be converted to int
    Java gerado (linha 18): int x = "texto";
```
