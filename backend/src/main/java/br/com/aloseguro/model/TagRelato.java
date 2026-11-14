package br.com.aloseguro.model;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tópicos que a IA (ou o classificador por regras) identifica no relato de uma denúncia.
 * A gravidade (0 a 1) entra no fator "Gravidade dos relatos" do motor de risco.
 */
public enum TagRelato {
    PEDIU_SENHA("Pediu senha ou token", 1.0),
    PEDIU_CODIGO("Pediu código de SMS ou WhatsApp", 1.0),
    PEDIU_PIX("Pediu Pix ou transferência", 1.0),
    ACESSO_REMOTO("Pediu para instalar aplicativo ou acesso remoto", 1.0),
    RETIRADA_CARTAO("Motoboy ou retirada do cartão", 1.0),
    DADOS_PESSOAIS("Pediu dados pessoais (CPF, nascimento)", 0.6),
    FALSO_SETOR("Transferiu para falso setor do banco", 0.5),
    URGENCIA("Pressão, urgência ou ameaça", 0.5),
    COMPRA_SUSPEITA("Alegou compra suspeita ou conta invadida", 0.4),
    OUTRO("Outro", 0.2);

    private final String descricao;
    private final double gravidade;

    TagRelato(String descricao, double gravidade) {
        this.descricao = descricao;
        this.gravidade = gravidade;
    }

    public String getDescricao() {
        return descricao;
    }

    public double getGravidade() {
        return gravidade;
    }

    /** Converte "PEDIU_SENHA,URGENCIA" em conjunto, ignorando valores desconhecidos. */
    public static Set<TagRelato> deTexto(String texto) {
        Set<TagRelato> tags = EnumSet.noneOf(TagRelato.class);
        if (texto == null || texto.isBlank()) {
            return tags;
        }
        for (String parte : texto.split(",")) {
            deCodigo(parte).ifPresent(tags::add);
        }
        return tags;
    }

    public static java.util.Optional<TagRelato> deCodigo(String codigo) {
        if (codigo == null) {
            return java.util.Optional.empty();
        }
        String limpo = codigo.trim().toUpperCase();
        return Arrays.stream(values()).filter(t -> t.name().equals(limpo)).findFirst();
    }

    public static String paraTexto(Set<TagRelato> tags) {
        return tags.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
    }

    /** Tag equivalente à opção que o usuário marcou no formulário. */
    public static TagRelato daAbordagem(TipoAbordagem tipo) {
        if (tipo == null) {
            return null;
        }
        return switch (tipo) {
            case PEDIU_SENHA -> PEDIU_SENHA;
            case PEDIU_CODIGO -> PEDIU_CODIGO;
            case PEDIU_TRANSFERENCIA -> PEDIU_PIX;
            case PEDIU_APLICATIVO -> ACESSO_REMOTO;
            case PEDIU_CARTAO -> RETIRADA_CARTAO;
            case OUTRO -> null;
        };
    }
}
