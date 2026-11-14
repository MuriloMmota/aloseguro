# Guia técnico do AlôSeguro (para o grupo estudar)

A professora pediu que todos saibam o projeto inteiro de forma técnica. Este guia segue o caminho de uma requisição, do celular até o banco, e no final tem as perguntas que ela provavelmente vai fazer.

---

## 1. Visão geral em uma frase

Um **site responsivo** (que também funciona como app instalado, via PWA) conversa por **HTTPS/JSON** com uma **API REST em Java/Spring Boot** hospedada no **Render**, que guarda os dados num **PostgreSQL gerenciado no Neon** e usa uma **IA (Gemini)** para ler os comentários das denúncias.

### Por que é um sistema distribuído

São processos independentes, em máquinas diferentes, que só se comunicam pela rede:

| Componente | Onde roda | Como se comunica |
|---|---|---|
| Site / PWA | Navegador do usuário | HTTP + JSON com a API |
| API REST | Container Docker no Render (EUA) | Recebe HTTP; fala JDBC com o banco; HTTP com a IA |
| PostgreSQL | Neon (nuvem gerenciada) | Protocolo PostgreSQL com SSL |
| IA (LLM) | Google (Gemini) | HTTP + JSON, formato compatível com a OpenAI |

Características de sistema distribuído que dá para citar:
- **Transparência de localização:** o usuário só vê um endereço; não sabe onde estão a API, o banco ou a IA
- **Tolerância a falhas:** se a IA cair ou demorar mais de 8 s, o sistema usa o classificador por palavras-chave e continua funcionando
- **API sem estado (stateless):** o servidor não guarda sessão; cada requisição traz o token JWT. Isso permite subir mais de uma instância da API se precisar
- **Escalabilidade:** banco e API escalam separados; o pool de conexões (Hikari) é limitado para não estourar o plano gratuito do Neon

---

## 2. Camadas do backend

```
controller  → recebe a requisição HTTP, valida o JSON (@Valid) e devolve o status
service     → regras de negócio (quem pode editar, limite de 24 h, etc.)
motor       → cálculo do score (não acessa banco, por isso é fácil de testar)
ia          → classificação dos relatos (IA + plano B por regras)
repository  → acesso ao banco com Spring Data JPA
model       → entidades (tabelas) e enums
security    → JWT, filtro de autenticação, limite de tentativas de login
config      → Spring Security, carga dos 1000 números fictícios
exception   → transforma erros em respostas JSON padronizadas
```

### Classes principais

| Classe | O que faz |
|---|---|
| `AuthController` / `AuthService` | Cadastro e login. Senha com **BCrypt**. Devolve o token JWT |
| `NumeroController` | Consulta pública de um número e ranking |
| `DenunciaController` / `DenunciaService` | CRUD de denúncias. Só o dono edita ou exclui (senão 403). Bloqueia a mesma pessoa denunciar o mesmo número 2 vezes em 24 h (409) |
| `ContaController` / `ContaService` | Ver perfil, mudar nome, excluir conta (apaga as denúncias da pessoa junto) |
| `InfoController` | `/api/motor` (explica o cálculo) e `/api/status` (totais e situação da IA) |
| `RiscoService` | Busca os dados no banco, chama o `MotorRisco` e monta a resposta com fatores, tópicos e relatos |
| `MotorRisco` | Calcula o score de 0 a 100 |
| `ClassificadorRelatos` | Manda o comentário para a IA e lê as tags; se falhar, usa `ClassificadorPorRegras` |
| `ClienteLlm` | Faz a chamada HTTP para a IA (java.net.http, sem biblioteca extra) |
| `NumeroTelefone` | Limpa, valida e formata o número; descobre se é celular, fixo, 0800 ou 4004 |
| `CargaDadosFicticios` | Gera os 1000 números, ~3.200 denúncias e 126 usuários se o banco estiver vazio |
| `JwtService` / `JwtAuthenticationFilter` | Gera e valida o token em cada requisição |
| `LimiteTentativasLogin` | Depois de 5 senhas erradas, bloqueia o e-mail por 15 minutos (429) |
| `GlobalExceptionHandler` | Erros viram JSON: 400 validação, 401, 403, 404, 409 conflito, 429 |

### Banco de dados (4 tabelas)

| Tabela | Campos importantes |
|---|---|
| `usuarios` | id, nome, email (único), senha_hash (BCrypt), criado_em |
| `numeros_suspeitos` | id, numero (único, só dígitos), ddd, tipo_linha, criado_em |
| `denuncias` | id, usuario_id (FK), numero_id (FK), banco_alegado, tipo_abordagem, descricao, data_hora_ligacao, **tags**, **tags_origem** (IA/REGRAS/SEED), criado_em |
| `centrais_oficiais` | id, banco, numero, descricao — usada para o alerta de spoofing |

