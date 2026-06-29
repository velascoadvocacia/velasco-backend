package com.example.lawyer.dto.request

import com.example.lawyer.domain.enums.TipoRescisao
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.LocalDate

data class ContratoTrabalhoRequest(
    @field:Size(max = 150)
    val funcaoExercida: String? = null,

    val dataAdmissao: LocalDate? = null,

    val dataDemissao: LocalDate? = null,

    val tipoRescisao: TipoRescisao? = null,

    @field:Digits(integer = 10, fraction = 2)
    val ultimaRemuneracao: BigDecimal? = null,

    val avisoPrevioProjetadoEm: LocalDate? = null,

    @field:Size(max = 2000)
    val jornadaDescricao: String? = null,

    @field:Size(max = 255)
    val localPrestacaoServico: String? = null
)
