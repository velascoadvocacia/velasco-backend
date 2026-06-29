package com.example.lawyer.service

import com.example.lawyer.domain.enums.TipoPessoa
import com.example.lawyer.domain.model.Pessoa
import com.example.lawyer.dto.common.PageResponse
import com.example.lawyer.dto.request.PessoaRequestDTO
import com.example.lawyer.dto.response.PessoaResponseDTO
import com.example.lawyer.exception.BusinessException
import com.example.lawyer.exception.ResourceNotFoundException
import com.example.lawyer.mapper.PessoaMapper
import com.example.lawyer.repository.PessoaRepository
import io.quarkus.panache.common.Page
import io.quarkus.panache.common.Sort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.validation.ValidationException
import jakarta.transaction.Transactional
import org.jboss.logging.Logger

@ApplicationScoped
class PessoaService(
    private val repository: PessoaRepository,
    private val pessoaMapper: PessoaMapper
) {
    private val logger = Logger.getLogger(PessoaService::class.java)

    @Transactional
    fun create(request: PessoaRequestDTO): PessoaResponseDTO {
        validatePessoa(request, null)
        val pessoa = pessoaMapper.toEntity(request)
        repository.persist(pessoa)
        logger.infof("Pessoa criada id=%s", pessoa.id)
        return pessoaMapper.toResponse(pessoa)
    }

    fun list(nome: String?, cpf: String?, tipoPessoa: TipoPessoa?, page: Int, size: Int): PageResponse<PessoaResponseDTO> {
        val params = mutableMapOf<String, Any>()
        val where = mutableListOf("ativo = true")
        nome?.takeIf { it.isNotBlank() }?.let {
            where += "lower(nome) like :nome"
            params["nome"] = "%${it.lowercase()}%"
        }
        DocumentValidator.onlyDigits(cpf)?.takeIf { it.isNotBlank() }?.let {
            where += "cpf = :cpf"
            params["cpf"] = it
        }
        tipoPessoa?.let {
            where += "tipoPessoa = :tipoPessoa"
            params["tipoPessoa"] = it
        }
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, 100)
        val query = repository.find(where.joinToString(" and "), Sort.by("nome").ascending(), params).page(Page.of(safePage, safeSize))
        return PageResponse(query.list().map(pessoaMapper::toResponse), safePage, safeSize, query.count(), query.pageCount())
    }

    fun getById(id: Long): PessoaResponseDTO = pessoaMapper.toResponse(findEntity(id))

    fun findEntity(id: Long): Pessoa =
        repository.findActiveById(id) ?: throw ResourceNotFoundException("Pessoa $id nao encontrada")

    @Transactional
    fun update(id: Long, request: PessoaRequestDTO): PessoaResponseDTO {
        val pessoa = findEntity(id)
        validatePessoa(request, id)
        pessoaMapper.updateEntity(pessoa, request)
        logger.infof("Pessoa atualizada id=%s", pessoa.id)
        return pessoaMapper.toResponse(pessoa)
    }

    @Transactional
    fun delete(id: Long) {
        val pessoa = findEntity(id)
        pessoa.ativo = false
        logger.infof("Pessoa inativada id=%s", id)
    }

    private fun validatePessoa(request: PessoaRequestDTO, id: Long?) {
        val cleanCpf = DocumentValidator.onlyDigits(request.cpf)
        val cleanCnpj = DocumentValidator.onlyDigits(request.cnpj)
        if (request.tipoPessoa == TipoPessoa.FISICA) {
            if (!DocumentValidator.isValidCpf(cleanCpf)) throw ValidationException("CPF invalido")
            if (repository.existsCpfForAnotherPessoa(cleanCpf!!, id)) throw BusinessException("CPF ja cadastrado")
            if (cleanCnpj != null) throw ValidationException("Pessoa fisica nao pode possuir CNPJ")
        }
        if (request.tipoPessoa == TipoPessoa.JURIDICA) {
            if (!DocumentValidator.isValidCnpj(cleanCnpj)) throw ValidationException("CNPJ invalido")
            if (repository.existsCnpjForAnotherPessoa(cleanCnpj!!, id)) throw BusinessException("CNPJ ja cadastrado")
            if (cleanCpf != null) throw ValidationException("Pessoa juridica nao pode possuir CPF")
        }
        val email = request.email!!.trim().lowercase()
        if (repository.existsEmailForAnotherPessoa(email, id)) throw BusinessException("Email ja cadastrado")
    }
}
