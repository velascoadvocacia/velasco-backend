package com.example.lawyer.mapper

import com.example.lawyer.domain.model.ContratoTrabalho
import com.example.lawyer.domain.model.EstrategiaProcessual
import com.example.lawyer.domain.model.Movimentacao
import com.example.lawyer.domain.model.Processo
import com.example.lawyer.dto.response.ContratoTrabalhoResponse
import com.example.lawyer.dto.response.EstrategiaProcessualResponse
import com.example.lawyer.dto.response.MovimentacaoResumoResponse
import com.example.lawyer.dto.response.ProcessoDTO
import com.example.lawyer.dto.response.ProcessoResumoResponse
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class ProcessoMapper(private val pessoaMapper: PessoaMapper) {
    fun toResponse(entity: Processo): ProcessoDTO = ProcessoDTO(
        id = entity.id!!,
        numeroProcesso = entity.numeroProcesso,
        descricao = entity.descricao,
        reclamante = pessoaMapper.toResumoResponse(entity.cliente!!),
        advogadoResponsavel = pessoaMapper.toResumoResponse(entity.advogado!!),
        cliente = pessoaMapper.toResumoResponse(entity.cliente!!),
        advogado = pessoaMapper.toResumoResponse(entity.advogado!!),
        reclamadas = entity.reclamadas.filter { it.ativo }.map(pessoaMapper::toResumoResponse),
        sociosResponsaveis = entity.sociosResponsaveis.filter { it.ativo }.map(pessoaMapper::toResumoResponse),
        dataAbertura = entity.dataAbertura,
        contratoTrabalho = entity.contratoTrabalho?.let(::toContratoResponse),
        estrategiaProcessual = entity.estrategiaProcessual?.let(::toEstrategiaResponse),
        status = entity.status,
        ativo = entity.ativo,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
        movimentacoes = entity.movimentacoes
            .filter { it.ativo }
            .sortedByDescending { it.dataMovimentacao }
            .map(::toResumoMovimentacao),
        rtDescricaoAcidente = entity.rtDescricaoAcidente,
        rtCctPeriodo = entity.rtCctPeriodo,
        rtClausulaConvencional = entity.rtClausulaConvencional,
        rtAssuntoClausula = entity.rtAssuntoClausula,
        rtRedacaoClausula = entity.rtRedacaoClausula,
        rtSalarioFuncaoOriginal = entity.rtSalarioFuncaoOriginal,
        rtSalarioFuncaoAcumulada = entity.rtSalarioFuncaoAcumulada,
        rtValorPagoPorFora = entity.rtValorPagoPorFora,
        rtMediaHorasExtras = entity.rtMediaHorasExtras
    )

    fun toResumoResponse(entity: Processo): ProcessoResumoResponse =
        ProcessoResumoResponse(entity.id!!, entity.numeroProcesso, entity.status)

    fun toResumoMovimentacao(entity: Movimentacao): MovimentacaoResumoResponse =
        MovimentacaoResumoResponse(entity.id!!, entity.descricao, entity.dataMovimentacao)

    private fun toContratoResponse(entity: ContratoTrabalho): ContratoTrabalhoResponse =
        ContratoTrabalhoResponse(
            funcaoExercida = entity.funcaoExercida,
            dataAdmissao = entity.dataAdmissao,
            dataDemissao = entity.dataDemissao,
            tipoRescisao = entity.tipoRescisao,
            ultimaRemuneracao = entity.ultimaRemuneracao,
            avisoPrevioProjetadoEm = entity.avisoPrevioProjetadoEm,
            jornadaDescricao = entity.jornadaDescricao,
            localPrestacaoServico = entity.localPrestacaoServico
        )

    private fun toEstrategiaResponse(entity: EstrategiaProcessual): EstrategiaProcessualResponse =
        EstrategiaProcessualResponse(
            fundamentosFaticos = entity.fundamentosFaticos,
            pedidosPrincipais = entity.pedidosPrincipais,
            observacoesInternas = entity.observacoesInternas,
            responsabilidadePretendida = entity.responsabilidadePretendida,
            possuiGrupoEconomico = entity.possuiGrupoEconomico,
            possuiAcidenteTrabalho = entity.possuiAcidenteTrabalho,
            possuiDoencaOcupacional = entity.possuiDoencaOcupacional,
            requerEmissaoCat = entity.requerEmissaoCat,
            valorCausa = entity.valorCausa
        )
}