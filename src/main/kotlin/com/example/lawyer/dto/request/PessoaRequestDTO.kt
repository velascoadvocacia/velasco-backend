package com.example.lawyer.dto.request

import com.example.lawyer.domain.enums.EstadoCivil
import com.example.lawyer.domain.enums.Sexo
import com.example.lawyer.domain.enums.TipoPessoa
import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import java.time.LocalDate

data class PessoaRequestDTO(
    @field:Size(max = 150)
    val nome: String?,

    @field:Size(max = 11)
    val cpf: String? = null,

    @field:Size(max = 14)
    val cnpj: String? = null,

    @field:Size(max = 180)
    val email: String?,

    @field:Size(max = 20)
    val telefone: String? = null,

    @field:Size(max = 60)
    val nacionalidade: String? = null,

    val estadoCivil: EstadoCivil? = null,

    val sexo: Sexo? = null,

    @field:Size(max = 20)
    val rg: String? = null,

    @field:Size(max = 20)
    val orgaoEmissorRg: String? = null,

    @field:Size(max = 20)
    val pis: String? = null,

    @field:Size(max = 150)
    val nomeMae: String? = null,

    @field:Size(max = 120)
    val profissao: String? = null,

    @field:Size(max = 180)
    val razaoSocial: String? = null,

    @field:Size(max = 180)
    val nomeFantasia: String? = null,

    @field:Size(max = 30)
    val inscricaoEstadual: String? = null,

    val tipoPessoa: TipoPessoa?,

    val dataNascimento: LocalDate? = null,

    @field:Valid
    val endereco: EnderecoRequest? = null,

    @field:Size(max = 2000)
    val observacoes: String? = null,

    val ativo: Boolean = true
)
