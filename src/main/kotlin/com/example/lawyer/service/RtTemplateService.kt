package com.example.lawyer.service

import com.example.lawyer.domain.model.Processo
import com.example.lawyer.domain.model.Usuario
import com.example.lawyer.dto.request.RtPreviewRequest
import com.example.lawyer.dto.response.RtPreviewBlockResponse
import com.example.lawyer.dto.response.RtPreviewInlineImageResponse
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
            generate = { processo, advogados, _, _ -> qualificacaoReclamante(processo, advogados) }
        ),
        QUALIFICACAO_RECLAMADA to RtBlockDefinition(
            titulo = { "Qualificação da Reclamada" },
            generate = { processo, _, _, _ -> qualificacaoReclamada(processo) }
        ),
        DADOS_RECLAMANTE to RtBlockDefinition(
            titulo = { "Dados do(a) Reclamante" },
            generate = { processo, _, _, _ -> dadosReclamante(processo) }
        ),
        CONTRATO_ASPECTOS_GERAIS to RtBlockDefinition(
            titulo = { "Contrato de trabalho - Aspectos gerais" },
            generate = { _, _, variaveis, _ -> contratoAspectosGerais(variaveis) }
        ),
        RECONHECIMENTO_VINCULO_EMPREGATICIO to RtBlockDefinition(
            titulo = { "Reconhecimento de vínculo empregatício" },
            generate = { _, _, variaveis, _ -> reconhecimentoVinculoEmpregaticio(variaveis) }
        ),
        PERIODO_SEM_REGISTRO_CTPS to RtBlockDefinition(
            titulo = { variaveis ->
                "Período sem registro (de ${formatVariableDateLong(variaveis["dataInicioPrestacaoServicos"])} " +
                    "à ${formatVariableDateLong(variaveis["dataAnotacaoCtps"])})"
            },
            generate = { _, _, variaveis, _ -> periodoSemRegistroCtps(variaveis) }
        ),
        DANO_MORAL_AUSENCIA_ANOTACAO_CTPS to RtBlockDefinition(
            titulo = { "Dano moral por ausência de anotação da CTPS" },
            generate = { _, _, variaveis, _ -> danoMoralAusenciaAnotacaoCtps(variaveis) }
        ),
        RETENCAO_CTPS_DANO_MORAL to RtBlockDefinition(
            titulo = { "Retenção da CTPS. Dano moral" },
            generate = { _, _, variaveis, _ -> retencaoCtpsDanoMoral(variaveis) }
        ),
        DIFERENCAS_SALARIAIS_PISO_CONVENCIONAL to RtBlockDefinition(
            titulo = { "Diferenças salariais. Piso convencional" },
            generate = { _, _, variaveis, _ -> diferencasSalariaisPisoConvencional(variaveis) }
        ),
        AUSENCIA_PAGAMENTO_VERBAS_RESCISORIAS to RtBlockDefinition(
            titulo = { "Ausência de pagamento das verbas rescisórias" },
            generate = { _, _, variaveis, blocosSelecionados ->
                ausenciaPagamentoVerbasRescisorias(variaveis, blocosSelecionados)
            }
        ),
        DANO_MORAL_AUSENCIA_PAGAMENTO_VERBAS_RESCISORIAS to RtBlockDefinition(
            titulo = { "Dano moral por ausência de pagamento das verbas rescisórias" },
            generate = { _, _, _, _ -> danoMoralAusenciaPagamentoVerbasRescisorias() }
        ),
        CONVERSAO_PEDIDO_DEMISSAO_RESCISAO_INDIRETA to RtBlockDefinition(
            titulo = { "Conversão do pedido de demissão em rescisão indireta" },
            generate = { processo, _, variaveis, _ ->
                conversaoPedidoDemissaoRescisaoIndireta(processo, variaveis)
            }
        ),
        REVERSAO_JUSTA_CAUSA_RESCISAO_INDIRETA to RtBlockDefinition(
            titulo = { "Reversão da justa causa para rescisão indireta" },
            generate = { _, _, variaveis, _ -> reversaoJustaCausaRescisaoIndireta(variaveis) }
        ),
        REVERSAO_JUSTA_CAUSA_DISPENSA_SEM_JUSTA_CAUSA to RtBlockDefinition(
            titulo = { "Reversão da justa causa para dispensa sem justa causa" },
            generate = { _, _, _, _ -> reversaoJustaCausaDispensaSemJustaCausa() }
        ),
        MULTA_ART_477_CLT to RtBlockDefinition(
            titulo = { "Multa do art. 477, § 8º, da CLT" },
            generate = { _, _, _, _ -> multaArt477Clt() }
        ),
        PEDIDO_RESCISAO_INDIRETA to RtBlockDefinition(
            titulo = { "Pedido de rescisão indireta" },
            generate = { _, _, variaveis, _ -> pedidoRescisaoIndireta(variaveis) }
        ),
        RESCISAO_INDIRETA_TUTELA_ANTECIPADA_VERBAS_INCONTROVERSAS to RtBlockDefinition(
            titulo = {
                "Rescisão indireta. Tutela antecipada. Verbas incontroversas " +
                    "(art. 294, parágrafo único, do CPC)"
            },
            generate = { _, _, variaveis, _ ->
                rescisaoIndiretaTutelaAntecipadaVerbasIncontroversas(variaveis)
            }
        ),
        TUTELA_URGENCIA_NATUREZA_CAUTELAR to RtBlockDefinition(
            titulo = { "Tutela de urgência de natureza cautelar. (art. 300 do CPC)" },
            generate = { _, _, _, _ -> tutelaUrgenciaNaturezaCautelar() }
        ),
        DISPENSA_DISCRIMINATORIA_REINTEGRACAO_OU_PAGAMENTO to RtBlockDefinition(
            titulo = { "Dispensa discriminatória. Reintegração OU Pagamento do período de afastamento" },
            generate = { _, _, variaveis, _ ->
                dispensaDiscriminatoriaReintegracaoOuPagamento(variaveis)
            }
        ),
        DISPENSA_DISCRIMINATORIA_DANOS_MORAIS to RtBlockDefinition(
            titulo = { "Dispensa discriminatória. Danos morais" },
            generate = { _, _, _, _ -> dispensaDiscriminatoriaDanosMorais() }
        ),
        DESVIO_FUNCAO_ATIVIDADE_EFETIVAMENTE_EXERCIDA to RtBlockDefinition(
            titulo = { "Desvio de função. Atividade efetivamente exercida pela parte autora" },
            generate = { processo, _, variaveis, _ ->
                desvioFuncaoAtividadeEfetivamenteExercida(processo, variaveis)
            }
        ),
        DIFERENCAS_SALARIAIS_ACUMULO_FUNCOES to RtBlockDefinition(
            titulo = { variaveis ->
                val funcaoContratada = variaveis["funcaoContratada"]
                    ?.takeIf(String::isNotBlank)
                    ?: variaveis["funcaoContrato"]
                val funcaoAcumulada = variaveis["funcaoAcumulada"]
                    ?.takeIf(String::isNotBlank)
                    ?: variaveis["funcaoEfetivamenteExercida"]
                "Diferenças salariais. Exercício de função de " +
                    "${funcaoContratada.orPlaceholder()} e de " + funcaoAcumulada.orPlaceholder()
            },
            generate = { processo, _, variaveis, _ ->
                diferencasSalariaisAcumuloFuncoes(processo, variaveis)
            }
        ),
        DIFERENCAS_SALARIAIS_MOTORISTA_CARRETEIRO_CARREGADOR to RtBlockDefinition(
            titulo = {
                "Diferenças salariais. Exercício de função de motorista carreteiro e " +
                    "de carregador de caminhão"
            },
            generate = { _, _, variaveis, _ ->
                diferencasSalariaisMotoristaCarreteiroCarregador(variaveis)
            },
            paragrafosAlinhadosDireita = setOf(9, 10, 11)
        ),
        SALARIO_A_LATERE to RtBlockDefinition(
            titulo = { "Salário a latere" },
            generate = { _, _, variaveis, _ -> salarioALatere(variaveis) }
        ),
        INTEGRACAO_ALUGUEL_VEICULO_PARTICULAR_NATUREZA_SALARIAL to RtBlockDefinition(
            titulo = { "Integração do aluguel do veículo particular. Natureza salarial" },
            generate = { _, _, variaveis, _ ->
                integracaoAluguelVeiculoParticularNaturezaSalarial(variaveis)
            },
            paragrafosRecuados = setOf(3)
        ),
        DANO_MORAL_ATRASO_SALARIAL to RtBlockDefinition(
            titulo = { "Dano moral por atraso salarial" },
            generate = { _, _, _, _ -> danoMoralAtrasoSalarial() }
        ),
        ADICIONAL_TRANSFERENCIA to RtBlockDefinition(
            titulo = { "Adicional de transferência" },
            generate = { processo, _, variaveis, _ -> adicionalTransferencia(processo, variaveis) },
            paragrafosRecuados = setOf(4)
        ),
        VERBAS_RESCISORIAS_MEDIA_HORAS_EXTRAS_NAO_PAGA to RtBlockDefinition(
            titulo = { "Verbas rescisórias. Média de horas extras não paga" },
            generate = { _, _, _, _ -> verbasRescisoriasMediaHorasExtrasNaoPaga() }
        ),
        JORNADA_TRABALHO to RtBlockDefinition(
            titulo = { "Jornada de trabalho" },
            generate = { _, _, variaveis, _ -> jornadaTrabalho(variaveis) }
        ),
        JORNADA_TRABALHO_HORAS_EXTRAS to RtBlockDefinition(
            titulo = { "a. Horas extras" },
            generate = { _, _, _, _ -> jornadaTrabalhoHorasExtras() }
        ),
        JORNADA_TRABALHO_NULIDADE_BANCO_HORAS to RtBlockDefinition(
            titulo = { "b. Nulidade do banco de horas" },
            generate = { _, _, variaveis, _ -> jornadaTrabalhoNulidadeBancoHoras(variaveis) },
            paragrafosRecuados = (3..8).toSet()
        ),
        JORNADA_TRABALHO_NULIDADE_ACORDO_COMPENSACAO_SEMANA_INGLESA to RtBlockDefinition(
            titulo = { "c. Nulidade do acordo de compensação de jornada (‘‘semana inglesa’’)" },
            generate = { _, _, _, _ -> jornadaTrabalhoNulidadeAcordoCompensacaoSemanaInglesa() },
            paragrafosRecuados = setOf(10, 11, 12)
        ),
        JORNADA_TRABALHO_TURNOS_ININTERRUPTOS_REVEZAMENTO to RtBlockDefinition(
            titulo = { "a. Turnos ininterruptos de revezamento" },
            generate = { _, _, _, _ -> jornadaTrabalhoTurnosIninterruptosRevezamento() },
            paragrafosRecuados = setOf(3)
        ),
        JORNADA_TRABALHO_DIAS_DESCANSO to RtBlockDefinition(
            titulo = { "e. Trabalho em dias de descanso" },
            generate = { _, _, _, _ -> jornadaTrabalhoDiasDescanso() },
            paragrafosRecuados = setOf(4)
        ),
        JORNADA_TRABALHO_ADICIONAL_NOTURNO to RtBlockDefinition(
            titulo = { "f. Adicional noturno" },
            generate = { _, _, variaveis, _ -> jornadaTrabalhoAdicionalNoturno(variaveis) }
        ),
        BAIXA_CTPS_TUTELA to RtBlockDefinition(
            titulo = { "3. Baixa na CTPS física. Tutela antecipada" },
            generate = { _, _, variaveis, _ -> baixaCtpsTutela(variaveis) }
        ),
        RESPONSABILIDADE_SOLIDARIA_GRUPO_ECONOMICO to RtBlockDefinition(
            titulo = { "Responsabilidade solidária. Grupo econômico" },
            generate = { _, _, variaveis, _ -> responsabilidadeSolidariaGrupoEconomico(variaveis) }
        ),
        RESPONSABILIDADE_SUBSIDIARIA to RtBlockDefinition(
            titulo = { "Responsabilidade subsidiária" },
            generate = { processo, _, _, _ -> responsabilidadeSubsidiaria(processo) }
        ),
        RESPONSABILIDADE_SUBSIDIARIA_CONTRATO_ADMINISTRATIVO to RtBlockDefinition(
            titulo = { "Responsabilidade subsidiária. Contrato administrativo" },
            generate = { processo, _, variaveis, _ -> responsabilidadeSubsidiariaContratoAdministrativo(processo, variaveis) }
        ),
        LEGITIMIDADE_PASSIVA_SOCIOS to RtBlockDefinition(
            titulo = { "Legitimidade passiva dos sócios das rés" },
            generate = { _, _, _, _ -> legitimidadePassivaSocios() }
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
        val blocosSelecionados = request.blocosSelecionados.map { it.trim() }.toSet()
        return orderedSelectedBlockIds(request.blocosSelecionados)
            .mapNotNull { blockId ->
                blockDefinitions[blockId]?.let { definition ->
                    val variaveis = variaveisAutomaticas(processo) +
                        variaveisDoBloco(processo, blockId) +
                        normalizeVariables(request.dadosVariaveis)
                    RtPreviewBlockResponse(
                        id = blockId,
                        titulo = definition.titulo(variaveis),
                        texto = definition.generate(processo, advogados, variaveis, blocosSelecionados),
                        anexos = if (blockId in BLOCOS_COM_ANEXOS) {
                            request.processoId?.let { processoAnexoService.list(it, blockId) }.orEmpty()
                        } else {
                            emptyList()
                        },
                        imagensFixas = fixedImages(blockId),
                        paragrafosAlinhadosDireita = definition.paragrafosAlinhadosDireita.sorted(),
                        paragrafosRecuados = definition.paragrafosRecuados.sorted()
                    )
                }
            }
    }

    private fun orderedSelectedBlockIds(selected: List<String>): List<String> {
        val ordered = selected.map(String::trim).filter(String::isNotEmpty).distinct().toMutableList()
        if (DANO_MORAL_AUSENCIA_ANOTACAO_CTPS in ordered && RETENCAO_CTPS_DANO_MORAL in ordered) {
            ordered.remove(RETENCAO_CTPS_DANO_MORAL)
            ordered.add(ordered.indexOf(DANO_MORAL_AUSENCIA_ANOTACAO_CTPS) + 1, RETENCAO_CTPS_DANO_MORAL)
        }
        if (REVERSAO_JUSTA_CAUSA_DISPENSA_SEM_JUSTA_CAUSA in ordered && MULTA_ART_477_CLT in ordered) {
            ordered.remove(MULTA_ART_477_CLT)
            ordered.add(ordered.indexOf(REVERSAO_JUSTA_CAUSA_DISPENSA_SEM_JUSTA_CAUSA) + 1, MULTA_ART_477_CLT)
        }
        if (
            DISPENSA_DISCRIMINATORIA_REINTEGRACAO_OU_PAGAMENTO in ordered &&
            DISPENSA_DISCRIMINATORIA_DANOS_MORAIS in ordered
        ) {
            ordered.remove(DISPENSA_DISCRIMINATORIA_DANOS_MORAIS)
            val position = ordered.indexOf(DISPENSA_DISCRIMINATORIA_REINTEGRACAO_OU_PAGAMENTO) + 1
            ordered.add(position, DISPENSA_DISCRIMINATORIA_DANOS_MORAIS)
        }
        orderBlockFamily(ordered, JORNADA_TRABALHO_BLOCK_ORDER)
        return ordered
    }

    private fun orderBlockFamily(ordered: MutableList<String>, familyOrder: List<String>) {
        val selectedFamily = familyOrder.filter(ordered::contains)
        if (selectedFamily.isEmpty()) return
        val insertionIndex = selectedFamily.minOf(ordered::indexOf)
        ordered.removeAll(selectedFamily.toSet())
        ordered.addAll(insertionIndex, selectedFamily)
    }

    private fun fixedImages(blockId: String): List<RtPreviewInlineImageResponse> =
        when (blockId) {
            RETENCAO_CTPS_DANO_MORAL -> listOf(
                RtPreviewInlineImageResponse(
                    url = RETENCAO_CTPS_IMAGE_URL,
                    contentType = "image/png",
                    nomeOriginal = RETENCAO_CTPS_IMAGE_NAME,
                    afterParagraph = 2
                )
            )
            MULTA_ART_477_CLT -> listOf(
                RtPreviewInlineImageResponse(
                    url = MULTA_ART_477_IMAGE_URL,
                    contentType = "image/png",
                    nomeOriginal = MULTA_ART_477_IMAGE_NAME,
                    afterParagraph = 1
                )
            )
            JORNADA_TRABALHO_DIAS_DESCANSO -> listOf(
                RtPreviewInlineImageResponse(
                    url = TRABALHO_DIAS_DESCANSO_IMAGE_URL,
                    contentType = "image/png",
                    nomeOriginal = TRABALHO_DIAS_DESCANSO_IMAGE_NAME,
                    afterParagraph = 5
                )
            )
            DIFERENCAS_SALARIAIS_MOTORISTA_CARRETEIRO_CARREGADOR -> listOf(
                RtPreviewInlineImageResponse(
                    url = MOTORISTA_CARRETEIRO_IMAGE_1_URL,
                    contentType = "image/png",
                    nomeOriginal = MOTORISTA_CARRETEIRO_IMAGE_1_NAME,
                    afterParagraph = 7,
                    caption = MOTORISTA_CARRETEIRO_IMAGE_1_SOURCE
                ),
                RtPreviewInlineImageResponse(
                    url = MOTORISTA_CARRETEIRO_IMAGE_2_URL,
                    contentType = "image/png",
                    nomeOriginal = MOTORISTA_CARRETEIRO_IMAGE_2_NAME,
                    afterParagraph = 7,
                    caption = MOTORISTA_CARRETEIRO_IMAGE_2_SOURCE
                )
            )
            else -> emptyList()
        }

    fun rightAlignedParagraphs(blockId: String?): List<Int> =
        blockId?.let { blockDefinitions[it]?.paragrafosAlinhadosDireita?.sorted() }.orEmpty()

    fun indentedParagraphs(blockId: String?): List<Int> =
        blockId?.let { blockDefinitions[it]?.paragrafosRecuados?.sorted() }.orEmpty()

    private fun variaveisDoBloco(processo: Processo, blocoId: String): Map<String, String?> =
        processo.dadosVariaveis
            .asSequence()
            .filter { it.blocoId == blocoId }
            .associate { it.campo to it.valor }

    private fun variaveisAutomaticas(processo: Processo): Map<String, String?> = mapOf(
        "funcaoContrato" to processo.contratoTrabalho?.funcaoExercida,
        "dataAdmissao" to processo.contratoTrabalho?.dataAdmissao?.toString(),
        "remuneracao" to processo.contratoTrabalho?.ultimaRemuneracao?.toPlainString()
    ).filterValues { !it.isNullOrBlank() }

    private fun normalizeVariables(variables: Map<String, Any?>): Map<String, String?> =
        variables.mapValues { (name, value) ->
            if (name == "sitesEncerramentoAtividades") formatStringList(value) else value?.toString()
        }

    private fun formatStringList(value: Any?): String {
        val items = when (value) {
            is Collection<*> -> value.mapNotNull { it?.toString()?.trim() }
            is Array<*> -> value.mapNotNull { it?.toString()?.trim() }
            is String -> listOf(value.trim())
            else -> emptyList()
        }.filter(String::isNotEmpty)

        return when (items.size) {
            0 -> "___"
            1 -> items.first()
            2 -> items.joinToString(" e ")
            else -> items.dropLast(1).joinToString(", ") + " e " + items.last()
        }
    }

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

    private fun reconhecimentoVinculoEmpregaticio(variaveis: Map<String, String?>): String {
        val motivoNaoEventualidade = variaveis["motivoNaoEventualidade"].orPlaceholder()
        val motivoOnerosidade = variaveis["motivoOnerosidade"].orPlaceholder()
        val motivoSubordinacao = variaveis["motivoSubordinacao"].orPlaceholder()
        val dataInicio = formatVariableDate(variaveis["dataInicioVinculo"])
        val dataFim = formatVariableDate(variaveis["dataFimVinculo"])

        return "A parte autora prestou serviços em favor da parte ré com todos os elementos do **art. 3º da CLT**, mas não foi realizada a formalização do vínculo empregatício, mediante anotação da CTPS e, consequentemente, com violação aos direitos correspondentes.\n\n" +
            "Exercia seu trabalho, enquanto pessoa física e mediante pessoalidade, com:\n\n" +
            "• não eventualidade, $motivoNaoEventualidade;\n\n" +
            "- a título de onerosidade, $motivoOnerosidade; e\n\n" +
            "- havia subordinação, pois $motivoSubordinacao.\n\n" +
            "Pelo exposto, com fundamento no **art. 3º da CLT**, **REQUER-SE** o reconhecimento do vínculo empregatício entre a parte autora e a parte ré, relativamente ao período de $dataInicio até $dataFim, bem como a condenação da ré para que proceda à anotação do referido período na CTPS da parte autora; em caso de descumprimento da obrigação, seja arbitrada multa diária, revertida em favor da parte autora, em valor a ser estabelecido por este Juízo, e, neste caso, sejam realizadas as anotações pela Secretaria desta Vara do Trabalho, nos termos do art. 39, § 1º, da CLT.\n\n" +
            "**REQUER-SE**, ainda, em virtude do período sem registro, a condenação da parte ré ao pagamento de todos os consectários legais do vínculo empregatício, quais sejam: 13º salário, férias + 1/3, depósitos mensais de FGTS mais a multa rescisória de 40% e aviso prévio, bem como indenização substitutiva relativa ao seguro-desemprego."
    }

    private fun periodoSemRegistroCtps(variaveis: Map<String, String?>): String {
        val dataAnotacaoCtps = formatVariableDate(variaveis["dataAnotacaoCtps"])
        val dataInicioPrestacaoServicos = formatVariableDate(variaveis["dataInicioPrestacaoServicos"])

        return "Conquanto a ré tenha anotado a CTPS da parte autora apenas em $dataAnotacaoCtps, sua prestação de serviços teve início antes, em $dataInicioPrestacaoServicos. Durante o intervalo sem registro, a parte autora laborou exercendo as **mesmas funções e cumprindo os mesmos horários e dias de trabalho** relativamente ao período com registro.\n\n" +
            "Observa-se violação aos **arts. 29 e 40 da CLT**, e, por força da **Súmula n.º 12 do TST** (As anotações apostas pelo empregador na carteira profissional do empregado não geram presunção \"juris et de jure\", mas apenas \"juris tantum\".), conquanto as anotações na carteira de trabalho possuam presunção de veracidade, podem ser elididas por prova em contrário, como no presente caso.\n\n" +
            "Pelo exposto, **REQUER-SE** o reconhecimento da existência de vínculo de emprego no período sem registro, com a consequente condenação da ré em obrigação de fazer, para que retifique a CTPS da parte autora, sob pena de multa diária no valor de R$ 1.000,00, revertida em favor da parte autora, ou em valor a ser estabelecido por este Juízo, e, neste caso, sejam realizadas as anotações pela Secretaria desta Vara do Trabalho, nos termos do art. 39, § 1º, da CLT.\n\n" +
            "Consequentemente, **REQUER-SE,** em virtude do período sem registro, a condenação da parte ré ao pagamento das diferenças de todos os consectários legais do vínculo empregatício: 13ºs salários, férias com 1/3, FGTS acrescido da multa de 40%."
    }

    private fun danoMoralAusenciaAnotacaoCtps(variaveis: Map<String, String?>): String {
        val descricaoDano = variaveis["descricaoDanoMoralCtps"]
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        return buildString {
            append("Há dano moral pela não anotação da CTPS. Ao sonegar esse direito fundamental, a ré gerou angústia à parte autora, que, por exemplo, mediante doença ou óbito, deixaria a si e sua família desamparados.\n\n")
            append("Nesse sentido, o TRT-1 já decidiu:\n\n")
            append("**Tribunal Regional do Trabalho da 1ª Região**\n")
            append("RECURSO ORDINÁRIO. DANO MORAL. NÃO ANOTAÇÃO CTPS. __**A não anotação da CTPS do empregado implica na sonegação de direitos elementares do empregado que produzem dano moral tanto pelo aspecto econômico já que impede o acesso a bens essenciais à subsistência, bem como pela intensa sujeição a que se submete o trabalhador sem uma rede social que o proteja (FGTS, seguro-desemprego, previdência social)**__. (TRT-1 - RO: 01005510920195010021 RJ, Relator: ANA MARIA SOARES DE MORAES, Data de Julgamento: 06/09/2021, Primeira Turma, Data de Publicação: 01/10/2021)\n")
            append("(grifo nosso)\n\n")
            append("Pelo exposto, com fundamento no **art. 5º, X, da Constituição Federal** e nos **arts. 186 e 927 do Código Civil, REQUER-SE** a condenação da parte ré ao pagamento de indenização a título de danos morais.")
            descricaoDano?.let { append("\n\n").append(it) }
        }
    }

    private fun retencaoCtpsDanoMoral(variaveis: Map<String, String?>): String {
        val dataAssinaturaCarteira = formatVariableDate(variaveis["dataAssinaturaCarteira"])

        return "A CTPS da parte autora ficou retida pela ré, que formalizou o vínculo de emprego, assinando a " +
            "CTPS, somente em $dataAssinaturaCarteira, violando o art. 29 da CLT.\n\n" +
            "O Tribunal Superior do Trabalho firmou tese vinculante a respeito de ser devida indenização por " +
            "danos morais, por presunção, quando a CTPS é retida injustificadamente pelo empregador além do " +
            "tempo previsto na CLT:\n\n" +
            "Pelo exposto, com fundamento no art. 29 da CLT e no Tema 192 do Tribunal Superior do Trabalho, " +
            "**REQUER-SE** a condenação da ré ao pagamento de danos morais."
    }

    private fun diferencasSalariaisPisoConvencional(variaveis: Map<String, String?>): String {
        val cctReferencia = variaveis["cctReferencia"].orPlaceholder()

        return "O salário pago à parte autora era inferior ao piso convencional, conforme CCT $cctReferencia:\n\n" +
            "Pelo exposto, diante da afronta ao **art. 7º, V, da Constituição Federal**, **REQUER-SE** a condenação da ré ao pagamento das diferenças salariais, considerando-se o piso salarial previsto nas convenções coletivas indicadas, com a consequente condenação da ré ao pagamento dos reflexos em RSR e, com estes, em férias + 1/3, 13º salários, FGTS + multa de 40%, aviso prévio, horas extras, adicional noturno e adicional de periculosidade."
    }

    private fun ausenciaPagamentoVerbasRescisorias(
        variaveis: Map<String, String?>,
        blocosSelecionados: Set<String>
    ): String = buildList {
        add("A parte ré não pagou as verbas rescisórias devidas, o que impõe a sua condenação, conforme exposto a seguir:")

        if (VERBAS_RESCISORIAS_AVISO_PREVIO in blocosSelecionados) {
            add("Diante do não pagamento do aviso prévio à parte autora, com fundamento no art. 487 da CLT, **REQUER-SE** a condenação da ré ao pagamento do aviso prévio (${variaveis["qtdDiasAviso"].orPlaceholder()}).")
        }
        if (VERBAS_RESCISORIAS_FERIAS in blocosSelecionados) {
            add("Nos termos do art. 7º, XVII, da Constituição Federal, **REQUER-SE** a condenação da ré ao pagamento de férias proporcionais + 1/3 (${variaveis["detalheFerias"].orPlaceholder()}).")
        }
        if (VERBAS_RESCISORIAS_DECIMO_TERCEIRO in blocosSelecionados) {
            add("Nos termos do art. 7º, VIII, da Constituição Federal, **REQUER-SE** a condenação da ré ao pagamento do 13º salário proporcional (${variaveis["detalheDecimoTerceiro"].orPlaceholder()}).")
        }
        if (VERBAS_RESCISORIAS_MULTA_FGTS in blocosSelecionados) {
            add("Com fundamento no art. 10, I, do Ato das Disposições Constitucionais Transitórias, **REQUER-SE** a condenação da ré ao pagamento da multa de 40% do FGTS.")
        }
        if (VERBAS_RESCISORIAS_MULTAS_467_477 in blocosSelecionados) {
            add("Diante do não pagamento das verbas rescisórias à parte autora, **REQUER-SE** a condenação da ré ao pagamento das multas do **art. 467 da CLT** e do **art. 477, § 8º, da CLT**.")
        }
    }.joinToString("\n\n")

    private fun danoMoralAusenciaPagamentoVerbasRescisorias(): String =
        """
        Diante do não pagamento das verbas rescisórias, fica evidente a existência de dano à parte autora pela impossibilidade de utilização dos seus valores rescisórios, situação gerada por culpa exclusiva da ré.

        Sobre o reconhecimento de danos morais em razão de não pagamento de verbas rescisórias, assim já julgaram os TRTs da 4ª, 17ª e 18ª Regiões:

        **TRT da 4ª Região**
        DANO MORAL. INADIMPLEMENTO DAS VERBAS RESCISÓRIAS. __**A ausência de pagamento das verbas rescisórias impõe, por si só, o dever de indenizar com fundamento extrapatrimonial. O dano moral é presumido (in re ipsa), nascendo do próprio ilícito praticado.**__ (TRT-4 - ROT: 00212003820165040011, Data de Julgamento: 07/11/2019, 1ª Turma)
        (grifo nosso)

        **TRT da 4ª Região**
        DANO MORAL. NÃO PAGAMENTO DAS VERBAS RESCISÓRIAS. INDENIZAÇÃO DEVIDA. __**O abalo moral deve ser presumido quando comprovado o não pagamento do quanto devido pelo empregador em relação às parcelas principal e acessórias,**__ bem como no que concerne à ausência da quitação das obrigações devidas no momento da rescisão do contrato. __**Comprovado o atraso no pagamento das parcelas rescisórias de forma a gerar danos morais ao trabalhador.**__ Recurso da reclamante provido. (TRT-4 - ROT: 00207204520205040003, Data de Julgamento: 05/05/2022, 2ª Turma)
        (grifo nosso)

        TRT da 17ª Região
        __**DANOS MORAIS, VERBAS RESCISÓRIAS. É devida indenização por danos morais no caso de dispensa sem o pagamento das verbas rescisórias. Súmula 46 do TRT da 17ª Região.**__ (TRT-17 - RO: 00003791920175170010, Relator: JAILSON PEREIRA DA SILVA, Data de Julgamento: 03/06/2019, Data de Publicação: 27/06/2019)
        (grifo nosso)

        **TRT da 18ª Região**
        AUSÊNCIA DE PAGAMENTO DAS VERBAS RESCISÓRIAS. DANO MORAL. __**O dano moral revela-se evidente e presumível pela ausência de pagamento das verbas rescisórias,**__ em razão da __**inconteste violação ao princípio da dignidade da pessoa humana, ultrapassando a seara de meros dissabores.**__ Não se trata do atraso no pagamento previsto na Súmula 49 deste Regional, mas sim da completa ausência de pagamento destas, __**acarretando o dever de indenizar.**__ (TRT18, RORSum - 0010605-26.2020.5.18.0083, Rel. GENTIL PIO DE OLIVEIRA, 1ª TURMA, 16/04/2021)

        No mesmo sentido, já decidiu o TRT-3:

        **TRT da 3ª Região**
        INDENIZAÇÃO POR DANO MORAL. ATRASO NO PAGAMENTO DAS VERBAS RESCISÓRIAS E RECOLHIMENTO DO FGTS. A mora no pagamento de verbas rescisórias e recolhimento do FGTS, inequivocamente, constitui lesão de ordem emocional. Não há dúvida de que o atraso injustificado do acerto rescisório __**acarreta sérios transtornos na vida do trabalhador que, além de perder o seu emprego, fonte de sua subsistência e de sua família, não pode contar com os valores da sua rescisão para garantir a sua sobrevivência até encontrar um novo emprego.**__ Tal situação gera um estado emocional instável para o trabalhador que não sabe como honrará os seus compromissos. A ausência do pagamento certamente que impõe ao trabalhador situações que afetam a sua dignidade, porquanto viola a sua subsistência e condições de uma vida digna, dada a impossibilidade de arcar com necessidades elementares de sua família. __**Nesse sentido, o dano moral se apresenta, in re ipsa. Emergem daí o nexo de causalidade, o dano e a culpa das reclamadas no evento danoso, configurando-se os elementos componentes da responsabilidade civil.**__ Sob este enfoque, a indenização por dano moral é devida. (TRT-3 - RO: 00102534320195030173 MG 0010253-43.2019.5.03.0173, Relator: Adriana Goulart de Sena Orsini, Data de Julgamento: 02/02/2022, Primeira Turma, Data de Publicação: 11/02/2022.)
        """.trimIndent()

    private fun conversaoPedidoDemissaoRescisaoIndireta(
        processo: Processo,
        variaveis: Map<String, String?>
    ): String {
        val nomeRe = processo.reclamadas.firstOrNull()?.nome.orPlaceholder()
        val descricaoFaltaGrave = variaveis["descricaoFaltaGrave"].orPlaceholder()

        return """
            A parte ré $nomeRe ($descricaoFaltaGrave). Tal situação não deixou alternativa para o autor a não ser pedir demissão.

            Há fundamento para a **rescisão indireta** do contrato de trabalho, nos termos do **art. 483, alínea d, da CLT**, tendo em vista o descumprimento das obrigações do contrato de trabalho.

            Frise-se que, conforme entendimento do TST, a existência de pedido de demissão não obsta a rescisão indireta, uma vez que a **caracterização da falta grave é suficiente para a conversão**, porquanto se presume a relação entre o descumprimento contratual patronal e a causa de extinção do contrato, sendo **desnecessária a prova de vício de consentimento no pedido de demissão:**

            **6ª Turma do TST**
            [...] CONVERSÃO DO PEDIDO DE DEMISSÃO EM RESCISÃO INDIRETA. INADIMPLEMENTO DAS PARCELAS DO FGTS. REQUISITOS DO ART. 896, § 1º-A, DA CLT, ATENDIDOS. Trata-se de debate acerca da possibilidade de conversão do pedido de demissão em rescisão indireta, diante do comprovado descumprimento contratual pelo empregador (inadimplemento das parcelas do FGTS). No Direito do Trabalho, o atraso reiterado no pagamento dos salários, bem como a irregularidade no recolhimento do FGTS, denota o não cumprimento das obrigações por parte do empregador e, portanto, enseja a rescisão contratual pelo empregado, nos termos do art. 483, d, da CLT. Ademais, esta Corte tem reiteradamente decidido pela relativização do requisito da imediatidade no tocante à rescisão indireta, em observância aos princípios da continuidade da prestação laboral e da proteção ao hipossuficiente. __**Por fim, é firme, na jurisprudência, o posicionamento de que o pedido de demissão do empregado, ainda que homologado pelo sindicato da categoria profissional, não obsta a configuração da rescisão indireta.**__ O art. 483, caput e § 3º, da CLT, faculta ao empregado considerar rescindido o contrato de trabalho antes de pleitear em juízo as verbas decorrentes da rescisão indireta. Todavia, __**o referido dispositivo não estabelece o procedimento a ser adotado pelo empregado quando o empregador incidir em um dos casos de justa causa. Vale dizer, não há qualquer exigência formal para o exercício da opção de se afastar do emprego antes do ajuizamento da respectiva ação trabalhista. Assim, no presente caso concreto, o pedido de demissão da obreira demonstra tão somente a impossibilidade de manutenção do vínculo empregatício, sem significar qualquer opção pela modalidade de extinção contratual. Comprovada em juízo a justa causa do empregador (o inadimplemento das parcelas do FGTS), presume-se a relação entre a falta patronal e a iniciativa da empregada de rescindir o contrato de trabalho.**__ E não há, no quadro fático delineado pelo TRT, qualquer indício de que tenha sido outro o motivo do desligamento da reclamante. Recurso de revista conhecido e provido. (RRAg-20815-32.2021.5.04.0006, 6ª Turma, Relator Desembargador Convocado Fabio Tulio Correia Ribeiro, DEJT 27/10/2023)
            (grifo nosso)

            Pelo exposto, **REQUER-SE** a conversão da modalidade da extinção contratual, com o reconhecimento de rescisão indireta e a consequente condenação da ré ao pagamento das verbas devidas nesse tipo de rescisão, quais sejam aviso-prévio proporcional ao tempo de serviço, férias integrais e proporcionais + 1/3, décimo terceiro salário proporcional e FGTS + multa de 40%. Consequentemente, **REQUER-SE** a liberação das guias complementares para saque de FGTS e seguro-desemprego, sob pena de multa diária no importe de R$ 1.000,00, ou outro valor a ser arbitrado por este Juízo, sem prejuízo da emissão de alvará judicial.
        """.trimIndent()
    }

    private fun reversaoJustaCausaRescisaoIndireta(variaveis: Map<String, String?>): String {
        val motivoJustaCausa = variaveis["motivoJustaCausa"].orPlaceholder()

        return """
            A ré, de modo habitual, $motivoJustaCausa.

            Logo, há fundamento para a **rescisão indireta** do contrato de trabalho, nos termos do **art. 483, alínea c, da CLT**, tendo em vista o descumprimento das obrigações do contrato de trabalho.

            Pelo exposto, **REQUER** seja declarada a nulidade da justa causa aplicada pela ré, com o __reconhecimento de rescisão indireta__, com a consequente condenação da ré ao pagamento das verbas devidas nesse tipo de rescisão, quais sejam aviso-prévio proporcional ao tempo de serviço, férias integrais e proporcionais + 1/3, décimo terceiro salário proporcional e FGTS + multa de 40%. Consequentemente, **REQUER-SE** a liberação das guias complementares para saque de FGTS e seguro-desemprego, sob pena de multa diária no importe de R$ 1.000,00, ou outro valor a ser arbitrado por este Juízo, sem prejuízo da emissão de alvará judicial.

            __Sucessivamente__, **REQUER** seja declarada a nulidade da justa causa aplicada pela ré, com sua __conversão para dispensa sem justa causa__, com a consequente condenação da ré ao pagamento das verbas devidas nesse tipo de rescisão, quais sejam aviso-prévio proporcional ao tempo de serviço, férias integrais e proporcionais + 1/3, décimo terceiro salário proporcional e FGTS + multa de 40%. Consequentemente, **REQUER** seja determinado à ré a expedição das guias para saque do FGTS e do seguro desemprego, sob pena de multa diária, no importe de R$ 1.000,00, ou outro valor a ser arbitrado por este Juízo, sem prejuízo da emissão de alvará judicial.
        """.trimIndent()
    }

    private fun reversaoJustaCausaDispensaSemJustaCausa(): String =
        "Pelo exposto, **REQUER-SE** a reversão da justa causa para dispensa sem justa causa, com a " +
            "consequente condenação da ré ao pagamento das verbas devidas nesse tipo de dispensa, quais " +
            "sejam aviso-prévio proporcional ao tempo de serviço, férias integrais e proporcionais + 1/3, " +
            "décimo terceiro salário proporcional e FGTS + multa de 40%. Consequentemente, **REQUER-SE** a " +
            "liberação das guias para saque de FGTS e seguro-desemprego, sob pena de multa diária, no importe " +
            "de R$ 1.000,00, ou outro valor a ser arbitrado por este Juízo, sem prejuízo da emissão de alvará judicial."

    private fun multaArt477Clt(): String =
        "O Tribunal Superior do Trabalho firmou tese vinculante a respeito de ser devida a multa do art. " +
            "477 da CLT quando é revertida em Juízo a dispensa por justa causa:\n\n" +
            "Pelo exposto, com fundamento no Tema 71 do TST, **REQUER-SE** a condenação da ré ao pagamento " +
            "da multa do **art. 477, § 8º, da CLT**."

    private fun pedidoRescisaoIndireta(variaveis: Map<String, String?>): String {
        val justificativa = variaveis["justificativaRescisaoIndireta"].orPlaceholder()

        return "Considerando $justificativa, fica evidente o descumprimento de obrigações " +
            "legais e contratuais por parte da ré.\n\n" +
            "Pelo exposto, **REQUER-SE** o reconhecimento da __rescisão indireta__ do contrato de " +
            "trabalho, com a consequente condenação da ré ao pagamento das verbas devidas nesse tipo " +
            "de rescisão, quais sejam saldo de salário, aviso-prévio proporcional ao tempo de serviço, " +
            "férias integrais e proporcionais + 1/3, décimo terceiro salário proporcional e FGTS + " +
            "multa de 40%. Consequentemente, **REQUER-SE** a liberação das guias complementares para " +
            "saque de FGTS e seguro-desemprego, sob pena de multa diária no importe de R$ 1.000,00, " +
            "ou outro valor a ser arbitrado por este Juízo, sem prejuízo da emissão de alvará judicial.\n\n" +
            "__Sucessivamente__, **REQUER-SE** o reconhecimento de __pedido de demissão__ do autor, " +
            "com a consequente condenação da ré ao pagamento das verbas devidas nesse tipo de rescisão, " +
            "quais sejam saldo de salário, férias integrais e proporcionais + 1/3, décimo terceiro " +
            "salário proporcional e FGTS."
    }

    private fun rescisaoIndiretaTutelaAntecipadaVerbasIncontroversas(
        variaveis: Map<String, String?>
    ): String {
        val sites = variaveis["sitesEncerramentoAtividades"].orPlaceholder()

        return "A parte autora pretende o reconhecimento de sua rescisão indireta, pelos descumprimentos " +
            "contratuais expostos no tópico anterior, pelo que é incontroverso existirem verbas rescisórias " +
            "devidas pela ré, se não aquelas próprias da rescisão indireta, ao menos as atinentes à extinção " +
            "do vínculo empregatício por pedido de demissão (pedido sucessivo, formulado no tópico anterior).\n\n" +
            "Pelo exposto, com fundamento no **art. 294, parágrafo único, do Código de Processo Civil**, " +
            "sendo incontroverso que a parte autora tem direito a verbas rescisórias, **REQUER** seja " +
            "determinado à ré que efetue o pagamento das verbas rescisórias à parte autora, no prazo de 10 " +
            "dias, na modalidade de pedido de demissão (saldo de salário, FGTS, 13º salário e férias), sem " +
            "prejuízo de sua complementação na hipótese de acolhimento do pedido de reconhecimento da " +
            "rescisão indireta, sob pena de execução imediata do valor estimado indicado nesta petição " +
            "inicial como devido.\n\n" +
            "Sucessivamente, considerando que a ré está em vias de encerramento de suas atividades " +
            "empresariais (conforme noticiado pelos seguintes sites: $sites), **REQUER** seja concedida " +
            "tutela de urgência, nos termos do **art. 300 do CPC**, para que seja efetuada a penhora " +
            "eletrônica de ativos financeiros em contas bancárias de titularidade da ré por meio do sistema " +
            "SISBAJUD, com repetição programada (\"Teimosinha\"), bem como seja determinada a " +
            "indisponibilidade de bens imóveis por meio do convênio CNIB e o bloqueio de circulação dos " +
            "veículos por meio do RENAJUD, __até o limite do valor estimado atribuído a este pedido.__\n\n" +
            "__No mérito__, **REQUER-SE** a confirmação do pedido de tutela antecipada, com o seu integral " +
            "acolhimento."
    }

    private fun tutelaUrgenciaNaturezaCautelar(): String =
        "Conforme indicado no tópico anterior, a ré está em vias de encerramento de suas atividades, " +
            "pelo que, para garantir a futura execução, **REQUER** seja concedida tutela de urgência, " +
            "nos termos do **art. 300 do CPC**, para que seja efetuada a penhora eletrônica de ativos " +
            "financeiros em contas bancárias de titularidade da ré por meio do sistema SISBAJUD, com " +
            "repetição programada (\"Teimosinha\"), bem como seja determinada a indisponibilidade de bens " +
            "imóveis por meio do convênio CNIB e o bloqueio de circulação dos veículos por meio do RENAJUD, " +
            "__até o limite do valor estimado atribuído a esta ação judicial__.\n\n" +
            "__No mérito__, **REQUER-SE** a confirmação do pedido de tutela de urgência, com o seu " +
            "integral acolhimento."

    private fun dispensaDiscriminatoriaReintegracaoOuPagamento(
        variaveis: Map<String, String?>
    ): String {
        val condicao = variaveis["condicaoDiscriminacao"].orPlaceholder()
        val comoFicouProvado = variaveis["comoFicouProvado"].orPlaceholder()
        val incluirJurisprudencia =
            variaveis["incluirJurisprudenciaDoenca"]?.toBooleanStrictOrNull() ?: false
        val opcaoDesfecho = variaveis["opcaoDesfecho"]?.trim()?.lowercase()
            ?: throw BusinessException(
                "Selecione o desfecho da dispensa discriminatória: reintegração ou pagamento em dobro"
            )

        val paragrafos = mutableListOf(
            "A parte autora é $condicao e a parte ré tinha conhecimento disso no momento da dispensa. " +
                "Conforme $comoFicouProvado, a parte autora foi dispensada por ser $condicao:",
            "De modo algum essa dispensa está relacionada a uma qualificação exigida para o cargo, mas, " +
                "sim, em discriminação da parte ré com o fato de a parte autora ser $condicao.",
            "O **art. 1º da Lei n.º 9.029/1995** prevê que *É proibida a adoção de qualquer prática " +
                "discriminatória e limitativa para efeito de acesso à relação de trabalho, ou de sua " +
                "manutenção, por motivo de sexo, origem, raça, cor, estado civil, situação familiar, " +
                "deficiência, reabilitação profissional, idade, entre outros, ressalvadas, nesse caso, as " +
                "hipóteses de proteção à criança e ao adolescente previstas no inciso XXXIII do art. 7º da " +
                "Constituição Federal.*",
            "No mesmo sentido, o **art. 3º, IV, da Constituição Federal** prevê que, dentre outros, constitui " +
                "objetivo fundamental da República Federativa do Brasil: *promover o bem de todos, sem " +
                "preconceitos de origem, raça, sexo, cor, idade e quaisquer outras formas de discriminação.*",
            "E o **art. 5º, XLI, da Constituição Federal** preceitua que *a lei punirá qualquer discriminação " +
                "atentatória dos direitos e liberdades fundamentais.*",
            "Com relação à consequência jurídica pela conduta discriminatória, o art. 4º, I e II, da Lei n.º " +
                "9.029/1995 prevê que O rompimento da relação de trabalho por ato discriminatório, nos moldes " +
                "desta Lei, além do direito à reparação pelo dano moral, **faculta ao empregado optar** entre: " +
                "I - **a reintegração com ressarcimento integral de todo o período de afastamento**, mediante " +
                "pagamento das remunerações devidas, corrigidas monetariamente e acrescidas de juros legais; " +
                "II - a **percepção, em dobro, da remuneração do período de afastamento**, corrigida " +
                "monetariamente e acrescida dos juros legais."
        )

        if (incluirJurisprudencia) {
            paragrafos += listOf(
                "A **Súmula n.º 443** do TST prevê: *presume-se discriminatória a despedida de empregado " +
                    "portador do vírus HIV ou de outra doença grave que suscite estigma ou preconceito. " +
                    "Inválido o ato, o empregado tem direito à reintegração no emprego.*",
                "A 2ª turma do Tribunal Superior do Trabalho, no processo Ag-AIRR - " +
                    "561-94.2019.5.13.0026, entendeu que: *\"A autora, desde novembro de 2018, encontra-se " +
                    "acometida de patologia na coluna cervical, bem como necessitou se afastar das suas " +
                    "atividades por um período contínuo, iniciado antes do término do aviso prévio, de modo* " +
                    "***que não há dúvidas de que ela foi demitida no momento que se encontrava enferma.*** " +
                    "*Evidencia-se dos depoimentos colhidos, em audiência, que ela tinha total conhecimento " +
                    "das enfermidades que a acometia, antes mesmo de conceder o aviso prévio, o* " +
                    "***que configura a alegada dispensa discriminatória. Diante dos referidos relatos, " +
                    "vislumbra-se a natureza discriminatória e ofensiva do ato patronal, em demitir a " +
                    "reclamante, no momento em que passava por sérias dificuldades em sua saúde.\"*** " +
                    "(grifo nosso).",
                "O acórdão ainda ressalta que: **\"A dispensa discriminatória, nos termos da Lei n. 9.029/95, " +
                    "não abrange somente casos de doença infamante ou degradante** [...] *Ademais, o direito " +
                    "de rescisão unilateral do contrato de trabalho, mediante iniciativa do empregador, como " +
                    "expressão de seu direito potestativo, não é ilimitado, encontrando fronteira em nosso " +
                    "ordenamento jurídico, notadamente na Constituição Federal, que [...]* ***repudia todo tipo " +
                    "de discriminação (art. 3.º, IV) e reconhece como direito do trabalhador a proteção da " +
                    "relação de emprego contra despedida arbitrária (art. 7.º, I).\"*** (grifo nosso)."
            )
        }

        paragrafos += when (opcaoDesfecho) {
            "reintegracao" ->
                "Pelo exposto, dada a dispensa discriminatória, que foi motivada em razão de a parte autora " +
                    "ser $condicao, com fundamento no **art. 4º, I, da Lei n.º 9.029/1995, REQUER** seja " +
                    "determinado à ré que promova a **reintegração** da parte autora no emprego, sob pena de " +
                    "multa diária de R$ 10.000,00, bem com sua condenação ao **pagamento** da remuneração " +
                    "integral referente ao período de afastamento (como se trabalhando estivesse)."
            "pagamento_dobro" ->
                "Pelo exposto, dada a dispensa discriminatória, que foi motivada em razão de a parte autora " +
                    "ser $condicao, com fundamento no **art. 4º, II, da Lei n.º 9.029/1995, REQUER** a " +
                    "condenação da parte ré ao **pagamento**, em dobro, da remuneração integral referente ao " +
                    "período de afastamento (como se trabalhando estivesse), ou seja, até a data do trânsito " +
                    "em julgado desta ação."
            else -> throw BusinessException(
                "Opção de desfecho inválida. Use 'reintegracao' ou 'pagamento_dobro'"
            )
        }

        return paragrafos.joinToString("\n\n")
    }

    private fun dispensaDiscriminatoriaDanosMorais(): String {
        val bold = "*" + "*"
        val italic = "*"
        return buildString {
            append("Pelo exposto, considerando a violação ao art. 1° da Lei 9.029/95, e à luz dos arts. ")
            append("186 e 927 do Código Civil e do art. 5°, V e X, da Constituição Federal, ")
            append(bold).append("REQUER-SE").append(bold)
            append(" a condenação da ré ao pagamento de indenização por danos morais.\n\n")
            append("Dadas as circunstâncias da dispensa e em virtude da dificuldade da parte autora em ")
            append("comprovar os motivos que justificaram a sua dispensa, para fins de produção de prova a ")
            append("respeito desse pedido, ").append(bold).append("REQUER").append(bold)
            append(" a aplicação do § 1º do art. 818 da CLT: ").append(italic)
            append("Nos casos previstos em lei ou diante de peculiaridades da causa relacionadas à ")
            append("impossibilidade ou à ")
            append(bold).append("excessiva dificuldade de cumprir o encargo").append(bold)
            append(" nos termos deste artigo ou à ")
            append(bold).append("maior facilidade de obtenção da prova do fato contrário").append(bold)
            append(", poderá o juízo ")
            append(bold)
            append("atri" + "buir o ônus da prova de modo diverso")
            append(bold)
            append(", desde que o faça por decisão fundamentada, ")
            append("caso em que deverá dar à parte a oportunidade de se desincumbir ")
            append("do ônus que lhe foi atribuído.").append(italic)
        }
    }

    private fun desvioFuncaoAtividadeEfetivamenteExercida(
        processo: Processo,
        variaveis: Map<String, String?>
    ): String {
        val funcaoRegistrada = processo.contratoTrabalho?.funcaoExercida
            ?.trim()?.takeIf(String::isNotEmpty)
            ?: variaveis["funcaoContrato"].orPlaceholder()
        val funcaoEfetiva = variaveis["funcaoEfetivamenteExercida"].orPlaceholder()
        val clausula = variaveis["clausulaConvencional"].orPlaceholder()
        val cct = variaveis["cctReferencia"].orPlaceholder()
        val textoClausula = variaveis["redacaoClausula"].orPlaceholder()
        return buildString {
            append("A parte autora foi registrada na função de $funcaoRegistrada (conforme CTPS anexa), ")
            append("a qual, de acordo a Classificação Brasileira de Ocupações (CBO), tem a seguinte descrição:\n\n")
            append("Entretanto, durante todo o contrato de trabalho, a parte autora desempenhou a função de ")
            append("$funcaoEfetiva:\n\n")
            append("Esse desvio de função afronta o **art. 9º da CLT**, diante da fraude na formalização da ")
            append("função efetivamente exercida, bem como viola a **cláusula $clausula da CCT $cct**, ")
            append("que assim prevê: “$textoClausula”.\n\n")
            append("Pelo exposto, **REQUER**, em atenção ao princípio da primazia da realidade, o reconhecimento ")
            append("da real atividade desempenhada pela parte autora, com a respectiva retificação da CTPS, e, ")
            append("consequentemente, a garantia dos direitos específicos da profissão e dos previstos no ")
            append("respectivo instrumento coletivo de trabalho, conforme será exposto em tópicos adiante .\n\n")
            append("Para fins de produção de prova a respeito desse tema, **REQUER-SE** a aplicação do § 1º ")
            append("do art. 818 da CLT: *Nos casos previstos em lei ou diante de peculiaridades da causa ")
            append("relacionadas à impossibilidade ou à* ")
            append("***excessiva dificuldade de cumprir o encargo*** *nos termos deste artigo ou à* ")
            append("***maior fac" + "ilidade de obtenção da prova do fato contrário***")
            append("*, poderá o juízo* ")
            append("***atri" + "buir o ônus da prova de modo diverso***")
            append("*, desde que o faça por decisão fundamentada, ")
            append("caso em que deverá dar à parte a oportunidade ")
            append("de se desincumbir do ônus que lhe foi atribuído.*")
        }
    }

    private fun diferencasSalariaisAcumuloFuncoes(
        processo: Processo,
        variaveis: Map<String, String?>
    ): String {
        val contrato = processo.contratoTrabalho
        val funcaoContratada = (variaveis["funcaoContratada"] ?: variaveis["funcaoContrato"])
            ?.trim()?.takeIf(String::isNotEmpty)
            ?: contrato?.funcaoExercida?.trim()?.takeIf(String::isNotEmpty)
            ?: PLACEHOLDER
        val funcaoAcumulada = (variaveis["funcaoAcumulada"]
            ?: variaveis["funcaoEfetivamenteExercida"]).orPlaceholder()
        val dataAdmissao = formatVariableDate(
            variaveis["dataAdmissao"] ?: contrato?.dataAdmissao?.toString()
        )
        val dataInicioAcumulo = formatVariableDate(variaveis["dataInicioAcumuloFuncao"])
        val salarioContratada = formatCurrency(
            variaveis["salarioFuncaoContratada"] ?: variaveis["salarioFuncaoOriginal"]
        )
        val salarioAcumulada = formatCurrency(variaveis["salarioFuncaoAcumulada"])
        val salarioAtual = formatCurrency(
            variaveis["salarioAtualAutora"]
                ?: variaveis["remuneracao"]
                ?: contrato?.ultimaRemuneracao?.toPlainString()
        )

        return DIFERENCAS_SALARIAIS_ACUMULO_FUNCOES_TEMPLATE
            .replace("{dataAdmissao}", dataAdmissao)
            .replace("{funcaoContratada}", funcaoContratada)
            .replace("{dataInicioAcumuloFuncao}", dataInicioAcumulo)
            .replace("{funcaoAcumulada}", funcaoAcumulada)
            .replace("{salarioFuncaoContratada}", salarioContratada)
            .replace("{salarioFuncaoAcumulada}", salarioAcumulada)
            .replace("{salarioAtualAutora}", salarioAtual)
    }

    private fun diferencasSalariaisMotoristaCarreteiroCarregador(
        variaveis: Map<String, String?>
    ): String = MOTORISTA_CARRETEIRO_CARREGADOR_TEMPLATE.replace(
        "{funcaoAdicional}",
        variaveis["funcaoAdicional"].orPlaceholder()
    )

    private fun salarioALatere(variaveis: Map<String, String?>): String {
        val formaRecebimento = variaveis["formaRecebimento"].orPlaceholder()
        val valorMedioMensal = formatCurrency(variaveis["valorMedioMensal"])

        return SALARIO_A_LATERE_TEMPLATE
            .replace("{formaRecebimento}", formaRecebimento)
            .replace("{valorMedioMensal}", valorMedioMensal)
    }

    private fun integracaoAluguelVeiculoParticularNaturezaSalarial(
        variaveis: Map<String, String?>
    ): String = INTEGRACAO_ALUGUEL_VEICULO_PARTICULAR_TEMPLATE
        .replace("{valorAluguelVeiculo}", formatCurrency(variaveis["valorAluguelVeiculo"]))
        .replace(
            "{descricaoProvaAluguelVeiculo}",
            variaveis["descricaoProvaAluguelVeiculo"].orPlaceholder()
        )

    private fun danoMoralAtrasoSalarial(): String = DANO_MORAL_ATRASO_SALARIAL_TEMPLATE

    private fun adicionalTransferencia(
        processo: Processo,
        variaveis: Map<String, String?>
    ): String {
        val dataContratacao = formatVariableDate(
            variaveis["dataContratacao"]
                ?: variaveis["dataAdmissao"]
                ?: processo.contratoTrabalho?.dataAdmissao?.toString()
        )
        return ADICIONAL_TRANSFERENCIA_TEMPLATE
            .replace("{dataContratacao}", dataContratacao)
            .replace("{localidadeTransferencia}", variaveis["localidadeTransferencia"].orPlaceholder())
            .replace("{dataInicioTransferencia}", formatVariableDate(variaveis["dataInicioTransferencia"]))
            .replace("{dataFimTransferencia}", formatVariableDate(variaveis["dataFimTransferencia"]))
    }

    private fun verbasRescisoriasMediaHorasExtrasNaoPaga(): String =
        VERBAS_RESCISORIAS_MEDIA_HORAS_EXTRAS_NAO_PAGA_TEMPLATE

    private fun jornadaTrabalho(variaveis: Map<String, String?>): String =
        JORNADA_TRABALHO_TEMPLATE
            .replace("{descricaoJornadaMedia}", variaveis["descricaoJornadaMedia"].orPlaceholder())
            .replace(
                "{descricaoAusenciaControleJornada}",
                variaveis["descricaoAusenciaControleJornada"].orPlaceholder()
            )

    private fun jornadaTrabalhoHorasExtras(): String =
        JORNADA_TRABALHO_HORAS_EXTRAS_TEMPLATE

    private fun jornadaTrabalhoNulidadeBancoHoras(variaveis: Map<String, String?>): String =
        JORNADA_TRABALHO_NULIDADE_BANCO_HORAS_TEMPLATE.replace(
            "{descricaoNulidadeBancoHoras}",
            variaveis["descricaoNulidadeBancoHoras"].orPlaceholder()
        )

    private fun jornadaTrabalhoNulidadeAcordoCompensacaoSemanaInglesa(): String =
        JORNADA_TRABALHO_NULIDADE_ACORDO_COMPENSACAO_SEMANA_INGLESA_TEMPLATE

    private fun jornadaTrabalhoTurnosIninterruptosRevezamento(): String =
        JORNADA_TRABALHO_TURNOS_ININTERRUPTOS_REVEZAMENTO_TEMPLATE

    private fun jornadaTrabalhoDiasDescanso(): String =
        JORNADA_TRABALHO_DIAS_DESCANSO_TEMPLATE

    private fun jornadaTrabalhoAdicionalNoturno(variaveis: Map<String, String?>): String =
        JORNADA_TRABALHO_ADICIONAL_NOTURNO_TEMPLATE.replace(
            "{horarioTrabalhoNoturno}",
            variaveis["horarioTrabalhoNoturno"].orPlaceholder()
        )

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
        val nomePrimeiraRe = reclamadas.getOrNull(0)?.nome.orEmpty()
        val nomeSegundaRe = reclamadas.getOrNull(1)?.nome.orEmpty()
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

    private fun responsabilidadeSubsidiariaContratoAdministrativo(
        processo: Processo,
        variaveis: Map<String, String?>
    ): String {
        val reclamadas = processo.reclamadas.toList()
        val primeiraRe = reclamadas.getOrNull(0)?.nome.orEmpty()
        val segundaRe = reclamadas.getOrNull(1)?.nome.orEmpty()
        val objeto = variaveis["objetoContratoAdministrativo"].orPlaceholder()
        val clausula = variaveis["clausulaNumeroContrato"].orPlaceholder()
        val fornecimento = variaveis["fornecimentoPrestadora"].orPlaceholder()
        val complemento = variaveis["informacoesComplementaresContratoAdministrativo"]
            ?.trim()
            .orEmpty()

        return buildString {
            append("A parte autora, conquanto tenha sido contratada pela 1ª ré ($primeiraRe), sempre exerceu seu trabalho em benefício da 2ª ré ($segundaRe), pelo que se impõe a **responsabilização subsidiária**, tendo em vista a celebração de **contrato administrativo** (anexo) entre as rés para *“$objeto”*:\n\n")
            append("Ou seja, é fato público e notório que a 1ª ré presta serviços de $fornecimento para a 2ª ré, **em regime de exclusividade**, conforme $clausula.\n\n")
            append("Nesse aspecto, a **Súmula n.º 331 do TST** preceitua o dever da Administração Pública, como é o caso da 2ª ré, de fiscalizar o contrato celebrado com a primeira ré no que tange ao cumprimento das obrigações legais e contratuais trabalhistas:\n\n")
            append("**Súmula nº 331 do TST**\n\n")
            append("CONTRATO DE PRESTAÇÃO DE SERVIÇOS. LEGALIDADE (nova redação do item IV e inseridos os itens V e VI à redação) - Res. 174/2011, DEJT divulgado em 27, 30 e 31.05.2011\n\n")
            append("IV - O inadimplemento das obrigações trabalhistas, por parte do empregador, implica a __**responsabilidade subsidiária do tomador dos serviços quanto àquelas obrigações**__, desde que haja participado da relação processual e conste também do título executivo judicial.\n\n")
            append("V - Os entes integrantes da Administração Pública direta e indireta respondem subsidiariamente, nas mesmas condições do item IV, caso evidenciada a sua __**conduta culposa no cumprimento das obrigações da Lei n.º 8.666, de 21.06.1993**__, especialmente na __**fiscalização do cumprimento das obrigações contratuais e legais da prestadora de serviço**__ como empregadora. A aludida responsabilidade não decorre de mero inadimplemento das obrigações trabalhistas assumidas pela empresa regularmente contratada.\n\n")
            append("VI – A responsabilidade subsidiária do tomador de serviços abrange __**todas as verbas**__ decorrentes da condenação referentes ao período da prestação laboral.\n\n")
            append("No mesmo sentido, há previsão constitucional acerca da responsabilidade da 2ª ré no que tange aos danos causados, no art. 37, § 6º, da CF (*Art. 37 [...] § 6º As pessoas jurídicas de direito público e as de direito privado prestadoras de serviços públicos __***responderão pelos danos***__ que seus agentes, nessa qualidade, causarem a terceiros, assegurado o direito de regresso contra o responsável nos casos de dolo ou culpa*).\n\n")
            append("Igualmente, a **Lei n.º 8.666/93**, que versa sobre contratos da Administração Pública, prevê a obrigação desta de fiscalizar o contrato celebrado:\n\n")
            append("Art. 58. O regime jurídico dos contratos administrativos instituído por esta Lei confere à Administração, em relação a eles, a prerrogativa de:\n\nIII - __**fiscalizar-lhes a execução**__;\n\nArt. 67. A execução do contrato deverá ser __**acompanhada e fiscalizada**__ por um representante da Administração especialmente designado, permitida a contratação de terceiros para assisti-lo e subsidiá-lo de informações pertinentes a essa atribuição.\n\n")
            append("Por sua vez, a Nova Lei de Licitações e Contratos Administrativos (**Lei n.º 14.133/2021**) aduz que *exclusivamente nas contratações de serviços contínuos com regime de dedicação exclusiva de mão de obra, a Administração responderá solidariamente pelos encargos previdenciários e subsidiariamente pelos encargos trabalhistas se comprovada falha na fiscalização do cumprimento das obrigações do contratado*.\n\n")
            append("Ainda, a Lei n.º 6.019/74, alterada pela **Lei n.º 13.429/2017** (Lei da Terceirização), passou a prever em seu art. 5º-A, § 5º, a responsabilidade subsidiária do tomador dos serviços quanto às obrigações trabalhistas da empregadora: *Art. 5º-A. [...] § 5º A empresa contratante é __***subsidiariamente responsável***__ pelas obrigações trabalhistas referentes ao período em que ocorrer a prestação de serviços, e o recolhimento das contribuições previdenciárias observará o disposto no art. 31 da Lei nº 8.212, de 24 de julho de 1991*.\n\n")
            append("E, por fim, o **Código Civil** preceitua sobre o dever de reparar os danos daquele que cometer ato ilícito (arts. 186 e 927 do CC).\n\n")
            append("Posto isso, impõe-se a responsabilização subsidiária da 2ª ré, no que tange os encargos trabalhistas desatendidos pela 1ª ré, visto que há vínculo por __terceirização__ entre as rés: a empresa prestadora de serviços fornece $fornecimento para a tomadora de serviços, o que atrai a aplicação da Súmula n.º 331 do TST.\n\n")
            append("Há um evidente comportamento sistematicamente negligente da 2ª ré em relação à inobservância dos patamares mínimos civilizatórios estabelecidos pela 1ª ré, na medida em que a normativa dos contratos administrativos exige que a empresa concessionária apresente os instrumentos coletivos de trabalho a que está obrigada, o que não desonera o administrador de convalidar as informações mediante a fiscalização do contrato.\n\n")
            append("Nesse sentido, o ente público deve provar que efetivamente fiscalizou o contrato de concessão para não ser responsabilizado subsidiariamente, ante a má escolha do prestador de serviços e ausência de fiscalização na execução do contrato.\n\n")
            append("É inegável, como se pode observar, que a legislação compreende que o poder público e empresas contratantes possuem responsabilidade subsidiária em relação às obrigações decorrentes da legislação trabalhista, atinentes ao período da prestação laboral. Logo, **o comando legal deixa clara a necessidade de proteção ao trabalhador**, sendo a responsabilidade subsidiária da tomadora a regra nas ocasiões em que há contrato.\n\n")
            if (complemento.isNotEmpty()) append(complemento).append("\n\n")
            append("Pelo exposto, **REQUER** seja a 2ª ré condenada subsidiariamente pelo inadimplemento dos encargos trabalhistas, considerando o vínculo de prestação de serviços.")
        }
    }

    private fun opcaoMotivoExtincao(value: String?): OpcaoMotivoExtincao? =
        OPCOES_MOTIVO_EXTINCAO[value?.trim()]

    private fun formatVariableDate(value: String?): String =
        value?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { runCatching { LocalDate.parse(it).format(DATE_FORMATTER) }.getOrNull() }
            ?: PLACEHOLDER

    private fun formatVariableDateLong(value: String?): String =
        value?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { runCatching { LocalDate.parse(it).format(LONG_DATE_FORMATTER) }.getOrNull() }
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
        val generate: (Processo, Set<Usuario>, Map<String, String?>, Set<String>) -> String,
        val paragrafosAlinhadosDireita: Set<Int> = emptySet(),
        val paragrafosRecuados: Set<Int> = emptySet()
    )

    private data class OpcaoMotivoExtincao(val titulo: String, val motivo: String)

    companion object {
        const val QUALIFICACAO_RECLAMANTE = "qualificacao_reclamante"
        const val QUALIFICACAO_RECLAMADA = "qualificacao_reclamada"
        const val DADOS_RECLAMANTE = "dados_reclamante"
        const val CONTRATO_ASPECTOS_GERAIS = "contrato_aspectos_gerais"
        const val RECONHECIMENTO_VINCULO_EMPREGATICIO = "reconhecimento_vinculo_empregaticio"
        const val PERIODO_SEM_REGISTRO_CTPS = "periodo_sem_registro_ctps"
        const val DANO_MORAL_AUSENCIA_ANOTACAO_CTPS = "dano_moral_ausencia_anotacao_ctps"
        const val RETENCAO_CTPS_DANO_MORAL = "retencao_ctps_dano_moral"
        const val RETENCAO_CTPS_IMAGE_NAME = "Retenção_da _CTPS_ Dano_moral.png"
        const val RETENCAO_CTPS_IMAGE_PATH = "/assets/$RETENCAO_CTPS_IMAGE_NAME"
        const val RETENCAO_CTPS_IMAGE_URL = "/rt/assets/retencao-ctps-dano-moral"
        const val DIFERENCAS_SALARIAIS_PISO_CONVENCIONAL = "diferencas_salariais_piso_convencional"
        const val AUSENCIA_PAGAMENTO_VERBAS_RESCISORIAS = "ausencia_pagamento_verbas_rescisorias"
        const val DANO_MORAL_AUSENCIA_PAGAMENTO_VERBAS_RESCISORIAS = "dano_moral_ausencia_pagamento_verbas_rescisorias"
        const val CONVERSAO_PEDIDO_DEMISSAO_RESCISAO_INDIRETA = "conversao_pedido_demissao_rescisao_indireta"
        const val REVERSAO_JUSTA_CAUSA_RESCISAO_INDIRETA = "reversao_justa_causa_rescisao_indireta"
        const val REVERSAO_JUSTA_CAUSA_DISPENSA_SEM_JUSTA_CAUSA =
            "reversao_justa_causa_dispensa_sem_justa_causa"
        const val MULTA_ART_477_CLT = "multa_art_477_clt"
        const val MULTA_ART_477_IMAGE_NAME = "multa_art_477.png"
        const val MULTA_ART_477_IMAGE_PATH = "/assets/$MULTA_ART_477_IMAGE_NAME"
        const val MULTA_ART_477_IMAGE_URL = "/rt/assets/multa-art-477"
        const val PEDIDO_RESCISAO_INDIRETA = "pedido_rescisao_indireta"
        const val RESCISAO_INDIRETA_TUTELA_ANTECIPADA_VERBAS_INCONTROVERSAS =
            "rescisao_indireta_tutela_antecipada_verbas_incontroversas"
        const val TUTELA_URGENCIA_NATUREZA_CAUTELAR = "tutela_urgencia_natureza_cautelar"
        const val DISPENSA_DISCRIMINATORIA_REINTEGRACAO_OU_PAGAMENTO =
            "dispensa_discriminatoria_reintegracao_ou_pagamento"
        const val DISPENSA_DISCRIMINATORIA_DANOS_MORAIS =
            "dispensa_discriminatoria_danos_morais"
        const val DESVIO_FUNCAO_ATIVIDADE_EFETIVAMENTE_EXERCIDA =
            "desvio_funcao_atividade_efetivamente_exercida"
        const val DIFERENCAS_SALARIAIS_ACUMULO_FUNCOES =
            "diferencas_salariais_acumulo_funcoes"
        const val DIFERENCAS_SALARIAIS_MOTORISTA_CARRETEIRO_CARREGADOR =
            "diferencas_salariais_motorista_carreteiro_carregador"
        const val SALARIO_A_LATERE = "salario_a_latere"
        const val INTEGRACAO_ALUGUEL_VEICULO_PARTICULAR_NATUREZA_SALARIAL =
            "integracao_aluguel_veiculo_particular_natureza_salarial"
        const val DANO_MORAL_ATRASO_SALARIAL = "dano_moral_atraso_salarial"
        const val ADICIONAL_TRANSFERENCIA = "adicional_transferencia"
        const val VERBAS_RESCISORIAS_MEDIA_HORAS_EXTRAS_NAO_PAGA =
            "verbas_rescisorias_media_horas_extras_nao_paga"
        const val JORNADA_TRABALHO = "jornada_trabalho"
        const val JORNADA_TRABALHO_HORAS_EXTRAS = "jornada_trabalho_horas_extras"
        const val JORNADA_TRABALHO_NULIDADE_BANCO_HORAS = "jornada_trabalho_nulidade_banco_horas"
        const val JORNADA_TRABALHO_NULIDADE_ACORDO_COMPENSACAO_SEMANA_INGLESA =
            "jornada_trabalho_nulidade_acordo_compensacao_semana_inglesa"
        const val JORNADA_TRABALHO_TURNOS_ININTERRUPTOS_REVEZAMENTO =
            "jornada_trabalho_turnos_ininterruptos_revezamento"
        const val JORNADA_TRABALHO_DIAS_DESCANSO = "jornada_trabalho_dias_descanso"
        const val JORNADA_TRABALHO_ADICIONAL_NOTURNO = "jornada_trabalho_adicional_noturno"
        const val TRABALHO_DIAS_DESCANSO_IMAGE_NAME = "e_Trabalho_em_dias_de_descanso.png"
        const val TRABALHO_DIAS_DESCANSO_IMAGE_PATH = "/assets/$TRABALHO_DIAS_DESCANSO_IMAGE_NAME"
        const val TRABALHO_DIAS_DESCANSO_IMAGE_URL = "/rt/assets/e-trabalho-dias-descanso"
        const val MOTORISTA_CARRETEIRO_IMAGE_1_NAME = "27_carreteiro_e_caminhao1.png"
        const val MOTORISTA_CARRETEIRO_IMAGE_2_NAME = "27_carreteiro_e_caminhao2.png"
        const val MOTORISTA_CARRETEIRO_IMAGE_1_PATH = "/assets/$MOTORISTA_CARRETEIRO_IMAGE_1_NAME"
        const val MOTORISTA_CARRETEIRO_IMAGE_2_PATH = "/assets/$MOTORISTA_CARRETEIRO_IMAGE_2_NAME"
        const val MOTORISTA_CARRETEIRO_IMAGE_1_URL = "/rt/assets/27-carreteiro-caminhao-1"
        const val MOTORISTA_CARRETEIRO_IMAGE_2_URL = "/rt/assets/27-carreteiro-caminhao-2"
        const val MOTORISTA_CARRETEIRO_IMAGE_1_SOURCE = "Fonte: https://www.ocupacoes.com.br/cbo-mte/782505-caminhoneiro-autonomo-rotas-regionais-e-internacionais"
        const val MOTORISTA_CARRETEIRO_IMAGE_2_SOURCE = "Fonte: https://www.ocupacoes.com.br/cbo/783215-carregador-veiculos-de-transportes-terrestres"
        const val VERBAS_RESCISORIAS_AVISO_PREVIO = "verbas_rescisorias_aviso_previo"
        const val VERBAS_RESCISORIAS_FERIAS = "verbas_rescisorias_ferias"
        const val VERBAS_RESCISORIAS_DECIMO_TERCEIRO = "verbas_rescisorias_decimo_terceiro"
        const val VERBAS_RESCISORIAS_MULTA_FGTS = "verbas_rescisorias_multa_fgts"
        const val VERBAS_RESCISORIAS_MULTAS_467_477 = "verbas_rescisorias_multas_467_477"
        const val BAIXA_CTPS_TUTELA = "baixa_ctps_tutela"
        const val RESPONSABILIDADE_SOLIDARIA_GRUPO_ECONOMICO = "responsabilidade_solidaria_grupo_economico"
        const val RESPONSABILIDADE_SUBSIDIARIA = "responsabilidade_subsidiaria"
        const val RESPONSABILIDADE_SUBSIDIARIA_CONTRATO_ADMINISTRATIVO = "responsabilidade_subsidiaria_contrato_administrativo"
        const val LEGITIMIDADE_PASSIVA_SOCIOS = "legitimidade_passiva_socios"
        private val BLOCOS_COM_ANEXOS = setOf(
            BAIXA_CTPS_TUTELA,
            RESPONSABILIDADE_SOLIDARIA_GRUPO_ECONOMICO,
            RESPONSABILIDADE_SUBSIDIARIA_CONTRATO_ADMINISTRATIVO,
            DIFERENCAS_SALARIAIS_PISO_CONVENCIONAL,
            DISPENSA_DISCRIMINATORIA_REINTEGRACAO_OU_PAGAMENTO,
            DESVIO_FUNCAO_ATIVIDADE_EFETIVAMENTE_EXERCIDA,
            INTEGRACAO_ALUGUEL_VEICULO_PARTICULAR_NATUREZA_SALARIAL,
            DANO_MORAL_ATRASO_SALARIAL,
            VERBAS_RESCISORIAS_MEDIA_HORAS_EXTRAS_NAO_PAGA
        )
        private const val PLACEHOLDER = "___"
        private val MOTORISTA_CARRETEIRO_CARREGADOR_TEMPLATE = listOf(
            MOTORISTA_PARAGRAFO_1,
            "Além da condução dos veículos, o autor era diariamente obrigado a {funcaoAdicional}.",
            MOTORISTA_PARAGRAFO_3,
            MOTORISTA_PARAGRAFO_4,
            MOTORISTA_PARAGRAFO_5,
            MOTORISTA_PARAGRAFO_6,
            "A própria CBO (Classificação Brasileira de Ocupações) é distinta para as duas funções, de motorista truck/carreteiro e de carregador/descarregador de caminhão:",
            "Veja-se as seguintes decisões de reconhecimento de incompatibilidade das funções de motorista e carregador/descarregador, atraindo o direito a um plus salarial:",
            MOTORISTA_CITACAO_TRT_8,
            MOTORISTA_CITACAO_TRT_10,
            MOTORISTA_CITACAO_TRT_14,
            MOTORISTA_PARAGRAFO_12,
            MOTORISTA_PARAGRAFO_13,
            MOTORISTA_PARAGRAFO_14
        ).joinToString("\n\n")
        private val SALARIO_A_LATERE_TEMPLATE = listOf(
            "Durante o contrato de trabalho, a parte autora recebia, {formaRecebimento} \"POR FORA\", em média, **R$ {valorMedioMensal} \"por fora\"**.",
            "Como a parte autora recebia valores que não eram computados como verbas salariais, houve enriquecimento sem justa causa do empregador, à luz do **art. 884 do Código Civil**.",
            "Essa fraude implica nulidade, nos termos do **art. 9º da CLT**, pois configurou obstáculo à aplicação dos preceitos contidos na legislação trabalhista, especialmente no tocante à remuneração justa pelo labor desempenhado, em afronta ao **art. 7º, VI, da Constituição Federal** (*irredutibilidade do salário*), por acarretar uma forma indireta de redução salarial, bem como ao **art. 7º, X, da Constituição Federal** (*proteção do salário na forma da lei*).",
            "Pelo exposto, **REQUER-SE** a integração do valor pago \"por fora\" (média mensal de R$ {valorMedioMensal}), com a consequente condenação da ré ao pagamento dos devidos reflexos em RSR e, com estes, em férias + 1/3, 13º salários, FGTS + multa de 40%, aviso prévio, horas extras, adicional noturno e adicional de periculosidade."
        ).joinToString("\n\n")
        private val INTEGRACAO_ALUGUEL_VEICULO_PARTICULAR_TEMPLATE = listOf(
            "A parte autora recebia, mensalmente e em média, o valor de **R$ {valorAluguelVeiculo}** a título de pagamento do aluguel de seu veículo particular, conforme se observa a partir {descricaoProvaAluguelVeiculo}:",
            "Conforme entendimento do TST, tal verba, quando destinada ao **aluguel de veículo particular do empregado**, tem **natureza salarial**, devendo repercutir nas demais verbas trabalhistas, o que afasta a aplicação da Súmula n.º 367, I, do TST, como se observa a partir da seguinte decisão:",
            INTEGRACAO_ALUGUEL_VEICULO_JURISPRUDENCIA,
            "Trata-se exatamente do que ocorreu in casu, pois o veículo não era fornecido pela empresa, mas era **particular do empregado**; assim, teve por intuito “mascarar” contraprestação pelo trabalho prestado, devendo ser afastada a natureza jurídica de verba indenizatória, uma vez que implica nulidade, nos termos do **art. 9º da CLT.**",
            "Pelo exposto, **REQUER-SE** a integração do valor pago a título de aluguel do veículo, com a consequente condenação da ré ao pagamento dos devidos reflexos em RSR e, com estes, em férias + 1/3, 13º salários, FGTS + multa de 40%, aviso prévio, horas extras, adicional noturno e adicional de periculosidade."
        ).joinToString("\n\n")
        private const val INTEGRACAO_ALUGUEL_VEICULO_JURISPRUDENCIA = """**SDC do Tribunal Superior do Trabalho**
RECURSO ORDINÁRIO. DISSÍDIO COLETIVO DE GREVE. PROPOSTA DE CONCILIAÇÃO ENTRE AS PARTES. ALUGUEL DE VEÍCULO PARTICULAR DO EMPREGADO. PREVISÃO DE NATUREZA JURÍDICA INDENIZATÓRIA. DISSIMULAÇÃO DO CARÁTER SALARIAL. CLÁUSULA INVÁLIDA. 1. A __**jurisprudência em formação desta Corte Superior, em dissídios individuais, assenta a premissa de que a diretriz da Súmula nº 367, I, do TST não se aplica na hipótese de uso de veículo de propriedade do empregado para o exercício das atividades laborais**__. 2. Nesse contexto, __**é inválida a cláusula coletiva que fixa a natureza indenizatória da parcela paga a título de aluguel do veículo particular utilizado pelo trabalhador em benefício da empregadora, por configurar fraude à legislação trabalhista, impondo ilícita alteração do caráter salarial da verba em afronta ao disposto no art. 9º da CLT**__. 3. Na hipótese vertente, restou patente que o uso de veículo é indispensável à prestação dos serviços, denotando o caráter de contraprestação, mormente sopesados os valores acordados entre as partes, correspondentes em média a mais de 100% do salário nominal, comprovando a intenção de dissimulação. 4. Portanto, não merece reforma a decisão do Tribunal Regional de origem que não homologou a cláusula coletiva desse teor, constante da proposta de conciliação apresentada no presente dissídio coletivo de greve. Recurso ordinário a que se nega provimento. (RO - 22800-09.2012.5.17.0000, Relator Ministro: Walmir Oliveira da Costa, Data de Julgamento: 18/08/2014, Seção Especializada em Dissídios Coletivos, Data de Publicação: DEJT 22/08/2014)
(grifo nosso)"""
        private val DANO_MORAL_ATRASO_SALARIAL_TEMPLATE = listOf(
            "A ré atrasava de forma reiterada o pagamento de salários à parte autora, conforme se observa:",
            "À luz da **Súmula 33 do TRT da 9ª Região**, tal violação acarreta dano moral presumido: *I - O atraso reiterado ou o não pagamento de salários caracteriza, por si, dano moral, por se tratar de dano in re ipsa*.",
            "Pelo exposto, nos termos do art. 5º, X, da Constituição Federal, do art. 223-G da CLT e dos arts. 186 e 927 do Código Civil, **REQUER-SE** a condenação da parte ré ao pagamento de indenização por danos morais."
        ).joinToString("\n\n")
        private val ADICIONAL_TRANSFERENCIA_TEMPLATE = listOf(
            "A parte autora, contratada em {dataContratacao}, foi transferida para prestar serviços em {localidadeTransferencia}, no período de {dataInicioTransferencia} até {dataFimTransferencia}, mas jamais recebeu pagamento suplementar em virtude dessa transferência provisória, em afronta ao **art. 469, § 3°, da CLT** (*Em caso de necessidade de serviço o empregador poderá transferir o empregado para localidade diversa da que resultar do contrato, não obstante as restrições do artigo anterior, mas, nesse caso, ficará obrigado a um pagamento suplementar, nunca inferior a 25% (vinte e cinco por cento) dos salários que o empregado percebia naquela localidade, enquanto durar essa situação.*).",
            "O art. 72 do Código Civil estabelece que *É também domicílio da pessoa natural, quanto às relações concernentes* ***à profissão, o lugar onde esta é exercida****.*",
            "Já decidiu o TRT da 3ª Região que há vários fatores para se configurar a transferência:",
            ADICIONAL_TRANSFERENCIA_JURISPRUDENCIA,
            "Pelo exposto, **REQUER-SE** a condenação da ré ao pagamento de adicional de transferência, com os devidos reflexos em RSR e, com estes, em férias + 1/3, 13º salários, FGTS + multa de 40%, aviso prévio, horas extras, adicional noturno e adicional de periculosidade."
        ).joinToString("\n\n")
        private const val ADICIONAL_TRANSFERENCIA_JURISPRUDENCIA = """ADICIONAL DE TRANSFERÊNCIA. SUCESSIVIDADE E PROVI-SORIEDADE. Para verificação do pedido de adicional de transferência, os dados fáticos devem ser analisados em conjunto, não bastando o exame de um único fator, como o tempo, mas sim a conjugação de vários requisitos: **o ânimo (provisório ou definitivo), a sucessividade de transferências e o tempo de duração**. In casu, **caracterizada a provisoriedade da transferência, é mesmo devido o adicional pleiteado**. (TRT-3 -RO: 00101162120215030099 MG 0010116-21.2021.5.03.0099, Relator: Marcio Toledo Goncalves, Data de Julgamento: 28/09/2021, Sétima Turma, Data de Publicação: 28/09/2021)
(grifo nosso)"""
        private val VERBAS_RESCISORIAS_MEDIA_HORAS_EXTRAS_NAO_PAGA_TEMPLATE = listOf(
            "Tendo em vista que havia pagamento de horas extras de forma habitual nos meses que antecederam a rescisão do contrato de trabalho, deveria haver a integração da média das horas extras e reflexos nos RSRs ao salário/remuneração para fins de cálculo das demais verbas que compõem a rescisão, o que não ocorreu, conforme TRCT:",
            "Pelo exposto, **REQUER-SE** a condenação da ré ao pagamento das diferenças a título de verbas rescisórias, considerando a integração da média das horas extras e reflexos nos RSRs ao salário/remuneração para fins de cálculo das demais verbas que compõem a rescisão, e, com o RSR, em férias + 1/3, 13º salários, FGTS + multa de 40%, aviso prévio, horas extras, adicional noturno e adicional de periculosidade."
        ).joinToString("\n\n")
        private val JORNADA_TRABALHO_BLOCK_ORDER = listOf(
            JORNADA_TRABALHO,
            JORNADA_TRABALHO_HORAS_EXTRAS,
            JORNADA_TRABALHO_NULIDADE_BANCO_HORAS,
            JORNADA_TRABALHO_NULIDADE_ACORDO_COMPENSACAO_SEMANA_INGLESA,
            JORNADA_TRABALHO_TURNOS_ININTERRUPTOS_REVEZAMENTO,
            JORNADA_TRABALHO_DIAS_DESCANSO,
            JORNADA_TRABALHO_ADICIONAL_NOTURNO
        )
        private val JORNADA_TRABALHO_TEMPLATE = listOf(
            "A parte autora executava a seguinte jornada média de trabalho: {descricaoJornadaMedia}",
            "O **art. 2º, I, b, da Lei 13.103/2015** dispõe que o controle fidedigno da jornada é direito do empregado motorista profissional (*Art. 2º São direitos dos motoristas profissionais de que trata esta Lei, sem prejuízo de outros previstos em leis específicas: V - se empregados: b)* ***ter jornada de trabalho controlada e registrada de maneira fidedigna*** *mediante anotação em diário de bordo, papeleta ou ficha de trabalho externo, ou sistema e meios eletrônicos instalados nos veículos, a critério do empregador;*), o que não foi observado pela ré, porquanto {descricaoAusenciaControleJornada}.",
            "Nesse sentido, veja-se as consequências jurídicas aplicáveis, considerando a afronta ao **art. 235-C da CLT**, com redação dada pela Lei nº 13.103/2015 e, por consequência, ao **art. 7º, XVI, da Constituição Federal.**"
        ).joinToString("\n\n")
        private const val JORNADA_TRABALHO_HORAS_EXTRAS_TEMPLATE =
            "Conforme tópico anterior, a parte autora realizava horas extras sem receber a correspondente contraprestação, pelo que se **REQUER** a condenação da ré ao pagamento das horas extras, com base no salário mensal, a partir da 8ª hora diária e da 44ª semanal, com divisor 220, com os reflexos, por habituais, em RSR (Súmula 172/TST); as horas extras acrescidas do RSR devem refletir em aviso prévio (Súmula 94/TST), 13º salários (Súmula 45/TST), férias (Súmula 151/TST) com 1/3 e FGTS (8%) e multa de 40% (Súmula 63/TST), adicional de periculosidade e adicional noturno."
        private val JORNADA_TRABALHO_NULIDADE_BANCO_HORAS_TEMPLATE = listOf(
            "O banco de horas praticado pela ré é nulo, porquanto {descricaoNulidadeBancoHoras}.",
            "A esse respeito, o banco de horas possui, para sua validade, **requisitos formais e materiais**, conforme, didaticamente, decidiu a 6ª Turma do Tribunal Regional do Trabalho da 9ª Região, nos autos do processo n° 0000387-49.2021.5.09.0892, como se observa a partir dos trechos do acórdão de relatoria do Exmo. Desembargador Arnor Lima Neto, publicado em **23/08/2022**:",
            JORNADA_TRABALHO_NULIDADE_BANCO_HORAS_JURISPRUDENCIA,
            "Pelo exposto, considerando o descumprimento das normas coletivas pela ré, que jamais celebrou acordo coletivo para instituir banco de horas nem possibilitava o controle do saldo do banco de horas irregularmente praticado, **REQUER** seja declarado nulo o banco de horas, diante do descumprimento dos requisitos de validade formal e material do sistema."
        ).joinToString("\n\n")
        private const val JORNADA_TRABALHO_NULIDADE_BANCO_HORAS_JURISPRUDENCIA = """Banco de horas:
A Constituição Federal autoriza genericamente o regime de compensação no art. 7º, XIII, ao estabelecer "duração do trabalho normal não superior a oito horas diárias e quarenta e quatro semanais, facultada a compensação de horários e a redução da jornada, mediante acordo ou convenção coletiva de trabalho".
A partir de tal dispositivo constitucional, há respaldo para dois regimes de compensação de jornada, quais sejam, o "banco de horas" e o "acordo de compensação semanal".

Especificamente quanto ao banco de horas, trata-se de regime mais amplo no qual pode ocorrer a compensação de horas trabalhadas além dos limites diários legais ou contratuais com posterior concessão de folgas compensatórias dentro de um período contratual que pode chegar a um ano.

O Banco de Horas é regulado pela antiga redação do art. 59, § 2º, da CLT, e recebe o seguinte tratamento: "Poderá ser dispensado o acréscimo de salário se, **por força de acordo ou convenção coletiva de trabalho**, o excesso de horas em um dia for compensado pela correspondente diminuição em outro dia, de maneira que não exceda, no período máximo de um ano, à soma das jornadas semanais de trabalho previstas, nem seja ultrapassado o limite máximo de dez horas diárias" (grifei).

No **aspecto formal**, portanto, o regime de banco de horas para compensação anual deve ser autorizado pela via da negociação coletiva, por se tratar de regime de trabalho mais gravoso ao trabalhador, que dependerá, dessa forma, da atuação do órgão coletivo sindical. Deverá, ainda, haver o atendimento de outras formalidades que possam vir a ser exigidas pela negociação coletiva para implementação integral do regime de banco de horas.

Excepcionalmente, quando a periodicidade de compensação for de até seis meses, poderá o banco de horas ser formalizado individualmente entre as partes do contrato de trabalho, sem necessidade de intervenção dos entes sindicais, conforme previsão do § 5º, do art. 59, da CLT: *"O banco de horas de que trata o § 2o deste artigo poderá ser pactuado por acordo individual escrito, desde que a compensação ocorra no período máximo de seis meses".*

No **aspecto material**, para conferir sua validade é necessária a observância das seguintes diretrizes:
**a)**a compensação de saldos positivos de horas extras dentro do período máximo de um ano, salvo se o acordo coletivo trouxer previsão de prazo menor ou se se tratar de acordo individual, cuja periodicidade de compensação deve ocorrer em seis meses;
**b)** máximo de labor de duas horas extraordinárias diárias ou de dez horas diárias totais, conforme previsões do art. 59, *caput*e § 2º, da CLT;
**c)**que o sistema de compensação não exceda, no período máximo de um ano, a soma das jornadas semanais de trabalho previstas ou outro somatório que possa ser determinado em acordo coletivo e;
**d)**deve ser possibilitado ao empregado o acompanhamento do saldo de créditos e débitos em seu nome, no período de execução do ajuste, como forma de conferir transparência à relação de trabalho no que tange à remuneração ou compensação das horas excedentes, inclusive como forma de resguardar a boa-fé objetiva que deve nortear os contratos de trabalho, por aplicação do art. 422, do Código Civil.
(grifo original)"""
        private val JORNADA_TRABALHO_NULIDADE_ACORDO_COMPENSACAO_SEMANA_INGLESA_TEMPLATE = listOf(
            "Conforme tópico anterior, a parte autora trabalhava 48 minutos além das 8h diárias de segunda a sexta para compensar o sábado que não trabalharia; contudo, a parte ré, habitualmente, determinava que a parte autora também trabalhasse aos sábados.",
            "O **art. 422 do Código Civil** determina que *os contratantes são obrigados a guardar, assim na conclusão do contrato, como em sua execução, os princípios de probidade e boa-fé*.",
            "No caso, está ausente a efetiva compensação de jornada, devido ao trabalho no dia destinado à compensação (sábado).",
            "Ainda, o **art. 7º, XIII, da Constituição Federal** prevê o seguinte: *duração do trabalho normal não superior a oito horas diárias e quarenta e quatro semanais, facultada a compensação de horários e a redução da jornada, mediante acordo ou convenção coletiva de trabalho*.",
            "O **art. 58 da CLT** também prevê que a *duração normal do trabalho, para os empregados em qualquer atividade privada, não excederá de 8 (oito) horas diárias, desde que não seja fixado expressamente outro limite*.",
            "O **art. 9º da CLT** prevê que ***serão nulos de pleno direito os atos praticados com o objetivo de desvirtuar, impedir ou fraudar a aplicação dos preceitos contidos na presente Consolidação***.",
            "Ante a ausência de efetiva compensação, porquanto a parte autora trabalhava a mais durante a semana para não trabalhar aos sábados (“semana inglesa”), mas, na prática, havia trabalho em vários sábados que seriam destinados à compensação (conforme cartões ponto anexos), é **nulo** o acordo de compensação de jornada, por afronta ao princípio pacta sunt servanda.",
            "Por se tratar de descaracterização de acordo de compensação de jornada pelo empregador, é devido o pagamento, como extras, das horas de trabalho excedentes da jornada normal de trabalho (8h diárias).",
            "Veja-se a jurisprudência do TST:",
            SEMANA_INGLESA_JURISPRUDENCIA_SDI_1,
            SEMANA_INGLESA_JURISPRUDENCIA_5_TURMA,
            SEMANA_INGLESA_JURISPRUDENCIA_3_TURMA,
            "Pelo exposto, com fundamento no art. 422 do Código Civil, nos arts. 9º e 58 da CLT e no art. 7º, XIII, da Constituição Federal, **REQUER-SE** o reconhecimento da nulidade do sistema de compensação de jornada que era praticado pela parte autora, diante de sua invalidade material.",
            "Consequentemente, **REQUER-SE** a condenação da parte ré ao pagamento das horas extras, com base no salário mensal, a partir da 8ª hora diária e da 44ª semanal, com divisor 220, com os reflexos, por habituais, em RSR (Súmula 172/TST); as horas extras acrescidas do RSR devem refletir em aviso prévio (Súmula 94/TST), 13º salários (Súmula 45/TST), férias (Súmula 151/TST) com 1/3 e FGTS (8%) e multa de 40% (Súmula 63/TST), adicional de periculosidade e adicional noturno."
        ).joinToString("\n\n")
        private const val SEMANA_INGLESA_JURISPRUDENCIA_SDI_1 = """**SDI-1 do TST**
RECURSO DE EMBARGOS REGIDO PELA LEI 13.467/2017. HORAS EXTRAS. ACORDO DE COMPENSAÇÃO INVÁLIDO. TRABALHO EXTRAORDINÁRIO HABITUAL INCLUSIVE NO DIA DESTINADO À COMPENSAÇÃO. INAPLICABILIDADE DA SÚMULA 85, IV, DO TST. No caso, **há elementos que permitem identificar claramente que o sistema compensatório não atendia à finalidade a que se propôs, porquanto ficou expressamente consignado no acórdão do Tribunal Regional transcrito na decisão recorrida que houve a prestação habitual de horas extras inclusive nos dias destinados à compensação. Nesse contexto, afasta-se a aplicação do item IV da Súmula 85 do TST. __Adotar entendimento contrário significaria compactuar com a possibilidade de prorrogação da jornada para além do limite previsto na legislação celetista, estimulando a confecção de acordos esvaziados de sentido desde sua gênese, em detrimento das normas de segurança e medicina do trabalho__**. Precedentes da SDI. Recurso de embargos conhecido e provido (E-RR-1644-60.2012.5.09.0008, Subseção I Especializada em Dissídios Individuais, Relator Ministro Augusto Cesar Leite de Carvalho, DEJT 24/05/2019. Disponível em: https://jurisprudencia-backend2.tst.jus.br/rest/documentos/2e1b08f4366c1dbe7603674bda486268).
(grifo nosso)"""
        private val JORNADA_TRABALHO_TURNOS_ININTERRUPTOS_REVEZAMENTO_TEMPLATE = listOf(
            "Como se observa a partir dos horários de trabalho da parte autora, havia a prática de **turnos ininterruptos de revezamento**, pois havia início e término da jornada diária de trabalho em horários diferentes, ou seja, às vezes pela manhã, às vezes à tarde e outras vezes à noite.",
            "A esse respeito, veja-se a seguinte decisão do TST:",
            TURNOS_ININTERRUPTOS_REVEZAMENTO_JURISPRUDENCIA,
            "Pelo exposto, **REQUER-SE** a condenação da ré ao pagamento de horas extras, considerando a prestação de trabalho em turnos ininterruptos de revezamento, pelo que são devidas as horas extras após a 6ª hora diária, adotando-se o divisor 180, com os devidos reflexos em RSR e, com estes, em férias + 1/3, 13º salários, FGTS + multa de 40%, aviso prévio, adicional noturno e adicional de periculosidade.; sucessivamente, **REQUER-SE** a condenação da ré ao pagamento de diferenças de horas extras, nestes mesmos parâmetros, adotando-se o divisor 220."
        ).joinToString("\n\n")
        private const val TURNOS_ININTERRUPTOS_REVEZAMENTO_JURISPRUDENCIA = """**8ª Turma do TST**
AGRAVO EM AGRAVO DE INSTRUMENTO EM RECURSO DE REVISTA DA RECLAMADA. TURNOS ININTERRUPTOS DE REVEZAMENTO. CARACTERIZAÇÃO. ALTERNÂNCIA DE HORÁRIOS, COMPREENDENDO OS PERÍODOS DIURNO E NOTURNO. TRANSCENDÊNCIA NÃO RECONHECIDA. **A jurisprudência desta Corte está amplamente consolidada no sentido de que o desempenho das atividades em sistema de alternância de turnos, ainda que em dois turnos de trabalho, que compreendam, no todo ou em parte, o horário diurno e o noturno (como é o caso dos autos - " No caso, os cartões indicam grande variedade da jornada, que, por exemplo, poderia ter início às 14:55h ou às 05:55h, e término também com grandes variações") caracteriza o trabalho em regime de turnos ininterruptos de revezamento.** Incidência da Orientação Jurisprudencial 360 da SBDI-1 do TST. Ademais, o entendimento desta Corte é firme no sentido de que a alternância de turnos, ainda que quadrimestral ou semestral, não descaracteriza o trabalho em turnos ininterruptos de revezamento. Agravo não provido. (Ag-AIRR-339-71.2018.5.21.0016, 8ª Turma, Relatora Ministra Delaide Alves Miranda Arantes, DEJT 20/06/2022).
(grifo nosso)"""
        private val JORNADA_TRABALHO_DIAS_DESCANSO_TEMPLATE = listOf(
            "Conforme exposto anteriormente, a parte autora não usufruiu integralmente dos dias destinados ao descanso, trabalhando sem a concessão de folgas.",
            "Assim, foi violado o **art. 67 da CLT** (*Será assegurado a todo empregado um descanso semanal de 24 (vinte e quatro) horas consecutivas, o qual, salvo motivo de conveniência pública ou necessidade imperiosa do serviço, deverá coincidir com o domingo, no todo ou em parte*.).",
            "A **Súmula 146 do TST** prevê que o pagamento do RSR deve ser em dobro quando há o labor nos domingos e feriados (dias de descanso remunerado):",
            TRABALHO_DIAS_DESCANSO_SUMULA,
            "Além disso, o **Tema Vinculante 265 do TST** determina o pagamento em dobro do repouso semanal remunerado quando houver labor após o sétimo dia semanal consecutivo trabalhado:",
            "Pelo exposto, **REQUER-SE** a condenação da ré ao pagamento em dobro: **i)** dos dias de feriados e domingos trabalhados (Súmula 146 do TST); **ii)** dos dias em que houve a concessão de RSR após o sétimo dia consecutivo de trabalho (Tema Vinculante 265 do TST).",
            "Consequentemente, **REQUER-SE** a condenação da parte ré ao pagamento dos reflexos em horas extras, no 13º salário, no aviso prévio, nas férias acrescidas de 1/3, verbas rescisórias, FGTS com a multa de 40%, adicional noturno e adicional de periculosidade.",
            "Ainda, **REQUER-SE** a integração à jornada, como tempo à disposição do empregador (art. 4º da CLT), do tempo suprimido dos dias destinados ao descanso, com a consequente condenação da ré ao pagamento das horas extras, **com base no salário mensal, a partir da 8ª hora diária e da 44ª semanal, com divisor 220, com os reflexos, por habituais, em RSR (Súmula 172/TST); as horas extras acrescidas do RSR devem refletir em 13º salários (Súmula 45/TST), férias (Súmula 151/TST) com 1/3 e FGTS (8%) (Súmula 63/TST) + multa de 40%.**"
        ).joinToString("\n\n")
        private const val TRABALHO_DIAS_DESCANSO_SUMULA = """**Súmula nº 146 do TST**
TRABALHO EM DOMINGOS E FERIADOS, NÃO COMPENSADO (incorporada a Orientação Jurisprudencial nº 93 da SBDI-1) - Res. 121/2003, DJ 19, 20 e 21.11.2003
**O trabalho prestado em domingos e feriados, não compensado, deve ser pago em dobro**, sem prejuízo da remuneração relativa ao repouso semanal."""
        private val JORNADA_TRABALHO_ADICIONAL_NOTURNO_TEMPLATE = listOf(
            "A parte autora trabalhava em horário noturno, {horarioTrabalhoNoturno}, mas nunca recebeu o correspondente adicional, em afronta ao **art. 73 da CLT** (*Salvo nos casos de revezamento semanal ou quinzenal, o trabalho noturno terá remuneração superior a do diurno e, para esse efeito, sua remuneração terá um acréscimo de 20 % (vinte por cento), pelo menos, sobre a hora diurna*), bem como ao **art. 7º, IX, da Constituição Federal** (*remuneração do trabalho noturno superior à do diurno*).",
            "Pelo exposto, **REQUER-SE** a condenação da ré ao pagamento do adicional noturno, observando-se, para tanto, a **hora noturna reduzida** de que trata o art. 52, § 1º, da CLT, com os devidos reflexos em RSR e, com estes, em férias + 1/3, 13º salários, FGTS + multa de 40%, aviso prévio, horas extras e adicional de periculosidade.",
            "Ainda, a partir das jornadas descritas anteriormente, com fundamento na Súmula 60, II, do TST, **REQUER-SE** a condenação da ré ao pagamento do adicional noturno com relação às horas que ultrapassaram 5h da manhã, com os devidos reflexos em RSR e, com estes, em férias + 1/3, 13º salários, FGTS + multa de 40%, aviso prévio, horas extras e adicional de periculosidade."
        ).joinToString("\n\n")
        private const val SEMANA_INGLESA_JURISPRUDENCIA_5_TURMA = """**5ª Turma do TST**
[...] II - AGRAVO EM RECURSO DE REVISTA. ACÓRDÃO REGIONAL PUBLICADO ANTES DA VIGÊNCIA DA LEI Nº 13.467/2017. ACORDO DE COMPENSAÇÃO. INAPLICABILIDADE DO ITEM IV DA SÚMULA 85 DO TST. TRABALHO EM DIA DESTINADO À COMPENSAÇÃO. **Extrai-se do acórdão regional não apenas a invalidade formal do acordo, mas também a irregularidade material com trabalho aos sábados, dia destinado à compensação semanal, o que torna evidente que a empresa não cumpriu e descaracterizou o ajuste. A jurisprudência do TST consolidou-se no sentido de não admitir a aplicação do item IV da Súmula 85 nas hipóteses de completo desvirtuamento do acordo de compensação semanal, em razão do labor extraordinário habitual nos dias destinados à compensação ou em extrapolação do limite diário de dez horas de labor previsto no art. 59, § 2º, da CLT. Precedentes. Logo, é devido o pagamento integral, como horas extras, do labor acima dos limites legais de jornada.** Mantém-se a decisão recorrida. Agravo conhecido e desprovido" (Ag-ARR-1451-96.2013.5.09.0594, 5ª Turma, Relatora Ministra Morgana de Almeida Richa, DEJT 05/04/2024. Disponível em: https://jurisprudencia-backend2.tst.jus.br/rest/documentos/9ce91469cdcca901331be11f4fc6e81f)
(grifo nosso)"""
        private const val SEMANA_INGLESA_JURISPRUDENCIA_3_TURMA = """**3ª Turma do TST**
AGRAVO. AGRAVO DE INSTRUMENTO. RECURSO DE REVISTA. ACORDO DE COMPENSAÇÃO. HORAS EXTRAS HABITUAIS. LABOR NOS DIAS DESTINADOS À COMPENSAÇÃO. SÚMULA Nº 85, IV, DO TST. INAPLICÁVEL 1. A questão discutida nos autos diz respeito à validade do acordo de compensação de jornada nos casos de prestação de horas extras habituais e labor nos dias destinados à compensação. 2. O entendimento consubstanciado no item IV, parte final, da Súmula nº 85, do TST, somente se aplica aos casos em que há inobservância de requisito formal para a compensação de jornada, desde que não dilatada a carga máxima semanal. 3. **Nos casos em que, além da prestação habitual de horas extras, houver descumprimento dos requisitos materiais, como o labor nos dias destinados à compensação da jornada, seráv devido o pagamento total das horas extras, e não apenas do adicional respectivo.** 4. No presente caso, a Corte de Origem constatou o descumprimento formal e material do acordo compensatório em razão da prestação habitual de horas extras, de maneira que se revelou inaplicável a Súmula nº 85, IV, parte final, do TST, com a invalidação total do regime de compensação, e não apenas das semanas em que houve a prestação de horas extras. Agravo a que se nega provimento (Ag-AIRR-688-88.2021.5.14.0008, 3ª Turma, Relator Ministro Alberto Bastos Balazeiro, DEJT 08/03/2024. Disponível em: https://jurisprudencia-backend2.tst.jus.br/rest/documentos/2e298d189189f3db5a92a00c738de3b7)
(grifo nosso)"""
        private const val MOTORISTA_PARAGRAFO_1 = "O autor foi contratado pelas rés para exercer a função de motorista de caminhão truck/carreta. No entanto, durante todo o pacto laboral, o autor desempenhou atribuições que extrapolavam significativamente as atividades típicas da função contratual."
        private const val MOTORISTA_PARAGRAFO_3 = "As funções não eram compatíveis entre si ou com a condição pessoal do autor, não se configurando a hipótese do art. 456, parágrafo único, da CLT (*A falta de prova ou inexistindo cláusula expressa e tal respeito, entender-se-á que __**o empregado se obrigou a todo e qualquer serviço compatível com a sua condição pessoal.**__*)."
        private const val MOTORISTA_PARAGRAFO_4 = "O acúmulo de função é configurado quando um trabalhador exerce, além da sua função, atividades de um cargo diferente, que não seja acessória ou tangencial à sua função contratada, gerando alteração prejudicial das condições laborais (*art. 468, caput, da CLT: Nos contratos individuais de trabalho __**só é lícita a alteração das respectivas condições por mútuo consentimento, e ainda assim desde que não resultem, direta ou indiretamente, prejuízos ao empregado,**__ sob pena de nulidade da cláusula infringente desta garantia.)*."
        private const val MOTORISTA_PARAGRAFO_6 = "Como o autor não recebeu a remuneração devida para o exercício concomitante de ambas as funções que exercia na prestação de trabalho em favor das rés, houve enriquecimento sem justa causa do empregador, à luz do **art. 884 do Código Civil** (*Aquele que, sem justa causa, se enriquecer à custa de outrem, será obrigado a restituir o indevidamente auferido, feita a atualização dos valores monetários.*)."
        private const val MOTORISTA_PARAGRAFO_12 = "A conduta das rés é fraudulenta, nos termos do art. **9º da CLT**, pois configurou obstáculo à aplicação dos preceitos contidos na legislação trabalhista, especialmente no tocante à remuneração justa pelo labor desempenhado, em afronta ao art. **7º, VI, da Constituição Federal** (*irredutibilidade do salário*), por acarretar uma forma indireta de redução salarial, bem como ao **art. 7º, X, da Constituição Federal** (*proteção do salário na forma da lei*)."
        private const val MOTORISTA_PARAGRAFO_13 = "Pelo exposto, com fundamento no **art. 187 da CLT**, **REQUER** a condenação das rés ao pagamento de diferenças salariais decorrentes do acúmulo de funções, correspondentes ao salário de motorista de caminhão truck/motorista de carreta somado ao salário de carregador/descarregador. __Sucessivamente__, **REQUER-SE** a condenação das rés ao pagamento de um plus salarial no percentual de 20% sobre o salário do autor. Consequentemente, **REQUER-SE** a condenação das rés ao pagamento dos reflexos em horas extras, no 13º salário, no aviso prévio e nas férias proporcionais acrescidas de 1/3, FGTS e 40%."
        private val MOTORISTA_PARAGRAFO_14 get() = MOTORISTA_PARAGRAFO_14_A + MOTORISTA_PARAGRAFO_14_B + MOTORISTA_PARAGRAFO_14_C
        private const val MOTORISTA_PARAGRAFO_14_A = "Para fins de produção de prova a respeito desse tema, **REQUER-SE** a aplicação do 1º do art. 818 da CLT: "
        private val MOTORISTA_PARAGRAFO_14_B get() = MOTORISTA_P14_B1 + MOTORISTA_P14_B2
        private val MOTORISTA_PARAGRAFO_14_C get() = MOTORISTA_P14_C1 + MOTORISTA_P14_C2
        private const val MOTORISTA_P14_C1 = "poderá o juízo **atribuir o ônus da prova de modo diverso**, desde que o faça por decisão fundamentada, "
        private const val MOTORISTA_P14_C2 = "caso em que deverá dar à parte a oportunidade de se desincumbir do ônus que lhe foi atribuído.*"
        private const val MOTORISTA_P14_B1 = "*Nos casos previstos em lei ou diante de peculiaridades da causa relacionadas à impossibilidade ou à "
        private val MOTORISTA_P14_B2 get() = MOTORISTA_P14_B2_A + MOTORISTA_P14_B2_B
        private const val MOTORISTA_P14_B2_A = "**excessiva dificuldade de cumprir o encargo** nos termos deste artigo ou à "
        private const val MOTORISTA_P14_B2_B = "**maior facilidade de obtenção da prova do fato contrário**, "
        private const val MOTORISTA_PARAGRAFO_5 = "Nesse sentido, entende o doutrinador José Affonso Dallegrave Neto (**Responsabilidade civil no direito do trabalho.** 6. Ed. São Paulo: LTr, 2017, p. 278): *\"é inegável que ***o desvio funcional e a dupla função são tidos como ilícitos, na medida em que são caracterizados pela determinação unilateral do empregador, e ao mesmo tempo são prejudiciais ao obreiro, o qual terá de assumir responsabilidades e encargos superiores aos limites do contratado.*** Ao assim proceder, ***o empregador estará exorbitando seu poder de comando (jus variandi) em flagrante abuso de direito de que trata o art. 187 do Código Civil.*** Tais hipóteses caracterizam até mesmo ofensa ao art. 468 da CLT, pois entre a função ajustada na celebração do contrato e o que lhe foi imposto posteriormente haverá sensível margem prejudicial ao trabalhador, mormente quando desacompanhada da respectiva compensação salarial.\"* (grifo nosso)."
        private const val MOTORISTA_CITACAO_TRT_8 = """**Tribunal Regional do Trabalho da 8ª Região**
[...] III - ACÚMULO DE FUNÇÃO. FUNÇÃO DE MOTORISTA E CARREGADOR - __**Considerando que a testemunha do reclamante afirmou que ele realizava as atividades de motorista e também fazia o descarregamento de mercadorias, restou provado o acúmulo de função, pois fazer descarregamento da mercadoria, de maneira habitual, não pode ser imputada a um motorista, ainda que a sua condição física assim permita, pois é fora das suas atividades típicas**__. [...] Recurso provido. (TRT da 8ª Região; Processo: 0000577-87.2021.5.08.0003 ROT; Data: 07/07/2022; Órgão Julgador: 1ª Turma; Relator.: MARCUS AUGUSTO LOSADA MAIA)
(grifo nosso)"""
        private const val MOTORISTA_CITACAO_TRT_10 = """**Tribunal Regional do Trabalho da 10ª Região**
[...] ACÚMULO DE FUNÇÃO. ATIVIDADE INCOMPATÍVEL COM AQUELA CONTRATADA. COMPROVAÇÃO. 1. O quanto disposto no art. 456, parágrafo único, da CLT, não se aplica na hipótese em que o empregador, furtando-se a uma nova contratação, de forma não eventual e sem o correspondente acréscimo salarial, designa a um empregado inicialmente contratado para um conjunto específico de atividades, o desempenho de funções alheias àquelas, muitas vezes mais complexas, exigindo maior responsabilidade, esforço físico superior, maiores riscos à saúde, ou ainda, extrapolando a jornada normal. __**2. Caso em que, furtando-se a uma nova contratação, e de modo não eventual e sem qualquer diferencial remuneratório, o empregador submete o empregado contratado, especificamente, como motorista, ao acúmulo da função de carregador de mercadorias, atividade não compatível com o conjunto específico daquelas inerentes ao motorista.**__ 3. Recurso a que se nega provimento. [...] (TRT-10 0001575-23.2016.5.10.0020, Relator.: PEDRO LUÍS VICENTIN FOLTRAN, Data de Julgamento: 23/01/2019, Data de Publicação: 01/02/2019)
(grifo nosso)"""
        private const val MOTORISTA_CITACAO_TRT_14 = """**Tribunal Regional do Trabalho da 14ª Região**
[...] ACÚMULO DE FUNÇÃO. MOTORISTA. CARREGADOR. CARGA E DESCARGA. CARACTERIZAÇÃO. "PLUS" SALARIAL DEVIDO. __**Cabível o pagamento de adicional por acúmulo de função quando comprovado que o trabalhador, executou serviços estranhos à função para a qual não foi contratado.**__ (TRT-14 - Recurso Ordinário Trabalhista: 0000266-10.2018.5.14.0141, Relator.: CARLOS AUGUSTO GOMES LOBO, SEGUNDA TURMA - OJ de Análise de Recurso)
(grifo nosso)"""
        private val DIFERENCAS_SALARIAIS_ACUMULO_FUNCOES_TEMPLATE = """
A parte autora iniciou sua prestação de serviços em favor da parte ré em {dataAdmissao}, na função de {funcaoContratada}, mas, em {dataInicioAcumuloFuncao}, passou a também exercer a função de {funcaoAcumulada}.

Ou seja, mesmo tendo sido contratada para exercer a função de {funcaoContratada}, a parte ré não contratou outro empregado para fazer a função de {funcaoAcumulada}, levando a parte autora a acumular as duas funções.

As funções não eram compatíveis entre si ou com a condição pessoal da parte autora, não se configurando a hipótese do art. 456, parágrafo único, da CLT (*A falta de prova ou inexistindo cláusula expressa e tal respeito, entender-se-á que* ***o empregado se obrigou a todo e qualquer serviço compatível com a sua condição pessoal****.*).
* *
O acúmulo de função é configurado quando um trabalhador exerce, além da sua função, atividades de um cargo diferente, que não seja acessória ou tangencial à sua função contratada, gerando alteração prejudicial das condições laborais (art. 468, *caput*, da CLT: *Nos contratos individuais de trabalho* ***só é lícita a alteração das respectivas condições por mútuo consentimento, e ainda assim desde que não resultem, direta ou indiretamente, prejuízos ao empregado****, sob pena de nulidade da cláusula infringente desta garantia*.).

Nesse sentido, entende o doutrinador José Affonso Dallegrave Neto (**Responsabilidade civil no direito do trabalho**. 6. Ed. São Paulo: LTr, 2017, p. 278): “*é inegável que* ***o desvio funcional e a dupla função são tidos como ilícitos, na medida em que são caracterizados pela determinação unilateral do empregador, e ao mesmo tempo são prejudiciais ao obreiro, o qual terá de assumir responsabilidades e encargos superiores aos limites do contratado****. Ao assim proceder,* ***o empregador estará exorbitando seu poder de comando (jus variandi) em flagrante abuso de direito de que trata o art. 187 do Código Civil****. Tais hipóteses caracterizam até mesmo ofensa ao art. 468 da CLT, pois entre a função ajustada na celebração do contrato e o que lhe foi imposto posteriormente haverá sensível margem prejudicial ao trabalhador, mormente quando desacompanhada da respectiva compensação salarial.*” (grifo nosso).

Como a parte autora não recebeu a remuneração devida para o exercício concomitante de todas as funções que exercia na prestação de trabalho em favor da ré, houve enriquecimento sem justa causa do empregador, à luz do **art. 884 do Código Civil** (*Aquele que, sem justa causa, se enriquecer à custa de outrem, será obrigado a restituir o indevidamente auferido, feita a atualização dos valores monetários.*).

A conduta da ré é fraudulenta, nos termos do **art. 9º da CLT**, pois configurou obstáculo à aplicação dos preceitos contidos na legislação trabalhista, especialmente no tocante à remuneração justa pelo labor desempenhado, em afronta ao **art. 7º, VI, da Constituição Federal** (*irredutibilidade do salário*), por acarretar uma forma indireta de redução salarial, bem como ao **art. 7º, X, da Constituição Federal** (*proteção do salário na forma da lei*).

Pelo exposto, com fundamento no **art. 187 da CLT**, **REQUER-SE** a condenação da parte ré ao pagamento de diferenças salariais decorrentes do acúmulo de funções, correspondentes ao salário de {salarioFuncaoContratada} somado ao salário de {salarioFuncaoAcumulada}. Consequentemente, **REQUER-SE** a condenação da ré ao pagamento dos reflexos em horas extras, no 13º salário, no aviso prévio e nas férias proporcionais acrescidas de 1/3, FGTS e 40%.

Sucessivamente, **REQUER-SE** a condenação da parte ré ao pagamento de diferenças salariais decorrentes do acúmulo de funções, correspondentes ao acréscimo de 40% sobre o salário de {salarioAtualAutora}. Consequentemente, **REQUER-SE** a condenação da ré ao pagamento dos reflexos em horas extras, no 13º salário, no aviso prévio e nas férias proporcionais acrescidas de 1/3, FGTS e 40%.
""".trimIndent()
        private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        private val LONG_DATE_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy", Locale("pt", "BR"))
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
