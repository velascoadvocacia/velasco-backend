package com.example.lawyer.service

import com.example.lawyer.dto.request.RtExportRequest
import com.example.lawyer.dto.request.RtExportBlockRequest
import com.example.lawyer.dto.request.RtExportImageRequest
import com.example.lawyer.dto.request.RtExportInlineImageRequest
import com.example.lawyer.dto.request.RtExportTableCellRequest
import jakarta.enterprise.context.ApplicationScoped
import org.apache.poi.util.Units
import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFRun
import org.apache.poi.xwpf.usermodel.XWPFTable
import org.apache.poi.xwpf.usermodel.XWPFTableCell
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STJc
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STLineSpacingRule
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STPageOrientation
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URL
import java.math.BigInteger
import javax.imageio.ImageIO
import org.jboss.logging.Logger

@ApplicationScoped
class RtExportService(private val docxHeaderService: DocxHeaderService) {
    private val logger = Logger.getLogger(RtExportService::class.java)

    fun generate(request: RtExportRequest): ByteArray {
        XWPFDocument().use { document ->
            configureA4Page(document)
            docxHeaderService.addHeader(document)
            addFooter(document)

            createEmptyParagraph(document)
            createTitle(document, "Reclamatória Trabalhista")
            createEmptyParagraph(document)

            request.blocks.forEach { block ->
                createSectionHeader(document, block.title)
                createBodyContent(document, block)
                createEmptyParagraph(document)
            }

            return ByteArrayOutputStream().use { output ->
                document.write(output)
                output.toByteArray()
            }
        }
    }

    private fun addFooter(document: XWPFDocument) {
        try {
            val policy = document.createHeaderFooterPolicy()
            val footer = policy.createFooter(XWPFHeaderFooterPolicy.DEFAULT)
            val p = footer.createParagraph()
            p.alignment = ParagraphAlignment.CENTER

            val inputStream: InputStream? = javaClass.getResourceAsStream("/assets/footer_velasco.png")
            if (inputStream != null) {
                val run = p.createRun()
                run.addPicture(
                    inputStream,
                    XWPFDocument.PICTURE_TYPE_PNG,
                    "footer_velasco.png",
                    Units.toEMU(240.0),
                    Units.toEMU(60.0)
                )
                inputStream.close()
            }
        } catch (e: Exception) {
            // Footer não criado se imagem não encontrada
        }
    }

    private fun configureA4Page(document: XWPFDocument) {
        val section = document.document.body.addNewSectPr()

        section.pgSz = CTPageSz.Factory.newInstance().apply {
            w = BigInteger.valueOf(11899)
            h = BigInteger.valueOf(16838)
            orient = STPageOrientation.PORTRAIT
        }

        section.pgMar = CTPageMar.Factory.newInstance().apply {
            top = BigInteger.valueOf(1560)
            bottom = BigInteger.valueOf(1702)
            left = BigInteger.valueOf(1560)
            right = BigInteger.valueOf(1126)
            header = BigInteger.valueOf(720)
            footer = BigInteger.ZERO
            gutter = BigInteger.ZERO
        }
    }

    // Título principal — Arial 18pt, negrito, centralizado, bordas superior e inferior
    private fun createTitle(document: XWPFDocument, text: String) {
        val p = document.createParagraph()
        p.alignment = ParagraphAlignment.CENTER

        val ppr = p.ctp.pPr ?: p.ctp.addNewPPr()

        // Bordas
        applyBorders(ppr)

        // Espaçamento entre linhas
        val spacing = ppr.addNewSpacing()
        spacing.line = BigInteger.valueOf(276)
        spacing.lineRule = STLineSpacingRule.AUTO

        val run = p.createRun()
        run.fontFamily = "Arial"
        run.fontSize = 18
        run.isBold = true
        run.setText(text)
    }

