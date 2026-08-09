package com.example.lawyer.dto.request

data class ProcuracaoExportRequest(
    val processoId: Long? = null,
    val reclamantesIds: List<Long> = emptyList(),
    val reclamadasIds: List<Long> = emptyList(),
    val advogadosIds: List<Long> = emptyList()
)
