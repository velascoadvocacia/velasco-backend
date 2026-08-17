package com.example.lawyer.dto.request

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank

data class RtExportRequest(
    @field:Valid
    val blocks: List<RtExportBlockRequest> = emptyList(),

    @field:NotBlank
    val claimantName: String,

    val processoId: Long? = null,
    val reclamantesIds: List<Long> = emptyList(),
    val reclamadasIds: List<Long> = emptyList(),
    val advogadosIds: List<Long> = emptyList(),
    val blocosSelecionados: List<String> = emptyList(),
    val dadosVariaveis: Map<String, Any?> = emptyMap()
)

data class RtExportBlockRequest(
    val id: String? = null,

    @field:NotBlank
    val title: String,

    @field:NotBlank
    val content: String,

    val anexos: List<RtExportImageRequest> = emptyList(),

    val imagensFixas: List<RtExportInlineImageRequest> = emptyList(),

    val paragrafosAlinhadosDireita: List<Int> = emptyList(),

    val paragrafosRecuados: List<Int> = emptyList()
)

data class RtExportImageRequest(
    val bytes: ByteArray? = null,
    val contentType: String,
    val nomeOriginal: String,
    val url: String? = null,
    val grupo: String = "geral",
    val afterParagraph: Int = 1
)

data class RtExportInlineImageRequest(
    val bytes: ByteArray,
    val contentType: String,
    val nomeOriginal: String,
    val afterParagraph: Int,
    val originalWidthPx: Int,
    val originalHeightPx: Int,
    val caption: String? = null
)
