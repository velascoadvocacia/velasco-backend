package com.example.lawyer.resource

import com.example.lawyer.domain.enums.TipoPessoa
import com.example.lawyer.dto.request.PessoaRequestDTO
import com.example.lawyer.dto.request.RtExportBlockRequest
import com.example.lawyer.dto.request.RtExportRequest
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
import java.time.LocalDate

@QuarkusTest
class RtExportResourceTest {
    @Inject
    lateinit var pessoaService: PessoaService

    @Test
    @TestSecurity(user = "advogado", roles = ["ADVOGADO"])
    fun `should export rt docx`() {
        given()
            .contentType("application/json")
            .body(
                RtExportRequest(
                    claimantName = "Maria Silva",
                    blocks = listOf(
                        RtExportBlockRequest(
                            title = "Dos Fatos",
                            content = "Conteúdo da reclamatória trabalhista."
                        )
                    )
                )
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
}
