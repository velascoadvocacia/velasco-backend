package com.example.lawyer.resource

import com.example.lawyer.domain.enums.StatusProcesso
import com.example.lawyer.dto.common.PageResponse
import com.example.lawyer.dto.request.ProcessoCreateRequest
import com.example.lawyer.dto.request.ProcessoUpdateRequest
import com.example.lawyer.dto.response.ProcessoDTO
import com.example.lawyer.service.ProcessoService
import jakarta.annotation.security.DenyAll
import jakarta.annotation.security.RolesAllowed
import jakarta.validation.Valid
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.DefaultValue
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/processos")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@DenyAll
class ProcessoResource(private val service: ProcessoService) {
    @POST
    @RolesAllowed("ADMIN", "ADVOGADO", "ASSISTENTE")
    fun create(@Valid request: ProcessoCreateRequest): Response =
        Response.status(Response.Status.CREATED).entity(service.create(request)).build()

    @GET
    @RolesAllowed("ADMIN", "ADVOGADO", "ASSISTENTE")
    fun list(
        @QueryParam("numeroProcesso") numeroProcesso: String?,
        @QueryParam("clienteId") clienteId: Long?,
        @QueryParam("advogadoId") advogadoId: Long?,
        @QueryParam("status") status: StatusProcesso?,
        @QueryParam("page") @DefaultValue("0") page: Int,
        @QueryParam("size") @DefaultValue("20") size: Int
    ): PageResponse<ProcessoDTO> =
        service.list(numeroProcesso, clienteId, advogadoId, status, page, size)

    @GET
    @Path("/{id}")
    @RolesAllowed("ADMIN", "ADVOGADO", "ASSISTENTE")
    fun getById(@PathParam("id") id: Long): ProcessoDTO = service.getById(id)

    @PUT
    @Path("/{id}")
    @RolesAllowed("ADMIN", "ADVOGADO")
    fun update(@PathParam("id") id: Long, @Valid request: ProcessoUpdateRequest): ProcessoDTO =
        service.update(id, request)

    @DELETE
    @Path("/{id}")
    @RolesAllowed("ADMIN")
    fun delete(@PathParam("id") id: Long): Response {
        service.delete(id)
        return Response.noContent().build()
    }
}
