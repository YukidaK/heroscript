# Referência rápida

## Gramática (EBNF)

```ebnf
program     = { character | squad | batcave | entry } EOF ;        (* no máximo um entry *)
entry       = battle | flashPoint ;
character   = ("hero" | "villain") IDENT "{" { member } "}" ;
member      = "power" propBlock | "gadget" propBlock | "form" propBlock
            | secret | [ "secret" ] mission | [ "secret" ] javaMethod | javaField ;
batcave     = "batcave" IDENT "{" { [ "secret" ] ( javaField | javaMethod ) } "}" ;
propBlock   = IDENT "{" { IDENT ("=" | "+=" | "-=" | "*=") expr ";" } "}" ;
secret      = "secret" type IDENT [ "=" expr ] [ "allow" IDENT { "," IDENT } ] ";" ;
mission     = "mission" IDENT [ "(" params ")" ] block ;
squad       = "squad" IDENT "{" { "members" IDENT { "," IDENT } ";" | "combo" propBlock } "}" ;
battle      = "battle" block ;
flashPoint  = "flashPoint" block ;                                 (* pode conter um battle *)

block       = "{" { statement } "}" ;
statement   = block
            | "unleash"   target [ "on" expr ] ";"
            | "transform" target ";"
            | "revert"    [ IDENT ] ";"
            | "evolve"    target "by" expr ";"
            | "summon"    IDENT "as" IDENT ";"
            | "recruit"   IDENT "into" IDENT ";"
            | "assemble"  IDENT "." IDENT [ "on" expr ] ";"
            | "announce"  expr ";"
            | "secret"     [ "final" ] type IDENT "=" expr ";"
            | "kryptonite" [ "final" ] type IDENT "=" expr ";"
            | "supervision" "." "getValue"  "(" IDENT ")" ";"
            | "supervision" "." "getMethod" "(" methodRef ")" ";"
            | "batplan" "(" ( IDENT | "this" ) ")" ";"
            | "deadpoolAction" "(" "chance" "=" INT ")" block
            | flashPoint
            | "flashtravel" INT ";"                                (* só dentro de flashPoint *)
            | "hulksmash" "(" expr ")" block
            | "blackwidow" block                                   (* só dentro de hulksmash *)
            | "hulkbuster" ";"                                     (* só dentro de hulksmash *)
            | javaStatement ;
target      = [ IDENT "." ] IDENT ;
```

## Propriedades

| Bloco | Propriedades (padrão) |
|---|---|
| `power` | `cost` (0), `damage` (0), `cooldown` (0) |
| `gadget` | `defense` (0), `uses` (ilimitado) |
| `form` | `duration` (0 = até revert), `uses` (ilimitado), modificadores de `damage`, `defense`, `energy`, `health`, `cost` |
| `combo` | `cost` (0), `damage` (0), `requires` (1) |

## Palavras reservadas

`hero` `villain` `power` `gadget` `form` `secret` `mission` `squad` `members` `combo` `battle`
`unleash` `transform` `revert` `evolve` `summon` `recruit` `assemble` `announce`

Da expansão: `batcave` `kryptonite` `supervision` `flashPoint` `flashtravel` `deadpoolAction` `batplan`
`hulksmash` `blackwidow` `hulkbuster`. Cada uma tem sua página:
[batcave](batcave.md), [kryptonite](kryptonite.md), [supervision](supervision.md),
[flashPoint e flashtravel](flashtravel.md), [deadpoolAction](deadpool-action.md), [batplan](batplan.md),
[hulksmash, blackwidow e hulkbuster](hulksmash.md).

Contextuais (só especiais dentro do comando): `on` `by` `as` `into` `allow`.

## Relato da batalha

| Prefixo | Significado |
|---|---|
| `[unleash]` | poder usado |
| `[hit]` / `[down]` | dano recebido / personagem derrotado |
| `[shield]` | gadget ativado |
| `[transform]` / `[revert]` | forma ativada, expirada / desfeita |
| `[recharge]` / `[evolve]` | energia recuperada / poder evoluído |
| `[mission]` | missão iniciada |
| `[squad]` / `[assemble]` | recrutamento / combo |
| `>>` | `announce` |
| `[supervision]` | valor observado por `supervision.getValue` |
| `[supervision.getMethod]` | estrutura de método mostrada por `supervision.getMethod` |

## Linha de comando

| Comando | O que faz |
|---|---|
| `./heroscript run arquivo.hero` | transpila, compila e executa |
| `./heroscript check arquivo.hero` | valida o programa, incluindo o Java embutido, sem executar |
| `./heroscript build arquivo.hero [-o pasta]` | grava o Java gerado |
| `./heroscript tokens arquivo.hero` | mostra os tokens do lexer |
| `./heroscript spiderSense arquivo.hero` | [diagnósticos](spider-sense.md) em JSON, sem executar |
| `./heroscript batinvestigate pasta-ou-arquivo símbolo` | [origens](batinvestigate.md) de um símbolo não `secret` |
