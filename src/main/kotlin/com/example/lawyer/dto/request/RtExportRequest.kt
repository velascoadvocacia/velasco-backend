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
    val dadosVariaveis: Map<String, String?> = emptyMap()
)

data class RtExportBlockRequest(
    @field:NotBlank
    val title: String,

    @field:NotBlank
    val content: String,

    val anexos: List<RtExportImageRequest> = emptyList()
)

data class RtExportImageRequest(
    val url: String,
    val contentType: String,
    val nomeOriginal: String
)
