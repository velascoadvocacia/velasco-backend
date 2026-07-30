package com.example.lawyer.mapper

import com.example.lawyer.domain.model.Endereco
import com.example.lawyer.domain.model.Pessoa
import com.example.lawyer.domain.enums.TipoPessoa
import com.example.lawyer.dto.request.EnderecoRequest
import com.example.lawyer.dto.request.PessoaRequestDTO
import com.example.lawyer.dto.response.EnderecoResponse
import com.example.lawyer.dto.response.PessoaResponseDTO
import com.example.lawyer.dto.response.PessoaResumoResponse
import com.example.lawyer.service.DocumentValidator
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class PessoaMapper {
    fun toEntity(request: PessoaRequestDTO): Pessoa = Pessoa(
        nome = resolveNome(request),
        cpf = cpfFor(request),
        cnpj = cnpjFor(request),
        email = request.email?.trim()?.lowercase() ?: "",
        telefone = request.telefone?.trim(),
        nacionalidade = request.nacionalidade?.trim(),
        estadoCivil = fisicaValue(request) { request.estadoCivil },
        rg = fisicaValue(request) { DocumentValidator.onlyDigits(request.rg) },
        orgaoEmissorRg = fisicaValue(request) { request.orgaoEmissorRg?.trim()?.uppercase() },
        pis = fisicaValue(request) { DocumentValidator.onlyDigits(request.pis) },
        nomeMae = fisicaValue(request) { request.nomeMae?.trim() },
        profissao = request.profissao?.trim(),
        razaoSocial = juridicaValue(request) { request.razaoSocial?.trim() },
        nomeFantasia = juridicaValue(request) { request.nomeFantasia?.trim() },
        inscricaoEstadual = juridicaValue(request) { request.inscricaoEstadual?.trim() },
        tipoPessoa = request.tipoPessoa ?: TipoPessoa.FISICA,
        dataNascimento = fisicaValue(request) { request.dataNascimento },
        endereco = toEndereco(request.endereco),
        observacoes = request.observacoes?.trim(),
        ativo = request.ativo
    )

    fun updateEntity(target: Pessoa, request: PessoaRequestDTO) {
        target.nome = resolveNome(request)
        target.cpf = cpfFor(request)
        target.cnpj = cnpjFor(request)
        target.email = request.email?.trim()?.lowercase() ?: ""
        target.telefone = request.telefone?.trim()
        target.nacionalidade = request.nacionalidade?.trim()
        target.estadoCivil = fisicaValue(request) { request.estadoCivil }
        target.rg = fisicaValue(request) { DocumentValidator.onlyDigits(request.rg) }
        target.orgaoEmissorRg = fisicaValue(request) { request.orgaoEmissorRg?.trim()?.uppercase() }
        target.pis = fisicaValue(request) { DocumentValidator.onlyDigits(request.pis) }
        target.nomeMae = fisicaValue(request) { request.nomeMae?.trim() }
        target.profissao = request.profissao?.trim()
        target.razaoSocial = juridicaValue(request) { request.razaoSocial?.trim() }
        target.nomeFantasia = juridicaValue(request) { request.nomeFantasia?.trim() }
        target.inscricaoEstadual = juridicaValue(request) { request.inscricaoEstadual?.trim() }
        target.tipoPessoa = request.tipoPessoa ?: TipoPessoa.FISICA
        target.dataNascimento = fisicaValue(request) { request.dataNascimento }
        target.endereco = toEndereco(request.endereco)
        target.observacoes = request.observacoes?.trim()
        target.ativo = request.ativo
    }

    fun toResponse(entity: Pessoa): PessoaResponseDTO = PessoaResponseDTO(
        id = entity.id!!,
        nome = entity.nome,
        cpf = entity.cpf,
        cnpj = entity.cnpj,
        email = entity.email,
        telefone = entity.telefone,
        nacionalidade = entity.nacionalidade,
        estadoCivil = entity.estadoCivil,
        rg = entity.rg,
        orgaoEmissorRg = entity.orgaoEmissorRg,
        pis = entity.pis,
        nomeMae = entity.nomeMae,
        profissao = entity.profissao,
        razaoSocial = entity.razaoSocial,
        nomeFantasia = entity.nomeFantasia,
        inscricaoEstadual = entity.inscricaoEstadual,
        tipoPessoa = entity.tipoPessoa,
        dataNascimento = entity.dataNascimento,
        endereco = entity.endereco?.let(::toEnderecoResponse),
        observacoes = entity.observacoes,
        ativo = entity.ativo,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt
    )

    fun toResumoResponse(entity: Pessoa): PessoaResumoResponse =
        PessoaResumoResponse(entity.id!!, entity.nome, entity.tipoPessoa)

    private fun toEndereco(request: EnderecoRequest?): Endereco? = request?.let {
        Endereco(
            rua = it.rua?.trim(),
            numero = it.numero?.trim(),
            complemento = it.complemento?.trim(),
            bairro = it.bairro?.trim(),
            cidade = it.cidade?.trim(),
            estado = it.estado?.trim()?.uppercase(),
            cep = DocumentValidator.onlyDigits(it.cep)
        )
    }

    private fun toEnderecoResponse(entity: Endereco): EnderecoResponse =
        EnderecoResponse(entity.rua, entity.numero, entity.complemento, entity.bairro, entity.cidade, entity.estado, entity.cep)

    private fun resolveNome(request: PessoaRequestDTO): String {
        val nome = request.nome?.trim()?.takeIf { it.isNotEmpty() }
        if (request.tipoPessoa == TipoPessoa.JURIDICA) {
            return nome
                ?: request.nomeFantasia?.trim()?.takeIf { it.isNotEmpty() }
                ?: request.razaoSocial?.trim()?.takeIf { it.isNotEmpty() }
                ?: ""
        }
        return nome ?: ""
    }

    private fun cpfFor(request: PessoaRequestDTO): String? =
        if (request.tipoPessoa == TipoPessoa.JURIDICA) null else DocumentValidator.onlyDigits(request.cpf)

    private fun cnpjFor(request: PessoaRequestDTO): String? =
        if (request.tipoPessoa == TipoPessoa.FISICA) null else DocumentValidator.onlyDigits(request.cnpj)

    private fun <T> fisicaValue(request: PessoaRequestDTO, value: () -> T): T? =
        if (request.tipoPessoa == TipoPessoa.JURIDICA) null else value()

    private fun <T> juridicaValue(request: PessoaRequestDTO, value: () -> T): T? =
        if (request.tipoPessoa == TipoPessoa.FISICA) null else value()
}
