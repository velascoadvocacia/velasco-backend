package com.example.lawyer.service

import com.example.lawyer.domain.enums.TipoPessoa
import com.example.lawyer.dto.request.PessoaRequestDTO
import com.example.lawyer.exception.BusinessException
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

@QuarkusTest
class PessoaServiceTest {
    @Inject
    lateinit var pessoaService: PessoaService

    @Test
    fun `should create pessoa when data is valid`() {
        val suffix = System.nanoTime().toString()
        val response = pessoaService.create(
            pessoaRequestDTO(
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
            pessoaRequestDTO(
                nome = "Pessoa Base $suffix",
                cpf = validCpf(100002),
                email = "base.$suffix@example.com"
            )
        )

        assertThrows(BusinessException::class.java) {
            pessoaService.create(
                pessoaRequestDTO(
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
            pessoaRequestDTO(
                nome = "Pessoa Base Email $suffix",
                cpf = validCpf(100003),
                email = "same.$suffix@example.com"
            )
        )

        assertThrows(BusinessException::class.java) {
            pessoaService.create(
                pessoaRequestDTO(
                    nome = "Pessoa Outro Email $suffix",
                    cpf = validCpf(100004),
                    email = "same.$suffix@example.com"
                )
            )
        }
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

    private fun pessoaRequestDTO(nome: String, cpf: String, email: String): PessoaRequestDTO = PessoaRequestDTO(
        nome = nome,
        cpf = cpf,
        cnpj = null,
        email = email,
        telefone = "11999999999",
        tipoPessoa = TipoPessoa.FISICA,
        dataNascimento = null,
        endereco = null,
        ativo = true
    )
}
