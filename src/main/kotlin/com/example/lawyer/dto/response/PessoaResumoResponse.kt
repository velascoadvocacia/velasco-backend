package com.example.lawyer.dto.response

import com.example.lawyer.domain.enums.TipoPessoa

data class PessoaResumoResponse(
    val id: Long,
    val nome: String,
    val tipoPessoa: TipoPessoa
)