As tabelas são criadas pelo Hibernate (`ddl-auto=update`). As chaves estrangeiras garantem a integridade: não dá para ter denúncia de usuário que não existe.

---

## 3. Caminho de uma consulta

`GET /api/numeros/11900000001`

1. O `JwtAuthenticationFilter` roda, mas essa rota é pública, então segue sem token
2. `NumeroController` chama `RiscoService.consultar`
3. `NumeroTelefone` normaliza (`(11) 90000-0001` → `11900000001`) e descobre que é **celular**. Se for inválido → 400
4. Verifica se é uma central oficial cadastrada
5. Busca as denúncias do número e conta quantos **vizinhos** (mesmo prefixo de 7 dígitos) foram denunciados
6. `MotorRisco.calcular` devolve score, nível, confiança e os 6 fatores
7. A resposta JSON inclui também os **tópicos** mais citados e os **relatos recentes**
8. O site (`consulta.js`) monta a tela: barra do score, fatores, tópicos e relatos

## 4. Caminho de uma denúncia

`POST /api/denuncias` com `Authorization: Bearer <token>`

1. O filtro valida a assinatura e a validade do token (2 horas). Sem token ou adulterado → 401
2. `@Valid` confere os campos (tipo de abordagem obrigatório, descrição até 500 caracteres...)
3. `DenunciaService` verifica a regra das 24 h (409 se repetir)
4. `ClassificadorRelatos` manda o comentário para a IA com `temperature 0` e uma lista fixa de tags. A IA responde em JSON; tags fora da lista são descartadas
5. Se a IA não tiver chave, demorar ou responder errado, o classificador por palavras-chave assume (origem `REGRAS`)
6. Salva no banco e devolve 201 com as tags identificadas, que aparecem na tela de confirmação

---

## 5. Motor de risco — as contas

Score = soma de 6 fatores (máximo 100):

| Fator | Máx. | Fórmula |
|---|---|---|
| Recência | 25 | cada denúncia vale `e^(−dias/15)`; pontos = `25 × (1 − e^(−soma/2))` |
| Pessoas | 25 | cada pessoa conta uma vez; conta < 1 dia vale 0,3, < 30 dias 0,7, antiga 1; pontos = `25 × (1 − e^(−soma/2,5))` |
| Gravidade | 20 | `20 × média da gravidade × min(1, denúncias/3)` |
| Linha | 10 | celular 10, fixo 4, 0800/4004 0 |
| Pico | 10 | denúncias nas últimas 24 h × 3,3 (máx. 10) |
| Vizinhança | 10 | 2 por vizinho denunciado (máx. 10) |

**Gravidade das tags:** pediu senha, código, Pix, acesso remoto ou cartão = 1,0 · dados pessoais 0,6 · falso setor e urgência 0,5 · compra suspeita 0,4 · outro 0,2.

**Faixas:** 0–29 baixo · 30–59 atenção · 60+ alto. Sem denúncia = "sem registros", a não ser que o bloco seja suspeito (vizinhança ≥ 6 → atenção). Central oficial = sempre alerta de spoofing.

**Confiança:** menos de 3 denúncias baixa · 3 a 5 média · 6 ou mais alta.

**Por que a curva `1 − e^(−x)`?** Ela cresce rápido nas primeiras denúncias e depois para de crescer. Assim, 50 denúncias não valem 50 vezes mais que uma, e um ataque com muitas contas falsas não leva um número inocente ao máximo (ainda mais porque conta nova pesa 0,3).

**Exemplo (teste F13):** 1 denúncia hoje, conta nova, celular, pediu senha:
recência 25×(1−e^(−0,5)) = 10 · pessoas 25×(1−e^(−0,12)) = 3 · gravidade 20×1×⅓ = 7 · linha 10 · pico 3 · vizinhança 0 → **33 = atenção**.

**Onde está a "IA" no motor:** a IA não dá a nota. Ela lê o texto livre e transforma em tópicos padronizados; o motor usa esses tópicos no fator gravidade. Assim a nota continua explicável e testável.

---

## 6. Segurança

| Item | Como foi feito |
|---|---|
| Autenticação | JWT assinado com HMAC-SHA256, válido por 2 horas, chave em variável de ambiente |
| Senhas | BCrypt (hash com salt), nunca guardadas em texto |
| Autorização | Rotas públicas só de leitura; denunciar, ranking e conta exigem token; só o dono altera a denúncia |
| Força bruta | 5 senhas erradas → bloqueio de 15 min (429) |
| Denúncia em massa | Uma por pessoa por número a cada 24 h; conta nova pesa menos no score |
| Validação | Bean Validation em todos os DTOs; número de telefone validado |
| SQL injection | JPA com parâmetros, nunca concatenação de texto |
| Prompt injection | A IA só pode devolver tags de uma lista fixa; o resto é descartado |
| Segredos | Senha do banco, chave JWT e chave da IA só em variáveis de ambiente (Render), nunca no GitHub |
| Transporte | HTTPS no Render e SSL obrigatório no Neon (`sslmode=require`) |
| CSRF | Desligado porque a API não usa cookie (token vai no cabeçalho) |

