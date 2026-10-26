package br.com.aloseguro.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Objetos de entrada e saída da autenticação.
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record CadastroRequest(
            @NotBlank(message = "é obrigatório") @Size(max = 100, message = "deve ter no máximo 100 caracteres") String nome,
            @NotBlank(message = "é obrigatório") @Email(message = "deve ser um e-mail válido") @Size(max = 150) String email,
            @NotBlank(message = "é obrigatória") @Size(min = 6, max = 72, message = "deve ter entre 6 e 72 caracteres") String senha) {
    }

    public record LoginRequest(
            @NotBlank(message = "é obrigatório") @Email(message = "deve ser um e-mail válido") String email,
            @NotBlank(message = "é obrigatória") String senha) {
    }

    public record TokenResponse(String token, String tipo, long expiraEmMinutos, String nome, String email) {
    }
}
