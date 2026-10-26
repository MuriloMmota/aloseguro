package br.com.aloseguro.util;

import br.com.aloseguro.exception.NegocioException;
import br.com.aloseguro.model.TipoLinha;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NumeroTelefoneTest {

    @Test
    @DisplayName("Remove formatação e o +55")
    void normalizaFormatos() {
        assertEquals("11900000001", NumeroTelefone.normalizar("+55 (11) 90000-0001"));
        assertEquals("11900000001", NumeroTelefone.normalizar("11 90000 0001"));
        assertEquals("08007001000", NumeroTelefone.normalizar("0800 700 1000"));
        assertEquals("40041000", NumeroTelefone.normalizar("4004-1000"));
    }

    @Test
    @DisplayName("Identifica o tipo de linha")
    void identificaTipo() {
        assertEquals(TipoLinha.CELULAR, NumeroTelefone.tipo("11900000001"));
        assertEquals(TipoLinha.FIXO, NumeroTelefone.tipo("1130000003"));
        assertEquals(TipoLinha.SERVICO_0800, NumeroTelefone.tipo("08007001000"));
        assertEquals(TipoLinha.CAPITAIS, NumeroTelefone.tipo("40041000"));
    }

    @Test
    @DisplayName("Formata para exibição")
    void formata() {
        assertEquals("(11) 90000-0001", NumeroTelefone.formatar("11900000001"));
        assertEquals("(11) 3000-0003", NumeroTelefone.formatar("1130000003"));
        assertEquals("0800 700 1000", NumeroTelefone.formatar("08007001000"));
        assertEquals("4004-1000", NumeroTelefone.formatar("40041000"));
    }

    @Test
    @DisplayName("DDD só existe para celular e fixo")
    void ddd() {
        assertEquals("21", NumeroTelefone.ddd("21900000002"));
        assertNull(NumeroTelefone.ddd("08007001000"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "123", "abc", "1190000000", "01900000001", "119000000011"})
    @DisplayName("Rejeita números inválidos")
    void rejeitaInvalidos(String entrada) {
        assertThrows(NegocioException.class, () -> NumeroTelefone.normalizar(entrada));
    }
}
