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
                        imagensFixas = fixedImages(blockId)
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
        return ordered
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
            else -> emptyList()
        }

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
        val generate: (Processo, Set<Usuario>, Map<String, String?>, Set<String>) -> String
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
            DESVIO_FUNCAO_ATIVIDADE_EFETIVAMENTE_EXERCIDA
        )
        private const val PLACEHOLDER = "___"
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
