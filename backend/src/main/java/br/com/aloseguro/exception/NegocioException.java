package br.com.aloseguro.exception;

import org.springframework.http.HttpStatus;

/**
 * Erro de regra de negócio com o status HTTP que deve ser devolvido ao cliente.
 */
public class NegocioException extends RuntimeException {

    private final HttpStatus status;

    public NegocioException(HttpStatus status, String mensagem) {
        super(mensagem);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
