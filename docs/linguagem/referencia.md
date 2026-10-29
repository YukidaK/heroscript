# Referência rápida

## Gramática (EBNF)

```ebnf
program     = { character | squad | battle } EOF ;
character   = ("hero" | "villain") IDENT "{" { member } "}" ;
member      = "power" propBlock | "gadget" propBlock | "form" propBlock
            | secret | mission | javaField ;
propBlock   = IDENT "{" { IDENT ("=" | "+=" | "-=" | "*=") expr ";" } "}" ;
secret      = "secret" type IDENT [ "=" expr ] [ "allow" IDENT { "," IDENT } ] ";" ;
mission     = "mission" IDENT [ "(" params ")" ] block ;
squad       = "squad" IDENT "{" { "members" IDENT { "," IDENT } ";" | "combo" propBlock } "}" ;
battle      = "battle" block ;

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
