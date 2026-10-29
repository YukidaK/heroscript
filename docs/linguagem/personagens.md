# Personagens: `hero` e `villain`

```java
hero SkyTitan {
    String name = "Sky Titan";   // nome exibido (padrão: o nome do tipo)
    int energy = 120;            // energia inicial e máxima (padrão 100)
    int health = 200;            // vida inicial e máxima (padrão 100)
    int rescues = 0;             // qualquer outro atributo Java também é permitido

    power HeatVision { cost = 20; damage = 55; }
}

villain Overlord {
    String name = "The Overlord";
    int health = 400;
}
```

## Atributos especiais

| Atributo | Padrão | Regra |
|---|---|---|
| `name` | nome do tipo | usado nas mensagens da batalha |
| `energy` | 100 | o valor inicial vira o **máximo**; `recharge` não passa dele |
| `health` | 100 | o valor inicial vira o **máximo**; chegando a 0 o personagem está derrotado |

Outros atributos (`int rescues = 0;`, `boolean enraged = false;`) viram campos públicos comuns e podem ser
usados em missões ou no `battle` (`hero.rescues++`).

## Herói × vilão

| | `hero` | `villain` |
|---|---|---|
| Pode atacar heróis | :x: (`FriendlyFire`) | :white_check_mark: |
| Pode entrar em [squad](squads.md) | :white_check_mark: | :x: (erro de compilação) |
| Poderes, gadgets, formas, missões, secrets | :white_check_mark: | :white_check_mark: |

## Criando personagens

No `battle` (ou numa missão), use `summon`:

```java
summon SkyTitan as titan;     // SkyTitan titan = new SkyTitan();
```

## Métodos disponíveis

Todo personagem é um `Fighter` (classe base do runtime) e oferece:

| Método | Retorno |
|---|---|
| `status()` | texto com vida, energia e forma atual |
| `isDefeated()` | `true` se a vida chegou a 0 |
| `maxEnergy()`, `maxHealth()` | valores máximos |
| `form()` | nome da forma ativa ou `"normal"` |
| `recharge(n)`, `shield(Gadget)` | ver [gadgets](gadgets.md) e [poderes](poderes.md) |

Os campos `name`, `energy` e `health` são públicos: `if (tony.energy < 30) { ... }`.
