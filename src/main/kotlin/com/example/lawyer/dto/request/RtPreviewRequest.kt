package com.example.lawyer.dto.request

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull

data class RtPreviewRequest(
    @field:NotNull
    val processoId: Long?,

    @field:NotEmpty
    val blocosSelecionados: List<String>,

    val advogadosIds: List<Long> = emptyList()
)
