package br.com.aloseguro.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Proteção contra "força bruta" no login: depois de N senhas erradas seguidas para o mesmo
 * e-mail, novas tentativas são recusadas (HTTP 429) por alguns minutos.
 *
 * Fica em memória: se houver mais de uma instância da API, cada uma conta separado.
 * Para este projeto, com uma instância no Render, é suficiente.
 */
@Component
public class LimiteTentativasLogin {

    private record Tentativas(int falhas, Instant bloqueadoAte) {
    }

    private final int maximoFalhas;
    private final Duration bloqueio;
    private final Map<String, Tentativas> porEmail = new ConcurrentHashMap<>();

    public LimiteTentativasLogin(@Value("${aloseguro.login.maximo-falhas:5}") int maximoFalhas,
                                 @Value("${aloseguro.login.bloqueio-minutos:15}") long bloqueioMinutos) {
        this.maximoFalhas = maximoFalhas;
        this.bloqueio = Duration.ofMinutes(bloqueioMinutos);
    }

    /** Devolve quantos segundos faltam de bloqueio, ou 0 se o e-mail pode tentar. */
    public long segundosBloqueado(String email) {
        Tentativas t = porEmail.get(chave(email));
        if (t == null || t.bloqueadoAte() == null) {
            return 0;
        }
        long restante = Duration.between(Instant.now(), t.bloqueadoAte()).getSeconds();
        if (restante <= 0) {
            porEmail.remove(chave(email));
            return 0;
        }
        return restante;
    }

    public void registrarFalha(String email) {
        porEmail.compute(chave(email), (k, atual) -> {
            int falhas = (atual == null ? 0 : atual.falhas()) + 1;
            Instant ate = falhas >= maximoFalhas ? Instant.now().plus(bloqueio) : null;
            return new Tentativas(falhas, ate);
        });
    }

    public void registrarSucesso(String email) {
        porEmail.remove(chave(email));
    }

    private static String chave(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