    // Cabeçalho de seção — Garamond 14pt, negrito, bordas superior e inferior
    private fun createSectionHeader(document: XWPFDocument, text: String) {
        val p = document.createParagraph()
        p.alignment = ParagraphAlignment.CENTER

        val ppr = p.ctp.pPr ?: p.ctp.addNewPPr()

        // Bordas
        applyBorders(ppr)

        // Espaçamento
        val spacing = ppr.addNewSpacing()
        spacing.line = BigInteger.valueOf(276)
        spacing.lineRule = STLineSpacingRule.AUTO
        spacing.before = BigInteger.valueOf(240)

        val run = p.createRun()
        run.fontFamily = "Garamond"
        run.fontSize = 14
        run.isBold = true
        run.setText(text)
    }

    // Corpo de texto — Garamond 12pt, justificado
    private fun createBodyContent(document: XWPFDocument, block: RtExportBlockRequest) {
        if (block.id == RtTemplateService.JORNADA_TRABALHO_INCONSTITUCIONALIDADE_INTERVALO_INTRAJORNADA) {
            createIntrajornadaConstitutionalityContent(document, block)
            return
        }
        if (block.id == RtTemplateService.MULTA_CONVENCIONAL) {
            createConventionalFineContent(document, block)
            return
        }
        logger.infof("DOCX bloco '%s': anexos.size=%d; iniciando criação de parágrafos", block.title, block.anexos.size)
        val paragraphTexts = block.content.split("\n\n")
        paragraphTexts.forEachIndexed { index, paragraphText ->
            val paragraphNumber = index + 1
            val alignment = if (paragraphNumber in block.paragrafosAlinhadosDireita) {
                ParagraphAlignment.RIGHT
            } else {
                ParagraphAlignment.BOTH
            }
            createBodyParagraph(
                document,
                paragraphText,
                alignment,
                paragraphNumber in block.paragrafosRecuados
            )
            if (block.id == RtTemplateService.JORNADA_TRABALHO_INTERVALO_INTRAJORNADA) {
                configureIntrajornadaParagraphPagination(
                    document.paragraphs.last(),
                    paragraphNumber,
                    paragraphTexts
                )
            } else if (block.id == RtTemplateService.JORNADA_TRABALHO_INCONSTITUCIONALIDADE_TEMPO_ESPERA) {
                configureWaitingTimeParagraphPagination(document.paragraphs.last(), paragraphNumber)
            } else if (block.id == RtTemplateService.JORNADA_TRABALHO_DANO_MORAL_JORNADA_EXTENUANTE) {
                configureExtenuatingJourneyParagraphPagination(document.paragraphs.last(), paragraphNumber)
            } else if (block.id == RtTemplateService.JORNADA_TRABALHO_INCONSTITUCIONALIDADE_JORNADA_HABITUAL_12H) {
                configureHabitualTwelveHourJourneyPagination(document.paragraphs.last(), paragraphNumber)
            }
            logger.infof("DOCX bloco '%s': parágrafo %d criado; anexos.size=%d", block.title, index + 1, block.anexos.size)
            block.anexos
                .filter { it.afterParagraph == index + 1 }
                .forEach { image ->
                    addBodyImage(
                        document,
                        image,
                        preserveAspectRatio = block.id == RtTemplateService.AUSENCIA_DEPOSITOS_FGTS
                    )
                }
            block.imagensFixas
                .filter { it.afterParagraph == index + 1 }
                .forEach { image ->
                    addInlineImage(
                        document,
                        image,
                        spaced = block.id == RtTemplateService.JORNADA_TRABALHO_DANO_MORAL_JORNADA_EXTENUANTE ||
                            block.id == RtTemplateService.JORNADA_TRABALHO_INCONSTITUCIONALIDADE_JORNADA_HABITUAL_12H
                    )
                    image.caption?.let { createImageCaption(document, it) }
                }
        }
    }

