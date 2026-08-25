package com.example.lawyer.dto.request

data class FichaEntrevistaExportRequest(
    val processoId: Long? = null,
    val reclamantesIds: List<Long> = emptyList(),
    val reclamadasIds: List<Long> = emptyList(),
    val dadosVariaveis: Map<String, Map<String, String?>> = emptyMap()
)
