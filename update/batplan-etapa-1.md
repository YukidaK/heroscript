# Batplan — primeira etapa

Implementado como `batplan(referencia);`: desativação por identidade, compartilhada pelos aliases dentro da execução. Não remove declarações, entradas de listas ou memória física, nem aceita callback de limpeza.

Contrato e limites: [documentação de batplan](../docs/linguagem/batplan.md).

`new` e `summon` criam outra instância. `flashtravel` conserva a desativação anterior. Objetos independentes permanecem utilizáveis; erros tratados permitem continuar a execução. A integração com squads ignora membros inativos sem invalidar os outros.

## Catálogo para spiderSense

A interface de spiderSense permanece adiada para a última etapa. Reutilizar estes diagnósticos do compilador:

- Vermelho: sintaxe diferente de um único identificador entre parênteses; alvo inexistente, inacessível, primitivo, método, classe ou declarado com `var`.
- Vermelho: lambdas, referências de métodos, classes locais/anônimas e formas de mutação sem instrumentação suportada em programas com batplan.
- Em execução: `BatplanNullTarget` e `ObjectInactive`. Não apresentar como erro estático confirmado quando o estado depender do fluxo ou da identidade dos objetos.

Cobertura: `tests/BatplanTests.java`, `examples/batplan.hero` e sua saída esperada. Inclui aliases, objetos convencionais, listas, arrays, primitivos, referências compartilhadas, métodos, campos, secret, batcave, supervision, squads, flashtravel, deadpoolAction, criação de novas instâncias e isolamento entre execuções.
