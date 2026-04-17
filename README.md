# GEO diagnosis

Descobre se sua marca aparece quando IAs respondem perguntas sobre sua categoria.

Cole seu domínio, escolha uma pergunta da sua categoria, e veja se o Gemini menciona sua marca na resposta — com score de 0 a 100.

**GEO (Generative Engine Optimization)** é a nova fronteira de visibilidade: não basta ranquear no Google, sua marca precisa aparecer quando ChatGPT, Gemini e outros respondem perguntas do seu nicho.

---

## Quick Start

### Pré-requisitos

- Java 21+
- Maven (ou use o wrapper `./mvnw` incluído no repo)
- Chave de API do [Google Gemini](https://ai.google.dev/)

### Rodando

```bash
cd backend
export GEMINI_API_KEY=sua_chave
./mvnw spring-boot:run
```

Server em `http://localhost:8080`

### Testes

```bash
./mvnw test  # 43 testes
```

---

## Como funciona

```
POST /start → retorna diagnosisId
       ↓
GET /queries → lista perguntas da categoria
       ↓
POST /analyze → roda a query no Gemini e analisa a resposta
       ↓
GET /{id} → resultado com score, sentiment e detalhes
```

O endpoint `POST /email` é opcional — salva o email do lead para follow-up.

---

## API

### 1. Iniciar diagnóstico

```
POST /api/diagnosis/start
```

```json
// Request
{
  "domain": "nomadglobal.com",
  "language": "pt",
  "clientBrandName": "Nomad"
}

// Response
{
  "diagnosisId": "a1b2c3d4",
  "brandName": "Nomad",
  "domain": "nomadglobal.com",
  "status": "QUERIES_READY"
}
```

### 2. Listar queries sugeridas

```
GET /api/diagnosis/{diagnosisId}/queries
```

```json
// Response
{
  "queries": [
    "Qual a melhor conta global para brasileiros?",
    "Como enviar dinheiro para o exterior com menor taxa?",
    "Quais fintechs oferecem cartão internacional?"
  ]
}
```

### 3. Analisar visibilidade

```
POST /api/diagnosis/analyze
```

```json
// Request
{
  "diagnosisId": "a1b2c3d4",
  "selectedQuery": "Qual a melhor conta global para brasileiros?"
}

// Response
{
  "score": 75,
  "sentiment": "POSITIVE",
  "brandMentioned": true,
  "competitorsMentioned": ["Wise", "C6 Bank"],
  "aiResponse": "Entre as opções mais populares, a Nomad se destaca por...",
  "detectionMethod": "BRAND_NAME"
}
```

### 4. Salvar email (opcional)

```
POST /api/diagnosis/email
```

```json
{
  "diagnosisId": "a1b2c3d4",
  "email": "lead@empresa.com"
}
```

### 5. Consultar resultado

```
GET /api/diagnosis/{diagnosisId}
```

Retorna o diagnóstico completo com todos os campos do analyze + metadata.

---

## Core Features

- **3-layer brand detection** — domínio exato, nome da marca, domain stem
- **Auto-fix de domínios** — se o Gemini cita o nome certo mas o domínio errado, corrige automaticamente
- **Score normalizado** — 0, 25, 50, 75, 100 (elimina ruído de variações)
- **Brand extraction** — da tag `<title>` via scraping ou input do usuário
- **Type-safe enums** — `DiagnosisStatus`, `Sentiment`
- **Fallbacks** — nunca falha completamente, sempre retorna um resultado

---

## Tech Stack

| Camada | Tecnologia |
|---|---|
| Runtime | Java 21 |
| Framework | Spring Boot 4.0.5 |
| AI | Gemini 2.5 Flash Lite |
| Scraping | JSoup |
| Storage | In-memory (ConcurrentHashMap) |
| Arquitetura | Clean / Hexagonal |

### Estrutura do projeto

```
backend/src/main/java/
├── domain/          # Entidades, enums, regras de negócio
├── application/     # Use cases, ports
└── infrastructure/  # Controllers, Gemini client, scraper
```

---

## Variáveis de ambiente

| Variável | Obrigatória | Descrição |
|---|---|---|
| `GEMINI_API_KEY` | Sim | Chave da API Google Gemini |

---

## Limitações conhecidas (MVP)

- **Sem persistência** — dados vivem em memória, reiniciou perdeu
- **Single AI provider** — só Gemini por enquanto (ChatGPT e Perplexity no roadmap)
- **Sem rate limiting** — sem proteção contra abuso em produção
- **Sem auth** — endpoints abertos

---

## Roadmap

- [ ] Persistência com PostgreSQL
- [ ] Suporte a múltiplos AI providers (ChatGPT, Perplexity, Claude)
- [ ] Frontend com dashboard de resultados
- [ ] Histórico de diagnósticos por domínio
- [ ] Comparativo temporal (como sua visibilidade evolui)
- [ ] Rate limiting e proteção
