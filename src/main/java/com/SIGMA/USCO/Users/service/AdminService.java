package com.SIGMA.USCO.Users.service;

import com.SIGMA.USCO.Modalities.Entity.DegreeModality;
import com.SIGMA.USCO.Modalities.Entity.enums.ModalityStatus;
import com.SIGMA.USCO.Modalities.Repository.DegreeModalityRepository;
import com.SIGMA.USCO.Modalities.Repository.ModalityRequirementsRepository;
import com.SIGMA.USCO.Modalities.dto.ModalityDTO;
import com.SIGMA.USCO.Modalities.dto.RequirementDTO;
import com.SIGMA.USCO.Users.Entity.Permission;
import com.SIGMA.USCO.Users.Entity.ProgramAuthority;
import com.SIGMA.USCO.Users.Entity.Role;
import com.SIGMA.USCO.Users.Entity.enums.ProgramRole;
import com.SIGMA.USCO.Users.Entity.enums.Status;
import com.SIGMA.USCO.Users.Entity.User;
import com.SIGMA.USCO.Users.dto.request.AssignExaminerMultipleProgramsRequest;
import com.SIGMA.USCO.Users.dto.request.assignAuthorityProgram;
import com.SIGMA.USCO.Users.dto.request.PermissionDTO;
import com.SIGMA.USCO.Users.dto.request.RegisterUserByAdminRequest;
import com.SIGMA.USCO.Users.dto.request.RoleRequest;
import com.SIGMA.USCO.Users.dto.request.UpdateUserRequest;
import com.SIGMA.USCO.Users.dto.response.UserResponse;
import com.SIGMA.USCO.Users.repository.PermissionRepository;
import com.SIGMA.USCO.Users.repository.ProgramAuthorityRepository;
import com.SIGMA.USCO.Users.repository.RoleRepository;
import com.SIGMA.USCO.Users.repository.UserRepository;
import com.SIGMA.USCO.academic.entity.AcademicProgram;
import com.SIGMA.USCO.academic.entity.StudentProfile;
import com.SIGMA.USCO.academic.repository.AcademicProgramRepository;
import com.SIGMA.USCO.academic.repository.StudentProfileRepository;
import com.SIGMA.USCO.documents.dto.RequiredDocumentDTO;
import com.SIGMA.USCO.documents.repository.RequiredDocumentRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final DegreeModalityRepository degreeModalityRepository;
    private final ModalityRequirementsRepository modalityRequirementsRepository;
    private final RequiredDocumentRepository requiredDocumentRepository;
    private final AcademicProgramRepository academicProgramRepository;
    private final ProgramAuthorityRepository programAuthorityRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final PasswordEncoder passwordEncoder;


    public ResponseEntity<?> getRoles() {

        return ResponseEntity.ok(roleRepository.findAll().stream().map(role -> RoleRequest.builder()
                                .id(role.getId())
                                .name(role.getName())
                                .permissionIds(
                                        role.getPermissions()
                                                .stream()
                                                .map(Permission::getId)
                                                .collect(Collectors.toSet()))
                                .build()
                        )
                        .toList()
        );
    }

    public ResponseEntity<?> createRole(RoleRequest request) {

        if (request.getName() == null || request.getName().isBlank()) {
            return ResponseEntity.badRequest().body("El nombre del rol es obligatorio");
        }

        if (roleRepository.findByName(request.getName()).isPresent()) {
            return ResponseEntity.badRequest().body("El rol ya existe.");
        }

        Set<Permission> permissions = Set.of();

        if (request.getPermissionIds() != null && !request.getPermissionIds().isEmpty()) {
            permissions = permissionRepository.findAllById(request.getPermissionIds())
                    .stream().collect(Collectors.toSet());
        }

        Role role = Role.builder()
                .name(request.getName().toUpperCase())
                .permissions(permissions)
                .build();

        roleRepository.save(role);

        return ResponseEntity.ok(" Rol creado correctamente.");
    }

    public ResponseEntity<?> updateRole(Long id, RoleRequest request){
        if (request.getName() == null || request.getName().isBlank()) {
            return ResponseEntity.badRequest().body("El nombre del rol es obligatorio");
        }

        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Rol no encontrado"));

        Optional<Role> existingRole = roleRepository.findByNameIgnoreCase(request.getName());

        if (existingRole.isPresent() && !existingRole.get().getId().equals(id)) {
            return ResponseEntity.badRequest().body("El rol ya existe.");
        }


        Set<Permission> permissions = Set.of();

        if (request.getPermissionIds() != null && !request.getPermissionIds().isEmpty()) {
            permissions = permissionRepository.findAllById(request.getPermissionIds())
                    .stream().collect(Collectors.toSet());
        }

        role.setName(request.getName().toUpperCase());
        role.setPermissions(permissions);

        roleRepository.save(role);

        return ResponseEntity.ok(" Rol actualizado correctamente.");
    }

    public ResponseEntity<?> assignRoleToUser(UpdateUserRequest request){

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Role role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new RuntimeException("Rol no encontrado"));

        user.getRoles().clear();
        user.getRoles().add(role);
        user.setLastUpdateDate(LocalDateTime.now());
        userRepository.save(user);

        return ResponseEntity.ok("Rol asignado correctamente al usuario.");
    }

    public ResponseEntity<?> changeUserStatus(UpdateUserRequest request){

        if (request.getStatus() == null) {
            return ResponseEntity.badRequest().body("El estado debe ser ACTIVE o INACTIVE.");
        }

        Status newStatus;
        try {
            newStatus = Status.valueOf(request.getStatus().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("El estado debe ser ACTIVE o INACTIVE.");
        }

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        user.setStatus(newStatus);
        user.setLastUpdateDate(LocalDateTime.now());
        userRepository.save(user);
        return ResponseEntity.ok("Estado del usuario actualizado correctamente.");

    }

    public ResponseEntity<?> createPermission (PermissionDTO request){

        if (request.getName() == null || request.getName().isBlank()) {
            return ResponseEntity.badRequest().body("El nombre del permiso es obligatorio");
        }

        if (permissionRepository.findByName(request.getName()).isPresent()) {
            return ResponseEntity.badRequest().body("El permiso ya existe.");
        }

        Permission permission = Permission.builder()
                .name(request.getName().toUpperCase())
                .build();

        permissionRepository.save(permission);

        return ResponseEntity.ok(" Permiso creado correctamente.");

    }

    public ResponseEntity<?> getPermissions() {

        return ResponseEntity.ok(permissionRepository.findAll().stream().map(permission -> PermissionDTO.builder()
                                .id(permission.getId())
                                .name(permission.getName())
                                .build()
                        )
                        .toList()
        );
    }

    public ResponseEntity<?> getUsers(String status, String role, Long academicProgramId, Long facultyId, 
                                       String name, String lastName, String email) {

        List<User> users;

        if (status == null || status.isBlank()) {
            users = userRepository.findAll();
        } else {
            Status userStatus;
            try {
                userStatus = Status.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest()
                        .body("Estado inválido. Use ACTIVE o INACTIVE");
            }
            users = userRepository.findByStatus(userStatus);
        }

        if (role != null && !role.isBlank()) {
            final String roleNameUpper = role.toUpperCase();
            users = users.stream()
                    .filter(user -> user.getRoles().stream()
                            .anyMatch(r -> r.getName().equalsIgnoreCase(roleNameUpper)))
                    .toList();
        }

        // Aplicar filtros de búsqueda por nombre, apellido y email
        if (name != null && !name.isBlank()) {
            final String searchName = name.toLowerCase();
            users = users.stream()
                    .filter(user -> user.getName().toLowerCase().contains(searchName))
                    .toList();
        }

        if (lastName != null && !lastName.isBlank()) {
            final String searchLastName = lastName.toLowerCase();
            users = users.stream()
                    .filter(user -> user.getLastName().toLowerCase().contains(searchLastName))
                    .toList();
        }

        if (email != null && !email.isBlank()) {
            final String searchEmail = email.toLowerCase();
            users = users.stream()
                    .filter(user -> user.getEmail().toLowerCase().contains(searchEmail))
                    .toList();
        }

        List<UserResponse> userResponses = new ArrayList<>();

        for (User user : users) {
            // Obtener todos los perfiles de autoridad del usuario
            Optional<StudentProfile> studentProfile = studentProfileRepository.findByUserId(user.getId());
            
            if (studentProfile.isPresent()) {
                // Si es estudiante, usar su programa académico
                StudentProfile sp = studentProfile.get();
                Long userFacultyId = sp.getFaculty().getId();
                Long userAcademicProgramId = sp.getAcademicProgram().getId();

                // Aplicar filtros
                if (facultyId != null && !userFacultyId.equals(facultyId)) {
                    continue;
                }
                if (academicProgramId != null && !userAcademicProgramId.equals(academicProgramId)) {
                    continue;
                }

                userResponses.add(
                    UserResponse.builder()
                            .id(user.getId())
                            .name(user.getName())
                            .lastname(user.getLastName())
                            .email(user.getEmail())
                            .status(user.getStatus())
                            .roles(
                                    user.getRoles().stream()
                                            .map(Role::getName)
                                            .collect(Collectors.toSet())
                            )
                            .faculty(sp.getFaculty().getName())
                            .academicProgram(sp.getAcademicProgram().getName())
                            .createdDate(user.getCreationDate())
                            .build()
                );
            } else {
                // Si no es estudiante, usar sus ProgramAuthority
                List<ProgramAuthority> authorities = programAuthorityRepository.findByUser_Id(user.getId());
                
                if (authorities.isEmpty()) {
                    // Usuario sin perfil de estudiante ni autoridades: mostrar sin facultad/programa
                    if (facultyId == null && academicProgramId == null) {
                        userResponses.add(
                            UserResponse.builder()
                                    .id(user.getId())
                                    .name(user.getName())
                                    .lastname(user.getLastName())
                                    .email(user.getEmail())
                                    .status(user.getStatus())
                                    .roles(
                                            user.getRoles().stream()
                                                    .map(Role::getName)
                                                    .collect(Collectors.toSet())
                                    )
                                    .faculty(null)
                                    .academicProgram(null)
                                    .createdDate(user.getCreationDate())
                                    .build()
                        );
                    }
                } else {
                    // Crear un UserResponse para CADA autoridad que coincida con los filtros
                    for (ProgramAuthority authority : authorities) {
                        Long userFacultyId = authority.getAcademicProgram().getFaculty().getId();
                        Long userAcademicProgramId = authority.getAcademicProgram().getId();

                        // Aplicar filtros
                        if (facultyId != null && !userFacultyId.equals(facultyId)) {
                            continue;
                        }
                        if (academicProgramId != null && !userAcademicProgramId.equals(academicProgramId)) {
                            continue;
                        }

                        userResponses.add(
                            UserResponse.builder()
                                    .id(user.getId())
                                    .name(user.getName())
                                    .lastname(user.getLastName())
                                    .email(user.getEmail())
                                    .status(user.getStatus())
                                    .roles(
                                            user.getRoles().stream()
                                                    .map(Role::getName)
                                                    .collect(Collectors.toSet())
                                    )
                                    .faculty(authority.getAcademicProgram().getFaculty().getName())
                                    .academicProgram(authority.getAcademicProgram().getName())
                                    .createdDate(user.getCreationDate())
                                    .build()
                        );
                    }
                }
            }
        }

        return ResponseEntity.ok(userResponses);
    }

    public ResponseEntity<?> desactiveUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (user.getStatus() == Status.INACTIVE) {
            return ResponseEntity.badRequest().body("El usuario ya está inactivo.");
        }

        user.setStatus(Status.INACTIVE);
        user.setLastUpdateDate(LocalDateTime.now());
        userRepository.save(user);

        return ResponseEntity.ok("Usuario desactivado correctamente.");
    }

    @Transactional
    public ProgramAuthority assignProgramHead(assignAuthorityProgram request){

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        AcademicProgram program = academicProgramRepository.findById(request.getAcademicProgramId())
                .orElseThrow(() -> new RuntimeException("Programa académico no encontrado"));

        Role programHeadRole = roleRepository.findByName("PROGRAM_HEAD")
                .orElseThrow(() -> new RuntimeException("Rol PROGRAM_HEAD no encontrado"));

        if (!user.getRoles().contains(programHeadRole)) {
            user.getRoles().add(programHeadRole);
            userRepository.save(user);
        }

        ProgramAuthority authority = ProgramAuthority.builder()
                .user(user)
                .academicProgram(program)
                .role(ProgramRole.PROGRAM_HEAD)
                .build();

        programAuthorityRepository.save(authority);
        return authority;
    }

    @Transactional
    public ProgramAuthority assignProjectDirector(assignAuthorityProgram request){

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        AcademicProgram program = academicProgramRepository.findById(request.getAcademicProgramId())
                .orElseThrow(() -> new RuntimeException("Programa académico no encontrado"));

        Role projectDirector = roleRepository.findByName("PROJECT_DIRECTOR")
                .orElseThrow(() -> new RuntimeException("Rol PROJECT_DIRECTOR no encontrado"));

        if (!user.getRoles().contains(projectDirector)) {
            user.getRoles().add(projectDirector);
            userRepository.save(user);
        }

        ProgramAuthority authority = ProgramAuthority.builder()
                .user(user)
                .academicProgram(program)
                .role(ProgramRole.PROJECT_DIRECTOR)
                .build();

        programAuthorityRepository.save(authority);
        return authority;
    }

    @Transactional
    public ProgramAuthority assignCommittee(assignAuthorityProgram request){

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        AcademicProgram program = academicProgramRepository.findById(request.getAcademicProgramId())
                .orElseThrow(() -> new RuntimeException("Programa académico no encontrado"));

        Role committee = roleRepository.findByName("PROGRAM_CURRICULUM_COMMITTEE")
                .orElseThrow(() -> new RuntimeException("Rol PROGRAM_CURRICULUM_COMMITTEE no encontrado"));

        if (!user.getRoles().contains(committee)) {
            user.getRoles().add(committee);
            userRepository.save(user);
        }

        ProgramAuthority authority = ProgramAuthority.builder()
                .user(user)
                .academicProgram(program)
                .role(ProgramRole.PROGRAM_CURRICULUM_COMMITTEE)
                .build();

        programAuthorityRepository.save(authority);
        return authority;
    }

    @Transactional
    public ProgramAuthority assignExaminer(assignAuthorityProgram request){

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        AcademicProgram program = academicProgramRepository.findById(request.getAcademicProgramId())
                .orElseThrow(() -> new RuntimeException("Programa académico no encontrado"));

        Role examiner = roleRepository.findByName("EXAMINER")
                .orElseThrow(() -> new RuntimeException("Rol EXAMINER no encontrado"));

        // Asegurar que el usuario tenga el rol EXAMINER
        if (!user.getRoles().contains(examiner)) {
            user.getRoles().add(examiner);
            userRepository.save(user);
        }

        // Permitir que el usuario sea jurado en múltiples programas
        boolean alreadyAssigned = programAuthorityRepository
                .existsByUser_IdAndAcademicProgram_IdAndRole(user.getId(), program.getId(), ProgramRole.EXAMINER);
        if (alreadyAssigned) {
            throw new RuntimeException("El jurado ya está asociado a este programa académico");
        }

        ProgramAuthority authority = ProgramAuthority.builder()
                .user(user)
                .academicProgram(program)
                .role(ProgramRole.EXAMINER)
                .build();

        programAuthorityRepository.save(authority);
        return authority;
    }

    /**
     * Asocia un jurado (EXAMINER) existente a un programa académico adicional.
     * Un jurado puede estar vinculado a múltiples programas académicos.
     */
    @Transactional
    public ResponseEntity<?> assignExaminerToAdditionalProgram(assignAuthorityProgram request) {

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        boolean hasExaminerRole = user.getRoles().stream()
                .anyMatch(r -> r.getName().equals("EXAMINER"));
        if (!hasExaminerRole) {
            return ResponseEntity.badRequest().body(
                    Map.of("success", false,
                           "message", "El usuario no tiene el rol EXAMINER")
            );
        }

        AcademicProgram program = academicProgramRepository.findById(request.getAcademicProgramId())
                .orElseThrow(() -> new RuntimeException("Programa académico no encontrado"));

        boolean alreadyAssigned = programAuthorityRepository
                .existsByUser_IdAndAcademicProgram_IdAndRole(user.getId(), program.getId(), ProgramRole.EXAMINER);
        if (alreadyAssigned) {
            return ResponseEntity.badRequest().body(
                    Map.of("success", false,
                           "message", "El jurado ya está asociado al programa: " + program.getName())
            );
        }

        ProgramAuthority authority = ProgramAuthority.builder()
                .user(user)
                .academicProgram(program)
                .role(ProgramRole.EXAMINER)
                .build();

        programAuthorityRepository.save(authority);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Jurado vinculado correctamente al programa: " + program.getName(),
                "examinerName", user.getName() + " " + user.getLastName(),
                "programName", program.getName()
        ));
    }

    /**
     * Desvincula un jurado (EXAMINER) de un programa académico específico.
     */
    @Transactional
    public ResponseEntity<?> removeExaminerFromProgram(Long userId, Long academicProgramId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        AcademicProgram program = academicProgramRepository.findById(academicProgramId)
                .orElseThrow(() -> new RuntimeException("Programa académico no encontrado"));

        List<ProgramAuthority> authorities = programAuthorityRepository
                .findByAcademicProgram_IdAndRole(academicProgramId, ProgramRole.EXAMINER)
                .stream()
                .filter(a -> a.getUser().getId().equals(userId))
                .toList();

        if (authorities.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    Map.of("success", false,
                           "message", "El jurado no está asociado al programa: " + program.getName())
            );
        }

        programAuthorityRepository.deleteAll(authorities);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Jurado desvinculado correctamente del programa: " + program.getName(),
                "examinerName", user.getName() + " " + user.getLastName(),
                "programName", program.getName()
        ));
    }

    /**
     * Asocia un usuario con el rol EXAMINER a múltiples programas académicos en una sola operación.
     * Si el usuario aún no tiene el rol EXAMINER, se lo asigna automáticamente.
     * Los programas donde ya esté asociado se omiten (no generan error).
     */
    @Transactional
    public ResponseEntity<?> assignExaminerToMultiplePrograms(AssignExaminerMultipleProgramsRequest request) {

        if (request.getUserId() == null) {
            return ResponseEntity.badRequest().body(
                    Map.of("success", false, "message", "El ID del usuario es obligatorio")
            );
        }

        if (request.getAcademicProgramIds() == null || request.getAcademicProgramIds().isEmpty()) {
            return ResponseEntity.badRequest().body(
                    Map.of("success", false, "message", "Debe proporcionar al menos un ID de programa académico")
            );
        }

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Role examinerRole = roleRepository.findByName("EXAMINER")
                .orElseThrow(() -> new RuntimeException("Rol EXAMINER no encontrado"));

        // Asignar el rol EXAMINER si el usuario aún no lo tiene
        if (user.getRoles().stream().noneMatch(r -> r.getName().equals("EXAMINER"))) {
            user.getRoles().add(examinerRole);
            userRepository.save(user);
        }

        List<Map<String, Object>> assigned = new ArrayList<>();
        List<Map<String, Object>> skipped = new ArrayList<>();

        for (Long programId : request.getAcademicProgramIds()) {

            AcademicProgram program = academicProgramRepository.findById(programId)
                    .orElse(null);

            if (program == null) {
                skipped.add(Map.of(
                        "academicProgramId", programId,
                        "reason", "Programa académico no encontrado"
                ));
                continue;
            }

            boolean alreadyAssigned = programAuthorityRepository
                    .existsByUser_IdAndAcademicProgram_IdAndRole(user.getId(), programId, ProgramRole.EXAMINER);

            if (alreadyAssigned) {
                skipped.add(Map.of(
                        "academicProgramId", programId,
                        "academicProgramName", program.getName(),
                        "reason", "El jurado ya estaba asociado a este programa"
                ));
                continue;
            }

            ProgramAuthority authority = ProgramAuthority.builder()
                    .user(user)
                    .academicProgram(program)
                    .role(ProgramRole.EXAMINER)
                    .build();

            programAuthorityRepository.save(authority);

            assigned.add(Map.of(
                    "academicProgramId", program.getId(),
                    "academicProgramName", program.getName(),
                    "facultyName", program.getFaculty().getName()
            ));
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "examinerId", user.getId(),
                "examinerName", user.getName() + " " + user.getLastName(),
                "examinerEmail", user.getEmail(),
                "programsAssigned", assigned,
                "programsSkipped", skipped,
                "totalAssigned", assigned.size(),
                "totalSkipped", skipped.size()
        ));
    }

    /**
     * Retorna todos los programas académicos a los que está asociado un jurado.
     */
    public ResponseEntity<?> getExaminerPrograms(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        boolean hasExaminerRole = user.getRoles().stream()
                .anyMatch(r -> r.getName().equals("EXAMINER"));
        if (!hasExaminerRole) {
            return ResponseEntity.badRequest().body(
                    Map.of("success", false,
                           "message", "El usuario no tiene el rol EXAMINER")
            );
        }

        List<ProgramAuthority> authorities = programAuthorityRepository
                .findByUser_IdAndRole(userId, ProgramRole.EXAMINER);

        List<Map<String, Object>> programs = authorities.stream()
                .map(a -> Map.<String, Object>of(
                        "programAuthorityId", a.getId(),
                        "academicProgramId", a.getAcademicProgram().getId(),
                        "academicProgramName", a.getAcademicProgram().getName(),
                        "facultyId", a.getAcademicProgram().getFaculty().getId(),
                        "facultyName", a.getAcademicProgram().getFaculty().getName()
                ))
                .toList();

        return ResponseEntity.ok(Map.of(
                "success", true,
                "examinerId", user.getId(),
                "examinerName", user.getName() + " " + user.getLastName(),
                "examinerEmail", user.getEmail(),
                "programs", programs
        ));
    }

    public ResponseEntity<List<ModalityDTO>> getModalities(ModalityStatus status) {

        List<DegreeModality> modalities;

        if (status != null) {
            modalities = degreeModalityRepository.findByStatus(status);
        } else {
            modalities = degreeModalityRepository.findAll();
        }

        List<ModalityDTO> response = modalities.stream()
                .map(mod -> ModalityDTO.builder()
                        .id(mod.getId())
                        .name(mod.getName())
                        .description(mod.getDescription())
                        .status(mod.getStatus())


                        .facultyId(mod.getFaculty().getId())
                        .facultyName(mod.getFaculty().getName())


                        .requirements(
                                modalityRequirementsRepository.findByModalityId(mod.getId())
                                        .stream()
                                        .map(req -> RequirementDTO.builder()
                                                .id(req.getId())
                                                .requirementName(req.getRequirementName())
                                                .description(req.getDescription())
                                                .ruleType(req.getRuleType())
                                                .expectedValue(req.getExpectedValue())
                                                .active(req.isActive())
                                                .build())
                                        .toList()
                        )


                        .documents(
                                requiredDocumentRepository.findByModalityId(mod.getId())
                                        .stream()
                                        .map(doc -> RequiredDocumentDTO.builder()
                                                .id(doc.getId())
                                                .modalityId( doc.getModality().getId())
                                                .documentName(doc.getDocumentName())
                                                .description(doc.getDescription())
                                                .allowedFormat(doc.getAllowedFormat())
                                                .maxFileSizeMB(doc.getMaxFileSizeMB())
                                                .documentType(doc.getDocumentType())
                                                .active(doc.isActive())
                                                .requiresProposalEvaluation(doc.isRequiresProposalEvaluation())
                                                .build())
                                        .toList()
                        )

                        .build()
                )
                .toList();

        return ResponseEntity.ok(response);
    }

    @Transactional
    public ResponseEntity<?> registerUserByAdmin(RegisterUserByAdminRequest request) {

        if (request.getName() == null || request.getName().isBlank() ||
                request.getLastName() == null || request.getLastName().isBlank() ||
                request.getEmail() == null || request.getEmail().isBlank() ||
                request.getPassword() == null || request.getPassword().isBlank() ||
                request.getRoleName() == null || request.getRoleName().isBlank()) {

            return ResponseEntity.badRequest()
                    .body("Todos los campos son obligatorios (nombre, apellido, correo, contraseña y rol)");
        }

        String email = request.getEmail().trim().toLowerCase();

        if (!email.endsWith("@usco.edu.co")) {
            return ResponseEntity.badRequest()
                    .body("El correo debe ser institucional con dominio @usco.edu.co");
        }

        if (userRepository.existsByEmail(email)) {
            return ResponseEntity.badRequest()
                    .body("Este correo ya está registrado en el sistema");
        }

        String roleName = request.getRoleName().toUpperCase();
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new RuntimeException("El rol " + roleName + " no existe en el sistema"));

        boolean requiresProgram = roleName.equals("PROGRAM_HEAD") ||
                roleName.equals("PROJECT_DIRECTOR") ||
                roleName.equals("PROGRAM_CURRICULUM_COMMITTEE");

        boolean isExaminer = roleName.equals("EXAMINER");

        if (requiresProgram && request.getAcademicProgramId() == null) {
            return ResponseEntity.badRequest()
                    .body("El rol " + roleName + " requiere que se especifique un programa académico");
        }

        // Crear y guardar el usuario
        User user = User.builder()
                .name(request.getName())
                .lastName(request.getLastName())
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .roles(Set.of(role))
                .status(Status.ACTIVE)
                .creationDate(LocalDateTime.now())
                .lastUpdateDate(LocalDateTime.now())
                .build();

        userRepository.save(user);

        // ── EXAMINER: asociar a múltiples programas ──────────────────────────
        if (isExaminer) {
            List<Long> programIds = request.getAcademicProgramIds();

            // Compatibilidad: si no envían la lista pero sí el id singular, usarlo
            if ((programIds == null || programIds.isEmpty()) && request.getAcademicProgramId() != null) {
                programIds = List.of(request.getAcademicProgramId());
            }

            if (programIds == null || programIds.isEmpty()) {
                // Sin programas: el jurado se registra sin asociación de programa
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "Usuario registrado exitosamente con el rol EXAMINER sin programas asociados. " +
                                   "Puede asociarlo a programas académicos posteriormente.",
                        "userId", user.getId(),
                        "examinerName", user.getName() + " " + user.getLastName()
                ));
            }

            List<Map<String, Object>> assigned = new ArrayList<>();
            List<Map<String, Object>> skipped  = new ArrayList<>();

            for (Long programId : programIds) {
                AcademicProgram program = academicProgramRepository.findById(programId).orElse(null);

                if (program == null) {
                    skipped.add(Map.of(
                            "academicProgramId", programId,
                            "reason", "Programa académico no encontrado"
                    ));
                    continue;
                }

                boolean alreadyAssigned = programAuthorityRepository
                        .existsByUser_IdAndAcademicProgram_IdAndRole(user.getId(), programId, ProgramRole.EXAMINER);

                if (alreadyAssigned) {
                    skipped.add(Map.of(
                            "academicProgramId", programId,
                            "academicProgramName", program.getName(),
                            "reason", "El jurado ya estaba asociado a este programa"
                    ));
                    continue;
                }

                ProgramAuthority authority = ProgramAuthority.builder()
                        .user(user)
                        .academicProgram(program)
                        .role(ProgramRole.EXAMINER)
                        .build();

                programAuthorityRepository.save(authority);

                assigned.add(Map.of(
                        "academicProgramId", program.getId(),
                        "academicProgramName", program.getName(),
                        "facultyName", program.getFaculty().getName()
                ));
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Usuario registrado exitosamente con el rol EXAMINER",
                    "userId", user.getId(),
                    "examinerName", user.getName() + " " + user.getLastName(),
                    "examinerEmail", user.getEmail(),
                    "programsAssigned", assigned,
                    "programsSkipped", skipped,
                    "totalAssigned", assigned.size(),
                    "totalSkipped", skipped.size()
            ));
        }

        // ── Otros roles: un solo programa obligatorio ────────────────────────
        if (requiresProgram) {
            AcademicProgram program = academicProgramRepository.findById(request.getAcademicProgramId())
                    .orElseThrow(() -> new RuntimeException("Programa académico no encontrado"));

            ProgramRole programRole = switch (roleName) {
                case "PROGRAM_HEAD"                   -> ProgramRole.PROGRAM_HEAD;
                case "PROJECT_DIRECTOR"               -> ProgramRole.PROJECT_DIRECTOR;
                case "PROGRAM_CURRICULUM_COMMITTEE"   -> ProgramRole.PROGRAM_CURRICULUM_COMMITTEE;
                default -> throw new RuntimeException("Rol de programa no válido");
            };

            ProgramAuthority authority = ProgramAuthority.builder()
                    .user(user)
                    .academicProgram(program)
                    .role(programRole)
                    .build();

            programAuthorityRepository.save(authority);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Usuario registrado exitosamente con el rol " + roleName +
                               " y asignado al programa académico: " + program.getName(),
                    "userId", user.getId()
            ));
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Usuario registrado exitosamente con el rol " + roleName,
                "userId", user.getId()
        ));
    }

}
