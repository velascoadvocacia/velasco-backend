package com.example.lawyer.mapper

import com.example.lawyer.domain.model.Movimentacao
import com.example.lawyer.dto.response.MovimentacaoResponse
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class MovimentacaoMapper(private val processoMapper: ProcessoMapper) {
    fun toResponse(entity: Movimentacao): MovimentacaoResponse = MovimentacaoResponse(
        id = entity.id!!,
        processo = processoMapper.toResumoResponse(entity.processo!!),
        descricao = entity.descricao,
        dataMovimentacao = entity.dataMovimentacao
    )
}