---

## 7. Site, acessibilidade e mobile

- HTML, CSS e JavaScript puros, servidos pela própria API (mesma origem, sem problema de CORS)
- **Responsivo:** abaixo de 860 px o menu vira botão (hambúrguer); testado em 390 px sem rolagem lateral
- **PWA:** `manifest.json` + `sw.js` → dá para instalar na tela inicial do celular; se cair a internet, mostra a página `offline.html`
- **Acessibilidade (WCAG 2.1 AA):**
  - link "Pular para o conteúdo" (primeiro Tab)
  - botões A− / A+ para o tamanho da letra e modo **alto contraste** (amarelo no preto)
  - **VLibras** (tradução para Libras, do governo federal)
  - contraste mínimo de 4,5:1 em todos os textos (tabela na página Acessibilidade)
  - o nível de risco nunca depende só da cor: sempre tem ícone e texto
  - `aria-live` para anunciar o resultado no leitor de tela; barra do score com `role="meter"`
  - formulários com `label`, `aria-invalid` e mensagem de erro ligada ao campo
- **Cores:** azul-petróleo/ciano passam confiança (identidade de segurança, sem copiar banco real); verde, âmbar e vermelho seguem o padrão de semáforo que todo mundo entende; azul para central oficial

---

## 8. Nuvem e entrega contínua

```
git push → GitHub Actions roda os testes (mvn verify)
         → Render detecta o push, monta a imagem Docker e publica
```

- **Dockerfile** em 2 etapas: uma com Maven para compilar, outra só com o Java 21 (imagem menor)
- **render.yaml** descreve o serviço (Blueprint): Docker, plano gratuito, região Virginia, health check em `/api/status`
- Neon e Render na mesma região (EUA) para diminuir a latência entre API e banco
- Plano gratuito do Render dorme após 15 min sem acesso

---

## 9. Testes

52 testes automatizados (57 casos, contando os parametrizados) + teste de carga (k6) + teste de usabilidade (SUS). Detalhes em `docs/testes/RELATORIO_TESTES.md`.

---

## 10. Perguntas prováveis da banca

**Por que isso é um sistema distribuído e não só um site?**
Porque cliente, API, banco e IA são processos separados em máquinas diferentes, comunicando-se por rede, com tolerância a falha da IA e API stateless.

**O que é REST?**
Estilo de API em que cada recurso tem uma URL (`/api/denuncias/5`) e o verbo HTTP diz a ação (GET lê, POST cria, PUT altera, DELETE apaga). A resposta é JSON e o código HTTP diz o resultado (200, 201, 400, 401, 403, 404, 409).

**Como funciona o JWT?**
No login a API gera um token com o e-mail e a validade, assinado com uma chave secreta. O cliente manda esse token em cada requisição. A API confere a assinatura; se alguém mudar uma letra, a assinatura não bate e volta 401.

**E se a IA estiver fora do ar?**
O `ClassificadorRelatos` espera no máximo 8 segundos. Se falhar, usa o classificador por palavras-chave. A denúncia é salva igual, com origem `REGRAS`.

**A IA decide se o número é golpe?**
Não. A IA só transforma o comentário em tópicos. Quem dá a nota é o motor, com fórmula fixa e explicada na tela.

**Como impedem alguém de denunciar um número inocente várias vezes?**
Uma denúncia por pessoa por número a cada 24 h, conta nova pesa 0,3, e a curva de saturação impede chegar a 100 só com volume.

**Por que o 0800 do banco aparece com alerta?**
Golpistas conseguem falsificar o número que aparece no identificador (spoofing). Por isso o sistema nunca diz "seguro": orienta a desligar e ligar de volta pelo número do cartão.

**De onde vêm os 1000 números?**
São fictícios, gerados pela classe `CargaDadosFicticios` com semente fixa (sempre os mesmos). Os bancos também são inventados (Aurora, Horizonte...).

**Onde fica a senha do banco?**
Só nas variáveis de ambiente do Render e do IntelliJ. O GitHub não tem nenhuma senha.

**Qual foi o resultado de desempenho?**
[preencher com os números do `relatorio-desempenho.txt` e do k6]

**O que fariam a seguir?**
Integração com operadoras para bloquear a chamada, app nativo com identificador de chamadas, e moderação das denúncias.
