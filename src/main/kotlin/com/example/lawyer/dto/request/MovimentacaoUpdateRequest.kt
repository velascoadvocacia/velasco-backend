package com.example.lawyer.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.OffsetDateTime

data class MovimentacaoUpdateRequest(
    @field:NotNull
    val processoId: Long?,

    @field:NotBlank
    @field:Size(max = 100000)
    val descricao: String?,

    @field:NotNull
    val dataMovimentacao: OffsetDateTime?
)
