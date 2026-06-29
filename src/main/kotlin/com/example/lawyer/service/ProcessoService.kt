package com.example.lawyer.service

import com.example.lawyer.domain.model.ContratoTrabalho
import com.example.lawyer.domain.model.EstrategiaProcessual
import com.example.lawyer.domain.model.Pessoa
import com.example.lawyer.domain.enums.StatusProcesso
import com.example.lawyer.domain.model.Processo
import com.example.lawyer.dto.common.PageResponse
import com.example.lawyer.dto.request.ContratoTrabalhoRequest
import com.example.lawyer.dto.request.EstrategiaProcessualRequest
import com.example.lawyer.dto.request.ProcessoCreateRequest
import com.example.lawyer.dto.request.ProcessoUpdateRequest
import com.example.lawyer.dto.response.ProcessoDTO
import com.example.lawyer.exception.BusinessException
import com.example.lawyer.exception.ResourceNotFoundException
import com.example.lawyer.mapper.ProcessoMapper
import com.example.lawyer.repository.ProcessoRepository
import io.quarkus.panache.common.Page
import io.quarkus.panache.common.Sort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.validation.ValidationException
import jakarta.transaction.Transactional
import org.jboss.logging.Logger

@ApplicationScoped
class ProcessoService(
    private val repository: ProcessoRepository,
    private val pessoaService: PessoaService,
    private val processoMapper: ProcessoMapper
) {
    private val logger = Logger.getLogger(ProcessoService::class.java)

    @Transactional
    fun create(request: ProcessoCreateRequest): ProcessoDTO {
        ensureUniqueNumero(request.numeroProcesso!!, null)
        if (request.clienteId == null || request.advogadoId == null) {
            throw BusinessException("Processo deve possuir cliente e advogado")
        }
        val processo = Processo(
            numeroProcesso = request.numeroProcesso.trim(),
            descricao = request.descricao!!.trim(),
            cliente = pessoaService.findEntity(request.clienteId!!),
            advogado = pessoaService.findEntity(request.advogadoId!!),
            dataAbertura = request.dataAbertura!!,
            contratoTrabalho = toContratoTrabalho(request.contratoTrabalho),
            estrategiaProcessual = toEstrategiaProcessual(request.estrategiaProcessual),
            status = request.status!!,
            ativo = request.ativo,
            rtDescricaoAcidente = request.rtDescricaoAcidente,
            rtCctPeriodo = request.rtCctPeriodo,
            rtClausulaConvencional = request.rtClausulaConvencional,
            rtAssuntoClausula = request.rtAssuntoClausula,
            rtRedacaoClausula = request.rtRedacaoClausula,
            rtSalarioFuncaoOriginal = request.rtSalarioFuncaoOriginal,
            rtSalarioFuncaoAcumulada = request.rtSalarioFuncaoAcumulada,
            rtValorPagoPorFora = request.rtValorPagoPorFora,
            rtMediaHorasExtras = request.rtMediaHorasExtras
        )
        processo.reclamadas = resolvePessoas(request.reclamadasIds)
        processo.sociosResponsaveis = resolvePessoas(request.sociosResponsaveisIds)
        repository.persist(processo)
        logger.infof("Processo criado id=%s numero=%s", processo.id, processo.numeroProcesso)
        return processoMapper.toResponse(processo)
    }

    fun list(
        numeroProcesso: String?,
        clienteId: Long?,
        advogadoId: Long?,
        status: StatusProcesso?,
        page: Int,
        size: Int
    ): PageResponse<ProcessoDTO> {
        val params = mutableMapOf<String, Any>()
        val where = mutableListOf("ativo = true")
        numeroProcesso?.takeIf { it.isNotBlank() }?.let {
            where += "lower(numeroProcesso) like :numeroProcesso"
            params["numeroProcesso"] = "%${it.lowercase()}%"
        }
        clienteId?.let {
            where += "cliente.id = :clienteId"
            params["clienteId"] = it
        }
        advogadoId?.let {
            where += "advogado.id = :advogadoId"
            params["advogadoId"] = it
        }
        status?.let {
            where += "status = :status"
            params["status"] = it
        }
        val queryText = where.joinToString(" and ").ifBlank { "1 = 1" }
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, 100)
        val query = repository.find(queryText, Sort.by("dataAbertura").descending(), params).page(Page.of(safePage, safeSize))
        return PageResponse(query.list().map(processoMapper::toResponse), safePage, safeSize, query.count(), query.pageCount())
    }

    fun getById(id: Long): ProcessoDTO = processoMapper.toResponse(findEntity(id))

    fun findEntity(id: Long): Processo =
        repository.findActiveById(id) ?: throw ResourceNotFoundException("Processo $id nao encontrado")

    @Transactional
    fun update(id: Long, request: ProcessoUpdateRequest): ProcessoDTO {
        val processo = findEntity(id)
        ensureUniqueNumero(request.numeroProcesso!!, id)
        if (request.clienteId == null || request.advogadoId == null) {
            throw BusinessException("Processo deve possuir cliente e advogado")
        }
        processo.numeroProcesso = request.numeroProcesso.trim()
        processo.descricao = request.descricao!!.trim()
        processo.cliente = pessoaService.findEntity(request.clienteId!!)
        processo.advogado = pessoaService.findEntity(request.advogadoId!!)
        processo.dataAbertura = request.dataAbertura!!
        processo.contratoTrabalho = toContratoTrabalho(request.contratoTrabalho)
        processo.estrategiaProcessual = toEstrategiaProcessual(request.estrategiaProcessual)
        processo.reclamadas = resolvePessoas(request.reclamadasIds)
        processo.sociosResponsaveis = resolvePessoas(request.sociosResponsaveisIds)
        processo.status = request.status!!
        processo.ativo = request.ativo
        processo.rtDescricaoAcidente = request.rtDescricaoAcidente
        processo.rtCctPeriodo = request.rtCctPeriodo
        processo.rtClausulaConvencional = request.rtClausulaConvencional
        processo.rtAssuntoClausula = request.rtAssuntoClausula
        processo.rtRedacaoClausula = request.rtRedacaoClausula
        processo.rtSalarioFuncaoOriginal = request.rtSalarioFuncaoOriginal
        processo.rtSalarioFuncaoAcumulada = request.rtSalarioFuncaoAcumulada
        processo.rtValorPagoPorFora = request.rtValorPagoPorFora
        processo.rtMediaHorasExtras = request.rtMediaHorasExtras
        logger.infof("Processo atualizado id=%s", processo.id)
        return processoMapper.toResponse(processo)
    }

    @Transactional
    fun delete(id: Long) {
        val processo = findEntity(id)
        processo.ativo = false
        logger.infof("Processo inativado id=%s", id)
    }

    private fun ensureUniqueNumero(numeroProcesso: String, id: Long?) {
        if (repository.existsNumeroForAnotherProcesso(numeroProcesso.trim(), id)) {
            throw BusinessException("Numero de processo ja cadastrado")
        }
    }

    private fun resolvePessoas(ids: List<Long>): MutableSet<Pessoa> =
        ids.asSequence()
            .distinct()
            .map(pessoaService::findEntity)
            .toCollection(linkedSetOf())

    private fun toContratoTrabalho(request: ContratoTrabalhoRequest?): ContratoTrabalho? = request?.let {
        ContratoTrabalho(
            funcaoExercida = it.funcaoExercida?.trim(),
            dataAdmissao = it.dataAdmissao,
            dataDemissao = it.dataDemissao,
            tipoRescisao = it.tipoRescisao,
            ultimaRemuneracao = it.ultimaRemuneracao,
            avisoPrevioProjetadoEm = it.avisoPrevioProjetadoEm,
            jornadaDescricao = it.jornadaDescricao?.trim(),
            localPrestacaoServico = it.localPrestacaoServico?.trim()
        )
    }

    private fun toEstrategiaProcessual(request: EstrategiaProcessualRequest?): EstrategiaProcessual? = request?.let {
        EstrategiaProcessual(
            fundamentosFaticos = it.fundamentosFaticos?.trim(),
            pedidosPrincipais = it.pedidosPrincipais?.trim(),
            observacoesInternas = it.observacoesInternas?.trim(),
            responsabilidadePretendida = it.responsabilidadePretendida,
            possuiGrupoEconomico = it.possuiGrupoEconomico,
            possuiAcidenteTrabalho = it.possuiAcidenteTrabalho,
            possuiDoencaOcupacional = it.possuiDoencaOcupacional,
            requerEmissaoCat = it.requerEmissaoCat,
            valorCausa = it.valorCausa
        )
    }
}
