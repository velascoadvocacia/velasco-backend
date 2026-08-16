package com.example.lawyer.dto.response

import java.time.OffsetDateTime

data class ProcessoAnexoResponse(
    val id: Long,
    val processoId: Long,
    val blocoId: String,
    val grupo: String,
    val ordem: Int,
    val afterParagraph: Int,
    val nomeOriginal: String,
    val contentType: String,
    val tamanhoBytes: Long,
    val url: String,
    val dataUpload: OffsetDateTime
)
