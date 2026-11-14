# Publicar o AlôSeguro na nuvem (Render + Neon)

Resultado final: um endereço público, tipo `https://aloseguro.onrender.com`, que abre no computador e no celular.

## Antes de começar

- O código atualizado já tem que estar no GitHub (`MuriloMmota/aloseguro`)
- Quem for criar no Render precisa ter acesso ao repositório. O mais simples é o **Murilo** criar, entrando no Render com a conta do GitHub dele

## Passo 1 — Banco (Neon)

**Opção recomendada:** criar um projeto novo no Neon na região **AWS US East 1 (N. Virginia)**, a mesma região do Render. Assim cada consulta ao banco não precisa ir e voltar do Brasil.

1. https://console.neon.tech → **New Project** → nome `aloseguro-prod` → região **AWS US East 1**
2. **Connect** → copie a *connection string* `postgresql://USUARIO:SENHA@HOST/neondb?sslmode=require`
3. Separe em três valores:
   - `DB_URL` = `jdbc:postgresql://HOST/neondb?sslmode=require`
   - `DB_USER` = `USUARIO`
   - `DB_PASSWORD` = `SENHA`

**Opção rápida:** usar o banco que já existe (São Paulo). Funciona, só fica um pouco mais lento. Como ele tem os dados antigos do checkpoint 2, no primeiro deploy coloque `DADOS_DEMO_RECRIAR=true` e, depois que subir, volte para `false`.

## Passo 2 — Chave da IA (opcional, mas recomendada)

1. https://aistudio.google.com/apikey → entre com uma conta Google → **Create API key**
2. Guarde a chave: ela vai na variável `LLM_API_KEY`

Sem a chave o sistema funciona igual, só que os tópicos dos relatos são identificados por palavras-chave em vez da IA. A página "Como funciona" mostra qual dos dois está ativo.

## Passo 3 — Render

1. https://render.com → **Get Started** → entre com o GitHub
2. **New + → Blueprint** → escolha o repositório `aloseguro`
3. O Render lê o arquivo `render.yaml` e mostra o serviço `aloseguro`. Ele pede os valores que não ficam no GitHub:

| Variável | Valor |
|---|---|
| `DB_URL` | do passo 1 |
| `DB_USER` | do passo 1 |
| `DB_PASSWORD` | do passo 1 |
| `LLM_API_KEY` | do passo 2 (pode deixar em branco) |

O `JWT_SECRET` é gerado sozinho pelo Render.

4. **Apply**. O primeiro deploy leva uns 5 a 10 minutos (ele baixa as bibliotecas e monta a imagem Docker)
5. Quando aparecer **Live**, clique no endereço lá em cima. Na primeira subida, a carga de 1000 números leva alguns segundos a mais

## Passo 4 — Conferir

- [ ] `https://SEU-ENDERECO.onrender.com/api/status` mostra `numerosCadastrados` perto de 1000
- [ ] Consultar `(11) 90000-0001` dá **alto risco**
- [ ] A página "Como funciona" mostra a IA como ativa (se colocou a chave)
- [ ] Abrir no celular, entrar com `demo@aloseguro.com` / `demo123` e fazer uma denúncia
- [ ] Se usou `DADOS_DEMO_RECRIAR=true`: voltar para `false` em **Environment** (senão apaga tudo a cada deploy)

## Observações

- **Plano gratuito dorme:** depois de 15 minutos sem acesso, o servidor desliga. O primeiro acesso depois disso demora perto de 1 minuto. **Antes de gravar o vídeo ou apresentar, abra o site uns 2 minutos antes.**
- **Deploy automático:** cada envio para o GitHub publica a versão nova sozinho
- **Logs:** no painel do serviço, aba **Logs** (é onde aparece qualquer erro)
- **Instalar no celular:** no Chrome do Android, menu ⋮ → **Adicionar à tela inicial**. No iPhone, Safari → Compartilhar → **Adicionar à Tela de Início**
