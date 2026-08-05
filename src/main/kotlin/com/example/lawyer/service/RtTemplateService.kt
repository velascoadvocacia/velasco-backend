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
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@ApplicationScoped
class RtTemplateService(
    private val processoService: ProcessoService,
    private val pessoaService: PessoaService,
    private val usuarioRepository: UsuarioRepository,
    private val processoAnexoService: ProcessoAnexoService,
    @ConfigProperty(name = "rt.escritorio.endereco")
    private val enderecoEscritorio: String
) {
    private val blockDefinitions = linkedMapOf(
        QUALIFICACAO_RECLAMANTE to RtBlockDefinition(
            titulo = { "Qualificação do Reclamante" },
            generate = { processo, advogados, _ -> qualificacaoReclamante(processo, advogados) }
        ),
        QUALIFICACAO_RECLAMADA to RtBlockDefinition(
            titulo = { "Qualificação da Reclamada" },
            generate = { processo, _, _ -> qualificacaoReclamada(processo) }
        ),
        DADOS_RECLAMANTE to RtBlockDefinition(
            titulo = { "Dados do(a) Reclamante" },
            generate = { processo, _, _ -> dadosReclamante(processo) }
        ),
        CONTRATO_ASPECTOS_GERAIS to RtBlockDefinition(
            titulo = { "Contrato de trabalho - Aspectos gerais" },
            generate = { _, _, variaveis -> contratoAspectosGerais(variaveis) }
        ),
        BAIXA_CTPS_TUTELA to RtBlockDefinition(
            titulo = { "3. Baixa na CTPS física. Tutela antecipada" },
            generate = { _, _, variaveis -> baixaCtpsTutela(variaveis) }
        ),
        RESPONSABILIDADE_SOLIDARIA_GRUPO_ECONOMICO to RtBlockDefinition(
            titulo = { "Responsabilidade solidária. Grupo econômico" },
            generate = { _, _, variaveis -> responsabilidadeSolidariaGrupoEconomico(variaveis) }
        ),
        RESPONSABILIDADE_SUBSIDIARIA to RtBlockDefinition(
            titulo = { "Responsabilidade subsidiária" },
            generate = { processo, _, _ -> responsabilidadeSubsidiaria(processo) }
        ),
        LEGITIMIDADE_PASSIVA_SOCIOS to RtBlockDefinition(
            titulo = { "Legitimidade passiva dos sócios das rés" },
            generate = { _, _, _ -> legitimidadePassivaSocios() }
        )
    )

    fun generateSelectedBlocks(request: RtPreviewRequest): List<RtPreviewBlockResponse> {
        val processo = if (request.processoId != null) {
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
                    val variaveis = variaveisDoBloco(processo, blockId) + request.dadosVariaveis
                    RtPreviewBlockResponse(
                        id = blockId,
                        titulo = definition.titulo(variaveis),
                        texto = definition.generate(processo, advogados, variaveis),
                        anexos = if (blockId in BLOCOS_COM_ANEXOS) {
                            request.processoId?.let { processoAnexoService.list(it, blockId) }.orEmpty()
                        } else {
                            emptyList()
                        }
                    )
                }
            }
    }

    private fun variaveisDoBloco(processo: Processo, blocoId: String): Map<String, String?> =
        processo.dadosVariaveis
            .asSequence()
            .filter { it.blocoId == blocoId }
            .associate { it.campo to it.valor }

    private fun contratoAspectosGerais(variaveis: Map<String, String?>): String {
        val motivo = opcaoMotivoExtincao(variaveis["motivoExtincao"])?.motivo ?: PLACEHOLDER
        val informacoesComplementares = variaveis["informacoesComplementares"]
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        return buildString {
            append("A parte autora foi contratada pela parte ré em ")
            append(formatVariableDate(variaveis["dataContratacao"]))
            append(", para exercer a função de ")
            append(variaveis["funcaoContrato"].orPlaceholder())
            append(", com última remuneração de R$ ")
            append(formatCurrency(variaveis["remuneracao"]))
            append(", com a extinção do vínculo empregatício ")
            append(motivo)
            append(" em ")
            append(formatVariableDate(variaveis["dataExtincao"]))
            append(".")
            informacoesComplementares?.let { append(" ").append(it) }
        }
    }

    private fun baixaCtpsTutela(variaveis: Map<String, String?>): String {
        val dataExtincao = formatVariableDate(variaveis["dataExtincao"])
        return "Conquanto a parte autora tenha sido dispensada sem justa causa, não houve a baixa na sua CTPS física pela ré, pelo que deve ser concedida tutela de urgência antecipada, nos termos do art. 300 do CPC, pois estão presentes o periculum in mora e o fumus boni juris, para que haja a baixa na CTPS:\n\n" +
            "Pelo exposto, **REQUER** seja concedida tutela de urgência antecipada, nos termos do art. 300 do CPC, para que seja determinado à ré que proceda à baixa na CTPS física, considerando como data do término da relação empregatícia o dia $dataExtincao. Consequentemente, em caso de descumprimento da obrigação, **REQUER** seja arbitrada multa diária, revertida em favor da parte autora, em valor a ser estabelecido por este Juízo, e, neste caso, sejam realizadas as anotações pela secretaria da Vara do Trabalho, nos termos do art. 39, § 1º, da CLT.\n\n" +
            "__No mérito__, **REQUER-SE** a confirmação do pedido de tutela antecipada acima formulado, com o seu integral acolhimento."
    }

    private fun responsabilidadeSolidariaGrupoEconomico(variaveis: Map<String, String?>): String {
        val atividade = variaveis["descricaoAtividadePrincipal"].orPlaceholder()
        return "As empresas rés, que formam um grupo econômico, se aproveitaram da mão de obra do autor, " +
            "havendo comunhão de interesses, administração integrada, com a finalidade de explorar de forma " +
            "integrada atividades econômicas semelhantes, de $atividade:\n\n" +
            "Pelo exposto, com fundamento no **art. 2º, § 2º, da CLT** (Sempre que uma ou mais empresas, tendo, embora, cada uma delas, " +
            "personalidade jurídica própria, estiverem sob __a direção, controle ou administração de outra, constituindo grupo industrial, comercial ou de qualquer outra atividade econômica, " +
            "serão, para os efeitos da relação de emprego, solidariamente responsáveis a empresa principal__ e " +
            "cada uma das subordinadas.), **REQUER-SE** a condenação solidária das rés."
    }

    private fun legitimidadePassivaSocios(): String =
        "As pessoas físicas indicadas como rés são sócias entre si no âmbito do grupo econômico familiar; também há indícios de fraude, considerando que os sócios das empresas se configuram como **sócios ocultos** no grupo econômico, gerenciando todas as empresas do referido grupo econômico.\n\n" +
            "Assim, é inegável que os sócios das empresas rés também devem responder a título solidário relativamente aos encargos trabalhistas descumpridos, sob pena de violar o **princípio da dignidade humana e da valorização do trabalho**.\n\n" +
            "Pelo exposto, **REQUER** sejam os sócios condenados solidariamente. __Sucessivamente__, **REQUER** sua condenação a título subsidiário."

    private fun responsabilidadeSubsidiaria(processo: Processo): String {
        val reclamadas = processo.reclamadas.toList()
        if (reclamadas.size < 2) {
            throw BusinessException(
                "O bloco \"Responsabilidade subsidiária\" requer ao menos 2 reclamadas selecionadas."
            )
        }
        val nomePrimeiraRe = reclamadas[0].nome.ifBlank { "não informado" }
        val nomeSegundaRe = reclamadas[1].nome.ifBlank { "não informado" }
        return responsabilidadeSubsidiariaTexto(nomePrimeiraRe, nomeSegundaRe)
    }

    private fun responsabilidadeSubsidiariaTexto(nomePrimeiraRe: String, nomeSegundaRe: String): String =
        buildString {
            append(
                "A parte autora, conquanto tenha sido contratada pela 1ª ré ($nomePrimeiraRe), sempre " +
                    "exerceu seu trabalho em benefício da 2ª ré ($nomeSegundaRe), pelo que se impõe a " +
                    "**responsabilização subsidiária**, pelo vínculo de terceirização entre ambas.\n\n"
            )
            append(
                "A atual redação dada pelo legislador nas Leis nº 13.429/2017 e 13.467/2017 determina a " +
                    "**responsabilização subsidiária** da tomadora, bem como o art. 5º-A, § 5º, da Lei " +
                    "6.019/74, com redação dada pela Lei 13.429/2017 e mantida pela Lei 13.467/2017, "
            )
            append("impõe, categoricamente, a responsabilização subsidiária da tomadora: ")
            append("*Art. 5º-A. Contratante é a pessoa física ou jurídica que celebra contrato com empresa ")
            append("de presta\u00e7\u00e3o de serviços relacionados a quaisquer de suas atividades, ")
            append("inclusive sua atividade principal. (Redação dada pela Lei nº 13.467, de 2017) [...]* ")
            append("__**§ 5º. A empresa contratante é subsidiariamente responsável pelas obrigações ")
            append("trabalhistas referentes ao período em que ocorrer a presta\u00e7\u00e3o de serviços,**__ ")
            append("e o recolhimento das contribuições previdenciárias observará o disposto no art. 31 ")
            append("da Lei nº 8.212, de 24 de julho de 1991.\n\n")
            append("Ainda, a responsabilidade subsidiária do tomador de serviços é resguardada pela Súmula 331 do TST: ")
            append("IV - ***O inadimplemento das obrigações trabalhistas, por parte do empregador, implica a ")
            append("responsabilidade subsidiária do tomador dos serviços quanto àquelas obrigações,*** ")
            append("desde que haja participado da relação processual e conste também do título executivo judicial. ")
            append("VI – A responsabilidade subsidiária do tomador de serviços abrange todas as verbas decorrentes ")
            append("da condenação referentes ao período da presta\u00e7\u00e3o laboral.\n\n")
            append("Pelo exposto, à luz dos **arts. 186 e 927 do Código Civil** (considerando que a ré ")
            append("$nomePrimeiraRe se beneficiou do trabalho prestado pela parte autora, o que atrai a teoria da ")
            append("responsabilidade civil), **REQUER** seja a 2ª ré condenada subsidiariamente.")
        }

    private fun opcaoMotivoExtincao(value: String?): OpcaoMotivoExtincao? =
        OPCOES_MOTIVO_EXTINCAO[value?.trim()]

    private fun formatVariableDate(value: String?): String =
        value?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { runCatching { LocalDate.parse(it).format(DATE_FORMATTER) }.getOrNull() }
            ?: PLACEHOLDER

    private fun formatCurrency(value: String?): String =
        value?.trim()?.takeIf { it.isNotEmpty() }
            ?.let {
                runCatching {
                    NumberFormat.getNumberInstance(Locale("pt", "BR")).apply {
                        minimumFractionDigits = 2
                        maximumFractionDigits = 2
                    }.format(BigDecimal(it))
                }.getOrNull()
            }
            ?: PLACEHOLDER

    private fun String?.orPlaceholder(): String =
        this?.trim()?.takeIf { it.isNotEmpty() } ?: PLACEHOLDER

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
        val titulo: (Map<String, String?>) -> String,
        val generate: (Processo, Set<Usuario>, Map<String, String?>) -> String
    )

    private data class OpcaoMotivoExtincao(val titulo: String, val motivo: String)

    companion object {
        const val QUALIFICACAO_RECLAMANTE = "qualificacao_reclamante"
        const val QUALIFICACAO_RECLAMADA = "qualificacao_reclamada"
        const val DADOS_RECLAMANTE = "dados_reclamante"
        const val CONTRATO_ASPECTOS_GERAIS = "contrato_aspectos_gerais"
        const val BAIXA_CTPS_TUTELA = "baixa_ctps_tutela"
        const val RESPONSABILIDADE_SOLIDARIA_GRUPO_ECONOMICO = "responsabilidade_solidaria_grupo_economico"
        const val RESPONSABILIDADE_SUBSIDIARIA = "responsabilidade_subsidiaria"
        const val LEGITIMIDADE_PASSIVA_SOCIOS = "legitimidade_passiva_socios"
        private val BLOCOS_COM_ANEXOS = setOf(BAIXA_CTPS_TUTELA, RESPONSABILIDADE_SOLIDARIA_GRUPO_ECONOMICO)
        private const val PLACEHOLDER = "___"
        private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        private val OPCOES_MOTIVO_EXTINCAO = mapOf(
            "1" to OpcaoMotivoExtincao("Opção 1.1 – Dispensa sem justa causa", "sem justa causa"),
            "2" to OpcaoMotivoExtincao("Opção 1.2 – Dispensa com justa causa", "com justa causa"),
            "3" to OpcaoMotivoExtincao("Opção 1.3 – Pedido de demissão", "por pedido de demissão"),
            "4" to OpcaoMotivoExtincao("Opção 1.4 – Rescisão indireta", "por rescisão indireta"),
            "5" to OpcaoMotivoExtincao(
                "Opção 1.5 – Reversão do pedido de demissão - Nulidade",
                "por reversão do pedido de demissão"
            )
        )
    }
}
