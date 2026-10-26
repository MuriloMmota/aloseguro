package br.com.aloseguro.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Número de telefone que recebeu pelo menos uma denúncia.
 * O número é guardado só com dígitos (ex.: 11900000001).
 */
@Entity
@Table(name = "numeros_suspeitos")
public class NumeroSuspeito {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 15)
    private String numero;

    @Column(length = 2)
    private String ddd;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_linha", nullable = false, length = 20)
    private TipoLinha tipoLinha;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected NumeroSuspeito() {
    }

    public NumeroSuspeito(String numero, String ddd, TipoLinha tipoLinha) {
        this.numero = numero;
        this.ddd = ddd;
        this.tipoLinha = tipoLinha;
    }

    @PrePersist
    void aoCriar() {
        if (criadoEm == null) {
            criadoEm = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getNumero() {
        return numero;
    }

    public String getDdd() {
        return ddd;
    }

    public TipoLinha getTipoLinha() {
        return tipoLinha;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
