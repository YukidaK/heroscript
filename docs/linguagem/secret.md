# Identidade secreta: `secret`

```java
hero NightOwl {
    secret String identity = "Daniel Dreiberg" allow Unmask;
    secret int hideouts = 3 allow Unmask, Patrol;

    mission Unmask { announce "sou " + identity; }      // permitido
    mission Patrol { announce "sou " + identity; }      // SecretAccessDenied
}
```

## Por que não é só `private`?

`private` libera o acesso para **qualquer código da classe**. Um `secret` só pode ser lido enquanto uma das
**missões listadas em `allow`**, do próprio personagem, estiver em execução:

| Onde a leitura acontece | Resultado |
|---|---|
| missão listada em `allow` | :white_check_mark: |
| outra missão do mesmo personagem | `SecretAccessDenied` |
| `battle` ou outro personagem (`owl.identity`) | `SecretAccessDenied` |

- O controle é feito em tempo de execução: a `Arena` mantém a pilha de missões em andamento.
- `secret` é somente leitura depois de declarado.
- `allow` citando uma missão que não existe é erro de compilação.

Veja o exemplo [Identidade secreta](../exemplos/secret.md).
