package com.example.lawyer.resource

import com.example.lawyer.domain.enums.EstadoCivil
import com.example.lawyer.domain.enums.TipoPessoa
import com.example.lawyer.dto.request.PessoaRequestDTO
import com.example.lawyer.service.PessoaService
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.security.TestSecurity
import io.restassured.RestAssured.given
import jakarta.inject.Inject
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.nullValue
import org.junit.jupiter.api.Test
import java.time.LocalDate

@QuarkusTest
class PessoaResourceTest {
    @Inject
    lateinit var pessoaService: PessoaService

    @Test
    @TestSecurity(user = "assistente", roles = ["ASSISTENTE"])
    fun `should explain invalid address fields in message consumed by frontend`() {
        given()
            .contentType("application/json")
            .body(mapOf("nome" to "Teste", "tipoPessoa" to "FISICA",
                "endereco" to mapOf("estado" to "Parana", "cep" to "123")))
            .`when`().post("/pessoas")
            .then().statusCode(400)
            .body("status", equalTo(400))
            .body("message", containsString("endereco.estado: Quando preenchida, informe a UF com 2 letras"))
            .body("message", containsString("endereco.cep: Quando preenchido, informe o CEP com 8 digitos"))
    }

    @Test
    @TestSecurity(user = "assistente", roles = ["ASSISTENTE"])
    fun `should accept stable union with optional fields empty`() {
        given()
            .contentType("application/json")
            .body(mapOf("nome" to "Cadastro minimo", "email" to "", "cpf" to "",
                "tipoPessoa" to "FISICA", "estadoCivil" to "UNIAO_ESTAVEL", "endereco" to null))
            .`when`().post("/pessoas")
            .then().statusCode(201)
            .body("nome", equalTo("Cadastro minimo"))
            .body("estadoCivil", equalTo("UNIAO_ESTAVEL"))
    }

    @Test
    @TestSecurity(user = "admin", roles = ["ADMIN"])
    fun `should create and update multiple people with blank fields without document conflicts`() {
        for (tipoPessoa in listOf("FISICA", "JURIDICA")) {
            for (blank in listOf("", "   ")) {
                val payload = mapOf("nome" to blank, "email" to blank, "cpf" to blank, "cnpj" to blank,
                    "razaoSocial" to blank, "tipoPessoa" to tipoPessoa,
                    "endereco" to mapOf("rua" to blank, "estado" to blank, "cep" to blank))
                val id = given().contentType("application/json").body(payload)
                    .`when`().post("/pessoas")
                    .then().statusCode(201)
                    .body("cpf", nullValue()).body("cnpj", nullValue())
                    .body("endereco.estado", nullValue()).body("endereco.cep", nullValue())
                    .extract().path<Int>("id")
                given().contentType("application/json").body(payload)
                    .`when`().put("/pessoas/$id")
                    .then().statusCode(200)
                    .body("cpf", nullValue()).body("cnpj", nullValue())
            }
        }
        given().contentType("application/json").body(emptyMap<String, Any>())
            .`when`().post("/pessoas")
            .then().statusCode(201)
    }

    @Test
    @TestSecurity(user = "assistente", roles = ["ASSISTENTE"])
    fun `should explain invalid CPF`() {
        given()
            .contentType("application/json")
            .body(mapOf("nome" to "Teste CPF", "cpf" to "11111111111", "tipoPessoa" to "FISICA"))
            .`when`().post("/pessoas")
            .then().statusCode(400)
            .body("message", equalTo("CPF invalido"))
    }

    @Test
    @TestSecurity(user = "assistente", roles = ["ASSISTENTE"])
    fun `should create pessoa`() {
        val cpf = validCpf(300001)
        given()
            .contentType("application/json")
            .body(
                pessoaFisicaRequest(
                    nome = "Cliente API",
                    cpf = cpf,
                    email = "cliente.api@example.com"
                )
            )
            .`when`()
            .post("/pessoas")
            .then()
            .statusCode(201)
            .body("nome", equalTo("Cliente API"))
            .body("cpf", equalTo(cpf))
    }

    @Test
    @TestSecurity(user = "advogado", roles = ["ADVOGADO"])
    fun `should create pessoa as advogado`() {
        val cpf = validCpf(300002)
        given()
            .contentType("application/json")
            .body(
                pessoaFisicaRequest(
                    nome = "Cliente Advogado",
                    cpf = cpf,
                    email = "cliente.advogado@example.com"
                )
            )
            .`when`()
            .post("/pessoas")
            .then()
            .statusCode(201)
            .body("nome", equalTo("Cliente Advogado"))
            .body("cpf", equalTo(cpf))
    }

    @Test
    @TestSecurity(user = "assistente", roles = ["ASSISTENTE"])
    fun `should list active pessoas with pagination`() {
        pessoaService.create(
            pessoaFisicaRequest(
                nome = "Ana Listagem",
                cpf = validCpf(300003),
                email = "ana.listagem@example.com"
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

    private fun pessoaFisicaRequest(nome: String, cpf: String, email: String): PessoaRequestDTO =
        PessoaRequestDTO(
            nome = nome,
            cpf = cpf,
            cnpj = null,
            email = email,
            telefone = "11911111111",
            nacionalidade = "Brasileira",
            estadoCivil = EstadoCivil.SOLTEIRO,
            rg = "123456789",
            orgaoEmissorRg = "SSP",
            pis = null,
            nomeMae = null,
            profissao = "Analista",
            razaoSocial = null,
            nomeFantasia = null,
            inscricaoEstadual = null,
            tipoPessoa = TipoPessoa.FISICA,
            dataNascimento = LocalDate.of(1990, 1, 1),
            endereco = null,
            observacoes = null,
            ativo = true
        )

    private fun validCpf(seed: Int): String {
        val base = seed.toString().padStart(9, '0').takeLast(9)
        val firstDigit = calculateCpfDigit(base, 10)
        val secondDigit = calculateCpfDigit(base + firstDigit, 11)
        return base + firstDigit + secondDigit
    }

    private fun calculateCpfDigit(value: String, initialWeight: Int): Int {
        val sum = value.mapIndexed { index, c -> c.digitToInt() * (initialWeight - index) }.sum()
        val mod = sum % 11
        return if (mod < 2) 0 else 11 - mod
    }
}
