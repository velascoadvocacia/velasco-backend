package com.example.lawyer.service

import com.example.lawyer.domain.enums.EstadoCivil
import com.example.lawyer.domain.enums.TratamentoAdvogado
import com.example.lawyer.domain.model.Endereco
import com.example.lawyer.domain.model.Pessoa
import com.example.lawyer.domain.model.Processo
import com.example.lawyer.domain.model.Usuario
import com.example.lawyer.dto.request.ProcuracaoExportRequest
import com.example.lawyer.exception.ResourceNotFoundException
import com.example.lawyer.repository.UsuarioRepository
import jakarta.enterprise.context.ApplicationScoped
import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy
import org.apache.poi.xwpf.usermodel.BreakType
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.UnderlinePatterns
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STLineSpacingRule
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STPageOrientation
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@ApplicationScoped
class ProcuracaoExportService(
    private val processoService: ProcessoService,
    private val pessoaService: PessoaService,
    private val usuarioRepository: UsuarioRepository,
    @ConfigProperty(name = "rt.escritorio.endereco")
    private val enderecoEscritorio: String
) {
    fun generate(request: ProcuracaoExportRequest): ProcuracaoGerada {
        val processo = request.processoId?.let(processoService::findEntity)
        val reclamante = resolvePessoas(request.reclamantesIds, processo?.reclamantes?.toList()).firstOrNull()
        val reclamada = resolvePessoas(request.reclamadasIds, processo?.reclamadas?.toList()).firstOrNull()
        val advogados = resolveAdvogados(request.advogadosIds, processo?.advogados?.toList())
        val dados = DadosDocumento(reclamante, reclamada, advogados, LocalDate.now())

        val bytes = XWPFDocument().use { document ->
            configurePage(document)
            addHeaderAndFooter(document)
            createProcuracao(document, dados)
            addPageBreak(document)
            createContrato(document, dados)
            addPageBreak(document)
            createDeclaracao(document, dados)
            ByteArrayOutputStream().use { output ->
                document.write(output)
                output.toByteArray()
            }
        }
        return ProcuracaoGerada(reclamante?.nome.orPlaceholder(), bytes)
    }

    private fun createProcuracao(document: XWPFDocument, dados: DadosDocumento) {
        title(document, "PROCURAÇÃO AD JUDICIA")
        spacer(document)
        labeledParagraph(document, "OUTORGANTE:", dados.qualificacaoReclamante(comNascimento = true), boldValue = true)
        spacer(document)
        labeledParagraph(document, "OUTORGADOS:", dados.listaOutorgados())
        labeledParagraph(
            document,
            "PODERES:",
            "Outorga(m) os mais amplos e gerais poderes da cláusula   “ad judicia et extra” para o foro em geral, qualquer instância ordinária ou extraordinária, na qual o autor ou réu litisconsorte ativo ou passivo, denunciado ou podendo ainda, utilizar dos poderes especiais para firmar compromisso de qualquer   natureza,   concordar   com contas e cálculos, transigir, variar, fazer acordos, receber e dar quitação, receber alvará judicial e guias de retirada, desistir, renunciar ao direito sobre o que se funda a ação, podendo ainda receber importância depositadas em nome do outorgante à conta bancaria referente ao processo encaminhado com o presente, em quaisquer agência bancaria, podendo atuar em conjunto ou separadamente, bem como substabelecer com ou sem reserva de poderes."
        )
        labeledParagraph(
            document,
            "PODERES ESPECIAIS:",
            "Para o fim especial de propor Reclamação Trabalhista em face de ${dados.nomeReclamada()} e outros."
        )
        spacer(document)
        body(document, "$CIDADE_ESCRITORIO, ${formatLongDate(dados.dataExportacao)}.", alignment = ParagraphAlignment.CENTER)
        repeat(2) { spacer(document) }
        signature(document, dados.nomeReclamante())
    }

    private fun createContrato(document: XWPFDocument, dados: DadosDocumento) {
        title(document, "CONTRATO DE HONORÁRIOS")
        body(
            document,
            "Por um lado, CONTRATADO: ${dados.primeiroAdvogado()}, estabelecido na $enderecoEscritorio, e de outro lado, CONTRATANTE: o (a) ${dados.qualificacaoReclamante(comNascimento = true)}, doravante denominado (a/s) de CONSTITUINTE, convencionam e contratam as cláusulas e condições seguintes:"
        )
        body(document, "1ª - Os CONSTITUÍDOS se comprometem em cumprimento ao mandato recebido, a patrocinar a causa do (a) CONSTITUINTE, que consiste em propor Reclamação Trabalhista em face de ${dados.nomeReclamada()} e outros.")
        body(document, "2ª - Em contraprestação, o (a/s) CONSTITUINTE se compromete a remunerar os serviços profissionais dos CONSTITUÍDOS na importância correspondente a 30% (trinta por cento) dos valores que vier a receber, a título de acordos ou liquidação de sentença, inclusive sobre valores de FGTS e do seguro desemprego. Em caso de acordo realizado diretamente entre autor e réu, sem anuência do CONSTITUIDO, o percentual de honorários incidirá sobre o valor dos pedidos constantes na petição inicial. Fica ajustado que haverá acréscimo de 5% (cinco por cento) sobre os honorários anteriormente pactuados na hipótese de efetiva atuação em grau recursal, consistente na elaboração, interposição ou acompanhamento de recursos contra decisões proferidas em 1º grau, a serem realizados por escritório parceiro sediado na cidade de Curitiba.")
        body(document, "3ª - Os percentuais retros pactuados são independentes de honorários advocatícios ou assistências que venham a ser deferidos em sentença.")
        body(document, "4ª - Os honorários e valores constantes nas cláusulas 2ª, 3ª e 4ª, deste contrato, ficam ajustados como valor líquido, certo e exigível para efeito de execução, valendo o presente instrumento como título de crédito (CPC, art. 586 c/c art. 585, I e art. 24 da lei 8906/94).")
        body(document, "5ª. – Todas à custa e despesas ligadas direta ou indiretamente com o processo, incluindo-se custas iniciais, taxas, honorários periciais, assistência técnica, fotocópias, emolumentos, viagens, portes, honorários de sucumbência, etc., ficarão a cargo do CONTRATANTE, que disponibilizará o numerário necessário no decorrer do processo ou ao final do mesmo, conforme o caso.")
        body(document, "6ª. – O não comparecimento injustificado do(a) CONTRATANTE às audiências designadas, bem como qualquer conduta que resulte na desistência da ação ou inviabilize o regular andamento do processo, autoriza a rescisão do presente contrato por culpa exclusiva do(a) CONTRATANTE. Nessas hipóteses, será devida aos CONSTITUÍDOS, a título de cláusula penal, multa equivalente a 01 (um) salário mínimo vigente, sem prejuízo do pagamento dos honorários proporcionais pelos serviços já prestados e das despesas eventualmente suportadas.")
        body(document, "7ª. - Fica eleito o foro de Cascavel - PR para dirimir quaisquer dúvidas acerca deste contrato, prevalecendo sobre qualquer outro por mais privilegiado que for.")
        body(document, "E por estarem assim justos e contratados, obrigam-se a cumprir todas as disposições do presente instrumento que assinam na presença das testemunhas abaixo firmadas, para que surta seus jurídicos e legais efeitos.")
        body(document, "$CIDADE_ESCRITORIO, ${formatLongDate(dados.dataExportacao)}.", alignment = ParagraphAlignment.CENTER)
        spacer(document)
        signature(document, "${dados.nomeReclamante()}                                      ADVOGADO:")
        spacer(document)
        body(document, "TESTEMUNHAS", bold = true, alignment = ParagraphAlignment.CENTER)
    }

    private fun createDeclaracao(document: XWPFDocument, dados: DadosDocumento) {
        title(document, "DECLARAÇÃO DE HIPOSSUFICIÊNCIA")
        spacer(document)
        body(
            document,
            "Eu, ${dados.qualificacaoReclamante(comNascimento = false)}, declaro para os devidos fins, de direito sob pena da lei, ser pessoa pobre não tendo condições de arcar com despesas e custas processuais, sem prejuízo de minha subsistência e de meus dependentes."
        )
        body(document, "Por ser expressão da verdade firmo o presente.")
        spacer(document)
        body(document, "$CIDADE_ESCRITORIO, ${formatLongDate(dados.dataExportacao)}.", alignment = ParagraphAlignment.CENTER)
        repeat(2) { spacer(document) }
        signature(document, dados.nomeReclamante())
    }

    private fun title(document: XWPFDocument, text: String) {
        val paragraph = document.createParagraph().apply { alignment = ParagraphAlignment.CENTER }
        paragraph.createRun().apply {
            setText(text)
            isBold = true
            underline = UnderlinePatterns.SINGLE
            fontFamily = FONT
            fontSize = 12
        }
    }

    private fun labeledParagraph(
        document: XWPFDocument,
        label: String,
        value: String,
        boldValue: Boolean = false
    ) {
        val paragraph = baseParagraph(document)
        run(paragraph, label, bold = true)
        run(paragraph, " $value", bold = boldValue)
    }

    private fun body(
        document: XWPFDocument,
        text: String,
        bold: Boolean = false,
        alignment: ParagraphAlignment = ParagraphAlignment.BOTH
    ) {
        val paragraph = baseParagraph(document).apply { this.alignment = alignment }
        run(paragraph, text, bold)
    }

    private fun signature(document: XWPFDocument, text: String) {
        body(document, text, alignment = ParagraphAlignment.CENTER)
    }

    private fun baseParagraph(document: XWPFDocument): XWPFParagraph = document.createParagraph().apply {
        alignment = ParagraphAlignment.BOTH
        spacingAfter = 80
        val spacing = ctp.pPr?.spacing ?: ctp.addNewPPr().addNewSpacing()
        spacing.line = BigInteger.valueOf(240)
        spacing.lineRule = STLineSpacingRule.AUTO
    }

    private fun run(paragraph: XWPFParagraph, text: String, bold: Boolean = false) {
        paragraph.createRun().apply {
            setText(text)
            isBold = bold
            fontFamily = FONT
            fontSize = 10
        }
    }

    private fun spacer(document: XWPFDocument) {
        document.createParagraph().apply { spacingAfter = 0 }
    }

    private fun addPageBreak(document: XWPFDocument) {
        document.createParagraph().createRun().addBreak(BreakType.PAGE)
    }

    private fun configurePage(document: XWPFDocument) {
        val section = document.document.body.addNewSectPr()
        section.pgSz = CTPageSz.Factory.newInstance().apply {
            w = BigInteger.valueOf(11_910)
            h = BigInteger.valueOf(16_840)
            orient = STPageOrientation.PORTRAIT
        }
        section.pgMar = CTPageMar.Factory.newInstance().apply {
            top = BigInteger.valueOf(1_620)
            right = BigInteger.valueOf(1_160)
            bottom = BigInteger.valueOf(2_040)
            left = BigInteger.valueOf(1_160)
            header = BigInteger.valueOf(159)
            footer = BigInteger.valueOf(1_846)
            gutter = BigInteger.ZERO
        }
    }

    private fun addHeaderAndFooter(document: XWPFDocument) {
        val policy = document.createHeaderFooterPolicy()
        val header = policy.createHeader(XWPFHeaderFooterPolicy.DEFAULT)
        val headerParagraph = header.createParagraph().apply { alignment = ParagraphAlignment.CENTER }
        javaClass.getResourceAsStream("/assets/header_velasco.png")!!.use { input ->
            headerParagraph.createRun().addPicture(
                input, XWPFDocument.PICTURE_TYPE_PNG, "header_velasco.png",
                7_543_800, 955_675
            )
        }
        val footer = policy.createFooter(XWPFHeaderFooterPolicy.DEFAULT)
        val footerParagraph = footer.createParagraph().apply { alignment = ParagraphAlignment.CENTER }
        javaClass.getResourceAsStream("/assets/footer_velasco.png")!!.use { input ->
            footerParagraph.createRun().addPicture(
                input, XWPFDocument.PICTURE_TYPE_PNG, "footer_velasco.png",
                7_554_595, 880_745
            )
        }
    }

    private fun resolvePessoas(ids: List<Long>, fallback: List<Pessoa>?): List<Pessoa> =
        if (ids.isNotEmpty()) ids.distinct().map(pessoaService::findEntity) else fallback.orEmpty()

    private fun resolveAdvogados(ids: List<Long>, fallback: List<Usuario>?): List<Usuario> =
        if (ids.isNotEmpty()) {
            ids.distinct().map { id ->
                usuarioRepository.findActiveById(id) ?: throw ResourceNotFoundException("Advogado $id não encontrado")
            }
        } else fallback.orEmpty().filter { it.ativo }

    private inner class DadosDocumento(
        private val reclamante: Pessoa?,
        private val reclamada: Pessoa?,
        private val advogados: List<Usuario>,
        val dataExportacao: LocalDate
    ) {
        fun nomeReclamante() = reclamante?.nome.orPlaceholder()
        fun nomeReclamada() = reclamada?.nome.orPlaceholder()

        fun qualificacaoReclamante(comNascimento: Boolean): String = buildString {
            append(nomeReclamante()).append(", ")
            append(reclamante?.nacionalidade.orPlaceholder()).append(", ")
            append(formatEstadoCivil(reclamante?.estadoCivil)).append(", inscrito(a) no CPFMF sob n.º ")
            append(reclamante?.cpf.orPlaceholder()).append(", e no RG sob o n.º ")
            append(formatRg(reclamante))
            if (comNascimento) {
                append(", nascido(a) em ").append(reclamante?.dataNascimento?.let(::formatLongDate).orPlaceholder())
            }
            append(", residente e domiciliado(a) em ")
            append(reclamante?.endereco?.cidade.orPlaceholder()).append(" – ")
            append(reclamante?.endereco?.estado.orPlaceholder()).append(", ")
            append(formatAddress(reclamante?.endereco)).append(", CEP ")
            append(formatCep(reclamante?.endereco?.cep)).append(", telefone ")
            append(reclamante?.telefone.orPlaceholder())
        }

        fun listaOutorgados(): String = advogados.takeIf { it.isNotEmpty() }
            ?.joinToString(", ") { formatAdvogado(it) }
            ?.plus(", todos estabelecidos na $enderecoEscritorio.")
            ?: "___, todos estabelecidos na $enderecoEscritorio."

        fun primeiroAdvogado(): String = advogados.firstOrNull()?.let(::formatAdvogado) ?: PLACEHOLDER
    }

    private fun formatAdvogado(usuario: Usuario): String {
        val pessoa = usuario.pessoa
        val generoFeminino = usuario.tratamento == TratamentoAdvogado.DRA
        val profissao = if (generoFeminino) "advogada" else "advogado"
        val inscrito = if (generoFeminino) "inscrita" else "inscrito"
        return "${pessoa?.nome.orPlaceholder()}, ${pessoa?.nacionalidade.orPlaceholder()}, " +
            "${formatEstadoCivil(pessoa?.estadoCivil)}, $profissao, $inscrito na " +
            "OAB/${usuario.ufOab.orPlaceholder()} nº ${usuario.numeroOab.orPlaceholder()}"
    }

    private fun formatRg(pessoa: Pessoa?): String = listOfNotNull(
        pessoa?.rg?.trim()?.takeIf { it.isNotEmpty() },
        pessoa?.orgaoEmissorRg?.trim()?.takeIf { it.isNotEmpty() }
    ).joinToString(" ").ifBlank { PLACEHOLDER }

    private fun formatAddress(endereco: Endereco?): String = listOfNotNull(
        endereco?.rua?.trim()?.takeIf { it.isNotEmpty() },
        endereco?.numero?.trim()?.takeIf { it.isNotEmpty() },
        endereco?.complemento?.trim()?.takeIf { it.isNotEmpty() },
        endereco?.bairro?.trim()?.takeIf { it.isNotEmpty() }?.let { "Bairro $it" }
    ).joinToString(", ").ifBlank { PLACEHOLDER }

    private fun formatCep(value: String?): String {
        val digits = value?.filter(Char::isDigit).orEmpty()
        return if (digits.length == 8) "${digits.take(5)}-${digits.drop(5)}" else value.orPlaceholder()
    }

    private fun formatEstadoCivil(value: EstadoCivil?): String = when (value) {
        EstadoCivil.SOLTEIRO -> "solteiro"
        EstadoCivil.CASADO -> "casado"
        EstadoCivil.DIVORCIADO -> "divorciado"
        EstadoCivil.VIUVO -> "viúvo"
        EstadoCivil.UNIAO_ESTAVEL -> "união estável"
        EstadoCivil.SEPARADO -> "separado"
        null -> PLACEHOLDER
    }

    private fun formatLongDate(date: LocalDate): String = date.format(LONG_DATE_FORMATTER)
    private fun String?.orPlaceholder(): String = this?.trim()?.takeIf(String::isNotEmpty) ?: PLACEHOLDER

    data class ProcuracaoGerada(val nomeReclamante: String, val bytes: ByteArray)

    private companion object {
        const val PLACEHOLDER = "___"
        const val FONT = "Georgia"
        const val CIDADE_ESCRITORIO = "Cascavel"
        val LONG_DATE_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy", Locale("pt", "BR"))
    }
}
