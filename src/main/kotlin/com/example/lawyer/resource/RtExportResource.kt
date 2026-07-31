package com.example.lawyer.resource

import com.example.lawyer.dto.request.RtExportRequest
import com.example.lawyer.dto.request.RtPreviewRequest
import com.example.lawyer.dto.response.RtPreviewResponse
import com.example.lawyer.service.RtExportService
import com.example.lawyer.service.RtTemplateService
import jakarta.annotation.security.RolesAllowed
import jakarta.validation.Valid
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.HttpHeaders
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/rt")
@Consumes(MediaType.APPLICATION_JSON)
class RtExportResource(
    private val service: RtExportService,
    private val templateService: RtTemplateService
) {
    @POST
    @Path("/preview")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("ADMIN", "ADVOGADO", "ASSISTENTE")
    fun preview(@Valid request: RtPreviewRequest): RtPreviewResponse {
        val processoId = request.processoId!!
        return RtPreviewResponse(
            processoId,
            templateService.generateSelectedBlocks(processoId, request.blocosSelecionados, request.advogadosIds)
        )
    }

    @POST
    @Path("/export")
    @Produces(DOCX_MEDIA_TYPE)
    @RolesAllowed("ADMIN", "ADVOGADO", "ASSISTENTE")
    fun export(@Valid request: RtExportRequest): Response {
        val buffer = service.generate(request)

        return Response.ok(buffer, DOCX_MEDIA_TYPE)
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"RT - ${sanitizeFilename(request.claimantName)}.docx\""
            )
            .build()
    }

    private fun sanitizeFilename(value: String): String =
        value.replace(Regex("[\\r\\n\\\\/:*?\"<>|]"), " ").trim().ifBlank { "Reclamante" }

    private companion object {
        const val DOCX_MEDIA_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    }
}
