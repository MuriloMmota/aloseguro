package br.com.aloseguro;

import br.com.aloseguro.model.CentralOficial;
import br.com.aloseguro.repository.CentralOficialRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes funcionais da API: cada teste faz requisições HTTP contra a aplicação
 * (com banco H2 em memória e IA desligada) e confere o status e o corpo da resposta.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiFuncionalTest {

    private static final AtomicInteger SEQUENCIA = new AtomicInteger();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private CentralOficialRepository centrais;

    // ================================================================ autenticação

    @Test
    @DisplayName("RF01 - cadastro cria a conta e devolve token JWT")
    void cadastroDevolveToken() throws Exception {
        mvc.perform(post("/api/auth/cadastro").contentType(MediaType.APPLICATION_JSON)
                        .content(json("Fulano", emailNovo(), "senha123")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tipo").value("Bearer"));
    }

    @Test
    @DisplayName("RF01 - cadastro com e-mail repetido devolve 409")
    void cadastroEmailRepetido() throws Exception {
        String email = emailNovo();
        cadastrarEObterToken(email);

        mvc.perform(post("/api/auth/cadastro").contentType(MediaType.APPLICATION_JSON)
                        .content(json("Outro", email, "senha123")))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("RF01 - cadastro com dados inválidos devolve 400 com os 3 erros")
    void cadastroInvalido() throws Exception {
        mvc.perform(post("/api/auth/cadastro").contentType(MediaType.APPLICATION_JSON)
                        .content(json("", "nao-e-email", "123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalhes", hasSize(3)));
    }

    @Test
    @DisplayName("RF02 - login com senha correta devolve token")
    void loginCorreto() throws Exception {
        String email = emailNovo();
        cadastrarEObterToken(email);

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(login(email, "senha123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    @DisplayName("RF02 - login com senha errada devolve 401")
    void loginSenhaErrada() throws Exception {
        String email = emailNovo();
        cadastrarEObterToken(email);

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(login(email, "errada")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("RNF03 - após 3 senhas erradas o login fica bloqueado (429), mesmo com a senha certa")
    void bloqueioPorForcaBruta() throws Exception {
        String email = emailNovo();
        cadastrarEObterToken(email);
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(login(email, "errada")))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(login(email, "senha123")))
                .andExpect(status().isTooManyRequests());
    }

    // ================================================================ consulta e motor

    @Test
    @DisplayName("RF03 - consulta é pública e número sem denúncias devolve SEM_REGISTROS com os 6 fatores")
    void consultaPublicaSemRegistros() throws Exception {
        mvc.perform(get("/api/numeros/11987650000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nivel").value("SEM_REGISTROS"))
                .andExpect(jsonPath("$.numeroFormatado").value("(11) 98765-0000"))
                .andExpect(jsonPath("$.tipoLinha").value("CELULAR"))
                .andExpect(jsonPath("$.fatores", hasSize(6)));
    }

    @Test
    @DisplayName("RF03 - número inválido devolve 400")
    void consultaNumeroInvalido() throws Exception {
        mvc.perform(get("/api/numeros/123"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("RF10 - explicação do motor e status são públicos")
    void motorEStatusPublicos() throws Exception {
        mvc.perform(get("/api/motor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fatores", hasSize(6)))
                .andExpect(jsonPath("$.faixas", hasSize(5)));
        mvc.perform(get("/api/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.iaAtiva").value(false));
    }

    // ================================================================ segurança JWT

    @Test
    @DisplayName("RNF02 - denúncia sem token devolve 401")
    void denunciaSemToken() throws Exception {
        mvc.perform(post("/api/denuncias").contentType(MediaType.APPLICATION_JSON)
                        .content(denuncia("11911110001", "PEDIU_SENHA", "teste")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("RNF02 - denúncia com token adulterado devolve 401")
    void denunciaTokenInvalido() throws Exception {
        mvc.perform(post("/api/denuncias").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer token.falso.qualquer")
                        .content(denuncia("11911110002", "PEDIU_SENHA", "teste")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("RNF02 - ranking exige token: 401 sem token, 200 com token")
    void rankingExigeToken() throws Exception {
        mvc.perform(get("/api/numeros/ranking")).andExpect(status().isUnauthorized());

        String token = cadastrarEObterToken(emailNovo());
        mvc.perform(get("/api/numeros/ranking").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    // ================================================================ denúncias e score

    @Test
    @DisplayName("RF04/RF05 - denúncia com token devolve 201 e muda o nível do número")
    void denunciaAlteraScore() throws Exception {
        String numero = "11922220001";
        String token = cadastrarEObterToken(emailNovo());

        mvc.perform(post("/api/denuncias").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(denuncia(numero, "PEDIU_SENHA", "Ligação de teste automatizado")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numeroFormatado").value("(11) 92222-0001"))
                .andExpect(jsonPath("$.tags[*].tag", hasItem("PEDIU_SENHA")));

        // recência 10 + pessoas 3 (conta nova) + gravidade 7 + celular 10 + pico 3 = 33
        mvc.perform(get("/api/numeros/" + numero))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nivel").value("ATENCAO"))
                .andExpect(jsonPath("$.score").value(33))
                .andExpect(jsonPath("$.totalDenuncias").value(1))
                .andExpect(jsonPath("$.topicos[0].tag").value("PEDIU_SENHA"));
    }

    @Test
    @DisplayName("RF05 - várias pessoas denunciando levam o número a ALTO risco")
    void variasDenunciasGeramAltoRisco() throws Exception {
        String numero = "11933330001";
        for (int i = 0; i < 5; i++) {
            String token = cadastrarEObterToken(emailNovo());
            mvc.perform(post("/api/denuncias").contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + token)
                            .content(denuncia(numero, "PEDIU_TRANSFERENCIA", "Ligação de teste automatizado")))
                    .andExpect(status().isCreated());
        }

        mvc.perform(get("/api/numeros/" + numero))
                .andExpect(jsonPath("$.nivel").value("ALTO"))
                .andExpect(jsonPath("$.pessoasQueDenunciaram").value(5))
                .andExpect(jsonPath("$.confianca").value("MEDIA"));
    }

    @Test
    @DisplayName("RF11 - o comentário é classificado em tópicos (IA desligada: classificador por regras)")
    void comentarioViraTags() throws Exception {
        String token = cadastrarEObterToken(emailNovo());
        mvc.perform(post("/api/denuncias").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(denuncia("11966660001", "OUTRO",
                                "Disse que era urgente e pediu um Pix e o código que chegou por SMS")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tagsOrigem").value("REGRAS"))
                .andExpect(jsonPath("$.tags[*].tag", hasItem("PEDIU_PIX")))
                .andExpect(jsonPath("$.tags[*].tag", hasItem("PEDIU_CODIGO")))
                .andExpect(jsonPath("$.tags[*].tag", hasItem("URGENCIA")));
    }

    @Test
    @DisplayName("RF04 - mesma pessoa não pode denunciar o mesmo número duas vezes em 24h")
    void denunciaDuplicada() throws Exception {
        String token = cadastrarEObterToken(emailNovo());
        String corpo = denuncia("11944440001", "OUTRO", "teste");

        mvc.perform(post("/api/denuncias").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token).content(corpo))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/denuncias").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token).content(corpo))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("RF07 - número de central oficial devolve alerta de spoofing, nunca 'seguro'")
    void centralOficialDevolveAlertaSpoofing() throws Exception {
        centrais.save(new CentralOficial("Banco Teste", "08009990000", "SAC"));

        mvc.perform(get("/api/numeros/08009990000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nivel").value("CENTRAL_OFICIAL"))
                .andExpect(jsonPath("$.bancoOficial").value("Banco Teste"));
    }

    @Test
    @DisplayName("RF06/RF09 - ranking e histórico listam as denúncias feitas")
    void rankingEHistorico() throws Exception {
        String token = cadastrarEObterToken(emailNovo());
        mvc.perform(post("/api/denuncias").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(denuncia("21955550001", "PEDIU_CODIGO", "teste")))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/denuncias/minhas").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].tipoAbordagem").value("PEDIU_CODIGO"));

        mvc.perform(get("/api/numeros/ranking").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", greaterThan(0)));
    }

    // ================================================================ CRUD de denúncia

    @Test
    @DisplayName("CRUD - criar, ler, atualizar e excluir a própria denúncia")
    void crudCompletoDaDenuncia() throws Exception {
        String token = cadastrarEObterToken(emailNovo());
        long id = criarDenuncia(token, "11977770001", "OUTRO", "Ligação estranha");

        // READ
        mvc.perform(get("/api/denuncias/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descricao").value("Ligação estranha"));

        // UPDATE: novo texto é classificado de novo
        mvc.perform(put("/api/denuncias/" + id).contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"tipoAbordagem\":\"PEDIU_CARTAO\",\"bancoAlegado\":\"Banco Teste\","
                                + "\"descricao\":\"Disse que um motoboy ia buscar o cartão\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoAbordagem").value("PEDIU_CARTAO"))
                .andExpect(jsonPath("$.tags[*].tag", hasItem("RETIRADA_CARTAO")));

        // DELETE
        mvc.perform(delete("/api/denuncias/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/denuncias/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("CRUD - ninguém edita ou exclui a denúncia de outra pessoa (403)")
    void naoAlteraDenunciaDeOutro() throws Exception {
        String dono = cadastrarEObterToken(emailNovo());
        String outro = cadastrarEObterToken(emailNovo());
        long id = criarDenuncia(dono, "11988880001", "OUTRO", "teste");

        mvc.perform(put("/api/denuncias/" + id).contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + outro)
                        .content("{\"tipoAbordagem\":\"OUTRO\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/denuncias/" + id).header("Authorization", "Bearer " + outro))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CRUD - atualizar sem o campo obrigatório devolve 400")
    void atualizarInvalido() throws Exception {
        String token = cadastrarEObterToken(emailNovo());
        long id = criarDenuncia(token, "11999990001", "OUTRO", "teste");
        mvc.perform(put("/api/denuncias/" + id).contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"descricao\":\"sem tipo\"}"))
                .andExpect(status().isBadRequest());
    }

    // ================================================================ conta

    @Test
    @DisplayName("CRUD conta - ver, alterar nome e excluir a conta (apaga as denúncias junto)")
    void crudDaConta() throws Exception {
        String email = emailNovo();
        String token = cadastrarEObterToken(email);
        criarDenuncia(token, "31944440001", "OUTRO", "teste");

        mvc.perform(get("/api/conta").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.totalDenuncias").value(1));

        mvc.perform(put("/api/conta").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"nome\":\"Nome Novo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Nome Novo"));

        mvc.perform(delete("/api/conta").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // depois de excluída, o token antigo não vale mais e o login falha
        mvc.perform(get("/api/conta").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(login(email, "senha123")))
                .andExpect(status().isUnauthorized());
    }

    // ================================================================ auxiliares

    private String cadastrarEObterToken(String email) throws Exception {
        String resposta = mvc.perform(post("/api/auth/cadastro").contentType(MediaType.APPLICATION_JSON)
                        .content(json("Usuário de Teste", email, "senha123")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(resposta, "$.token");
    }

    private long criarDenuncia(String token, String numero, String tipo, String descricao) throws Exception {
        String resposta = mvc.perform(post("/api/denuncias").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(denuncia(numero, tipo, descricao)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(resposta, "$.id")).longValue();
    }

    private static String emailNovo() {
        return "teste" + SEQUENCIA.incrementAndGet() + "@aloseguro.com";
    }

    private static String json(String nome, String email, String senha) {
        return "{\"nome\":\"%s\",\"email\":\"%s\",\"senha\":\"%s\"}".formatted(nome, email, senha);
    }

    private static String login(String email, String senha) {
        return "{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, senha);
    }

    private static String denuncia(String numero, String tipo, String descricao) {
        return """
                {"numero":"%s","bancoAlegado":"Banco Teste","tipoAbordagem":"%s","descricao":"%s"}
                """.formatted(numero, tipo, descricao);
    }
}
