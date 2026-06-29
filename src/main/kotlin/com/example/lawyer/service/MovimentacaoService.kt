package com.example.lawyer.service

import com.example.lawyer.domain.model.Movimentacao
import com.example.lawyer.dto.common.PageResponse
import com.example.lawyer.dto.request.MovimentacaoCreateRequest
import com.example.lawyer.dto.request.MovimentacaoUpdateRequest
import com.example.lawyer.dto.response.MovimentacaoResponse
import com.example.lawyer.exception.ResourceNotFoundException
import com.example.lawyer.mapper.MovimentacaoMapper
import com.example.lawyer.repository.MovimentacaoRepository
import io.quarkus.panache.common.Page
import io.quarkus.panache.common.Sort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import org.jboss.logging.Logger
import java.time.OffsetDateTime

@ApplicationScoped
class MovimentacaoService(
    private val repository: MovimentacaoRepository,
    private val processoService: ProcessoService,
    private val movimentacaoMapper: MovimentacaoMapper
) {
    private val logger = Logger.getLogger(MovimentacaoService::class.java)

    @Transactional
    fun create(request: MovimentacaoCreateRequest): MovimentacaoResponse {
        val movimentacao = Movimentacao(
            processo = processoService.findEntity(request.processoId!!),
            descricao = request.descricao!!.trim(),
            dataMovimentacao = request.dataMovimentacao ?: OffsetDateTime.now(),
            ativo = true
        )
        repository.persist(movimentacao)
        logger.infof("Movimentacao criada id=%s processoId=%s", movimentacao.id, movimentacao.processo?.id)
        return movimentacaoMapper.toResponse(movimentacao)
    }

    fun list(processoId: Long?, page: Int, size: Int): PageResponse<MovimentacaoResponse> {
        val params = mutableMapOf<String, Any>()
        val where = mutableListOf("ativo = true")
        if (processoId != null) {
            params["processoId"] = processoId
            where += "processo.id = :processoId"
        }
        val queryText = where.joinToString(" and ")
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, 100)
        val query = repository.find(queryText, Sort.by("dataMovimentacao").descending(), params).page(Page.of(safePage, safeSize))
        return PageResponse(query.list().map(movimentacaoMapper::toResponse), safePage, safeSize, query.count(), query.pageCount())
    }

    fun getById(id: Long): MovimentacaoResponse = movimentacaoMapper.toResponse(findEntity(id))

    private fun findEntity(id: Long): Movimentacao =
        repository.findActiveById(id) ?: throw ResourceNotFoundException("Movimentacao $id nao encontrada")

    @Transactional
    fun update(id: Long, request: MovimentacaoUpdateRequest): MovimentacaoResponse {
        val movimentacao = findEntity(id)
        movimentacao.processo = processoService.findEntity(request.processoId!!)
        movimentacao.descricao = request.descricao!!.trim()
        movimentacao.dataMovimentacao = request.dataMovimentacao!!
        logger.infof("Movimentacao atualizada id=%s", movimentacao.id)
        return movimentacaoMapper.toResponse(movimentacao)
    }

    @Transactional
    fun delete(id: Long) {
        val movimentacao = findEntity(id)
        movimentacao.ativo = false
        logger.infof("Movimentacao inativada id=%s", id)
    }
}
