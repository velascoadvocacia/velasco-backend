package com.example.lawyer.dto.response

data class EnderecoResponse(
    val rua: String?,
    val numero: String?,
    val complemento: String?,
    val bairro: String?,
    val cidade: String?,
    val estado: String?,
    val cep: String?
)
