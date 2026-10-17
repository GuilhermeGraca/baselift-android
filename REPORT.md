# Relatório do Projeto — BaseLift

## Índice

1. [Descrição Geral](#1-descrição-geral)
2. [Motivação e Contexto](#2-motivação-e-contexto)
3. [Modelo de Negócio](#3-modelo-de-negócio)
4. [Arquitetura](#4-arquitetura)
5. [Gestão de Estado — MVVM com StateFlow](#5-gestão-de-estado--mvvm-com-stateflow)
6. [Stack Tecnológica](#6-stack-tecnológica)
7. [Base de Dados (Room)](#7-base-de-dados-room)
8. [Funcionalidades Implementadas](#8-funcionalidades-implementadas)
9. [Componentes UI Reutilizáveis](#9-componentes-ui-reutilizáveis)
10. [Navegação](#10-navegação)
11. [Design e Experiência de Utilizador (UX)](#11-design-e-experiência-de-utilizador-ux)
12. [Processo de Desenvolvimento](#12-processo-de-desenvolvimento)
13. [Otimizações de Performance](#13-otimizações-de-performance)
14. [Estrutura de Ficheiros](#14-estrutura-de-ficheiros)
15. [Estado Atual do Desenvolvimento](#15-estado-atual-do-desenvolvimento)
16. [O que Falta / Próximos Passos](#16-o-que-falta--próximos-passos)

---

## 1. Descrição Geral

A **BaseLift** é uma aplicação Android de fitness e nutrição desenvolvida em Kotlin. O objetivo é centralizar num só lugar o rastreio de treinos, nutrição e peso corporal, sem as distrações comuns de apps semelhantes (tutoriais, publicidade, funcionalidades bloqueadas por subscrição).

A app é direcionada para utilizadores que já treinam, por isso a interface apresenta apenas os dados essenciais. Funciona 100% offline — todos os dados são guardados localmente no dispositivo.

---

## 2. Motivação e Contexto

O projeto surge de uma necessidade pessoal. As funcionalidades típicas de rastreio fitness estão espalhadas por múltiplas apps, e muitas delas bloqueiam as funções mais úteis atrás de subscrições pagas. A ideia foi construir uma alternativa gratuita, offline-first, com uma interface de alta qualidade focada em quem já sabe o que está a fazer na academia.

---

## 3. Modelo de Negócio

O modelo é faseado:

- **Fase 1 (atual)**: todas as funcionalidades são gratuitas para maximizar a base de utilizadores.
- **Fase 2 (futura)**: introdução de funcionalidades avançadas (base de dados global de alimentos, estimativa de calorias por IA a partir de fotos) num modelo de subscrição mensal. Estas dependem de APIs externas pagas. As funcionalidades base continuariam gratuitas e offline.

---

## 4. Arquitetura

A app segue o padrão **MVVM** (Model-View-ViewModel), que é a abordagem recomendada pela Google para Android moderno.

```
Model
 └── local/
     ├── entity/       → data classes anotadas com @Entity (tabelas Room)
     ├── dao/          → interfaces com queries SQL (@Dao)
     └── AppDatabase   → configuração singleton do Room
 └── repository/       → camada de abstração entre DAOs e ViewModels

ViewModel
 └── onboarding/       → OnboardingViewModel
 └── nutrition/        → NutritionViewModel
 └── workout/          → WorkoutViewModel
 └── progress/         → ProgressViewModel
 └── dashboard/        → DashboardViewModel

View (Jetpack Compose)
 └── onboarding/       → OnboardingScreen, CustomTargetsScreen
 └── nutrition/        → NutritionScreen + componentes
 └── workout/          → WorkoutScreen + componentes
 └── insights/         → InsightsScreen + componentes
 └── dashboard/        → DashboardScreen + componentes
 └── navigation/       → AppNavigation (grafo de navegação)
 └── components/       → componentes reutilizáveis de gráficos
 └── theme/            → tema visual (cores, tipografia, dark mode)
```

### Injeção de Dependências

É usada **injeção manual** sem Hilt ou Dagger. O `AppContainer` é uma interface que define todos os repositórios disponíveis. A implementação `DefaultAppContainer` instancia-os de forma `lazy` (só quando necessários). É inicializado uma vez na `BaseLiftApplication`.

```kotlin
interface AppContainer {
    val database: AppDatabase
    val userRepository: UserRepository
    val progressRepository: ProgressRepository
    val workoutRepository: WorkoutRepository
    val nutritionRepository: NutritionRepository
}
```

---

## 5. Gestão de Estado — MVVM com StateFlow

O fluxo de dados segue sempre a mesma direção:

```
Room DB → Flow<T> (DAO) → Repository → StateFlow<T> (ViewModel) → Composable
```

O Room emite um `Flow` diretamente a partir de cada query — quando a base de dados muda, a UI atualiza-se automaticamente sem polling ou callbacks manuais.

Cada ViewModel expõe o estado como `StateFlow` imutável (via `.asStateFlow()`). O `MutableStateFlow` interno só pode ser alterado pelo próprio ViewModel; a View apenas lê e chama funções públicas. A conversão de `Flow` para `StateFlow` é feita com `.stateIn(scope = viewModelScope, started = SharingStarted.Lazily)` — o `Lazily` garante que o flow só corre quando há observadores ativos.

Quando o estado depende de múltiplas fontes (ex: utilizador + logs + templates), usa-se `combine()`. O `DashboardViewModel` combina 7 flows em simultâneo. Os cálculos pesados (streaks, volumes, tendências) correm em `Dispatchers.Default` via `flowOn()` para não bloquear a Main Thread.

Nem todo o estado vai para o ViewModel. Estado puramente de UI (campo expandido, diálogo aberto, posição de swipe) fica em `remember {}` no Composable — é descartado ao sair do ecrã. Estado que precisa de persistir durante a sessão (sessão ativa, rascunhos de peso/reps) fica no ViewModel em `mutableStateMapOf`.

Na View, os `StateFlow` são observados com `collectAsStateWithLifecycle()`, que para de coletar quando a app vai para segundo plano, evitando processamento desnecessário.

---

## 6. Stack Tecnológica


| Componente | Tecnologia |
|---|---|
| Linguagem | Kotlin |
| UI Framework | Jetpack Compose + Material 3 |
| Navegação | Navigation Compose |
| Base de Dados | Room Database (SQLite) |
| Async / Reatividade | Kotlin Coroutines + StateFlow / Flow |
| Gráfico de Peso | Android Canvas (personalizado) |
| Carregamento de Imagens | Coil (`coil-compose:2.5.0`) |
| Processamento Código Room | KSP (Kotlin Symbol Processing) |
| `compileSdk` | 35 (Android 15) |
| `minSdk` | 24 (Android 7.0) |

> *Nota: Inicialmente a app chegou a ter a biblioteca Vico implementada para os gráficos, mas acabou-se por decidir refazer tudo usando o Android Canvas nativo para garantir controlo total sobre os visuais e interações (eixo Y dinâmico, tooltips, etc).*

---

## 6. Base de Dados (Room)

A base de dados chama-se `baselift_database` e está na versão **10**. O Room gera a implementação automaticamente via KSP.

### Entidades (Tabelas)

| Entidade | Tabela | Descrição |
|---|---|---|
| `UserEntity` | `user_table` | Perfil único do utilizador (id=1) |
| `WeightLogEntity` | `weight_logs` | Histórico de registos de peso |
| `PhotoLogEntity` | `photo_logs` | URIs de fotos do diário visual |
| `WorkoutEntity` | `workouts` | Planos/rotinas de treino |
| `ExerciseEntity` | `exercises` | Exercícios dentro de um workout |
| `WorkoutSessionEntity` | `workout_sessions` | Sessões de treino (ativas/concluídas) |
| `SetLogEntity` | `set_logs` | Registo de cada série (peso, reps, PR) |
| `NutritionLogEntity` | `nutrition_logs` | Registo diário de calorias/macros |
| `MealTemplateEntity` | `meal_templates` | Templates de refeições pré-configuradas |

### Relações entre Tabelas

- `ExerciseEntity` tem chave estrangeira para `WorkoutEntity` (CASCADE DELETE)
- `SetLogEntity` tem chaves estrangeiras para `WorkoutSessionEntity` e `ExerciseEntity` (CASCADE DELETE)
- `WorkoutSessionEntity` fica ligada ao `WorkoutEntity` por `workoutId`

### Migrações

O esquema evoluiu ao longo do desenvolvimento com migrações incrementais:
- **v7→v8**: coluna `setCount` adicionada à tabela `exercises`
- **v8→v9**: criação das tabelas `nutrition_logs` e `meal_templates`
- **v9→v10**: criação de índices em `timestamp`, `isCompleted`, para melhorar a performance das queries

### DAOs

Cada DAO define as queries SQL para a sua tabela. As queries que devolvem dados reactivos usam `Flow<...>` para atualizar a UI automaticamente. As queries de escrita são `suspend fun`.

Exemplos de queries relevantes no `WorkoutDao`:
```sql
-- ambos os máximos históricos para PR, numa única consulta
SELECT MAX(sl.weight) AS maxWeight,
       MAX(sl.weight * (1.0 + (sl.reps / 30.0))) AS max1RM
FROM set_logs sl
INNER JOIN workout_sessions ws ON sl.sessionId = ws.id
WHERE sl.exerciseId = :exerciseId AND ws.isCompleted = 1 AND sl.isCompleted = 1
```

---

## 7. Funcionalidades Implementadas

### 7.1 OnBoarding e Perfil

O fluxo de entrada recolhe os dados do utilizador em 4 passos:
- **Dados biométricos**: género, idade, peso (KG ou LBS), altura (CM ou FT)
- **Nível de atividade**: Sedentary → Extra Active
- **Objetivo**: Extreme Loss → Extreme Gain (7 opções)
- **Resumo calculado**: calorias e macros diários calculados dinamicamente

O utilizador pode também definir **metas personalizadas** (Custom Targets) em vez de usar os valores calculados.

**Recalibração (Smart Recalibration)**: em qualquer altura, o utilizador pode recalibrar a baseline na área de Insights. O fluxo de onboarding é reutilizado para este efeito — o botão final muda para "UPDATE BASELINE" e no final redireciona de volta para os Insights.

### 7.2 Cálculo de Métricas (`CalculatorUtils`)

Toda a lógica de cálculo está isolada no objeto `CalculatorUtils`:

**BMR (Mifflin-St Jeor)**:
- Masculino: `(10 × kg) + (6.25 × cm) - (5 × idade) + 5`
- Feminino: `(10 × kg) + (6.25 × cm) - (5 × idade) - 161`

**TDEE**: `BMR × multiplicador de atividade` (1.2 a 2.1)

**Ajuste calórico por objetivo**: de -1000 kcal (Extreme Loss) a +1000 kcal (Extreme Gain)

**Mínimo de segurança**: o resultado é sempre limitado a ≥ 1200 kcal

**Divisão de macros**:
- Proteína: 30% das calorias ÷ 4 kcal/g
- Hidratos: 45% das calorias ÷ 4 kcal/g
- Gordura: 25% das calorias ÷ 9 kcal/g

**BMI**: `peso(kg) / altura(m)²`

Quando o utilizador tem Custom Targets, só o BMI é recalculado — os macros permanecem como definidos.

---

### 7.3 Nutrição

O ecrã de nutrição é composto por 5 componentes independentes:

**DailyProgressSection**: gráfico circular das calorias restantes + barras lineares de Proteína, Hidratos e Gordura. Actualiza em tempo real conforme os registos do dia.

**QuickLogSection**: registo rápido com dois modos:
- *Calories Only*: apenas calorias
- *Detailed Macros*: calorias + proteína + hidratos + gordura

**QuickAddMealsSection**: templates de refeições pré-configuradas. Toque único para registar. Botão de configuração para criar/editar templates com nome, ícone e macros (as calorias são calculadas automaticamente).

**TodaysLogsSection**: tabela com os registos do dia. Inclui botão "Reset Logs" para apagar todos. Long-press num registo ativa uma animação a vermelho e abre diálogo de confirmação de eliminação.

**Lógica de data**: os registos usam timestamps. O `NutritionViewModel` usa um `refreshTrigger` para garantir que os logs do dia correto são carregados (os dados dão reset à meia-noite automaticamente do ponto de vista da UI).

---

### 7.4 Treinos

O ecrã de treinos é unificado — gere rotinas e regista cargas na mesma view.

**Estrutura de dados**:
- `WorkoutEntity`: nome do plano (ex: "Push Day")
- `ExerciseEntity`: exercício dentro do plano (nome, equipamento, grupos musculares, nº séries)
- `WorkoutSessionEntity`: sessão ativa ou concluída
- `SetLogEntity`: cada série com peso, reps e tipo de PR

**Estado da UI** (`WorkoutUiState`):
- Lista de workouts disponíveis
- Workout selecionado
- Sessão ativa
- Lista de exercícios com as séries (`ExerciseUiModel` → `List<SetUiModel>`)

**Lógica de séries**:
- Cada exercício tem um `setCount` que define o número de séries
- As séries têm estado sequencial: só se pode completar a próxima se a anterior estiver concluída
- É possível adicionar/remover séries durante a sessão
- Os rascunhos de peso/reps são mantidos em `mutableStateMapOf` (sobrevivem a recomposições)
- A conclusão/desmarcação marca imediatamente a série como pendente no `WorkoutViewModel`. O bloqueio por exercício permanece até a escrita terminar e o estado persistido chegar à UI, impedindo toques repetidos e alterações sequenciais prematuras; exercícios diferentes podem guardar em paralelo.
- A UI mostra um indicador de gravação e bloqueia os controlos envolvidos e a finalização da sessão enquanto há séries pendentes. Falhas libertam o bloqueio, preservam os rascunhos e apresentam uma mensagem para repetir o toque. Os comandos validam a sessão e a série atuais e resolvem o ID persistido no ViewModel.
- Adicionar uma série executa um único `UPDATE exercises SET setCount = setCount + 1 WHERE id = ...`, usando o valor atual da base de dados. Toques rápidos não reutilizam a contagem antiga da UI nem reescrevem nome, equipamento, grupos musculares ou ordem. Falhas apresentam uma mensagem para repetir a adição; os rascunhos e séries registadas são preservados.
- Remover a última série confirma o seu número e a sessão ativa numa transação: decrementa apenas a contagem correspondente e elimina apenas os logs desse número na sessão atual. Confirmações antigas não removem uma série diferente; sessões concluídas e a contagem mínima de uma série são protegidas. O bloqueio permanece até a escrita e as duas atualizações da UI (exercício e logs) estarem concluídas. Os rascunhos só são limpos após sucesso; falhas preservam os dados e permitem repetir a operação.
- A reordenação usa `reorderExercises()` com atualizações apenas de `orderIndex`, agrupadas numa transação. Editar nome/equipamento/grupos musculares atualiza apenas esses campos, preservando alterações concorrentes à contagem ou ordem. A reordenação continua a atualizar visualmente a lista de imediato.
- O `WorkoutViewModel` mantém um worker de reordenação por rotina: a gravação em curso termina e os pedidos intermédios ainda não iniciados são substituídos pela ordem mais recente. Essa ordem é aplicada aos modelos frescos recebidos do Room até a gravação e o respetivo snapshot confirmarem os índices, preservando séries, metadados e exercícios adicionados/removidos. Uma falha do pedido mais recente retira a ordem provisória, apresenta os modelos observados da base de dados e mostra uma mensagem apenas na rotina afetada; arrastar novamente permite repetir. Cancelar o ViewModel não é apresentado como erro. Não há consultas adicionais de histórico nem um novo schema.
- Tocar novamente na rotina já selecionada reutiliza a seleção ativa (incluindo carregamento em curso), sem limpar os exercícios, repetir a consulta de histórico ou recuperar/reagendar a mesma sessão. Finalizar força uma nova seleção, para abrir a próxima sessão e atualizar o histórico. Cada tentativa tem uma identidade; resultados e erros de tentativas canceladas não substituem a seleção atual. Falhas de sessão/histórico/observação apresentam uma ação para repetir, preservando os rascunhos e dados gravados. As linhas só ficam disponíveis após os dados da tentativa estarem prontos. Alterações aos metadados da rotina não reiniciam a seleção; uma gravação tardia do temporizador não troca a rotina visível.
- A edição de nome/equipamento/grupos musculares mantém os três campos no ViewModel e só fecha automaticamente depois da gravação e da confirmação pelo modelo observado. O estado de gravação bloqueia submissões repetidas e alterações aos campos; uma falha mantém as entradas e apresenta uma mensagem para repetir. «Close» durante uma gravação esconde o diálogo sem cancelar o comando aceite; a falha pode ser retomada ao abrir a edição desse exercício. «Cancel» fora da gravação descarta o formulário. A escrita continua a atualizar apenas os campos de metadados, sem alterar contagem, ordem, séries ou rascunhos de peso/repetições.
- Depois de largar um exercício, o ajuste visual segue o ID do exercício, espera dois frames com posição e tamanho estáveis e abandona a espera ao fim de 500ms ou se o item sair da vista. Não depende de uma altura mínima de 120dp. Um novo drag cancela o ajuste anterior; mudar de rotina ou sair do ecrã cancela o scope de apresentação, sem cancelar a gravação da ordem no ViewModel.

**Personal Records (PRs)**:
- **PR de Peso**: o peso desta série supera o máximo histórico do exercício
- **PR de Volume (1RM estimado)**: `peso × (1 + reps/30)` supera o 1RM histórico
- Deteção automática ao completar uma série, guardada no campo `prType` do `SetLogEntity`
- `checkPR()` lê os dois máximos independentes através de `getExercisePrMaxima()`, evitando duas consultas ao mesmo histórico. Mantém o epsilon de 0,01, a prioridade de PR de peso e a exclusão de séries/sessões incompletas; a escrita continua com o comportamento anterior.

**Rest Timer**: temporizador de descanso integrado com controlos rápidos (+30s, -10s, Skip)


**Sessões**: quando o utilizador inicia um treino, é criada uma `WorkoutSessionEntity` com `isCompleted = false`. Ao terminar o treino, a sessão fica marcada como completa e os rascunhos são limpos.

`startOrGetSession()` usa `WorkoutDao.getOrCreateActiveSession()` para consultar/criar a sessão numa única transação. Pedidos concorrentes para o mesmo treino reutilizam o mesmo registo; recuperar uma sessão conserva o timestamp e as séries já registadas. A notificação continua a ser agendada fora da transação, para cinco horas após cada chamada, com `ExistingWorkPolicy.REPLACE`.

**Cache de queries**: o `WorkoutViewModel` mantém um cache local `previousSetsCache` para evitar N+1 queries à base de dados ao reconstruir o estado da UI.

---

### 7.5 Progresso de Peso e Diário Visual (Insights)

**Gráfico de Peso** (Canvas personalizado):
- Desenhado inteiramente com `android.graphics.Canvas` sem bibliotecas externas
- Cor Verde Néon (`#CCFF00`)
- Eixo Y dinâmico: começa no menor peso registado (não em zero), para realçar variações subtis
- Linha de meta tracejada em Azul Elétrico (`#00E5FF`) quando definida
- Tooltips interativos ao tocar num ponto (peso + data)
- Exibe data de início, média e data de fim no topo; valor médio no eixo Y
- Filtros temporais: 7D, 30D, 1Y, ALL
- Preparação reutilizada enquanto os dados não mudam: rótulos de peso/volume semanal, downsampling de linhas (20 pontos) e agregação de barras (35). Os filtros rolling são recalculados na recomposição quando um ponto ultrapassa o limite inclusivo ou o relógio recua; alterações nos pontos, escala ou objetivo limpam seleções que já não correspondem à geometria do gráfico.

**Technical History Ledger**:
- Lista os registos de peso mais recentes
- Inicialmente mostra 4 registos com fade-out no último
- Botão "LOAD MORE DATA" para expandir / "LOAD LESS DATA" para colapsar
- Cada registo é um item com chave estável na `LazyColumn` do ecrã; a expansão compõe apenas as linhas necessárias ao viewport. A ordenação é reutilizada enquanto o histórico não muda e o delta usa diretamente o registo seguinte, sem procurar na lista inteira por linha.
- Long-press: animação de preenchimento gradual vermelho de 0% a 100% → abre diálogo de confirmação glassmorphic

**Delta inteligente**: o indicador de diferença (+/- KG) usa Verde Néon ou Coral Suave consoante a direção é favorável ou desfavorável ao objetivo do utilizador (perder vs ganhar peso). Recalculado automaticamente em recalibrações.

**Atualização automática de peso**: ao inserir um registo com data de hoje, o `weight` do `UserEntity` é atualizado e o BMI é recalculado.

**Validação de datas**: datas futuras são bloqueadas tanto no picker de peso como no de fotos.

**Diário Visual**:
- Fotos selecionadas da galeria nativa via `ActivityResultContracts.PickVisualMedia`
- Persistência offline: `takePersistableUriPermission` garante acesso à URI após reinicio
- Seleção em lote (batch upload): múltiplas fotos com a mesma data
- Ordenação cronológica da esquerda (mais antiga) para a direita (mais recente, marcada como `LATEST`)
- Gradientes de scroll dinâmicos nas extremidades
- Detalhe ao tocar: zoom gestual (pinch-to-zoom), arrasto, reposição ao soltar o gesto, cruzamento automático com o peso registado nesse dia
- O diário e o detalhe partilham um índice por dia local, reutilizado até os pesos ou o fuso horário mudarem. Mantém o primeiro peso desse dia na ordem fornecida pelo DAO (cronológica), incluindo empates. O zoom/arrasto lê o estado apenas em `graphicsLayer {}` e nos gestos; não recompõe o diálogo a cada movimento. A imagem volta a 1× ao soltar o gesto e a transformação reinicia ao trocar de foto.

---

### 7.6 Dashboard

O Dashboard agrega dados de nutrição e treino para dar uma visão geral da consistência do utilizador.

**Consistency Journal** (streaks):
- **Nutrition Streak** e **Workout Streak**: contam dias únicos com atividade, percorrendo o calendário para trás até exceder os dias de descanso permitidos numa semana de segunda a domingo (configurável: 0–7).
- Com 0, um dia anterior sem atividade quebra a streak. Com 1–6, os dias sem atividade consomem a tolerância da respetiva semana; a tolerância reinicia ao entrar noutra semana. Aumentar os dias de descanso nunca diminui a streak.
- Hoje pode estar incompleto e vários registos no mesmo dia contam como um dia. Os dias de descanso não aumentam o contador. Com 7, contam-se todos os dias com atividade até hoje; registos futuros não contam.
- Cartões com animação de glow quando a streak atinge 7 dias
- **Calendário semanal**: mostra os 7 dias da semana (Seg–Dom) com marcadores visuais de nutrição e treino

**Total Training Volume**:
- Gráfico de barras (`BarChartWithControls`) com volume total semanal (SUM(peso × reps) por semana ISO)
- Filtros temporais: 1M, 3M, 1Y
- Mostra valor total no período e variação percentual face ao período anterior

**Routines** (menus colapsáveis de dois níveis):
- **Nível 1**: lista de workouts. Ao expandir, gráfico de volume desse workout ao longo do tempo
- **Nível 2**: lista de exercícios dentro do workout. Ao expandir:
  - Cartão de PR de Peso Máximo absoluto
  - Cartão de PR de Volume Máximo (peso × reps)
  - Gráfico de Volume ao longo do tempo (verde néon)
  - Gráfico de Peso Máximo ao longo do tempo (azul elétrico)
- Carregamento lazy: cada submenu expandido observa `getCompletedSetLogsForExercise()` via um flow frio, prepara os gráficos em `Dispatchers.Default` e cancela a coleção quando sai da composição. Mostra carregamento e permite repetir uma consulta que falhou; os gráficos por exercício não são preparados pelo resumo do Dashboard.

**Calendário histórico**: registo global de todos os dias com nutrição e/ou treino, disponível no componente `CustomCalendar`.

---

## 8. Componentes UI Reutilizáveis

### `InteractiveChartWithControls`
Gráfico de linhas/área com:
- Filtros temporais (7D, 30D, 1Y, ALL) ou escala cronológica (toggle)
- Tooltips interativos (suporte a `\n` para múltiplas linhas)
- Botão para definir/remover objetivo
- Cor parametrizável
- Desenhado via Canvas; 24 KB de código

### `BarChartWithControls`
Gráfico de barras com:
- Filtros temporais (1M, 3M, 1Y)
- Resumo de volume total + variação percentual

### `ChartDataPoint`
Modelo de dados genérico: `xValue: Long`, `yValue: Float`, `tooltipLabel: String`, `extraValue: Float`

### `CustomCalendar`
Calendário navegável mês a mês com marcadores de dias de nutrição e treino. Permite ver histórico de toda a atividade.

---

## 9. Navegação

A navegação é gerida por `AppNavigation.kt` com Navigation Compose. O grafo inclui:
- `onboarding` → `custom_targets` → `dashboard`
- `dashboard` (tab) ← → `nutrition` (tab) ← → `workout` (tab) ← → `insights` (tab)

Existe um `TopHeaderBar` global com foto de perfil e ícone de definições (acesso a Reset total de dados).

A barra de navegação inferior (`BottomNavigationBar`) tem 4 tabs: Dashboard, Nutrition, Workout, Insights.

---

## 11. Design e Experiência de Utilizador (UX)

O design foi um dos focos principais do projeto. Paleta exclusivamente dark mode, tipografia em maiúsculas, e nenhum elemento decorativo — cada pixel tem um propósito.

**Paleta de cores:**

| Cor | Hex | Uso |
|---|---|---|
| Fundo | `#000000` / `#131313` | Ecrãs e cards |
| Verde Néon | `#CCFF00` | CTA, progresso positivo, gráfico de peso |
| Azul Elétrico | `#00E5FF` | Linha de meta, hidratos, gráfico de peso máximo |
| Coral Suave | — | Alertas, ações destrutivas, delta desfavorável |

Os diálogos e overlays usam glassmorphism: gradiente linear subtil (`alpha 0.08 → 0.02`) + borda `alpha 0.15`.

**Padrões de eliminação:**
- *Registos simples* (nutrição e peso): **long-press** durante 600ms anima um gradiente Coral da esquerda para a direita, depois abre diálogo de confirmação.
- *Templates Quick Add*: como o toque simples já regista a refeição, usar long-press causaria eliminações acidentais. Foi implementado **swipe-to-delete** para a direita: o item desloca-se (foreground com `offset`), revelando um background vermelho com ícone de lixo que cresce de 50% → 100% consoante o progresso. Ao cruzar 80dp, dispara feedback háptico. Se soltar antes, faz snap de volta com spring animation. Ao confirmar, a saída é encadeada: slide → shrink vertical → fade.
- *Fotos*: fluxo de 2 passos no próprio botão (REMOVE → CANCEL / CONFIRM), sem diálogo extra.

**Outros elementos de UX:**
- Cards colapsáveis com fade-out no último item visível (alpha 0.4), sugerindo que há mais conteúdo
- Gráfico de peso em Canvas com eixo Y dinâmico (começa no mínimo, não no zero), área preenchida, linha de meta tracejada e tooltips por toque
- Cartões de streak com efeito glow (halo radial + sombra com blurRadius 25f) ao atingir ≥ 7 semanas
- Tooltips no calendário semanal ao tocar num dia ativo
- Gradientes de scroll animados nas extremidades do carrossel de fotos

---

## 12. Processo de Desenvolvimento

**Fase de mockups**: antes de escrever código, foram criados mockups dos ecrãs principais (onboarding, nutrição, workout, Insights, Dashboard). Nesta fase ficaram definidos a paleta, a tipografia em maiúsculas e o sistema de cards. Ter uma referência visual concreta poupou iterações durante a implementação.

**Vertical Slicing**: o desenvolvimento foi estruturado em 6 fases. A Fase 1 foi horizontal (fundações: base de dados, tema, DI, navegação). As Fases 2 a 6 foram verticais — cada uma entregou uma funcionalidade completa da BD até à UI, utilizável no final da fase:

```
Fase 1 → App compila + tema + navegação
Fase 2 → Onboarding + cálculo de metas
Fase 3 → Registo de peso + gráfico + diário de fotos
Fase 4 → Treinos + registo de séries + PRs
Fase 5 → Nutrição + templates de refeições
Fase 6 → Dashboard + streaks + gráficos históricos
```

Isto permitiu usar a app a sério durante o desenvolvimento, o que revelou problemas reais de UX. Por exemplo, depois da Fase 5 detetou-se que os logs não faziam reset correto ao mudar de dia, o que levou à criação do `refreshTrigger` no `NutritionViewModel`.

**Refinamentos finais**: após todas as fases, houve uma fase de polimento focada em performance e UX, motivada por lentidão na navegação entre tabs. O crossfade da `NavHost` foi removido e o gráfico de peso foi substituído do Vico por Canvas personalizado para controlo total sobre o aspeto visual. A revisão de performance posterior removeu os atrasos artificiais de 50ms no Dashboard e Workout: o conteúdo disponível entra na composição sem esperar por um temporizador, mantendo as listas lazy e o carregamento real dos dados.

---

## 12. Otimizações de Performance

- **Remoção do crossfade** na `NavHost` entre tabs para evitar sobreposição de ecrãs
- **Conteúdo disponível sem atraso artificial** no Dashboard e Workout: as listas lazy compõem os itens necessários à vista sem um temporizador de 50ms; os dados e gráficos continuam a respeitar os seus fluxos de carregamento
- **Índices na base de dados** (migração v9→v10): em `timestamp`, `isCompleted`, etc., para acelerar queries históricas
- **Cache de queries** no `WorkoutViewModel` (`previousSetsCache`) para evitar N+1 queries ao reconstruir o estado dos exercícios
- **`flowOn(Dispatchers.IO / Default)`** em todos os flows pesados para não bloquear a Main Thread
- **Subscrições ligadas à apresentação**: Dashboard, históricos de peso/fotos do Insights e estado de apresentação da Nutrition usam `WhileSubscribed(1_000)` para parar o trabalho a montante após um segundo sem observadores, mantendo o último estado. O perfil partilhado continua disponível para o cabeçalho e navegação. Na Nutrition, registos, templates e metas voltam a ser observados ao regressar; a consulta diária é reconstruída e os comandos de registo/eliminação mantêm o seu próprio scope. No Workout, a mesma política controla a lista de rotinas, as queries de exercícios/séries e a reconstrução dos modelos. Recuperação inicial da seleção/sessão e leitura do histórico só começam quando o ecrã é observado. Regressar valida primeiro a lista atual de rotinas, depois lê as linhas sem repetir uma recuperação de sessão/histórico já concluída. Inputs e comandos aceites sobrevivem à saída; confirmações já persistidas deixam de aguardar modelos quando a observação termina. As linhas retidas não ficam disponíveis para novos comandos antes das emissões atuais; falhas da lista têm retry. A renovação de sessão após uma finalização concluída fora do ecrã aguarda o regresso. A política do reminder mantém cinco horas com `REPLACE` por chamada real de recuperação/criação; o arranque inicial passa a acontecer ao abrir Workout, não ao criar a navegação, e regressos normais não reagendam. `Lazily`, ainda usado noutros fluxos, adia o primeiro arranque mas não cancela a coleção quando os observadores saem.
- **Atualização diária da Nutrition**: o estado observa um intervalo imutável de dia local/fuso, calculado a partir de uma amostra de tempo. Eventos de resume/data/hora/fuso rearmam a espera até à próxima meia-noite; se o intervalo não mudou, mantêm a query atual. A meia-noite atualiza registos e totais sem reiniciar metas/templates. O temporizador termina com as subscrições após o grace period de um segundo. Leitura e reset usam os mesmos limites de dia, incluindo dias DST curtos/longos e meias-noites inexistentes/repetidas; o SQL inclusivo termina no milissegundo anterior ao dia seguinte. O reset calcula o dia atual ao executar, sem reutilizar o snapshot de apresentação.
- **Limpeza de `systemBarsPadding()`** globais para eliminar espaços em branco desnecessários no Dashboard e Insights

---

## 14. Estrutura de Ficheiros (Resumo)

```
com.example.baselift/
├── AppContainer.kt                  ← DI Manual
├── BaseLiftApplication.kt           ← Application class
├── MainActivity.kt                  ← Ponto de entrada
│
├── Utils/
│   └── CalculatorUtils.kt           ← BMR, TDEE, BMI, macros
│
├── Model/
│   ├── local/
│   │   ├── AppDatabase.kt           ← Singleton Room (v10, 9 entidades)
│   │   ├── dao/
│   │   │   ├── UserDao.kt
│   │   │   ├── WeightLogDao.kt
│   │   │   ├── PhotoLogDao.kt
│   │   │   ├── WorkoutDao.kt        ← queries de PR, sessions, sets
│   │   │   └── NutritionDao.kt
│   │   └── entity/
│   │       ├── UserEntity.kt
│   │       ├── WeightLogEntity.kt
│   │       ├── PhotoLogEntity.kt
│   │       ├── WorkoutEntity.kt
│   │       ├── ExerciseEntity.kt
│   │       ├── WorkoutSessionEntity.kt
│   │       ├── SetLogEntity.kt      ← inclui prType
│   │       ├── NutritionLogEntity.kt
│   │       └── MealTemplateEntity.kt
│   └── repository/
│       ├── UserRepository.kt
│       ├── ProgressRepository.kt
│       ├── WorkoutRepository.kt     ← lógica de PR e sessions
│       └── NutritionRepository.kt
│
├── ViewModel/
│   ├── onboarding/OnboardingViewModel.kt
│   ├── nutrition/NutritionViewModel.kt
│   ├── workout/WorkoutViewModel.kt
│   ├── progress/ProgressViewModel.kt
│   └── dashboard/DashboardViewModel.kt  ← 461 linhas, streaks, volumes, tendências
│
└── View/
    ├── theme/                       ← Dark mode, cores, tipografia
    ├── navigation/AppNavigation.kt  ← Grafo completo + TopHeaderBar
    ├── components/
    │   ├── InteractiveChartWithControls.kt  ← 24KB, Canvas
    │   ├── BarChartWithControls.kt          ← 15KB
    │   ├── ChartDataPoint.kt
    │   └── CustomCalendar.kt               ← 12KB
    ├── onboarding/
    │   ├── OnboardingScreen.kt
    │   ├── CustomTargetsScreen.kt
    │   └── components/OnboardingComponents.kt
    ├── nutrition/
    │   ├── NutritionScreen.kt
    │   └── components/
    │       ├── DailyProgressSection.kt
    │       ├── QuickLogSection.kt
    │       ├── QuickAddMealsSection.kt
    │       ├── TodaysLogsSection.kt
    │       └── ConfigureMealDialog.kt
    ├── workout/
    │   ├── WorkoutScreen.kt
    │   └── components/
    │       ├── ExerciseCard.kt
    │       ├── SetRow.kt
    │       └── WorkoutDialogs.kt
    ├── insights/
    │   ├── InsightsScreen.kt
    │   └── components/InsightsComponents.kt  ← 38KB
    └── dashboard/
        ├── DashboardScreen.kt
        └── components/
            ├── ConsistencyJournalSection.kt  ← 24KB
            ├── RoutinesSection.kt            ← 21KB
            └── TotalTrainingVolumeSection.kt
```

---

## 15. Estado Atual do Desenvolvimento

Todas as 6 fases do plano original estão concluídas:

| Fase | Descrição | Estado |
|---|---|---|
| 1 | Fundações (dependências, tema, Room, DI, navegação) | ✅ Concluído |
| 2 | OnBoarding, perfil, Insights baseline | ✅ Concluído |
| 3 | Progresso de peso e diário visual | ✅ Concluído |
| 4 | Planeamento e registo de treinos | ✅ Concluído |
| 5 | Nutrição | ✅ Concluído |
| 6 | Dashboard com streaks, volumes e rotinas | ✅ Concluído |

A app está funcional e todas as funcionalidades descritas no plano original foram implementadas, incluindo as otimizações de performance adicionadas após a fase 6.

---

## 16. O que Falta / Possíveis Próximos Passos

- **Testes unitários**: o plano previa JUnit para `OnboardingViewModel` (cálculo de calorias), DAOs (Room in-memory) e repositories (lógica de PRs), mas não chegaram a ser implementados
- **Testes instrumentados**: verificação do fluxo completo em emulador/dispositivo físico
- **Internacionalização**: todas as strings estão em `strings.xml`, o que facilita tradução futura
- **Funcionalidades pagas (Fase 2)**: base de dados global de alimentos, estimativa calórica por IA
- **ID da aplicação**: ainda está como `com.example.baselift` (placeholder padrão do Android Studio), deveria ser atualizado para publicação
