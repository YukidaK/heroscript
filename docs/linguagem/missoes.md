# Missões

Missões são os comportamentos de um personagem — os "métodos" da HeroScript.

```java
hero Striker {
    power Barrage { cost = 35; damage = 50; }

    mission Engage(Fighter captor) {          // parâmetros como em Java (opcional)
        unleash Barrage on captor;            // sem "striker.": é o próprio personagem
    }

    mission Rescue(Fighter captor, Medic medic) {
        Engage(captor);                       // chamar outra missão
        if (energy < 35) {
            medic.Support(this);              // missão de outro herói
        }
    }
}
```

No `battle`, missões são chamadas como métodos: `striker.Rescue(kidnapper, medic);`

## O que uma missão faz a mais que um método

- Registra o início no relato: `[mission] Striker inicia a missão Rescue`.
- Entra na pilha de missões da `Arena`, que controla o acesso a [`secret`](secret.md).
- Permite usar `unleash`, `transform`, `revert`, `evolve` **sem** indicar o personagem.

## Tipos úteis para parâmetros

| Tipo | Aceita |
|---|---|
| `Fighter` | qualquer herói ou vilão |
| `Hero` / `Villain` | só heróis / só vilões |
| `NomeDoTipo` | aquele tipo específico (ex.: `Medic`) |

Missões não retornam valores. Para guardar resultados, use atributos (`int rescues = 0;`).
