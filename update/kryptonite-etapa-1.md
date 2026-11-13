# kryptonite: contrato inicial e conflitos

**Estado:** primeira etapa implementada para variáveis locais em `battle`, `mission`
e métodos de batcave, com máscara no `supervision`. Na batcave, eventos internos
são completamente suprimidos. O comando único batinvestigate localiza os locais
kryptonite, sem exibir conteúdo. Campos kryptonite continuam pendentes.
Complementa os contratos iniciais de `supervision` e `batcave`.

## Papel do recurso

`kryptonite` marca uma declaração para impedir que `supervision` observe seu valor.
O programa continua podendo ler e alterar a variável pelas regras normais.
O futuro `batinvestigate` pode localizar a declaração, analisar seus usos e
mostrar seu código, inclusive literais presentes no fonte.

Não concede nem retira permissões de leitura do programa. Essa diferença
mantém um papel próprio em relação a `secret ... allow`.

## Primeira etapa sugerida

Começar com variáveis locais em `battle` e `mission`, de tipo primitivo ou `String`:

```hero
battle {
    kryptonite int reserve = 42;
    int visible = 7;

    supervision.getValue(reserve);
    reserve += 8;
    supervision.getValue(reserve);
    supervision.getValue(visible);

    if (reserve == 50) {
        announce "Cálculo correto";
    }
}
```

As duas observações de `reserve` mostram `[protegido]`, com o nome, arquivo e linha;
a de `visible` mostra 7. O cálculo e o teste continuam funcionando.
Nem 42 nem 50 devem aparecer nos eventos de observação de `reserve`.

Regras da sintaxe proposta:

- Uma declaração por instrução, com tipo explícito e inicializador obrigatório.
- Permitir `kryptonite final int reserve = 42;`; `final` mantém a regra de imutabilidade Java.
- Rejeitar repetição do modificador, múltiplas variáveis na mesma declaração,
  `var`, arrays, objetos e marcação de parâmetros ou retornos nesta etapa.
- A marcação pertence à declaração resolvida, durante toda sua vida no escopo.
  Reatribuir o valor não remove a marcação.
- Variável fora do escopo continua inválida, mesmo que sua observação fosse mascarada.

Campos de personagens e batcaves ficam para uma segunda etapa. Isso evita
prometer observação de membros quando o contrato inicial de `supervision`
aceita somente identificadores simples.

## Matriz de interação

| Situação | Programa | supervision.getValue(| batinvestigate futuro |
|---|---|---|---|
| Variável comum | Regras normais | Mostra valor suportado | Localiza e analisa |
| `kryptonite` | Regras normais | `[protegido]`, sem ler valor | Localiza e analisa o fonte |
| `secret` | Respeita `allow` | Mascara se autorizado); mantém erro se não autorizado | Oculta símbolo e seus detalhes |
| `secret` + `kryptonite` | Combinação proibida | Não compila | Não se aplica |
| Dentro de `batcave` | Regras de acesso da batcave | Nenhum evento interno, nem marcador | Investigação futura |

A combinação `secret` + `kryptonite` é proibida por decisão do usuário,
em qualquer ordem. Não está prevista sua implementação futura.
Missão autorizada a ler `secret` não passa a poder observá-lo pelo `supervision`.

## Conflitos que precisam ficar explícitos

### Marcação por declaração e cópias

Na primeira versão, a proteção não se propaga automaticamente:

```hero
kryptonite int reserve = 42;
int copy = reserve;
supervision.getValue(reserve); // [protegido]
supervision.getValue(copy);    // 42: copy não foi marcada
```

Esse comportamento é uma limitação deliberada da proposta inicial, não uma
garantia de sigilo sobre o valor em todo o programa. Para ocultar também cópias,
operações, parâmetros e retornos derivados, precisaríamos definir e implementar
propagação de marcações. Se essa for a intenção do recurso, o contrato deve ser
ampliado antes da implementação; uma simples filtragem por nome não atende.

### Impressão explícita e código-fonte

`announce reserve;` continua imprimindo o valor. `System.out`, logs existentes,
Java gerado e acesso direto ao fonte não são filtrados por esse modificador.
Da mesma forma, investigação estática pode mostrar `reserve = 42` no código;
isso não equivale a observar o valor atual de execução.

### Resolução de símbolos

Não usar uma lista global de nomes protegidos. Duas missões podem declarar
`reserve` com marcações diferentes, sem interferência entre elas.
Blocos seguem as regras de escopo Java: redeclarar uma variável local em um
escopo sobreposto continua inválido. Testar nomes iguais em escopos independentes
e, na etapa de campos, sombreamento de campo por variável local.

### Validação antes da ocultação

Uma variável inexistente, fora de escopo ou com tipo não suportado deve gerar
diagnóstico, em vez de ser silenciosamente transformada em `[protegido]`.
Após validar a referência e suas permissões, gerar o evento mascarado sem
incluir acesso ao valor, serialização ou chamada a getter no caminho de observação.
O inicializador da declaração continua executando normalmente, uma única vez.

## Critérios de aceitação

1. O exemplo principal gera duas observações protegidas, uma pública com 7 e
   a mensagem de cálculo correto, com código de saída 0.
2. Reatribuição, incremento e uso em condições preservam o comportamento normal
   e mantêm a marcação da declaração.
3. Valores protegidos de cada tipo suportado, inclusive `String` nula e Unicode,
   produzem o mesmo marcador, sem formatação do valor.
4. Inicializador com efeito colateral permitido é executado uma única vez;
   observar depois não repete esse efeito.
5. Uma missão com `kryptonite int reserve` não mascara `int reserve` de outra missão.
6. Fora de escopo, tipo incompatível, modificador repetido e formas não suportadas
   falham com diagnóstico. `check` valida a nova construção sem executá-la,
   conforme a resolução de símbolos prevista no contrato de `supervision`.
7. `final` impede reatribuição; não é removido junto com o modificador temático.
8. A cópia não marcada e a impressão explícita seguem os limites documentados.
9. Testes inspecionam também o Java gerado para confirmar que o evento protegido
   não recebe o valor, e não apenas que a saída final foi substituída.
10. Os 16 testes atuais mantêm saídas e códigos de retorno.

Batcave e execuções consecutivas na mesma JVM já possuem testes. A rejeição da
combinação `secret` + `kryptonite` também é testada. Quando houver investigação
estática, adicionar seus testes. Usar um valor
sentinela exclusivo e verificar sua ausência nos eventos protegidos, junto da
presença de uma observação pública, para detectar supressão excessiva.

## Ordem sugerida

1. Resolver variáveis e escopos para `supervision`.
2. Reconhecer declarações locais `kryptonite` e preservar sua semântica Java.
3. Integrar a máscara sem avaliar o valor observado e executar os testes acima.
4. A integração com batcave foi implementada. Ampliar para campos e investigação
   em etapas próprias, mantendo proibida a combinação com `secret`.

O exemplo `examples/vision.hero` e os testes `tests/FeatureTests.java` validam
a etapa local implementada. As linhas sobre recursos futuros continuam sendo propostas.
