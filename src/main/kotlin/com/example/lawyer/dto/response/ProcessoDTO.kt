package com.example.lawyer.dto.response

import com.example.lawyer.domain.enums.StatusProcesso
import java.time.LocalDate
import java.time.OffsetDateTime

data class ProcessoDTO(
    val id: Long,
    val numeroProcesso: String,
    val descricao: String,
    val reclamante: PessoaResumoResponse,
    val advogadoResponsavel: PessoaResumoResponse,
    val cliente: PessoaResumoResponse,
    val advogado: PessoaResumoResponse,
    val reclamadas: List<PessoaResumoResponse>,
    val sociosResponsaveis: List<PessoaResumoResponse>,
    val dataAbertura: LocalDate,
    val contratoTrabalho: ContratoTrabalhoResponse?,
    val estrategiaProcessual: EstrategiaProcessualResponse?,
    val status: StatusProcesso,
    val ativo: Boolean,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val movimentacoes: List<MovimentacaoResumoResponse>,
    val rtDescricaoAcidente: String? = null,
    val rtCctPeriodo: String? = null,
    val rtClausulaConvencional: String? = null,
    val rtAssuntoClausula: String? = null,
    val rtRedacaoClausula: String? = null,
    val rtSalarioFuncaoOriginal: String? = null,
    val rtSalarioFuncaoAcumulada: String? = null,
    val rtValorPagoPorFora: String? = null,
    val rtMediaHorasExtras: String? = null
)