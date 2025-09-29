# Card API — Desafio Hyperativa

API para **tokenização e consulta de cartões** com ingestão por **TXT de layout fixo** e por **JSON**, protegida por **Keycloak**.  
Foco em **segurança end-to-end** (AES-GCM + HMAC) e **subida simples** via Docker Compose.

---

## Funcionalidades

- `POST /cards` — cadastra (ou retorna) identificador e token de um cartão (idempotente).
- `POST /cards/upload` — ingere um **TXT** no layout do desafio (header + linhas `C*` + trailer).
- `GET /cards/lookup` — verifica se um **PAN completo** existe e retorna o **identificador único** do sistema (UUID).

> **Luhn**: a validação existe, porém como o TXT do desafio contém PANs fora do padrão, **deixamos opcional** via `cards.validation.require-luhn=false` (padrão).  
> Para obrigar Luhn, use `cards.validation.require-luhn=true`.

---

## Tecnologias

- Java 21, Spring Boot 3.3+
- Spring Security (Resource Server / JWT)
- Keycloak (realm `cards`)
- JPA/Hibernate + PostgreSQL
- springdoc-openapi (Swagger UI)
- Lombok
- Docker
- MySQL

---

## Segurança & Armazenamento

- PAN em claro **não** é persistido.
- Persistimos:
    - `pan_enc`: **AES-256-GCM** (IV aleatório por registro).
    - `pan_hmac_hex`: **HMAC-SHA256** do PAN normalizado (determinístico → busca/idempotência).
    - Metadados: `bin`, `last4`, `brand`, `expiryMonth`, `expiryYear`, `token` (aleatório), `id` (UUID).
- Chaves via propriedades:
    - `cards.crypto.aesKeyHex` → **64 hex** (32 bytes) para AES-256.
    - `cards.crypto.hmacKeyHex` → recomendado **≥ 64 hex** (32 bytes).
- Mantenha **as mesmas chaves** entre restarts; se trocar, a busca por PAN (via HMAC) deixa de funcionar para dados antigos.

Gerando chaves de exemplo:
```bash
# 32 bytes (64 hex) para AES-256
openssl rand -hex 32

# 32 bytes (64 hex) para HMAC-SHA256
openssl rand -hex 32
```
---

## Passo a passo para subir o app (Docker Compose)

1. **Pré-requisitos**
    - Docker e Docker Compose instalados.

2. **Clonar o repositório**
   ```bash
   git clone <seu-repo>
   cd <seu-repo>
   
   docker compose up -d --build
    ```

## Verificações rápidas

- Keycloak: http://localhost:8081/ (realm cards)
- Swagger: http://localhost:8080/swagger-ui/index.html

## Como autenticar e usar as APIs

Obtenha um Access Token do Keycloak e use Bearer nas chamadas.

> Client secret vai ser despejado nos log's do Keycloak na primeira subida. E pode ser acessado via KeyCloadk UI usando user admin e pass admin
> Client ID: `cards-client`

```
curl --location 'http://localhost:8081/realms/cards/protocol/openid-connect/token' \
--header 'Content-Type: application/x-www-form-urlencoded' \
--data-urlencode 'grant_type=client_credentials' \
--data-urlencode 'client_id=cards-client' \
--data-urlencode 'client_secret=<CLIENT_SECRET>'
```

---

## Testes

- Unitários: CardIngestionService, CardSecureServiceImpl, CryptoServiceImpl.

- Integrados: CardController (contexto web + JWT).
