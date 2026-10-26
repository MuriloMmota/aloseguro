package br.com.aloseguro.model;

public enum TipoLinha {
    CELULAR("Celular"),
    FIXO("Telefone fixo"),
    SERVICO_0800("Número de serviço (0800 / 0300)"),
    CAPITAIS("Número de capitais (4004 / 3003)");

    private final String descricao;

    TipoLinha(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
