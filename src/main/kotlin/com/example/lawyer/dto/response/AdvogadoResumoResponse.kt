package com.example.lawyer.dto.response

data class AdvogadoResumoResponse(
    val id: Long,
    val nome: String,
    val ufOab: String?,
    val numeroOab: String?
)
