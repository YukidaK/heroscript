# Exemplos

Todos os exemplos estão em [`examples/`](https://github.com/YukidaK/heroscript/tree/main/examples),
são executados pelos testes automáticos e podem ser abertos no playground. A saída mostrada é a saída real.

| Exemplo | Mostra | |
|---|---|---|
| [Olá, herói](hello.md) | O menor programa completo: um herói, um vilão e dois golpes. | [▶](../playground/index.html?example=hello) |
| [Iron Guardian](iron.md) | O exemplo da proposta original: herói com armadura, dois poderes, uma forma temporária e uma missão. | [▶](../playground/index.html?example=iron) |
| [Recarga de poderes](cooldown.md) | Um poder forte com `cooldown = 2` alternando com um fraco enquanto recarrega. | [▶](../playground/index.html?example=cooldown) |
| [Transformações](transformations.md) | Formas com duração, número de usos, modificadores e `revert`. | [▶](../playground/index.html?example=transformations) |
| [Treino com evolve](evolve_loop.md) | Um laço `while` do Java em que o poder evolui a cada golpe. | [▶](../playground/index.html?example=evolve_loop) |
| [Identidade secreta](secret.md) | `secret` com acesso liberado por missão — diferente de `private`. | [▶](../playground/index.html?example=secret) |
| [Squad e combos](squad.md) | Três heróis com estilos diferentes (gadgets, poderes naturais) cooperando. | [▶](../playground/index.html?example=squad) |
| [Missão de resgate](rescue.md) | Missões com parâmetros que chamam outras missões, inclusive de outro herói. | [▶](../playground/index.html?example=rescue) |
| [Torneio](tournament.md) | Arrays e for-each do Java: o campeão enfrenta vários vilões em sequência. | [▶](../playground/index.html?example=tournament) |
| [Luta contra chefe](boss_fight.md) | Um vilão com missão própria, gadget e uma forma de fúria quando perde metade da vida. | [▶](../playground/index.html?example=boss_fight) |
| [Tratando erros](catch_errors.md) | `switch` do Java sobre `e.kind()` para reagir a cada tipo de `HeroError`. | [▶](../playground/index.html?example=catch_errors) |
| [Todos os erros](errors.md) | Dispara e trata, em sequência, cada `HeroError` da linguagem. | [▶](../playground/index.html?example=errors) |
| [Batcave](batcave.md) | Uma `batcave` com membros privados por padrão: o `supervision` não enxerga nada lá dentro. | [▶](../playground/index.html?example=batcave) |
| [Membros secret](secret_members.md) | `secret` em campos, métodos e variáveis locais, dentro e fora de uma batcave. | [▶](../playground/index.html?example=secret_members) |
| [Supervision e kryptonite](vision.md) | `supervision.getValue` observando variáveis comuns e uma variável `kryptonite`. | [▶](../playground/index.html?example=vision) |
| [Supervision](supervision.md) | `getValue` mostra o valor de uma variável; `getMethod` mostra a estrutura de um método sem executá-lo. | [▶](../playground/index.html?example=supervision) |
| [Flashtravel](flashtravel.md) | `flashPoint` como entrada do programa e saltos para linhas físicas com `flashtravel`. | [▶](../playground/index.html?example=flashtravel) |
| [Deadpool action](deadpool_action.md) | Blocos que só executam se um sorteio permitir, com `chance` de 0 a 100. | [▶](../playground/index.html?example=deadpool_action) |
| [Batplan](batplan.md) | `batplan(objeto)` desativa uma instância e todos os seus aliases. | [▶](../playground/index.html?example=batplan) |
| [Hulksmash](hulksmash.md) | Um laço que executa somente as regiões `blackwidow` do seu corpo. | [▶](../playground/index.html?example=hulksmash) |

Para rodar localmente: `./heroscript run examples/<nome>.hero`.
