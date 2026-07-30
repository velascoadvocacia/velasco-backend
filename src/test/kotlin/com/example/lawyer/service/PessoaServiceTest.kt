package com.example.lawyer.service

import com.example.lawyer.domain.enums.EstadoCivil
import com.example.lawyer.domain.enums.TipoPessoa
import com.example.lawyer.dto.request.PessoaRequestDTO
import com.example.lawyer.exception.BusinessException
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.LocalDate

@QuarkusTest
class PessoaServiceTest {
    @Inject
    lateinit var pessoaService: PessoaService

    @Test
    fun `should create pessoa when data is valid`() {
        val suffix = System.nanoTime().toString()
        val response = pessoaService.create(
            pessoaFisicaRequestDTO(
                suffix = suffix,
                nome = "Maria Souza $suffix",
                cpf = validCpf(100001),
                email = "valid.$suffix@example.com"
            )
        )

        assertEquals("Maria Souza $suffix", response.nome)
        assertEquals(validCpf(100001), response.cpf)
    }

    @Test
    fun `should throw business exception when cpf already exists`() {
        val suffix = System.nanoTime().toString()
        pessoaService.create(
            pessoaFisicaRequestDTO(
                suffix = suffix,
                nome = "Pessoa Base $suffix",
                cpf = validCpf(100002),
                email = "base.$suffix@example.com"
            )
        )

        assertThrows(BusinessException::class.java) {
            pessoaService.create(
                pessoaFisicaRequestDTO(
                    suffix = suffix,
                    nome = "Pessoa Duplicada $suffix",
                    cpf = validCpf(100002),
                    email = "other.$suffix@example.com"
                )
            )
        }
    }

    @Test
    fun `should throw business exception when email already exists`() {
        val suffix = System.nanoTime().toString()
        pessoaService.create(
            pessoaFisicaRequestDTO(
                suffix = suffix,
                nome = "Pessoa Base Email $suffix",
                cpf = validCpf(100003),
                email = "same.$suffix@example.com"
            )
        )

        assertThrows(BusinessException::class.java) {
            pessoaService.create(
                pessoaFisicaRequestDTO(
                    suffix = suffix,
                    nome = "Pessoa Outro Email $suffix",
                    cpf = validCpf(100004),
                    email = "same.$suffix@example.com"
                )
            )
        }
    }

    @Test
    fun `should create pessoa juridica without nome using razao social fallback`() {
        val suffix = System.nanoTime().toString()
        val response = pessoaService.create(
            pessoaJuridicaRequestDTO(
                suffix = suffix,
                nome = null,
                nomeFantasia = null,
                razaoSocial = "Empresa LTDA $suffix",
                cnpj = validCnpj(200001),
                email = "empresa.$suffix@example.com"
            )
        )

        assertEquals("Empresa LTDA $suffix", response.nome)
        assertEquals(validCnpj(200001), response.cnpj)
    }

    @Test
    fun `should create pessoa fisica without optional fields`() {
        val suffix = System.nanoTime().toString()

        val response = pessoaService.create(
            pessoaFisicaRequestDTO(
                suffix = suffix,
                nome = "Sem CPF $suffix",
                cpf = null,
                email = "sem.cpf.$suffix@example.com"
            )
        )

        assertEquals(null, response.cpf)
    }

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

    private fun pessoaFisicaRequestDTO(
        suffix: String,
        nome: String?,
        cpf: String?,
        email: String
    ): PessoaRequestDTO = PessoaRequestDTO(
        nome = nome,
        cpf = cpf,
        cnpj = null,
        email = email,
        telefone = "11999999999",
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
        observacoes = "PF $suffix",
        ativo = true
    )

    private fun pessoaJuridicaRequestDTO(
        suffix: String,
        nome: String?,
        nomeFantasia: String?,
        razaoSocial: String,
        cnpj: String,
        email: String
    ): PessoaRequestDTO = PessoaRequestDTO(
        nome = nome,
        cpf = null,
        cnpj = cnpj,
        email = email,
        telefone = "1133334444",
        nacionalidade = null,
        estadoCivil = null,
        rg = null,
        orgaoEmissorRg = null,
        pis = null,
        nomeMae = null,
        profissao = null,
        razaoSocial = razaoSocial,
        nomeFantasia = nomeFantasia,
        inscricaoEstadual = null,
        tipoPessoa = TipoPessoa.JURIDICA,
        dataNascimento = null,
        endereco = null,
        observacoes = "PJ $suffix",
        ativo = true
    )

    private fun validCnpj(seed: Int): String {
        val base = seed.toString().padStart(12, '0').takeLast(12)
        val firstDigit = calculateCnpjDigit(base, intArrayOf(5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2))
        val secondDigit = calculateCnpjDigit(base + firstDigit, intArrayOf(6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2))
        return base + firstDigit + secondDigit
    }

    private fun calculateCnpjDigit(value: String, weights: IntArray): Int {
        val sum = weights.mapIndexed { index, weight -> value[index].digitToInt() * weight }.sum()
        val mod = sum % 11
        return if (mod < 2) 0 else 11 - mod
    }
}
