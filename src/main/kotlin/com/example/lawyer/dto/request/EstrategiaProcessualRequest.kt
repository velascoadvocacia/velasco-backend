package com.example.lawyer.dto.request

import com.example.lawyer.domain.enums.TipoResponsabilidade
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.Size
import java.math.BigDecimal

data class EstrategiaProcessualRequest(
    @field:Size(max = 5000)
    val fundamentosFaticos: String? = null,

    @field:Size(max = 5000)
    val pedidosPrincipais: String? = null,

    @field:Size(max = 5000)
    val observacoesInternas: String? = null,

    val responsabilidadePretendida: TipoResponsabilidade? = null,

    val possuiGrupoEconomico: Boolean = false,

    val possuiAcidenteTrabalho: Boolean = false,

    val possuiDoencaOcupacional: Boolean = false,

    val requerEmissaoCat: Boolean = false,

    @field:Digits(integer = 10, fraction = 2)
    val valorCausa: BigDecimal? = null
)
