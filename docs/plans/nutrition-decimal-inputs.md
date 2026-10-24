# Macros e quantidades decimais

Data: 24/10/2026. Branch: `feature/nutrition-decimal-inputs`.
Estado: implementação e validação técnica concluídas; sem commit, a aguardar validação do utilizador.

## Decisão

Por pedido do utilizador, substituir a limitação a inteiros de N1/N2 por:

- Kcal inteiras, incluindo o recálculo explícito a partir dos macros.
- Proteína, hidratos e gorduras com até duas casas decimais no Quick Log e no Configure Meal.
- Quantidade consumida com até duas casas decimais, tanto no Quick Log como no diálogo de templates por 100 g.
- Vírgula e ponto aceites como separador decimal; sem separar milhares. Texto inválido fica visível e impede guardar, sem remover caracteres silenciosamente.
- Sem conversões de gramas para mililitros e sem alterar os objetivos nutricionais.

A escolha de duas casas é uma convenção prática da aplicação. Por exemplo, 12,5 g de um alimento com 95 kcal, 2,5 g P, 4,12 g C e 0,04 g F por 100 g resulta em **12 kcal, 0,31 g P, 0,52 g C e 0,01 g F**.

## Um checkpoint completo

1. Adaptar parser, cálculo partilhado, teclado e campos existentes, mantendo layout e estados de gravação.
2. Preservar frações nos modelos e em Room (versão 13). A migração 12→13 reconstrói apenas as duas tabelas nutricionais, conservando os dados e índice; o upgrade 11→13 também é validado.
3. Adaptar os consumidores: preview, histórico, totais diários e Dashboard. Apresentar até duas casas, sem zeros finais desnecessários.
4. Validar cálculo, input inválido, overflow, falhas/retry, rotação, persistência e migração; disponibilizar a build otimizada para validação manual.
5. Fazer commit apenas após aprovação do utilizador.

## Política numérica

Os macros usam `Double`/REAL na persistência. A conversão por porção e o recálculo de kcal usam `BigDecimal`, sem arredondar fatores intermédios. Arredondar uma vez por registo, com metade para cima: kcal a 1, macros a 0,01 g. Assim, preview e valores guardados concordam. A apresentação dos somatórios elimina os artefactos binários e mantém a mesma precisão.

Campos vazios de nutrientes significam zero. A quantidade por 100 g deve ser positiva. Rejeitar valores não finitos, negativos, resultados acima de `Int.MAX_VALUE` (mantendo o limite anterior) e registos cujo total arredonde inteiramente a zero. Templates antigos mantêm os valores, agora representados como decimais; não recalcular o histórico.

## Validação manual

Validação técnica: 350 testes unitários aprovados; 26 testes Android aprovados no emulador (migrações 11→13 e 12→13, persistência, DAO, Quick Log, templates, quantidade, formato dos totais e botões +/−); lint sem erros; APKs debug e release compilados. A atualização para validação no telemóvel usa a release otimizada, com a mesma assinatura da versão anterior.

- Quick Log → Detailed → 100G: inserir 95 kcal, P `2,5`, C `4.12`, F `0,04` e quantidade `12,5`. Confirmar o exemplo acima no preview e no registo.
- Criar um template por 100 g com esses valores; confirmar que pedir `12,5` g produz os mesmos totais. Reabrir a app e verificar que os macros não perderam precisão.
- Criar/usá-lo em Serving: registar os próprios valores nutricionais, sem pedir quantidade. Os botões +/− dos macros continuam a somar/subtrair 1 g sem eliminar a parte decimal.
- Confirmar que kcal rejeitam `95,5` e macros/gramas rejeitam três casas decimais; a aplicação nunca transforma `12,5` em `125`.
- Confirmar o aspeto dos campos, histórico e totais no telemóvel. Atualizar a release otimizada por cima da anterior, preservando os dados; não usar a build debug com o injector de mock data para esta validação.
