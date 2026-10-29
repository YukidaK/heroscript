# Battle

`battle { ... }` é o ponto de entrada do programa — vira o `public static void main` do Java gerado.

```java
battle {
    summon IronGuardian as tony;
    summon Crusher as crusher;

    tony.SaveCity(crusher);
    unleash crusher.GroundSlam on tony;
    announce tony.status();
}
```

## Regras

- No máximo um `battle` por arquivo. Um arquivo sem `battle` pode ser validado (`check`), mas não executado.
- Fora de missões, os comandos precisam indicar o personagem: `unleash tony.RepulsorBlast`, não
  `unleash RepulsorBlast` (erro de compilação com dica).
- Um `HeroError` não tratado encerra o programa, mostra o erro e retorna código de saída `1`.

## Comandos

| Comando | Equivalente Java |
|---|---|
| `summon Tipo as var;` | `Tipo var = new Tipo();` |
| `unleash p.Poder on alvo;` | `p.unleash("Poder", alvo);` |
| `transform p.Forma;` / `revert p;` | `p.transform("Forma");` / `p.revert();` |
| `evolve p.Poder by n;` | `p.evolve("Poder", n);` |
| `recruit heroi into squad;` | `squad.recruit(heroi);` |
| `assemble squad.Combo on alvo;` | `squad.assemble("Combo", alvo);` |
| `announce expr;` | `Arena.announce(expr);` (imprime `>> ...`) |