    private fun createConventionalFineContent(document: XWPFDocument, block: RtExportBlockRequest) {
        val paragraphs = block.content.split("\n\n")
        require(paragraphs.size == 3) { "Conteúdo inválido para o bloco de multa convencional" }
        createBodyParagraph(document, paragraphs[0])
        document.paragraphs.last().ctp.pPr.spacing.after = BigInteger.valueOf(120)
        createConventionalFineTable(document, block)
        createBodyParagraph(document, paragraphs[1])
        document.paragraphs.last().ctp.pPr.spacing.before = BigInteger.valueOf(160)
        createBodyParagraph(document, paragraphs[2])
    }

    private fun createConventionalFineTable(document: XWPFDocument, block: RtExportBlockRequest) {
        val model = requireNotNull(block.tabela) { "Tabela ausente para o bloco de multa convencional" }
        require(model.cabecalhos.size == 3) { "Cabeçalho inválido para a tabela de multa convencional" }
        val table = document.createTable(model.linhas.size + 1, 3)
        table.setWidth(USEFUL_PAGE_WIDTH_TWIPS.toString())
        configureBlackBorders(table)
        val widths = listOf(2303, 1566, 5343)
        table.rows.forEachIndexed { rowIndex, row ->
            val rowProperties = if (row.ctRow.isSetTrPr) row.ctRow.trPr else row.ctRow.addNewTrPr()
            rowProperties.addNewCantSplit().`val` = true
            if (rowIndex == 0) rowProperties.addNewTblHeader().`val` = true
            row.tableCells.forEachIndexed { columnIndex, cell ->
                cell.setWidth(widths[columnIndex].toString())
                configureConventionalFineCellMargins(cell)
            }
        }
        model.cabecalhos.forEachIndexed { index, text ->
            configureConventionalFineCell(
                table.getRow(0).getCell(index),
                RtExportTableCellRequest(text),
                header = true,
                fill = "B4C6E7"
            )
        }
        model.linhas.forEachIndexed { rowIndex, cells ->
            require(cells.size == 3) { "Linha inválida para a tabela de multa convencional" }
            val fill = if (rowIndex % 2 == 0) "E7E6E6" else "E9EFF7"
            cells.forEachIndexed { columnIndex, cell ->
                configureConventionalFineCell(table.getRow(rowIndex + 1).getCell(columnIndex), cell, false, fill)
            }
        }
    }

    private fun configureConventionalFineCell(
        cell: XWPFTableCell,
        value: RtExportTableCellRequest,
        header: Boolean,
        fill: String
    ) {
        cell.verticalAlignment = if (header) XWPFTableCell.XWPFVertAlign.CENTER else XWPFTableCell.XWPFVertAlign.TOP
        val properties = cell.ctTc.tcPr ?: cell.ctTc.addNewTcPr()
        val shading = if (properties.isSetShd) properties.shd else properties.addNewShd()
        shading.fill = fill
        val paragraph = cell.paragraphs.single()
        paragraph.alignment = if (header) ParagraphAlignment.CENTER else ParagraphAlignment.LEFT
        val paragraphProperties = paragraph.ctp.pPr ?: paragraph.ctp.addNewPPr()
        val spacing = if (paragraphProperties.isSetSpacing) paragraphProperties.spacing else paragraphProperties.addNewSpacing()
        spacing.before = BigInteger.ZERO
        spacing.after = BigInteger.ZERO
        addMultilineRun(paragraph, value.texto, bold = header, italic = value.italico)
    }

    private fun configureConventionalFineCellMargins(cell: XWPFTableCell) {
        val properties = cell.ctTc.tcPr ?: cell.ctTc.addNewTcPr()
        val margins = if (properties.isSetTcMar) properties.tcMar else properties.addNewTcMar()
        listOf(margins.addNewTop(), margins.addNewLeft(), margins.addNewBottom(), margins.addNewRight()).forEach {
            it.w = BigInteger.valueOf(90)
            it.type = STTblWidth.DXA
        }
    }

