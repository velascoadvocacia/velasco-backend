package com.example.lawyer.resource

import com.example.lawyer.dto.request.RtExportRequest
import com.example.lawyer.dto.request.RtExportBlockRequest
import com.example.lawyer.dto.request.RtExportImageRequest
import com.example.lawyer.dto.request.RtExportInlineImageRequest
import com.example.lawyer.dto.request.RtPreviewRequest
import com.example.lawyer.dto.request.ProcuracaoExportRequest
import com.example.lawyer.dto.response.RtPreviewResponse
import com.example.lawyer.service.RtExportService
import com.example.lawyer.service.ProcessoAnexoService
import com.example.lawyer.service.ProcuracaoExportService
import com.example.lawyer.service.RtTemplateService
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.security.RolesAllowed
import jakarta.validation.Valid
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.HttpHeaders
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.jboss.resteasy.reactive.RestForm
import org.jboss.resteasy.reactive.PartType
import org.jboss.resteasy.reactive.multipart.FileUpload
import org.jboss.logging.Logger
import java.nio.file.Files

@Path("/rt")
@Consumes(MediaType.APPLICATION_JSON)
class RtExportResource(
    private val service: RtExportService,
    private val templateService: RtTemplateService,
    private val processoAnexoService: ProcessoAnexoService,
    private val procuracaoExportService: ProcuracaoExportService,
    private val objectMapper: ObjectMapper
) {
    @POST
    @Path("/export-procuracao")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(DOCX_MEDIA_TYPE)
    @RolesAllowed("ADMIN", "ADVOGADO", "ASSISTENTE")
    fun exportProcuracao(@Valid request: ProcuracaoExportRequest): Response {
        val generated = procuracaoExportService.generate(request)
        return Response.ok(generated.bytes, DOCX_MEDIA_TYPE)
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"PROCURAÇÃO AD JUDICIA - ${sanitizeFilename(generated.nomeReclamante).uppercase()}.docx\""
            )
            .build()
    }

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

    @GET
    @Path("/assets/retencao-ctps-dano-moral")
    @Produces("image/png")
    @RolesAllowed("ADMIN", "ADVOGADO", "ASSISTENTE")
    fun retencaoCtpsDanoMoralImage(): Response =
        Response.ok(staticAssetBytes(RtTemplateService.RETENCAO_CTPS_IMAGE_PATH), "image/png").build()

    @POST
    @Path("/export")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(DOCX_MEDIA_TYPE)
    @RolesAllowed("ADMIN", "ADVOGADO", "ASSISTENTE")
    fun export(
        @RestForm("payload") @PartType(MediaType.TEXT_PLAIN) payload: String?,
        @RestForm("documento") @PartType(MediaType.TEXT_PLAIN) documentoLegado: String?,
        @RestForm(FileUpload.ALL) arquivos: List<FileUpload>
    ): Response {
        val payloadJson = payload ?: documentoLegado
        if (payloadJson.isNullOrBlank()) {
            throw com.example.lawyer.exception.BusinessException(
                "Campo multipart 'payload' é obrigatório e deve conter o JSON da exportação"
            )
        }
        val request = runCatching { objectMapper.readValue(payloadJson, RtExportRequest::class.java) }
            .getOrElse { error ->
                throw com.example.lawyer.exception.BusinessException("Campo multipart 'payload' contém JSON inválido")
            }
        return exportRequest(request, arquivos)
    }

    private fun exportRequest(request: RtExportRequest, arquivos: List<FileUpload>): Response {
        logger.infof(
            "RT export multipart: arquivos recebidos=%d; campos=%s",
            arquivos.size,
            arquivos.joinToString(prefix = "[", postfix = "]") { it.name() }
        )
        val imagensPorBloco = arquivos
            .map { file ->
                val blocoId = attachmentBlockId(file.name())
                    ?: attachmentBlockId(file.fileName())
                    ?: "baixa_ctps_tutela"
                blocoId to file
            }
            .groupBy { (blocoId, _) -> canonicalBlockId(blocoId) }
            .mapValues { (_, entries) -> entries.map { (_, file) ->
                val contentType = file.contentType().lowercase()
                if (contentType !in ALLOWED_IMAGE_TYPES) {
                    throw com.example.lawyer.exception.BusinessException("Tipo de imagem não permitido: $contentType")
                }
                RtExportImageRequest(
                    bytes = Files.readAllBytes(file.uploadedFile()),
                    contentType = contentType,
                    nomeOriginal = file.fileName()
                )
            } }
        logger.infof(
            "RT export multipart: associação por bloco=%s",
            imagensPorBloco.entries.joinToString(prefix = "[", postfix = "]") { "${it.key}=${it.value.size}" }
        )
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
                val anexos = imagensPorBloco[block.id].orEmpty().ifEmpty {
                    block.anexos.map {
                        RtExportImageRequest(contentType = it.contentType, nomeOriginal = it.nomeOriginal, url = it.url)
                    }
                }
                logger.infof("RT export bloco '%s' (%s): anexos associados=%d", block.titulo, block.id, anexos.size)
                RtExportBlockRequest(
                    id = block.id,
                    title = block.titulo,
                    content = block.texto,
                    anexos = anexos,
                    imagensFixas = fixedInlineImages(block.id)
                )
            }
        } else {
            val anexosCtps = request.processoId?.let { processoAnexoService.list(it) }.orEmpty()
            request.blocks.map { block ->
                val blocoId = block.id ?: blockIdFromTitle(block.title)
                val anexosMultipart = blocoId?.let { imagensPorBloco[it] }.orEmpty()
                val anexosPersistidos = if (blocoId == "baixa_ctps_tutela") {
                    anexosCtps.map {
                        RtExportImageRequest(contentType = it.contentType, nomeOriginal = it.nomeOriginal, url = it.url)
                    }
                } else emptyList()
                var resolved = if (block.anexos.isEmpty()) {
                    block.copy(anexos = anexosMultipart.ifEmpty { anexosPersistidos })
                } else block
                if (resolved.imagensFixas.isEmpty()) {
                    resolved = resolved.copy(imagensFixas = fixedInlineImages(blocoId))
                }
                logger.infof("RT export bloco legado '%s': anexos associados=%d", resolved.title, resolved.anexos.size)
                resolved
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

    private fun staticAssetBytes(path: String): ByteArray =
        javaClass.getResourceAsStream(path)?.use { it.readBytes() }
            ?: error("Imagem estática não encontrada: $path")

    private fun fixedInlineImages(blockId: String?): List<RtExportInlineImageRequest> =
        if (blockId == RtTemplateService.RETENCAO_CTPS_DANO_MORAL) {
            listOf(
                RtExportInlineImageRequest(
                    bytes = staticAssetBytes(RtTemplateService.RETENCAO_CTPS_IMAGE_PATH),
                    contentType = "image/png",
                    nomeOriginal = RtTemplateService.RETENCAO_CTPS_IMAGE_NAME,
                    afterParagraph = 2,
                    originalWidthPx = RETENCAO_CTPS_IMAGE_WIDTH_PX,
                    originalHeightPx = RETENCAO_CTPS_IMAGE_HEIGHT_PX
                )
            )
        } else {
            emptyList()
        }

    private fun sanitizeFilename(value: String): String =
        value.replace(Regex("[\\r\\n\\\\/:*?\"<>|]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { "Reclamante" }

    private fun blockIdFromTitle(title: String): String? {
        val normalized = java.text.Normalizer.normalize(title, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
        return when {
            "retencao da ctps" in normalized || normalized.endsWith("ctps. dano moral") ->
                RtTemplateService.RETENCAO_CTPS_DANO_MORAL
            "baixa na ctps" in normalized -> "baixa_ctps_tutela"
            "contrato administrativo" in normalized -> "responsabilidade_subsidiaria_contrato_administrativo"
            "grupo economico" in normalized -> "responsabilidade_solidaria_grupo_economico"
            "piso convencional" in normalized -> "diferencas_salariais_piso_convencional"
            else -> null
        }
    }

    private fun attachmentBlockId(value: String): String? =
        Regex("^anexo_(.+)_\\d+(?:\\.[^.]+)?$").matchEntire(value)?.groupValues?.get(1)

    private fun canonicalBlockId(value: String): String = when (value) {
        "baixa_ctps", "baixa_ctps_fisica" -> "baixa_ctps_tutela"
        else -> value
    }

    private companion object {
        val logger: Logger = Logger.getLogger(RtExportResource::class.java)
        const val DOCX_MEDIA_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        const val RETENCAO_CTPS_IMAGE_WIDTH_PX = 695
        const val RETENCAO_CTPS_IMAGE_HEIGHT_PX = 416
        val ALLOWED_IMAGE_TYPES = setOf("image/jpeg", "image/png")
    }
}
