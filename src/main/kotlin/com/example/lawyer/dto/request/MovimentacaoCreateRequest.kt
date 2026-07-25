package com.example.lawyer.dto.request

import jakarta.validation.constraints.Size
import java.time.OffsetDateTime

data class MovimentacaoCreateRequest(
    val processoId: Long?,

    @field:Size(max = 100000)
    val descricao: String?,

    val dataMovimentacao: OffsetDateTime? = null
)
