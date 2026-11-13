# deadpoolAction

`deadpoolAction(chance = N) { ... }` sorteia se um bloco será executado cada
vez que a execução alcançar sua entrada. Chance é uma porcentagem inteira
literal de 0 a 100. Não existe parâmetro seed na linguagem.

```java
battle {
    deadpoolAction(chance = 50) {
        announce "Today I decided to work!";
    }
    announce "The program continues";
}
```

O primeiro announce poderá executar ou ser ignorado; o segundo continua sendo
alcançado normalmente. Não há mensagem automática quando o bloco é ignorado.
O nome tem A maiúsculo: `deadpoolAction`.

## Sorteio e fluxo

- Cada entrada faz um novo sorteio; a decisão não é armazenada entre passagens.
- A chamada ao sorteio também ocorre nos limites 0 e 100, com resultados
  respectivamente “ignorar sempre” e “executar sempre”.
- Em loops, o bloco decide novamente a cada iteração em que sua entrada for alcançada.
- Blocos aninhados só chegam ao próprio sorteio se a execução entrar neles;
  o sorteio do bloco externo pode impedir que o interno seja alcançado.
- Se ignorado, nenhum efeito do corpo ocorre, incluindo inicializadores e chamadas.
- Se executado, atribuições, return, break, continue e exceções mantêm seus efeitos
  normais. O recurso não captura nem esconde exceções reais.
- Variáveis declaradas no corpo ficam no seu escopo. O código inteiro continua
  sendo validado pelo compilador, mesmo quando chance é 0.

A operação pode aparecer em battle, missões, métodos e flashPoint, respeitando
os limites das construções que a contêm. Não é uma expressão com valor de retorno.
Nesta versão chance é obrigatória e literal: variáveis, expressões, frações,
seed e outros parâmetros não são aceitos.

## Relação com flashtravel

**A execução do corpo deve sempre passar pelo sorteio da entrada.**

1. Flashtravel não pode estar escrito dentro do corpo, mesmo em if, loops,
   flashPoints ou outros deadpoolActions internos, e mesmo com chance 0.
2. Um flashtravel externo não pode apontar para linhas internas do corpo,
   incluindo cabeçalhos de loops e outros blocos internos.
3. Pode apontar para a linha em que começa a instrução deadpoolAction. Nesse
   caso o sorteio é executado novamente antes de uma possível entrada no corpo.
4. Se abertura e corpo compartilham uma linha, o destino é a entrada da instrução,
   com sorteio. Uma linha com instrução interna antes de uma instrução externa
   não pode ser usada para contornar a restrição.

Essas regras são verificadas na compilação. Continuam valendo os limites de
flashPoint, como a proibição atual de flashPoints aninhados. Um método chamado
pelo corpo pode usar seu próprio flashPoint: o sorteio externo já ocorreu e
os saltos desse método continuam limitados à própria região.

## Ferramentas e proteção

Supervision conserva suas regras de classe/escopo e as proteções de secret,
kryptonite e batcave. GetMethod pode descrever um deadpoolAction permitido,
mostrando sua chance e a estrutura filtrada, sem fazer o sorteio nem executar
o corpo. Batinvestigate continua localizando declarações visíveis dentro do
bloco, sem revelar conteúdo e sem listar símbolos secret.

Check e a análise de fonte não fazem sorteios. O [spiderSense](spider-sense.md) avisa, sem impedir a
compilação, sobre todo `deadpoolAction` executável com chance menor que 100.

## Exemplo reproduzível

O exemplo de regressão usa 0% e 100% para ter saída estável sem seed:

```java
--8<-- "examples/deadpool_action.hero"
```

```bash
./heroscript run examples/deadpool_action.hero
```
