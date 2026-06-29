package com.example.lawyer.service

object DocumentValidator {
    fun onlyDigits(value: String?): String? = value?.filter { it.isDigit() }

    fun isValidCpf(value: String?): Boolean {
        val cpf = onlyDigits(value) ?: return false
        if (cpf.length != 11 || cpf.toSet().size == 1) return false
        val first = cpf.calculateDigit(9, 10)
        val second = cpf.calculateDigit(10, 11)
        return cpf[9].digitToInt() == first && cpf[10].digitToInt() == second
    }

    fun isValidCnpj(value: String?): Boolean {
        val cnpj = onlyDigits(value) ?: return false
        if (cnpj.length != 14 || cnpj.toSet().size == 1) return false
        val firstWeights = intArrayOf(5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2)
        val secondWeights = intArrayOf(6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2)
        return cnpj[12].digitToInt() == cnpj.calculateDigit(firstWeights) &&
            cnpj[13].digitToInt() == cnpj.calculateDigit(secondWeights)
    }

    private fun String.calculateDigit(length: Int, initialWeight: Int): Int {
        val sum = take(length).mapIndexed { index, char -> char.digitToInt() * (initialWeight - index) }.sum()
        val mod = sum % 11
        return if (mod < 2) 0 else 11 - mod
    }

    private fun String.calculateDigit(weights: IntArray): Int {
        val sum = weights.mapIndexed { index, weight -> this[index].digitToInt() * weight }.sum()
        val mod = sum % 11
        return if (mod < 2) 0 else 11 - mod
    }
}
