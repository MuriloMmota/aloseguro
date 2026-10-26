package br.com.aloseguro.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Número oficial de atendimento de um banco.
 * Quando alguém consulta um destes números, o sistema não responde "seguro":
 * responde com o alerta de spoofing (número verdadeiro, mas possivelmente falsificado).
 */
@Entity
@Table(name = "centrais_oficiais")
public class CentralOficial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String banco;

    @Column(nullable = false, unique = true, length = 15)
    private String numero;

    @Column(length = 150)
    private String descricao;

    protected CentralOficial() {
    }

    public CentralOficial(String banco, String numero, String descricao) {
        this.banco = banco;
        this.numero = numero;
        this.descricao = descricao;
    }

    public Long getId() {
        return id;
    }

    public String getBanco() {
        return banco;
    }

    public String getNumero() {
        return numero;
    }

    public String getDescricao() {
        return descricao;
    }
}
