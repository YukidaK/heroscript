# Luta contra chefe

Um vilão com missão própria, gadget e uma forma de fúria quando perde metade da vida.

[:material-play: Abrir no playground](../playground/index.html?example=boss_fight){ .md-button .md-button--primary }

=== "boss_fight.hero"

    ```java
    --8<-- "examples/boss_fight.hero"
    ```

=== "Saída"

    ```text
    --8<-- "tests/expected/boss_fight.out"
    ```

## O que observar

- O vilão usa um atributo Java (`boolean enraged`) para entrar em fúria só uma vez.
- O escudo do Paladin tem 3 usos; depois disso o `catch` avisa que ele está sem escudo.
- Nem todo herói vence: com o dano dobrado do dragão, o Paladin cai na rodada 5.
