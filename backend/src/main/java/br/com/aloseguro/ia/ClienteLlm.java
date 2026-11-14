package br.com.aloseguro.ia;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Cliente HTTP para um modelo de linguagem (LLM) no padrão "chat completions" da OpenAI.
 *
 * O padrão é o Google Gemini, que oferece esse formato compatível. Para trocar de provedor
 * (OpenAI, Groq, OpenRouter...) basta mudar LLM_BASE_URL, LLM_MODEL e LLM_API_KEY:
 * nenhuma linha de código muda.
 *
 * Sem chave configurada, o cliente fica desligado e o sistema usa só o classificador por regras.
 */
@Component
public class ClienteLlm {

    private static final Logger log = LoggerFactory.getLogger(ClienteLlm.class);

    private final String baseUrl;
    private final String apiKey;
    private final String modelo;
    private final Duration timeout;
    private final ObjectMapper json;
    private final HttpClient http;

    private volatile String ultimoErro;
    private volatile Instant ultimoSucesso;

    public ClienteLlm(@Value("${aloseguro.ia.base-url}") String baseUrl,
                      @Value("${aloseguro.ia.api-key:}") String apiKey,
                      @Value("${aloseguro.ia.modelo}") String modelo,
                      @Value("${aloseguro.ia.timeout-segundos:8}") long timeoutSegundos,
                      ObjectMapper json) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.modelo = modelo;
        this.timeout = Duration.ofSeconds(timeoutSegundos);
        this.json = json;
        this.http = HttpClient.newBuilder().connectTimeout(this.timeout).build();
    }

    public boolean ativo() {
        return !apiKey.isEmpty();
    }

    /**
     * Envia as instruções e o texto ao modelo e devolve o conteúdo da resposta.
     * Qualquer falha (sem rede, chave inválida, demora) devolve vazio: quem chama usa o plano B.
     */
    public Optional<String> completar(String instrucoes, String texto) {
        if (!ativo()) {
            return Optional.empty();
        }
        try {
            ObjectNode corpo = json.createObjectNode();
            corpo.put("model", modelo);
            corpo.put("temperature", 0);
            ArrayNode mensagens = corpo.putArray("messages");
            mensagens.addObject().put("role", "system").put("content", instrucoes);
            mensagens.addObject().put("role", "user").put("content", texto);

            HttpRequest requisicao = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(corpo)))
                    .build();

            HttpResponse<String> resposta = http.send(requisicao, HttpResponse.BodyHandlers.ofString());
            if (resposta.statusCode() != 200) {
                registrarErro("HTTP " + resposta.statusCode() + ": " + resumir(resposta.body()));
                return Optional.empty();
            }

            JsonNode conteudo = json.readTree(resposta.body()).path("choices").path(0).path("message").path("content");
            if (conteudo.isMissingNode() || conteudo.asText().isBlank()) {
                registrarErro("Resposta sem conteúdo");
                return Optional.empty();
            }
            ultimoSucesso = Instant.now();
            ultimoErro = null;
            return Optional.of(conteudo.asText());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            registrarErro("Interrompido");
            return Optional.empty();
        } catch (Exception e) {
            registrarErro(e.getClass().getSimpleName() + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    private void registrarErro(String erro) {
        ultimoErro = erro;
        log.warn("IA indisponível, usando classificação por regras. Motivo: {}", erro);
    }

    private static String resumir(String texto) {
        if (texto == null) {
            return "";
        }
        return texto.length() > 200 ? texto.substring(0, 200) + "..." : texto;
    }

    public String getModelo() {
        return modelo;
    }

    public String getUltimoErro() {
        return ultimoErro;
    }

    public Instant getUltimoSucesso() {
        return ultimoSucesso;
    }

    public String getProvedor() {
        return baseUrl.contains("googleapis") ? "Google Gemini" : baseUrl;
    }
}
