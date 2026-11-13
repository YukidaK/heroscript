# Hulksmash

`hulksmash (condição) { ... }` repete enquanto sua condição booleana for verdadeira. Em cada repetição, executa somente as regiões `blackwidow` do seu corpo, na ordem em que aparecem no arquivo.

```java
battle {
    int rage = 0;
    hulksmash (rage < 5) {
        announce "Ignored";
        if (false) {
            blackwidow {
                rage++;
                announce rage;
                if (rage == 3) {
                    hulkbuster;
                }
            }
        }
        hulkbuster;
    }
    announce "Finished";
}
```

O exemplo imprime `1`, `2`, `3` e `Finished`. O `if (false)` externo não é avaliado. O `if` interno funciona normalmente. O último `hulkbuster`, fora de `blackwidow`, é ignorado.

## Regiões executáveis

- `blackwidow { ... }` só é permitido dentro de um `hulksmash`.
- Fora das regiões executáveis, condições, inicializadores, chamadas e comandos comuns não são executados. Um `if`, `for` ou outro bloco ignorado funciona apenas como recipiente para localizar regiões `blackwidow`; seus cabeçalhos não produzem efeitos.
- Dentro de `blackwidow`, o código funciona normalmente, incluindo condições, loops, exceções e comandos HeroScript. Uma região `blackwidow` dentro de outra segue o fluxo normal da região externa.
- Cada região tem seu próprio escopo. Variáveis declaradas em instruções ignoradas não existem na execução; variáveis locais de uma região não ficam disponíveis em outra. Declare antes do `hulksmash` os valores que várias regiões precisam compartilhar.
- Um `hulksmash` aninhado possui suas próprias regiões e só é executado quando alcançado dentro de uma região ativa do loop externo. Suas regiões não são incorporadas ao loop externo.

As construções são analisadas sintaticamente, incluindo chaves, delimitadores e regras dos comandos HeroScript. Instruções Java ignoradas não são emitidas nem verificadas pelo javac; o código executável e a condição do `hulksmash` continuam sujeitos às verificações de tipos e escopo. Use chaves nos controles que contêm comandos HeroScript.

## Saída

`hulkbuster;` encerra o `hulksmash` mais próximo, mesmo quando está dentro de outro loop comum. Ele só produz efeito quando alcançado dentro de uma região `blackwidow`; fora dela, mas ainda dentro do `hulksmash`, é ignorado. Fora de qualquer `hulksmash`, é erro de compilação.

`break` e `continue` mantêm seu comportamento usual em relação ao loop mais próximo. `hulkbuster` não captura exceções nem pula blocos `finally` ativos. Um método pode retornar normalmente de dentro de uma região executável.

## Integração

`hulksmash` pode aparecer dentro de `flashPoint`. Somente instruções executáveis são destinos de `flashtravel`; cabeçalhos e instruções ignorados não viram destinos. Os saltos seguem as regras já existentes de escopo e inicialização. `hulkbuster` continua associado ao seu `hulksmash` lexical.

Um `deadpoolAction` executado dentro de `blackwidow` mantém seu sorteio, inclusive ao conter um `hulkbuster`. Fora de `blackwidow`, ele é uma estrutura ignorada: não ocorre sorteio; regiões `blackwidow` contidas nele são localizadas normalmente. A proibição de `flashtravel` dentro de `deadpoolAction` permanece para instruções executáveis. Flashtravel ignorado pelo hulksmash não produz salto e não exige flashPoint.

`batplan` funciona normalmente em regiões executáveis. A investigação estática conserva as origens do código-fonte, e `supervision.getMethod` mostra a estrutura sanitizada sem executar o loop.

## Avisos do spiderSense

Um `hulksmash` sem `blackwidow` é válido. Sua condição continua sendo avaliada; se permanecer verdadeira, ele pode repetir indefinidamente. Um `hulkbuster` ignorado também é válido. Esses casos produzem avisos amarelos do spiderSense, sem bloquear a compilação. Flashtravel fora de blackwidow também recebe aviso por ser ignorado.

Execute o exemplo com `./heroscript run examples/hulksmash.hero`.
