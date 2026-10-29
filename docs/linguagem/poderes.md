# Poderes e `unleash`

## Declarando

```java
power UniBeam {
    cost = 40;      // energia gasta (padrão 0)
    damage = 90;    // dano causado (padrão 0)
    cooldown = 2;   // ações do personagem até poder usar de novo (padrão 0)
}
```

Os valores podem ser expressões Java (`damage = 20 * 3;`). Propriedades desconhecidas são erro de compilação:

```text
HeroSyntaxError em typo.hero:4:9 — propriedade desconhecida 'dmg' em power Kick (esperado: cooldown, cost, damage)
```

## Usando: `unleash`

```java
unleash RepulsorBlast on target;        // dentro de uma mission: o próprio personagem
unleash tony.RepulsorBlast on crusher;  // em qualquer lugar: personagem.Poder
unleash medic.Adrenaline;               // sem alvo
```

O alvo depois de `on` é qualquer expressão Java que resulte em um personagem.

### O que a linguagem verifica, nesta ordem

| # | Regra | Erro |
|---|---|---|
| 1 | o personagem não está derrotado | `CharacterDefeated` |
| 2 | o poder existe | `PowerNotFound` |
| 3 | herói não ataca outro herói | `FriendlyFire` |
| 4 | o poder não está em recarga | `PowerOnCooldown` |
| 5 | há energia suficiente | `InsufficientEnergy` |

Depois disso: a energia é descontada, o dano é calculado (aplicando a [forma](formas.md) ativa) e o alvo recebe o
dano menos a defesa do [escudo](gadgets.md). Por fim a **ação termina**: as recargas avançam uma unidade e a forma
ativa perde uma ação de duração.

!!! info "Recarga é contada em ações"
    `cooldown = 2` significa que o poder fica indisponível durante as **duas próximas ações bem-sucedidas** do
    mesmo personagem. Uma tentativa que falha não conta como ação. Veja o exemplo [Recarga](../exemplos/cooldown.md).

## Evoluindo: `evolve`

```java
evolve kid.Spark by 5;   // +5 de dano permanente
```

## Recuperando energia

`recharge(n)` é um método: dentro de uma missão `recharge(50);`, fora dela `tony.recharge(50);`. A energia nunca
passa do máximo (exceto quando uma forma a elevou).
