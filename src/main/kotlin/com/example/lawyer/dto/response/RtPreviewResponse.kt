package com.example.lawyer.dto.response

data class RtPreviewResponse(
    val processoId: Long?,
    val blocos: List<RtPreviewBlockResponse>
)

data class RtPreviewBlockResponse(
    val id: String,
    val titulo: String,
    val texto: String,
    val anexos: List<ProcessoAnexoResponse> = emptyList(),
    val imagensFixas: List<RtPreviewInlineImageResponse> = emptyList()
)

data class RtPreviewInlineImageResponse(
    val url: String,
    val contentType: String,
    val nomeOriginal: String,
    val afterParagraph: Int
)