    private fun addMultilineRun(
        paragraph: XWPFParagraph,
        text: String,
        bold: Boolean = false,
        italic: Boolean = false
    ) {
        val run = paragraph.createRun().apply {
            fontFamily = "Garamond"
            fontSize = 12
            isBold = bold
            isItalic = italic
        }
        text.split("\n").forEachIndexed { index, line ->
            if (index > 0) run.addBreak()
            run.setText(line)
        }
    }

    private fun configureIntrajornadaParagraphPagination(
        paragraph: XWPFParagraph,
        paragraphNumber: Int,
        paragraphTexts: List<String>
    ) {
        val properties = paragraph.ctp.pPr ?: paragraph.ctp.addNewPPr()
        val followedByGrifo = paragraphTexts.getOrNull(paragraphNumber) == "(grifo nosso)"
        if (paragraphNumber in INTRAJORNADA_KEEP_NEXT_PARAGRAPHS || followedByGrifo) {
            properties.addNewKeepNext().`val` = true
        }
        if (paragraph.text == "(grifo nosso)") {
            properties.addNewKeepLines().`val` = true
        }
    }

    private fun configureWaitingTimeParagraphPagination(paragraph: XWPFParagraph, paragraphNumber: Int) {
        val properties = paragraph.ctp.pPr ?: paragraph.ctp.addNewPPr()
        if (paragraphNumber == 1 || paragraphNumber == 2) {
            properties.addNewKeepNext().`val` = true
        }
        if (paragraphNumber == 3) {
            properties.addNewKeepLines().`val` = true
        }
    }

    private fun configureExtenuatingJourneyParagraphPagination(
        paragraph: XWPFParagraph,
        paragraphNumber: Int
    ) {
        if (paragraphNumber == 18 || paragraphNumber == 21) {
            val properties = paragraph.ctp.pPr ?: paragraph.ctp.addNewPPr()
            properties.addNewKeepNext().`val` = true
        }
    }

    private fun configureHabitualTwelveHourJourneyPagination(
        paragraph: XWPFParagraph,
        paragraphNumber: Int
    ) {
        if (paragraphNumber == 3) {
            val properties = paragraph.ctp.pPr ?: paragraph.ctp.addNewPPr()
            properties.addNewKeepNext().`val` = true
        }
    }

    private fun createIntrajornadaConstitutionalityContent(
        document: XWPFDocument,
        block: RtExportBlockRequest
    ) {
        val paragraphs = block.content.split("\n\n")
        require(paragraphs.size == 5) { "Conteúdo inválido para o bloco de inconstitucionalidade intrajornada" }
        createBodyParagraph(document, paragraphs[0])
        document.paragraphs.last().ctp.pPr.spacing.after = BigInteger.valueOf(TABLE_SPACING_BEFORE_TWIPS)
        createIntrajornadaComparisonTable(document, paragraphs[1], paragraphs[2])
        createBodyParagraph(document, paragraphs[3])
        document.paragraphs.last().ctp.pPr.spacing.before = BigInteger.valueOf(TABLE_SPACING_AFTER_TWIPS)
        createBodyParagraph(document, paragraphs[4])
    }

    private fun createIntrajornadaComparisonTable(document: XWPFDocument, original: String, altered: String) {
        val table = document.createTable(2, 2)
        table.setWidth(USEFUL_PAGE_WIDTH_TWIPS.toString())
        configureBlackBorders(table)
        table.rows.forEach { row ->
            val rowProperties = if (row.ctRow.isSetTrPr) row.ctRow.trPr else row.ctRow.addNewTrPr()
            rowProperties.addNewCantSplit().`val` = true
        }
        listOf("Redação original", "Redação alterada").forEachIndexed { index, header ->
            configureComparisonCell(table.getRow(0).getCell(index), header, header = true)
        }
        configureComparisonCell(table.getRow(1).getCell(0), original, header = false)
        configureComparisonCell(table.getRow(1).getCell(1), altered, header = false)
    }

