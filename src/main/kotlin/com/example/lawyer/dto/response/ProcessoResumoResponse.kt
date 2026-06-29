package com.example.lawyer.dto.response

import com.example.lawyer.domain.enums.StatusProcesso

data class ProcessoResumoResponse(
    val id: Long,
    val numeroProcesso: String,
    val status: StatusProcesso
)
