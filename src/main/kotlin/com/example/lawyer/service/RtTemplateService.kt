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
        val enderecoCompleto = listOfNotNull(
            endereco?.rua,
            endereco?.numero,
            endereco?.complemento,
            endereco?.bairro
        ).joinToString(", ").ifBlank { "endereço não informado" }
        val cidade = endereco?.cidade?.takeIf { it.isNotBlank() } ?: "não informado"
        val estado = endereco?.estado?.takeIf { it.isNotBlank() } ?: "não informado"
        val cep = endereco?.cep?.takeIf { it.isNotBlank() } ?: "não informado"
        val listaAdvogados = advogados.filter { it.ativo }.joinAdvogados()

        return "${reclamante?.nome ?: "não informado"}, " +
            "${reclamante?.nacionalidade ?: "não informado"}, estado civil " +
            "${reclamante?.estadoCivil?.name ?: "não informado"}, " +
            "${reclamante?.profissao ?: "não informado"}, residente e domiciliado " +
            "$enderecoCompleto, em $cidade – $estado, CEP $cep, devidamente qualificado no item 1, " +
            "através de seus procuradores que subscreve, $listaAdvogados, " +
            "com endereço profissional na $enderecoEscritorio, onde recebem intimações, " +
            "vem à presença de Vossa Excelência, com fundamento no art. 840 da CLT, ajuizar esta"
    }

    private fun List<Usuario>.joinAdvogados(): String {
        val formatted = map { advogado ->
            val nome = advogado.pessoa?.nome ?: "não informado"
            "Dr. $nome, ${formatOab(advogado.oab)}"
        }
        return when (formatted.size) {
            0 -> "não informado"
            1 -> formatted.first()
            2 -> formatted.joinToString(" e ")
            else -> formatted.dropLast(1).joinToString(", ") + " e " + formatted.last()
        }
    }

    private fun formatOab(oab: String?): String {
        val value = oab?.trim()?.takeIf { it.isNotEmpty() } ?: "não informado"
        return if (value.startsWith("OAB/", ignoreCase = true)) value else "OAB/$value"
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
