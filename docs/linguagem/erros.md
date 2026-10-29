# Erros

## Em tempo de compilação

Detectados antes de executar, sempre com arquivo, linha e coluna.

| Situação | Exemplo de mensagem |
|---|---|
| sintaxe | `HeroSyntaxError em x.hero:6:5 — ';' faltando no valor` |
| propriedade inválida | `propriedade desconhecida 'dmg' em power Kick (esperado: cooldown, cost, damage)` |
| vilão em squad | `'Joker' é um villain e não pode ser membro de squad` |
| tipo desconhecido | `tipo desconhecido 'Batman' (declare-o com hero, villain ou squad)` |
| `allow` inválido | `secret 'identity' autoriza a missão 'X', que não existe em NightOwl` |
| comando sem personagem fora de missão | `'unleash' sem personagem só funciona dentro de uma mission; ...` |
| nome repetido | `'Rookie' já foi declarado` |
| erro no Java embutido | `HeroCompileError em x.hero:8 — incompatible types: ...` |

## Em tempo de execução: `HeroError`

| Erro | Quando |
|---|---|
| `InsufficientEnergy` | energia menor que o custo do poder |
| `PowerNotFound` | poder inexistente no personagem |
| `PowerOnCooldown` | poder usado antes de recarregar |
| `FriendlyFire` | herói tentando atacar outro herói |
| `GadgetNotFound` | gadget inexistente |
| `GadgetUnavailable` | gadget sem usos restantes |
| `FormNotFound` | forma inexistente |
| `TransformationExpired` | forma sem usos restantes |
| `SecretAccessDenied` | leitura de `secret` fora das missões autorizadas |
| `NotASquadMember` | tipo de herói não aceito pelo squad |
| `SquadUnavailable` | membros disponíveis insuficientes para o combo |
| `ComboNotFound` | combo inexistente |
| `CharacterDefeated` | personagem derrotado tentando agir |

Formato: `HeroError: <Tipo> — <detalhe>`. Trate com `try { ... } catch (HeroError e) { ... }`; sem tratamento o
programa termina com código `1`. O exemplo [Todos os erros](../exemplos/errors.md) dispara cada um deles.
