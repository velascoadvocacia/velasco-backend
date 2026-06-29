package com.example.lawyer.mapper

import com.example.lawyer.domain.model.Endereco
import com.example.lawyer.domain.model.Pessoa
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
        nome = request.nome!!.trim(),
        cpf = DocumentValidator.onlyDigits(request.cpf),
        cnpj = DocumentValidator.onlyDigits(request.cnpj),
        email = request.email!!.trim().lowercase(),
        telefone = request.telefone?.trim(),
        nacionalidade = request.nacionalidade?.trim(),
        estadoCivil = request.estadoCivil,
        rg = DocumentValidator.onlyDigits(request.rg),
        orgaoEmissorRg = request.orgaoEmissorRg?.trim()?.uppercase(),
        pis = DocumentValidator.onlyDigits(request.pis),
        nomeMae = request.nomeMae?.trim(),
        profissao = request.profissao?.trim(),
        razaoSocial = request.razaoSocial?.trim(),
        nomeFantasia = request.nomeFantasia?.trim(),
        inscricaoEstadual = request.inscricaoEstadual?.trim(),
        tipoPessoa = request.tipoPessoa!!,
        dataNascimento = request.dataNascimento,
        endereco = toEndereco(request.endereco),
        observacoes = request.observacoes?.trim(),
        ativo = request.ativo
    )

    fun updateEntity(target: Pessoa, request: PessoaRequestDTO) {
        target.nome = request.nome!!.trim()
        target.cpf = DocumentValidator.onlyDigits(request.cpf)
        target.cnpj = DocumentValidator.onlyDigits(request.cnpj)
        target.email = request.email!!.trim().lowercase()
        target.telefone = request.telefone?.trim()
        target.nacionalidade = request.nacionalidade?.trim()
        target.estadoCivil = request.estadoCivil
        target.rg = DocumentValidator.onlyDigits(request.rg)
        target.orgaoEmissorRg = request.orgaoEmissorRg?.trim()?.uppercase()
        target.pis = DocumentValidator.onlyDigits(request.pis)
        target.nomeMae = request.nomeMae?.trim()
        target.profissao = request.profissao?.trim()
        target.razaoSocial = request.razaoSocial?.trim()
        target.nomeFantasia = request.nomeFantasia?.trim()
        target.inscricaoEstadual = request.inscricaoEstadual?.trim()
        target.tipoPessoa = request.tipoPessoa!!
        target.dataNascimento = request.dataNascimento
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
}
