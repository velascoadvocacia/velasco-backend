package com.example.lawyer.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.OffsetDateTime

data class MovimentacaoCreateRequest(
    @field:NotNull
    val processoId: Long?,

    @field:NotBlank
    @field:Size(max = 100000)
    val descricao: String?,

    val dataMovimentacao: OffsetDateTime? = null
)
