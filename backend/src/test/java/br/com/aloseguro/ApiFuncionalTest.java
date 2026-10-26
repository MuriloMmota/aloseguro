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
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes funcionais da API: cada teste faz requisições HTTP reais contra a aplicação
 * (com banco H2 em memória) e confere o status e o corpo da resposta.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiFuncionalTest {

    private static final AtomicInteger SEQUENCIA = new AtomicInteger();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private CentralOficialRepository centrais;

    // ---------------------------------------------------------------- autenticação

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
    @DisplayName("RF01 - cadastro com dados inválidos devolve 400")
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
                        .content("{\"email\":\"%s\",\"senha\":\"senha123\"}".formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    @DisplayName("RF02 - login com senha errada devolve 401")
    void loginSenhaErrada() throws Exception {
        String email = emailNovo();
        cadastrarEObterToken(email);

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"senha\":\"errada\"}".formatted(email)))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- consulta

    @Test
    @DisplayName("RF03 - consulta é pública e número sem denúncias devolve SEM_REGISTROS")
    void consultaPublicaSemRegistros() throws Exception {
        mvc.perform(get("/api/numeros/11987650000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nivel").value("SEM_REGISTROS"))
                .andExpect(jsonPath("$.numeroFormatado").value("(11) 98765-0000"))
                .andExpect(jsonPath("$.tipoLinha").value("CELULAR"));
    }

    @Test
    @DisplayName("RF03 - número inválido devolve 400")
    void consultaNumeroInvalido() throws Exception {
        mvc.perform(get("/api/numeros/123"))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- segurança JWT

    @Test
    @DisplayName("RNF02 - denúncia sem token devolve 401")
    void denunciaSemToken() throws Exception {
        mvc.perform(post("/api/denuncias").contentType(MediaType.APPLICATION_JSON)
                        .content(denuncia("11911110001", "PEDIU_SENHA")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("RNF02 - denúncia com token adulterado devolve 401")
    void denunciaTokenInvalido() throws Exception {
        mvc.perform(post("/api/denuncias").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer token.falso.qualquer")
                        .content(denuncia("11911110002", "PEDIU_SENHA")))
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

    // ---------------------------------------------------------------- denúncia e score

    @Test
    @DisplayName("RF04/RF05 - denúncia com token devolve 201 e altera o score do número")
    void denunciaAlteraScore() throws Exception {
        String numero = "11922220001";
        String token = cadastrarEObterToken(emailNovo());

        mvc.perform(post("/api/denuncias").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(denuncia(numero, "PEDIU_SENHA")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numeroFormatado").value("(11) 92222-0001"));

        // 1 denúncia recente (6) + 1 pessoa (6) + celular (15) + pediu senha (10) = 37
        mvc.perform(get("/api/numeros/" + numero))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nivel").value("ATENCAO"))
                .andExpect(jsonPath("$.score").value(37))
                .andExpect(jsonPath("$.totalDenuncias").value(1));
    }

    @Test
    @DisplayName("RF05 - várias pessoas denunciando elevam o número para ALTO risco")
    void variasDenunciasGeramAltoRisco() throws Exception {
        String numero = "11933330001";
        for (int i = 0; i < 5; i++) {
            String token = cadastrarEObterToken(emailNovo());
            mvc.perform(post("/api/denuncias").contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + token)
                            .content(denuncia(numero, "PEDIU_TRANSFERENCIA")))
                    .andExpect(status().isCreated());
        }

        // 30 + 30 + 15 (celular) + 15 (pico) + 10 (crítica) = 100
        mvc.perform(get("/api/numeros/" + numero))
                .andExpect(jsonPath("$.nivel").value("ALTO"))
                .andExpect(jsonPath("$.score").value(100))
                .andExpect(jsonPath("$.pessoasQueDenunciaram").value(5));
    }

    @Test
    @DisplayName("RF04 - mesma pessoa não pode denunciar o mesmo número duas vezes em 24h")
    void denunciaDuplicada() throws Exception {
        String token = cadastrarEObterToken(emailNovo());
        String corpo = denuncia("11944440001", "OUTRO");

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
                        .content(denuncia("21955550001", "PEDIU_CODIGO")))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/denuncias/minhas").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].tipoAbordagem").value("PEDIU_CODIGO"));

        mvc.perform(get("/api/numeros/ranking").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", greaterThan(0)));
    }

    // ---------------------------------------------------------------- auxiliares

    private String cadastrarEObterToken(String email) throws Exception {
        String resposta = mvc.perform(post("/api/auth/cadastro").contentType(MediaType.APPLICATION_JSON)
                        .content(json("Usuário de Teste", email, "senha123")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(resposta, "$.token");
    }

    private static String emailNovo() {
        return "teste" + SEQUENCIA.incrementAndGet() + "@aloseguro.com";
    }

    private static String json(String nome, String email, String senha) {
        return "{\"nome\":\"%s\",\"email\":\"%s\",\"senha\":\"%s\"}".formatted(nome, email, senha);
    }

    private static String denuncia(String numero, String tipo) {
        return """
                {"numero":"%s","bancoAlegado":"Banco Teste","tipoAbordagem":"%s",
                 "descricao":"Ligação de teste automatizado"}
                """.formatted(numero, tipo);
    }
}
