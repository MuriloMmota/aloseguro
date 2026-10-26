package br.com.aloseguro.dto;

import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;

public record ErroResponse(int status, String erro, String mensagem, List<String> detalhes, Instant timestamp) {

    public static ErroResponse de(HttpStatus status, String mensagem, List<String> detalhes) {
        return new ErroResponse(status.value(), status.getReasonPhrase(), mensagem, detalhes, Instant.now());
    }
}
