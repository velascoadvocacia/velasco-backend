package com.example.lawyer.domain.model

import com.example.lawyer.domain.enums.TipoRescisao
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import java.math.BigDecimal
import java.time.LocalDate

@Embeddable
open class ContratoTrabalho(
    @Column(name = "contrato_funcao_exercida", length = 150)
    open var funcaoExercida: String? = null,

    @Column(name = "contrato_data_admissao")
    open var dataAdmissao: LocalDate? = null,

    @Column(name = "contrato_data_demissao")
    open var dataDemissao: LocalDate? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "contrato_tipo_rescisao", length = 30)
    open var tipoRescisao: TipoRescisao? = null,

    @Column(name = "contrato_ultima_remuneracao", precision = 12, scale = 2)
    open var ultimaRemuneracao: BigDecimal? = null,

    @Column(name = "contrato_aviso_previo_projetado_em")
    open var avisoPrevioProjetadoEm: LocalDate? = null,

    @Column(name = "contrato_jornada_descricao", length = 2000)
    open var jornadaDescricao: String? = null,

    @Column(name = "contrato_local_prestacao_servico", length = 255)
    open var localPrestacaoServico: String? = null
)
