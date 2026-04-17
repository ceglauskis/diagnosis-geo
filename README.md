# AI Growth Agent

Descobre se sua marca aparece quando IAs respondem perguntas sobre sua categoria (GEO — Generative Engine Optimization).

## Quick Start

```bash
cd backend
export GEMINI_API_KEY=sua_chave
./mvnw spring-boot:run
```

Server em `http://localhost:8080`

## API (5 endpoints)

```bash
# 1. Start
POST /api/diagnosis/start
{
  "domain": "nomadglobal.com",
  "language": "pt",
  "clientBrandName": "Nomad"
}

# 2. Get queries
GET /api/diagnosis/{diagnosisId}/queries

# 3. Analyze
POST /api/diagnosis/analyze
{
  "diagnosisId": "...",
  "selectedQuery": "..."
}

# 4. Save email
POST /api/diagnosis/email
{
  "diagnosisId": "...",
  "email": "..."
}

# 5. Get result
GET /api/diagnosis/{diagnosisId}
```

## Testes

```bash
./mvnw test  # 43 testes
```

## Core Features

- **3-layer brand detection** — domínio exato, nome da marca, domain stem
- **Auto-fix domínios** — se Gemini cita nome certo mas domínio errado, corrige
- **Score normalization** — 0, 25, 50, 75, 100 (elimina ruído)
- **Brand extraction** — da tag `<title>` ou input do usuário
- **Type-safe enums** — DiagnosisStatus, Sentiment
- **Fallbacks** — nunca falha completamente

## Tech

- Java 21, Spring Boot 4.0.5
- Gemini 2.5 Flash Lite
- JSoup (scraper)
- In-memory store
