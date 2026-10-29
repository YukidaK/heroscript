# Formas e `transform`

Uma forma altera atributos do personagem enquanto está ativa.

```java
form Beast {
    duration = 2;   // ações até voltar ao normal (padrão 0 = até revert)
    uses = 2;       // quantas vezes pode ser ativada (padrão ilimitado)
    damage *= 3;    // modificadores...
    defense += 10;
}
```

## Modificadores

| Atributo | Efeito | Quando |
|---|---|---|
| `damage` | dano dos poderes | a cada `unleash` |
| `cost` | custo dos poderes | a cada `unleash` |
| `defense` | defesa ao receber golpes (soma ao escudo) | a cada golpe recebido |
| `energy` | energia atual | uma vez, ao transformar |
| `health` | vida atual | uma vez, ao transformar |

Operadores: `=`, `+=`, `-=`, `*=`. `duration` e `uses` só aceitam `=`.

## Comandos

```java
transform Beast;            // dentro de uma mission
transform shifter.Beast;    // em qualquer lugar
revert shifter;             // volta ao normal (revert; dentro da mission)
```

Regras:

- Transformar já transformado reverte a forma anterior primeiro.
- Com `duration`, a forma expira sozinha após N ações (`[transform] a forma Beast de Shifter expirou`).
- Ao reverter, energia e vida acima do máximo são cortadas.
- Sem usos restantes → `TransformationExpired`; forma inexistente → `FormNotFound`.

Veja o exemplo completo em [Transformações](../exemplos/transformations.md).
