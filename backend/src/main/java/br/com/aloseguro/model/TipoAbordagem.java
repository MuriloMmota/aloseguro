package br.com.aloseguro.model;

import java.util.Set;

/**
 * O que o golpista pediu durante a ligação.
 */
public enum TipoAbordagem {
    PEDIU_SENHA("Pediu senha ou token"),
    PEDIU_CODIGO("Pediu código recebido por SMS"),
    PEDIU_TRANSFERENCIA("Pediu Pix ou transferência"),
    PEDIU_APLICATIVO("Pediu para instalar um aplicativo"),
    PEDIU_CARTAO("Pediu para entregar o cartão"),
    OUTRO("Outro");

    /** Abordagens que nenhum banco faz por telefone: pesam mais no score. */
    public static final Set<TipoAbordagem> CRITICAS =
            Set.of(PEDIU_SENHA, PEDIU_CODIGO, PEDIU_TRANSFERENCIA, PEDIU_APLICATIVO, PEDIU_CARTAO);

    private final String descricao;

    TipoAbordagem(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
