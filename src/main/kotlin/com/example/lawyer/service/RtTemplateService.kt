package com.example.lawyer.service

import com.example.lawyer.domain.model.Processo
import com.example.lawyer.domain.model.Usuario
import com.example.lawyer.dto.response.RtPreviewBlockResponse
import com.example.lawyer.exception.BusinessException
import com.example.lawyer.exception.ResourceNotFoundException
import com.example.lawyer.repository.UsuarioRepository
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
class RtTemplateService(
    private val processoService: ProcessoService,
    private val usuarioRepository: UsuarioRepository,
    @ConfigProperty(name = "rt.escritorio.endereco")
    private val enderecoEscritorio: String
) {
    fun generateSelectedBlocks(
        processoId: Long,
        selectedBlocks: List<String>,
        advogadosIds: List<Long> = emptyList()
    ): List<RtPreviewBlockResponse> {
        val processo = processoService.findEntity(processoId)
        val advogados = if (advogadosIds.isEmpty()) processo.advogados else resolveAdvogados(advogadosIds)
        return selectedBlocks.map { it.trim() }.filter { it == QUALIFICACAO_RECLAMANTE }.distinct().map {
            RtPreviewBlockResponse(
                blocoId = QUALIFICACAO_RECLAMANTE,
                titulo = "Qualificação do Reclamante",
                texto = qualificacaoReclamante(processo, advogados)
            )
        }
    }

    private fun qualificacaoReclamante(processo: Processo, advogados: Set<Usuario>): String {
        val reclamante = processo.reclamantes.firstOrNull()
        val endereco = reclamante?.endereco
        val qualificacao = listOfNotNull(
            reclamante?.nome,
            reclamante?.nacionalidade?.let(::neutralizeGender),
            reclamante?.estadoCivil?.name?.lowercase()?.let(::neutralizeGender),
            reclamante?.profissao
        ).joinToString(", ")
        val listaAdvogados = advogados.filter { it.ativo }.joinAdvogados()

        return "$qualificacao, residente e domiciliado(a)${formatAddress(endereco)}, " +
            "devidamente qualificado(a) no item 1, através de seus procuradores que subscreve, $listaAdvogados, " +
            "com endereço profissional na $enderecoEscritorio, onde recebem intimações, " +
            "vem à presença de Vossa Excelência, com fundamento no art. 840 da CLT, ajuizar esta"
    }

    private fun List<Usuario>.joinAdvogados(): String {
        val formatted = map { advogado ->
            val nome = advogado.pessoa?.nome ?: ""
            val tratamento = advogado.tratamento?.abreviacao ?: "Dr(a)."
            val oab = formatOab(advogado.oab)?.let { ", OAB/$it" } ?: ""
            "$tratamento $nome$oab".trim()
        }
        return when (formatted.size) {
            0 -> ""
            1 -> formatted.first()
            2 -> formatted.joinToString(" e ")
            else -> formatted.dropLast(1).joinToString(", ") + " e " + formatted.last()
        }
    }

    private fun formatOab(oab: String?): String? {
        val value = oab?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return value.replaceFirst(Regex("^OAB/", RegexOption.IGNORE_CASE), "")
    }

    private fun neutralizeGender(value: String): String =
        if (value.endsWith("(a)")) value else if (value.endsWith("o")) "${value.dropLast(1)}o(a)" else value

    private fun formatAddress(endereco: com.example.lawyer.domain.model.Endereco?): String {
        if (endereco == null) return ""
        val address = buildString {
            endereco.rua?.takeIf { it.isNotBlank() }?.let { append(" na $it") }
            endereco.numero?.takeIf { it.isNotBlank() }?.let { append(", nº $it") }
            endereco.complemento?.takeIf { it.isNotBlank() }?.let { append(", $it") }
            endereco.bairro?.takeIf { it.isNotBlank() }?.let { append(", $it") }
            val cidade = endereco.cidade?.takeIf { it.isNotBlank() }
            val estado = endereco.estado?.takeIf { it.isNotBlank() }
            if (cidade != null || estado != null) {
                append(", em ")
                append(listOfNotNull(cidade, estado).joinToString(" – "))
            }
            endereco.cep?.takeIf { it.isNotBlank() }?.let { append(", CEP ${formatCep(it)}") }
        }
        return address
    }

    private fun formatCep(value: String): String {
        val digits = value.filter { it.isDigit() }
        return if (digits.length == 8) "${digits.substring(0, 5)}-${digits.substring(5)}" else value
    }

    private fun resolveAdvogados(ids: List<Long>): Set<Usuario> {
        val advogados = ids.distinct().map {
            usuarioRepository.findActiveById(it) ?: throw ResourceNotFoundException("Advogado nao encontrado")
        }
        advogados.forEach {
            if (it.perfil != com.example.lawyer.domain.enums.PerfilUsuario.ADVOGADO) {
                throw BusinessException("Usuario selecionado nao possui perfil de advogado")
            }
        }
        if (advogados.isEmpty()) throw BusinessException("Processo deve possuir ao menos um advogado")
        return advogados.toSet()
    }

    companion object {
        const val QUALIFICACAO_RECLAMANTE = "qualificacao-reclamante"
    }
}
