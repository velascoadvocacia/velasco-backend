package com.example.lawyer.service

import com.example.lawyer.domain.enums.EstadoCivil
import com.example.lawyer.domain.model.Endereco
import com.example.lawyer.domain.model.Pessoa
import com.example.lawyer.domain.model.Processo
import com.example.lawyer.dto.request.FichaEntrevistaExportRequest
import jakarta.enterprise.context.ApplicationScoped
import org.apache.poi.util.Units
import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.TableRowAlign
import org.apache.poi.xwpf.usermodel.UnderlinePatterns
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFTable
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STLineSpacingRule
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STPageOrientation
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.math.BigInteger
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@ApplicationScoped
class FichaEntrevistaExportService(
    private val processoService: ProcessoService,
    private val pessoaService: PessoaService,
    private val docxHeaderService: DocxHeaderService
) {
    fun generate(request: FichaEntrevistaExportRequest): FichaEntrevistaGerada {
        val processo = request.processoId?.let(processoService::findEntity)
        val reclamante = resolvePessoas(request.reclamantesIds, processo?.reclamantes?.toList()).firstOrNull()
        val reclamada = resolvePessoas(request.reclamadasIds, processo?.reclamadas?.toList()).firstOrNull()
        val contrato = mergedVariables(processo, request.dadosVariaveis, CONTRATO_ASPECTOS_GERAIS)
        val periodoSemRegistro = mergedVariables(processo, request.dadosVariaveis, PERIODO_SEM_REGISTRO_CTPS)
        val dados = DadosFicha(processo, reclamante, reclamada, contrato, periodoSemRegistro, LocalDate.now())

        val bytes = XWPFDocument().use { document ->
            configurePage(document)
            addHeaderAndFooter(document)
            createFicha(document, dados)
            ByteArrayOutputStream().use { output ->
                document.write(output)
                output.toByteArray()
            }
        }
        return FichaEntrevistaGerada(dados.nomeReclamante.ifBlank { "documento" }, bytes)
    }

    private fun createFicha(document: XWPFDocument, dados: DadosFicha) {
        title(document, "FICHA DE ENTREVISTA")
        spacer(document, 80)
        twoColumnLine(
            document,
            left = listOf(Segment("DADOS DO(A) AUTOR(A):", bold = true)),
            right = listOf(Segment("Data da Prescrição: ____/____/______"))
        )
        twoColumnLine(
            document,
            left = listOf(Segment("Nome: ", bold = true), Segment(dados.nomeReclamante.orLine(NAME_LINE))),
            right = listOf(Segment("Telefone: ", bold = true), Segment(dados.telefoneReclamante.orLine(PHONE_LINE)))
        )
        secondManualContactLine(document)
        labeledLine(document, "Endereço:", dados.enderecoReclamante.orLine(LONG_LINE))
        threeColumnLine(
            document,
            listOf(Segment("RG: ", bold = true), Segment(dados.rg.orLine(SHORT_LINE))),
            listOf(Segment("CPF: ", bold = true), Segment(dados.cpf.orLine(SHORT_LINE))),
            listOf(Segment("PIS: ", bold = true), Segment(dados.pis.orLine(SHORT_LINE)))
        )
        body(document, "CTPS: _________________    SÉRIE: _____________-_____   Data de Nascimento: ${dados.dataNascimento.orLine(DATE_LINE)}")
        twoColumnLine(
            document,
            left = listOf(Segment("Estado Civil: ", bold = true), Segment(dados.estadoCivil.orLine(MEDIUM_LINE))),
            right = listOf(Segment("Nome da Mãe: ", bold = true), Segment(dados.nomeMae.orLine("________________________")))
        )

        section(document, "DADOS DA RECLAMADA:")
        labeledLine(document, "Razão Social:", dados.nomeReclamada.orLine(LONG_LINE))
        labeledLine(document, "Endereço:", dados.enderecoReclamada.orLine(LONG_LINE))
        labeledLine(document, "CNPJ:", dados.cnpj.orLine(MEDIUM_LINE))

        section(document, "DADOS DO EMPREGO:")
        employmentDetailsTable(document, dados)
        body(
            document,
            "Horário da Jornada: _____:_____  às  _____:_____  (_____:_____)  _____:_____  às  _____:_____",
            keepLines = true
        )

        body(
            document,
            "Histórico: (HE – salário por fora – diferença salarial – equiparação – troca de roupa – menor - Adicionais – férias – 13º salário – FGTS – seguro desemprego – aviso prévio – multa 477 - Saldo de salário – salário atrasado – observações)",
            boldPrefix = "Histórico:",
            fontSize = SMALL_TEXT_FONT_SIZE
        )
        repeat(HISTORY_LINES) { manualLine(document) }

        body(
            document,
            "Declaro que forneci as informações acima e que estou ciente de que poderei ser condenado(a) às custas processuais no caso de indeferimento ou improcedência da ação.",
            fontSize = SMALL_TEXT_FONT_SIZE
        )
        centered(document, "$CIDADE_ESCRITORIO, ${formatLongDate(dados.dataExportacao)}.", keepNext = true, before = 320)
        signature(document, dados.nomeReclamante.orLine(NAME_LINE))
        centered(document, "Assinatura do Autor(a)")
    }

    private fun mergedVariables(
        processo: Processo?,
        requestVariables: Map<String, Map<String, String?>>,
        blockId: String
    ): Map<String, String?> {
        val persisted = if (processo != null && blockId in processo.blocosSelecionados) {
            processo.dadosVariaveis.filter { it.blocoId == blockId }.associate { it.campo to it.valor }
        } else emptyMap()
        return persisted + requestVariables[blockId].orEmpty()
    }

    private fun resolvePessoas(ids: List<Long>, fallback: List<Pessoa>?): List<Pessoa> =
        if (ids.isNotEmpty()) ids.distinct().map(pessoaService::findEntity) else fallback.orEmpty()

    private inner class DadosFicha(
        processo: Processo?,
        reclamante: Pessoa?,
        reclamada: Pessoa?,
        contrato: Map<String, String?>,
        periodoSemRegistro: Map<String, String?>,
        val dataExportacao: LocalDate
    ) {
        val nomeReclamante = reclamante?.nome.clean().orEmpty()
        val telefoneReclamante = reclamante?.telefone.clean().orEmpty()
        val enderecoReclamante = formatAddress(reclamante?.endereco).orEmpty()
        val rg = reclamante?.rg.clean().orEmpty()
        val cpf = formatCpf(reclamante?.cpf).orEmpty()
        val pis = reclamante?.pis.clean().orEmpty()
        val dataNascimento = formatDate(reclamante?.dataNascimento)
        val estadoCivil = formatEstadoCivil(reclamante?.estadoCivil).orEmpty()
        val nomeMae = reclamante?.nomeMae.clean().orEmpty()
        val nomeReclamada = listOf(reclamada?.razaoSocial, reclamada?.nomeFantasia, reclamada?.nome)
            .firstNotNullOfOrNull { it.clean() }.orEmpty()
        val enderecoReclamada = formatAddress(reclamada?.endereco).orEmpty()
        val cnpj = formatCnpj(reclamada?.cnpj).orEmpty()
        val dataContratacao = formatVariableDate(contrato["dataContratacao"])
        val dataExtincao = formatVariableDate(contrato["dataExtincao"])
        val motivoExtincao = formatMotivo(contrato["motivoExtincao"])
        val dataInicioPrestacao = formatVariableDate(periodoSemRegistro["dataInicioPrestacaoServicos"])
        val dataAnotacaoCtps = formatVariableDate(periodoSemRegistro["dataAnotacaoCtps"])
        val funcao = reclamante?.profissao.clean()
            ?: contrato["funcaoContrato"].clean()
            ?: processo?.contratoTrabalho?.funcaoExercida.clean()
            ?: ""
        val remuneracao = formatCurrency(contrato["remuneracao"])
    }

    private fun title(document: XWPFDocument, text: String) {
        val paragraph = document.createParagraph().apply { alignment = ParagraphAlignment.CENTER }
        run(paragraph, text, bold = true, underline = true)
    }

    private fun section(document: XWPFDocument, text: String) {
        spacer(document, 100)
        val paragraph = baseParagraph(document)
        run(paragraph, text, bold = true)
    }

    private fun labeledLine(document: XWPFDocument, label: String, value: String) {
        val paragraph = baseParagraph(document)
        run(paragraph, "$label ", bold = true)
        run(paragraph, value)
    }

    private fun body(
        document: XWPFDocument,
        text: String,
        boldPrefix: String? = null,
        fontSize: Int = FONT_SIZE,
        keepLines: Boolean = false
    ) {
        val paragraph = baseParagraph(document)
        if (keepLines) (paragraph.ctp.pPr ?: paragraph.ctp.addNewPPr()).addNewKeepLines().`val` = true
        if (boldPrefix != null && text.startsWith(boldPrefix)) {
            run(paragraph, boldPrefix, bold = true, fontSize = fontSize)
            run(paragraph, text.removePrefix(boldPrefix), fontSize = fontSize)
        } else run(paragraph, text, fontSize = fontSize)
    }

    private fun twoColumnLine(document: XWPFDocument, left: List<Segment>, right: List<Segment>) =
        formTable(document, listOf(5_200, 3_500), listOf(left, right))

    private fun threeColumnLine(
        document: XWPFDocument,
        first: List<Segment>,
        second: List<Segment>,
        third: List<Segment>
    ) = formTable(document, listOf(2_900, 2_900, 2_900), listOf(first, second, third))

    private fun secondManualContactLine(document: XWPFDocument) {
        val table = twoColumnLine(
            document,
            left = listOf(Segment("")),
            right = listOf(Segment("Telefone: ___________________"))
        )
        val paragraph = table.getRow(0).getCell(0).paragraphs.first()
        val borders = (paragraph.ctp.pPr ?: paragraph.ctp.addNewPPr()).addNewPBdr()
        borders.addNewBottom().apply {
            `val` = STBorder.SINGLE
            sz = BigInteger.valueOf(8)
            space = BigInteger.valueOf(2)
        }
    }

    private fun employmentDetailsTable(document: XWPFDocument, dados: DadosFicha) {
        val table = document.createTable(1, 2).apply {
            setWidth("8700")
            setTableAlignment(TableRowAlign.LEFT)
            removeBorders(this)
            setCellMargins(0, 0, 0, 0)
        }
        val row = table.getRow(0)
        row.ctRow.addNewTrPr().addNewCantSplit().`val` = true
        val left = row.getCell(0).apply { setWidth("5200") }
        val right = row.getCell(1).apply { setWidth("3500") }

        employmentCellLine(
            left.paragraphs.first(),
            listOf(
                Segment("Admissão: ", bold = true), Segment(dados.dataContratacao),
                Segment("    Demissão: ", bold = true), Segment(dados.dataExtincao)
            )
        )
        employmentCellLine(
            left.addParagraph(),
            listOf(
                Segment("Período sem Registro: ", bold = true),
                Segment("${dados.dataInicioPrestacao}  a  ${dados.dataAnotacaoCtps}")
            )
        )
        employmentCellLine(
            right.paragraphs.first(),
            listOf(Segment("Motivo: ", bold = true), Segment(dados.motivoExtincao))
        )
        employmentCellLine(
            right.addParagraph(),
            listOf(Segment("Função: ", bold = true), Segment(dados.funcao.orLine(MEDIUM_LINE)))
        )
        employmentCellLine(
            right.addParagraph(),
            listOf(Segment("Valor: ", bold = true), Segment("R$ ${dados.remuneracao}"))
        )
    }

    private fun employmentCellLine(paragraph: XWPFParagraph, segments: List<Segment>) {
        paragraph.apply {
            alignment = ParagraphAlignment.LEFT
            spacingBefore = 0
            spacingAfter = 40
        }
        segments.forEach { run(paragraph, it.text, it.bold) }
    }

    private fun formTable(document: XWPFDocument, widths: List<Int>, cells: List<List<Segment>>): XWPFTable {
        val table = document.createTable(1, cells.size).apply {
            setWidth(widths.sum().toString())
            setTableAlignment(TableRowAlign.LEFT)
            removeBorders(this)
            setCellMargins(0, 0, 0, 0)
        }
        val row = table.getRow(0)
        row.ctRow.addNewTrPr().addNewCantSplit().`val` = true
        cells.forEachIndexed { index, segments ->
            val cell = row.getCell(index)
            cell.setWidth(widths[index].toString())
            val paragraph = cell.paragraphs.first().apply {
                alignment = ParagraphAlignment.LEFT
                spacingBefore = 0
                spacingAfter = 40
            }
            segments.forEach { run(paragraph, it.text, it.bold) }
        }
        return table
    }

    private fun removeBorders(table: XWPFTable) {
        val borders = table.ctTbl.tblPr.tblBorders ?: table.ctTbl.tblPr.addNewTblBorders()
        listOf(
            borders.addNewTop(), borders.addNewLeft(), borders.addNewBottom(),
            borders.addNewRight(), borders.addNewInsideH(), borders.addNewInsideV()
        ).forEach { it.`val` = STBorder.NONE }
    }

    private fun manualLine(document: XWPFDocument) {
        val paragraph = baseParagraph(document).apply { spacingAfter = 100 }
        val borders = (paragraph.ctp.pPr ?: paragraph.ctp.addNewPPr()).addNewPBdr()
        borders.addNewBottom().apply {
            `val` = STBorder.SINGLE
            sz = BigInteger.valueOf(6)
            space = BigInteger.valueOf(2)
        }
    }

    private fun centered(
        document: XWPFDocument,
        text: String,
        keepNext: Boolean = false,
        before: Int = 0
    ) {
        val paragraph = baseParagraph(document).apply {
            alignment = ParagraphAlignment.CENTER
            spacingBefore = before
        }
        if (keepNext) (paragraph.ctp.pPr ?: paragraph.ctp.addNewPPr()).addNewKeepNext().`val` = true
        run(paragraph, text)
    }

    private fun signature(document: XWPFDocument, name: String) {
        val paragraph = baseParagraph(document).apply {
            alignment = ParagraphAlignment.CENTER
            indentationLeft = 2_100
            indentationRight = 2_100
            spacingBefore = 720
            spacingAfter = 0
        }
        val borders = (paragraph.ctp.pPr ?: paragraph.ctp.addNewPPr()).addNewPBdr()
        borders.addNewTop().apply {
            `val` = STBorder.SINGLE
            sz = BigInteger.valueOf(8)
            space = BigInteger.valueOf(4)
        }
        run(paragraph, name, bold = true)
    }

    private fun baseParagraph(document: XWPFDocument): XWPFParagraph = document.createParagraph().apply {
        alignment = ParagraphAlignment.LEFT
        spacingAfter = 60
        val spacing = ctp.pPr?.spacing ?: ctp.addNewPPr().addNewSpacing()
        spacing.line = BigInteger.valueOf(240)
        spacing.lineRule = STLineSpacingRule.AUTO
    }

    private fun run(
        paragraph: XWPFParagraph,
        text: String,
        bold: Boolean = false,
        underline: Boolean = false,
        fontSize: Int = FONT_SIZE
    ) {
        paragraph.createRun().apply {
            setText(text)
            isBold = bold
            if (underline) this.underline = UnderlinePatterns.SINGLE
            fontFamily = FONT
            this.fontSize = fontSize
        }
    }

    private fun spacer(document: XWPFDocument, after: Int) {
        document.createParagraph().spacingAfter = after
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
            bottom = BigInteger.valueOf(3_200)
            left = BigInteger.valueOf(1_160)
            header = BigInteger.valueOf(159)
            footer = BigInteger.valueOf(1_846)
            gutter = BigInteger.ZERO
        }
    }

    private fun addHeaderAndFooter(document: XWPFDocument) {
        docxHeaderService.addHeader(document)
        val footer = document.createHeaderFooterPolicy().createFooter(XWPFHeaderFooterPolicy.DEFAULT)
        val lineParagraph = (footer.paragraphs.firstOrNull() ?: footer.createParagraph()).apply {
            alignment = ParagraphAlignment.CENTER
            spacingAfter = 0
        }
        javaClass.getResourceAsStream("/assets/footer_line.png")!!.use {
            lineParagraph.createRun().addPicture(it, XWPFDocument.PICTURE_TYPE_PNG, "footer_line.png", Units.toEMU(594.0), Units.toEMU(15.7))
        }
        val footerParagraph = footer.createParagraph().apply {
            alignment = ParagraphAlignment.CENTER
            spacingBefore = 0
        }
        javaClass.getResourceAsStream("/assets/footer_velasco.png")!!.use {
            footerParagraph.createRun().addPicture(it, XWPFDocument.PICTURE_TYPE_PNG, "footer_velasco.png", Units.toEMU(188.0), Units.toEMU(56.7))
        }
    }

    private fun formatAddress(address: Endereco?): String? {
        address ?: return null
        val street = listOfNotNull(address.rua.clean(), address.numero.clean()).joinToString(", ")
        return listOfNotNull(
            street.takeIf { it.isNotBlank() },
            address.complemento.clean(),
            address.bairro.clean()?.let { "Bairro $it" },
            address.cidade.clean(),
            address.estado.clean(),
            formatCep(address.cep)?.let { "CEP $it" }
        ).joinToString(", ").takeIf { it.isNotBlank() }
    }

    private fun formatDate(value: LocalDate?): String = value?.format(DATE_FORMATTER) ?: ""
    private fun formatVariableDate(value: String?): String = value.clean()
        ?.let { runCatching { LocalDate.parse(it).format(DATE_FORMATTER) }.getOrNull() }
        ?: DATE_LINE

    private fun formatCurrency(value: String?): String = value.clean()?.let {
        runCatching {
            val decimal = if (it.contains(',')) {
                BigDecimal(it.replace(".", "").replace(",", "."))
            } else {
                BigDecimal(it)
            }
            NumberFormat.getNumberInstance(PT_BR).apply {
                minimumFractionDigits = 2
                maximumFractionDigits = 2
            }.format(decimal)
        }.getOrNull()
    } ?: MONEY_LINE

    private fun formatMotivo(value: String?): String = when (value.clean()) {
        "1" -> "Dispensa sem justa causa"
        "2" -> "Dispensa com justa causa"
        "3" -> "Pedido de demissão"
        "4" -> "Rescisão indireta"
        "5" -> "Reversão do pedido de demissão"
        else -> MEDIUM_LINE
    }

    private fun formatEstadoCivil(value: EstadoCivil?): String? = when (value) {
        EstadoCivil.SOLTEIRO -> "Solteiro(a)"
        EstadoCivil.CASADO -> "Casado(a)"
        EstadoCivil.DIVORCIADO -> "Divorciado(a)"
        EstadoCivil.VIUVO -> "Viúvo(a)"
        EstadoCivil.UNIAO_ESTAVEL -> "União estável"
        EstadoCivil.SEPARADO -> "Separado(a)"
        null -> null
    }

    private fun formatCpf(value: String?): String? = value.clean()?.let {
        val digits = it.filter(Char::isDigit)
        if (digits.length == 11) "${digits.take(3)}.${digits.substring(3, 6)}.${digits.substring(6, 9)}-${digits.takeLast(2)}" else it
    }

    private fun formatCnpj(value: String?): String? = value.clean()?.let {
        val digits = it.filter(Char::isDigit)
        if (digits.length == 14) "${digits.take(2)}.${digits.substring(2, 5)}.${digits.substring(5, 8)}/${digits.substring(8, 12)}-${digits.takeLast(2)}" else it
    }

    private fun formatCep(value: String?): String? = value.clean()?.let {
        val digits = it.filter(Char::isDigit)
        if (digits.length == 8) "${digits.take(5)}-${digits.takeLast(3)}" else it
    }

    private fun formatLongDate(value: LocalDate): String = value.format(LONG_DATE_FORMATTER)
    private fun String?.clean(): String? = this?.trim()?.takeIf(String::isNotEmpty)
    private fun String.orLine(line: String): String = ifBlank { line }

    data class FichaEntrevistaGerada(val nomeReclamante: String, val bytes: ByteArray)
    private data class Segment(val text: String, val bold: Boolean = false)

    private companion object {
        const val CONTRATO_ASPECTOS_GERAIS = "contrato_aspectos_gerais"
        const val PERIODO_SEM_REGISTRO_CTPS = "periodo_sem_registro_ctps"
        const val FONT = "Georgia"
        const val FONT_SIZE = 12
        const val SMALL_TEXT_FONT_SIZE = 8
        const val CIDADE_ESCRITORIO = "Cascavel"
        const val HISTORY_LINES = 6
        const val DATE_LINE = "____/____/______"
        const val NAME_LINE = "____________________________"
        const val PHONE_LINE = "___________________"
        const val SHORT_LINE = "_______________"
        const val MEDIUM_LINE = "________________________"
        const val LONG_LINE = "____________________________________________"
        const val MONEY_LINE = "____________"
        val PT_BR: Locale = Locale("pt", "BR")
        val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        val LONG_DATE_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy", PT_BR)
    }
}
