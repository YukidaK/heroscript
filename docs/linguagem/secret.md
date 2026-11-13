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

## Variáveis locais e métodos

Além da regra de campos de personagens acima, `secret` pode marcar variáveis
locais, campos de batcave, métodos tipados de personagens e batcaves e missões:

```java
batcave Vault {
    secret String code = "EXEMPLO";

    secret boolean validate(String input) {
        return code.equals(input);
    }

    public boolean authenticate(String input) {
        return validate(input);
    }
}

battle {
    secret int ticket = 123;
    supervision.getValue(ticket); // [protegido]
    announce new Vault().authenticate("EXEMPLO");
}
```

Locais exigem tipo explícito e inicializador; `final` é opcional. Para métodos,
escreva `secret` antes de `public`, `private` ou `static`. Métodos secret e
`secret mission` suprimem eventos de supervision.getValue(durante a execução, inclusive
chamadas síncronas, restaurando a observação ao sair. `announce` e logs existentes
continuam funcionando. Retornos e cópias não herdam a marcação.

O `allow` permanece exclusivo dos campos de personagens); não é exigido para
campos de batcave, métodos ou variáveis locais. A regra de somente leitura
descrita acima é dos campos antigos; novos locais e campos de batcave podem
ser alterados normalmente, salvo se declarados `final`.

Não se pode combinar `secret` com `kryptonite`, nem marcar a própria batcave.
O [batinvestigate](batinvestigate.md) omite esses símbolos, inclusive dentro
de batcaves, e não percorre o corpo de métodos ou missões secret. Nome secret e
nome inexistente retornam a mesma resposta genérica. O comando único localiza
origens e não exibe implementações.
