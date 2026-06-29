package com.example.lawyer.resource

import com.example.lawyer.dto.common.PageResponse
import com.example.lawyer.dto.request.MovimentacaoCreateRequest
import com.example.lawyer.dto.request.MovimentacaoUpdateRequest
import com.example.lawyer.dto.response.MovimentacaoResponse
import com.example.lawyer.service.MovimentacaoService
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

@Path("/movimentacoes")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@DenyAll
class MovimentacaoResource(private val service: MovimentacaoService) {
    @POST
    @RolesAllowed("ADMIN", "ADVOGADO", "ASSISTENTE")
    fun create(@Valid request: MovimentacaoCreateRequest): Response =
        Response.status(Response.Status.CREATED).entity(service.create(request)).build()

    @GET
    @RolesAllowed("ADMIN", "ADVOGADO", "ASSISTENTE")
    fun list(
        @QueryParam("processoId") processoId: Long?,
        @QueryParam("page") @DefaultValue("0") page: Int,
        @QueryParam("size") @DefaultValue("20") size: Int
    ): PageResponse<MovimentacaoResponse> = service.list(processoId, page, size)

    @GET
    @Path("/{id}")
    @RolesAllowed("ADMIN", "ADVOGADO", "ASSISTENTE")
    fun getById(@PathParam("id") id: Long): MovimentacaoResponse = service.getById(id)

    @PUT
    @Path("/{id}")
    @RolesAllowed("ADMIN", "ADVOGADO")
    fun update(@PathParam("id") id: Long, @Valid request: MovimentacaoUpdateRequest): MovimentacaoResponse =
        service.update(id, request)

    @DELETE
    @Path("/{id}")
    @RolesAllowed("ADMIN")
    fun delete(@PathParam("id") id: Long): Response {
        service.delete(id)
        return Response.noContent().build()
    }
}
