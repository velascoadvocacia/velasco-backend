package com.example.lawyer.resource

import com.example.lawyer.dto.request.RtExportBlockRequest
import com.example.lawyer.dto.request.RtExportRequest
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.security.TestSecurity
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.startsWith
import org.junit.jupiter.api.Test

@QuarkusTest
class RtExportResourceTest {
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
}