    private fun configureComparisonCell(cell: XWPFTableCell, text: String, header: Boolean) {
        cell.setWidth(TABLE_COLUMN_WIDTH_TWIPS.toString())
        cell.verticalAlignment = XWPFTableCell.XWPFVertAlign.TOP
        configureCellMargins(cell)
        val paragraph = cell.paragraphs.single()
        paragraph.alignment = if (header) ParagraphAlignment.CENTER else ParagraphAlignment.BOTH
        val paragraphProperties = paragraph.ctp.pPr ?: paragraph.ctp.addNewPPr()
        val spacing = if (paragraphProperties.isSetSpacing) paragraphProperties.spacing else paragraphProperties.addNewSpacing()
        spacing.before = BigInteger.ZERO
        spacing.after = BigInteger.ZERO
        if (header) paragraphProperties.addNewKeepNext().`val` = true
        if (header) {
            addFormattedRun(paragraph, text, bold = true)
        } else {
            addFormattedText(paragraph, text)
        }
    }

    private fun configureCellMargins(cell: XWPFTableCell) {
        val cellProperties = cell.ctTc.tcPr ?: cell.ctTc.addNewTcPr()
        val margins = if (cellProperties.isSetTcMar) cellProperties.tcMar else cellProperties.addNewTcMar()
        listOf(margins.addNewLeft(), margins.addNewRight()).forEach { margin ->
            margin.w = BigInteger.valueOf(TABLE_CELL_HORIZONTAL_MARGIN_TWIPS)
            margin.type = STTblWidth.DXA
        }
    }

    private fun configureBlackBorders(table: XWPFTable) {
        val tableProperties = table.ctTbl.tblPr
        if (tableProperties.isSetTblBorders) tableProperties.unsetTblBorders()
        val borders = tableProperties.addNewTblBorders()
        listOf(
            borders.addNewTop(), borders.addNewLeft(), borders.addNewBottom(),
            borders.addNewRight(), borders.addNewInsideH(), borders.addNewInsideV()
        ).forEach { border ->
            border.`val` = STBorder.SINGLE
            border.sz = BigInteger.valueOf(4)
            border.color = "000000"
        }
    }

    private fun createBodyParagraph(
        document: XWPFDocument,
        text: String,
        alignment: ParagraphAlignment = ParagraphAlignment.BOTH,
        recuado: Boolean = false
    ) {
        val p = document.createParagraph()
        p.alignment = alignment
        if (recuado) {
            p.indentationLeft = JURISPRUDENCE_LEFT_INDENT_TWIPS
            p.indentationRight = JURISPRUDENCE_RIGHT_INDENT_TWIPS
        }

        val ppr = p.ctp.pPr ?: p.ctp.addNewPPr()

        val spacing = ppr.addNewSpacing()
        spacing.line = BigInteger.valueOf(276)
        spacing.lineRule = STLineSpacingRule.AUTO
        spacing.after = BigInteger.valueOf(120)

        addFormattedText(p, text)
    }

    private fun createImageCaption(document: XWPFDocument, caption: String) {
        document.createParagraph().apply {
            alignment = ParagraphAlignment.CENTER
            createRun().apply {
                fontFamily = "Garamond"
                fontSize = 10
                isItalic = true
                setText(caption)
            }
        }
    }

    private fun addFormattedText(
        paragraph: XWPFParagraph,
        text: String,
        inherited: TextStyle = TextStyle()
    ) {
        var cursor = 0
        while (cursor < text.length) {
            val token = formattingTokenAt(text, cursor)
            if (token == null) {
                val next = (cursor + 1 until text.length)
                    .firstOrNull { formattingTokenAt(text, it) != null }
                    ?: text.length
                addFormattedRun(paragraph, text.substring(cursor, next), inherited.bold, inherited.underline, inherited.italic)
                cursor = next
                continue
            }

            val closingIndex = if (token.open == "*") {
                findClosingItalicMarker(text, cursor + 1)
            } else {
                text.indexOf(token.close, cursor + token.open.length)
            }
            if (closingIndex < 0) {
                addFormattedRun(paragraph, token.open, inherited.bold, inherited.underline, inherited.italic)
                cursor += token.open.length
                continue
            }

            val style = inherited.merge(token.style)
            addFormattedText(
                paragraph,
                text.substring(cursor + token.open.length, closingIndex),
                style
            )
            cursor = closingIndex + token.close.length
        }
    }

