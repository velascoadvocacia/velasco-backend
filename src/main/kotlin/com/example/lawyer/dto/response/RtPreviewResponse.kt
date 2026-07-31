package com.example.lawyer.dto.response

data class RtPreviewResponse(
    val processoId: Long,
    val blocos: List<RtPreviewBlockResponse>
)

data class RtPreviewBlockResponse(
    val blocoId: String,
    val titulo: String,
    val texto: String
)
