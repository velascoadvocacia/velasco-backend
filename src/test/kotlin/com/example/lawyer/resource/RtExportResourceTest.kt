package com.example.lawyer.resource

import com.example.lawyer.domain.enums.TipoPessoa
import com.example.lawyer.domain.enums.EstadoCivil
import com.example.lawyer.domain.enums.PerfilUsuario
import com.example.lawyer.domain.enums.TratamentoAdvogado
import com.example.lawyer.domain.enums.Sexo
import com.example.lawyer.domain.enums.StatusProcesso
import com.example.lawyer.dto.request.EnderecoRequest
import com.example.lawyer.dto.request.EstrategiaProcessualRequest
import com.example.lawyer.dto.request.PessoaRequestDTO
import com.example.lawyer.dto.request.ProcessoCreateRequest
import com.example.lawyer.dto.request.RtPreviewRequest
import com.example.lawyer.dto.request.UsuarioCreateRequest
import com.example.lawyer.service.PessoaService
import com.example.lawyer.service.ProcessoService
import com.example.lawyer.service.UsuarioService
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.security.TestSecurity
import io.restassured.RestAssured.given
import jakarta.inject.Inject
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.startsWith
import org.hamcrest.CoreMatchers.not
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
import java.nio.file.Files
import java.nio.file.Path
import java.util.Random
import java.util.zip.ZipInputStream
import javax.imageio.ImageIO

@QuarkusTest
class RtExportResourceTest {
    @Inject
    lateinit var pessoaService: PessoaService

    @Inject
    lateinit var usuarioService: UsuarioService

    @Inject
    lateinit var processoService: ProcessoService

