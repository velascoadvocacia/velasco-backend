package com.example.lawyer.service

import com.example.lawyer.dto.request.RtExportRequest
import com.example.lawyer.dto.request.RtExportBlockRequest
import jakarta.enterprise.context.ApplicationScoped
import org.apache.poi.util.Units
import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFRun
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STJc
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STLineSpacingRule
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STPageOrientation
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URL
import java.math.BigInteger
import org.jboss.logging.Logger

@ApplicationScoped
class RtExportService {
    private val logger = Logger.getLogger(RtExportService::class.java)

    fun generate(request: RtExportRequest): ByteArray {
        XWPFDocument().use { document ->
            configureA4Page(document)
            addHeader(document)
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

    private fun addHeader(document: XWPFDocument) {
        try {
            val policy = document.createHeaderFooterPolicy()
            val header = policy.createHeader(XWPFHeaderFooterPolicy.DEFAULT)
            val p = header.createParagraph()
            p.alignment = ParagraphAlignment.CENTER

            val inputStream: InputStream? = javaClass.getResourceAsStream("/assets/header_velasco.jpeg")
            if (inputStream != null) {
                val run = p.createRun()
                run.addPicture(
                    inputStream,
                    XWPFDocument.PICTURE_TYPE_JPEG,
                    "header_velasco.jpeg",
                    Units.toEMU(470.0),
                    Units.toEMU(80.0)
                )
                inputStream.close()
            }
        } catch (e: Exception) {
            // Header não criado se imagem não encontrada
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
        block.content.split("\n\n").forEachIndexed { index, paragraphText ->
            createBodyParagraph(document, paragraphText)
            if (index == 0) {
                block.anexos.forEach { image -> addBodyImage(document, image.url, image.contentType, image.nomeOriginal) }
            }
        }
    }

    private fun createBodyParagraph(document: XWPFDocument, text: String) {
        val p = document.createParagraph()
        p.alignment = ParagraphAlignment.BOTH

        val ppr = p.ctp.pPr ?: p.ctp.addNewPPr()

        val spacing = ppr.addNewSpacing()
        spacing.line = BigInteger.valueOf(276)
        spacing.lineRule = STLineSpacingRule.AUTO
        spacing.after = BigInteger.valueOf(120)

        val tokenPattern = Regex("(\\*\\*|__)(.+?)\\1")
        var cursor = 0
        tokenPattern.findAll(text).forEach { match ->
            addFormattedRun(p, text.substring(cursor, match.range.first), false, false)
            val marker = match.value.take(2)
            addFormattedRun(p, match.groupValues[2], marker == "**", marker == "__")
            cursor = match.range.last + 1
        }
        addFormattedRun(p, text.substring(cursor), false, false)
    }

    private fun addFormattedRun(paragraph: XWPFParagraph, text: String, bold: Boolean, underline: Boolean) {
        if (text.isEmpty()) return
        val run = paragraph.createRun()
        run.fontFamily = "Garamond"
        run.fontSize = 12
        run.isBold = bold
        run.underline = if (underline) org.apache.poi.xwpf.usermodel.UnderlinePatterns.SINGLE else org.apache.poi.xwpf.usermodel.UnderlinePatterns.NONE
        run.setText(text)
    }

    private fun addBodyImage(document: XWPFDocument, url: String, contentType: String, name: String) {
        runCatching {
            URL(url).openStream().use { input ->
                val paragraph = document.createParagraph()
                paragraph.alignment = ParagraphAlignment.CENTER
                paragraph.createRun().addPicture(
                    input,
                    pictureType(contentType),
                    name,
                    Units.toEMU(450.0),
                    Units.toEMU(300.0)
                )
            }
        }.onFailure { error ->
            logger.warnf(error, "Não foi possível inserir a imagem anexada %s no DOCX", name)
        }
    }

    private fun pictureType(contentType: String): Int = when (contentType.lowercase()) {
        "image/png" -> XWPFDocument.PICTURE_TYPE_PNG
        else -> XWPFDocument.PICTURE_TYPE_JPEG
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
