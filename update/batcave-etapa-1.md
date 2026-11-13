> Atualização de 13/11/2026: autorização síncrona da batcave, locais Java no batinvestigate, FriendlyFire em combos e literais foram corrigidos. Contrato atual: [correcoes-revisao-2026-11-13.md](correcoes-revisao-2026-11-13.md). O texto abaixo preserva o estado anterior à correção.

# Batcave: contrato inicial para validar

Estado: primeira etapa implementada, incluindo supressao de supervision.

## Primeira etapa sugerida

`batcave Nome { ... }` declara uma classe especial no nivel principal do arquivo.
Nao e um personagem e nao herda energia, vida ou poderes.
Instancias sao criadas com `new Nome()`. Metodos publicos sao a interface externa;
campos e metodos sem modificador sao privados por padrao.

Exemplo executavel:

```hero
batcave WayneSecurity {
    int attempts = 0;

    public boolean authenticate(String code) {
        attempts++;
        return code != null;
    }
}

battle {
    WayneSecurity security = new WayneSecurity();
    announce security.authenticate("teste");
}
```

## Criterios de aceitacao

- Uma batcave vazia e uma com metodo publico compilam.
- O exemplo acima executa e imprime `>> true`.
- Estado pertence a cada instancia; duas instancias nao compartilham `attempts`.
- Acesso externo a campo ou metodo privado falha na compilacao Java gerada.
- Declarar `villain` diretamente dentro da batcave gera erro HeroScript com linha.
- Nome duplicado entre batcave, hero, villain e squad gera erro HeroScript.
- Blocos incompletos geram erro de sintaxe, sem travar o parser.
- Os 16 casos anteriores continuam produzindo as mesmas saidas e codigos de retorno.

## Limites e etapas seguintes

Esta primeira etapa nao implementa autorizacao de chamadas por tipo de chamador.
Proibir declaracao de villain dentro da batcave nao impede que uma missao de villain
chame um metodo publico: essa regra precisa de contrato e testes proprios.

Observacoes de `supervision` sao suprimidas durante metodos e inicializadores de
campos, inclusive em chamadas sincronas para codigo externo. Retornos, excecoes
e batcaves aninhadas restauram o contexto em `finally`. Fora da batcave, as
observacoes voltam a funcionar. `announce` e logs de combate continuam explicitos.

Cada batcave e uma classe Java separada no arquivo gerado: isso faz com que
`private` realmente bloqueie acesso pelo battle e pelos personagens. Campos
podem ser explicitamente `public`, mas o padrao e `private`; metodos seguem
a mesma regra. Metodos `static` sao permitidos e tambem suprimem observacoes.

Nesta etapa, campos sao de instancia, uma declaracao por instrucao; campos
`static`, `protected`, construtores explicitos, classes aninhadas e `throws` em
assinaturas nao sao suportados. Instanciacao usa
`new Nome()`, sem `summon`. Variaveis locais `kryptonite` sao permitidas nos
metodos; dentro da batcave nao geram nem mesmo o marcador de observacao.
`secret` e `kryptonite` nunca podem ser combinados.

Campos, metodos e locais podem receber `secret`, conforme `secret-etapa-2.md`;
a propria batcave nao pode. O comando unico `batinvestigate` localiza seus membros
comuns e omite os marcados secret. Autorizacao por identidade/tipo do chamador,
As antigas propostas inspect/trace foram descartadas. `secret ... allow` permanece restrito
a campos de personagens.
Nao ha propagacao automatica de protecao para valores devolvidos publicamente.
O contexto de supressao e por thread; propagacao para tarefas assincronas nao
faz parte desta etapa.

O exemplo `examples/batcave.hero` e os testes em `tests/FeatureTests.java`
cobrem estado por instancia, privacidade, callbacks, inicializadores, excecoes,
aninhamento, erros de sintaxe e execucoes consecutivas na mesma JVM.

## Validacao no Windows

Na raiz do projeto, executar `powershell -ExecutionPolicy Bypass -File tests/run_tests.ps1`.
O script compila as fontes e compara saidas UTF-8 e codigos de retorno com
`tests/expected/`, sem regravar os resultados esperados.
