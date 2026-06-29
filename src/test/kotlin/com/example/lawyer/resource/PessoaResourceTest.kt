package com.example.lawyer.resource

import com.example.lawyer.domain.enums.TipoPessoa
import com.example.lawyer.dto.request.PessoaRequestDTO
import com.example.lawyer.service.PessoaService
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.security.TestSecurity
import io.restassured.RestAssured.given
import jakarta.inject.Inject
import org.hamcrest.CoreMatchers.equalTo
import org.junit.jupiter.api.Test

@QuarkusTest
class PessoaResourceTest {
    @Inject
    lateinit var pessoaService: PessoaService

    @Test
    @TestSecurity(user = "assistente", roles = ["ASSISTENTE"])
    fun `should create pessoa`() {
        given()
            .contentType("application/json")
            .body(
                PessoaRequestDTO(
                    nome = "Cliente API",
                    cpf = "11144477735",
                    cnpj = null,
                    email = "cliente.api@example.com",
                    telefone = "11911111111",
                    tipoPessoa = TipoPessoa.FISICA,
                    dataNascimento = null,
                    endereco = null,
                    ativo = true
                )
            )
            .`when`()
            .post("/pessoas")
            .then()
            .statusCode(201)
            .body("nome", equalTo("Cliente API"))
            .body("cpf", equalTo("11144477735"))
    }

    @Test
    @TestSecurity(user = "advogado", roles = ["ADVOGADO"])
    fun `should create pessoa as advogado`() {
        given()
            .contentType("application/json")
            .body(
                PessoaRequestDTO(
                    nome = "Cliente Advogado",
                    cpf = "98765432100",
                    cnpj = null,
                    email = "cliente.advogado@example.com",
                    telefone = "11922222222",
                    tipoPessoa = TipoPessoa.FISICA,
                    dataNascimento = null,
                    endereco = null,
                    ativo = true
                )
            )
            .`when`()
            .post("/pessoas")
            .then()
            .statusCode(201)
            .body("nome", equalTo("Cliente Advogado"))
            .body("cpf", equalTo("98765432100"))
    }

    @Test
    @TestSecurity(user = "assistente", roles = ["ASSISTENTE"])
    fun `should list active pessoas with pagination`() {
        pessoaService.create(
            PessoaRequestDTO(
                nome = "Ana Listagem",
                cpf = "12345678909",
                cnpj = null,
                email = "ana.listagem@example.com",
                telefone = null,
                tipoPessoa = TipoPessoa.FISICA,
                dataNascimento = null,
                endereco = null,
                ativo = true
            )
        )

        given()
            .queryParam("page", 0)
            .queryParam("size", 10)
            .queryParam("nome", "Ana")
            .`when`()
            .get("/pessoas")
            .then()
            .statusCode(200)
            .body("page", equalTo(0))
            .body("size", equalTo(10))
            .body("items.size()", equalTo(1))
            .body("items[0].nome", equalTo("Ana Listagem"))
    }
}
