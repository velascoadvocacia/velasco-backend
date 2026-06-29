package com.example.lawyer.dto.response

import com.example.lawyer.domain.enums.TipoResponsabilidade
import java.math.BigDecimal

data class EstrategiaProcessualResponse(
    val fundamentosFaticos: String?,
    val pedidosPrincipais: String?,
    val observacoesInternas: String?,
    val responsabilidadePretendida: TipoResponsabilidade?,
    val possuiGrupoEconomico: Boolean,
    val possuiAcidenteTrabalho: Boolean,
    val possuiDoencaOcupacional: Boolean,
    val requerEmissaoCat: Boolean,
    val valorCausa: BigDecimal?
)
