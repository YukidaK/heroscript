# Squads e combos

```java
squad DawnLeague {
    members ShadowKnight, SkyTitan, StormRider;   // tipos aceitos (opcional)

    combo JusticeStorm {
        cost = 60;       // custo total, dividido entre os participantes
        damage = 180;
        requires = 3;    // membros necessários (padrão 1)
    }
}
```

## Usando

```java
summon DawnLeague as league;
recruit knight into league;              // league.recruit(knight)
assemble league.JusticeStorm on overlord;
```

## Regras

- Apenas heróis entram em squads. Um `villain` em `members` ou em `recruit` é **erro de compilação**.
- Se `members` foi declarado, só aqueles tipos são aceitos → `NotASquadMember`.
- Cada participante paga `custo ÷ requires` (arredondado para cima).
- Participam os primeiros `requires` membros **vivos e com energia para sua parte**; se não houver membros
  suficientes → `SquadUnavailable`.
- Combo inexistente → `ComboNotFound`.
- Combo contra um herói → `FriendlyFire`, antes de descontar energia ou aplicar dano. Combos sem alvo ou contra vilões continuam permitidos.

`league.members()` devolve a lista de heróis (útil em `for (Hero h : league.members())`).

Veja o exemplo [Squad e combos](../exemplos/squad.md).
