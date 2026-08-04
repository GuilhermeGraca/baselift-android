# BaseLift

Aplicação Android de fitness e nutrição, 100% offline. Centraliza o rastreio de treinos, peso corporal e nutrição num único lugar, sem subscrições nem publicidade.

> **Nota:** Este README contém apenas um resumo. Para informações muito mais detalhadas sobre a arquitetura, decisões de design, fluxo de dados e funcionalidades completas, consulte o relatório do projeto disponível na raiz do repositório:
> - [REPORT.md](REPORT.md)
> - `BaseLift_Projeto_DAM_A51827.pdf` (mesmo conteúdo, formatado para leitura)

---

## Setup

### Pré-requisitos

- Android Studio Hedgehog ou mais recente
- JDK 17+
- Android SDK 35

### Correr o projeto

```bash
# Clonar o repositório
git clone <https://github.com/GuilhermeGraca/BaseLift>

# Abrir no Android Studio e correr num emulador ou dispositivo físico (API 24+)
```

Não são necessárias chaves de API nem configuração adicional — a app é completamente offline.

---

## Features

- **Onboarding** — registo de dados biométricos, nível de atividade e objetivo; cálculo automático de calorias e macros (Mifflin-St Jeor / TDEE)
- **Nutrição** — registo rápido de calorias e macros, templates de refeições com toque único, visualização diária com gráfico circular e barras de macros
- **Treinos** — gestão de rotinas, registo de séries por exercício, deteção automática de Personal Records (peso máximo e 1RM estimado), rest timer
- **Insights** — gráfico de peso com Canvas personalizado, diário visual de fotos com zoom gestual, smart recalibration
- **Dashboard** — streaks de nutrição e treino por semana, volume total semanal, histórico de PRs por exercício, calendário histórico de atividade

---

## Tech Stack

| Camada | Tecnologia |
|---|---|
| Linguagem | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Navegação | Navigation Compose |
| Base de dados | Room (SQLite) — versão 10 |
| Reatividade | Kotlin Coroutines + StateFlow / Flow |
| Imagens | Coil 2.5.0 |
| Build | KSP (Kotlin Symbol Processing) |
| `minSdk` | 24 (Android 7.0) |
| `compileSdk` | 35 (Android 15) |

---

## Arquitetura

O projeto segue **MVVM** com injeção de dependências manual (sem Hilt):

```
Model (Room entities + DAOs + Repositories)
    ↓  Flow<T>
ViewModel (StateFlow + Coroutines)
    ↓  collectAsStateWithLifecycle()
View (Jetpack Compose)
```

O `AppContainer` é inicializado na `Application` class e instancia os repositórios de forma lazy. Cada ViewModel recebe o repositório necessário via factory.

## Base de Dados

Room Database com 9 entidades e migrações incrementais até à versão 10:

| Entidade | Descrição |
|---|---|
| `UserEntity` | Perfil único do utilizador |
| `WeightLogEntity` | Histórico de peso |
| `PhotoLogEntity` | URIs de fotos do diário visual |
| `WorkoutEntity` | Planos de treino |
| `ExerciseEntity` | Exercícios dentro de um plano |
| `WorkoutSessionEntity` | Sessões de treino |
| `SetLogEntity` | Séries registadas (peso, reps, PR) |
| `NutritionLogEntity` | Logs diários de nutrição |
| `MealTemplateEntity` | Templates de refeições rápidas |


## Estado do Projeto

Todas as funcionalidades planeadas estão implementadas. A app está funcional e utilizável.

**Em falta:**
- Testes unitários (JUnit) e instrumentados
- Atualizar o application ID de `com.example.baselift` antes de publicar
-Implementar features mais ambiciosas, de Estimação de kcals com IA com modelos de visão e acesso a base de dados de alimentos para kcal tracking

---

## Licença

Projeto pessoal / académico. Sem licença open-source definida.
