package br.com.aloseguro.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Comunicação com a API REST do AlôSeguro.
 * As chamadas são bloqueantes: sempre execute fora da thread principal.
 */
public class ApiClient {

    /** 10.0.2.2 é o endereço do computador visto de dentro do emulador Android. */
    public static final String SERVIDOR_PADRAO = "http://10.0.2.2:8080";

    private static final String PREFERENCIAS = "aloseguro";
    private static final String CHAVE_SERVIDOR = "servidor";

    private final String servidor;

    public ApiClient(Context contexto) {
        this.servidor = servidor(contexto);
    }

    public static String servidor(Context contexto) {
        return preferencias(contexto).getString(CHAVE_SERVIDOR, SERVIDOR_PADRAO);
    }

    public static void salvarServidor(Context contexto, String endereco) {
        String limpo = endereco.trim().replaceAll("/+$", "");
        if (!limpo.startsWith("http")) {
            limpo = "http://" + limpo;
        }
        preferencias(contexto).edit().putString(CHAVE_SERVIDOR, limpo).apply();
    }

    private static SharedPreferences preferencias(Context contexto) {
        return contexto.getSharedPreferences(PREFERENCIAS, Context.MODE_PRIVATE);
    }

    /** GET /api/numeros/{numero} — rota pública, não precisa de token. */
    public Consulta consultar(String numero) throws ApiException {
        String digitos = numero == null ? "" : numero.replaceAll("\\D", "");
        if (digitos.isEmpty()) {
            throw new ApiException("Digite um número de telefone.");
        }

        HttpURLConnection conexao = null;
        try {
            conexao = (HttpURLConnection) new URL(servidor + "/api/numeros/" + digitos).openConnection();
            conexao.setConnectTimeout(4000);
            conexao.setReadTimeout(4000);
            conexao.setRequestProperty("Accept", "application/json");

            int status = conexao.getResponseCode();
            InputStream entrada = status >= 400 ? conexao.getErrorStream() : conexao.getInputStream();
            String corpo = ler(entrada);
            JSONObject json = corpo.isEmpty() ? new JSONObject() : new JSONObject(corpo);

            if (status >= 400) {
                throw new ApiException(json.optString("mensagem", "Erro " + status + " ao consultar."));
            }
            return Consulta.de(json);

        } catch (IOException e) {
            throw new ApiException("Não foi possível conectar à API em " + servidor
                    + ". Confira se ela está rodando e se o endereço do servidor está correto.");
        } catch (JSONException e) {
            throw new ApiException("O servidor respondeu em um formato inesperado.");
        } finally {
            if (conexao != null) {
                conexao.disconnect();
            }
        }
    }

    private static String ler(InputStream entrada) throws IOException {
        if (entrada == null) {
            return "";
        }
        StringBuilder texto = new StringBuilder();
        try (BufferedReader leitor = new BufferedReader(new InputStreamReader(entrada, StandardCharsets.UTF_8))) {
            String linha;
            while ((linha = leitor.readLine()) != null) {
                texto.append(linha);
            }
        }
        return texto.toString();
    }

    public static class ApiException extends Exception {
        public ApiException(String mensagem) {
            super(mensagem);
        }
    }
}
