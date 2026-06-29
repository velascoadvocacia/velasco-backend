package com.example.lawyer.domain.model

import com.example.lawyer.domain.enums.StatusProcesso
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Embedded
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToOne
import jakarta.persistence.ManyToMany
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(name = "processos")
open class Processo(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null,

    @Column(nullable = false, unique = true, length = 40)
    open var numeroProcesso: String = "",

    @Column(nullable = false, columnDefinition = "TEXT")
    open var descricao: String = "",

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    open var cliente: Pessoa? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "advogado_id", nullable = false)
    open var advogado: Pessoa? = null,

    @Column(nullable = false)
    open var dataAbertura: LocalDate = LocalDate.now(),

    @Embedded
    open var contratoTrabalho: ContratoTrabalho? = null,

    @Embedded
    open var estrategiaProcessual: EstrategiaProcessual? = null,

    @Column(name = "rt_descricao_acidente", columnDefinition = "TEXT")
    open var rtDescricaoAcidente: String? = null,

    @Column(name = "rt_cct_periodo", length = 100)
    open var rtCctPeriodo: String? = null,

    @Column(name = "rt_clausula_convencional", length = 100)
    open var rtClausulaConvencional: String? = null,

    @Column(name = "rt_assunto_clausula", length = 255)
    open var rtAssuntoClausula: String? = null,

    @Column(name = "rt_redacao_clausula", columnDefinition = "TEXT")
    open var rtRedacaoClausula: String? = null,

    @Column(name = "rt_salario_funcao_original", length = 100)
    open var rtSalarioFuncaoOriginal: String? = null,

    @Column(name = "rt_salario_funcao_acumulada", length = 100)
    open var rtSalarioFuncaoAcumulada: String? = null,

    @Column(name = "rt_valor_pago_por_fora", length = 100)
    open var rtValorPagoPorFora: String? = null,

    @Column(name = "rt_media_horas_extras", length = 100)
    open var rtMediaHorasExtras: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    open var status: StatusProcesso = StatusProcesso.ABERTO,

    @Column(nullable = false)
    open var ativo: Boolean = true,

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "processos_reclamadas",
        joinColumns = [JoinColumn(name = "processo_id")],
        inverseJoinColumns = [JoinColumn(name = "pessoa_id")]
    )
    open var reclamadas: MutableSet<Pessoa> = linkedSetOf(),

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "processos_socios_responsaveis",
        joinColumns = [JoinColumn(name = "processo_id")],
        inverseJoinColumns = [JoinColumn(name = "pessoa_id")]
    )
    open var sociosResponsaveis: MutableSet<Pessoa> = linkedSetOf(),

    @OneToMany(mappedBy = "processo", cascade = [CascadeType.ALL], orphanRemoval = true)
    open var movimentacoes: MutableList<Movimentacao> = mutableListOf()
) : AuditableEntity()