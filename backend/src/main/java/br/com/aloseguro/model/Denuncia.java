package br.com.aloseguro.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Relato de um usuário que recebeu uma ligação de golpe.
 */
@Entity
@Table(name = "denuncias")
public class Denuncia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "numero_id", nullable = false)
    private NumeroSuspeito numero;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "banco_alegado", length = 100)
    private String bancoAlegado;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_abordagem", nullable = false, length = 30)
    private TipoAbordagem tipoAbordagem;

    @Column(length = 500)
    private String descricao;

    @Column(name = "data_hora_ligacao")
    private LocalDateTime dataHoraLigacao;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected Denuncia() {
    }

    public Denuncia(NumeroSuspeito numero, Usuario usuario, String bancoAlegado,
                    TipoAbordagem tipoAbordagem, String descricao, LocalDateTime dataHoraLigacao) {
        this.numero = numero;
        this.usuario = usuario;
        this.bancoAlegado = bancoAlegado;
        this.tipoAbordagem = tipoAbordagem;
        this.descricao = descricao;
        this.dataHoraLigacao = dataHoraLigacao;
    }

    @PrePersist
    void aoCriar() {
        if (criadoEm == null) {
            criadoEm = Instant.now();
        }
    }

    /** Usado apenas pelos dados de demonstração, para simular denúncias antigas. */
    public void definirCriadoEm(Instant criadoEm) {
        this.criadoEm = criadoEm;
    }

    public Long getId() {
        return id;
    }

    public NumeroSuspeito getNumero() {
        return numero;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public String getBancoAlegado() {
        return bancoAlegado;
    }

    public TipoAbordagem getTipoAbordagem() {
        return tipoAbordagem;
    }

    public String getDescricao() {
        return descricao;
    }

    public LocalDateTime getDataHoraLigacao() {
        return dataHoraLigacao;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
