package com.example.lawyer.resource

import com.example.lawyer.dto.request.RtExportRequest
import com.example.lawyer.dto.request.RtExportBlockRequest
import com.example.lawyer.dto.request.RtExportImageRequest
import com.example.lawyer.dto.request.RtPreviewRequest
import com.example.lawyer.dto.response.RtPreviewResponse
import com.example.lawyer.service.RtExportService
import com.example.lawyer.service.ProcessoAnexoService
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
    private val templateService: RtTemplateService,
    private val processoAnexoService: ProcessoAnexoService
) {
    @POST
    @Path("/preview")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("ADMIN", "ADVOGADO", "ASSISTENTE")
    fun preview(@Valid request: RtPreviewRequest): RtPreviewResponse {
        return RtPreviewResponse(
            request.processoId,
            templateService.generateSelectedBlocks(request)
        )
    }

    @POST
    @Path("/export")
    @Produces(DOCX_MEDIA_TYPE)
    @RolesAllowed("ADMIN", "ADVOGADO", "ASSISTENTE")
    fun export(@Valid request: RtExportRequest): Response {
        val generatedBlocks = if (request.blocosSelecionados.isNotEmpty()) {
            templateService.generateSelectedBlocks(
                RtPreviewRequest(
                    processoId = request.processoId,
                    reclamantesIds = request.reclamantesIds,
                    reclamadasIds = request.reclamadasIds,
                    advogadosIds = request.advogadosIds,
                    blocosSelecionados = request.blocosSelecionados,
                    dadosVariaveis = request.dadosVariaveis
                )
            ).map { block ->
                RtExportBlockRequest(
                    title = block.titulo,
                    content = block.texto,
                    anexos = block.anexos.map { RtExportImageRequest(it.url, it.contentType, it.nomeOriginal) }
                )
            }
        } else {
            val anexosCtps = request.processoId?.let(processoAnexoService::list).orEmpty()
            request.blocks.map { block ->
                if (block.title == "3. Baixa na CTPS física. Tutela antecipada" && block.anexos.isEmpty()) {
                    block.copy(anexos = anexosCtps.map { RtExportImageRequest(it.url, it.contentType, it.nomeOriginal) })
                } else block
            }
        }
        if (generatedBlocks.isEmpty()) {
            throw com.example.lawyer.exception.BusinessException("Informe ao menos um bloco para exportação")
        }
        val buffer = service.generate(request.copy(blocks = generatedBlocks))

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
