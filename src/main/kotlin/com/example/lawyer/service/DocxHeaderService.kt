package com.example.lawyer.service

import jakarta.enterprise.context.ApplicationScoped
import org.apache.poi.util.Units
import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.XWPFDocument

@ApplicationScoped
class DocxHeaderService {
    fun addHeader(document: XWPFDocument) {
        try {
            val policy = document.createHeaderFooterPolicy()
            val header = policy.createHeader(XWPFHeaderFooterPolicy.DEFAULT)
            val paragraph = header.createParagraph()
            paragraph.alignment = ParagraphAlignment.CENTER

            javaClass.getResourceAsStream("/assets/header_velasco.jpeg")?.use { input ->
                paragraph.createRun().addPicture(
                    input,
                    XWPFDocument.PICTURE_TYPE_JPEG,
                    "header_velasco.jpeg",
                    Units.toEMU(470.0),
                    Units.toEMU(80.0)
                )
            }
        } catch (_: Exception) {
            // Mantém a exportação disponível caso o recurso visual não possa ser carregado.
        }
    }
}
