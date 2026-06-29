package com.example.lawyer.dto.response

import com.example.lawyer.domain.enums.TipoRescisao
import java.math.BigDecimal
import java.time.LocalDate

data class ContratoTrabalhoResponse(
    val funcaoExercida: String?,
    val dataAdmissao: LocalDate?,
    val dataDemissao: LocalDate?,
    val tipoRescisao: TipoRescisao?,
    val ultimaRemuneracao: BigDecimal?,
    val avisoPrevioProjetadoEm: LocalDate?,
    val jornadaDescricao: String?,
    val localPrestacaoServico: String?
)