    private fun formattingTokenAt(text: String, index: Int): FormattingToken? = when {
        text.startsWith("__***", index) -> FormattingToken("__***", "***__", TextStyle(bold = true, underline = true, italic = true))
        text.startsWith("__**", index) -> FormattingToken("__**", "**__", TextStyle(bold = true, underline = true))
        text.startsWith("***", index) -> FormattingToken("***", "***", TextStyle(bold = true, italic = true))
        text.startsWith("**", index) -> FormattingToken("**", "**", TextStyle(bold = true))
        text.startsWith("__", index) -> FormattingToken("__", "__", TextStyle(underline = true))
        text.startsWith("*", index) -> FormattingToken("*", "*", TextStyle(italic = true))
        else -> null
    }

    private fun findClosingItalicMarker(text: String, start: Int): Int =
        (start until text.length).firstOrNull { index ->
            text[index] == '*' &&
                (index == 0 || text[index - 1] != '*') &&
                (index + 1 >= text.length || text[index + 1] != '*')
        } ?: -1

    private fun addFormattedRun(
        paragraph: XWPFParagraph,
        text: String,
        bold: Boolean = false,
        underline: Boolean = false,
        italic: Boolean = false
    ) {
        if (text.isEmpty()) return
        val run = paragraph.createRun()
        run.fontFamily = "Garamond"
        run.fontSize = 12
        run.isBold = bold
        run.isItalic = italic
        run.underline = if (underline) org.apache.poi.xwpf.usermodel.UnderlinePatterns.SINGLE else org.apache.poi.xwpf.usermodel.UnderlinePatterns.NONE
        run.setText(text)
    }

    private data class TextStyle(
        val bold: Boolean = false,
        val underline: Boolean = false,
        val italic: Boolean = false
    ) {
        fun merge(other: TextStyle) = TextStyle(
            bold = bold || other.bold,
            underline = underline || other.underline,
            italic = italic || other.italic
        )
    }

    private data class FormattingToken(
        val open: String,
        val close: String,
        val style: TextStyle
    )

    private fun addBodyImage(
        document: XWPFDocument,
        image: RtExportImageRequest,
        preserveAspectRatio: Boolean = false
    ) {
        val name = image.nomeOriginal
        logger.infof("DOCX imagem '%s': bytes=%d, url=%s", name, image.bytes?.size ?: 0, image.url ?: "<bytes>")
        try {
            val input = image.bytes?.inputStream() ?: URL(image.url ?: error("Imagem sem bytes e sem URL")).openStream()
            input.use {
                val bytes = it.readBytes()
                val dimensions = if (preserveAspectRatio) {
                    val source = ImageIO.read(bytes.inputStream()) ?: error("Imagem inválida: $name")
                    val scale = minOf(
                        BODY_IMAGE_MAX_WIDTH_POINTS / source.width,
                        BODY_IMAGE_MAX_HEIGHT_POINTS / source.height
                    )
                    source.width * scale to source.height * scale
                } else {
                    BODY_IMAGE_MAX_WIDTH_POINTS to 300.0
                }
                val paragraph = document.createParagraph()
                paragraph.alignment = ParagraphAlignment.CENTER
                if (preserveAspectRatio) {
                    val properties = paragraph.ctp.pPr ?: paragraph.ctp.addNewPPr()
                    val spacing = properties.addNewSpacing()
                    spacing.before = BigInteger.valueOf(FIXED_IMAGE_VERTICAL_SPACING_TWIPS)
                    spacing.after = BigInteger.valueOf(FIXED_IMAGE_VERTICAL_SPACING_TWIPS)
                }
                paragraph.createRun().addPicture(
                    bytes.inputStream(),
                    pictureType(image.contentType),
                    name,
                    Units.toEMU(dimensions.first),
                    Units.toEMU(dimensions.second)
                )
                logger.infof("DOCX imagem '%s': addPicture concluído; mediaCount=%d", name, document.allPictures.size)
            }
        } catch (error: Exception) {
            logger.errorf(error, "DOCX imagem '%s': falha ao abrir bytes/URL ou inserir imagem; URL=%s", name, image.url ?: "<bytes>")
            throw error
        }
    }

