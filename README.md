# AlôSeguro

Sistema distribuído para consulta e denúncia de números usados no **Golpe da Falsa Central de Atendimento**.

Projeto A3 — Sistemas Distribuídos e Mobile — Universidade Anhembi Morumbi
Rafael Casarano · Murilo Medeiros Mota · Stephano Loyola Olivera — Profa. Cássia Assis

---

## O que tem neste repositório

| Pasta | Conteúdo |
|---|---|
| `backend/` | API REST em Java 21 + Spring Boot, com Spring Security, JWT e PostgreSQL. O site fica em `backend/src/main/resources/static` e é servido pela própria API |
| `android/` | Aplicativo Android em Java que consulta a mesma API |
| `docs/` | MER, DER, protótipos e coleção do Postman |
| `docker-compose.yml` | Sobe a API e um PostgreSQL local com um comando |

## Arquitetura

```
 Navegador (site)  ─┐
                    ├──  HTTP / JSON  ──▶  API REST (Spring Boot)  ──▶  PostgreSQL (Neon, nuvem)
 App Android  ──────┘   Bearer <token>     Security → Controller →
                                           Service → Repository
```

---

## 1. Instalar

| Ferramenta | Link |
|---|---|
| JDK 21 (Temurin) | https://adoptium.net |
| IntelliJ IDEA Community | https://www.jetbrains.com/idea/download |
| Git | https://git-scm.com |
| Postman | https://www.postman.com/downloads |
| Android Studio (só para o app) | https://developer.android.com/studio |

Não precisa instalar Maven: o IntelliJ já traz um.

## 2. Criar o banco no Neon

1. Crie uma conta em https://neon.tech e um projeto `aloseguro` na região **São Paulo (sa-east-1)**
2. No painel, clique em **Connect** e copie a *connection string*. Ela tem este formato:
   `postgresql://USUARIO:SENHA@HOST/neondb?sslmode=require`
3. Separe as três partes que a aplicação usa:

| Variável | Valor |
|---|---|
| `DB_URL` | `jdbc:postgresql://HOST/neondb?sslmode=require` |
| `DB_USER` | `USUARIO` |
| `DB_PASSWORD` | `SENHA` |

> **Nunca coloque a senha em nenhum arquivo do repositório.** Ela fica só nas variáveis de ambiente.

As tabelas são criadas automaticamente na primeira execução.

## 3. Rodar a API no IntelliJ

1. **File → Open** e escolha a pasta `backend` (o IntelliJ reconhece o `pom.xml` e baixa as bibliotecas)
2. Abra `src/main/java/br/com/aloseguro/AloSeguroApplication.java`
3. Clique na seta verde ao lado de `main` → **Modify Run Configuration**
4. Em **Environment variables**, cole (trocando pelos seus valores):
   ```
   DB_URL=jdbc:postgresql://HOST/neondb?sslmode=require;DB_USER=USUARIO;DB_PASSWORD=SENHA
   ```
5. Clique em **Run**. Quando aparecer `Started AloSeguroApplication`, abra http://localhost:8080

### Alternativa com Docker

Com o Docker Desktop instalado, na pasta raiz do projeto:

```
docker compose up --build
```

## 4. Dados de demonstração

Na primeira execução, com o banco vazio, a API cria dados **fictícios** para a apresentação:

| Número para consultar | Resultado esperado |
|---|---|
| (11) 90000-0001 | Alto risco (score 100) |
| (21) 90000-0002 | Atenção (score 49) |
| (11) 3000-0003 | Risco baixo (score 12) |
| 0800 700 1000 | Central oficial — alerta de spoofing |
| (11) 91234-5678 | Sem registros |

Login de demonstração: **demo@aloseguro.com** / **demo123**

Para desligar: variável `DADOS_DEMO=false`.

## 5. Rodar os testes

No IntelliJ: clique com o botão direito na pasta `src/test/java` → **Run 'All Tests'**.
Os testes usam um banco H2 em memória, então não precisam do Neon.

## 6. Endpoints

| Método | Rota | Acesso | Descrição |
|---|---|---|---|
| POST | `/api/auth/cadastro` | Público | Cria conta e devolve o token |
| POST | `/api/auth/login` | Público | Devolve o token JWT |
| GET | `/api/numeros/{numero}` | Público | Consulta o número e calcula o score de risco |
| GET | `/api/numeros/ranking?dias=30&limite=10` | **Token** | Números mais denunciados |
| POST | `/api/denuncias` | **Token** | Registra uma denúncia |
| GET | `/api/denuncias/minhas` | **Token** | Histórico do usuário logado |

Rotas com **Token** exigem o cabeçalho `Authorization: Bearer <token>`. Sem ele, a API responde `401`.

A coleção pronta para o Postman está em `docs/postman/AloSeguro.postman_collection.json` (Postman → Import). A requisição de login já guarda o token automaticamente para as próximas.

## 7. Score de risco (versão 1)

| Variável | Pontos |
|---|---|
| Denúncias nos últimos 30 dias | 6 por denúncia, até 30 |
| Pessoas diferentes que denunciaram | 6 por pessoa, até 30 |
| Celular se apresentando como banco | 15 |
| Pico: 3 ou mais denúncias em 24 horas | 15 |
| Golpista pediu senha, código, Pix, app ou cartão | 10 |

Faixas: até 30 **baixo** · até 70 **atenção** · acima de 70 **alto**.
Se o número for de uma **central oficial**, a resposta é sempre o alerta de spoofing, nunca "seguro".

A regra fica em `backend/src/main/java/br/com/aloseguro/service/ScoreRiscoService.java`.

## 8. App Android

Veja `android/README.md`.
