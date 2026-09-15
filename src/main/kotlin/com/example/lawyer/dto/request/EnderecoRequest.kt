package com.example.lawyer.dto.request

import jakarta.validation.constraints.Size
import jakarta.validation.constraints.Pattern

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

    @field:Pattern(regexp = "\\s*|.{2}", message = "Quando preenchida, informe a UF com 2 letras (ex.: SP)")
    val estado: String? = null,

    @field:Pattern(regexp = "\\s*|[0-9]{8}", message = "Quando preenchido, informe o CEP com 8 digitos, sem pontuacao")
    val cep: String? = null
)
