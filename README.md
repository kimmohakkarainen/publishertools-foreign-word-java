# foreign-word-chat

Small Spring Boot 4 / Java 21 REST service for chat completions using either **Ollama** (local) or **Microsoft Foundry** (Azure OpenAI via the official OpenAI Java SDK and Spring AI `openai-sdk` integration).

## Requirements

- JDK 21
- Maven 3.9+ (for example installed under `C:\apps\maven`), or use the included Maven Wrapper (`mvnw.cmd` / `./mvnw`)
- For the **ollama** profile: a running [Ollama](https://ollama.com/) instance and a pulled model (default configuration expects `mistral`)
- For the **foundry** profile: Azure OpenAI / Foundry endpoint, API key or Azure identity, and deployment name

## Build and test

```bash
mvn verify
```

On Windows with a custom Maven install:

```powershell
& "C:\apps\maven\bin\mvn.cmd" verify
```

## Run

### Ollama (default profile)

Default profile is `ollama` (see `application.yml`). Ensure Ollama is listening on `http://localhost:11434` (or set `OLLAMA_BASE_URL`).

```powershell
$env:SPRING_PROFILES_ACTIVE='ollama'   # optional; default is ollama
mvn spring-boot:run
```

### Microsoft Foundry / Azure OpenAI

```powershell
$env:SPRING_PROFILES_ACTIVE='foundry'
$env:AZURE_INFERENCE_ENDPOINT='https://YOUR_RESOURCE.openai.azure.com'
$env:AZURE_INFERENCE_KEY='your-key'   # optional fallback when AAD token auth is unavailable
$env:AZURE_OPENAI_DEPLOYMENT='your-deployment-name'
mvn spring-boot:run
```

Authentication order for the `foundry` profile:

1. **AAD token auth first** via `DefaultAzureCredential` using:
   - `AZURE_CLIENT_ID`
   - `AZURE_TENANT_ID`
   - `AZURE_CLIENT_SECRET`
2. **API key fallback** via `AZURE_INFERENCE_KEY`

Example service principal setup (PowerShell):

```powershell
$env:SPRING_PROFILES_ACTIVE='foundry'
$env:AZURE_INFERENCE_ENDPOINT='https://YOUR_RESOURCE.openai.azure.com'
$env:AZURE_OPENAI_DEPLOYMENT='your-deployment-name'
$env:AZURE_CLIENT_ID='00000000-0000-0000-0000-000000000000'
$env:AZURE_TENANT_ID='00000000-0000-0000-0000-000000000000'
$env:AZURE_CLIENT_SECRET='your-client-secret'
mvn spring-boot:run
```

Compatibility note: `OPENAI_BASE_URL` and `OPENAI_API_KEY` are still accepted as fallbacks for existing environments.

Optional: override chat model hint with `OPENAI_CHAT_MODEL`.

## API

- `POST /api/v1/chat/completions` — JSON body:
  - Either `message` (single user turn) or `messages` (list of `{ "role": "system"|"user"|"assistant", "content": "..." }`)
  - Optional `system` (prepended as a system message when using `message`)
  - Optional `temperature`, `maxTokens`

Example:

```bash
curl -s -X POST http://localhost:8080/api/v1/chat/completions ^
  -H "Content-Type: application/json" ^
  -d "{\"message\":\"What is Spring Boot?\"}"
```

### Async file processing (in-memory queue)

Uploads a text file; a background worker thread processes jobs (placeholder: line count and preview). State is **not** persisted across restarts.

- `POST /api/v1/jobs` — multipart form field `file` (must be `text/*`, max 10MB per [application.yml](src/main/resources/application.yml)). Returns `201` with `jobId` and `IN_PROGRESS` status.
- `GET /api/v1/jobs/{id}/status` — `IN_PROGRESS`, `FINISHED`, or `ERROR`. `404` if the id is unknown.
- `GET /api/v1/jobs/{id}/result` — `200` with `result` when `FINISHED`, or with `errorMessage` when `ERROR`. `409 Conflict` while `IN_PROGRESS`. `404` if unknown.

Example (Windows):

```powershell
curl -s -X POST http://localhost:8080/api/v1/jobs -F "file=@C:\path\sample.txt;type=text/plain"
curl -s http://localhost:8080/api/v1/jobs/<jobId>/status
curl -s http://localhost:8080/api/v1/jobs/<jobId>/result
```

- OpenAPI: `http://localhost:8080/v3/api-docs`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Health: `http://localhost:8080/actuator/health`

## Configuration notes

- **Provider selection** uses `spring.ai.model.chat`: `ollama` vs `openai-sdk` in profile-specific YAML (`application-ollama.yml`, `application-foundry.yml`).
- The project depends on both Spring AI starters; **embedding** and **image** generation for the OpenAI SDK are set to `none` in `application.yml` so Ollama-only runs do not require Azure credentials. For Foundry image/embedding, override `spring.ai.model.image` / `spring.ai.model.embedding` as needed.
- Spring AI exposes a `ChatClient.Builder` bean; this app adds an explicit `ChatClient` bean in `ChatClientConfiguration`.

## Docker

The `Dockerfile` is multi-stage (Maven build inside the image). Run:

```bash
docker build -t foreign-word-chat:local .
```

Run Ollama and the app together (pull `mistral` inside Ollama on first use, or set `OLLAMA_MODEL`):

```bash
docker compose up --build
```

Inside Compose, the app uses `OLLAMA_BASE_URL=http://ollama:11434`.

## License

Proprietary / internal (adjust as needed for your org).
