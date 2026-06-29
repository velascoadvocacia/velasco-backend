package com.example.lawyer.dto.response

import com.example.lawyer.domain.enums.EstadoCivil
import com.example.lawyer.domain.enums.TipoPessoa
import java.time.LocalDate
import java.time.OffsetDateTime

data class PessoaResponseDTO(
    val id: Long,
    val nome: String,
    val cpf: String?,
    val cnpj: String?,
    val email: String,
    val telefone: String?,
    val nacionalidade: String?,
    val estadoCivil: EstadoCivil?,
    val rg: String?,
    val orgaoEmissorRg: String?,
    val pis: String?,
    val nomeMae: String?,
    val profissao: String?,
    val razaoSocial: String?,
    val nomeFantasia: String?,
    val inscricaoEstadual: String?,
    val tipoPessoa: TipoPessoa,
    val dataNascimento: LocalDate?,
    val endereco: EnderecoResponse?,
    val observacoes: String?,
    val ativo: Boolean,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime
)
