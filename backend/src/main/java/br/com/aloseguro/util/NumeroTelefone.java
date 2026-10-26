package br.com.aloseguro.util;

import br.com.aloseguro.exception.NegocioException;
import br.com.aloseguro.model.TipoLinha;
import org.springframework.http.HttpStatus;

/**
 * Normaliza, valida e formata números de telefone brasileiros.
 * Todo número é guardado e comparado só com dígitos, sem o +55.
 */
public final class NumeroTelefone {

    private NumeroTelefone() {
    }

    public static String normalizar(String entrada) {
        if (entrada == null || entrada.isBlank()) {
            throw invalido("Informe um número de telefone.");
        }
        String digitos = entrada.replaceAll("\\D", "");

        // Remove o código do país quando a pessoa digita +55
        if (digitos.startsWith("55") && (digitos.length() == 12 || digitos.length() == 13)) {
            digitos = digitos.substring(2);
        }

        if (tipoOuNulo(digitos) == null) {
            throw invalido("Número inválido. Use DDD + número, um 0800 ou um número de capitais (4004 / 3003).");
        }
        return digitos;
    }

    public static TipoLinha tipo(String digitos) {
        TipoLinha tipo = tipoOuNulo(digitos);
        if (tipo == null) {
            throw invalido("Número inválido.");
        }
        return tipo;
    }

    public static String ddd(String digitos) {
        TipoLinha tipo = tipo(digitos);
        return (tipo == TipoLinha.CELULAR || tipo == TipoLinha.FIXO) ? digitos.substring(0, 2) : null;
    }

    public static String formatar(String digitos) {
        return switch (tipo(digitos)) {
            case CELULAR -> "(%s) %s-%s".formatted(digitos.substring(0, 2), digitos.substring(2, 7), digitos.substring(7));
            case FIXO -> "(%s) %s-%s".formatted(digitos.substring(0, 2), digitos.substring(2, 6), digitos.substring(6));
            case SERVICO_0800 -> "%s %s %s".formatted(digitos.substring(0, 4), digitos.substring(4, 7), digitos.substring(7));
            case CAPITAIS -> "%s-%s".formatted(digitos.substring(0, 4), digitos.substring(4));
        };
    }

    private static TipoLinha tipoOuNulo(String d) {
        if (d.length() == 11 && d.matches("0[3589]00\\d{7}")) {
            return TipoLinha.SERVICO_0800;
        }
        if (d.length() == 8 && d.matches("[34]00\\d{5}")) {
            return TipoLinha.CAPITAIS;
        }
        if (d.length() == 11 && d.matches("[1-9][1-9]9\\d{8}")) {
            return TipoLinha.CELULAR;
        }
        if (d.length() == 10 && d.matches("[1-9][1-9][2-5]\\d{7}")) {
            return TipoLinha.FIXO;
        }
        return null;
    }

    private static NegocioException invalido(String mensagem) {
        return new NegocioException(HttpStatus.BAD_REQUEST, mensagem);
    }
}
