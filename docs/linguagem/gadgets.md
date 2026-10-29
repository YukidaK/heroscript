# Gadgets e `shield`

```java
gadget Armor {
    defense = 40;   // dano bloqueado (padrão 0)
    uses = 2;       // ativações permitidas (sem uses = ilimitado)
}
```

## Ativando: `shield(Gadget)`

```java
shield(Armor);           // dentro de uma mission
tony.shield(Armor);      // em qualquer lugar
```

Regras:

- O escudo protege contra **o próximo golpe** recebido e depois se desfaz.
- Cada ativação gasta um uso; sem usos restantes → `GadgetUnavailable`.
- Gadget inexistente → `GadgetNotFound`.
- A defesa de uma [forma](formas.md) (`defense += 10`) soma com a do escudo.

```text
[shield] Iron Guardian ativa Armor (defesa 40, 1 uso(s) restante(s))
[hit] Iron Guardian sofre 10 de dano (60 bloqueado) → HP 140
```

!!! note "Por baixo dos panos"
    O nome dentro de `shield(...)` é sempre o nome de um gadget: o compilador traduz `shield(Armor)` para
    `shield("Armor")`, e a verificação acontece em tempo de execução.
