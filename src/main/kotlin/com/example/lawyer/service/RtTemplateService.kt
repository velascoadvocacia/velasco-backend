package com.example.lawyer.service

import com.example.lawyer.domain.model.Processo
import com.example.lawyer.domain.model.Usuario
import com.example.lawyer.dto.request.RtPreviewRequest
import com.example.lawyer.dto.response.RtPreviewBlockResponse
import com.example.lawyer.exception.BusinessException
import com.example.lawyer.exception.ResourceNotFoundException
import com.example.lawyer.repository.UsuarioRepository
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.time.format.DateTimeFormatter

@ApplicationScoped
class RtTemplateService(
    private val processoService: ProcessoService,
    private val pessoaService: PessoaService,
    private val usuarioRepository: UsuarioRepository,
    @ConfigProperty(name = "rt.escritorio.endereco")
    private val enderecoEscritorio: String
) {
    private val blockDefinitions = linkedMapOf(
        QUALIFICACAO_RECLAMANTE to RtBlockDefinition(
            titulo = "Qualificação do Reclamante",
            generate = { processo, advogados -> qualificacaoReclamante(processo, advogados) }
        ),
        QUALIFICACAO_RECLAMADA to RtBlockDefinition(
            titulo = "Qualificação da Reclamada",
            generate = { processo, _ -> qualificacaoReclamada(processo) }
        ),
        DADOS_RECLAMANTE to RtBlockDefinition(
            titulo = "Dados do(a) Reclamante",
            generate = { processo, _ -> dadosReclamante(processo) }
        )
    )

    fun generateSelectedBlocks(request: RtPreviewRequest): List<RtPreviewBlockResponse> {
        val precisaProcesso = request.processoId != null &&
            (request.reclamantesIds.isEmpty() || request.reclamadasIds.isEmpty() || request.advogadosIds.isEmpty())
        val processo = if (precisaProcesso) {
            processoService.findEntity(request.processoId!!)
        } else {
            Processo()
        }
        if (request.reclamantesIds.isNotEmpty()) {
            processo.reclamantes = resolvePessoas(request.reclamantesIds)
        }
        if (request.reclamadasIds.isNotEmpty()) {
            processo.reclamadas = resolvePessoas(request.reclamadasIds)
        }
        val advogados = if (request.advogadosIds.isEmpty()) {
            processo.advogados
        } else {
            resolveAdvogados(request.advogadosIds)
        }
        return request.blocosSelecionados.map { it.trim() }
            .distinct()
            .mapNotNull { blockId ->
                blockDefinitions[blockId]?.let { definition ->
                    RtPreviewBlockResponse(
                        id = blockId,
                        titulo = definition.titulo,
                        texto = definition.generate(processo, advogados)
                    )
                }
            }
    }

    private fun dadosReclamante(processo: Processo): String =
        processo.reclamantes.mapNotNull { reclamante ->
            listOfNotNull(
                reclamante.cpf?.trim()?.takeIf { it.isNotEmpty() }?.let { "CPF: $it" },
                reclamante.dataNascimento?.format(DATE_FORMATTER)?.let { "D.N.: $it" },
                reclamante.nomeMae?.trim()?.takeIf { it.isNotEmpty() }?.let { "Mãe: $it" }
            ).joinToString("\n").takeIf { it.isNotEmpty() }
        }.joinToString("\n\n")

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

    private fun qualificacaoReclamada(processo: Processo): String {
        val reclamadas = processo.reclamadas.toList()
        if (reclamadas.isEmpty()) return "contra, pelas razões de fato e de Direito que passa a expor."

        val qualificacoes = reclamadas.mapIndexed { index, pessoa ->
            val ordinal = ordinalReclamada(index + 1)
            val nome = pessoa.nome.ifBlank { "não informado" }
            val tipo = if (pessoa.tipoPessoa == com.example.lawyer.domain.enums.TipoPessoa.JURIDICA) {
                "pessoa jurídica de direito privado"
            } else {
                "pessoa física"
            }
            val documento = if (pessoa.tipoPessoa == com.example.lawyer.domain.enums.TipoPessoa.JURIDICA) {
                pessoa.cnpj?.takeIf { it.isNotBlank() }?.let { "CNPJ n.º $it" }
            } else {
                pessoa.cpf?.takeIf { it.isNotBlank() }?.let { "CPF n.º $it" }
            }
            val endereco = formatReclamadaAddress(pessoa.endereco)
            buildString {
                if (reclamadas.size > 1) append("$ordinal RECLAMADA, ")
                append(nome).append(", ").append(tipo)
                documento?.let { append(", ").append(it) }
                if (endereco.isNotBlank()) append(", com endereço à ").append(endereco)
            }
        }

        return "contra ${qualificacoes.joinToString("; e ")}, pelas razões de fato e de Direito que passa a expor."
    }

    private fun formatReclamadaAddress(endereco: com.example.lawyer.domain.model.Endereco?): String {
        if (endereco == null) return ""
        val rua = endereco.rua?.trim()?.takeIf { it.isNotEmpty() }?.let {
            if (it.startsWith("Rua ", ignoreCase = true)) it else "Rua $it"
        }
        return buildList {
            rua?.let(::add)
            endereco.numero?.trim()?.takeIf { it.isNotEmpty() }?.let { add("n.º $it") }
            endereco.cep?.trim()?.takeIf { it.isNotEmpty() }?.let { add("CEP n.º ${formatCep(it)}") }
            endereco.bairro?.trim()?.takeIf { it.isNotEmpty() }?.let { add("Bairro $it") }
            listOfNotNull(
                endereco.cidade?.trim()?.takeIf { it.isNotEmpty() },
                endereco.estado?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
            ).takeIf { it.isNotEmpty() }?.let { add(it.joinToString(", ")) }
        }.joinToString(", ")
    }

    private fun ordinalReclamada(numero: Int): String {
        val unidades = listOf("", "PRIMEIRA", "SEGUNDA", "TERCEIRA", "QUARTA", "QUINTA", "SEXTA", "SÉTIMA", "OITAVA", "NONA")
        val dezenas = mapOf(10 to "DÉCIMA", 20 to "VIGÉSIMA", 30 to "TRIGÉSIMA", 40 to "QUADRAGÉSIMA", 50 to "QUINQUAGÉSIMA", 60 to "SEXAGÉSIMA", 70 to "SEPTUAGÉSIMA", 80 to "OCTOGÉSIMA", 90 to "NONAGÉSIMA")
        return when {
            numero < 1 -> ""
            numero < 10 -> unidades[numero]
            numero < 20 -> "DÉCIMA ${unidades[numero - 10]}"
            numero % 10 == 0 -> dezenas[numero] ?: numero.toString()
            else -> "${dezenas[numero / 10 * 10] ?: numero.toString()} ${unidades[numero % 10]}"
        }
    }

    private fun List<Usuario>.joinAdvogados(): String {
        val formatted = map { advogado ->
            val nome = advogado.pessoa?.nome ?: ""
            val tratamento = advogado.tratamento?.abreviacao ?: "Dr(a)."
            val oab = formatOab(advogado.ufOab, advogado.numeroOab)?.let { ", $it" } ?: ""
            "$tratamento $nome$oab".trim()
        }
        return when (formatted.size) {
            0 -> ""
            1 -> formatted.first()
            2 -> formatted.joinToString(" e ")
            else -> formatted.dropLast(1).joinToString(", ") + " e " + formatted.last()
        }
    }

    private fun formatOab(uf: String?, numero: String?): String? {
        val estado = uf?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: return null
        val inscricao = numero?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return "OAB/$estado nº $inscricao"
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

    private fun resolvePessoas(ids: List<Long>) = ids.distinct()
        .map(pessoaService::findEntity)
        .toCollection(linkedSetOf())

    private data class RtBlockDefinition(
        val titulo: String,
        val generate: (Processo, Set<Usuario>) -> String
    )

    companion object {
        const val QUALIFICACAO_RECLAMANTE = "qualificacao_reclamante"
        const val QUALIFICACAO_RECLAMADA = "qualificacao_reclamada"
        const val DADOS_RECLAMANTE = "dados_reclamante"
        private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    }
}
