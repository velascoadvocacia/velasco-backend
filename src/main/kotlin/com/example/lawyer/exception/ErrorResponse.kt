package com.example.lawyer.exception

import java.time.OffsetDateTime

data class ErrorResponse(
    val message: String,
    val status: Int,
    val timestamp: OffsetDateTime = OffsetDateTime.now()
)
