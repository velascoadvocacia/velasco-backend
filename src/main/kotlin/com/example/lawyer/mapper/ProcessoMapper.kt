package com.example.lawyer.mapper

import com.example.lawyer.domain.model.ContratoTrabalho
import com.example.lawyer.domain.model.EstrategiaProcessual
import com.example.lawyer.domain.model.Movimentacao
import com.example.lawyer.domain.model.Processo
import com.example.lawyer.dto.response.ContratoTrabalhoResponse
import com.example.lawyer.dto.response.AdvogadoResumoResponse
import com.example.lawyer.dto.response.EstrategiaProcessualResponse
import com.example.lawyer.dto.response.MovimentacaoResumoResponse
import com.example.lawyer.dto.response.ProcessoDTO
import com.example.lawyer.dto.response.ProcessoResumoResponse
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class ProcessoMapper(private val pessoaMapper: PessoaMapper) {
    fun toResponse(entity: Processo): ProcessoDTO {
        val reclamantes = entity.reclamantes.filter { it.ativo }.map(pessoaMapper::toResumoResponse)
        val advogados = entity.advogados.filter { it.ativo }.map {
            AdvogadoResumoResponse(it.id!!, it.pessoa?.nome ?: "não informado", it.ufOab, it.numeroOab)
        }
        val dados = entity.dadosVariaveis.groupBy { it.blocoId }
            .mapValues { (_, values) -> values.associate { it.campo to it.valor } }
        val reclamante = reclamantes.firstOrNull()
        val advogadoPessoa = entity.advogados.firstOrNull { it.ativo }?.pessoa
        return ProcessoDTO(
            id = entity.id!!,
            numeroProcesso = entity.numeroProcesso,
            descricao = entity.descricao,
            reclamante = reclamante,
            advogadoResponsavel = advogadoPessoa?.let(pessoaMapper::toResumoResponse),
            cliente = reclamante,
            advogado = advogadoPessoa?.let(pessoaMapper::toResumoResponse),
            reclamantes = reclamantes,
            advogados = advogados,
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
            rtMediaHorasExtras = entity.rtMediaHorasExtras,
            blocosSelecionados = entity.blocosSelecionados.toList(),
            dadosVariaveis = dados
        )
    }

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
