package com.SIGMA.USCO.Users.controller;

import com.SIGMA.USCO.Modalities.Entity.enums.ModalityStatus;
import com.SIGMA.USCO.Modalities.dto.ModalityDTO;
import com.SIGMA.USCO.Users.Entity.ProgramAuthority;
import com.SIGMA.USCO.Users.dto.request.AssignExaminerMultipleProgramsRequest;
import com.SIGMA.USCO.Users.dto.request.assignAuthorityProgram;
import com.SIGMA.USCO.Users.dto.request.PermissionDTO;
import com.SIGMA.USCO.Users.dto.request.RegisterUserByAdminRequest;
import com.SIGMA.USCO.Users.dto.request.RoleRequest;
import com.SIGMA.USCO.Users.dto.request.UpdateUserRequest;
import com.SIGMA.USCO.Users.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Administración", description = "Operaciones administrativas: roles, permisos, usuarios y autoridades")
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearer-jwt")
public class AdminController {

    private final AdminService adminService;

    @Operation(summary = "Crear rol", description = "Crea un nuevo rol en el sistema con los permisos especificados")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Rol creado exitosamente"),
            @ApiResponse(responseCode = "400", description = "Solicitud inválida"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado - sin permiso PERM_CREATE_ROLE")
    })
    @PostMapping("/createRole")
    @PreAuthorize("hasAuthority('PERM_CREATE_ROLE')")
    public ResponseEntity<?> createRole(@RequestBody RoleRequest request) {
        return adminService.createRole(request);
    }

    @Operation(summary = "Actualizar rol", description = "Actualiza un rol existente con nuevos permisos")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Rol actualizado exitosamente"),
            @ApiResponse(responseCode = "400", description = "Solicitud inválida"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado"),
            @ApiResponse(responseCode = "404", description = "Rol no encontrado")
    })
    @PutMapping("/updateRole/{id}")
    @PreAuthorize("hasAuthority('PERM_UPDATE_ROLE')")
    public ResponseEntity<?> updateRole(@Parameter(description = "ID del rol") @PathVariable Long id, @RequestBody RoleRequest request) {
        return adminService.updateRole(id, request);
    }

    @Operation(summary = "Asignar rol a usuario", description = "Asigna un rol existente a un usuario")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Rol asignado exitosamente"),
            @ApiResponse(responseCode = "400", description = "Solicitud inválida"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado")
    })
    @PostMapping("/assignRole")
    @PreAuthorize("hasAuthority('PERM_ASSIGN_ROLE')")
    public ResponseEntity<?> assignRoleToUser(@RequestBody UpdateUserRequest request) {
        return adminService.assignRoleToUser(request);
    }

    @Operation(summary = "Cambiar estado de usuario", description = "Cambia el estado de un usuario (activo/inactivo)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Estado actualizado"),
            @ApiResponse(responseCode = "400", description = "Solicitud inválida"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado")
    })
    @PostMapping("/changeUserStatus")
    @PreAuthorize("hasAuthority('PERM_ACTIVATE_OR_DEACTIVATE_USER')")
    public ResponseEntity<?> changeUserStatus(@RequestBody UpdateUserRequest request){
        return adminService.changeUserStatus(request);
    }

    @Operation(summary = "Obtener roles", description = "Obtiene la lista de todos los roles del sistema")
    @ApiResponse(responseCode = "200", description = "Lista de roles obtenida")
    @GetMapping("/getRoles")
    @PreAuthorize("hasAuthority('PERM_VIEW_ROLE')")
    public ResponseEntity<?> getRoles() {
        return adminService.getRoles();
    }

    @Operation(summary = "Crear permiso", description = "Crea un nuevo permiso en el sistema")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Permiso creado"),
            @ApiResponse(responseCode = "400", description = "Solicitud inválida"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado")
    })
    @PostMapping("/createPermission")
    @PreAuthorize("hasAuthority('PERM_CREATE_PERMISSION')")
    public ResponseEntity<?> createPermission(@RequestBody PermissionDTO request) {
        return adminService.createPermission(request);
    }

    @Operation(summary = "Obtener permisos", description = "Obtiene la lista de todos los permisos disponibles")
    @ApiResponse(responseCode = "200", description = "Lista de permisos obtenida")
    @GetMapping("/getPermissions")
    @PreAuthorize("hasAuthority('PERM_VIEW_PERMISSION')")
    public ResponseEntity<?> getPermissions() {
        return adminService.getPermissions();
    }

    @Operation(summary = "Obtener usuarios", description = "Obtiene lista de usuarios con filtros opcionales por estado, rol, programa académico, facultad, nombre, apellido y email")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de usuarios obtenida"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado")
    })
    @GetMapping("/getUsers")
    @PreAuthorize("hasAuthority('PERM_VIEW_USER')")
    public ResponseEntity<?> getUsers(
            @Parameter(description = "Filtrar por estado (ACTIVE, INACTIVE)") @RequestParam(required = false) String status,
            @Parameter(description = "Filtrar por nombre de rol") @RequestParam(required = false) String role,
            @Parameter(description = "Filtrar por ID de programa académico") @RequestParam(required = false, name = "programId") Long academicProgramId,
            @Parameter(description = "Filtrar por ID de facultad") @RequestParam(required = false) Long facultyId,
            @Parameter(description = "Filtrar por nombre (búsqueda parcial)") @RequestParam(required = false) String name,
            @Parameter(description = "Filtrar por apellido (búsqueda parcial)") @RequestParam(required = false) String lastName,
            @Parameter(description = "Filtrar por email (búsqueda parcial)") @RequestParam(required = false) String email
    ) {
        return adminService.getUsers(status, role, academicProgramId, facultyId, name, lastName, email);
    }

    @Operation(summary = "Desactivar usuario", description = "Desactiva un usuario específico por su ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Usuario desactivado"),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado")
    })
    @PutMapping("/changeUserStatus/{userId}")
    @PreAuthorize("hasAuthority('PERM_ACTIVATE_OR_DEACTIVATE_USER')")
    public ResponseEntity<?> desactiveUser(@Parameter(description = "ID del usuario") @PathVariable Long userId) {
        return adminService.desactiveUser(userId);
    }

    @Operation(summary = "Obtener modalidades", description = "Obtiene lista de modalidades con filtro opcional de estado")
    @ApiResponse(responseCode = "200", description = "Lista de modalidades obtenida")
    @GetMapping("/modalities")
    @PreAuthorize("hasAuthority('PERM_VIEW_MODALITIES_ADMIN')")
    public ResponseEntity<List<ModalityDTO>> getModalities(@Parameter(description = "Filtrar por estado de modalidad") @RequestParam(required = false) ModalityStatus status) {
        return adminService.getModalities(status);
    }

    @Operation(summary = "Asignar jefe de programa", description = "Asigna un usuario como jefe de programa académico")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Jefe asignado correctamente"),
            @ApiResponse(responseCode = "400", description = "Solicitud inválida"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado")
    })
    @PostMapping("/assign-program-head")
    @PreAuthorize("hasAuthority('PERM_ASSIGN_PROGRAM_HEAD')")
    public ResponseEntity<?> assignProgramHead(@RequestBody assignAuthorityProgram request){

        try {
            ProgramAuthority assigned = adminService.assignProgramHead(request);
            return ResponseEntity.ok(
                    Map.of(
                        "message", "Se ha asignado el jefe de programa correctamente"
                     )
            );

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }

    }

    @Operation(summary = "Asignar director de proyecto", description = "Asigna un usuario como director de proyecto para una modalidad")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Director asignado correctamente"),
            @ApiResponse(responseCode = "400", description = "Solicitud inválida"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado")
    })
    @PostMapping("/assign-project-director")
    @PreAuthorize("hasAuthority('PERM_ASSIGN_PROGRAM_HEAD')")
    public ResponseEntity<?> assignProjectDirector(@RequestBody assignAuthorityProgram request){

        try {
            ProgramAuthority assigned = adminService.assignProjectDirector(request);
            return ResponseEntity.ok(
                    Map.of(
                            "message", "Se ha asignado el director de proyecto correctamente"
                    )
            );

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }

    }

    @Operation(summary = "Asignar miembro del comité", description = "Asigna un usuario como miembro del comité curricular")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Miembro asignado correctamente"),
            @ApiResponse(responseCode = "400", description = "Solicitud inválida"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado")
    })
    @PostMapping("/assign-committee-member")
    @PreAuthorize("hasAuthority('PERM_ASSIGN_PROGRAM_HEAD')")
    public ResponseEntity<?> assignCommittee(@RequestBody assignAuthorityProgram request){

        try {
            ProgramAuthority assigned = adminService.assignCommittee(request);
            return ResponseEntity.ok(
                    Map.of(
                            "message", "Se ha asignado el miembro del comité correctamente"
                    )
            );

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }

    }

    @Operation(summary = "Asignar jurado/evaluador", description = "Asigna un usuario como jurado o evaluador para una modalidad")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Jurado asignado correctamente"),
            @ApiResponse(responseCode = "400", description = "Solicitud inválida"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado")
    })
    @PostMapping("/assign-examiner")
    @PreAuthorize("hasAuthority('PERM_ASSIGN_EXAMINER')")
    public ResponseEntity<?> assignExaminer(@RequestBody assignAuthorityProgram request){

        try {
            ProgramAuthority assigned = adminService.assignExaminer(request);
            return ResponseEntity.ok(
                    Map.of(
                            "message", "Se ha asignado el jurado/evaluador (examiner) correctamente"
                    )
            );

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }

    }

    @Operation(summary = "Registrar usuario por administrador", description = "Crea un nuevo usuario en el sistema con los datos proporcionados")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Usuario registrado exitosamente"),
            @ApiResponse(responseCode = "400", description = "Solicitud inválida"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado")
    })
    @PostMapping("/register-user")
    @PreAuthorize("hasAuthority('PERM_CREATE_USER')")
    public ResponseEntity<?> registerUserByAdmin(@RequestBody RegisterUserByAdminRequest request) {
        return adminService.registerUserByAdmin(request);
    }

    @Operation(
            summary = "Asignar jurado a múltiples programas",
            description = "Vincula un jurado a múltiples programas académicos en una sola operación. Si el usuario no tiene el rol EXAMINER, se lo asigna automáticamente.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "{ \"userId\": 1, \"academicProgramIds\": [1, 2, 3] }",
                    required = true
            )
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Jurado vinculado a programas exitosamente"),
            @ApiResponse(responseCode = "400", description = "Solicitud inválida"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado")
    })
    @PostMapping("/examiner/assign-programs")
    @PreAuthorize("hasAuthority('PERM_ASSIGN_EXAMINER')")
    public ResponseEntity<?> assignExaminerToMultiplePrograms(
            @RequestBody AssignExaminerMultipleProgramsRequest request) {
        return adminService.assignExaminerToMultiplePrograms(request);
    }

    @Operation(
            summary = "Asignar jurado a programa adicional",
            description = "Vincula un jurado existente a un programa académico adicional. Un jurado puede estar vinculado a múltiples programas."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Jurado vinculado exitosamente"),
            @ApiResponse(responseCode = "400", description = "Solicitud inválida"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado")
    })
    @PostMapping("/examiner/assign-program")
    @PreAuthorize("hasAuthority('PERM_ASSIGN_EXAMINER')")
    public ResponseEntity<?> assignExaminerToAdditionalProgram(@RequestBody assignAuthorityProgram request) {
        return adminService.assignExaminerToAdditionalProgram(request);
    }

    @Operation(
            summary = "Desvinc ular jurado de programa",
            description = "Desvincula un jurado de un programa académico específico"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Jurado desvinculado exitosamente"),
            @ApiResponse(responseCode = "404", description = "No encontrado"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado")
    })
    @DeleteMapping("/examiner/{userId}/program/{academicProgramId}")
    @PreAuthorize("hasAuthority('PERM_ASSIGN_EXAMINER')")
    public ResponseEntity<?> removeExaminerFromProgram(
            @Parameter(description = "ID del usuario jurado") @PathVariable Long userId,
            @Parameter(description = "ID del programa académico") @PathVariable Long academicProgramId) {
        return adminService.removeExaminerFromProgram(userId, academicProgramId);
    }

    @Operation(
            summary = "Obtener programas de jurado",
            description = "Retorna todos los programas académicos a los que está asociado un jurado"
    )
    @ApiResponse(responseCode = "200", description = "Lista de programas obtenida")
    @GetMapping("/examiner/{userId}/programs")
    @PreAuthorize("hasAuthority('PERM_ASSIGN_EXAMINER')")
    public ResponseEntity<?> getExaminerPrograms(@Parameter(description = "ID del usuario jurado") @PathVariable Long userId) {
        return adminService.getExaminerPrograms(userId);
    }

}
