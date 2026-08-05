package com.example.lawyer.resource

import com.example.lawyer.domain.enums.TipoPessoa
import com.example.lawyer.dto.request.PessoaRequestDTO
import com.example.lawyer.dto.request.RtPreviewRequest
import com.example.lawyer.service.PessoaService
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.security.TestSecurity
import io.restassured.RestAssured.given
import jakarta.inject.Inject
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.startsWith
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.UnderlinePatterns
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.awt.Color
import java.awt.image.BufferedImage
import java.time.LocalDate
import java.util.Random
import java.util.zip.ZipInputStream
import javax.imageio.ImageIO

@QuarkusTest
class RtExportResourceTest {
    @Inject
    lateinit var pessoaService: PessoaService

    @Test
    @TestSecurity(user = "advogado", roles = ["ADVOGADO"])
    fun `should export rt docx`() {
        given()
            .multiPart(
                "payload",
                """{"claimantName":"Maria Silva","blocks":[{"title":"Dos Fatos","content":"Conteúdo da reclamatória trabalhista."}]}""",
                "text/plain"
            )
            .`when`()
            .post("/rt/export")
            .then()
            .statusCode(200)
            .header("Content-Type", equalTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
            .header("Content-Disposition", equalTo("attachment; filename=\"RT - Maria Silva.docx\""))
            .body(startsWith("PK"))
    }

    @Test
    @TestSecurity(user = "advogado", roles = ["ADVOGADO"])
    fun `should render all supported text styles in docx`() {
        val payload = """{
            "claimantName":"Maria Silva",
            "blocks":[{"title":"Formatação","content":"**negrito** *italico* __sublinhado__ __**ambosBU**__ ***ambosBI***"}]
        }""".trimIndent()

        val docx = given()
            .multiPart("payload", payload, "text/plain")
            .`when`()
            .post("/rt/export")
            .then()
            .statusCode(200)
            .extract()
            .asByteArray()

        XWPFDocument(ByteArrayInputStream(docx)).use { document ->
            val runs = document.paragraphs.first { it.text.contains("negrito italico") }.runs
                .associateBy { it.text() }
            assertTrue(runs.getValue("negrito").isBold)
            assertTrue(runs.getValue("italico").isItalic)
            assertEquals(UnderlinePatterns.SINGLE, runs.getValue("sublinhado").underline)
            assertTrue(runs.getValue("ambosBU").isBold)
            assertEquals(UnderlinePatterns.SINGLE, runs.getValue("ambosBU").underline)
            assertTrue(runs.getValue("ambosBI").isBold)
            assertTrue(runs.getValue("ambosBI").isItalic)
        }
    }

    @Test
    @TestSecurity(user = "advogado", roles = ["ADVOGADO"])
    fun `should export economic group image inside docx`() {
        val payload = """{
            "claimantName":"Maria Silva",
            "blocosSelecionados":["responsabilidade_solidaria_grupo_economico"],
            "dadosVariaveis":{"descricaoAtividadePrincipal":"comércio varejista"}
        }""".trimIndent()
        val docx = given()
            .multiPart("payload", payload, "text/plain")
            .multiPart("anexo_responsabilidade_solidaria_grupo_economico_0", "grupo.png", TEST_IMAGE_1, "image/png")
            .`when`()
            .post("/rt/export")
            .then()
            .statusCode(200)
            .extract()
            .asByteArray()

        assertDocxImages(
            docx,
            listOf(TEST_IMAGE_1),
            listOf("As empresas rés, que formam um grupo econômico")
        )
    }

    @Test
    @TestSecurity(user = "advogado", roles = ["ADVOGADO"])
    fun `should export ctps image inside docx`() {
        val payload = """{
            "claimantName":"Maria Silva",
            "blocosSelecionados":["baixa_ctps_tutela"]
        }""".trimIndent()

        val docx = given()
            .multiPart("payload", payload, "text/plain")
            .multiPart("anexo_baixa_ctps_tutela_0", "ctps.png", TEST_IMAGE_1, "image/png")
            .`when`()
            .post("/rt/export")
            .then()
            .statusCode(200)
            .extract()
            .asByteArray()

        assertDocxImages(docx, listOf(TEST_IMAGE_1), listOf("Conquanto a parte autora"))
    }

    @Test
    @TestSecurity(user = "advogado", roles = ["ADVOGADO"])
    fun `should export ctps image with legacy block title and field alias`() {
        val payload = """{
            "claimantName":"Maria Silva",
            "blocks":[{
                "title":"Baixa na CTPS física. Tutela antecipada",
                "content":"Conquanto a parte autora tenha sido dispensada"
            }]
        }""".trimIndent()

        val docx = given()
            .multiPart("payload", payload, "text/plain")
            .multiPart("anexo_baixa_ctps_0", "ctps.png", TEST_IMAGE_1, "image/png")
            .`when`()
            .post("/rt/export")
            .then()
            .statusCode(200)
            .extract()
            .asByteArray()

        assertDocxImages(docx, listOf(TEST_IMAGE_1), listOf("Conquanto a parte autora"))
    }

    @Test
    @TestSecurity(user = "advogado", roles = ["ADVOGADO"])
    fun `should keep images associated with both blocks in same docx`() {
        val payload = """{
            "claimantName":"Maria Silva",
            "blocosSelecionados":["baixa_ctps_tutela","responsabilidade_solidaria_grupo_economico"],
            "dadosVariaveis":{"descricaoAtividadePrincipal":"comércio varejista"}
        }""".trimIndent()

        val docx = given()
            .multiPart("payload", payload, "text/plain")
            .multiPart("anexo_baixa_ctps_tutela_0", "ctps.png", TEST_IMAGE_1, "image/png")
            .multiPart("anexo_responsabilidade_solidaria_grupo_economico_0", "grupo.png", TEST_IMAGE_2, "image/png")
            .`when`()
            .post("/rt/export")
            .then()
            .statusCode(200)
            .extract()
            .asByteArray()

        assertDocxImages(
            docx,
            listOf(TEST_IMAGE_1, TEST_IMAGE_2),
            listOf("Conquanto a parte autora", "As empresas rés, que formam um grupo econômico")
        )
    }

    @Test
    @TestSecurity(user = "advogado", roles = ["ADVOGADO"])
    fun `should preserve multiple image order in economic group block`() {
        val payload = """{
            "claimantName":"Maria Silva",
            "blocosSelecionados":["responsabilidade_solidaria_grupo_economico"],
            "dadosVariaveis":{"descricaoAtividadePrincipal":"comércio varejista"}
        }""".trimIndent()

        val docx = given()
            .multiPart("payload", payload, "text/plain")
            .multiPart("anexo_responsabilidade_solidaria_grupo_economico_0", "cnpj.png", TEST_IMAGE_1, "image/png")
            .multiPart("anexo_responsabilidade_solidaria_grupo_economico_1", "qsa.png", TEST_IMAGE_2, "image/png")
            .`when`()
            .post("/rt/export")
            .then()
            .statusCode(200)
            .extract()
            .asByteArray()

        assertDocxImages(
            docx,
            listOf(TEST_IMAGE_1, TEST_IMAGE_2),
            listOf(
                "As empresas rés, que formam um grupo econômico",
                "As empresas rés, que formam um grupo econômico"
            )
        )
    }

    @Test
    @TestSecurity(user = "advogado", roles = ["ADVOGADO"])
    fun `should export economic group images with legacy blocks payload`() {
        val payload = """{
            "claimantName":"Maria Silva",
            "blocks":[{
                "id":"responsabilidade_solidaria_grupo_economico",
                "title":"Responsabilidade solidária. Grupo econômico",
                "content":"As empresas reclamadas formam um grupo economico"
            }]
        }""".trimIndent()

        val docx = given()
            .multiPart("payload", payload, "text/plain")
            .multiPart("anexo_responsabilidade_solidaria_grupo_economico_0", "cnpj.png", TEST_IMAGE_1, "image/png")
            .multiPart("anexo_responsabilidade_solidaria_grupo_economico_1", "qsa.png", TEST_IMAGE_2, "image/png")
            .`when`()
            .post("/rt/export")
            .then()
            .statusCode(200)
            .extract()
            .asByteArray()

        assertDocxImages(
            docx,
            listOf(TEST_IMAGE_1, TEST_IMAGE_2),
            listOf(
                "As empresas reclamadas formam um grupo economico",
                "As empresas reclamadas formam um grupo economico"
            )
        )
    }

    private fun assertDocxImages(docx: ByteArray, expectedImages: List<ByteArray>, bodyPrefixes: List<String>) {
        XWPFDocument(ByteArrayInputStream(docx)).use { document ->
            val pictureParagraphs = document.paragraphs.withIndex().filter { (_, paragraph) ->
                paragraph.runs.any { it.embeddedPictures.isNotEmpty() }
            }
            assertEquals(expectedImages.size, pictureParagraphs.size, "Quantidade incorreta de parágrafos com imagem")

            expectedImages.forEachIndexed { index, expectedImage ->
                val bodyIndex = document.paragraphs.withIndex().firstOrNull { (paragraphIndex, paragraph) ->
                    paragraph.text.startsWith(bodyPrefixes[index]) && paragraphIndex < pictureParagraphs[index].index
                }?.index ?: -1
                assertTrue(bodyIndex >= 0, "Parágrafo do bloco não encontrado antes da imagem ${index + 1}")
                if (index == 0 || bodyPrefixes[index] != bodyPrefixes[index - 1]) {
                    assertEquals(bodyIndex + 1, pictureParagraphs[index].index, "Imagem fora da posição do bloco")
                } else {
                    assertEquals(pictureParagraphs[index - 1].index + 1, pictureParagraphs[index].index, "Ordem das imagens alterada")
                }
                val actualBytes = pictureParagraphs[index].value.runs
                    .flatMap { it.embeddedPictures }
                    .single()
                    .pictureData.data
                assertTrue(expectedImage.contentEquals(actualBytes), "Conteúdo/ordem da imagem incorreto")
            }
        }
    }

    @Test
    @TestSecurity(user = "advogado", roles = ["ADVOGADO"])
    fun `should preview claimant data and omit empty fields`() {
        val suffix = System.nanoTime().toString()
        val first = pessoaService.create(
            pessoaRequest(
                suffix = suffix,
                cpf = "12345678909",
                dataNascimento = LocalDate.of(1990, 3, 15),
                nomeMae = "Maria da Silva"
            )
        )
        val second = pessoaService.create(
            pessoaRequest(
                suffix = "segundo-$suffix",
                cpf = null,
                dataNascimento = LocalDate.of(1985, 11, 27),
                nomeMae = ""
            )
        )

        given()
            .contentType("application/json")
            .body(
                RtPreviewRequest(
                    reclamantesIds = listOf(first.id!!, second.id!!),
                    blocosSelecionados = listOf("dados_reclamante")
                )
            )
            .`when`()
            .post("/rt/preview")
            .then()
            .statusCode(200)
            .body("blocos.size()", equalTo(1))
            .body("blocos[0].id", equalTo("dados_reclamante"))
            .body(
                "blocos[0].texto",
                equalTo("CPF: 12345678909\nD.N.: 15/03/1990\nMãe: Maria da Silva\n\nD.N.: 27/11/1985")
            )
    }

    @Test
    @TestSecurity(user = "advogado", roles = ["ADVOGADO"])
    fun `should preview general contract aspects with dynamic title`() {
        given()
            .contentType("application/json")
            .body(
                RtPreviewRequest(
                    blocosSelecionados = listOf("contrato_aspectos_gerais"),
                    dadosVariaveis = mapOf(
                        "dataContratacao" to "2023-01-10",
                        "funcaoContrato" to "Auxiliar de produção",
                        "remuneracao" to "1800.00",
                        "motivoExtincao" to "1",
                        "dataExtincao" to "2024-05-20",
                        "dataProjecaoAviso" to "2024-06-19",
                        "informacoesComplementares" to ""
                    )
                )
            )
            .`when`()
            .post("/rt/preview")
            .then()
            .statusCode(200)
            .body("blocos.size()", equalTo(1))
            .body("blocos[0].titulo", equalTo("Contrato de trabalho - Aspectos gerais"))
            .body(
                "blocos[0].texto",
                equalTo(
                    "A parte autora foi contratada pela parte ré em 10/01/2023, para exercer a função de " +
                        "Auxiliar de produção, com última remuneração de R$ 1.800,00, com a extinção do vínculo " +
                        "empregatício sem justa causa em 20/05/2024."
                )
            )
    }

    @Test
    @TestSecurity(user = "advogado", roles = ["ADVOGADO"])
    fun `should preview economic group liability with formatting`() {
        given()
            .contentType("application/json")
            .body(
                RtPreviewRequest(
                    blocosSelecionados = listOf("responsabilidade_solidaria_grupo_economico"),
                    dadosVariaveis = mapOf("descricaoAtividadePrincipal" to "comércio varejista de alimentos")
                )
            )
            .`when`()
            .post("/rt/preview")
            .then()
            .statusCode(200)
            .body("blocos.size()", equalTo(1))
            .body("blocos[0].id", equalTo("responsabilidade_solidaria_grupo_economico"))
            .body("blocos[0].titulo", equalTo("Responsabilidade solidária. Grupo econômico"))
            .body("blocos[0].texto", containsString("de comércio varejista de alimentos:"))
            .body("blocos[0].texto", containsString("**art. 2º, § 2º, da CLT**"))
            .body("blocos[0].texto", containsString("__a direção, controle ou administração de outra"))
            .body("blocos[0].texto", containsString("**REQUER-SE** a condenação solidária das rés."))
            .body("blocos[0].anexos.size()", equalTo(0))
    }

    @Test
    @TestSecurity(user = "advogado", roles = ["ADVOGADO"])
    fun `should use placeholder when economic activity is blank`() {
        given()
            .contentType("application/json")
            .body(
                RtPreviewRequest(
                    blocosSelecionados = listOf("responsabilidade_solidaria_grupo_economico"),
                    dadosVariaveis = mapOf("descricaoAtividadePrincipal" to "  ")
                )
            )
            .`when`()
            .post("/rt/preview")
            .then()
            .statusCode(200)
            .body("blocos[0].texto", containsString("semelhantes, de ___:"))
    }

    @Test
    @TestSecurity(user = "advogado", roles = ["ADVOGADO"])
    fun `should preview subsidiary liability preserving defendant order`() {
        val suffix = System.nanoTime().toString()
        val first = pessoaService.create(pessoaRequest("primeira-$suffix", null, null, null))
        val second = pessoaService.create(pessoaRequest("segunda-$suffix", null, null, null))

        given()
            .contentType("application/json")
            .body(
                RtPreviewRequest(
                    reclamadasIds = listOf(first.id!!, second.id!!),
                    blocosSelecionados = listOf("responsabilidade_subsidiaria")
                )
            )
            .`when`()
            .post("/rt/preview")
            .then()
            .statusCode(200)
            .body("blocos[0].id", equalTo("responsabilidade_subsidiaria"))
            .body("blocos[0].titulo", equalTo("Responsabilidade subsidiária"))
            .body("blocos[0].texto", containsString("1ª ré (Reclamante primeira-$suffix)"))
            .body("blocos[0].texto", containsString("2ª ré (Reclamante segunda-$suffix)"))
            .body("blocos[0].texto", containsString("__**§ 5º. A empresa contratante"))
            .body("blocos[0].texto", containsString("***O inadimplemento das obrigações"))
    }

    @Test
    @TestSecurity(user = "advogado", roles = ["ADVOGADO"])
    fun `should preview subsidiary liability with one defendant`() {
        val suffix = System.nanoTime().toString()
        val first = pessoaService.create(pessoaRequest("unica-$suffix", null, null, null))

        given()
            .contentType("application/json")
            .body(
                RtPreviewRequest(
                    reclamadasIds = listOf(first.id!!),
                    blocosSelecionados = listOf("responsabilidade_subsidiaria")
                )
            )
            .`when`()
            .post("/rt/preview")
            .then()
            .statusCode(200)
            .body("blocos[0].texto", containsString("1ª ré (Reclamante unica-$suffix)"))
            .body("blocos[0].texto", containsString("2ª ré ()"))
    }

    @Test
    @TestSecurity(user = "advogado", roles = ["ADVOGADO"])
    fun `should preview subsidiary liability without defendants`() {
        given()
            .contentType("application/json")
            .body(RtPreviewRequest(blocosSelecionados = listOf("responsabilidade_subsidiaria")))
            .`when`()
            .post("/rt/preview")
            .then()
            .statusCode(200)
            .body("blocos[0].texto", containsString("1ª ré ()"))
            .body("blocos[0].texto", containsString("2ª ré ()"))
    }

    private fun pessoaRequest(
        suffix: String,
        cpf: String?,
        dataNascimento: LocalDate?,
        nomeMae: String?
    ) = PessoaRequestDTO(
        nome = "Reclamante $suffix",
        cpf = cpf,
        email = "reclamante.$suffix@example.com",
        nomeMae = nomeMae,
        tipoPessoa = TipoPessoa.FISICA,
        dataNascimento = dataNascimento
    )

    private companion object {
        val TEST_IMAGE_1: ByteArray = realisticScreenshot(1234)
        val TEST_IMAGE_2: ByteArray = realisticScreenshot(5678)

        fun realisticScreenshot(seed: Long): ByteArray {
            val image = BufferedImage(900, 1200, BufferedImage.TYPE_INT_RGB)
            val graphics = image.createGraphics()
            graphics.color = Color.WHITE
            graphics.fillRect(0, 0, image.width, image.height)
            graphics.color = Color(35, 70, 120)
            graphics.fillRect(0, 0, image.width, 110)
            graphics.color = Color.DARK_GRAY
            repeat(28) { row ->
                val y = 150 + row * 32
                graphics.drawLine(45, y, 855, y)
                graphics.drawString("Registro ${row + 1}  Documento trabalhista  Dados cadastrais", 60, y - 8)
            }
            graphics.dispose()

            val random = Random(seed)
            repeat(120_000) {
                val x = random.nextInt(image.width)
                val y = 115 + random.nextInt(image.height - 115)
                val tone = 190 + random.nextInt(66)
                image.setRGB(x, y, Color(tone, tone, tone).rgb)
            }
            return ByteArrayOutputStream().use { output ->
                ImageIO.write(image, "png", output)
                output.toByteArray().also {
                    check(it.size >= 50 * 1024) { "Imagem realista deve possuir pelo menos 50 KB" }
                }
            }
        }
    }
}
