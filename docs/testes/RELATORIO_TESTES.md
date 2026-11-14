# Relatório de testes — AlôSeguro

Os testes automatizados rodam no IntelliJ (botão direito em `src/test/java` → *Run 'All Tests'*) e no GitHub Actions a cada envio (aba **Actions** do repositório).

| Tipo | Classe | Quantidade | O que cobre |
|---|---|---|---|
| Funcional (API REST) | `ApiFuncionalTest` | 22 | Endpoints, códigos HTTP, segurança JWT, CRUD, regras de negócio |
| Unidade (motor de risco) | `MotorRiscoTest` | 11 | Cada fator do cálculo, saturação, faixas |
| Unidade (IA) | `ClassificadorRelatosTest` | 8 | Leitura da resposta da IA, plano B por regras, prompt injection |
| Persistência | `PersistenciaTest` | 5 | CRUD no banco, integridade e consistência |
| Unidade (telefone) | `NumeroTelefoneTest` | 4 (9 casos) | Normalização, validação e formatação |
| Não funcional (desempenho) + carga | `DesempenhoTest` | 2 | 1000 números fictícios, tempo de resposta normal e em pico |
| Não funcional (desempenho na nuvem) | `docs/testes/carga-k6.js` | — | Carga no sistema publicado no Render |
| Não funcional (usabilidade) | `docs/testes/teste-usabilidade.md` | — | Tarefas com usuários + questionário SUS |

---

## 1. Testes funcionais da API (`ApiFuncionalTest`)

| ID | Teste | Requisição | Esperado |
|---|---|---|---|
| F01 | Cadastro válido | `POST /api/auth/cadastro` | 201 + token JWT |
| F02 | E-mail repetido | `POST /api/auth/cadastro` | 409 |
| F03 | Cadastro com dados inválidos | `POST /api/auth/cadastro` | 400 com os 3 erros |
| F04 | Login correto | `POST /api/auth/login` | 200 + token |
| F05 | Senha errada | `POST /api/auth/login` | 401 |
| F06 | Força bruta: 3 senhas erradas | `POST /api/auth/login` | 429 na 4ª tentativa |
| F07 | Consulta pública sem denúncias | `GET /api/numeros/{n}` | 200, `SEM_REGISTROS`, 6 fatores |
| F08 | Número inválido | `GET /api/numeros/123` | 400 |
| F09 | Motor e status públicos | `GET /api/motor`, `GET /api/status` | 200 |
| F10 | Denúncia sem token | `POST /api/denuncias` | 401 |
| F11 | Token adulterado | `POST /api/denuncias` | 401 |
| F12 | Ranking exige token | `GET /api/numeros/ranking` | 401 sem token, 200 com token |
| F13 | Denúncia muda o score | `POST` + `GET` | 201; nível `ATENCAO`, score 33 |
| F14 | 5 pessoas denunciando | `POST` ×5 + `GET` | nível `ALTO`, confiança média |
| F15 | Comentário vira tópicos | `POST /api/denuncias` | tags PIX, código e urgência |
| F16 | Denúncia repetida em 24 h | `POST` ×2 | 409 na segunda |
| F17 | Central oficial | `GET /api/numeros/0800...` | `CENTRAL_OFICIAL` |
| F18 | Histórico e ranking | `GET /minhas`, `GET /ranking` | 200 com a denúncia |
| F19 | CRUD completo da denúncia | `POST`, `GET`, `PUT`, `DELETE` | 201, 200, 200 (reclassificada), 204, 404 |
| F20 | Alterar denúncia de outra pessoa | `PUT`, `DELETE` | 403 |
| F21 | Atualizar sem campo obrigatório | `PUT /api/denuncias/{id}` | 400 |
| F22 | CRUD da conta | `GET`, `PUT`, `DELETE /api/conta` | 200, 200, 204; depois token e login recusados |

## 2. Persistência (`PersistenciaTest`)

| ID | Teste | Esperado |
|---|---|---|
| P01 | CRUD de denúncia direto no banco | criar, ler, atualizar e excluir funcionam |
| P02 | E-mail duplicado | o banco recusa (restrição UNIQUE) |
| P03 | Número duplicado | o banco recusa (restrição UNIQUE) |
| P04 | Apagar usuário com denúncias | o banco recusa (chave estrangeira) |
| P05 | Tags gravadas e lidas | os mesmos tópicos voltam, sem perda |

## 3. Motor de risco (`MotorRiscoTest`)

| ID | Teste | Esperado |
|---|---|---|
| M01 | Sem denúncias | score 0, `SEM_REGISTROS` |
| M02 | Número oficial | sempre `CENTRAL_OFICIAL` |
| M03 | Recência | hoje = 10 pontos; 60 dias atrás = 0 |
| M04 | Conta nova | pesa 3 pontos contra 8 de uma conta antiga |
| M05 | Mesma pessoa duas vezes | conta uma vez só |
| M06 | 50 denúncias | nenhum fator passa do máximo; total 100 |
| M07 | Gravidade | relatos graves = 20; leves = 4 |
| M08 | Perfil da linha | celular 10, fixo 4 |
| M09 | Pico | 3 denúncias em 24 h = 10 |
| M10 | Bloco suspeito | número novo num bloco denunciado = `ATENCAO` |
| M11 | Faixas | 29 baixo, 30 atenção, 60 alto |

## 4. Classificação por IA (`ClassificadorRelatosTest`)

| ID | Teste | Esperado |
|---|---|---|
| I01 | IA respondendo | tags vêm da IA, origem `IA` |
| I02 | Resposta com texto em volta | JSON é extraído mesmo assim |
| I03 | Prompt injection | tags inventadas são descartadas |
| I04 | IA fora do ar | classificador por palavras-chave assume |
| I05 | Opção do formulário | sempre vira tag |
| I06 | Tag "Outro" | só aparece quando nada mais foi encontrado |
| I07 | Acentos e maiúsculas | "CÓDIGO" e "codigo" são iguais |
| I08 | Comando enviado à IA | lista todas as tags permitidas |

## 5. Desempenho (`DesempenhoTest` e k6)

**Na máquina** (`DesempenhoTest`, banco H2 com 1000 números): 300 consultas seguidas e 500 consultas com 20 usuários simultâneos. Critério: 95% das respostas abaixo de 500 ms. O relatório é gravado em `backend/target/relatorio-desempenho.txt`.

| Cenário | Média | p95 | Máximo | Resultado |
|---|---|---|---|---|
| Normal | [preencher após rodar] | | | |
| Pico (20 simultâneos) | | | | |

**Na nuvem** (`k6`, Render + Neon): 5 usuários por 1 minuto e depois 30 usuários simultâneos.

| Cenário | Média | p95 | Erros | Resultado |
|---|---|---|---|---|
| Normal (5 usuários) | [preencher após rodar] | | | |
| Pico (30 usuários) | | | | |

## 6. Usabilidade

Ver `teste-usabilidade.md`. Resultado: [preencher: média SUS, taxa de sucesso por tarefa e o que foi ajustado].