    private fun addInlineImage(
        document: XWPFDocument,
        image: RtExportInlineImageRequest,
        spaced: Boolean = false
    ) {
        val widthEmu = Units.toEMU(BODY_IMAGE_MAX_WIDTH_POINTS)
        val heightEmu = (widthEmu.toLong() * image.originalHeightPx / image.originalWidthPx).toInt()
        image.bytes.inputStream().use { input ->
            document.createParagraph().apply {
                alignment = ParagraphAlignment.CENTER
                if (spaced) {
                    val properties = ctp.pPr ?: ctp.addNewPPr()
                    val spacing = properties.addNewSpacing()
                    spacing.before = BigInteger.valueOf(FIXED_IMAGE_VERTICAL_SPACING_TWIPS)
                    spacing.after = BigInteger.valueOf(FIXED_IMAGE_VERTICAL_SPACING_TWIPS)
                }
                createRun().addPicture(
                    input,
                    pictureType(image.contentType),
                    image.nomeOriginal,
                    widthEmu,
                    heightEmu
                )
            }
        }
    }

    private fun pictureType(contentType: String): Int = when (contentType.lowercase()) {
        "image/png" -> XWPFDocument.PICTURE_TYPE_PNG
        else -> XWPFDocument.PICTURE_TYPE_JPEG
    }

    private companion object {
        const val BODY_IMAGE_MAX_WIDTH_POINTS = 450.0
        const val BODY_IMAGE_MAX_HEIGHT_POINTS = 600.0
        const val JURISPRUDENCE_LEFT_INDENT_TWIPS = 1440
        const val JURISPRUDENCE_RIGHT_INDENT_TWIPS = 720
        const val USEFUL_PAGE_WIDTH_TWIPS = 9213
        const val TABLE_COLUMN_WIDTH_TWIPS = 4606
        const val TABLE_CELL_HORIZONTAL_MARGIN_TWIPS = 140L
        const val TABLE_SPACING_BEFORE_TWIPS = 160L
        const val TABLE_SPACING_AFTER_TWIPS = 200L
        const val FIXED_IMAGE_VERTICAL_SPACING_TWIPS = 160L
        val INTRAJORNADA_KEEP_NEXT_PARAGRAPHS = setOf(19, 23, 27, 31, 36, 40, 45)
    }

    // Parágrafo vazio
    private fun createEmptyParagraph(document: XWPFDocument) {
        val p = document.createParagraph()
        val run = p.createRun()
        run.fontFamily = "Garamond"
        run.fontSize = 12
        run.setText("")
    }

    // Aplica bordas superior e inferior no padrão do documento
    private fun applyBorders(ppr: org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr) {
        val pBdr = ppr.addNewPBdr()

        val top = pBdr.addNewTop()
        top.`val` = STBorder.SINGLE
        top.sz = BigInteger.valueOf(4)
        top.space = BigInteger.valueOf(1)
        top.color = "auto"

        val bottom = pBdr.addNewBottom()
        bottom.`val` = STBorder.SINGLE
        bottom.sz = BigInteger.valueOf(4)
        bottom.space = BigInteger.valueOf(1)
        bottom.color = "auto"
    }
}
