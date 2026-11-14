# AlôSeguro

Sistema distribuído para consulta e denúncia de números usados no **Golpe da Falsa Central de Atendimento**.

Projeto A3 — Sistemas Distribuídos e Mobile — Universidade Anhembi Morumbi

Rafael Casarano · Murilo Medeiros Mota · Stephano Loyola Olivera — Profa. Cássia Assis

---

## O que tem neste repositório

| Pasta | Conteúdo |
|---|---|
| `backend/` | API REST em Java 21 + Spring Boot (Security, JWT, JPA). O site responsivo fica em `backend/src/main/resources/static` e é servido pela própria API |
| `docs/` | Arquitetura, MER/DER, protótipos, coleção do Postman, testes e guia de deploy |
| `render.yaml` | Configuração do deploy no Render (nuvem) |
| `.github/workflows/` | Testes automáticos no GitHub Actions a cada envio |
| `docker-compose.yml` | Sobe a API e um PostgreSQL local com um comando |

## Arquitetura

```
 Navegador / celular (site responsivo, instalável como PWA)
            │  HTTPS + JSON   (Authorization: Bearer <token JWT>)
            ▼
 ┌──────────────── Render (nuvem, container Docker) ────────────────┐
 │  API REST Spring Boot                                            │
 │  Security/JWT → Controller → Service → Motor de risco → Repository│
 │                                 │                                │
 │                                 └── Classificador de relatos ────┼──▶ API de IA (Gemini)
 └──────────────────────────────────┬───────────────────────────────┘
                                    ▼
                     PostgreSQL gerenciado (Neon, nuvem)
```

Mais detalhes em `docs/GUIA_TECNICO.md`.

---

## 1. Instalar

| Ferramenta | Link |
|---|---|
| JDK 21 (Temurin) | https://adoptium.net |
| IntelliJ IDEA Community | https://www.jetbrains.com/idea/download |
| GitHub Desktop | https://desktop.github.com |
| Postman (opcional) | https://www.postman.com/downloads |

Não precisa instalar Maven: o IntelliJ já traz um.

## 2. Variáveis de ambiente

| Variável | Obrigatória | O que é |
|---|---|---|
| `DB_URL` | sim | `jdbc:postgresql://HOST/neondb?sslmode=require` |
| `DB_USER` | sim | Usuário do banco no Neon |
| `DB_PASSWORD` | sim | Senha do banco no Neon |
| `JWT_SECRET` | na nuvem | Chave para assinar os tokens (mínimo 32 caracteres) |
| `LLM_API_KEY` | não | Chave da IA (Google AI Studio). Sem ela, os relatos são classificados por palavras-chave |
| `LLM_MODEL` | não | Padrão `gemini-flash-latest` |
| `DADOS_DEMO` | não | `true` (padrão) carrega 1000 números fictícios se o banco estiver vazio |
| `DADOS_DEMO_RECRIAR` | não | `true` apaga tudo e recria os dados fictícios (usar uma vez só) |

> **Nunca coloque senha ou chave em nenhum arquivo do repositório.** Elas ficam só no IntelliJ (Run Configuration) e no painel do Render.

## 3. Rodar no IntelliJ

1. **File → Open** e escolha a pasta `backend`
2. Abra `AloSeguroApplication.java`, seta verde → **Modify Run Configuration**
3. Em **Environment variables**, cole (trocando pelos seus valores):
   ```
   DB_URL=jdbc:postgresql://HOST/neondb?sslmode=require;DB_USER=USUARIO;DB_PASSWORD=SENHA
   ```
4. **Run**. Quando aparecer `Started AloSeguroApplication`, abra http://localhost:8080

Alternativa com Docker: `docker compose up --build` na pasta raiz.

## 4. Publicar na nuvem (Render)

Passo a passo em `docs/DEPLOY_RENDER.md`.

## 5. Dados fictícios (carga de 1000 números)

Com o banco vazio, a API gera automaticamente **1000 números fictícios**, cerca de 3.200 denúncias e 126 usuários (blocos de números, bancos e centrais inventados). Números bons para demonstrar:

| Número | Resultado esperado |
|---|---|
| (11) 90000-0001 | Alto risco |
| (21) 90000-0002 | Atenção |
| (11) 3000-0003 | Risco baixo |
| 0800 700 1000 | Central oficial — alerta de spoofing |
| (11) 97770-9999 | Sem denúncias, mas bloco suspeito — atenção |
| (11) 91234-5678 | Sem registros |

Login de demonstração: **demo@aloseguro.com** / **demo123**

## 6. Testes

- **IntelliJ:** botão direito em `src/test/java` → **Run 'All Tests'** (usa banco H2 em memória, não precisa do Neon)
- **GitHub:** aba **Actions** — roda sozinho a cada envio
- **Carga na nuvem:** `docs/testes/carga-k6.js`
- **Usabilidade:** `docs/testes/teste-usabilidade.md`

Relatório completo: `docs/testes/RELATORIO_TESTES.md`.

## 7. Endpoints

| Método | Rota | Acesso | Descrição |
|---|---|---|---|
| POST | `/api/auth/cadastro` | Público | Cria conta e devolve o token |
| POST | `/api/auth/login` | Público | Devolve o token JWT (bloqueia após 5 erros seguidos) |
| GET | `/api/numeros/{numero}` | Público | Consulta: score, nível, confiança, fatores, tópicos e relatos |
| GET | `/api/motor` | Público | Explica os fatores e faixas do motor de risco |
| GET | `/api/status` | Público | Totais da base e situação da IA |
| GET | `/api/numeros/ranking?dias=30&limite=10` | **Token** | Números mais denunciados |
| POST | `/api/denuncias` | **Token** | Registra uma denúncia (a IA identifica os tópicos) |
| GET | `/api/denuncias/minhas` | **Token** | Histórico do usuário |
| GET / PUT / DELETE | `/api/denuncias/{id}` | **Token** | Ver, corrigir ou excluir a própria denúncia |
| GET / PUT / DELETE | `/api/conta` | **Token** | Ver perfil, alterar nome ou excluir a conta |

Rotas com **Token** exigem `Authorization: Bearer <token>`. Coleção do Postman em `docs/postman/`.

## 8. Motor de risco (versão 2)

O score vai de 0 a 100 e é a soma de 6 fatores explicáveis, mostrados na tela:

| Fator | Máx. | Ideia |
|---|---|---|
| Denúncias recentes | 25 | Cada denúncia perde metade do peso a cada ~10 dias |
| Pessoas diferentes | 25 | Conta nova pesa menos (evita denúncia falsa em massa) |
| Gravidade dos relatos | 20 | Pedir senha, código, Pix, acesso remoto ou cartão pesa mais |
| Perfil da linha | 10 | Banco não liga de celular |
| Pico de denúncias | 10 | Várias denúncias em 24 h |
| Números vizinhos | 10 | Golpistas usam blocos de números em sequência |

Faixas: até 29 **baixo** · 30 a 59 **atenção** · 60 ou mais **alto**. Central oficial sempre mostra o alerta de spoofing.
Código em `backend/src/main/java/br/com/aloseguro/motor/MotorRisco.java`.
