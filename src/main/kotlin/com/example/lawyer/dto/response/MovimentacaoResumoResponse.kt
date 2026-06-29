package com.example.lawyer.dto.response

import java.time.OffsetDateTime

data class MovimentacaoResumoResponse(
    val id: Long,
    val descricao: String,
    val dataMovimentacao: OffsetDateTime
)
