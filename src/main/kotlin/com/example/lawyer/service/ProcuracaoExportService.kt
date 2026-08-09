package com.example.lawyer.service

import com.example.lawyer.domain.enums.EstadoCivil
import com.example.lawyer.domain.enums.TratamentoAdvogado
import com.example.lawyer.domain.enums.Sexo
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
import org.apache.poi.xwpf.usermodel.TableRowAlign
import org.apache.poi.xwpf.usermodel.UnderlinePatterns
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STLineSpacingRule
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STPageOrientation
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTabJc
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
    private val docxHeaderService: DocxHeaderService,
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
        return ProcuracaoGerada(reclamante?.nome.clean() ?: "documento", bytes)
    }

    private fun createProcuracao(document: XWPFDocument, dados: DadosDocumento) {
        title(document, "PROCURAÇÃO AD JUDICIA")
        spacer(document)
        qualificationParagraph(document, "OUTORGANTE:", dados.qualificacaoReclamante(comNascimento = true), fixedTextColumn = true)
        blockSpacer(document)
        outorgadosParagraph(document, dados.outorgados())
        labeledParagraph(
            document,
            "PODERES:",
            "Outorga(m) os mais amplos e gerais poderes da cláusula   “ad judicia et extra” para o foro em geral, qualquer instância ordinária ou extraordinária, na qual o autor ou réu litisconsorte ativo ou passivo, denunciado ou podendo ainda, utilizar dos poderes especiais para firmar compromisso de qualquer   natureza,   concordar   com contas e cálculos, transigir, variar, fazer acordos, receber e dar quitação, receber alvará judicial e guias de retirada, desistir, renunciar ao direito sobre o que se funda a ação, podendo ainda receber importância depositadas em nome do outorgante à conta bancaria referente ao processo encaminhado com o presente, em quaisquer agência bancaria, podendo atuar em conjunto ou separadamente, bem como substabelecer com ou sem reserva de poderes.",
            fixedTextColumn = true
        )
        blockSpacer(document)
        poderesEspeciaisParagraph(document, dados.nomeReclamada())
        dateAndSignatureBlock(
            document, dados.dataExportacao, dados.nomeReclamante(), PROCURACAO_SIGNATURE_SPACE_BEFORE_TWIPS
        )
    }

    private fun createContrato(document: XWPFDocument, dados: DadosDocumento) {
        title(document, "CONTRATO DE HONORÁRIOS")
        spacer(document)
        contractOpening(document, dados)
        numberedClause(document, "1ª -", "Os CONSTITUÍDOS se comprometem em cumprimento ao mandato recebido, a patrocinar a causa do (a) CONSTITUINTE, que consiste em ${dados.acaoContraReclamada()}.")
        numberedClause(
            document,
            "2ª -",
            "Em contraprestação, o (a/s) CONSTITUINTE se compromete a remunerar os serviços profissionais dos CONSTITUÍDOS na importância correspondente a 30% (trinta por cento) dos valores que vier a receber, a título de acordos ou liquidação de sentença, inclusive sobre valores de FGTS e do seguro desemprego. Em caso de acordo realizado diretamente entre autor e réu, sem anuência do CONSTITUIDO, o percentual de honorários incidirá sobre o valor dos pedidos constantes na petição inicial. Fica ajustado que haverá acréscimo de 5% (cinco por cento) sobre os honorários anteriormente pactuados na hipótese de efetiva atuação em grau recursal, consistente na elaboração, interposição ou acompanhamento de recursos contra decisões proferidas em 1º grau, a serem realizados por escritório parceiro sediado na cidade de Curitiba.",
            boldFragments = listOf("30% (trinta por cento)", "5% (cinco por cento)")
        )
        numberedClause(document, "3ª -", "Os percentuais retros pactuados são independentes de honorários advocatícios ou assistências que venham a ser deferidos em sentença.")
        numberedClause(document, "4ª -", "Os honorários e valores constantes nas cláusulas 2ª, 3ª e 4ª, deste contrato, ficam ajustados como valor líquido, certo e exigível para efeito de execução, valendo o presente instrumento como título de crédito (CPC, art. 586 c/c art. 585, I e art. 24 da lei 8906/94).")
        numberedClause(document, "5ª -", "Todas à custa e despesas ligadas direta ou indiretamente com o processo, incluindo-se custas iniciais, taxas, honorários periciais, assistência técnica, fotocópias, emolumentos, viagens, portes, honorários de sucumbência, etc., ficarão a cargo do CONTRATANTE, que disponibilizará o numerário necessário no decorrer do processo ou ao final do mesmo, conforme o caso.")
        numberedClause(document, "6ª -", "O não comparecimento injustificado do(a) CONTRATANTE às audiências designadas, bem como qualquer conduta que resulte na desistência da ação ou inviabilize o regular andamento do processo, autoriza a rescisão do presente contrato por culpa exclusiva do(a) CONTRATANTE. Nessas hipóteses, será devida aos CONSTITUÍDOS, a título de cláusula penal, multa equivalente a 01 (um) salário mínimo vigente, sem prejuízo do pagamento dos honorários proporcionais pelos serviços já prestados e das despesas eventualmente suportadas.")
        numberedClause(document, "7ª -", "Fica eleito o foro de Cascavel - PR para dirimir quaisquer dúvidas acerca deste contrato, prevalecendo sobre qualquer outro por mais privilegiado que for.")
        body(document, "E por estarem assim justos e contratados, obrigam-se a cumprir todas as disposições do presente instrumento que assinam na presença das testemunhas abaixo firmadas, para que surta seus jurídicos e legais efeitos.")
        contractDateAndSignatureBlock(document, dados.dataExportacao, dados.nomeReclamante())
    }

    private fun createDeclaracao(document: XWPFDocument, dados: DadosDocumento) {
        title(document, "DECLARAÇÃO DE HIPOSSUFICIÊNCIA")
        spacer(document)
        declarationOpening(document, dados.qualificacaoReclamante(comNascimento = false))
        body(document, "Por ser expressão da verdade firmo o presente.")
        dateAndSignatureBlock(
            document, dados.dataExportacao, dados.nomeReclamante(), DECLARACAO_SIGNATURE_SPACE_BEFORE_TWIPS
        )
    }

    private fun declarationOpening(document: XWPFDocument, qualification: Qualification) {
        val paragraph = baseParagraph(document)
        run(paragraph, "Eu")
        qualification.name.takeIf(String::isNotBlank)?.let {
            run(paragraph, ", ${it.uppercase(PT_BR)}", bold = true)
        }
        qualification.details.takeIf(String::isNotBlank)?.let { run(paragraph, ", $it") }
        run(
            paragraph,
            ", declaro para os devidos fins, de direito sob pena da lei, ser pessoa pobre não tendo condições de arcar com despesas e custas processuais, sem prejuízo de minha subsistência e de meus dependentes."
        )
    }

    private fun title(document: XWPFDocument, text: String) {
        val paragraph = document.createParagraph().apply(::centerOnPage)
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
        boldValue: Boolean = false,
        fixedTextColumn: Boolean = false
    ) {
        val paragraph = baseParagraph(document)
        if (fixedTextColumn) applyFixedTextColumn(paragraph)
        run(paragraph, label, bold = true)
        if (fixedTextColumn) tab(paragraph) else run(paragraph, " ")
        run(paragraph, value, bold = boldValue)
    }

    private fun qualificationParagraph(
        document: XWPFDocument,
        label: String,
        qualification: Qualification,
        fixedTextColumn: Boolean = false
    ) {
        val paragraph = baseParagraph(document)
        if (fixedTextColumn) applyFixedTextColumn(paragraph)
        run(paragraph, label, bold = true)
        if (fixedTextColumn) tab(paragraph) else run(paragraph, " ")
        qualification.name.takeIf { it.isNotBlank() }?.let { run(paragraph, it.uppercase(PT_BR), bold = true) }
        qualification.details.takeIf { it.isNotBlank() }?.let { run(paragraph, ", $it") }
    }

    private fun outorgadosParagraph(document: XWPFDocument, advogados: List<AdvogadoFormatado>) {
        val paragraph = baseParagraph(document)
        applyFixedTextColumn(paragraph)
        run(paragraph, "OUTORGADOS:", bold = true)
        tab(paragraph)
        if (advogados.isEmpty()) {
            run(paragraph, " Todos estabelecidos na $enderecoEscritorio.")
            return
        }
        advogados.forEachIndexed { index, advogado ->
            run(paragraph, if (index == 0) advogado.nome else ", ${advogado.nome}", bold = true)
            advogado.detalhes.takeIf(String::isNotBlank)?.let { run(paragraph, ", $it") }
        }
        run(paragraph, ", todos estabelecidos na $enderecoEscritorio.")
    }

    private fun poderesEspeciaisParagraph(document: XWPFDocument, nomeReclamada: String) {
        val paragraph = baseParagraph(document)
        run(paragraph, "PODERES ESPECIAIS:", bold = true)
        run(paragraph, " Para o fim especial de propor Reclamação Trabalhista")
        if (nomeReclamada.isNotBlank()) {
            run(paragraph, " em face de ")
            run(paragraph, nomeReclamada, bold = true, underline = true)
            run(paragraph, " e outros")
        }
        run(paragraph, ".")
    }

    private fun applyFixedTextColumn(paragraph: XWPFParagraph) {
        applyTextColumn(paragraph, TEXT_COLUMN_POSITION_TWIPS)
    }

    private fun applyTextColumn(paragraph: XWPFParagraph, positionTwips: Int, leftIndentTwips: Int = 0) {
        paragraph.indentationLeft = positionTwips
        paragraph.indentationHanging = positionTwips - leftIndentTwips
        val properties = paragraph.ctp.pPr ?: paragraph.ctp.addNewPPr()
        val tabs = properties.tabs ?: properties.addNewTabs()
        tabs.addNewTab().apply {
            `val` = STTabJc.LEFT
            pos = BigInteger.valueOf(positionTwips.toLong())
        }
    }

    private fun tab(paragraph: XWPFParagraph) {
        paragraph.createRun().addTab()
    }

    private fun contractOpening(document: XWPFDocument, dados: DadosDocumento) {
        val paragraph = baseParagraph(document)
        run(paragraph, "Por um lado, CONTRATADO: ")
        dados.primeiroAdvogado()?.let { advogado ->
            run(paragraph, advogado.nome, bold = true)
            advogado.detalhes.takeIf(String::isNotBlank)?.let { run(paragraph, ", $it") }
        }
        run(paragraph, ", estabelecido na $enderecoEscritorio, e de outro lado, CONTRATANTE: o (a) ")
        val qualification = dados.qualificacaoReclamante(comNascimento = true)
        qualification.name.takeIf { it.isNotBlank() }?.let { run(paragraph, it.uppercase(PT_BR), bold = true) }
        qualification.details.takeIf { it.isNotBlank() }?.let { run(paragraph, ", $it") }
        run(paragraph, ", doravante denominado (a/s) de CONSTITUINTE, convencionam e contratam as cláusulas e condições seguintes:")
    }

    private fun numberedClause(
        document: XWPFDocument,
        label: String,
        text: String,
        boldFragments: List<String> = emptyList()
    ) {
        val paragraph = baseParagraph(document)
        applyTextColumn(paragraph, CLAUSE_TEXT_COLUMN_POSITION_TWIPS, CLAUSE_LEFT_INDENT_TWIPS)
        run(paragraph, label)
        tab(paragraph)
        appendWithBoldFragments(paragraph, text, boldFragments)
    }

    private fun appendWithBoldFragments(paragraph: XWPFParagraph, text: String, boldFragments: List<String>) {
        var cursor = 0
        boldFragments.forEach { fragment ->
            val start = text.indexOf(fragment, cursor)
            if (start >= 0) {
                if (start > cursor) run(paragraph, text.substring(cursor, start))
                run(paragraph, fragment, bold = true)
                cursor = start + fragment.length
            }
        }
        if (cursor < text.length) run(paragraph, text.substring(cursor))
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

    private fun dateAndSignatureBlock(
        document: XWPFDocument,
        date: LocalDate,
        text: String,
        spaceBeforeTwips: Int
    ) {
        val dateParagraph = baseParagraph(document).apply {
            alignment = ParagraphAlignment.CENTER
            spacingBefore = spaceBeforeTwips
            spacingAfter = DATE_SIGNATURE_GAP_TWIPS
        }
        keepTogether(dateParagraph, keepWithNext = true)
        run(dateParagraph, "$CIDADE_ESCRITORIO, ${formatLongDate(date)}.")

        val signature = baseParagraph(document).apply {
            alignment = ParagraphAlignment.CENTER
            spacingBefore = 0
            indentationLeft = SIGNATURE_SIDE_INDENT_TWIPS
            indentationRight = SIGNATURE_SIDE_INDENT_TWIPS
        }
        keepTogether(signature)
        addSignatureLine(signature)
        run(signature, text, bold = true)
    }

    private fun contractDateAndSignatureBlock(document: XWPFDocument, date: LocalDate, nomeReclamante: String) {
        val dateParagraph = baseParagraph(document).apply {
            alignment = ParagraphAlignment.CENTER
            spacingBefore = CONTRATO_SIGNATURE_SPACE_BEFORE_TWIPS
            spacingAfter = DATE_SIGNATURE_GAP_TWIPS
        }
        keepTogether(dateParagraph, keepWithNext = true)
        run(dateParagraph, "$CIDADE_ESCRITORIO, ${formatLongDate(date)}.")

        contractSignatureTable(document, nomeReclamante, "ADVOGADO:", keepWithNext = true)

        val witnesses = baseParagraph(document).apply {
            alignment = ParagraphAlignment.CENTER
            spacingBefore = WITNESSES_SPACE_BEFORE_TWIPS
            spacingAfter = DATE_SIGNATURE_GAP_TWIPS
        }
        keepTogether(witnesses, keepWithNext = true)
        run(witnesses, "TESTEMUNHAS", bold = true)
        contractSignatureTable(document, "TESTEMUNHA 1", "TESTEMUNHA 2")
    }

    private fun contractSignatureTable(
        document: XWPFDocument,
        leftLabel: String,
        rightLabel: String,
        keepWithNext: Boolean = false
    ) {
        val table = document.createTable(1, 3).apply {
            setWidth(CONTRACT_SIGNATURE_TABLE_WIDTH_TWIPS)
            setTableAlignment(TableRowAlign.CENTER)
            removeBorders()
            setCellMargins(0, 0, 0, 0)
        }
        val row = table.getRow(0).apply { setCantSplitRow(true) }
        listOf(0 to leftLabel, 2 to rightLabel).forEach { (index, label) ->
            row.getCell(index).apply { setWidth(CONTRACT_SIGNATURE_CELL_WIDTH_TWIPS.toString()) }
                .paragraphs.first().apply {
                    alignment = ParagraphAlignment.CENTER
                    spacingBefore = 0
                    spacingAfter = 0
                    keepTogether(this, keepWithNext)
                    addSignatureLine(this)
                    run(this, label, bold = true)
                }
        }
        row.getCell(1).setWidth(CONTRACT_SIGNATURE_GAP_WIDTH_TWIPS.toString())
    }

    private fun addSignatureLine(paragraph: XWPFParagraph) {
        val properties = paragraph.ctp.pPr ?: paragraph.ctp.addNewPPr()
        val borders = properties.pBdr ?: properties.addNewPBdr()
        borders.addNewTop().apply {
            `val` = STBorder.SINGLE
            sz = BigInteger.valueOf(8)
            space = BigInteger.valueOf(4)
        }
    }

    private fun keepTogether(paragraph: XWPFParagraph, keepWithNext: Boolean = false) {
        val properties = paragraph.ctp.pPr ?: paragraph.ctp.addNewPPr()
        if (!properties.isSetKeepLines) properties.addNewKeepLines()
        if (keepWithNext && !properties.isSetKeepNext) properties.addNewKeepNext()
    }

    private fun baseParagraph(document: XWPFDocument): XWPFParagraph = document.createParagraph().apply {
        alignment = ParagraphAlignment.BOTH
        spacingAfter = 80
        val spacing = ctp.pPr?.spacing ?: ctp.addNewPPr().addNewSpacing()
        spacing.line = BigInteger.valueOf(240)
        spacing.lineRule = STLineSpacingRule.AUTO
    }

    private fun run(
        paragraph: XWPFParagraph,
        text: String,
        bold: Boolean = false,
        underline: Boolean = false
    ) {
        paragraph.createRun().apply {
            setText(text)
            isBold = bold
            if (underline) this.underline = UnderlinePatterns.SINGLE
            fontFamily = FONT
            fontSize = BODY_FONT_SIZE
        }
    }

    private fun spacer(document: XWPFDocument) {
        document.createParagraph().apply { spacingAfter = 0 }
    }

    private fun blockSpacer(document: XWPFDocument) = spacer(document)

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
            bottom = BigInteger.valueOf(BODY_BOTTOM_MARGIN_TWIPS.toLong())
            left = BigInteger.valueOf(1_160)
            header = BigInteger.valueOf(159)
            footer = BigInteger.valueOf(1_846)
            gutter = BigInteger.ZERO
        }
    }

    private fun addHeaderAndFooter(document: XWPFDocument) {
        docxHeaderService.addHeader(document)
        val policy = document.createHeaderFooterPolicy()
        val footer = policy.createFooter(XWPFHeaderFooterPolicy.DEFAULT)
        val footerLineParagraph = (footer.paragraphs.firstOrNull() ?: footer.createParagraph()).apply {
            centerOnPage(this)
            spacingAfter = 0
        }
        val footerLineSize = proportionalImageSize(FOOTER_LINE_WIDTH_PX, FOOTER_LINE_HEIGHT_PX)
        javaClass.getResourceAsStream("/assets/footer_line.png")!!.use { input ->
            footerLineParagraph.createRun().addPicture(
                input, XWPFDocument.PICTURE_TYPE_PNG, "footer_line.png",
                footerLineSize.widthEmu, footerLineSize.heightEmu
            )
        }

        val footerParagraph = footer.createParagraph().apply {
            centerOnPage(this)
            spacingBefore = 0
        }
        val footerSize = proportionalImageSize(FOOTER_IMAGE_WIDTH_PX, FOOTER_IMAGE_HEIGHT_PX)
            .scaled(FOOTER_SCALE_PERCENT)
        javaClass.getResourceAsStream("/assets/footer_velasco.png")!!.use { input ->
            footerParagraph.createRun().addPicture(
                input, XWPFDocument.PICTURE_TYPE_PNG, "footer_velasco.png",
                footerSize.widthEmu, footerSize.heightEmu
            )
        }
    }

    private fun centerOnPage(paragraph: XWPFParagraph) {
        paragraph.alignment = ParagraphAlignment.CENTER
        paragraph.indentationLeft = 0
        paragraph.indentationRight = 0
        paragraph.indentationFirstLine = 0
    }

    private fun proportionalImageSize(originalWidthPx: Int, originalHeightPx: Int): ImageSize {
        val heightAtMaximumWidth = IMAGE_MAX_WIDTH_EMU.toLong() * originalHeightPx / originalWidthPx
        return if (heightAtMaximumWidth <= IMAGE_MAX_HEIGHT_EMU) {
            ImageSize(IMAGE_MAX_WIDTH_EMU, heightAtMaximumWidth.toInt())
        } else {
            ImageSize(
                widthEmu = (IMAGE_MAX_HEIGHT_EMU.toLong() * originalWidthPx / originalHeightPx).toInt(),
                heightEmu = IMAGE_MAX_HEIGHT_EMU
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
        fun nomeReclamante() = reclamante?.nome.clean().orEmpty()
        fun nomeReclamada() = reclamada?.nome.clean().orEmpty()

        fun qualificacaoReclamante(comNascimento: Boolean): Qualification {
            val pessoa = reclamante
            val feminino = isFeminino(pessoa)
            val parts = mutableListOf<String>()
            pessoa?.nacionalidade.clean()?.lowercase(PT_BR)?.let(parts::add)
            formatEstadoCivil(pessoa?.estadoCivil, feminino)?.let(parts::add)
            pessoa?.cpf.clean()?.let { parts += "inscrito(a) no CPFMF sob n.º $it" }
            formatRg(pessoa)?.let { parts += "e no RG sob o n.º $it" }
            if (comNascimento) pessoa?.dataNascimento?.let { parts += "nascido(a) em ${formatLongDate(it)}" }
            formatResidence(pessoa?.endereco)?.let(parts::add)
            pessoa?.telefone.clean()?.let { parts += "telefone $it" }
            return Qualification(nomeReclamante(), parts.joinToString(", ").removePrefix("e "))
        }

        fun outorgados(): List<AdvogadoFormatado> = advogados.map(::formatAdvogadoParts)

        fun primeiroAdvogado(): AdvogadoFormatado? = advogados.firstOrNull()?.let(::formatAdvogadoParts)
        fun acaoContraReclamada(): String = nomeReclamada().takeIf { it.isNotBlank() }
            ?.let { "propor Reclamação Trabalhista em face de $it e outros" }
            ?: "propor Reclamação Trabalhista"
    }

    private fun formatAdvogadoParts(usuario: Usuario): AdvogadoFormatado {
        val pessoa = usuario.pessoa
        val generoFeminino = pessoa?.sexo == Sexo.FEMININO || usuario.tratamento == TratamentoAdvogado.DRA
        val profissao = if (generoFeminino) "advogada" else "advogado"
        val inscrito = if (generoFeminino) "inscrita" else "inscrito"
        val details = mutableListOf<String>()
        pessoa?.nacionalidade.clean()?.lowercase(PT_BR)?.let(details::add)
        formatEstadoCivil(pessoa?.estadoCivil, generoFeminino)?.let(details::add)
        details += profissao
        val oab = formatOab(usuario)
        if (oab != null) details += "$inscrito na $oab"
        return AdvogadoFormatado(
            nome = pessoa?.nome.clean()?.uppercase(PT_BR).orEmpty(),
            detalhes = details.joinToString(", ")
        )
    }

    private fun formatRg(pessoa: Pessoa?): String? = pessoa?.rg.clean()?.let { rg ->
        listOfNotNull(rg, pessoa?.orgaoEmissorRg.clean()).joinToString(" ")
    }

    private fun formatResidence(endereco: Endereco?): String? {
        endereco ?: return null
        val location = listOfNotNull(endereco.cidade.clean(), endereco.estado.clean())
            .joinToString(" – ").takeIf { it.isNotBlank() }
        val streetAndNumber = listOfNotNull(endereco.rua.clean(), endereco.numero.clean())
            .joinToString(", ").takeIf { it.isNotBlank() }
        val address = listOfNotNull(
            streetAndNumber?.let { base -> endereco.complemento.clean()?.let { "$base – $it" } ?: base },
            endereco.bairro.clean()?.let { "Bairro $it" },
            formatCep(endereco.cep)?.let { "CEP $it" }
        ).joinToString(", ")
        val complete = listOfNotNull(location, address.takeIf { it.isNotBlank() }).joinToString(", ")
        return complete.takeIf { it.isNotBlank() }?.let { "residente e domiciliado(a) em $it" }
    }

    private fun formatCep(value: String?): String? {
        val digits = value?.filter(Char::isDigit).orEmpty()
        return if (digits.length == 8) "${digits.take(5)}-${digits.drop(5)}" else value.clean()
    }

    private fun formatEstadoCivil(value: EstadoCivil?, feminino: Boolean): String? = when (value) {
        EstadoCivil.SOLTEIRO -> if (feminino) "solteira" else "solteiro"
        EstadoCivil.CASADO -> if (feminino) "casada" else "casado"
        EstadoCivil.DIVORCIADO -> if (feminino) "divorciada" else "divorciado"
        EstadoCivil.VIUVO -> if (feminino) "viúva" else "viúvo"
        EstadoCivil.UNIAO_ESTAVEL -> "união estável"
        EstadoCivil.SEPARADO -> if (feminino) "separada" else "separado"
        null -> null
    }

    private fun isFeminino(pessoa: Pessoa?): Boolean = pessoa?.sexo == Sexo.FEMININO ||
        (pessoa?.sexo == null && pessoa?.nacionalidade.clean()?.lowercase(PT_BR)?.endsWith("a") == true)

    private fun formatOab(usuario: Usuario): String? {
        val uf = usuario.ufOab.clean()
        val numero = usuario.numeroOab.clean()
        return when {
            uf != null && numero != null -> "OAB/$uf nº $numero"
            uf != null -> "OAB/$uf"
            numero != null -> "OAB nº $numero"
            else -> null
        }
    }

    private fun formatLongDate(date: LocalDate): String = date.format(LONG_DATE_FORMATTER)
    private fun String?.clean(): String? = this?.trim()?.takeIf(String::isNotEmpty)

    private data class Qualification(val name: String, val details: String) {
        val text: String = listOf(name, details).filter(String::isNotBlank).joinToString(", ")
    }

    private data class AdvogadoFormatado(val nome: String, val detalhes: String)

    private data class ImageSize(val widthEmu: Int, val heightEmu: Int)

    private fun ImageSize.scaled(percent: Int): ImageSize = ImageSize(
        widthEmu = widthEmu * percent / 100,
        heightEmu = heightEmu * percent / 100
    )

    data class ProcuracaoGerada(val nomeReclamante: String, val bytes: ByteArray)

    private companion object {
        const val FONT = "Georgia"
        const val BODY_FONT_SIZE = 12
        // 1.700 twips = aproximadamente 3 cm, suficiente para o maior rótulo (OUTORGADOS:).
        const val TEXT_COLUMN_POSITION_TWIPS = 1_700
        // As cláusulas começam 0,4 cm para dentro; o texto permanece em uma coluna fixa.
        const val CLAUSE_LEFT_INDENT_TWIPS = 227
        const val CLAUSE_TEXT_COLUMN_POSITION_TWIPS = 927
        // 720 twips = 36 pt, equivalente a aproximadamente três linhas de 12 pt.
        const val DATE_SIGNATURE_GAP_TWIPS = 720
        const val SIGNATURE_SIDE_INDENT_TWIPS = 2_100
        const val CONTRACT_SIGNATURE_TABLE_WIDTH_TWIPS = 7_600
        const val CONTRACT_SIGNATURE_CELL_WIDTH_TWIPS = 3_500
        const val CONTRACT_SIGNATURE_GAP_WIDTH_TWIPS = 600
        const val WITNESSES_SPACE_BEFORE_TWIPS = 240
        // Amplia a reserva entre o corpo do documento e a imagem do rodapé.
        const val BODY_BOTTOM_MARGIN_TWIPS = 3_200
        // Folga suficiente para manter data + 36 pt + assinatura juntos no fim da primeira página.
        const val PROCURACAO_SIGNATURE_SPACE_BEFORE_TWIPS = 1_600
        const val CONTRATO_SIGNATURE_SPACE_BEFORE_TWIPS = 1_200
        // Usa a mesma reserva segura para manter a declaração e sua assinatura em uma página.
        const val DECLARACAO_SIGNATURE_SPACE_BEFORE_TWIPS = 1_600
        const val CIDADE_ESCRITORIO = "Cascavel"
        const val IMAGE_MAX_WIDTH_EMU = 7_543_800
        const val IMAGE_MAX_HEIGHT_EMU = 1_044_713
        const val FOOTER_IMAGE_WIDTH_PX = 670
        const val FOOTER_IMAGE_HEIGHT_PX = 202
        const val FOOTER_LINE_WIDTH_PX = 794
        const val FOOTER_LINE_HEIGHT_PX = 21
        const val FOOTER_SCALE_PERCENT = 75
        val PT_BR: Locale = Locale("pt", "BR")
        val LONG_DATE_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy", Locale("pt", "BR"))
    }
}
