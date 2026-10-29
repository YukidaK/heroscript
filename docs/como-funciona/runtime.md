# Runtime

O código gerado é fino: **as regras da linguagem ficam no runtime** (`src/heroscript/runtime`), em Java comum.

| Classe | Responsabilidade |
|---|---|
| `Fighter` | base de herói e vilão: atributos, poderes, gadgets, formas; regras de `unleash`, `shield`, `transform`, `revert`, `recharge`, `evolve`, `checkSecret` |
| `Hero` / `Villain` | os dois lados (heróis não atacam heróis; vilões não entram em squads) |
| `Power`, `Gadget`, `Form` | dados de cada habilidade |
| `Squad` | recrutamento com tipos permitidos e combos cooperativos |
| `Arena` | pilha de missões em andamento, relato da batalha, `announce` e encerramento |
| `HeroError` | erros de execução nomeados (`kind()`) |

## Exemplo: a regra do `unleash`

```java
public void unleash(String powerName, Fighter target) {
    requireAlive();                                           // CharacterDefeated
    Power p = powers.get(powerName);
    if (p == null) throw new HeroError("PowerNotFound", ...);
    if (target != null && target != this && this instanceof Hero && target instanceof Hero)
        throw new HeroError("FriendlyFire", ...);
    if (p.cooldownLeft > 0) throw new HeroError("PowerOnCooldown", ...);
    int cost = ...;                                           // forma ativa pode alterar o custo
    if (energy < cost) throw new HeroError("InsufficientEnergy", ...);
    energy -= cost;
    int damage = ...;                                         // forma ativa pode alterar o dano
    if (target != null) target.receive(damage, name);         // escudo + defesa da forma
    endAction(p);                                             // avança recargas e duração da forma
}
```

## Exemplo: `secret`

A `Arena` mantém uma pilha `(personagem, missão)`. A leitura só passa se o topo da pilha for uma missão autorizada
do próprio personagem:

```java
protected void checkSecret(String secret, String... allowedMissions) {
    if (!Arena.inMissionOf(this, allowedMissions))
        throw new HeroError("SecretAccessDenied", ...);
}
```

## Encerramento

`Arena.fail` imprime o erro não tratado e chama um *exit handler*. No terminal o `JavaRunner` troca o
`System.exit` por um sinal interno, para que o programa termine sem derrubar a JVM — o que é indispensável no
navegador, onde a mesma JVM executa vários programas seguidos.