    @Test
    @TestSecurity(user = "advogado", roles = ["ADVOGADO"])
    fun `should export procuracao honorarios and hipossuficiencia with selected data`() {
        val suffix = System.nanoTime().toString()
        val reclamante = pessoaService.create(
            PessoaRequestDTO(
                nome = "Nayara Fernanda de Freitas Batista",
                cpf = validCpfFromSuffix(suffix, 1),
                email = "maria.procuracao.$suffix@example.com",
                telefone = "(45) 99999-0000",
                nacionalidade = "brasileira",
                estadoCivil = EstadoCivil.SOLTEIRO,
                sexo = Sexo.FEMININO,
                rg = "123456789",
                orgaoEmissorRg = "SESP/PR",
                tipoPessoa = TipoPessoa.FISICA,
                dataNascimento = LocalDate.of(1990, 1, 10),
                endereco = EnderecoRequest(
                    rua = "Rua Paraná",
                    numero = "38",
                    complemento = "Casa 2",
                    bairro = "Rainha dos Apóstolos",
                    cidade = "Terra Roxa",
                    estado = "PR",
                    cep = "85990000"
                )
            )
        )
        val reclamada = pessoaService.create(
            PessoaRequestDTO(
                nome = "Empresa Exemplo $suffix",
                cnpj = "11222333000181",
                email = "empresa.$suffix@example.com",
                tipoPessoa = TipoPessoa.JURIDICA
            )
        )
        val pessoaAdvogado = pessoaService.create(
            PessoaRequestDTO(
                nome = "Lucas Advogado $suffix",
                cpf = validCpfFromSuffix(suffix, 2),
                email = "lucas.advogado.$suffix@example.com",
                nacionalidade = "brasileiro",
                estadoCivil = EstadoCivil.CASADO,
                tipoPessoa = TipoPessoa.FISICA
            )
        )
        val advogado = usuarioService.create(
            UsuarioCreateRequest(
                username = "adv.procuracao.$suffix",
                senha = "senha-segura-123",
                pessoaId = pessoaAdvogado.id,
                perfil = PerfilUsuario.ADVOGADO,
                ufOab = "PR",
                numeroOab = "52.533",
                tratamento = TratamentoAdvogado.DR
            )
        )

        val docx = given()
            .contentType("application/json")
            .body(
                mapOf(
                    "reclamantesIds" to listOf(reclamante.id),
                    "reclamadasIds" to listOf(reclamada.id),
                    "advogadosIds" to listOf(advogado.id)
                )
            )
            .`when`()
            .post("/rt/export-procuracao")
            .then()
            .statusCode(200)
            .header("Content-Type", equalTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
            .header(
                "Content-Disposition",
                equalTo("attachment; filename=\"PROCURAÇÃO AD JUDICIA - NAYARA FERNANDA DE FREITAS BATISTA.docx\"")
            )
            .extract()
            .asByteArray()

        XWPFDocument(ByteArrayInputStream(docx)).use { document ->
            val text = document.paragraphs.joinToString("\n") { it.text }
            assertTrue(text.contains("PROCURAÇÃO AD JUDICIA"))
            assertTrue(text.contains("CONTRATO DE HONORÁRIOS"))
            assertTrue(text.contains("DECLARAÇÃO DE HIPOSSUFICIÊNCIA"))
            assertTrue(text.contains("NAYARA FERNANDA DE FREITAS BATISTA, brasileira, solteira"))
            assertTrue(text.contains("Rua Paraná, 38 – Casa 2, Bairro Rainha dos Apóstolos, CEP 85990-000"))
            assertTrue(text.contains("LUCAS ADVOGADO $suffix, brasileiro, casado, advogado, inscrito na OAB/PR nº 52.533"))
            assertTrue(text.contains("Empresa Exemplo $suffix e outros"))
            assertEquals(2, document.paragraphs.sumOf { paragraph -> paragraph.runs.sumOf { it.ctr.brList.size } })
            assertTrue(document.headerList.any { header ->
                header.paragraphs.any { paragraph -> paragraph.runs.any { it.embeddedPictures.isNotEmpty() } }
            })
            assertTrue(document.footerList.any { footer ->
                footer.paragraphs.any { paragraph -> paragraph.runs.any { it.embeddedPictures.isNotEmpty() } }
            })
            assertTrue(document.footerList.single().paragraphs.all {
                it.alignment == org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER
            })
            val headerParagraph = document.headerList.single().paragraphs.first { paragraph ->
                paragraph.runs.any { it.embeddedPictures.isNotEmpty() }
            }
            val headerPicture = headerParagraph.runs.single().embeddedPictures.single()
            assertEquals(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER, headerParagraph.alignment)
            assertEquals(org.apache.poi.util.Units.toEMU(470.0).toLong(), headerPicture.ctPicture.spPr.xfrm.ext.cx)
            assertEquals(org.apache.poi.util.Units.toEMU(80.0).toLong(), headerPicture.ctPicture.spPr.xfrm.ext.cy)
            val footerLinePicture = document.footerList.single().paragraphs[0].runs.single().embeddedPictures.single()
            assertEquals(7_543_800L, footerLinePicture.ctPicture.spPr.xfrm.ext.cx)
            assertEquals(199_521L, footerLinePicture.ctPicture.spPr.xfrm.ext.cy)
            assertEquals(
                794.0 / 21.0,
                footerLinePicture.ctPicture.spPr.xfrm.ext.cx.toDouble() / footerLinePicture.ctPicture.spPr.xfrm.ext.cy,
                0.001
            )
            val footerPicture = document.footerList.single().paragraphs[1].runs.single().embeddedPictures.single()
            assertEquals(2_598_852L, footerPicture.ctPicture.spPr.xfrm.ext.cx)
            assertEquals(783_534L, footerPicture.ctPicture.spPr.xfrm.ext.cy)
            assertEquals(
                670.0 / 202.0,
                footerPicture.ctPicture.spPr.xfrm.ext.cx.toDouble() / footerPicture.ctPicture.spPr.xfrm.ext.cy,
                0.001
            )
            val procuracaoTitle = document.paragraphs.first { it.text == "PROCURAÇÃO AD JUDICIA" }
            assertEquals(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER, procuracaoTitle.alignment)
            assertEquals(0, procuracaoTitle.indentationLeft)
            assertEquals(0, procuracaoTitle.indentationRight)
            assertEquals(0, procuracaoTitle.indentationFirstLine)
            assertTrue(procuracaoTitle.runs.single().isBold)
            assertEquals(UnderlinePatterns.SINGLE, procuracaoTitle.runs.single().underline)
            listOf("OUTORGANTE:", "OUTORGADOS:", "PODERES:", "Por um lado, CONTRATADO:", "Eu,")
                .map { prefix -> document.paragraphs.first { it.text.startsWith(prefix) } }
                .flatMap { it.runs }
                .filter { it.text().isNotEmpty() && it.ctr.tabList.isEmpty() }
                .forEach { assertEquals(12, it.fontSize) }
            val outorganteParagraph = document.paragraphs.first { it.text.startsWith("OUTORGANTE:") }
            val outorgadosParagraph = document.paragraphs.first { it.text.startsWith("OUTORGADOS:") }
            val poderesParagraph = document.paragraphs.first { it.text.startsWith("PODERES:") }
            listOf(outorganteParagraph, outorgadosParagraph, poderesParagraph).forEach { paragraph ->
                assertEquals(1_700, paragraph.indentationLeft)
                assertEquals(1_700, paragraph.indentationHanging)
                assertEquals(java.math.BigInteger.valueOf(1_700), paragraph.ctp.pPr.tabs.tabList.single().pos)
                assertEquals(1, paragraph.runs.sumOf { it.ctr.tabList.size })
            }
            val poderesIndex = document.paragraphs.indexOf(poderesParagraph)
            assertTrue(document.paragraphs[poderesIndex + 1].text.isEmpty())
            assertTrue(document.paragraphs[poderesIndex + 2].text.startsWith("PODERES ESPECIAIS:"))
            val nameRun = document.paragraphs.flatMap { it.runs }
                .first { it.text() == "NAYARA FERNANDA DE FREITAS BATISTA" }
            assertTrue(nameRun.isBold)
            assertTrue(document.paragraphs.flatMap { it.runs }.filter { it.text().contains("brasileira") }.none { it.isBold })
            val advogadoNameRun = outorgadosParagraph.runs.first { it.text().contains("LUCAS ADVOGADO $suffix") }
            assertTrue(advogadoNameRun.isBold)
            val reclamadaRun = document.paragraphs.first { it.text.startsWith("PODERES ESPECIAIS:") }
                .runs.first { it.text() == "Empresa Exemplo $suffix" }
            assertTrue(reclamadaRun.isBold)
            assertEquals(UnderlinePatterns.SINGLE, reclamadaRun.underline)
            val declarationParagraph = document.paragraphs.first { it.text.startsWith("Eu,") }
            assertTrue(declarationParagraph.runs.first {
                it.text().contains("NAYARA FERNANDA DE FREITAS BATISTA")
            }.isBold)
            val claimantSignatures = document.paragraphs.filter { it.text == "Nayara Fernanda de Freitas Batista" }
            assertEquals(setOf(0), claimantSignatures.map { it.spacingBefore }.toSet())
            claimantSignatures.forEach { signatureParagraph ->
                assertEquals("single", signatureParagraph.ctp.pPr.pBdr.top.`val`.toString())
                assertTrue(signatureParagraph.ctp.pPr.isSetKeepLines)
                assertEquals(2_100, signatureParagraph.indentationLeft)
                assertEquals(2_100, signatureParagraph.indentationRight)
                assertTrue(signatureParagraph.runs.single().isBold)
            }
            assertEquals(2, document.tables.size)
            val signerLabels = document.tables[0].rows.single().tableCells.map { it.text }
            assertEquals(listOf("Nayara Fernanda de Freitas Batista", "", "ADVOGADO:"), signerLabels)
            val witnessLabels = document.tables[1].rows.single().tableCells.map { it.text }
            assertEquals(listOf("TESTEMUNHA 1", "", "TESTEMUNHA 2"), witnessLabels)
            document.tables.forEach { table ->
                assertEquals(7_600, table.width)
                assertTrue(!table.ctTbl.tblPr.isSetTblBorders)
                listOf(0, 2).forEach { cellIndex ->
                    val signatureParagraph = table.getRow(0).getCell(cellIndex).paragraphs.single()
                    assertEquals("single", signatureParagraph.ctp.pPr.pBdr.top.`val`.toString())
                    assertTrue(signatureParagraph.runs.single().isBold)
                }
            }
            val witnesses = document.paragraphs.first { it.text == "TESTEMUNHAS" }
            assertEquals(240, witnesses.spacingBefore)
            assertEquals(720, witnesses.spacingAfter)
            val dateParagraphs = document.paragraphs.filter { it.text.startsWith("Cascavel,") }
            assertEquals(listOf(1_600, 1_200, 1_600), dateParagraphs.map { it.spacingBefore })
            dateParagraphs.forEach { dateParagraph ->
                assertEquals(720, dateParagraph.spacingAfter)
                assertTrue(dateParagraph.ctp.pPr.isSetKeepNext)
            }
            val contractTitle = document.paragraphs.first { it.text == "CONTRATO DE HONORÁRIOS" }
            val contractTitleIndex = document.paragraphs.indexOf(contractTitle)
            assertTrue(document.paragraphs[contractTitleIndex - 1].runs.any { it.ctr.brList.isNotEmpty() })
            assertTrue(document.paragraphs[contractTitleIndex + 1].text.isEmpty())
            val contractOpening = document.paragraphs.first { it.text.startsWith("Por um lado, CONTRATADO:") }
            assertTrue(contractOpening.runs.first { it.text().contains("LUCAS ADVOGADO $suffix") }.isBold)
            val clauses = (1..7).map { number ->
                document.paragraphs.first { it.text.startsWith("${number}ª -") }
            }
            clauses.forEach { clause ->
                assertEquals(927, clause.indentationLeft)
                assertEquals(700, clause.indentationHanging)
                assertEquals(java.math.BigInteger.valueOf(927), clause.ctp.pPr.tabs.tabList.single().pos)
                assertEquals(1, clause.runs.sumOf { it.ctr.tabList.size })
            }
            assertEquals(java.math.BigInteger.valueOf(3_200), document.document.body.sectPr.pgMar.bottom)
            val secondClause = clauses[1]
            assertTrue(secondClause.runs.first { it.text() == "30% (trinta por cento)" }.isBold)
            assertTrue(secondClause.runs.first { it.text() == "5% (cinco por cento)" }.isBold)
        }
        saveGeneratedDocument("procuracao-nayara-completa.docx", docx)
    }

    @Test
    @TestSecurity(user = "advogado", roles = ["ADVOGADO"])
    fun `should omit absent fields without placeholders or broken punctuation`() {
        val suffix = System.nanoTime().toString()
        val reclamante = pessoaService.create(
            PessoaRequestDTO(
                nome = "Pessoa Com Dados Omitidos",
                cpf = validCpfFromSuffix(suffix, 3),
                email = "omissao.$suffix@example.com",
                nacionalidade = "brasileira",
                sexo = Sexo.FEMININO,
                tipoPessoa = TipoPessoa.FISICA,
                endereco = EnderecoRequest(cidade = "Cascavel", estado = "PR")
            )
        )
        val pessoaAdvogado = pessoaService.create(
            PessoaRequestDTO(
                nome = "Advogada Sem Qualificacao",
                cpf = validCpfFromSuffix(suffix, 4),
                email = "adv.omissao.$suffix@example.com",
                sexo = Sexo.FEMININO,
                tipoPessoa = TipoPessoa.FISICA
            )
        )
        val advogado = usuarioService.create(
            UsuarioCreateRequest(
                username = "adv.omissao.$suffix",
                senha = "senha-segura-123",
                pessoaId = pessoaAdvogado.id,
                perfil = PerfilUsuario.ADVOGADO,
                ufOab = "PR",
                numeroOab = "88.198",
                tratamento = TratamentoAdvogado.DRA
            )
        )

        val docx = given().contentType("application/json")
            .body(mapOf("reclamantesIds" to listOf(reclamante.id), "advogadosIds" to listOf(advogado.id)))
            .`when`().post("/rt/export-procuracao").then().statusCode(200).extract().asByteArray()

        XWPFDocument(ByteArrayInputStream(docx)).use { document ->
            val text = document.paragraphs.joinToString("\n") { it.text }
            assertTrue(text.contains("PESSOA COM DADOS OMITIDOS, brasileira, inscrito(a) no CPFMF sob n.º ${reclamante.cpf}, residente e domiciliado(a) em Cascavel – PR"))
            assertTrue(text.contains("ADVOGADA SEM QUALIFICACAO, advogada, inscrita na OAB/PR nº 88.198"))
            assertTrue(text.contains("propor Reclamação Trabalhista."))
            assertTrue(!text.contains("___"))
            assertTrue(!text.contains(", ,"))
            assertTrue(!text.contains("RG sob"))
            assertTrue(!text.contains("nascido(a) em"))
        }
        saveGeneratedDocument("procuracao-campos-omitidos.docx", docx)
    }

    private fun saveGeneratedDocument(filename: String, bytes: ByteArray) {
        val directory = Path.of("target", "documentos-teste")
        Files.createDirectories(directory)
        Files.write(directory.resolve(filename), bytes)
    }

    private fun validCpfFromSuffix(suffix: String, discriminator: Int): String {
        val base = (suffix.filter(Char::isDigit).takeLast(8) + discriminator).padStart(9, '1').takeLast(9)
        fun digit(value: String, weight: Int): Int {
            val sum = value.mapIndexed { index, char -> char.digitToInt() * (weight - index) }.sum()
            val remainder = (sum * 10) % 11
            return if (remainder == 10) 0 else remainder
        }
        val first = digit(base, 10)
        val second = digit(base + first, 11)
        return "$base$first$second"
    }

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
            "blocks":[{"title":"Formatação","content":"**negrito** *italico* __sublinhado__ __**ambosBU**__ ***ambosBI*** __***todos***__"}]
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
            assertTrue(runs.getValue("todos").isBold)
            assertTrue(runs.getValue("todos").isItalic)
            assertEquals(UnderlinePatterns.SINGLE, runs.getValue("todos").underline)
        }
    }

    @Test
    @TestSecurity(user = "advogado", roles = ["ADVOGADO"])
    fun `should export administrative contract image only in its block`() {
        val payload = """{
            "claimantName":"Maria Silva",
            "blocosSelecionados":["responsabilidade_subsidiaria_contrato_administrativo"],
            "dadosVariaveis":{
                "objetoContratoAdministrativo":"serviços de limpeza",
                "clausulaNumeroContrato":"cláusula 8ª do Contrato nº 123",
                "fornecimentoPrestadora":"mão de obra",
                "informacoesComplementaresContratoAdministrativo":"Complemento administrativo exportado."
            }
        }""".trimIndent()

        val docx = given()
            .multiPart("payload", payload, "text/plain")
            .multiPart("anexo_responsabilidade_subsidiaria_contrato_administrativo_0", "contrato.png", TEST_IMAGE_1, "image/png")
            .`when`()
            .post("/rt/export")
            .then()
            .statusCode(200)
            .extract()
            .asByteArray()

        assertDocxImages(docx, listOf(TEST_IMAGE_1), listOf("A parte autora, conquanto tenha sido contratada"))
        XWPFDocument(ByteArrayInputStream(docx)).use { document ->
            assertTrue(document.paragraphs.any { it.text.contains("Complemento administrativo exportado.") })
        }
    }

    @Test
    @TestSecurity(user = "advogado", roles = ["ADVOGADO"])
    fun `should export administrative contract image with frontend multipart convention`() {
        val payload = """{
            "claimantName":"Maria Silva",
            "blocks":[{
                "title":"Responsabilidade subsidiária. Contrato administrativo",
                "content":"A parte autora, conquanto tenha sido contratada pela 1ª ré"
            }]
        }""".trimIndent()

        val docx = given()
            .multiPart(
                "payload",
                payload,
                "text/plain"
            )
            .multiPart(
                "arquivos",
                "anexo_responsabilidade_subsidiaria_contrato_administrativo_0.png",
                TEST_IMAGE_1,
                "image/png"
            )
            .`when`()
            .post("/rt/export")
            .then()
            .statusCode(200)
            .extract()
            .asByteArray()

        assertDocxImages(docx, listOf(TEST_IMAGE_1), listOf("A parte autora, conquanto tenha sido contratada"))
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
    fun `should preview employment relationship recognition with interview fields`() {
        given()
            .contentType("application/json")
            .body(
                RtPreviewRequest(
                    blocosSelecionados = listOf("reconhecimento_vinculo_empregaticio"),
                    dadosVariaveis = mapOf(
                        "motivoNaoEventualidade" to "trabalhava habitualmente de segunda a sábado",
                        "motivoOnerosidade" to "recebia remuneração mensal",
                        "motivoSubordinacao" to "cumpria ordens do supervisor",
                        "dataInicioVinculo" to "2023-01-10",
                        "dataFimVinculo" to "2024-05-20"
                    )
                )
            )
            .`when`()
            .post("/rt/preview")
            .then()
            .statusCode(200)
            .body("blocos[0].id", equalTo("reconhecimento_vinculo_empregaticio"))
            .body("blocos[0].titulo", equalTo("Reconhecimento de vínculo empregatício"))
            .body("blocos[0].texto", containsString("• não eventualidade, trabalhava habitualmente de segunda a sábado;"))
            .body("blocos[0].texto", containsString("- a título de onerosidade, recebia remuneração mensal; e"))
            .body("blocos[0].texto", containsString("- havia subordinação, pois cumpria ordens do supervisor."))
            .body("blocos[0].texto", containsString("período de 10/01/2023 até 20/05/2024"))
    }

    @Test
    @TestSecurity(user = "advogado", roles = ["ADVOGADO"])
    fun `should use placeholders for blank employment relationship fields`() {
        given()
            .contentType("application/json")
            .body(RtPreviewRequest(blocosSelecionados = listOf("reconhecimento_vinculo_empregaticio")))
            .`when`()
            .post("/rt/preview")
            .then()
            .statusCode(200)
            .body("blocos[0].texto", containsString("• não eventualidade, ___;"))
            .body("blocos[0].texto", containsString("- a título de onerosidade, ___; e"))
            .body("blocos[0].texto", containsString("- havia subordinação, pois ___."))
            .body("blocos[0].texto", containsString("período de ___ até ___"))
    }

    @Test
    @TestSecurity(user = "advogado", roles = ["ADVOGADO"])
    fun `should preview unregistered ctps period with exclusive dates`() {
        given()
            .contentType("application/json")
            .body(
                RtPreviewRequest(
                    blocosSelecionados = listOf("periodo_sem_registro_ctps"),
                    dadosVariaveis = mapOf(
                        "dataAnotacaoCtps" to "2026-08-08",
                        "dataInicioPrestacaoServicos" to "2026-08-02"
                    )
                )
            )
            .`when`()
            .post("/rt/preview")
            .then()
            .statusCode(200)
            .body("blocos[0].id", equalTo("periodo_sem_registro_ctps"))
            .body(
                "blocos[0].titulo",
                equalTo("Período sem registro (de 02 de agosto de 2026 à 08 de agosto de 2026)")
            )
            .body("blocos[0].texto", containsString("apenas em 08/08/2026"))
            .body("blocos[0].texto", containsString("teve início antes, em 02/08/2026"))
            .body("blocos[0].texto", containsString("**mesmas funções e cumprindo os mesmos horários e dias de trabalho**"))
            .body("blocos[0].texto", containsString("**arts. 29 e 40 da CLT**"))
            .body("blocos[0].texto", containsString("**Súmula n.º 12 do TST**"))
            .body("blocos[0].texto", containsString("**REQUER-SE**"))
    }

    @Test
    @TestSecurity(user = "advogado", roles = ["ADVOGADO"])
    fun `should use placeholders for blank unregistered ctps dates`() {
        given()
            .contentType("application/json")
            .body(RtPreviewRequest(blocosSelecionados = listOf("periodo_sem_registro_ctps")))
            .`when`()
            .post("/rt/preview")
            .then()
            .statusCode(200)
            .body("blocos[0].titulo", equalTo("Período sem registro (de ___ à ___)"))
            .body("blocos[0].texto", containsString("anotado a CTPS da parte autora apenas em ___"))
            .body("blocos[0].texto", containsString("teve início antes, em ___"))
    }

    @Test
    @TestSecurity(user = "advogado", roles = ["ADVOGADO"])
    fun `should use placeholder for one missing date in unregistered ctps title`() {
        given()
            .contentType("application/json")
            .body(
                RtPreviewRequest(
                    blocosSelecionados = listOf("periodo_sem_registro_ctps"),
                    dadosVariaveis = mapOf("dataAnotacaoCtps" to "2022-06-15")
                )
            )
            .`when`()
            .post("/rt/preview")
            .then()
            .statusCode(200)
            .body(
                "blocos[0].titulo",
                equalTo("Período sem registro (de ___ à 15 de junho de 2022)")
            )
    }

    @Test
    @TestSecurity(user = "advogado", roles = ["ADVOGADO"])
    fun `should preview moral damages for missing ctps annotation with description`() {
        val descricao = "O trabalhador ficou impossibilitado de comprovar o vínculo e acessar benefícios previdenciários."

        given()
            .contentType("application/json")
            .body(
                RtPreviewRequest(
                    blocosSelecionados = listOf("dano_moral_ausencia_anotacao_ctps"),
                    dadosVariaveis = mapOf("descricaoDanoMoralCtps" to descricao)
                )
            )
            .`when`()
            .post("/rt/preview")
            .then()
            .statusCode(200)
            .body("blocos[0].id", equalTo("dano_moral_ausencia_anotacao_ctps"))
            .body("blocos[0].titulo", equalTo("Dano moral por ausência de anotação da CTPS"))
            .body("blocos[0].texto", containsString("**Tribunal Regional do Trabalho da 1ª Região**"))
            .body("blocos[0].texto", containsString("__**A não anotação da CTPS do empregado"))
            .body("blocos[0].texto", containsString("**arts. 186 e 927 do Código Civil, REQUER-SE**"))
            .body("blocos[0].texto", containsString("\n\n$descricao"))
    }

    @Test
    @TestSecurity(user = "advogado", roles = ["ADVOGADO"])
    fun `should omit blank moral damages description for missing ctps annotation`() {
        given()
            .contentType("application/json")
            .body(
                RtPreviewRequest(
                    blocosSelecionados = listOf("dano_moral_ausencia_anotacao_ctps"),
                    dadosVariaveis = mapOf("descricaoDanoMoralCtps" to "   ")
                )
            )
            .`when`()
            .post("/rt/preview")
            .then()
            .statusCode(200)
            .body(
                "blocos[0].texto",
                org.hamcrest.Matchers.endsWith("pagamento de indenização a título de danos morais.")
            )
    }

    @Test
    @TestSecurity(user = "advogado", roles = ["ADVOGADO"])
    fun `should preview conventional salary floor differences`() {
        given()
            .contentType("application/json")
            .body(
                RtPreviewRequest(
                    blocosSelecionados = listOf("diferencas_salariais_piso_convencional"),
                    dadosVariaveis = mapOf("cctReferencia" to "CCT 2025/2026")
                )
            )
            .`when`()
            .post("/rt/preview")
            .then()
            .statusCode(200)
            .body("blocos[0].id", equalTo("diferencas_salariais_piso_convencional"))
            .body("blocos[0].titulo", equalTo("Diferenças salariais. Piso convencional"))
            .body("blocos[0].texto", containsString("conforme CCT CCT 2025/2026:"))
            .body("blocos[0].texto", containsString("**art. 7º, V, da Constituição Federal**"))
            .body("blocos[0].texto", containsString("**REQUER-SE**"))
    }

    @Test
    @TestSecurity(user = "advogado", roles = ["ADVOGADO"])
    fun `should use placeholder for blank conventional salary floor reference`() {
        given()
            .contentType("application/json")
            .body(RtPreviewRequest(blocosSelecionados = listOf("diferencas_salariais_piso_convencional")))
            .`when`()
            .post("/rt/preview")
            .then()
            .statusCode(200)
            .body("blocos[0].texto", containsString("conforme CCT ___:"))
    }

    @Test
    @TestSecurity(user = "advogado", roles = ["ADVOGADO"])
    fun `should export multiple conventional salary floor images from multipart bytes`() {
        val payload = """{
            "claimantName":"Maria Silva",
            "blocosSelecionados":["diferencas_salariais_piso_convencional"],
            "dadosVariaveis":{"cctReferencia":"CCT 2025/2026"}
        }""".trimIndent()

        val docx = given()
            .multiPart("payload", payload, "text/plain")
            .multiPart("anexo_diferencas_salariais_piso_convencional_0", "holerite.png", TEST_IMAGE_1, "image/png")
            .multiPart("anexo_diferencas_salariais_piso_convencional_1", "cct.png", TEST_IMAGE_2, "image/png")
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
                "O salário pago à parte autora era inferior ao piso convencional",
                "O salário pago à parte autora era inferior ao piso convencional"
            )
        )
    }

    @Test
    @TestSecurity(user = "advogado", roles = ["ADVOGADO"])
    fun `should preview selected severance payment children in fixed order`() {
        given()
            .contentType("application/json")
            .body(
                RtPreviewRequest(
                    blocosSelecionados = listOf(
                        "verbas_rescisorias_multas_467_477",
                        "ausencia_pagamento_verbas_rescisorias",
                        "verbas_rescisorias_decimo_terceiro",
                        "verbas_rescisorias_aviso_previo"
                    ),
                    dadosVariaveis = mapOf(
                        "qtdDiasAviso" to "33 dias",
                        "detalheDecimoTerceiro" to "8/12 avos"
                    )
                )
            )
            .`when`()
            .post("/rt/preview")
            .then()
            .statusCode(200)
            .body("blocos.size()", equalTo(1))
            .body("blocos[0].id", equalTo("ausencia_pagamento_verbas_rescisorias"))
            .body("blocos[0].titulo", equalTo("Ausência de pagamento das verbas rescisórias"))
            .body(
                "blocos[0].texto",
                equalTo(
                    "A parte ré não pagou as verbas rescisórias devidas, o que impõe a sua condenação, conforme exposto a seguir:\n\n" +
                        "Diante do não pagamento do aviso prévio à parte autora, com fundamento no art. 487 da CLT, **REQUER-SE** a condenação da ré ao pagamento do aviso prévio (33 dias).\n\n" +
                        "Nos termos do art. 7º, VIII, da Constituição Federal, **REQUER-SE** a condenação da ré ao pagamento do 13º salário proporcional (8/12 avos).\n\n" +
                        "Diante do não pagamento das verbas rescisórias à parte autora, **REQUER-SE** a condenação da ré ao pagamento das multas do **art. 467 da CLT** e do **art. 477, § 8º, da CLT**."
                )
            )
    }

    @Test
    @TestSecurity(user = "advogado", roles = ["ADVOGADO"])
    fun `should preview only introduction when no severance payment child is selected`() {
        given()
            .contentType("application/json")
            .body(RtPreviewRequest(blocosSelecionados = listOf("ausencia_pagamento_verbas_rescisorias")))
            .`when`()
            .post("/rt/preview")
            .then()
            .statusCode(200)
            .body(
                "blocos[0].texto",
                equalTo("A parte ré não pagou as verbas rescisórias devidas, o que impõe a sua condenação, conforme exposto a seguir:")
            )
    }

    @Test
    @TestSecurity(user = "advogado", roles = ["ADVOGADO"])
    fun `should use placeholder for selected severance payment child with blank field`() {
        given()
            .contentType("application/json")
            .body(
                RtPreviewRequest(
                    blocosSelecionados = listOf(
                        "ausencia_pagamento_verbas_rescisorias",
                        "verbas_rescisorias_ferias"
                    )
                )
            )
            .`when`()
            .post("/rt/preview")
            .then()
            .statusCode(200)
            .body("blocos[0].texto", containsString("férias proporcionais + 1/3 (___)."))
    }

    @Test
    @TestSecurity(user = "advogado", roles = ["ADVOGADO"])
    fun `should preview all severance payment children`() {
        given()
            .contentType("application/json")
            .body(
                RtPreviewRequest(
                    blocosSelecionados = listOf(
                        "ausencia_pagamento_verbas_rescisorias",
                        "verbas_rescisorias_aviso_previo",
                        "verbas_rescisorias_ferias",
                        "verbas_rescisorias_decimo_terceiro",
                        "verbas_rescisorias_multa_fgts",
                        "verbas_rescisorias_multas_467_477"
                    )
                )
            )
            .`when`()
            .post("/rt/preview")
            .then()
            .statusCode(200)
            .body("blocos.size()", equalTo(1))
            .body("blocos[0].texto", containsString("aviso prévio (___)"))
            .body("blocos[0].texto", containsString("férias proporcionais + 1/3 (___)"))
            .body("blocos[0].texto", containsString("13º salário proporcional (___)"))
            .body("blocos[0].texto", containsString("multa de 40% do FGTS"))
            .body("blocos[0].texto", containsString("**art. 477, § 8º, da CLT**"))
    }

    @Test
    @TestSecurity(user = "advogado", roles = ["ADVOGADO"])
    fun `should preview moral damages for unpaid severance with all citations and formatting`() {
        given()
            .contentType("application/json")
            .body(
                RtPreviewRequest(
                    blocosSelecionados = listOf("dano_moral_ausencia_pagamento_verbas_rescisorias")
                )
            )
            .`when`()
            .post("/rt/preview")
            .then()
            .statusCode(200)
            .body("blocos.size()", equalTo(1))
            .body("blocos[0].id", equalTo("dano_moral_ausencia_pagamento_verbas_rescisorias"))
            .body(
                "blocos[0].titulo",
                equalTo("Dano moral por ausência de pagamento das verbas rescisórias")
            )
            .body("blocos[0].texto", containsString("**TRT da 4ª Região**"))
            .body("blocos[0].texto", containsString("__**A ausência de pagamento das verbas rescisórias impõe"))
            .body("blocos[0].texto", containsString("__**DANOS MORAIS, VERBAS RESCISÓRIAS."))
            .body("blocos[0].texto", containsString("**TRT da 18ª Região**"))
            .body("blocos[0].texto", containsString("__**acarretando o dever de indenizar.**__"))
            .body("blocos[0].texto", containsString("**TRT da 3ª Região**"))
            .body("blocos[0].texto", containsString("__**Nesse sentido, o dano moral se apresenta, in re ipsa."))
            .body("blocos[0].anexos.size()", equalTo(0))
    }

    @Test
    @TestSecurity(user = "advogado", roles = ["ADVOGADO"])
    fun `should preview resignation conversion using first defendant and serious breach`() {
        val suffix = System.nanoTime().toString()
        val first = pessoaService.create(pessoaRequest("primeira-conversao-$suffix", null, null, null))
        val second = pessoaService.create(pessoaRequest("segunda-conversao-$suffix", null, null, null))

        given()
            .contentType("application/json")
            .body(
                RtPreviewRequest(
                    reclamadasIds = listOf(first.id!!, second.id!!),
                    blocosSelecionados = listOf("conversao_pedido_demissao_rescisao_indireta"),
                    dadosVariaveis = mapOf(
                        "descricaoFaltaGrave" to "deixou de recolher o FGTS durante todo o contrato"
                    )
                )
            )
            .`when`()
            .post("/rt/preview")
            .then()
            .statusCode(200)
            .body("blocos[0].id", equalTo("conversao_pedido_demissao_rescisao_indireta"))
            .body(
                "blocos[0].titulo",
                equalTo("Conversão do pedido de demissão em rescisão indireta")
            )
            .body(
                "blocos[0].texto",
                containsString(
                    "A parte ré Reclamante primeira-conversao-$suffix " +
                        "(deixou de recolher o FGTS durante todo o contrato)."
                )
            )
            .body("blocos[0].texto", not(containsString("Reclamante segunda-conversao-$suffix")))
            .body("blocos[0].texto", containsString("**rescisão indireta**"))
            .body("blocos[0].texto", containsString("**art. 483, alínea d, da CLT**"))
            .body("blocos[0].texto", containsString("__**Por fim, é firme, na jurisprudência"))
            .body("blocos[0].texto", containsString("__**o referido dispositivo não estabelece"))
            .body("blocos[0].texto", containsString("**REQUER-SE** a conversão"))
    }

    @Test
    @TestSecurity(user = "advogado", roles = ["ADVOGADO"])
    fun `should use placeholders in resignation conversion without defendant or serious breach`() {
        given()
            .contentType("application/json")
            .body(
                RtPreviewRequest(
                    blocosSelecionados = listOf("conversao_pedido_demissao_rescisao_indireta")
                )
            )
            .`when`()
            .post("/rt/preview")
            .then()
            .statusCode(200)
            .body("blocos[0].texto", containsString("A parte ré ___ (___)."))
    }

    @Test
    @TestSecurity(user = "advogado", roles = ["ADVOGADO"])
    fun `should preview just cause reversal to indirect termination`() {
        val motivo = "deixou de recolher corretamente o FGTS e atrasou o pagamento dos salários"

        given()
            .contentType("application/json")
            .body(
                RtPreviewRequest(
                    blocosSelecionados = listOf("reversao_justa_causa_rescisao_indireta"),
                    dadosVariaveis = mapOf("motivoJustaCausa" to motivo)
                )
            )
            .`when`()
            .post("/rt/preview")
            .then()
            .statusCode(200)
            .body("blocos[0].id", equalTo("reversao_justa_causa_rescisao_indireta"))
            .body("blocos[0].titulo", equalTo("Reversão da justa causa para rescisão indireta"))
            .body("blocos[0].texto", containsString("A ré, de modo habitual, $motivo."))
            .body("blocos[0].texto", containsString("**rescisão indireta**"))
            .body("blocos[0].texto", containsString("**art. 483, alínea c, da CLT**"))
            .body("blocos[0].texto", containsString("__reconhecimento de rescisão indireta__"))
            .body("blocos[0].texto", containsString("__Sucessivamente__"))
            .body("blocos[0].texto", containsString("__conversão para dispensa sem justa causa__"))
            .body("blocos[0].texto", containsString("**REQUER-SE**"))
            .body("blocos[0].anexos.size()", equalTo(0))
    }

    @Test
    @TestSecurity(user = "advogado", roles = ["ADVOGADO"])
    fun `should use placeholder for blank just cause reason`() {
        given()
            .contentType("application/json")
            .body(RtPreviewRequest(blocosSelecionados = listOf("reversao_justa_causa_rescisao_indireta")))
            .`when`()
            .post("/rt/preview")
            .then()
            .statusCode(200)
            .body("blocos[0].texto", containsString("A ré, de modo habitual, ___."))
    }

    @Test
    @TestSecurity(user = "advogado", roles = ["ADVOGADO"])
    fun `should preview fixed just cause reversal to dismissal without cause only when selected`() {
        val expectedText =
            "Pelo exposto, **REQUER-SE** a reversão da justa causa para dispensa sem justa causa, com a " +
                "consequente condenação da ré ao pagamento das verbas devidas nesse tipo de dispensa, quais " +
                "sejam aviso-prévio proporcional ao tempo de serviço, férias integrais e proporcionais + 1/3, " +
                "décimo terceiro salário proporcional e FGTS + multa de 40%. Consequentemente, **REQUER-SE** a " +
                "liberação das guias para saque de FGTS e seguro-desemprego, sob pena de multa diária, no importe " +
                "de R$ 1.000,00, ou outro valor a ser arbitrado por este Juízo, sem prejuízo da emissão de alvará judicial."

        given()
            .contentType("application/json")
            .body(
                RtPreviewRequest(
                    blocosSelecionados = listOf("reversao_justa_causa_dispensa_sem_justa_causa"),
                    dadosVariaveis = mapOf("campoIgnorado" to "não deve alterar o texto")
                )
            )
            .`when`()
            .post("/rt/preview")
            .then()
            .statusCode(200)
            .body("blocos.size()", equalTo(1))
            .body("blocos[0].id", equalTo("reversao_justa_causa_dispensa_sem_justa_causa"))
            .body("blocos[0].titulo", equalTo("Reversão da justa causa para dispensa sem justa causa"))
            .body("blocos[0].texto", equalTo(expectedText))
            .body("blocos[0].anexos.size()", equalTo(0))

        given()
            .contentType("application/json")
            .body(RtPreviewRequest(blocosSelecionados = listOf("dados_reclamante")))
            .`when`()
            .post("/rt/preview")
            .then()
            .statusCode(200)
            .body("blocos.size()", equalTo(1))
            .body("blocos[0].id", equalTo("dados_reclamante"))
    }

    @Test
    @TestSecurity(user = "advogado", roles = ["ADVOGADO"])
    fun `should preview article 477 fine immediately after just cause reversal`() {
        val expectedText =
            "O Tribunal Superior do Trabalho firmou tese vinculante a respeito de ser devida a multa do art. " +
                "477 da CLT quando é revertida em Juízo a dispensa por justa causa:\n\n" +
                "Pelo exposto, com fundamento no Tema 71 do TST, **REQUER-SE** a condenação da ré ao pagamento " +
                "da multa do **art. 477, § 8º, da CLT**."

        given()
            .contentType("application/json")
            .body(
                RtPreviewRequest(
                    blocosSelecionados = listOf(
                        "multa_art_477_clt",
                        "reversao_justa_causa_dispensa_sem_justa_causa"
                    ),
                    dadosVariaveis = mapOf("campoIgnorado" to "não altera texto fixo")
                )
            )
            .`when`()
            .post("/rt/preview")
            .then()
            .statusCode(200)
            .body("blocos.size()", equalTo(2))
            .body("blocos[0].id", equalTo("reversao_justa_causa_dispensa_sem_justa_causa"))
            .body("blocos[1].id", equalTo("multa_art_477_clt"))
            .body("blocos[1].titulo", equalTo("Multa do art. 477, § 8º, da CLT"))
            .body("blocos[1].texto", equalTo(expectedText))
            .body("blocos[1].anexos.size()", equalTo(0))
            .body("blocos[1].imagensFixas.size()", equalTo(1))
            .body("blocos[1].imagensFixas[0].url", equalTo("/rt/assets/multa-art-477"))
            .body("blocos[1].imagensFixas[0].contentType", equalTo("image/png"))
            .body("blocos[1].imagensFixas[0].nomeOriginal", equalTo("multa_art_477.png"))
            .body("blocos[1].imagensFixas[0].afterParagraph", equalTo(1))

        given()
            .contentType("application/json")
            .body(RtPreviewRequest(blocosSelecionados = listOf("dados_reclamante")))
            .`when`()
            .post("/rt/preview")
            .then()
            .statusCode(200)
            .body("blocos.size()", equalTo(1))
            .body("blocos[0].id", equalTo("dados_reclamante"))
    }

    @Test
    @TestSecurity(user = "advogado", roles = ["ADVOGADO"])
    fun `should preview indirect termination injunction with naturally joined site list`() {
        val payload = """{
            "blocosSelecionados":["rescisao_indireta_tutela_antecipada_verbas_incontroversas"],
            "dadosVariaveis":{
                "sitesEncerramentoAtividades":["site1.com","site2.com.br","site3.com"]
            }
        }""".trimIndent()

        given()
            .contentType("application/json")
            .body(payload)
            .`when`()
            .post("/rt/preview")
            .then()
            .statusCode(200)
            .body("blocos.size()", equalTo(1))
            .body("blocos[0].id", equalTo("rescisao_indireta_tutela_antecipada_verbas_incontroversas"))
            .body(
                "blocos[0].titulo",
                equalTo(
                    "Rescisão indireta. Tutela antecipada. Verbas incontroversas " +
                        "(art. 294, parágrafo único, do CPC)"
                )
            )
            .body("blocos[0].texto", containsString("sites: site1.com, site2.com.br e site3.com"))
            .body(
                "blocos[0].texto",
                containsString("**art. 294, parágrafo único, do Código de Processo Civil**")
            )
            .body("blocos[0].texto", containsString("**REQUER** seja determinado à ré"))
            .body("blocos[0].texto", containsString("**art. 300 do CPC**"))
            .body(
                "blocos[0].texto",
                containsString("__até o limite do valor estimado atribuído a este pedido.__")
            )
            .body("blocos[0].texto", containsString("__No mérito__, **REQUER-SE**"))
            .body("blocos[0].anexos.size()", equalTo(0))
            .body("blocos[0].imagensFixas.size()", equalTo(0))

        given()
            .contentType("application/json")
            .body(
                """{
                    "blocosSelecionados":["rescisao_indireta_tutela_antecipada_verbas_incontroversas"],
                    "dadosVariaveis":{"sitesEncerramentoAtividades":[]}
                }""".trimIndent()
            )
            .`when`()
            .post("/rt/preview")
            .then()
            .statusCode(200)
            .body("blocos[0].texto", containsString("sites: ___"))
    }

    @Test
    @TestSecurity(user = "advogado", roles = ["ADVOGADO"])
    fun `should preview and export fixed precautionary emergency injunction`() {
        val expectedText =
            "Conforme indicado no tópico anterior, a ré está em vias de encerramento de suas atividades, " +
                "pelo que, para garantir a futura execução, **REQUER** seja concedida tutela de urgência, " +
                "nos termos do **art. 300 do CPC**, para que seja efetuada a penhora eletrônica de ativos " +
                "financeiros em contas bancárias de titularidade da ré por meio do sistema SISBAJUD, com " +
                "repetição programada (\"Teimosinha\"), bem como seja determinada a indisponibilidade de bens " +
                "imóveis por meio do convênio CNIB e o bloqueio de circulação dos veículos por meio do RENAJUD, " +
                "__até o limite do valor estimado atribuído a esta ação judicial__.\n\n" +
                "__No mérito__, **REQUER-SE** a confirmação do pedido de tutela de urgência, com o seu " +
                "integral acolhimento."

        given()
            .contentType("application/json")
            .body(RtPreviewRequest(blocosSelecionados = listOf("tutela_urgencia_natureza_cautelar")))
            .`when`()
            .post("/rt/preview")
            .then()
            .statusCode(200)
            .body("blocos.size()", equalTo(1))
            .body("blocos[0].id", equalTo("tutela_urgencia_natureza_cautelar"))
            .body("blocos[0].titulo", equalTo("Tutela de urgência de natureza cautelar. (art. 300 do CPC)"))
            .body("blocos[0].texto", equalTo(expectedText))
            .body("blocos[0].anexos.size()", equalTo(0))
            .body("blocos[0].imagensFixas.size()", equalTo(0))

        given()
            .contentType("application/json")
            .body(RtPreviewRequest(blocosSelecionados = listOf("dados_reclamante")))
            .`when`()
            .post("/rt/preview")
            .then()
            .statusCode(200)
            .body("blocos.size()", equalTo(1))
            .body("blocos[0].id", equalTo("dados_reclamante"))

        val payload = """{
            "claimantName":"Maria Silva",
            "blocosSelecionados":["tutela_urgencia_natureza_cautelar"]
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
            val first = document.paragraphs.first { it.text.startsWith("Conforme indicado no tópico anterior") }
            assertTrue(first.runs.first { it.text() == "REQUER" }.isBold)
            assertTrue(first.runs.first { it.text() == "art. 300 do CPC" }.isBold)
            assertEquals(
                UnderlinePatterns.SINGLE,
                first.runs.first { it.text().startsWith("até o limite do valor estimado") }.underline
            )
            val second = document.paragraphs.first { it.text.startsWith("No mérito") }
            assertEquals(UnderlinePatterns.SINGLE, second.runs.first { it.text() == "No mérito" }.underline)
            assertTrue(second.runs.first { it.text() == "REQUER-SE" }.isBold)
        }
    }

    @Test
    @TestSecurity(user = "advogado", roles = ["ADVOGADO"])
    fun `should serve and embed fixed article 477 image after first paragraph`() {
        val expectedImage = Files.readAllBytes(Path.of("src/main/resources/assets/multa_art_477.png"))
        val servedImage = given()
            .`when`()
            .get("/rt/assets/multa-art-477")
            .then()
            .statusCode(200)
            .header("Content-Type", startsWith("image/png"))
            .extract()
            .asByteArray()
        assertTrue(expectedImage.contentEquals(servedImage))

        val payload = """{
            "claimantName":"Maria Silva",
            "blocosSelecionados":["multa_art_477_clt"]
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
            val thesisIndex = document.paragraphs.indexOfFirst {
                it.text.startsWith("O Tribunal Superior do Trabalho firmou tese vinculante")
            }
            assertTrue(thesisIndex >= 0)
            val imageParagraph = document.paragraphs[thesisIndex + 1]
            assertEquals(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER, imageParagraph.alignment)
            val picture = imageParagraph.runs.single().embeddedPictures.single()
            assertEquals(org.apache.poi.util.Units.toEMU(450.0).toLong(), picture.ctPicture.spPr.xfrm.ext.cx)
            assertEquals(
                730.0 / 495.0,
                picture.ctPicture.spPr.xfrm.ext.cx.toDouble() / picture.ctPicture.spPr.xfrm.ext.cy,
                0.001
            )
            assertTrue(document.paragraphs[thesisIndex + 2].text.startsWith("Pelo exposto, com fundamento no Tema 71"))
            assertTrue(document.allPictures.any { expectedImage.contentEquals(it.data) })
        }
    }

    @Test
    @TestSecurity(user = "advogado", roles = ["ADVOGADO"])
    fun `should preview and persist indirect termination request justification`() {
        val suffix = System.nanoTime().toString()
        val justificativa = "os reiterados atrasos salariais e a ausência de depósitos do FGTS"
        val expectedText =
            "Considerando $justificativa, fica evidente o descumprimento de obrigações " +
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

        given()
            .contentType("application/json")
            .body(
                RtPreviewRequest(
                    blocosSelecionados = listOf("pedido_rescisao_indireta"),
                    dadosVariaveis = mapOf("justificativaRescisaoIndireta" to justificativa)
                )
            )
            .`when`()
            .post("/rt/preview")
            .then()
            .statusCode(200)
            .body("blocos.size()", equalTo(1))
            .body("blocos[0].id", equalTo("pedido_rescisao_indireta"))
            .body("blocos[0].titulo", equalTo("Pedido de rescisão indireta"))
            .body("blocos[0].texto", equalTo(expectedText))
            .body("blocos[0].anexos.size()", equalTo(0))

        given()
            .contentType("application/json")
            .body(RtPreviewRequest(blocosSelecionados = listOf("pedido_rescisao_indireta")))
            .`when`()
            .post("/rt/preview")
            .then()
            .statusCode(200)
            .body("blocos[0].texto", startsWith("Considerando ___, fica evidente"))

        val reclamante = pessoaService.create(
            pessoaRequest(suffix, validCpfFromSuffix(suffix, 3), null, null)
        )
        val reclamada = pessoaService.create(
            PessoaRequestDTO(
                nome = "Reclamada $suffix",
                cpf = validCpfFromSuffix(suffix, 4),
                email = "reclamada.$suffix@example.com",
                tipoPessoa = TipoPessoa.FISICA
            )
        )
        val pessoaAdvogado = pessoaService.create(
            PessoaRequestDTO(
                nome = "Advogado rescisão $suffix",
                cpf = validCpfFromSuffix(suffix, 5),
                email = "advogado.rescisao.$suffix@example.com",
                tipoPessoa = TipoPessoa.FISICA
            )
        )
        val advogado = usuarioService.create(
            UsuarioCreateRequest(
                username = "adv.rescisao.$suffix",
                senha = "senha-segura-123",
                pessoaId = pessoaAdvogado.id,
                perfil = PerfilUsuario.ADVOGADO,
                ufOab = "PR",
                numeroOab = "99.999"
            )
        )
        val processo = processoService.create(
            ProcessoCreateRequest(
                numeroProcesso = "RT-$suffix",
                descricao = "Teste de persistência da justificativa",
                reclamantesIds = listOf(reclamante.id),
                advogadosIds = listOf(advogado.id),
                reclamadasIds = listOf(reclamada.id),
                dataAbertura = LocalDate.now(),
                estrategiaProcessual = EstrategiaProcessualRequest(),
                status = StatusProcesso.ABERTO,
                blocosSelecionados = listOf("pedido_rescisao_indireta"),
                dadosVariaveis = mapOf(
                    "pedido_rescisao_indireta" to mapOf(
                        "justificativaRescisaoIndireta" to justificativa
                    )
                )
            )
        )

        assertEquals(listOf("pedido_rescisao_indireta"), processo.blocosSelecionados)
        assertEquals(
            justificativa,
            processoService.getById(processo.id)
                .dadosVariaveis["pedido_rescisao_indireta"]?.get("justificativaRescisaoIndireta")
        )

        given()
            .contentType("application/json")
            .body(
                RtPreviewRequest(
                    processoId = processo.id,
                    blocosSelecionados = listOf("pedido_rescisao_indireta")
                )
            )
            .`when`()
            .post("/rt/preview")
            .then()
            .statusCode(200)
            .body("blocos[0].texto", equalTo(expectedText))
    }

    @Test
    @TestSecurity(user = "advogado", roles = ["ADVOGADO"])
    fun `should preview retention of work card moral damages with fixed image in canonical order`() {
        val expectedText =
            "A CTPS da parte autora ficou retida pela ré, que formalizou o vínculo de emprego, assinando a " +
                "CTPS, somente em 10/08/2026, violando o art. 29 da CLT.\n\n" +
                "O Tribunal Superior do Trabalho firmou tese vinculante a respeito de ser devida indenização por " +
                "danos morais, por presunção, quando a CTPS é retida injustificadamente pelo empregador além do " +
                "tempo previsto na CLT:\n\n" +
                "Pelo exposto, com fundamento no art. 29 da CLT e no Tema 192 do Tribunal Superior do Trabalho, " +
                "**REQUER-SE** a condenação da ré ao pagamento de danos morais."

        given()
            .contentType("application/json")
            .body(
                RtPreviewRequest(
                    blocosSelecionados = listOf(
                        "retencao_ctps_dano_moral",
                        "dano_moral_ausencia_anotacao_ctps"
                    ),
                    dadosVariaveis = mapOf("dataAssinaturaCarteira" to "2026-08-10")
                )
            )
            .`when`()
            .post("/rt/preview")
            .then()
            .statusCode(200)
            .body("blocos.size()", equalTo(2))
            .body("blocos[0].id", equalTo("dano_moral_ausencia_anotacao_ctps"))
            .body("blocos[1].id", equalTo("retencao_ctps_dano_moral"))
            .body("blocos[1].titulo", equalTo("Retenção da CTPS. Dano moral"))
            .body("blocos[1].texto", equalTo(expectedText))
            .body("blocos[1].anexos.size()", equalTo(0))
            .body("blocos[1].imagensFixas.size()", equalTo(1))
            .body("blocos[1].imagensFixas[0].url", equalTo("/rt/assets/retencao-ctps-dano-moral"))
            .body("blocos[1].imagensFixas[0].contentType", equalTo("image/png"))
            .body("blocos[1].imagensFixas[0].nomeOriginal", equalTo("Retenção_da _CTPS_ Dano_moral.png"))
            .body("blocos[1].imagensFixas[0].afterParagraph", equalTo(2))

        given()
            .contentType("application/json")
            .body(RtPreviewRequest(blocosSelecionados = listOf("retencao_ctps_dano_moral")))
            .`when`()
            .post("/rt/preview")
            .then()
            .statusCode(200)
            .body("blocos[0].texto", startsWith("A CTPS da parte autora ficou retida"))
            .body("blocos[0].texto", containsString("somente em ___"))
    }

    @Test
    @TestSecurity(user = "advogado", roles = ["ADVOGADO"])
    fun `should serve and embed fixed retention of work card image after second paragraph`() {
        val expectedImage = Files.readAllBytes(
            Path.of("src/main/resources/assets/Retenção_da _CTPS_ Dano_moral.png")
        )
        val servedImage = given()
            .`when`()
            .get("/rt/assets/retencao-ctps-dano-moral")
            .then()
            .statusCode(200)
            .header("Content-Type", startsWith("image/png"))
            .extract()
            .asByteArray()
        assertTrue(expectedImage.contentEquals(servedImage))

        val payload = """{
            "claimantName":"Maria Silva",
            "blocosSelecionados":["retencao_ctps_dano_moral"],
            "dadosVariaveis":{"dataAssinaturaCarteira":"2026-08-10"}
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
            val bodyParagraphs = document.paragraphs
            val thesisIndex = bodyParagraphs.indexOfFirst {
                it.text.startsWith("O Tribunal Superior do Trabalho firmou tese vinculante")
            }
            assertTrue(thesisIndex >= 0)
            val imageParagraph = bodyParagraphs[thesisIndex + 1]
            assertEquals(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER, imageParagraph.alignment)
            val picture = imageParagraph.runs.single().embeddedPictures.single()
            assertEquals(org.apache.poi.util.Units.toEMU(450.0).toLong(), picture.ctPicture.spPr.xfrm.ext.cx)
            assertEquals(
                695.0 / 416.0,
                picture.ctPicture.spPr.xfrm.ext.cx.toDouble() / picture.ctPicture.spPr.xfrm.ext.cy,
                0.001
            )
            assertTrue(bodyParagraphs[thesisIndex + 2].text.startsWith("Pelo exposto, com fundamento no art. 29"))
            assertTrue(document.allPictures.any { expectedImage.contentEquals(it.data) })
        }
    }

    @Test
    @TestSecurity(user = "advogado", roles = ["ADVOGADO"])
    fun `should inject fixed retention image in legacy blocks export`() {
        val expectedImage = Files.readAllBytes(
            Path.of("src/main/resources/assets/Retenção_da _CTPS_ Dano_moral.png")
        )
        val payload = """{
            "claimantName":"Maria Silva",
            "blocks":[{
                "title":"Retenção da CTPS. Dano moral",
                "content":"Primeiro paragrafo.\n\nSegundo paragrafo antes da imagem.\n\nTerceiro paragrafo depois da imagem."
            }]
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
            val secondParagraphIndex = document.paragraphs.indexOfFirst {
                it.text == "Segundo paragrafo antes da imagem."
            }
            assertTrue(secondParagraphIndex >= 0, "Parágrafos exportados: ${document.paragraphs.map { it.text }}")
            assertTrue(document.paragraphs[secondParagraphIndex + 1].runs.single().embeddedPictures.isNotEmpty())
            assertEquals("Terceiro paragrafo depois da imagem.", document.paragraphs[secondParagraphIndex + 2].text)
            assertTrue(document.allPictures.any { expectedImage.contentEquals(it.data) })
        }
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

    @Test
    @TestSecurity(user = "advogado", roles = ["ADVOGADO"])
    fun `should preview administrative contract liability variables and formatting`() {
        given()
            .contentType("application/json")
            .body(
                RtPreviewRequest(
                    blocosSelecionados = listOf("responsabilidade_subsidiaria_contrato_administrativo"),
                    dadosVariaveis = mapOf(
                        "objetoContratoAdministrativo" to "serviços de limpeza",
                        "clausulaNumeroContrato" to "cláusula 8ª do Contrato nº 123",
                        "fornecimentoPrestadora" to "mão de obra e equipamentos",
                        "informacoesComplementaresContratoAdministrativo" to "Informação adicional administrativa."
                    )
                )
            )
            .`when`()
            .post("/rt/preview")
            .then()
            .statusCode(200)
            .body("blocos[0].id", equalTo("responsabilidade_subsidiaria_contrato_administrativo"))
            .body("blocos[0].titulo", equalTo("Responsabilidade subsidiária. Contrato administrativo"))
            .body("blocos[0].texto", containsString("*“serviços de limpeza”*"))
            .body("blocos[0].texto", containsString("conforme cláusula 8ª do Contrato nº 123"))
            .body("blocos[0].texto", containsString("fornece mão de obra e equipamentos"))
            .body("blocos[0].texto", containsString("__***responderão pelos danos***__"))
            .body("blocos[0].texto", containsString("Informação adicional administrativa."))
    }

    @Test
    @TestSecurity(user = "advogado", roles = ["ADVOGADO"])
    fun `should keep complementary information isolated between contract blocks`() {
        given()
            .contentType("application/json")
            .body(
                RtPreviewRequest(
                    blocosSelecionados = listOf(
                        "contrato_aspectos_gerais",
                        "responsabilidade_subsidiaria_contrato_administrativo"
                    ),
                    dadosVariaveis = mapOf(
                        "informacoesComplementares" to "Complemento exclusivo do contrato geral.",
                        "informacoesComplementaresContratoAdministrativo" to
                            "Complemento exclusivo do contrato administrativo."
                    )
                )
            )
            .`when`()
            .post("/rt/preview")
            .then()
            .statusCode(200)
            .body("blocos[0].texto", containsString("Complemento exclusivo do contrato geral."))
            .body("blocos[0].texto", not(containsString("Complemento exclusivo do contrato administrativo.")))
            .body("blocos[1].texto", containsString("Complemento exclusivo do contrato administrativo."))
            .body("blocos[1].texto", not(containsString("Complemento exclusivo do contrato geral.")))
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
