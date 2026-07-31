package com.example.lawyer.dto.request

import jakarta.validation.constraints.NotEmpty

data class RtPreviewRequest(
    val processoId: Long? = null,

    val reclamantesIds: List<Long> = emptyList(),

    val reclamadasIds: List<Long> = emptyList(),

    @field:NotEmpty
    val blocosSelecionados: List<String> = emptyList(),

    val advogadosIds: List<Long> = emptyList(),

    val dadosVariaveis: Map<String, String?> = emptyMap()
)
