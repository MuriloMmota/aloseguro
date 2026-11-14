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
import java.util.Set;

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

    /** Tópicos identificados no relato, separados por vírgula (ex.: "PEDIU_PIX,URGENCIA"). */
    @Column(length = 300)
    private String tags;

    /** Quem gerou as tags: IA, REGRAS ou SEED (dados fictícios). */
    @Column(name = "tags_origem", length = 10)
    private String tagsOrigem;

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

    /** Edição feita pelo próprio autor da denúncia (o número denunciado não muda). */
    public void atualizar(String bancoAlegado, TipoAbordagem tipoAbordagem, String descricao,
                          LocalDateTime dataHoraLigacao) {
        this.bancoAlegado = bancoAlegado;
        this.tipoAbordagem = tipoAbordagem;
        this.descricao = descricao;
        this.dataHoraLigacao = dataHoraLigacao;
    }

    public void definirTags(Set<TagRelato> tags, String origem) {
        this.tags = TagRelato.paraTexto(tags);
        this.tagsOrigem = origem;
    }

    /** Usado apenas pelos dados de demonstração e pelos testes, para simular denúncias antigas. */
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

    public Set<TagRelato> getTags() {
        return TagRelato.deTexto(tags);
    }

    public String getTagsOrigem() {
        return tagsOrigem;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
