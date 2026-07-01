package com.example.lawyer.resource

import com.example.lawyer.domain.enums.PerfilUsuario
import com.example.lawyer.dto.common.PageResponse
import com.example.lawyer.dto.request.UsuarioCreateRequest
import com.example.lawyer.dto.request.UsuarioUpdateRequest
import com.example.lawyer.dto.response.UsuarioDTO
import com.example.lawyer.service.UsuarioService
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

@Path("/usuarios")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@DenyAll
class UsuarioResource(private val service: UsuarioService) {
    @POST
    @RolesAllowed("ADMIN")
    fun create(@Valid request: UsuarioCreateRequest): Response =
        Response.status(Response.Status.CREATED).entity(service.create(request)).build()

    @GET
    @RolesAllowed("ADMIN", "ADVOGADO", "ASSISTENTE")
    fun list(
        @QueryParam("username") username: String?,
        @QueryParam("perfil") perfil: PerfilUsuario?,
        @QueryParam("page") @DefaultValue("0") page: Int,
        @QueryParam("size") @DefaultValue("20") size: Int
    ): PageResponse<UsuarioDTO> = service.list(username, perfil, page, size)

    @GET
    @Path("/{id}")
    @RolesAllowed("ADMIN", "ADVOGADO", "ASSISTENTE")
    fun getById(@PathParam("id") id: Long): UsuarioDTO = service.getById(id)

    @PUT
    @Path("/{id}")
    @RolesAllowed("ADMIN")
    fun update(@PathParam("id") id: Long, @Valid request: UsuarioUpdateRequest): UsuarioDTO =
        service.update(id, request)

    @DELETE
    @Path("/{id}")
    @RolesAllowed("ADMIN")
    fun delete(@PathParam("id") id: Long): Response {
        service.delete(id)
        return Response.noContent().build()
    }
}
