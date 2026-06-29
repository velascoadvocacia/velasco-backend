package com.example.lawyer.dto.response

import java.time.OffsetDateTime

data class MovimentacaoResponse(
    val id: Long,
    val processo: ProcessoResumoResponse,
    val descricao: String,
    val dataMovimentacao: OffsetDateTime
)
