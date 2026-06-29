package com.example.lawyer.dto.request

import com.example.lawyer.domain.enums.StatusProcesso
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.LocalDate

data class ProcessoUpdateRequest(
    @field:NotBlank
    @field:Size(max = 40)
    val numeroProcesso: String?,

    @field:NotBlank
    @field:Size(max = 100000)
    val descricao: String?,

    @field:NotNull
    val clienteId: Long?,

    @field:NotNull
    val advogadoId: Long?,

    @field:NotNull
    val dataAbertura: LocalDate?,

    @field:Valid
    val contratoTrabalho: ContratoTrabalhoRequest? = null,

    @field:Valid
    val estrategiaProcessual: EstrategiaProcessualRequest? = null,

    val reclamadasIds: List<Long> = emptyList(),

    val sociosResponsaveisIds: List<Long> = emptyList(),

    @field:NotNull
    val status: StatusProcesso?,

    val ativo: Boolean = true,

    val rtDescricaoAcidente: String? = null,
    val rtCctPeriodo: String? = null,
    val rtClausulaConvencional: String? = null,
    val rtAssuntoClausula: String? = null,
    val rtRedacaoClausula: String? = null,
    val rtSalarioFuncaoOriginal: String? = null,
    val rtSalarioFuncaoAcumulada: String? = null,
    val rtValorPagoPorFora: String? = null,
    val rtMediaHorasExtras: String? = null
)