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
    val imagensFixas: List<RtPreviewInlineImageResponse> = emptyList(),
    val paragrafosAlinhadosDireita: List<Int> = emptyList(),
    val paragrafosRecuados: List<Int> = emptyList(),
    val tabela: RtPreviewTableResponse? = null
)

data class RtPreviewTableResponse(
    val cabecalhos: List<String>,
    val linhas: List<List<RtPreviewTableCellResponse>>
)

data class RtPreviewTableCellResponse(
    val texto: String,
    val italico: Boolean = false
)

data class RtPreviewInlineImageResponse(
    val url: String,
    val contentType: String,
    val nomeOriginal: String,
    val afterParagraph: Int,
    val caption: String? = null
)
