package com.example.lawyer.dto.request

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty

data class RtExportRequest(
    @field:NotEmpty
    @field:Valid
    val blocks: List<RtExportBlockRequest>,

    @field:NotBlank
    val claimantName: String
)

data class RtExportBlockRequest(
    @field:NotBlank
    val title: String,

    @field:NotBlank
    val content: String
)
