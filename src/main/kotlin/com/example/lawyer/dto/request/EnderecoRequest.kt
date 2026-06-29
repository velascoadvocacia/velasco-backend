package com.example.lawyer.dto.request

import jakarta.validation.constraints.Size

data class EnderecoRequest(
    @field:Size(max = 150)
    val rua: String? = null,

    @field:Size(max = 20)
    val numero: String? = null,

    @field:Size(max = 100)
    val complemento: String? = null,

    @field:Size(max = 100)
    val bairro: String? = null,

    @field:Size(max = 100)
    val cidade: String? = null,

    @field:Size(min = 2, max = 2)
    val estado: String? = null,

    @field:Size(min = 8, max = 8)
    val cep: String? = null
)
