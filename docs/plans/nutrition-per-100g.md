# Nutrition por 100 g — plano de implementação

Estado: **apenas planeado; sem código implementado**.
Data: 23 de outubro de 2026.
Branch: `feature/nutrition-per-100g`, criado a partir de `master` em `e72d84a`.
Processo: implementar um checkpoint de cada vez; apresentar a build e os resultados dos testes; aguardar validação do utilizador; só depois fazer o respetivo commit.

## Objetivo e decisão de simplificação

Permitir introduzir os valores nutricionais por 100 g e a quantidade consumida em gramas, deixando a aplicação calcular o que deve acrescentar ao dia. Os mesmos alimentos podem ser guardados como templates para reutilizar os valores nutricionais e pedir apenas a quantidade em cada consumo.

**Decisão expressa do utilizador: usar apenas valores inteiros, mesmo quando o rótulo tem decimais.** Calorias, proteínas, hidratos, gorduras e quantidade em gramas continuam inteiros. Não introduzir suporte a decimais nem alterar os tipos nutricionais da base de dados. O utilizador escolhe o inteiro a introduzir a partir do rótulo; a aplicação não deve transformar silenciosamente `12,5` em `125`.

A inspiração é o formato por 100 g presente nos rótulos. A informação nutricional europeia também prevê valores por 100 ml; este plano cobre **apenas gramas**, conforme pedido. Não assumir equivalência entre gramas e mililitros. [Comissão Europeia: rotulagem nutricional](https://food.ec.europa.eu/food-safety/labelling-and-nutrition/food-information-consumers-legislation/nutrition-labelling_en).

## O que o código atual faz

| Área | Comportamento confirmado | Consequência para a feature |
|---|---|---|
| `QuickLogSection.kt` | Dois modos: Calories Only e Detailed Macros. Campos inteiros; o clique envia valores totais e limpa os campos imediatamente. | Acrescentar a opção por 100 g aos dois modos e uma pré-visualização dos valores que serão guardados. |
| `ConfigureMealDialog.kt` | Cria templates com nome, ícone, kcal e macros. As kcal são editáveis; o botão Recalculate aplica 4/4/9 aos macros. | Acrescentar a checkbox sem retirar nenhuma destas funcionalidades. Não substituir kcal do rótulo automaticamente. |
| `QuickAddMealsSection.kt` | Tocar regista imediatamente; swipe abre a eliminação. Existe um toast de sucesso emitido no clique, antes de a escrita terminar. | Dividir a ação do toque pelo tipo de template. Abrir o diálogo por 100 g não pode registar nem mostrar sucesso. |
| `NutritionViewModel.kt` | Quick Log e templates criam `NutritionLogEntity` com totais. Escritas usam `viewModelScope`; a eliminação de templates já tem pending/erro, mas criar/registar não têm confirmação explícita de sucesso na UI. | Reutilizar o padrão local de estado de escrita nos formulários afetados, para distinguir guardar, sucesso e falha. |
| `MealTemplateEntity.kt` | Guarda kcal/macros como `Int`; não existe informação sobre a base dos valores. | Acrescentar apenas `isPer100g`, com valor predefinido `false`. |
| `NutritionLogEntity.kt` | Guarda totais inteiros por registo. Dashboard e Nutrition somam esses totais. | Guardar o resultado proporcional já calculado. Não alterar os consumidores do histórico. |
| `AppDatabase.kt` | Room 2.6.1, versão 11, migrações explícitas; `exportSchema = false`. | Preparar migração 11→12 e verificar a preservação de dados. |

O diálogo atual de configuração recebe apenas `onSave` e `onDismiss`: não existe neste fluxo um editor de templates já guardados, apesar da referência genérica a edição no relatório. Este plano mantém criação, escolha de ícone, recálculo e eliminação; não acrescenta um editor completo.

## Comportamento proposto

### 1. Quick Log

1. Manter os separadores Calories Only e Detailed Macros e o fluxo atual quando a opção está desligada.
2. Acrescentar no card um seletor **“Portion Basis”** com **Serving / 100G**, começando em Serving. Cada opção deve ser clicável e ter semântica de seleção; o visual segue o protótipo fornecido pelo utilizador.
3. Quando ativa, identificar claramente que kcal/macros são por 100 g. Apresentar o campo **“Amount consumed (g)”**, inicialmente vazio, antes do botão de adicionar.
4. Mostrar uma linha compacta **“To be logged”** com os totais calculados. Em Calories Only, mostrar apenas kcal e guardar macros a zero, mesmo que existam drafts no outro separador.
5. Desativar Add Entry enquanto a quantidade estiver vazia, inválida ou a zero, ou enquanto o resultado não for um registo válido. Nunca assumir silenciosamente que quantidade vazia significa 100 g.
6. Mudar Serving/100G altera a interpretação e os indicadores dos inputs, preservando o texto introduzido. Não multiplicar nem substituir os campos durante a troca. Em Serving, ignorar a quantidade retida.
7. Ao mudar entre os dois separadores, preservar a base e a quantidade. Depois de uma gravação bem-sucedida, limpar nutrientes e quantidade, mantendo o separador e a base escolhidos para facilitar registos consecutivos.
8. Usar estado de formulário recuperável numa rotação. Se a escrita falhar, conservar os campos, mostrar erro local e permitir tentar novamente. Limpar apenas o formulário associado ao pedido que terminou, nunca um draft novo.

Esta alteração não deve bloquear o Quick Log enquanto o histórico ou os templates ainda estão a carregar. Esse comportamento já está coberto por `NutritionLoadingSkeletonTest` e deve continuar.

### 2. Configuração de uma meal

1. Acrescentar a mesma checkbox após o nome/ícone, desligada inicialmente.
2. Com a opção ativa, guardar os valores como referência por 100 g e identificar essa base junto dos inputs e das calorias. **Não pedir quantidade consumida nesta fase.**
3. Manter nome, ícones, kcal editáveis, macros, botões de ajuste e Recalculate. O recálculo continua a ser uma ação explícita: kcal podem diferir do resultado 4/4/9, pelo que não se deve impor uma igualdade ou substituir o valor manual.
4. Guardar `isPer100g` com o template. Templates normais continuam a representar o valor total de uma utilização.
5. Confirmar e fechar o formulário depois de a gravação ter sucesso. Numa falha, manter o draft e permitir retry; durante a submissão, impedir a confirmação repetida do mesmo formulário.

### 3. Lista de Quick Add Meals e consumo

| Tipo de template | Apresentação | Ação do toque |
|---|---|---|
| Total / normal | Sem badge; kcal do consumo completo; indicação atual de toque para registar. | Registar diretamente, como hoje, sem pedir quantidade. |
| Por 100 g | Badge textual **“PER 100 G”**; calorias identificadas como referência por 100 g; indicação **“TAP TO ENTER AMOUNT”**. | Abrir diálogo de quantidade, sem qualquer escrita nesta fase. |

O diálogo de quantidade apresenta o nome da meal, a referência por 100 g, um campo de gramas inteiras, os totais a registar e as ações Cancel/Add Entry. A quantidade começa vazia em cada novo consumo; dar foco ao campo numérico para permitir introduzir e confirmar rapidamente. Não guardar a última quantidade como default nesta primeira versão.

Confirmar uma quantidade válida cria **um único registo com os totais calculados**. Cancelar, tocar fora ou carregar Back antes da confirmação não altera logs, totais ou streaks. Não mostrar um toast de sucesso ao abrir o diálogo. Se houver feedback de sucesso, deve seguir a conclusão da escrita.

Manter a identificação dos itens por ID, ordem, Show More/Show Less e swipe-to-delete existentes. O badge deve funcionar também com nomes longos, ecrãs pequenos e texto aumentado. Não fazer depender a distinção apenas da cor.

O diálogo conserva um snapshot identificado do template selecionado. Se esse template for removido antes da confirmação enquanto a lista está ativa, invalidar a seleção e impedir um toque obsoleto. Depois de aceite um pedido, a escrita usa os valores capturados; apagar posteriormente o template não altera logs já criados.

## Regras de cálculo e validação

**Para cada valor: total = valor por 100 g × gramas consumidas ÷ 100.** Aplicar a regra a kcal, proteínas, hidratos e gorduras independentemente.

- Os inputs são inteiros não negativos; a quantidade tem de ser um inteiro estritamente positivo.
- Em modo total, o fator é 1. Em modo por 100 g, a quantidade é obrigatória em qualquer ponto de entrada, incluindo o ViewModel. Nunca fazer fallback para um registo de 100 g se faltar quantidade.
- Arredondar cada resultado uma única vez ao inteiro mais próximo; metade arredonda para cima. Não arredondar o fator de escala nem os valores intermédios.
- Fazer a multiplicação em `Long` antes de dividir. Verificar que o resultado cabe em `Int` antes de converter; rejeitar valores fora da gama, sem overflow ou saturação silenciosa.
- Usar exatamente a mesma função pura para preview e gravação. Não voltar a multiplicar um resultado já convertido em total.
- Preservar kcal introduzidas pelo utilizador; não as voltar a derivar dos macros depois da escala.
- Inputs vazios de kcal/macros podem representar zero, como hoje. Um campo não vazio inválido deve dar erro, e não converter silenciosamente para zero. Um registo novo por 100 g cujo resultado arredonde a zero em todos os campos não deve criar uma entrada vazia nem atividade no calendário.
- Texto colado com vírgula/ponto decimal, sinal negativo, letras ou valor demasiado grande deve permanecer identificável como inválido e impedir a confirmação. Não reutilizar um filtro que apenas remove os caracteres e transforma `12,5` em `125`.
- Não impor regras alimentares adicionais, como obrigar kcal a corresponder aos macros ou somar exatamente 100 g de macronutrientes.

| Referência por 100 g | Quantidade | Totais a guardar |
|---|---:|---|
| 200 kcal, P 10, C 20, F 5 | 150 g | 300 kcal, P 15, C 30, F 8 |
| 200 kcal, P 10, C 20, F 5 | 100 g | 200 kcal, P 10, C 20, F 5 |
| 200 kcal, P 10, C 20, F 5 | 50 g | 100 kcal, P 5, C 10, F 3 |
| 200 kcal em Calories Only | 125 g | 250 kcal; macros a zero |

O arredondamento pode perder até meio grama ou meia kcal por campo e por registo; é uma consequência aceite do modelo inteiro escolhido. A UI, o histórico e o somatório diário devem concordar sobre o mesmo valor guardado.

## Organização da implementação

### Cálculo e comandos

Criar um pequeno ficheiro de cálculo/validação, por exemplo `Model/nutrition/NutritionPortionCalculator.kt`, independente de Compose, Room e ViewModel. Não criar uma nova camada genérica de regras ou converter os números para strings dentro do modelo persistido.

Os componentes mantêm o texto dos campos e apresentam o preview; o ViewModel valida a submissão e usa o mesmo cálculo antes de construir `NutritionLogEntity`. O evento de Quick Log deve transportar os valores de origem, o modo e a quantidade opcional, em vez de tratar o preview como se fosse uma nova referência por 100 g. O comando de template recebe o template identificado e a quantidade opcional; esta só pode ser omitida no modo total. O repositório continua responsável pela persistência. Não introduzir consultas de base de dados por tecla, novas subscrições de histórico ou trabalho assíncrono para quatro multiplicações.

Usar estado pequeno de pending/resultado/erro nos comandos afetados, seguindo o padrão já presente na eliminação de templates. Identificar o pedido para que uma conclusão antiga não limpe um draft mais recente. A proteção deve evitar confirmação duplicada do mesmo formulário, sem bloquear registos deliberados sucessivos da mesma meal ou depender de um cooldown artificial.

Uma escrita aceite continua em `viewModelScope` se o utilizador sair da aba. Capturar os valores e o instante do registo ao aceitar a confirmação; abrir um diálogo antes da meia-noite e confirmar depois regista no novo dia. Sucesso significa que a inserção terminou; totais e logs continuam a chegar pelo fluxo Room existente. Propagar cancelamento de coroutines normalmente e apresentar falhas de escrita recuperáveis.

Não acrescentar spinner de ecrã inteiro ou duração mínima de loading. Se uma operação persistente precisar de indicador, reutilizar o feedback local já existente e o respetivo limiar de apresentação. Checkbox, cálculo e abertura do diálogo devem responder imediatamente.

### Persistência e migração

- Acrescentar `isPer100g: Boolean = false` a `MealTemplateEntity`, no fim dos parâmetros para reduzir impacto em chamadas existentes. O default Kotlin e o default SQL devem corresponder: false / 0.
- Avançar `AppDatabase` de 11 para 12 e adicionar `MIGRATION_11_12`. A mudança de schema é apenas uma coluna não nula com default 0 em `meal_templates`. Não reconstruir tabelas nem alterar os campos de kcal/macros.
- Todos os templates existentes ficam normais: os mesmos IDs, nomes, ícones, valores e comportamento de toque único. Não inferir tipo a partir do nome ou do ícone.
- `NutritionLogEntity`, queries diárias, targets, Dashboard e streaks continuam a consumir totais inteiros. Não guardar valores de referência por 100 g em `nutrition_logs`.
- Não acrescentar foreign key do log ao template: eliminar um template deve continuar a preservar o histórico. Today's Logs continua a mostrar os totais; guardar quantidade como metadado separado fica fora desta versão.
- Registar a nova migração no builder de produção, mantendo o caminho 7→8→9→10→11→12. Não adicionar fallback destrutivo para upgrades.
- Preparar exportação do schema 11 **antes** de alterar a entidade/versão; depois exportar o 12. Usar KSP e `MigrationTestHelper` da dependência Room 2.6.1 já existente. Guardar os dois schemas no repositório e disponibilizá-los aos testes. Não atualizar Room/Gradle como parte desta feature. [Room 2.x: migrações e testes](https://developer.android.com/training/data-storage/room/v2).
- Testar a migração com uma base em ficheiro contendo templates normais, logs históricos e dados das restantes áreas; verificar schema, valores, IDs e uma nova inserção após a migração. Testar também instalação limpa da v12.

Existe atualmente `fallbackToDestructiveMigrationOnDowngrade()`. **Depois de migrar o telemóvel para v12, não instalar uma build antiga que abra a base como v11:** isso pode apagar dados. Fazer a primeira verificação de migração no emulador/fixture e corrigir qualquer problema avançando com v12. Validar no telemóvel com release otimizada e a mesma assinatura, sem desinstalar ou limpar dados. O injector local de mock data não deve participar na validação de migração.

## Roadmap — dois checkpoints

### Checkpoint N1 — Quick Log por 100 g e cálculo partilhado

**Esforço: médio. Resultado utilizável:** o registo manual passa a aceitar valores por 100 g e uma quantidade inteira, com preview correto nos dois separadores.

**Implementar:** cálculo e validação partilhados; seletor Serving/100G e quantidade no Quick Log; identificação da base dos valores; preview; submissão com estado de escrita, preservação do draft em erro e proteção contra confirmação duplicada. O modo total continua disponível sem quantidade. Não alterar o schema neste checkpoint.

**Ficheiros principais:** `QuickLogSection.kt`, `NutritionScreen.kt`, `NutritionViewModel.kt`, novo ficheiro de cálculo, `strings.xml` e testes correspondentes. Se for útil, extrair apenas o campo de quantidade/preview que o próximo checkpoint vai reutilizar.

**Verificação automática:**

- Testes puros para 50/100/125/150 g, arredondamento .5, independência das kcal, zero, inválidos, valores grandes e overflow.
- Testes de ViewModel para registo total e proporcional, macros zero em Calories Only, uma inserção por submissão, falha/retry e escrita aceite antes de sair do ecrã.
- Testes Compose para seleção de 100G, aparecimento do campo, preview, separadores, texto decimal colado rejeitado, quantidade inválida, limpeza só após sucesso e draft em rotação.
- Reexecutar os testes relevantes de data/subscrições e o caso em que Quick Log funciona durante a primeira carga de Nutrition; compilar debug/release e executar lint.

**O utilizador valida:** registar 200 kcal por 100 g com 150 g e confirmar +300 kcal; repetir em Detailed Macros; voltar a Serving e confirmar o fluxo antigo; experimentar quantidade vazia/zero e trocar de aba. Confirmar também o novo aspeto do card no telemóvel.

**Commit, apenas após validação:** `Add per-100g quick nutrition logging`.

### Checkpoint N2 — Templates por 100 g, badge e diálogo de consumo

**Esforço: médio, um pouco maior que N1. Resultado utilizável:** meals normais e por 100 g convivem na mesma lista; as primeiras registam diretamente e as segundas pedem apenas gramas.

**Implementar pela seguinte ordem dentro do checkpoint:**

1. Exportar o schema 11, acrescentar o flag/default e a migração 11→12; validar preservação dos dados antes de instalar no telemóvel.
2. Adicionar a checkbox à criação de templates e persistir a escolha, mantendo nome, ícone, kcal manuais e recálculo explícito.
3. Distinguir os itens da lista com badge/rótulo e encaminhar o toque conforme o tipo. Criar `MealQuantityDialog.kt` com quantidade, preview, cancelar e confirmar, usando o cálculo de N1.
4. Adaptar `saveMealTemplate` e `logMealTemplate` para validar o modo e confirmar resultados da escrita; nunca registar um template por 100 g sem quantidade. Mover o feedback de sucesso prematuro para o resultado da operação.
5. Concluir strings, acessibilidade, teclado e disposição em ecrãs pequenos. Atualizar README/REPORT para descrever o comportamento final; retirar “Planned” do selling point apenas quando o checkpoint estiver validado.

**Ficheiros principais:** `MealTemplateEntity.kt`, `AppDatabase.kt`, configuração mínima de exportação de schemas em `app/build.gradle.kts`, schemas 11/12, `ConfigureMealDialog.kt`, `QuickAddMealsSection.kt`, novo `MealQuantityDialog.kt`, `NutritionScreen.kt`, `NutritionViewModel.kt`, `strings.xml` e testes. O DAO/repositório só precisam de alteração se o contrato de confirmação da escrita o exigir; a entidade já é guardada/lida pelos métodos existentes.

**Verificação automática:**

- Migração 11→12 com dados preservados e flag false; round-trip de um template por 100 g, incluindo fechar e reabrir a base; instalação limpa v12.
- Template normal continua a gerar os mesmos totais num toque. Por 100 g abre o diálogo e não escreve antes de confirmar. Cancelar não escreve; confirmar 150 g aplica uma única escala.
- O mesmo template usado com 50 g e 150 g mantém os valores de referência inalterados e produz logs independentes. Não aceitar quantidade ausente/zero, resultado vazio ou submissão repetida do mesmo formulário.
- Falha ao guardar template ou consumo mantém contexto para retry e não mostra sucesso. Uma escrita aceite termina mesmo saindo da aba. Remover template preserva logs existentes e invalida um diálogo ainda não confirmado quando aplicável.
- Manter verdes `NutritionDaoTest`, `NutritionViewModelTest`, `QuickAddMealsSectionTest`, testes de loading/data/subscrições e analytics dependentes de nutrição; compilar debug/release e executar lint.

**O utilizador valida:** confirmar que as meals antigas continuam iguais depois da atualização; criar uma normal e uma por 100 g; confirmar badge e diferenças de toque; cancelar e depois registar duas quantidades diferentes; reiniciar a app; testar swipe-to-delete e verificar que os logs/totais persistem.

**Commit, apenas após validação:** `Add per-100g meal templates and quantity logging`.

Este é o último checkpoint previsto. A verificação final dos dois fluxos e a atualização da documentação pertencem a N2; não criar um terceiro checkpoint apenas para uma limpeza genérica.

## Critérios de conclusão e limites

1. Quick Log por 100 g funciona nos dois modos; a opção desligada mantém o registo total.
2. Templates antigos continuam normais e mantêm os dados; templates por 100 g persistem corretamente e têm distinção textual na lista.
3. Só a confirmação de quantidade regista o consumo de uma meal por 100 g. Preview, log e totais concordam, com a política de arredondamento documentada.
4. Nenhum fluxo novo aceita texto decimal como outro inteiro, perde drafts por erro, confirma sucesso antes de guardar ou introduz delays artificiais.
5. A migração preserva dados, os testes proporcionais passam e a release otimizada é validada no telemóvel.
6. Commits só após validação do utilizador; atualizar o estado deste plano em cada checkpoint. O documento de selling points está atualmente ignorado pelo Git e é um rascunho local: mantê-lo assim, salvo pedido explícito para o versionar.

Ficam fora do âmbito: valores decimais, mililitros/conversão de densidade, leitor de códigos de barras/OCR, catálogo de alimentos, receitas com ingredientes, sincronização, edição completa de templates existentes e reformulação dos gráficos/históricos. Não alterar os tempos de subscrição nem as otimizações concluídas para implementar esta feature.

## Estado dos checkpoints

| Checkpoint | Implementação | Testes | Validação manual | Commit |
|---|---|---|---|---|
| N1 — Quick Log | Implementado | Unitários, Compose, regressão e lint passaram | Validado no telemóvel | Incluído no commit N1 |
| N2 — Templates | Por iniciar | Por executar | Pendente | — |

O checkpoint N1 mantém os valores inteiros e não altera a base de dados. O N2 só será comitado depois da validação manual. O rascunho de selling points continua ignorado pelo Git.
