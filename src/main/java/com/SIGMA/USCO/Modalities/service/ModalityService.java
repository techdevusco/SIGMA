package com.SIGMA.USCO.Modalities.service;

import com.SIGMA.USCO.Modalities.Entity.*;
import com.SIGMA.USCO.Modalities.Entity.enums.*;
import com.SIGMA.USCO.Modalities.Repository.*;
import com.SIGMA.USCO.Modalities.dto.*;
import com.SIGMA.USCO.Modalities.dto.response.*;
import com.SIGMA.USCO.Users.Entity.*;
import com.SIGMA.USCO.Users.Entity.enums.ProgramRole;
import com.SIGMA.USCO.Users.repository.ProgramAuthorityRepository;
import com.SIGMA.USCO.Users.repository.UserRepository;
import com.SIGMA.USCO.academic.entity.*;
import com.SIGMA.USCO.academic.repository.*;
import com.SIGMA.USCO.documents.dto.*;
import com.SIGMA.USCO.documents.entity.*;
import com.SIGMA.USCO.documents.entity.enums.DocumentEditRequestStatus;
import com.SIGMA.USCO.documents.entity.enums.FinalDocumentRubricType;
import com.SIGMA.USCO.documents.entity.enums.DocumentStatus;
import com.SIGMA.USCO.documents.entity.enums.DocumentType;
import com.SIGMA.USCO.documents.entity.enums.EditRequestVoteDecision;
import com.SIGMA.USCO.documents.entity.enums.ExaminerDocumentDecision;
import com.SIGMA.USCO.documents.repository.*;
import com.SIGMA.USCO.notifications.entity.enums.NotificationRecipientType;
import com.SIGMA.USCO.notifications.event.*;
import com.SIGMA.USCO.notifications.listeners.ExaminerNotificationListener;
import com.SIGMA.USCO.notifications.publisher.NotificationEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import com.SIGMA.USCO.notifications.repository.NotificationRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.text.Normalizer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModalityService {

    private final DegreeModalityRepository degreeModalityRepository;
    private final ModalityRequirementsRepository modalityRequirementsRepository;
    private final RequiredDocumentRepository requiredDocumentRepository;
    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final StudentModalityRepository studentModalityRepository;
    private final StudentModalityMemberRepository studentModalityMemberRepository;
    private final StudentDocumentRepository studentDocumentRepository;
    private final ModalityProcessStatusHistoryRepository historyRepository;
    private final StudentDocumentStatusHistoryRepository documentHistoryRepository;
    private final NotificationEventPublisher notificationEventPublisher;
    private final FacultyRepository facultyRepository;
    private final ProgramDegreeModalityRepository programDegreeModalityRepository;
    private final ProgramAuthorityRepository programAuthorityRepository;
    private final DefenseExaminerRepository defenseExaminerRepository;
    private final DefenseEvaluationCriteriaRepository defenseEvaluationCriteriaRepository;
    private final SeminarRepository seminarRepository;
    private final ExaminerNotificationListener examinerNotificationListener;
    private final NotificationRepository notificationRepository;
    private final ProposalEvaluationRepository proposalEvaluationRepository;
    private final FinalDocumentEvaluationRepository secondaryDocumentEvaluationRepository;
    private final ExaminerDocumentReviewRepository examinerDocumentReviewRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final DocumentEditRequestRepository documentEditRequestRepository;
    private final DocumentEditRequestVoteRepository documentEditRequestVoteRepository;
    private final AcademicHistoryPdfRepository academicHistoryPdfRepository;


    @Value("${file.upload-dir}")
    private String uploadDir;


    public DegreeModality createModality(ModalityDTO request) {

        if (request.getName() == null || request.getName().isBlank()) {
            throw new IllegalArgumentException("El nombre de la modalidad es obligatorio.");
        }

        if (request.getFacultyId() == null) {
            throw new IllegalArgumentException("La facultad es obligatoria.");
        }

        Faculty faculty = facultyRepository.findById(request.getFacultyId())
                .orElseThrow(() ->
                        new IllegalArgumentException("La facultad no existe.")
                );

        if (degreeModalityRepository.existsByNameIgnoreCaseAndFacultyId(request.getName(), faculty.getId())) {
            throw new IllegalArgumentException("Ya existe una modalidad con ese nombre en esta facultad.");
        }

        DegreeModality modality = DegreeModality.builder()
                .name(request.getName().toUpperCase())
                .description(request.getDescription())
                .status(ModalityStatus.ACTIVE)
                .faculty(faculty)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return degreeModalityRepository.save(modality);
    }


    public ResponseEntity<?> updateModality(Long modalityId, ModalityDTO request) {

        if (!degreeModalityRepository.existsById(modalityId)) {
            return ResponseEntity.badRequest().body("La modalidad con ID " + modalityId + " no existe.");
        }

        DegreeModality modality = degreeModalityRepository.findById(modalityId).orElseThrow();

        Faculty faculty = facultyRepository.findById(request.getFacultyId())
                .orElseThrow(() ->
                        new IllegalArgumentException("La facultad no existe.")
                );

        modality.setFaculty(faculty);
        modality.setName(request.getName());
        modality.setDescription(request.getDescription());
        modality.setStatus(request.getStatus());
        modality.setUpdatedAt(LocalDateTime.now());

        degreeModalityRepository.save(modality);

        return ResponseEntity.ok("Modalidad actualizada exitosamente");
    }
    public ResponseEntity<?> desactiveModality(Long modalityId) {

        if (!degreeModalityRepository.existsById(modalityId)) {
            return ResponseEntity.badRequest().body("La modalidad con ID " + modalityId + " no existe.");
        }

        DegreeModality modality = degreeModalityRepository.findById(modalityId).orElseThrow();

        modality.setStatus(ModalityStatus.INACTIVE);
        modality.setUpdatedAt(LocalDateTime.now());

        degreeModalityRepository.save(modality);

        return ResponseEntity.ok("Modalidad desactivada exitosamente");
    }
    public void createModalityRequirements(Long modalityId, List<RequirementDTO> requirements) {

        if (requirements == null || requirements.isEmpty()) {
            throw new IllegalArgumentException("La lista de requisitos no puede estar vacía.");
        }

        DegreeModality modality = degreeModalityRepository.findById(modalityId)
                .orElseThrow(() -> new IllegalArgumentException("La modalidad con ID " + modalityId + " no existe."));

        for (RequirementDTO req : requirements) {

            if (req.getRequirementName() == null || req.getRequirementName().isBlank()) {throw new IllegalArgumentException("El nombre del requisito es obligatorio.");}

            if (req.getRuleType() == null) {
                throw new IllegalArgumentException("El tipo de regla es obligatorio para el requisito: " + req.getRequirementName());
            }

            if (req.getExpectedValue() == null || req.getExpectedValue().isBlank()) {
                throw new IllegalArgumentException("El valor esperado es obligatorio para el requisito: " + req.getRequirementName());
            }

            ModalityRequirements requirement = ModalityRequirements.builder()
                    .modality(modality)
                    .requirementName(req.getRequirementName())
                    .description(req.getDescription())
                    .ruleType(req.getRuleType())
                    .expectedValue(req.getExpectedValue())
                    .active(true)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            modalityRequirementsRepository.save(requirement);
        }
    }



    public void updateModalityRequirement(Long modalityId, Long requirementId, RequirementDTO req) {

        DegreeModality modality = degreeModalityRepository.findById(modalityId)
                .orElseThrow(() ->
                        new IllegalArgumentException("La modalidad con ID " + modalityId + " no existe.")
                );

        ModalityRequirements requirement = modalityRequirementsRepository.findById(requirementId)
                .orElseThrow(() ->
                        new IllegalArgumentException("El requisito con ID " + requirementId + " no existe.")
                );

        if (!requirement.getModality().getId().equals(modality.getId())) {
            throw new IllegalArgumentException(
                    "El requisito no pertenece a la modalidad indicada."
            );
        }



        if (req.getRequirementName() != null) {
            if (req.getRequirementName().isBlank()) {
                throw new IllegalArgumentException("El nombre del requisito no puede estar vacío.");
            }
            requirement.setRequirementName(req.getRequirementName());
        }

        if (req.getDescription() != null) {
            requirement.setDescription(req.getDescription());
        }

        if (req.getRuleType() != null) {
            requirement.setRuleType(req.getRuleType());
        }

        if (req.getExpectedValue() != null) {
            if (req.getExpectedValue().isBlank()) {
                throw new IllegalArgumentException("El valor esperado no puede estar vacío.");
            }
            requirement.setExpectedValue(req.getExpectedValue());
        }



        requirement.setUpdatedAt(LocalDateTime.now());

        modalityRequirementsRepository.save(requirement);
    }

    public ResponseEntity<List<RequirementDTO>> getModalityRequirements(Long modalityId, Boolean active) {

        if (!degreeModalityRepository.existsById(modalityId)) {
            return ResponseEntity.badRequest().body(List.of());
        }

        List<ModalityRequirements> requirements;

        if (active != null) {
            requirements = modalityRequirementsRepository.findByModalityIdAndActive(modalityId, active);
        } else {
            requirements = modalityRequirementsRepository.findByModalityId(modalityId);
        }

        List<RequirementDTO> response = requirements.stream()
                .map(r -> RequirementDTO.builder()
                        .id(r.getId())
                        .requirementName(r.getRequirementName())
                        .description(r.getDescription())
                        .ruleType(r.getRuleType())
                        .expectedValue(r.getExpectedValue())
                        .active(r.isActive())
                        .build())
                .toList();

        return ResponseEntity.ok(response);
    }
    public ResponseEntity<?> deleteRequirement(Long requirementId) {

        ModalityRequirements requirement = modalityRequirementsRepository.findById(requirementId)
                .orElseThrow(() -> new RuntimeException("Requisito no encontrado"));

        requirement.setActive(false);
        requirement.setUpdatedAt(LocalDateTime.now());

        modalityRequirementsRepository.save(requirement);

        return ResponseEntity.ok("Requisito desactivado correctamente");
    }

    public ResponseEntity<?> activeRequirement (Long requirementId){
        ModalityRequirements requirement = modalityRequirementsRepository.findById(requirementId)
                .orElseThrow(() -> new RuntimeException("Requisito no encontrado"));

        requirement.setActive(true);
        requirement.setUpdatedAt(LocalDateTime.now());

        modalityRequirementsRepository.save(requirement);

        return ResponseEntity.ok("Requisito activado correctamente");

    }

    public ResponseEntity<List<ModalityDTO>> getAllModalities() {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        StudentProfile profile = studentProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Perfil académico no encontrado"));

        Long userProgramId = profile.getAcademicProgram().getId();

        List<DegreeModality> modalities = degreeModalityRepository.findByStatus(ModalityStatus.ACTIVE);

        List<ModalityDTO> modalityDTOs = modalities.stream().map(mod -> {

            Optional<ProgramDegreeModality> pdmOpt = programDegreeModalityRepository
                    .findByAcademicProgramIdAndDegreeModalityIdAndActiveTrue(userProgramId, mod.getId());

            Long creditsRequired = null;
            if (pdmOpt.isPresent() && pdmOpt.get().getCreditsRequired() != null) {
                creditsRequired = pdmOpt.get().getCreditsRequired();
            }

            return ModalityDTO.builder()
                    .id(mod.getId())
                    .name(mod.getName())
                    .facultyName(mod.getFaculty().getName())
                    .description(mod.getDescription())
                    .facultyId(mod.getFaculty().getId())
                    .status(mod.getStatus())
                    .requiredCredits(creditsRequired != null ? creditsRequired.doubleValue() : null)
                    .build();

        }).toList();

        return ResponseEntity.ok(modalityDTOs);
    }

    public ResponseEntity<?> getModalityDetail(Long modalityId) {

        if (!degreeModalityRepository.existsById(modalityId)) {
            return ResponseEntity.badRequest().body("La modalidad con ID " + modalityId + " no existe.");
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        StudentProfile profile = studentProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Perfil académico no encontrado"));

        Long userProgramId = profile.getAcademicProgram().getId();


        Optional<ProgramDegreeModality> pdmOpt = programDegreeModalityRepository
                .findByAcademicProgramIdAndDegreeModalityIdAndActiveTrue(userProgramId, modalityId);

        Long creditsRequired = null;
        if (pdmOpt.isPresent() && pdmOpt.get().getCreditsRequired() != null) {
            creditsRequired = pdmOpt.get().getCreditsRequired();
        }

        var requirements = modalityRequirementsRepository.findByModalityIdAndActiveTrue(modalityId)
                .stream()
                .map(req -> RequirementDTO.builder()
                        .id(req.getId())
                        .requirementName(req.getRequirementName())
                        .description(req.getDescription())
                        .expectedValue(req.getExpectedValue())
                        .ruleType(req.getRuleType())
                        .build())
                .toList();

        var documents = requiredDocumentRepository
                .findByModalityIdAndActiveTrueAndDocumentType(modalityId, DocumentType.MANDATORY)
                .stream()
                .map(doc -> RequiredDocumentDTO.builder()
                        .id(doc.getId())
                        .modalityId(modalityId)
                        .documentName(doc.getDocumentName())
                        .description(doc.getDescription())
                        .allowedFormat(doc.getAllowedFormat())
                        .maxFileSizeMB(doc.getMaxFileSizeMB())
                        .documentType(doc.getDocumentType())
                        .build())
                .toList();

        DegreeModality modality = degreeModalityRepository.findById(modalityId).orElseThrow();

        ModalityDTO modalityDetail = ModalityDTO.builder()
                .id(modalityId)
                .name(modality.getName())
                .description(modality.getDescription())
                .facultyId(modality.getFaculty().getId())
                .facultyName(modality.getFaculty().getName())
                .requiredCredits(creditsRequired != null ? creditsRequired.doubleValue() : null)
                .requirements(requirements)
                .documents(documents)
                .build();

        return ResponseEntity.ok(modalityDetail);

    }

    @Transactional
    public ResponseEntity<?> startStudentModalityIndividual(Long modalityId) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User student = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        StudentProfile profile = studentProfileRepository.findByUserId(student.getId())
                .orElseThrow(() -> new RuntimeException("Debe completar su perfil académico antes de seleccionar una modalidad"));


        DegreeModality modality = degreeModalityRepository.findById(modalityId)
                .orElseThrow(() -> new RuntimeException("La modalidad con ID " + modalityId + " no existe"));


        ProgramDegreeModality programDegreeModality =
                programDegreeModalityRepository.findByAcademicProgramIdAndDegreeModalityIdAndActiveTrue(profile.getAcademicProgram().getId(), modalityId)
                        .orElseThrow(() -> new RuntimeException("La modalidad no está habilitada para tu programa académico"));



        // Verificar si el estudiante tiene modalidades activas (en proceso)
        // CORRECTIONS_REJECTED_FINAL también es un estado finalizado que permite iniciar nueva modalidad

        List<ModalityProcessStatus> finalizedStatuses = List.of(
                ModalityProcessStatus.MODALITY_CLOSED,
                ModalityProcessStatus.MODALITY_CANCELLED,
                ModalityProcessStatus.GRADED_FAILED,
                ModalityProcessStatus.CORRECTIONS_REJECTED_FINAL
        );

        // Obtener todas las modalidades del estudiante como miembro activo
        List<StudentModalityMember> activeMembers = studentModalityMemberRepository.findByStudentIdAndStatus(
                student.getId(),
                MemberStatus.ACTIVE
        );

        // Verificar si alguna de esas modalidades NO está finalizada
        for (StudentModalityMember member : activeMembers) {
            ModalityProcessStatus currentStatus = member.getStudentModality().getStatus();
            if (!finalizedStatuses.contains(currentStatus)) {
                return ResponseEntity.badRequest().body(
                        Map.of(
                                "eligible", false,
                                "message", "Ya tienes una modalidad de grado en curso. No puedes iniciar otra."
                        )
                );
            }
        }

        // Verificar si el estudiante tiene una modalidad CERRADA (MODALITY_CLOSED)
        // Si tiene una modalidad cerrada, NO puede volver a iniciar la MISMA modalidad
        List<StudentModality> closedModalities = studentModalityRepository.findByLeaderIdAndStatus(
                student.getId(),
                ModalityProcessStatus.MODALITY_CLOSED
        );

        for (StudentModality closedModality : closedModalities) {
            if (closedModality.getProgramDegreeModality().getDegreeModality().getId().equals(modalityId)) {
                return ResponseEntity.badRequest().body(
                        Map.of(
                                "eligible", false,
                                "message", "No puedes volver a iniciar esta modalidad porque ya fue cerrada anteriormente. Debes seleccionar una modalidad diferente."
                        )
                );
            }
        }



        List<ModalityRequirements> requirements = modalityRequirementsRepository.findByModalityIdAndActiveTrue(modalityId);

        List<ValidationItemDTO> results = new ArrayList<>();
        boolean allValid = true;

        for (ModalityRequirements req : requirements) {

            if (req.getRuleType() != RuleType.NUMERIC) {
                continue;
            }

            boolean fulfilled = true;
            String studentValue = "";


            if (req.getRequirementName().toLowerCase().contains("crédito")) {

                double percentageRequired = Double.parseDouble(req.getExpectedValue());
                long totalCredits = profile.getAcademicProgram().getTotalCredits();
                long requiredCredits = Math.round(totalCredits * percentageRequired);

                fulfilled = profile.getApprovedCredits() >= requiredCredits;
                studentValue = profile.getApprovedCredits() + " / " + requiredCredits;
            }


            if (req.getRequirementName().toLowerCase().contains("promedio")) {

                fulfilled = profile.getGpa() >= Double.parseDouble(req.getExpectedValue());
                studentValue = String.valueOf(profile.getGpa());
            }

            results.add(
                    ValidationItemDTO.builder()
                            .requirementName(req.getRequirementName())
                            .expectedValue(req.getExpectedValue())
                            .studentValue(studentValue)
                            .fulfilled(fulfilled)
                            .build()
            );

            if (!fulfilled) {
                allValid = false;
            }
        }

        if (!allValid) {
            return ResponseEntity.badRequest().body(
                    ValidationResultDTO.builder()
                            .eligible(false)
                            .results(results)
                            .message("No cumples los requisitos académicos para esta modalidad")
                            .build()
            );
        }



        StudentModality studentModality = StudentModality.builder()
                .leader(student)
                .modalityType(ModalityType.INDIVIDUAL)
                .academicProgram(profile.getAcademicProgram())
                .programDegreeModality(programDegreeModality)
                .status(ModalityProcessStatus.UNDER_REVIEW_PROGRAM_HEAD)
                .selectionDate(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        studentModalityRepository.save(studentModality);


        StudentModalityMember member = StudentModalityMember.builder()
                .studentModality(studentModality)
                .student(student)
                .isLeader(true)
                .status(MemberStatus.ACTIVE)
                .joinedAt(LocalDateTime.now())
                .build();

        studentModalityMemberRepository.save(member);



        historyRepository.save(
                ModalityProcessStatusHistory.builder()
                        .studentModality(studentModality)
                        .status(ModalityProcessStatus.UNDER_REVIEW_PROGRAM_HEAD)
                        .changeDate(LocalDateTime.now())
                        .responsible(student)
                        .observations("Modalidad individual iniciada por el estudiante")
                        .build()
        );



        notificationEventPublisher.publish(
                new StudentModalityStarted(
                        studentModality.getId(),
                        student.getId()
                )
        );

        return ResponseEntity.ok(
                Map.of(
                        "eligible", true,
                        "studentModalityId", studentModality.getId(),
                        "studentModalityName", modality.getName(),
                        "modalityType", "INDIVIDUAL",
                        "message", "Modalidad iniciada correctamente. Puedes subir los documentos."
                )
        );
    }


    public ResponseEntity<?> uploadRequiredDocument(Long studentModalityId, Long requiredDocumentId, MultipartFile file) throws IOException {

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body("El archivo es obligatorio");
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User uploader = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        StudentModality studentModality = studentModalityRepository.findById(studentModalityId)
                .orElseThrow(() -> new RuntimeException("Modalidad del estudiante no encontrada"));

        // Verificar si es miembro activo (estudiante) o si es el director asignado a la modalidad
        boolean isActiveMember = studentModalityMemberRepository.isActiveMember(
                studentModalityId,
                uploader.getId()
        );

        boolean isAssignedDirector = studentModality.getProjectDirector() != null &&
                studentModality.getProjectDirector().getId().equals(uploader.getId());

        if (!isActiveMember && !isAssignedDirector) {
            return ResponseEntity.status(403).body("No autorizado para subir documentos a esta modalidad");
        }

        // Para efectos de trazabilidad, usamos 'uploader' como responsable.
        // Si es el director, el folder de almacenamiento sigue siendo el del estudiante líder.
        User student = isAssignedDirector && !isActiveMember
                ? studentModality.getLeader()
                : uploader;

        RequiredDocument requiredDocument = requiredDocumentRepository.findById(requiredDocumentId)
                .orElseThrow(() -> new RuntimeException("Documento requerido no existe"));

        DegreeModality modality = studentModality.getProgramDegreeModality().getDegreeModality();

        if (!requiredDocument.getModality().getId().equals(modality.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("El documento no pertenece a la modalidad seleccionada");
        }

        // Validación: Los documentos de tipo SECONDARY solo pueden ser subidos por el director del proyecto
        if (requiredDocument.getDocumentType() == DocumentType.SECONDARY && !isAssignedDirector) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "success", false,
                            "error", "Acceso denegado",
                            "message", "Este documento solo puede ser subido por el director del proyecto. Por favor, póngase en contacto con el director " +
                                    (studentModality.getProjectDirector() != null
                                            ? studentModality.getProjectDirector().getName() + " " + studentModality.getProjectDirector().getLastName()
                                            : "asignado")
                    ));
        }

        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase();

        if (requiredDocument.getAllowedFormat() != null &&
                !requiredDocument.getAllowedFormat().toLowerCase().contains(extension)) {
            return ResponseEntity.badRequest().body("Formato de archivo no permitido");
        }

        if (requiredDocument.getMaxFileSizeMB() != null &&
                file.getSize() > requiredDocument.getMaxFileSizeMB() * 1024L * 1024L) {
            return ResponseEntity.badRequest().body("El archivo supera el tamaño permitido");
        }


        String modalityFolder = modality.getName()
                .replaceAll("[^a-zA-Z0-9]", "_");

        String studentFolder = student.getName() + student.getLastName() + "_" +
                student.getLastName() + "_" +
                student.getId();

        Path basePath = Paths.get(
                uploadDir,
                modalityFolder,
                studentFolder
        );

        Files.createDirectories(basePath);

        String finalFileName = UUID.randomUUID() + "_" + originalFilename;
        Path fullPath = basePath.resolve(finalFileName);

        Files.copy(file.getInputStream(), fullPath, StandardCopyOption.REPLACE_EXISTING);


        StudentDocument studentDocument = studentDocumentRepository
                .findByStudentModalityIdAndDocumentConfigId(studentModalityId, requiredDocumentId)
                .orElse(
                        StudentDocument.builder()
                                .studentModality(studentModality)
                                .documentConfig(requiredDocument)
                                .build()
                );

        studentDocument.setFileName(originalFilename);
        studentDocument.setFilePath(fullPath.toString());
        studentDocument.setUploadDate(LocalDateTime.now());

        // ========== LÓGICA DE CORRECCIONES ==========
        // Determinar si se trata de una resubida de correcciones según el estado actual de la modalidad
        ModalityProcessStatus currentModalityStatus = studentModality.getStatus();

        boolean isResubmittingCorrection =
                currentModalityStatus == ModalityProcessStatus.CORRECTIONS_REQUESTED_PROGRAM_HEAD ||
                currentModalityStatus == ModalityProcessStatus.CORRECTIONS_REQUESTED_PROGRAM_CURRICULUM_COMMITTEE ||
                currentModalityStatus == ModalityProcessStatus.CORRECTIONS_REQUESTED_EXAMINERS;

        // ========== RESUBIDA POR EDICIÓN APROBADA ==========
        // Verificar si el documento existente tiene una solicitud de edición aprobada
        boolean isResubmittingApprovedEdit = false;
        StudentDocument existingDoc = studentDocumentRepository
                .findByStudentModalityIdAndDocumentConfigId(studentModalityId, requiredDocumentId)
                .orElse(null);
        if (existingDoc != null && existingDoc.getStatus() == DocumentStatus.EDIT_REQUEST_APPROVED) {
            isResubmittingApprovedEdit = true;
        }

        if (isResubmittingApprovedEdit) {
              // Cerrar la solicitud de edición aprobada (marcar como completada con el reenvío)
            documentEditRequestRepository
                    .findTopByStudentDocumentIdAndStatusOrderByCreatedAtDesc(
                            existingDoc.getId(), DocumentEditRequestStatus.APPROVED)
                    .ifPresent(req -> {
                        // Los votos ya están registrados; solo guardamos la referencia para trazabilidad
                        documentEditRequestRepository.save(req);
                    });

            // El documento vuelve a PENDING para re-revisión por jurados
            studentDocument.setStatus(DocumentStatus.PENDING);
            studentDocument.setFileName(originalFilename);
            studentDocument.setFilePath(fullPath.toString());
            studentDocument.setUploadDate(LocalDateTime.now());
            studentDocumentRepository.save(studentDocument);

            // Limpiar las reviews anteriores de jurados para este documento (ExaminerDocumentReview)
            List<ExaminerDocumentReview> oldReviews = examinerDocumentReviewRepository
                    .findByStudentDocumentId(studentDocument.getId());
            examinerDocumentReviewRepository.deleteAll(oldReviews);

            // Limpiar también los votos de la solicitud de edición aprobada (DocumentEditRequestVote)
            documentEditRequestRepository
                    .findTopByStudentDocumentIdAndStatusOrderByCreatedAtDesc(
                            existingDoc.getId(), DocumentEditRequestStatus.APPROVED)
                    .ifPresent(req -> {
                        List<DocumentEditRequestVote> editVotes = documentEditRequestVoteRepository
                                .findByEditRequestId(req.getId());
                        documentEditRequestVoteRepository.deleteAll(editVotes);
                    });

            // Cambiar el estado de la modalidad a EXAMINERS_ASSIGNED para que los jurados revisen
            studentModality.setStatus(ModalityProcessStatus.EXAMINERS_ASSIGNED);
            studentModality.setUpdatedAt(LocalDateTime.now());
            studentModalityRepository.save(studentModality);

            // Trazabilidad en el historial del DOCUMENTO
            documentHistoryRepository.save(
                    StudentDocumentStatusHistory.builder()
                            .studentDocument(studentDocument)
                            .status(DocumentStatus.PENDING)
                            .changeDate(LocalDateTime.now())
                            .responsible(uploader)
                            .observations((isAssignedDirector && !isActiveMember ? "Director" : "Estudiante") +
                                    " resubió el documento '" +
                                    originalFilename +
                                    "' tras aprobación de solicitud de edición. Pendiente de re-revisión por jurados.")
                            .build()
            );

            // Trazabilidad en el historial de la MODALIDAD
            historyRepository.save(
                    ModalityProcessStatusHistory.builder()
                            .studentModality(studentModality)
                            .status(ModalityProcessStatus.EXAMINERS_ASSIGNED)
                            .changeDate(LocalDateTime.now())
                            .responsible(uploader)
                            .observations((isAssignedDirector && !isActiveMember ? "Director" : "Estudiante") +
                                    " actualizó el documento '" +
                                    studentDocument.getDocumentConfig().getDocumentName() +
                                    "' con los cambios aprobados por los jurados. " +
                                    "La modalidad regresa al estado de revisión por jurados.")
                            .build()
            );

            notificationEventPublisher.publish(
                    new StudentDocumentUpdatedEvent(studentModality.getId(), studentDocument.getId(), uploader.getId())
            );

            return ResponseEntity.ok(Map.of(
                    "message", "Documento actualizado correctamente. Los jurados evaluarán la nueva versión.",
                    "path", fullPath.toString(),
                    "documentStatus", studentDocument.getStatus().name(),
                    "modalityStatus", studentModality.getStatus().name()
            ));

        } else if (isResubmittingCorrection) {
            // Marcar el documento como corrección reenviada
            studentDocument.setStatus(DocumentStatus.CORRECTION_RESUBMITTED);
            studentDocumentRepository.save(studentDocument);

            // Si las correcciones venían de jurados, limpiar SOLO el voto del jurado que solicitó
            // correcciones, conservando el voto ACCEPTED del jurado que ya aprobó
            if (currentModalityStatus == ModalityProcessStatus.CORRECTIONS_REQUESTED_EXAMINERS) {
                List<ExaminerDocumentReview> oldReviews = examinerDocumentReviewRepository
                        .findByStudentDocumentId(studentDocument.getId());
                // Eliminar solo los votos de CORRECTIONS_REQUESTED; los ACCEPTED se conservan
                List<ExaminerDocumentReview> reviewsToDelete = oldReviews.stream()
                        .filter(r -> r.getDecision() == ExaminerDocumentDecision.CORRECTIONS_REQUESTED)
                        .toList();
                examinerDocumentReviewRepository.deleteAll(reviewsToDelete);
            }

            documentHistoryRepository.save(
                    StudentDocumentStatusHistory.builder()
                            .studentDocument(studentDocument)
                            .status(DocumentStatus.CORRECTION_RESUBMITTED)
                            .changeDate(LocalDateTime.now())
                            .responsible(uploader)
                            .observations("Documento corregido reenviado por " +
                                    (isAssignedDirector && !isActiveMember ? "el director" : "el estudiante"))
                            .build()
            );

            // Determinar el nuevo estado de la modalidad según quién solicitó las correcciones
            ModalityProcessStatus newModalityStatusAfterResubmit = switch (currentModalityStatus) {
                case CORRECTIONS_REQUESTED_PROGRAM_HEAD ->
                        ModalityProcessStatus.CORRECTIONS_SUBMITTED_TO_PROGRAM_HEAD;
                case CORRECTIONS_REQUESTED_PROGRAM_CURRICULUM_COMMITTEE ->
                        ModalityProcessStatus.CORRECTIONS_SUBMITTED_TO_COMMITTEE;
                case CORRECTIONS_REQUESTED_EXAMINERS ->
                        ModalityProcessStatus.CORRECTIONS_SUBMITTED_TO_EXAMINERS;
                default -> ModalityProcessStatus.CORRECTIONS_SUBMITTED;
            };

            String requesterLabel = switch (currentModalityStatus) {
                case CORRECTIONS_REQUESTED_PROGRAM_HEAD -> "Jefatura de Programa y/o coordinación de modalidades";
                case CORRECTIONS_REQUESTED_PROGRAM_CURRICULUM_COMMITTEE -> "Comité de Currículo de Programa";
                case CORRECTIONS_REQUESTED_EXAMINERS -> "Jurado evaluador";
                default -> "revisor";
            };

            // Cambiar el estado de la modalidad al estado específico
            studentModality.setStatus(newModalityStatusAfterResubmit);
            studentModality.setUpdatedAt(LocalDateTime.now());
            studentModalityRepository.save(studentModality);

            historyRepository.save(
                    ModalityProcessStatusHistory.builder()
                            .studentModality(studentModality)
                            .status(newModalityStatusAfterResubmit)
                            .changeDate(LocalDateTime.now())
                            .responsible(uploader)
                            .observations("Correcciones enviadas por " +
                                    (isAssignedDirector && !isActiveMember ? "el director" : "el estudiante") +
                                    " tras solicitud de: " + requesterLabel)
                            .build()
            );

            notificationEventPublisher.publish(
                    new CorrectionResubmittedEvent(
                            studentModality.getId(),
                            studentDocument.getId(),
                            uploader.getId(),
                            studentDocument.getDocumentConfig().getDocumentName(),
                            student.getId()
                    )
            );

        } else {
            // Subida normal: estado PENDING
            studentDocument.setStatus(DocumentStatus.PENDING);
            studentDocumentRepository.save(studentDocument);

            documentHistoryRepository.save(
                    StudentDocumentStatusHistory.builder()
                            .studentDocument(studentDocument)
                            .status(DocumentStatus.PENDING)
                            .changeDate(LocalDateTime.now())
                            .responsible(uploader)
                            .observations("Documento cargado o actualizado por " +
                                    (isAssignedDirector && !isActiveMember ? "el director" : "el estudiante"))
                            .build()
            );

            notificationEventPublisher.publish(
                    new StudentDocumentUpdatedEvent(studentModality.getId(), studentDocument.getId(), uploader.getId())
            );

            // ========== VERIFICAR SI TODOS LOS DOCUMENTOS MANDATORY HAN SIDO SUBIDOS ==========
            checkAndUpdateModalityStatusIfAllMandatoryDocsUploaded(studentModality, uploader);
        }

        return ResponseEntity.ok(
                Map.of(
                        "message", isResubmittingCorrection
                                ? "Documento de corrección enviado correctamente. Será revisado por el evaluador correspondiente."
                                : "Documento subido correctamente",
                        "path", fullPath.toString(),
                        "documentStatus", studentDocument.getStatus().name(),
                        "modalityStatus", studentModality.getStatus().name()
                )
        );
    }


    private void checkAndUpdateModalityStatusIfAllMandatoryDocsUploaded(StudentModality studentModality, User responsibleUser) {

        // Solo aplicar esta lógica si la modalidad está en estado MODALITY_SELECTED
        if (studentModality.getStatus() != ModalityProcessStatus.MODALITY_SELECTED) {
            return;
        }

        Long modalityId = studentModality.getProgramDegreeModality().getDegreeModality().getId();

        // Obtener todos los documentos MANDATORY requeridos para esta modalidad
        List<RequiredDocument> mandatoryDocuments = requiredDocumentRepository
                .findByModalityIdAndActiveTrueAndDocumentType(modalityId, DocumentType.MANDATORY);

        if (mandatoryDocuments.isEmpty()) {
            return; // No hay documentos obligatorios configurados
        }

        // Obtener todos los documentos subidos por el estudiante
        List<StudentDocument> uploadedDocuments = studentDocumentRepository
                .findByStudentModalityId(studentModality.getId());

        Set<Long> uploadedDocumentIds = uploadedDocuments.stream()
                .map(doc -> doc.getDocumentConfig().getId())
                .collect(Collectors.toSet());

        // Verificar si TODOS los documentos MANDATORY han sido subidos
        boolean allMandatoryDocsUploaded = mandatoryDocuments.stream()
                .allMatch(doc -> uploadedDocumentIds.contains(doc.getId()));

        if (allMandatoryDocsUploaded) {
            // Cambiar el estado de la modalidad a UNDER_REVIEW_PROGRAM_HEAD
            studentModality.setStatus(ModalityProcessStatus.UNDER_REVIEW_PROGRAM_HEAD);
            studentModality.setUpdatedAt(LocalDateTime.now());
            studentModalityRepository.save(studentModality);

            // Registrar en el historial
            historyRepository.save(
                    ModalityProcessStatusHistory.builder()
                            .studentModality(studentModality)
                            .status(ModalityProcessStatus.UNDER_REVIEW_PROGRAM_HEAD)
                            .changeDate(LocalDateTime.now())
                            .responsible(responsibleUser)
                            .observations("Todos los documentos obligatorios han sido subidos. " +
                                         "La modalidad pasa automáticamente a revisión del jefe de programa.")
                            .build()
            );

            log.info("Modalidad {} cambió automáticamente a UNDER_REVIEW_PROGRAM_HEAD - Todos los documentos MANDATORY subidos",
                    studentModality.getId());
        }
    }
    public ResponseEntity<?> validateAllDocumentsUploaded(Long studentModalityId) {

        StudentModality studentModality = studentModalityRepository.findById(studentModalityId)
                .orElseThrow(() -> new RuntimeException("Modalidad no encontrada"));

        Long modalityId = studentModality.getProgramDegreeModality().getDegreeModality().getId();

        List<RequiredDocument> requiredDocuments =
                requiredDocumentRepository.findByModalityIdAndActiveTrue(modalityId);

        List<StudentDocument> uploadedDocuments =
                studentDocumentRepository.findByStudentModalityId(studentModalityId);

        Set<Long> uploadedIds = uploadedDocuments.stream()
                .map(d -> d.getDocumentConfig().getId())
                .collect(Collectors.toSet());

        List<String> missingDocuments = requiredDocuments.stream()
                .filter(doc -> doc.getDocumentType() == DocumentType.MANDATORY)
                .filter(doc -> !uploadedIds.contains(doc.getId()))
                .map(RequiredDocument::getDocumentName)
                .toList();

        boolean allUploaded = missingDocuments.isEmpty();

        return ResponseEntity.ok(
                Map.of(
                        "canContinue", allUploaded,
                        "missingDocuments", missingDocuments
                )
        );
    }

    public ResponseEntity<?> getAvailableDocumentsForStudent() {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User student = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));


        Optional<StudentModality> studentModalityOpt = studentModalityRepository
                .findTopByStudentIdOrderByUpdatedAtDesc(student.getId());

        if (studentModalityOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "success", false,
                            "message", "No se encontró una modalidad asociada al estudiante"
                    ));
        }

        StudentModality studentModality = studentModalityOpt.get();
        Long studentModalityId = studentModality.getId();
        Long modalityId = studentModality.getProgramDegreeModality().getDegreeModality().getId();


        List<RequiredDocument> mandatoryDocuments = requiredDocumentRepository
                .findByModalityIdAndActiveTrueAndDocumentType(modalityId, DocumentType.MANDATORY);


        List<StudentDocument> uploadedDocuments = studentDocumentRepository
                .findByStudentModalityId(studentModalityId);

        Set<Long> uploadedDocumentIds = uploadedDocuments.stream()
                .map(d -> d.getDocumentConfig().getId())
                .collect(Collectors.toSet());


        List<String> missingMandatoryDocs = mandatoryDocuments.stream()
                .filter(doc -> !uploadedDocumentIds.contains(doc.getId()))
                .map(RequiredDocument::getDocumentName)
                .toList();

        if (!missingMandatoryDocs.isEmpty()) {


            List<RequiredDocument> mandatoryOnly = mandatoryDocuments;

            List<Map<String, Object>> documentList = mandatoryOnly.stream()
                    .map(requiredDoc -> {
                        Map<String, Object> docInfo = new HashMap<>();
                        docInfo.put("requiredDocumentId", requiredDoc.getId());
                        docInfo.put("documentName", requiredDoc.getDocumentName());
                        docInfo.put("description", requiredDoc.getDescription());
                        docInfo.put("documentType", requiredDoc.getDocumentType());
                        docInfo.put("allowedFormat", requiredDoc.getAllowedFormat());
                        docInfo.put("maxFileSizeMB", requiredDoc.getMaxFileSizeMB());
                        docInfo.put("uploaded", false);
                        return docInfo;
                    })
                    .toList();

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "studentModalityId", studentModalityId,
                    "documents", documentList,
                    "statistics", Map.of(
                            "totalDocuments", documentList.size(),
                            "uploadedDocuments", 0,
                            "pendingDocuments", documentList.size(),
                            "mandatoryDocuments", documentList.size(),
                            "secondaryDocuments", 0
                    )
            ));
        }



        List<RequiredDocument> allDocuments = requiredDocumentRepository
                .findByModalityIdAndActiveTrueAndDocumentTypeIn(
                        modalityId,
                        List.of(DocumentType.MANDATORY, DocumentType.SECONDARY)
                );


        Map<Long, StudentDocument> uploadedMap = uploadedDocuments.stream()
                .collect(Collectors.toMap(
                        d -> d.getDocumentConfig().getId(),
                        d -> d
                ));


        List<Map<String, Object>> documentList = allDocuments.stream()
                .map(requiredDoc -> {
                    StudentDocument uploaded = uploadedMap.get(requiredDoc.getId());

                    Map<String, Object> docInfo = new HashMap<>();
                    docInfo.put("requiredDocumentId", requiredDoc.getId());
                    docInfo.put("documentName", requiredDoc.getDocumentName());
                    docInfo.put("description", requiredDoc.getDescription());
                    docInfo.put("documentType", requiredDoc.getDocumentType());
                    docInfo.put("allowedFormat", requiredDoc.getAllowedFormat());
                    docInfo.put("maxFileSizeMB", requiredDoc.getMaxFileSizeMB());
                    docInfo.put("uploaded", uploaded != null);

                    if (uploaded != null) {
                        docInfo.put("studentDocumentId", uploaded.getId());
                        docInfo.put("fileName", uploaded.getFileName());
                        docInfo.put("status", uploaded.getStatus());
                        docInfo.put("notes", uploaded.getNotes());
                        docInfo.put("uploadDate", uploaded.getUploadDate());
                    }

                    return docInfo;
                })
                .toList();

        long totalDocuments = documentList.size();
        long uploadedCount = documentList.stream()
                .filter(doc -> (Boolean) doc.get("uploaded"))
                .count();
        long mandatoryCount = documentList.stream()
                .filter(doc -> doc.get("documentType") == DocumentType.MANDATORY)
                .count();
        long secondaryCount = documentList.stream()
                .filter(doc -> doc.get("documentType") == DocumentType.SECONDARY)
                .count();

        return ResponseEntity.ok(Map.of(
                "success", true,
                "studentModalityId", studentModalityId,
                "documents", documentList,
                "statistics", Map.of(
                        "totalDocuments", totalDocuments,
                        "uploadedDocuments", uploadedCount,
                        "pendingDocuments", totalDocuments - uploadedCount,
                        "mandatoryDocuments", mandatoryCount,
                        "secondaryDocuments", secondaryCount
                )
        ));
    }

    public ResponseEntity<?> getStudentDocuments(Long studentModalityId) {

        StudentModality studentModality = studentModalityRepository
                .findById(studentModalityId)
                .orElseThrow(() ->
                        new RuntimeException("Modalidad del estudiante no encontrada")
                );

        List<StudentDocument> documents =
                studentDocumentRepository.findByStudentModalityId(studentModalityId);

        List<Map<String, Object>> response = documents.stream()
                .map(doc -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("studentDocumentId", doc.getId());
                    map.put("documentName", doc.getDocumentConfig().getDocumentName());
                    map.put("documentType", doc.getDocumentConfig().getDocumentType());
                    map.put("status", doc.getStatus());
                    map.put("notes", doc.getNotes());
                    map.put("uploadedAt", doc.getUploadDate());
                    map.put("filePath", doc.getFilePath());
                    return map;
                })
                .toList();

        return ResponseEntity.ok(response);
    }
    public ResponseEntity<?> viewStudentDocument(Long studentDocumentId) throws MalformedURLException {

        StudentDocument doc = studentDocumentRepository.findById(studentDocumentId)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        Path path = Paths.get(doc.getFilePath());
        if (!Files.exists(path)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("File not found on server");
        }

        UrlResource resource = new UrlResource(path.toUri());

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + doc.getFileName() + "\"")
                .body(resource);

    }
    public ResponseEntity<?> reviewStudentDocument(Long studentDocumentId, DocumentReviewDTO request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User reviewer = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        StudentDocument document = studentDocumentRepository.findById(studentDocumentId)
                .orElseThrow(() -> new RuntimeException("Document not found"));



        if (document.getStatus() == DocumentStatus.CORRECTIONS_REQUESTED_BY_PROGRAM_HEAD) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", "No se puede cambiar el estado del documento porque está en estado " + document.getStatus() + ". El estudiante debe primero corregir y resubir el documento para que pueda ser revisado por jefatura de programa nuevamente."
                    )
            );
        }

        if (document.getStatus() == DocumentStatus.ACCEPTED_FOR_EXAMINER_REVIEW){
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", "No se puede cambiar el estado del documento porque ya fue aceptado por los jurados evaluadores."
                    )
            );
        }




        if (document.getStatus() == DocumentStatus.CORRECTIONS_REQUESTED_BY_PROGRAM_CURRICULUM_COMMITTEE ||
           document.getStatus() == DocumentStatus.CORRECTIONS_REQUESTED_BY_EXAMINER){
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", "No se puede cambiar el estado del documento porque está en estado " + document.getStatus() + ". El estudiante debe primero corregir y resubir el documento para que pueda ser revisado por el comité de currículo o los jurados evaluadores nuevamente."
                    )
            );
        }



        // Validación de estado permitido
        DocumentStatus currentStatus = document.getStatus();
        if (currentStatus != DocumentStatus.PENDING &&
            currentStatus != DocumentStatus.ACCEPTED_FOR_PROGRAM_HEAD_REVIEW &&
            currentStatus != DocumentStatus.REJECTED_FOR_PROGRAM_HEAD_REVIEW &&
            currentStatus !=  DocumentStatus.CORRECTION_RESUBMITTED) {
            return ResponseEntity.badRequest().body(
                Map.of(
                    "success", false,
                    "message", "No puedes cambiar el estado de este documento.",
                    "currentStatus", currentStatus
                )
            );
        }

        ModalityProcessStatus modalityStatus = document.getStudentModality().getStatus();

        if (modalityStatus == ModalityProcessStatus.CORRECTIONS_SUBMITTED_TO_EXAMINERS ||
             modalityStatus == ModalityProcessStatus.CORRECTIONS_REQUESTED_PROGRAM_CURRICULUM_COMMITTEE) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", "No se puede cambiar el estado del documento porque la modalidad está en estado " + modalityStatus + ". El estudiante debe primero corregir y resubir el documento para que pueda ser revisado por el comité de currículo o los jurados evaluadores nuevamente."
                    )
            );
        }


        AcademicProgram documentProgram = document.getStudentModality().getAcademicProgram();

        boolean authorized = programAuthorityRepository.existsByUser_IdAndAcademicProgram_IdAndRole(reviewer.getId(), documentProgram.getId(), ProgramRole.PROGRAM_HEAD);

        if (!authorized) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("No tienes permisos para revisar documentos de este programa académico");
        }

        if ((request.getStatus() == DocumentStatus.REJECTED_FOR_PROGRAM_HEAD_REVIEW ||
                request.getStatus() == DocumentStatus.CORRECTIONS_REQUESTED_BY_PROGRAM_HEAD)
                && (request.getNotes() == null || request.getNotes().isBlank())) {

            return ResponseEntity.badRequest().body("Debe proporcionar notas al rechazar o solicitar correcciones");
        }

        document.setStatus(request.getStatus());
        document.setNotes(request.getNotes());
        document.setUploadDate(LocalDateTime.now());

        studentDocumentRepository.save(document);

        if (request.getStatus() == DocumentStatus.CORRECTIONS_REQUESTED_BY_PROGRAM_HEAD) {
            StudentModality studentModality = document.getStudentModality();


            LocalDateTime now = LocalDateTime.now();

            studentModality.setStatus(ModalityProcessStatus.CORRECTIONS_REQUESTED_PROGRAM_HEAD);
            studentModality.setCorrectionRequestDate(now);
            studentModality.setCorrectionDeadline(now.plusDays(30));
            studentModality.setCorrectionReminderSent(false);
            studentModality.setUpdatedAt(now);
            studentModalityRepository.save(studentModality);

            historyRepository.save(
                    ModalityProcessStatusHistory.builder()
                            .studentModality(studentModality)
                            .status(ModalityProcessStatus.CORRECTIONS_REQUESTED_PROGRAM_HEAD)
                            .changeDate(now)
                            .responsible(reviewer)
                            .observations("Jefe de programa solicitó correcciones en documento: " +
                                    document.getDocumentConfig().getDocumentName() +
                                    ". Notas: " + request.getNotes())
                            .build()
            );

            List<StudentModalityMember> activeMembers = studentModalityMemberRepository
                    .findByStudentModalityIdAndStatus(studentModality.getId(), MemberStatus.ACTIVE);

            for (StudentModalityMember member : activeMembers) {
                notificationEventPublisher.publish(
                        new DocumentCorrectionsRequestedEvent(
                                document.getId(),
                                member.getStudent().getId(),
                                request.getNotes(),
                                NotificationRecipientType.PROGRAM_HEAD,
                                reviewer.getId()
                        )
                );
            }
        }

        documentHistoryRepository.save(
                StudentDocumentStatusHistory.builder()
                        .studentDocument(document)
                        .status(request.getStatus())
                        .changeDate(LocalDateTime.now())
                        .responsible(reviewer)
                        .observations(request.getNotes())
                        .build()
        );

        return ResponseEntity.ok(
                Map.of(
                        "message", "Documento revisado correctamente",
                        "documentId", document.getId(),
                        "newStatus", document.getStatus()
                )
        );
    }

    @Transactional
    public ResponseEntity<?> approveModalityByProgramHead(Long studentModalityId) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User programHead = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        StudentModality studentModality = studentModalityRepository.findById(studentModalityId)
                .orElseThrow(() -> new RuntimeException("Modality not found"));


        Long academicProgramId = studentModality.getAcademicProgram().getId();

        boolean isAuthorized =
                programAuthorityRepository.existsByUser_IdAndAcademicProgram_IdAndRole(
                                programHead.getId(),
                                academicProgramId,
                                ProgramRole.PROGRAM_HEAD
                        );

        if (!isAuthorized) {
            return ResponseEntity.status(403).body(
                    Map.of(
                            "approved", false,
                            "message", "No tienes permisos para aprobar modalidades de este programa académico"
                    )
            );
        }


        if (!(studentModality.getStatus() == ModalityProcessStatus.UNDER_REVIEW_PROGRAM_HEAD ||
                studentModality.getStatus() == ModalityProcessStatus.CORRECTIONS_REQUESTED_PROGRAM_HEAD ||
                studentModality.getStatus() == ModalityProcessStatus.CORRECTIONS_SUBMITTED_TO_PROGRAM_HEAD ||
                studentModality.getStatus() == ModalityProcessStatus.CANCELLATION_REJECTED
                )) {

            return ResponseEntity.badRequest().body(
                    Map.of(
                            "approved", false,
                            "message", "La modalidad no está en un estado válido para ser aprobada por la jefatura de programa",
                            "currentStatus", studentModality.getStatus()
                    )
            );
        }

        Long modalityId =
                studentModality
                        .getProgramDegreeModality()
                        .getDegreeModality()
                        .getId();


        List<RequiredDocument> mandatoryDocuments =
                requiredDocumentRepository.findByModalityIdAndActiveTrueAndDocumentType(modalityId, DocumentType.MANDATORY);

        List<StudentDocument> uploadedDocuments =
                studentDocumentRepository.findByStudentModalityId(studentModalityId);

        Map<Long, StudentDocument> uploadedMap =
                uploadedDocuments.stream()
                        .collect(Collectors.toMap(
                                doc -> doc.getDocumentConfig().getId(),
                                doc -> doc
                        ));

        List<Map<String, Object>> invalidDocuments = new ArrayList<>();

        for (RequiredDocument required : mandatoryDocuments) {

            StudentDocument uploaded = uploadedMap.get(required.getId());

            if (uploaded == null) {
                invalidDocuments.add(
                        Map.of(
                                "documentName", required.getDocumentName(),
                                "status", "NOT_UPLOADED"
                        )
                );
                continue;
            }

            if (uploaded.getStatus() != DocumentStatus.ACCEPTED_FOR_PROGRAM_HEAD_REVIEW) {
                invalidDocuments.add(
                        Map.of(
                                "documentName", required.getDocumentName(),
                                "status", uploaded.getStatus()
                        )
                );
            }
        }

        if (!invalidDocuments.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "approved", false,
                            "message", "Para poder aprobar la modalidad, todos los documentos obligatorios deben estar aceptados",
                            "documents", invalidDocuments
                    )
            );
        }


        studentModality.setStatus(ModalityProcessStatus.READY_FOR_PROGRAM_CURRICULUM_COMMITTEE);
        studentModality.setUpdatedAt(LocalDateTime.now());
        studentModalityRepository.save(studentModality);

        historyRepository.save(
                ModalityProcessStatusHistory.builder()
                        .studentModality(studentModality)
                        .status(ModalityProcessStatus.READY_FOR_PROGRAM_CURRICULUM_COMMITTEE)
                        .changeDate(LocalDateTime.now())
                        .responsible(programHead)
                        .observations("Modalidad aprobada por jefatura de programa")
                        .build()
        );

        notificationEventPublisher.publish(
                new ModalityApprovedByProgramHead(
                        studentModality.getId(),
                        programHead.getId()
                )
        );

        return ResponseEntity.ok(
                Map.of(
                        "approved", true,
                        "newStatus", ModalityProcessStatus.READY_FOR_PROGRAM_CURRICULUM_COMMITTEE,
                        "message", "Modalidad aprobada correctamente y enviada al comité de currículo de programa"
                )
        );
    }

    @Transactional
    public ResponseEntity<?> approveModalityByCommittee(Long studentModalityId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User committeeMember = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        StudentModality studentModality = studentModalityRepository.findById(studentModalityId)
                .orElseThrow(() -> new RuntimeException("Modality not found"));

        Long academicProgramId = studentModality.getAcademicProgram().getId();

        boolean isAuthorized =
                programAuthorityRepository
                        .existsByUser_IdAndAcademicProgram_IdAndRole(
                                committeeMember.getId(),
                                academicProgramId,
                                ProgramRole.PROGRAM_CURRICULUM_COMMITTEE
                        );

        if (!isAuthorized) {
            return ResponseEntity.status(403).body(
                    Map.of(
                            "approved", false,
                            "message", "No tienes permisos para aprobar modalidades de este programa académico"
                    )
            );
        }

        if (studentModality.getStatus() != ModalityProcessStatus.READY_FOR_APPROVED_BY_PROGRAM_CURRICULUM_COMMITTEE) {

            return ResponseEntity.badRequest().body(
                    Map.of(
                            "approved", false,
                            "message", "La modalidad no está en estado válido para aprobación por el comité de currículo de programa",
                            "currentStatus", studentModality.getStatus()
                    )
            );
        }


        List<StudentDocument> documents = studentDocumentRepository.findByStudentModalityId(studentModalityId);
        boolean allDocumentsApproved = documents.stream()
                .allMatch(doc -> doc.getStatus() == DocumentStatus.ACCEPTED_FOR_PROGRAM_CURRICULUM_COMMITTEE_REVIEW);
        if (!allDocumentsApproved) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "approved", false,
                            "message", "No se puede aprobar la modalidad. Todos los documentos deben estar aprobados por el comité de currículo de programa."
                    )
            );
        }

        studentModality.setStatus(ModalityProcessStatus.READY_FOR_EXAMINERS);
        studentModality.setUpdatedAt(LocalDateTime.now());
        studentModalityRepository.save(studentModality);

        historyRepository.save(
                ModalityProcessStatusHistory.builder()
                        .studentModality(studentModality)
                        .status(ModalityProcessStatus.READY_FOR_EXAMINERS)
                        .changeDate(LocalDateTime.now())
                        .responsible(committeeMember)
                        .observations("Modalidad aprobada por el Comité de currículo de programa")
                        .build()
        );



        return ResponseEntity.ok(
                Map.of(
                        "approved", true,
                        "newStatus", ModalityProcessStatus.READY_FOR_EXAMINERS,
                        "message", "Modalidad aprobada definitivamente por el comité de currículo de programa"
                )
        );
    }


    @Transactional
    public ResponseEntity<?> reviewStudentDocumentByExaminer(Long studentDocumentId, DocumentReviewDTO request) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User examiner = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        boolean hasExaminerRole = examiner.getRoles().stream()
                .anyMatch(role -> role.getName().equals("EXAMINER"));

        if (!hasExaminerRole) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    Map.of("success", false, "message", "El usuario no tiene rol de EXAMINER")
            );
        }

        StudentDocument document = studentDocumentRepository.findById(studentDocumentId)
                .orElseThrow(() -> new RuntimeException("Documento no encontrado"));

        StudentModality studentModality = document.getStudentModality();


        DefenseExaminer defenseExaminer = defenseExaminerRepository
                .findByStudentModalityIdAndExaminerId(studentModality.getId(), examiner.getId())
                .orElse(null);

        if (defenseExaminer == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    Map.of("success", false, "message", "No estás asignado como jurado de esta modalidad")
            );
        }

        ExaminerType examinerType = defenseExaminer.getExaminerType();

        // ===== VALIDACIÓN: Solo se pueden evaluar documentos MANDATORY con requiresProposalEvaluation=true =====
        // Documentos MANDATORY sin esta condición (ej: contratos, formularios) no son evaluables por el jurado.
        // Los documentos SECONDARY sí pueden ser evaluados por el jurado (son los documentos finales).
        DocumentType docType = document.getDocumentConfig().getDocumentType();
        if (docType == DocumentType.MANDATORY && !document.getDocumentConfig().isRequiresProposalEvaluation()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "success", false,
                    "message", "Este documento obligatorio no requiere evaluación por parte del jurado. " +
                               "Solo los documentos de propuesta de grado marcados para evaluación por jurado pueden ser revisados por este rol."
            ));
        }
        // =================================================================================

        // Validar que el documento no esté bloqueado esperando al estudiante
        if (document.getStatus() == DocumentStatus.CORRECTIONS_REQUESTED_BY_EXAMINER) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "No se puede cambiar el estado del documento porque está en estado " +
                            document.getStatus() + ". El estudiante debe primero corregir y resubir el documento."
            ));
        }

        // ===== VALIDACIÓN: Un jurado no puede cambiar su decisión una vez emitida =====
        // Excepción: si su decisión anterior fue CORRECTIONS_REQUESTED, el estudiante resubió
        // y ahora el jurado debe re-evaluar la nueva versión del documento.
        ExaminerDocumentReview existingReview = examinerDocumentReviewRepository
                .findByStudentDocumentIdAndExaminerId(document.getId(), examiner.getId())
                .orElse(null);

        if (existingReview != null) {
            ExaminerDocumentDecision previousDecision = existingReview.getDecision();

            if (previousDecision == ExaminerDocumentDecision.ACCEPTED) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Ya aprobaste este documento. Una vez emitida la aprobación no puede ser modificada.",
                        "yourPreviousDecision", previousDecision.name()
                ));
            }

            if (previousDecision == ExaminerDocumentDecision.REJECTED) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Ya rechazaste este documento. Una vez emitido el rechazo no puede ser modificado.",
                        "yourPreviousDecision", previousDecision.name()
                ));
            }

            // Si previousDecision == CORRECTIONS_REQUESTED: el jurado puede re-votar
            // porque el estudiante resubió el documento con las correcciones.
            // Verificamos que el documento esté efectivamente en estado de resubmisión.
            if (previousDecision == ExaminerDocumentDecision.CORRECTIONS_REQUESTED) {
                if (document.getStatus() != DocumentStatus.CORRECTION_RESUBMITTED) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "success", false,
                            "message", "Solicitaste correcciones en este documento. Debes esperar a que el estudiante resuba el documento corregido antes de emitir una nueva evaluación.",
                            "yourPreviousDecision", previousDecision.name(),
                            "documentStatus", document.getStatus().name()
                    ));
                }
            }
        }
        // =============================================================================

        if (request.getStatus() != DocumentStatus.ACCEPTED_FOR_EXAMINER_REVIEW &&
                request.getStatus() != DocumentStatus.REJECTED_FOR_EXAMINER_REVIEW &&
                request.getStatus() != DocumentStatus.CORRECTIONS_REQUESTED_BY_EXAMINER) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Estado de documento inválido para revisión por jurado"
            ));
        }

        if ((request.getStatus() == DocumentStatus.REJECTED_FOR_EXAMINER_REVIEW ||
                request.getStatus() == DocumentStatus.CORRECTIONS_REQUESTED_BY_EXAMINER)
                && (request.getNotes() == null || request.getNotes().isBlank())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Debe proporcionar notas al rechazar o solicitar correcciones"
            ));
        }

        // Determinar la decisión individual del jurado
        ExaminerDocumentDecision individualDecision = switch (request.getStatus()) {
            case ACCEPTED_FOR_EXAMINER_REVIEW -> ExaminerDocumentDecision.ACCEPTED;
            case REJECTED_FOR_EXAMINER_REVIEW -> ExaminerDocumentDecision.REJECTED;
            case CORRECTIONS_REQUESTED_BY_EXAMINER -> ExaminerDocumentDecision.CORRECTIONS_REQUESTED;
            default -> throw new IllegalArgumentException("Estado inválido");
        };

        // Guardar/actualizar la review individual del jurado
        ExaminerDocumentReview review = examinerDocumentReviewRepository
                .findByStudentDocumentIdAndExaminerId(document.getId(), examiner.getId())
                .orElse(ExaminerDocumentReview.builder()
                        .studentDocument(document)
                        .examiner(examiner)
                        .isTiebreakerVote(examinerType == ExaminerType.TIEBREAKER_EXAMINER)
                        .build());
        review.setDecision(individualDecision);
        review.setNotes(request.getNotes());
        review.setReviewedAt(LocalDateTime.now());
        review.setIsTiebreakerVote(examinerType == ExaminerType.TIEBREAKER_EXAMINER);
        examinerDocumentReviewRepository.save(review);

        // Guardar historial del documento
        documentHistoryRepository.save(
                StudentDocumentStatusHistory.builder()
                        .studentDocument(document)
                        .status(request.getStatus())
                        .changeDate(LocalDateTime.now())
                        .responsible(examiner)
                        .observations("Jurado " + examiner.getName() + " " + examiner.getLastName() +
                                " (" + examinerType.name() + "): " +
                                (request.getNotes() != null ? request.getNotes() : individualDecision.name()))
                        .build()
        );

        // Manejar ProposalEvaluation si aplica
        if (document.getDocumentConfig().isRequiresProposalEvaluation()
                && request.getProposalEvaluation() != null) {
            ProposalEvaluationRequest evalReq = request.getProposalEvaluation();
            if (evalReq.getSummary() == null || evalReq.getBackgroundJustification() == null
                    || evalReq.getProblemStatement() == null || evalReq.getObjectives() == null
                    || evalReq.getMethodology() == null || evalReq.getBibliographyReferences() == null
                    || evalReq.getDocumentOrganization() == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Debe proporcionar calificaciones para todos los aspectos de la propuesta de grado"
                ));
            }
            ProposalEvaluation proposalEvaluation = proposalEvaluationRepository
                    .findByStudentDocumentIdAndExaminerId(document.getId(), examiner.getId())
                    .orElse(null);
            if (proposalEvaluation != null) {
                proposalEvaluation.setSummary(evalReq.getSummary());
                proposalEvaluation.setBackgroundJustification(evalReq.getBackgroundJustification());
                proposalEvaluation.setProblemStatement(evalReq.getProblemStatement());
                proposalEvaluation.setObjectives(evalReq.getObjectives());
                proposalEvaluation.setMethodology(evalReq.getMethodology());
                proposalEvaluation.setBibliographyReferences(evalReq.getBibliographyReferences());
                proposalEvaluation.setDocumentOrganization(evalReq.getDocumentOrganization());
                proposalEvaluation.setEvaluatedAt(LocalDateTime.now());
            } else {
                proposalEvaluation = ProposalEvaluation.builder()
                        .studentDocument(document)
                        .examiner(examiner)
                        .summary(evalReq.getSummary())
                        .backgroundJustification(evalReq.getBackgroundJustification())
                        .problemStatement(evalReq.getProblemStatement())
                        .objectives(evalReq.getObjectives())
                        .methodology(evalReq.getMethodology())
                        .bibliographyReferences(evalReq.getBibliographyReferences())
                        .documentOrganization(evalReq.getDocumentOrganization())
                        .evaluatedAt(LocalDateTime.now())
                        .build();
            }
            proposalEvaluationRepository.save(proposalEvaluation);
        }

        // ===== LÓGICA DE CONSENSO ENTRE JURADOS =====
        ResponseEntity<?> consensusResult = processExaminerConsensus(
                document, studentModality, examiner, examinerType, individualDecision, request.getNotes()
        );
        if (consensusResult != null) {
            // Si el consenso retorna un resultado especial (rechazo final), devolvemos ese
            return consensusResult;
        }

        // Construir respuesta
        String message = switch (request.getStatus()) {
            case ACCEPTED_FOR_EXAMINER_REVIEW -> "Documento aceptado por el jurado. Se evaluará el veredicto de todos los jurados.";
            case REJECTED_FOR_EXAMINER_REVIEW -> "Documento rechazado por el jurado. Se evaluará el veredicto de todos los jurados.";
            case CORRECTIONS_REQUESTED_BY_EXAMINER -> "Correcciones solicitadas. El estudiante deberá subir el documento corregido.";
            default -> "Documento revisado correctamente";
        };

        ProposalEvaluation savedProposalEvaluation = proposalEvaluationRepository
                .findByStudentDocumentIdAndExaminerId(document.getId(), examiner.getId())
                .orElse(null);

        Map<String, Object> responseBody = new java.util.HashMap<>();
        responseBody.put("success", true);
        responseBody.put("documentId", document.getId());
        responseBody.put("documentName", document.getDocumentConfig().getDocumentName());
        responseBody.put("examinerDecision", individualDecision.name());
        responseBody.put("currentDocumentStatus", document.getStatus());
        responseBody.put("examinerName", examiner.getName() + " " + examiner.getLastName());
        responseBody.put("examinerType", examinerType.name());
        responseBody.put("message", message);

        if (savedProposalEvaluation != null) {
            Map<String, Object> proposalEvaluationInfo = new java.util.HashMap<>();
            proposalEvaluationInfo.put("id", savedProposalEvaluation.getId());
            proposalEvaluationInfo.put("summary", savedProposalEvaluation.getSummary());
            proposalEvaluationInfo.put("backgroundJustification", savedProposalEvaluation.getBackgroundJustification());
            proposalEvaluationInfo.put("problemStatement", savedProposalEvaluation.getProblemStatement());
            proposalEvaluationInfo.put("objectives", savedProposalEvaluation.getObjectives());
            proposalEvaluationInfo.put("methodology", savedProposalEvaluation.getMethodology());
            proposalEvaluationInfo.put("bibliographyReferences", savedProposalEvaluation.getBibliographyReferences());
            proposalEvaluationInfo.put("documentOrganization", savedProposalEvaluation.getDocumentOrganization());
            proposalEvaluationInfo.put("evaluatedAt", savedProposalEvaluation.getEvaluatedAt());
            responseBody.put("proposalEvaluation", proposalEvaluationInfo);
        } else {
            responseBody.put("proposalEvaluation", null);
        }

        return ResponseEntity.ok(responseBody);
    }

    @Transactional
        public ResponseEntity<?> reviewFinalDocumentByExaminer(Long studentDocumentId, DocumentReviewDTO request) {

        if (request == null || request.getFinalEvaluation() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Debe enviar la evaluación detallada del documento final en el campo finalEvaluation"
            ));
        }

        if (request.getStatus() != DocumentStatus.ACCEPTED_FOR_EXAMINER_REVIEW &&
                request.getStatus() != DocumentStatus.REJECTED_FOR_EXAMINER_REVIEW &&
                request.getStatus() != DocumentStatus.CORRECTIONS_REQUESTED_BY_EXAMINER) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Estado de documento inválido para revisión por jurado"
            ));
        }

        if ((request.getStatus() == DocumentStatus.REJECTED_FOR_EXAMINER_REVIEW ||
                request.getStatus() == DocumentStatus.CORRECTIONS_REQUESTED_BY_EXAMINER)
                && (request.getNotes() == null || request.getNotes().isBlank())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Debe proporcionar notas al rechazar o solicitar correcciones"
            ));
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User examiner = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        StudentDocument document = studentDocumentRepository.findById(studentDocumentId)
                .orElseThrow(() -> new RuntimeException("Documento no encontrado"));

        StudentModality studentModality = document.getStudentModality();

        if (studentModality.getStatus() != ModalityProcessStatus.READY_FOR_DEFENSE &&
              studentModality.getStatus() != ModalityProcessStatus.CORRECTIONS_SUBMITTED_TO_EXAMINERS) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "currentStatus", studentModality.getStatus().name(),
                    "message", "La modalidad no está en un estado válido para revisión de documentos finales por parte del jurado"
            ));
        }



        if (document.getDocumentConfig().getDocumentType() != DocumentType.SECONDARY ||
                !document.getDocumentConfig().isRequiresProposalEvaluation()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "success", false,
                    "message", "Solo se permite evaluar documentos finales que requieran evaluación por parte del jurado"
            ));
        }

        FinalEvaluationRequest evalReq = request.getFinalEvaluation();
        FinalDocumentRubricType rubricType = resolveFinalDocumentRubricType(studentModality);
        String validationError = validateFinalEvaluationByRubric(evalReq, rubricType);
        if (validationError != null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "rubricType", rubricType.name(),
                    "message", validationError
            ));
        }

        DocumentStatus previousDocumentStatus = document.getStatus();
        String previousDocumentNotes = document.getNotes();
        ModalityProcessStatus previousModalityStatus = studentModality.getStatus();

        ResponseEntity<?> reviewResult = reviewStudentDocumentByExaminer(studentDocumentId, request);
        if (!reviewResult.getStatusCode().is2xxSuccessful()) {
            return reviewResult;
        }

        // Releer para reflejar estados resultantes del consenso entre jurados.
        StudentDocument updatedDocument = studentDocumentRepository.findById(studentDocumentId)
                .orElseThrow(() -> new RuntimeException("Documento no encontrado"));
        StudentModality updatedModality = studentModalityRepository.findById(studentModality.getId())
                .orElseThrow(() -> new RuntimeException("Modalidad no encontrada"));

        // Consenso positivo en documento final: asegurar transición de cierre de revisión final.
        // Se invoca cuando el documento fue aprobado Y la modalidad está en fase de revisión final
        // (READY_FOR_DEFENSE o CORRECTIONS_SUBMITTED_TO_EXAMINERS)
        if (updatedDocument.getStatus() == DocumentStatus.ACCEPTED_FOR_EXAMINER_REVIEW) {
            boolean isInFinalReviewPhase = updatedModality.getStatus() == ModalityProcessStatus.READY_FOR_DEFENSE ||
                    updatedModality.getStatus() == ModalityProcessStatus.CORRECTIONS_SUBMITTED_TO_EXAMINERS;
            if (isInFinalReviewPhase) {
                checkAndTransitionIfAllSecondaryApprovedByExaminers(updatedModality, examiner);
                updatedModality = studentModalityRepository.findById(updatedModality.getId())
                        .orElse(updatedModality);
            }
        }

        FinalDocumentEvaluation secondaryEvaluation = secondaryDocumentEvaluationRepository
                .findByStudentDocumentIdAndExaminerId(document.getId(), examiner.getId())
                .orElse(FinalDocumentEvaluation.builder()
                        .studentDocument(document)
                        .examiner(examiner)
                        .build());

        applyFinalEvaluationByRubric(secondaryEvaluation, evalReq, rubricType);
        secondaryEvaluation.setEvaluatedAt(LocalDateTime.now());
        secondaryDocumentEvaluationRepository.save(secondaryEvaluation);

        // Trazabilidad específica de la rúbrica del documento final (independiente del consenso).
        saveFinalEvaluationTraceability(updatedDocument, examiner, request.getStatus(), request.getNotes(), secondaryEvaluation);

        // Trazabilidad explícita de cambios finales por consenso.
        if (previousDocumentStatus != updatedDocument.getStatus() || !Objects.equals(previousDocumentNotes, updatedDocument.getNotes())) {
            documentHistoryRepository.save(
                    StudentDocumentStatusHistory.builder()
                            .studentDocument(updatedDocument)
                            .status(updatedDocument.getStatus())
                            .changeDate(LocalDateTime.now())
                            .responsible(examiner)
                            .observations("Estado final del documento tras consenso de jurados: " +
                                    previousDocumentStatus + " -> " + updatedDocument.getStatus() +
                                    (updatedDocument.getNotes() != null && !updatedDocument.getNotes().isBlank()
                                            ? ". Notas finales: " + updatedDocument.getNotes()
                                            : ""))
                            .build()
            );
        }



        Map<String, Object> secondaryEvaluationInfo = buildFinalEvaluationInfoMap(secondaryEvaluation);

        Map<String, Object> traceability = new LinkedHashMap<>();
        traceability.put("previousDocumentStatus", previousDocumentStatus != null ? previousDocumentStatus.name() : null);
        traceability.put("currentDocumentStatus", updatedDocument.getStatus() != null ? updatedDocument.getStatus().name() : null);
        traceability.put("previousModalityStatus", previousModalityStatus != null ? previousModalityStatus.name() : null);
        traceability.put("currentModalityStatus", updatedModality.getStatus() != null ? updatedModality.getStatus().name() : null);
        traceability.put("examinerNotes", request.getNotes());

        Object responseBody = reviewResult.getBody();
        if (responseBody instanceof Map<?, ?> mapBody) {
            Map<String, Object> mergedBody = new LinkedHashMap<>();
            mapBody.forEach((key, value) -> mergedBody.put(String.valueOf(key), value));
            mergedBody.put("secondaryEvaluation", secondaryEvaluationInfo);
            mergedBody.put("finalEvaluation", secondaryEvaluationInfo);
            mergedBody.put("currentModalityStatus", updatedModality.getStatus().name());
            mergedBody.put("traceability", traceability);
            return ResponseEntity.status(reviewResult.getStatusCode()).body(mergedBody);
        }

        Map<String, Object> fallbackBody = new LinkedHashMap<>();
        fallbackBody.put("success", true);
        fallbackBody.put("message", "Documento SECONDARY evaluado correctamente");
        fallbackBody.put("reviewResult", responseBody);
        fallbackBody.put("secondaryEvaluation", secondaryEvaluationInfo);
        fallbackBody.put("finalEvaluation", secondaryEvaluationInfo);
        fallbackBody.put("currentModalityStatus", updatedModality.getStatus().name());
        fallbackBody.put("traceability", traceability);

        return ResponseEntity.status(reviewResult.getStatusCode()).body(fallbackBody);
    }

    private void saveFinalEvaluationTraceability(StudentDocument document,
                                                 User examiner,
                                                 DocumentStatus requestedStatus,
                                                 String notes,
                                                 FinalDocumentEvaluation evaluation) {
        String observations = "Rúbrica de documento final registrada por jurado " +
                examiner.getName() + " " + examiner.getLastName() +
                ". Decisión: " + (requestedStatus != null ? requestedStatus.name() : "SIN_ESTADO") +
                ". " + buildFinalEvaluationObservations(evaluation) +
                (notes != null && !notes.isBlank() ? ". Notas del jurado: " + notes : "");

        documentHistoryRepository.save(
                StudentDocumentStatusHistory.builder()
                        .studentDocument(document)
                        .status(document.getStatus())
                        .changeDate(LocalDateTime.now())
                        .responsible(examiner)
                        .observations(observations)
                        .build()
        );
    }

    private String buildFinalEvaluationObservations(FinalDocumentEvaluation evaluation) {
        FinalDocumentRubricType rubricType = evaluation.getRubricType() != null
                ? evaluation.getRubricType()
                : FinalDocumentRubricType.STANDARD;

        if (rubricType == FinalDocumentRubricType.PROFESSIONAL_PRACTICE) {
            return "Rúbrica=PROFESSIONAL_PRACTICE => generalObjective=" + evaluation.getGeneralObjective() +
                    ", activitiesObjectiveCoherence=" + evaluation.getActivitiesObjectiveCoherence() +
                    ", criticalActivitiesDescription=" + evaluation.getCriticalActivitiesDescription() +
                    ", practiceComplianceEvidence=" + evaluation.getPracticeComplianceEvidence() +
                    ", organizationAndWriting=" + evaluation.getOrganizationAndWriting();
        }

        return "Rúbrica=STANDARD => summary=" + evaluation.getSummary() +
                ", introduction=" + evaluation.getIntroduction() +
                ", materialsAndMethods=" + evaluation.getMaterialsAndMethods() +
                ", resultsAndDiscussion=" + evaluation.getResultsAndDiscussion() +
                ", conclusions=" + evaluation.getConclusions() +
                ", bibliographyReferences=" + evaluation.getBibliographyReferences() +
                ", documentOrganization=" + evaluation.getDocumentOrganization() +
                ", prototypeOrSoftware=" + evaluation.getPrototypeOrSoftware();
    }

    private FinalDocumentRubricType resolveFinalDocumentRubricType(StudentModality studentModality) {
        String modalityName = studentModality.getProgramDegreeModality().getDegreeModality().getName();
        String normalizedName = normalizeText(modalityName);
        if ("practica profesional".equals(normalizedName)) {
            return FinalDocumentRubricType.PROFESSIONAL_PRACTICE;
        }
        return FinalDocumentRubricType.STANDARD;
    }

    private String validateFinalEvaluationByRubric(FinalEvaluationRequest evalReq, FinalDocumentRubricType rubricType) {
        if (rubricType == FinalDocumentRubricType.PROFESSIONAL_PRACTICE) {
            if (evalReq.getGeneralObjective() == null ||
                    evalReq.getActivitiesObjectiveCoherence() == null ||
                    evalReq.getCriticalActivitiesDescription() == null ||
                    evalReq.getPracticeComplianceEvidence() == null ||
                    evalReq.getOrganizationAndWriting() == null) {
                return "Para la modalidad Práctica Profesional debe proporcionar calificaciones para todos los criterios: " +
                        "objetivo general, coherencia actividades-objetivo, descripción crítica de actividades, " +
                        "evidencia de cumplimiento de la práctica y organización/redacción del documento.";
            }
            return null;
        }

        if (evalReq.getSummary() == null ||
                evalReq.getIntroduction() == null ||
                evalReq.getMaterialsAndMethods() == null ||
                evalReq.getResultsAndDiscussion() == null ||
                evalReq.getConclusions() == null ||
                evalReq.getBibliographyReferences() == null ||
                evalReq.getDocumentOrganization() == null) {
            return "Debe proporcionar calificaciones para todos los aspectos obligatorios del documento final";
        }
        return null;
    }

    private void applyFinalEvaluationByRubric(FinalDocumentEvaluation evaluation,
                                              FinalEvaluationRequest evalReq,
                                              FinalDocumentRubricType rubricType) {
        evaluation.setRubricType(rubricType);

        if (rubricType == FinalDocumentRubricType.PROFESSIONAL_PRACTICE) {
            evaluation.setGeneralObjective(evalReq.getGeneralObjective());
            evaluation.setActivitiesObjectiveCoherence(evalReq.getActivitiesObjectiveCoherence());
            evaluation.setCriticalActivitiesDescription(evalReq.getCriticalActivitiesDescription());
            evaluation.setPracticeComplianceEvidence(evalReq.getPracticeComplianceEvidence());
            evaluation.setOrganizationAndWriting(evalReq.getOrganizationAndWriting());

            // Mapeo legacy para mantener compatibilidad con columnas históricas NOT NULL.
            evaluation.setSummary(evalReq.getGeneralObjective());
            evaluation.setIntroduction(evalReq.getActivitiesObjectiveCoherence());
            evaluation.setMaterialsAndMethods(evalReq.getCriticalActivitiesDescription());
            evaluation.setResultsAndDiscussion(evalReq.getPracticeComplianceEvidence());
            evaluation.setConclusions(evalReq.getOrganizationAndWriting());
            evaluation.setBibliographyReferences(evalReq.getOrganizationAndWriting());
            evaluation.setDocumentOrganization(evalReq.getOrganizationAndWriting());
            evaluation.setPrototypeOrSoftware(null);
            return;
        }

        evaluation.setSummary(evalReq.getSummary());
        evaluation.setIntroduction(evalReq.getIntroduction());
        evaluation.setMaterialsAndMethods(evalReq.getMaterialsAndMethods());
        evaluation.setResultsAndDiscussion(evalReq.getResultsAndDiscussion());
        evaluation.setConclusions(evalReq.getConclusions());
        evaluation.setBibliographyReferences(evalReq.getBibliographyReferences());
        evaluation.setDocumentOrganization(evalReq.getDocumentOrganization());
        evaluation.setPrototypeOrSoftware(evalReq.getPrototypeOrSoftware());

        // Limpiar campos de práctica si se reutiliza la misma fila de evaluación.
        evaluation.setGeneralObjective(null);
        evaluation.setActivitiesObjectiveCoherence(null);
        evaluation.setCriticalActivitiesDescription(null);
        evaluation.setPracticeComplianceEvidence(null);
        evaluation.setOrganizationAndWriting(null);
    }

    private Map<String, Object> buildFinalEvaluationInfoMap(FinalDocumentEvaluation evaluation) {
        Map<String, Object> info = new LinkedHashMap<>();
        FinalDocumentRubricType rubricType = evaluation.getRubricType() != null
                ? evaluation.getRubricType()
                : FinalDocumentRubricType.STANDARD;

        info.put("id", evaluation.getId());
        info.put("rubricType", rubricType.name());

        if (rubricType == FinalDocumentRubricType.PROFESSIONAL_PRACTICE) {
            info.put("generalObjective", evaluation.getGeneralObjective());
            info.put("activitiesObjectiveCoherence", evaluation.getActivitiesObjectiveCoherence());
            info.put("criticalActivitiesDescription", evaluation.getCriticalActivitiesDescription());
            info.put("practiceComplianceEvidence", evaluation.getPracticeComplianceEvidence());
            info.put("organizationAndWriting", evaluation.getOrganizationAndWriting());
            // Campos legacy para no romper consumidores existentes.
            info.put("summary", evaluation.getSummary());
            info.put("introduction", evaluation.getIntroduction());
            info.put("materialsAndMethods", evaluation.getMaterialsAndMethods());
            info.put("resultsAndDiscussion", evaluation.getResultsAndDiscussion());
            info.put("conclusions", evaluation.getConclusions());
            info.put("bibliographyReferences", evaluation.getBibliographyReferences());
            info.put("documentOrganization", evaluation.getDocumentOrganization());
            info.put("prototypeOrSoftware", evaluation.getPrototypeOrSoftware());
        } else {
            info.put("summary", evaluation.getSummary());
            info.put("introduction", evaluation.getIntroduction());
            info.put("materialsAndMethods", evaluation.getMaterialsAndMethods());
            info.put("resultsAndDiscussion", evaluation.getResultsAndDiscussion());
            info.put("conclusions", evaluation.getConclusions());
            info.put("bibliographyReferences", evaluation.getBibliographyReferences());
            info.put("documentOrganization", evaluation.getDocumentOrganization());
            info.put("prototypeOrSoftware", evaluation.getPrototypeOrSoftware());
        }

        info.put("evaluatedAt", evaluation.getEvaluatedAt());
        return info;
    }

    /**
     * Procesa el consenso entre jurados sobre un documento.
     * Implementa la lógica:
     * - Ambos jurados primarios aprueban → documento aprobado, verificar si todos los MANDATORY están aprobados
     * - Uno aprueba + otro solicita correcciones → estudiante debe corregir hasta que el jurado que solicitó correcciones apruebe.
     * - Uno aprueba + otro rechaza → se requiere jurado de desempate (DOCUMENT_REVIEW_TIEBREAKER_REQUIRED)
     * - Jurado de desempate decide (cualquier decisión) → se aplica su decisión
     *
     * @return ResponseEntity si hay un resultado final especial (rechazo), null si continúa normal
     */
    private ResponseEntity<?> processExaminerConsensus(StudentDocument document, StudentModality studentModality, User examiner, ExaminerType examinerType, ExaminerDocumentDecision individualDecision, String notes) {

        Long documentId = document.getId();
        Long modalityId = studentModality.getId();

        // Si es el jurado de desempate, su decisión es definitiva
        if (examinerType == ExaminerType.TIEBREAKER_EXAMINER) {
            return processTiebreakerDocumentDecision(document, studentModality, examiner, individualDecision, notes);
        }

        // Obtener los dos jurados primarios
        List<DefenseExaminer> primaryExaminers = defenseExaminerRepository
                .findPrimaryExaminersByStudentModalityId(modalityId);

        if (primaryExaminers.size() < 2) {
            // Solo hay un jurado asignado, su decisión es suficiente
            applyExaminerDecisionToDocument(document, studentModality, examiner, individualDecision, notes, true);
            return null;
        }

        // Recolectar las reviews de los dos jurados primarios para ESTE documento
        List<ExaminerDocumentReview> primaryReviews = new ArrayList<>();
        for (DefenseExaminer pe : primaryExaminers) {
            examinerDocumentReviewRepository
                    .findByStudentDocumentIdAndExaminerId(documentId, pe.getExaminer().getId())
                    .ifPresent(primaryReviews::add);
        }

        // Si aún no han votado ambos jurados primarios en la ronda actual, esperar al segundo
        if (primaryReviews.size() < 2) {
            // Determinar quién ya votó (el que aprobó previamente y conservó su voto)
            boolean hasExistingAccepted = primaryReviews.stream()
                    .anyMatch(r -> r.getDecision() == ExaminerDocumentDecision.ACCEPTED);

            // Si quien ya votó aprobó, el documento está en revisión pendiente del que solicitó correcciones
            if (hasExistingAccepted) {
                // El documento está esperando que el jurado que solicitó correcciones vote de nuevo
                // Conservar el estado actual (CORRECTION_RESUBMITTED) sin sobreescribirlo a PENDING
                document.setNotes("Aprobado por un jurado. Esperando revisión del otro jurado principal (" +
                        primaryReviews.size() + "/2 votos).");
            } else {
                // Primer voto registrado, esperando al segundo jurado primario
                document.setStatus(DocumentStatus.PENDING);
                document.setNotes("En revisión por jurados. Evaluaciones recibidas: " + primaryReviews.size() + "/2");
            }
            studentDocumentRepository.save(document);
            return null;
        }

        // Ambos jurados primarios han votado — analizar el resultado
        ExaminerDocumentDecision decision1 = primaryReviews.get(0).getDecision();
        ExaminerDocumentDecision decision2 = primaryReviews.get(1).getDecision();

        // CASO 1: Ambos aprueban
        if (decision1 == ExaminerDocumentDecision.ACCEPTED && decision2 == ExaminerDocumentDecision.ACCEPTED) {
            document.setStatus(DocumentStatus.ACCEPTED_FOR_EXAMINER_REVIEW);
            document.setNotes("Aprobado por ambos jurados principales");
            studentDocumentRepository.save(document);



            // Verificar si todos los documentos del mismo tipo están aprobados
            checkAndTransitionIfAllMandatoryApprovedByExaminers(document, studentModality, examiner);
            return null;
        }

        // CASO 2: Ambos solicitan correcciones o ambos rechazan
        if (decision1 == ExaminerDocumentDecision.CORRECTIONS_REQUESTED && decision2 == ExaminerDocumentDecision.CORRECTIONS_REQUESTED) {
            return applyCorrectionsRequestedByPrimaryExaminers(document, studentModality, examiner, primaryReviews, notes);
        }

        if (decision1 == ExaminerDocumentDecision.REJECTED && decision2 == ExaminerDocumentDecision.REJECTED) {
            return applyRejectionByBothPrimaryExaminers(document, studentModality, examiner, notes);
        }

        // CASO 3: Uno aprueba, el otro solicita correcciones → el estudiante debe corregir
        boolean oneApprovedOneCorrected =
                (decision1 == ExaminerDocumentDecision.ACCEPTED && decision2 == ExaminerDocumentDecision.CORRECTIONS_REQUESTED) ||
                (decision1 == ExaminerDocumentDecision.CORRECTIONS_REQUESTED && decision2 == ExaminerDocumentDecision.ACCEPTED);
        if (oneApprovedOneCorrected) {
            String combinedNotes = primaryReviews.stream()
                    .filter(r -> r.getDecision() == ExaminerDocumentDecision.CORRECTIONS_REQUESTED)
                    .map(r -> r.getNotes() != null ? r.getNotes() : "")
                    .collect(Collectors.joining("; "));
            return applyCorrectionsRequestedByPrimaryExaminers(document, studentModality, examiner, primaryReviews, combinedNotes);
        }

        // CASO 4: Uno aprueba, el otro rechaza → DESEMPATE REQUERIDO (único caso de desempate)
        boolean tiebreakerRequired =
                (decision1 == ExaminerDocumentDecision.ACCEPTED && decision2 == ExaminerDocumentDecision.REJECTED) ||
                (decision1 == ExaminerDocumentDecision.REJECTED && decision2 == ExaminerDocumentDecision.ACCEPTED);

        if (tiebreakerRequired) {
            document.setStatus(DocumentStatus.PENDING);
            document.setNotes("Decisión dividida: un jurado aprobó y el otro rechazó. Se requiere jurado de desempate.");
            studentDocumentRepository.save(document);

            studentModality.setStatus(ModalityProcessStatus.DOCUMENT_REVIEW_TIEBREAKER_REQUIRED);
            studentModality.setUpdatedAt(LocalDateTime.now());
            studentModalityRepository.save(studentModality);

            historyRepository.save(ModalityProcessStatusHistory.builder()
                    .studentModality(studentModality)
                    .status(ModalityProcessStatus.DOCUMENT_REVIEW_TIEBREAKER_REQUIRED)
                    .changeDate(LocalDateTime.now())
                    .responsible(examiner)
                    .observations("Un jurado aprobó y el otro rechazó el documento '" +
                            document.getDocumentConfig().getDocumentName() +
                            "'. Se requiere jurado de desempate para resolver.")
                    .build());
            return null;
        }

        // CASO 5: Uno rechaza, el otro solicita correcciones → el estudiante corrige;
        // solo el jurado que solicitó correcciones debe re-votar; el que rechazó conserva su voto REJECTED.
        // Cuando el que solicitó correcciones vuelva a votar, se re-evaluará el consenso:
        //   - Si aprueba → REJECTED + ACCEPTED → entra a desempate (CASO 4)
        //   - Si rechaza → REJECTED + REJECTED → rechazo definitivo
        //   - Si vuelve a pedir correcciones → ciclo se repite
        boolean rejectedVsCorrections =
                (decision1 == ExaminerDocumentDecision.REJECTED && decision2 == ExaminerDocumentDecision.CORRECTIONS_REQUESTED) ||
                (decision1 == ExaminerDocumentDecision.CORRECTIONS_REQUESTED && decision2 == ExaminerDocumentDecision.REJECTED);

        if (rejectedVsCorrections) {
            // Obtener las notas del jurado que solicitó correcciones
            String correctionNotes = primaryReviews.stream()
                    .filter(r -> r.getDecision() == ExaminerDocumentDecision.CORRECTIONS_REQUESTED)
                    .map(r -> r.getNotes() != null ? r.getNotes() : "")
                    .findFirst()
                    .orElse(notes != null ? notes : "");

            int currentAttempts = studentModality.getCorrectionAttempts() == null ? 0 : studentModality.getCorrectionAttempts();
            int newAttempts = currentAttempts + 1;

            if (newAttempts > 3) {
                // Agotó intentos → rechazo definitivo
                studentModality.setStatus(ModalityProcessStatus.CORRECTIONS_REJECTED_FINAL);
                studentModality.setCorrectionAttempts(newAttempts);
                studentModality.setUpdatedAt(LocalDateTime.now());
                studentModalityRepository.save(studentModality);

                document.setStatus(DocumentStatus.CORRECTIONS_REQUESTED_BY_EXAMINER);
                document.setNotes(correctionNotes);
                studentDocumentRepository.save(document);

                historyRepository.save(ModalityProcessStatusHistory.builder()
                        .studentModality(studentModality)
                        .status(ModalityProcessStatus.CORRECTIONS_REJECTED_FINAL)
                        .changeDate(LocalDateTime.now())
                        .responsible(examiner)
                        .observations("Rechazado definitivamente tras agotar 3 intentos de corrección. " +
                                "Documento: " + document.getDocumentConfig().getDocumentName())
                        .build());

                List<StudentModalityMember> members = studentModalityMemberRepository
                        .findByStudentModalityIdAndStatus(studentModality.getId(), MemberStatus.ACTIVE);
                for (StudentModalityMember member : members) {
                    notificationEventPublisher.publish(new CorrectionRejectedFinalEvent(
                            studentModality.getId(), document.getId(),
                            member.getStudent().getId(),
                            document.getDocumentConfig().getDocumentName(),
                            correctionNotes, examiner.getId()));
                }

                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "La propuesta ha sido rechazada definitivamente. El estudiante agotó las 3 oportunidades.",
                        "documentId", document.getId(),
                        "newModalityStatus", ModalityProcessStatus.CORRECTIONS_REJECTED_FINAL,
                        "attemptsUsed", newAttempts
                ));
            }

            // Solicitar correcciones: solo el jurado que las pidió deberá re-votar
            document.setStatus(DocumentStatus.CORRECTIONS_REQUESTED_BY_EXAMINER);
            document.setNotes("Correcciones solicitadas por un jurado (el otro rechazó). " +
                    "Una vez corregido, el jurado que solicitó correcciones decidirá si aprueba o rechaza.\n" +
                    correctionNotes);
            studentDocumentRepository.save(document);

            studentModality.setCorrectionAttempts(newAttempts);
            studentModality.setStatus(ModalityProcessStatus.CORRECTIONS_REQUESTED_EXAMINERS);
            LocalDateTime now = LocalDateTime.now();
            studentModality.setCorrectionRequestDate(now);
            studentModality.setCorrectionDeadline(now.plusDays(30));
            studentModality.setCorrectionReminderSent(false);
            studentModality.setUpdatedAt(now);
            studentModalityRepository.save(studentModality);

            historyRepository.save(ModalityProcessStatusHistory.builder()
                    .studentModality(studentModality)
                    .status(ModalityProcessStatus.CORRECTIONS_REQUESTED_EXAMINERS)
                    .changeDate(now)
                    .responsible(examiner)
                    .observations("Un jurado rechazó y el otro solicitó correcciones (intento " + newAttempts +
                            " de 3). El estudiante debe corregir para que el jurado que solicitó correcciones " +
                            "decida si aprueba o rechaza. Observaciones: " + correctionNotes)
                    .build());

            List<StudentModalityMember> activeMembers = studentModalityMemberRepository
                    .findByStudentModalityIdAndStatus(studentModality.getId(), MemberStatus.ACTIVE);
            for (StudentModalityMember member : activeMembers) {
                notificationEventPublisher.publish(new DocumentCorrectionsRequestedEvent(
                        document.getId(), member.getStudent().getId(),
                        "Un jurado rechazó el documento y el otro solicitó correcciones. " +
                        "Por favor corrija y resuba el documento. Observaciones: " + correctionNotes,
                        NotificationRecipientType.PROGRAM_CURRICULUM_COMMITTEE, examiner.getId()
                ));
            }
            return null;
        }

        return null;
    }

    /**
     * Aplica la decisión del jurado de desempate sobre el documento (es definitiva).
     */
    private ResponseEntity<?> processTiebreakerDocumentDecision(StudentDocument document, StudentModality studentModality, User tiebreaker, ExaminerDocumentDecision decision, String notes) {

        switch (decision) {
            case ACCEPTED -> {
                document.setStatus(DocumentStatus.ACCEPTED_FOR_EXAMINER_REVIEW);
                document.setNotes("Aprobado por el jurado de desempate");
                studentDocumentRepository.save(document);

                historyRepository.save(ModalityProcessStatusHistory.builder()
                        .studentModality(studentModality)
                        .status(studentModality.getStatus())
                        .changeDate(LocalDateTime.now())
                        .responsible(tiebreaker)
                        .observations("Jurado de desempate aprobó el documento: " +
                                document.getDocumentConfig().getDocumentName())
                        .build());

                checkAndTransitionIfAllMandatoryApprovedByExaminers(document, studentModality, tiebreaker);
            }
            case CORRECTIONS_REQUESTED -> {
                applyCorrectionsRequestedFromTiebreaker(document, studentModality, tiebreaker, notes);
            }
            case REJECTED -> {
                applyRejectionByTiebreaker(document, studentModality, tiebreaker, notes);
            }
        }
        return null;
    }

    /**
     * Aplica correcciones solicitadas por jurados primarios al estudiante.
     */
    private ResponseEntity<?> applyCorrectionsRequestedByPrimaryExaminers(StudentDocument document, StudentModality studentModality, User examiner, List<ExaminerDocumentReview> reviews, String notes) {

        // ===== LÓGICA DE CONTADOR DE INTENTOS =====
        // Solo incrementar el contador si la modalidad NO está ya en estado CORRECTIONS_REQUESTED_EXAMINERS
        // Esto evita que si ambos jurados solicitan correcciones, se cuente como 2 intentos en lugar de 1
        boolean shouldIncrementAttempt = studentModality.getStatus() != ModalityProcessStatus.CORRECTIONS_REQUESTED_EXAMINERS;
        
        int currentAttempts = studentModality.getCorrectionAttempts() == null ? 0 : studentModality.getCorrectionAttempts();
        int newAttempts = shouldIncrementAttempt ? currentAttempts + 1 : currentAttempts;

        if (newAttempts > 3) {
            studentModality.setStatus(ModalityProcessStatus.CORRECTIONS_REJECTED_FINAL);
            studentModality.setCorrectionAttempts(newAttempts);
            studentModality.setUpdatedAt(LocalDateTime.now());
            studentModalityRepository.save(studentModality);

            historyRepository.save(ModalityProcessStatusHistory.builder()
                    .studentModality(studentModality)
                    .status(ModalityProcessStatus.CORRECTIONS_REJECTED_FINAL)
                    .changeDate(LocalDateTime.now())
                    .responsible(examiner)
                    .observations("Rechazado definitivamente. El estudiante agotó 3 oportunidades de corrección. Documento: " +
                            document.getDocumentConfig().getDocumentName())
                    .build());

            List<StudentModalityMember> members = studentModalityMemberRepository
                    .findByStudentModalityIdAndStatus(studentModality.getId(), MemberStatus.ACTIVE);
            for (StudentModalityMember member : members) {
                notificationEventPublisher.publish(new CorrectionRejectedFinalEvent(
                        studentModality.getId(), document.getId(),
                        member.getStudent().getId(),
                        document.getDocumentConfig().getDocumentName(),
                        notes, examiner.getId()));
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "La propuesta ha sido rechazada definitivamente. El estudiante agotó las 3 oportunidades.",
                    "documentId", document.getId(),
                    "newModalityStatus", ModalityProcessStatus.CORRECTIONS_REJECTED_FINAL,
                    "attemptsUsed", newAttempts
            ));
        }

        String combinedNotes = reviews.stream()
                .filter(r -> r.getDecision() == ExaminerDocumentDecision.CORRECTIONS_REQUESTED && r.getNotes() != null)
                .map(ExaminerDocumentReview::getNotes)
                .collect(Collectors.joining(" | "));
        if (combinedNotes.isBlank() && notes != null) combinedNotes = notes;

        document.setStatus(DocumentStatus.CORRECTIONS_REQUESTED_BY_EXAMINER);
        document.setNotes(combinedNotes);
        studentDocumentRepository.save(document);

        studentModality.setCorrectionAttempts(newAttempts);
        studentModality.setStatus(ModalityProcessStatus.CORRECTIONS_REQUESTED_EXAMINERS);
        LocalDateTime now = LocalDateTime.now();
        studentModality.setCorrectionRequestDate(now);
        studentModality.setCorrectionDeadline(now.plusDays(30));
        studentModality.setCorrectionReminderSent(false);
        studentModality.setUpdatedAt(now);
        studentModalityRepository.save(studentModality);

        // Trazabilidad: indicar si este es un nuevo intento o una solicitud adicional del mismo intento
        String attemptMessage = shouldIncrementAttempt
                ? "Jurados solicitaron correcciones (intento " + newAttempts + " de 3): " + combinedNotes
                : "Jurado adicional solicitó correcciones para el intento " + newAttempts + " (ya en proceso): " + combinedNotes;

        historyRepository.save(ModalityProcessStatusHistory.builder()
                .studentModality(studentModality)
                .status(ModalityProcessStatus.CORRECTIONS_REQUESTED_EXAMINERS)
                .changeDate(now)
                .responsible(examiner)
                .observations(attemptMessage)
                .build());

        List<StudentModalityMember> activeMembers = studentModalityMemberRepository
                .findByStudentModalityIdAndStatus(studentModality.getId(), MemberStatus.ACTIVE);
        for (StudentModalityMember member : activeMembers) {
            notificationEventPublisher.publish(new DocumentCorrectionsRequestedEvent(
                    document.getId(), member.getStudent().getId(),
                    combinedNotes, NotificationRecipientType.EXAMINER, examiner.getId()
            ));
        }
        return null;
    }

    /**
     * Verifica si el documento es de tipo SECONDARY (documento final) para aplicar lógica de cancelación.
     * Si es documento SECONDARY y hay rechazo, cancela la modalidad completamente.
     * Si es documento MANDATORY, mantiene la lógica actual.
     *
     * @param document el documento siendo evaluado
     * @return true si es documento final (SECONDARY)
     */
    private boolean isFinalDocument(StudentDocument document) {
        return document.getDocumentConfig().getDocumentType() == DocumentType.SECONDARY;
    }

    /**
     * Cancela la modalidad por consenso de rechazo en documento final.
     * - Cambia estado de modalidad a MODALITY_CANCELLED
     * - Elimina la relación estudiante-modalidad (StudentModalityMember)
     * - Registra el cambio en historial
     * - Notifica al estudiante
     */
    private ResponseEntity<?> cancelModalityByFinalDocumentRejection(StudentDocument document, StudentModality studentModality, User examiner, String reason) {

        // Cambiar estado de modalidad a MODALITY_CANCELLED
        studentModality.setStatus(ModalityProcessStatus.MODALITY_CANCELLED);
        studentModality.setUpdatedAt(LocalDateTime.now());
        studentModalityRepository.save(studentModality);

        // Obtener y eliminar miembros activos (relación estudiante-modalidad)
        List<StudentModalityMember> members = studentModalityMemberRepository
                .findByStudentModalityIdAndStatus(studentModality.getId(), MemberStatus.ACTIVE);
        
        for (StudentModalityMember member : members) {
            studentModalityMemberRepository.delete(member);
        }

        // Registrar en historial
        String observations = "Modalidad cancelada por rechazo de documento final. " +
                "Documento: " + document.getDocumentConfig().getDocumentName() + ". " +
                (reason != null && !reason.isBlank() ? "Motivo: " + reason : "Documento rechazado por los jurados.");

        historyRepository.save(
                ModalityProcessStatusHistory.builder()
                        .studentModality(studentModality)
                        .status(ModalityProcessStatus.MODALITY_CANCELLED)
                        .changeDate(LocalDateTime.now())
                        .responsible(examiner)
                        .observations(observations)
                        .build()
        );

        // Actualizar estado del documento
        document.setStatus(DocumentStatus.REJECTED_FOR_EXAMINER_REVIEW);
        document.setNotes("Documento final rechazado - Modalidad cancelada");
        studentDocumentRepository.save(document);

        // Notificar a los estudiantes
        for (StudentModalityMember member : members) {
            notificationEventPublisher.publish(
                    new CorrectionRejectedFinalEvent(
                            studentModality.getId(), document.getId(),
                            member.getStudent().getId(),
                            document.getDocumentConfig().getDocumentName(),
                            "Modalidad cancelada por rechazo de documento final. " +
                            (reason != null && !reason.isBlank() ? reason : "Documento rechazado por los jurados. Puedes iniciar una nueva modalidad."),
                            examiner.getId())
            );
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "La modalidad ha sido cancelada por rechazo de documento final. Puedes iniciar una nueva modalidad.",
                "documentId", document.getId(),
                "documentName", document.getDocumentConfig().getDocumentName(),
                "newModalityStatus", ModalityProcessStatus.MODALITY_CANCELLED.name(),
                "deletedMembers", members.size()
        ));
    }

    /**
     * Aplica correcciones solicitadas por el jurado de desempate.
     */
    private void applyCorrectionsRequestedFromTiebreaker(StudentDocument document, StudentModality studentModality, User tiebreaker, String notes) {

        // ===== LÓGICA DE CONTADOR DE INTENTOS =====
        // Solo incrementar el contador si la modalidad NO está ya en estado CORRECTIONS_REQUESTED_EXAMINERS
        // Esto evita que si el jurado de desempate solicita correcciones en paralelo con jurados primarios,
        // se cuente como 2 intentos en lugar de 1
        boolean shouldIncrementAttempt = studentModality.getStatus() != ModalityProcessStatus.CORRECTIONS_REQUESTED_EXAMINERS;
        
        int currentAttempts = studentModality.getCorrectionAttempts() == null ? 0 : studentModality.getCorrectionAttempts();
        int newAttempts = shouldIncrementAttempt ? currentAttempts + 1 : currentAttempts;

        document.setStatus(DocumentStatus.CORRECTIONS_REQUESTED_BY_EXAMINER);
        document.setNotes(notes);
        studentDocumentRepository.save(document);

        studentModality.setCorrectionAttempts(newAttempts);
        studentModality.setStatus(ModalityProcessStatus.CORRECTIONS_REQUESTED_EXAMINERS);
        LocalDateTime now = LocalDateTime.now();
        studentModality.setCorrectionRequestDate(now);
        studentModality.setCorrectionDeadline(now.plusDays(30));
        studentModality.setCorrectionReminderSent(false);
        studentModality.setUpdatedAt(now);
        studentModalityRepository.save(studentModality);

        // Trazabilidad: indicar si este es un nuevo intento o una solicitud adicional del mismo intento
        String attemptMessage = shouldIncrementAttempt
                ? "Jurado de desempate solicitó correcciones (intento " + newAttempts + " de 3): " + notes
                : "Jurado de desempate solicitó correcciones para el intento " + newAttempts + " (ya en proceso): " + notes;

        historyRepository.save(ModalityProcessStatusHistory.builder()
                .studentModality(studentModality)
                .status(ModalityProcessStatus.CORRECTIONS_REQUESTED_EXAMINERS)
                .changeDate(now)
                .responsible(tiebreaker)
                .observations(attemptMessage)
                .build());

        List<StudentModalityMember> activeMembers = studentModalityMemberRepository
                .findByStudentModalityIdAndStatus(studentModality.getId(), MemberStatus.ACTIVE);
        for (StudentModalityMember member : activeMembers) {
            notificationEventPublisher.publish(new DocumentCorrectionsRequestedEvent(
                    document.getId(), member.getStudent().getId(),
                    notes, NotificationRecipientType.EXAMINER, tiebreaker.getId()
            ));
        }
    }

    /**
     * Aplica rechazo definitivo por ambos jurados primarios.
     * Si es un documento final (SECONDARY), cancela la modalidad completamente.
     * Si es documento MANDATORY, marca como rechazado para correcciones.
     */
    private ResponseEntity<?> applyRejectionByBothPrimaryExaminers(StudentDocument document, StudentModality studentModality, User examiner, String notes) {

        // Verificar si es un documento final (SECONDARY)
        if (isFinalDocument(document)) {
            return cancelModalityByFinalDocumentRejection(document, studentModality, examiner, notes);
        }

        // Lógica existente para documentos MANDATORY
        studentModality.setStatus(ModalityProcessStatus.CORRECTIONS_REJECTED_FINAL);
        studentModality.setUpdatedAt(LocalDateTime.now());
        studentModalityRepository.save(studentModality);

        document.setStatus(DocumentStatus.REJECTED_FOR_EXAMINER_REVIEW);
        document.setNotes("Rechazado por ambos jurados principales");
        studentDocumentRepository.save(document);

        historyRepository.save(ModalityProcessStatusHistory.builder()
                .studentModality(studentModality)
                .status(ModalityProcessStatus.CORRECTIONS_REJECTED_FINAL)
                .changeDate(LocalDateTime.now())
                .responsible(examiner)
                .observations("Ambos jurados principales rechazaron el documento: " +
                        document.getDocumentConfig().getDocumentName())
                .build());

        List<StudentModalityMember> members = studentModalityMemberRepository
                .findByStudentModalityIdAndStatus(studentModality.getId(), MemberStatus.ACTIVE);
        for (StudentModalityMember member : members) {
            notificationEventPublisher.publish(new CorrectionRejectedFinalEvent(
                    studentModality.getId(), document.getId(),
                    member.getStudent().getId(),
                    document.getDocumentConfig().getDocumentName(),
                    "Rechazado por ambos jurados principales. " + (notes != null ? notes : ""),
                    examiner.getId()));
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "El documento fue rechazado por ambos jurados principales.",
                "documentId", document.getId(),
                "newModalityStatus", ModalityProcessStatus.CORRECTIONS_REJECTED_FINAL
        ));
    }

    /**
     * Aplica rechazo definitivo por el jurado de desempate.
     * Si es un documento final (SECONDARY), cancela la modalidad completamente.
     * Si es documento MANDATORY, marca como rechazado para correcciones.
     */
    private void applyRejectionByTiebreaker(StudentDocument document, StudentModality studentModality, User tiebreaker, String notes) {

        // Verificar si es un documento final (SECONDARY)
        if (isFinalDocument(document)) {
            cancelModalityByFinalDocumentRejection(document, studentModality, tiebreaker, notes);
            return;
        }

        // Lógica existente para documentos MANDATORY
        studentModality.setStatus(ModalityProcessStatus.CORRECTIONS_REJECTED_FINAL);
        studentModality.setUpdatedAt(LocalDateTime.now());
        studentModalityRepository.save(studentModality);

        document.setStatus(DocumentStatus.REJECTED_FOR_EXAMINER_REVIEW);
        document.setNotes("Rechazado por el jurado de desempate");
        studentDocumentRepository.save(document);

        historyRepository.save(ModalityProcessStatusHistory.builder()
                .studentModality(studentModality)
                .status(ModalityProcessStatus.CORRECTIONS_REJECTED_FINAL)
                .changeDate(LocalDateTime.now())
                .responsible(tiebreaker)
                .observations("Jurado de desempate rechazó el documento: " +
                        document.getDocumentConfig().getDocumentName() + ". " + (notes != null ? notes : ""))
                .build());

        List<StudentModalityMember> members = studentModalityMemberRepository
                .findByStudentModalityIdAndStatus(studentModality.getId(), MemberStatus.ACTIVE);
        for (StudentModalityMember member : members) {
            notificationEventPublisher.publish(new CorrectionRejectedFinalEvent(
                    studentModality.getId(), document.getId(),
                    member.getStudent().getId(),
                    document.getDocumentConfig().getDocumentName(),
                    "Rechazado por jurado de desempate. " + (notes != null ? notes : ""),
                    tiebreaker.getId()));
        }
    }

    /**
     * Aplica la decisión del examiner al documento directamente (cuando solo hay un jurado).
     */
    private void applyExaminerDecisionToDocument(StudentDocument document, StudentModality studentModality, User examiner, ExaminerDocumentDecision decision, String notes, boolean singleExaminer) {

        DocumentStatus newStatus = switch (decision) {
            case ACCEPTED -> DocumentStatus.ACCEPTED_FOR_EXAMINER_REVIEW;
            case REJECTED -> DocumentStatus.REJECTED_FOR_EXAMINER_REVIEW;
            case CORRECTIONS_REQUESTED -> DocumentStatus.CORRECTIONS_REQUESTED_BY_EXAMINER;
        };
        document.setStatus(newStatus);
        document.setNotes(notes);
        studentDocumentRepository.save(document);

        if (decision == ExaminerDocumentDecision.ACCEPTED) {
            checkAndTransitionIfAllMandatoryApprovedByExaminers(document, studentModality, examiner);
        }
    }

    /**
     * Punto de entrada: se invoca cada vez que un documento es aprobado por consenso de jurados.
     * Usa el TIPO del documento (MANDATORY o SECONDARY) para determinar la fase y delegar.
     *
     * - Documento MANDATORY aprobado → verifica si todos los MANDATORY están listos → PROPOSAL_APPROVED
     * - Documento SECONDARY aprobado → verifica si todos los SECONDARY están listos → FINAL_REVIEW_COMPLETED
     */
    private void checkAndTransitionIfAllMandatoryApprovedByExaminers(StudentDocument approvedDocument, StudentModality studentModality, User responsible) {
        DocumentType approvedDocType = approvedDocument.getDocumentConfig().getDocumentType();

        if (approvedDocType == DocumentType.SECONDARY) {
            checkAndTransitionIfAllSecondaryApprovedByExaminers(studentModality, responsible);
        } else {
            // MANDATORY (o cualquier otro tipo en fase de propuesta)
            checkAndTransitionIfAllMandatoryDocs(studentModality, responsible);
        }
    }

    /**
     * Sobrecarga de compatibilidad cuando no se tiene el documento disponible.
     * Usa el estado de la modalidad como heurístico de fase.
     * NOTA: preferir siempre la versión con documento cuando sea posible.
     */
    private void checkAndTransitionIfAllMandatoryApprovedByExaminers(StudentModality studentModality, User responsible) {
        // Heurístico: READY_FOR_DEFENSE indica que la modalidad está en fase de revisión de SECONDARY
        boolean isSecondaryPhase = studentModality.getStatus() == ModalityProcessStatus.READY_FOR_DEFENSE;
        if (isSecondaryPhase) {
            checkAndTransitionIfAllSecondaryApprovedByExaminers(studentModality, responsible);
        } else {
            checkAndTransitionIfAllMandatoryDocs(studentModality, responsible);
        }
    }

    /**
     * Verifica que todos los documentos MANDATORY con requiresProposalEvaluation=true
     * estén en ACCEPTED_FOR_EXAMINER_REVIEW.
     * Solo los documentos de propuesta MANDATORY marcados para evaluación por jurado son verificados aquí.
     * Si todos están aprobados → DOCUMENTS_APPROVED_BY_EXAMINERS → PROPOSAL_APPROVED + notificaciones.
     */
    private void checkAndTransitionIfAllMandatoryDocs(StudentModality studentModality, User responsible) {
        Long modalityId = studentModality.getProgramDegreeModality().getDegreeModality().getId();

        // Solo documentos MANDATORY que requieren evaluación de propuesta (propuesta de grado, etc.)
        List<RequiredDocument> evaluableMandatoryDocs = requiredDocumentRepository
                .findByModalityIdAndActiveTrue(modalityId)
                .stream()
                .filter(req -> req.getDocumentType() == DocumentType.MANDATORY
                        && Boolean.TRUE.equals(req.isRequiresProposalEvaluation()))
                .toList();

        if (evaluableMandatoryDocs.isEmpty()) return;

        for (RequiredDocument reqDoc : evaluableMandatoryDocs) {
            StudentDocument doc = studentDocumentRepository
                    .findByStudentModalityIdAndDocumentConfigId(studentModality.getId(), reqDoc.getId())
                    .orElse(null);
            if (doc == null || doc.getStatus() != DocumentStatus.ACCEPTED_FOR_EXAMINER_REVIEW) {
                return; // Aún hay documentos MANDATORY evaluables sin aprobar
            }
        }

        // ✅ Todos los documentos MANDATORY evaluables han sido aprobados
        studentModality.setStatus(ModalityProcessStatus.DOCUMENTS_APPROVED_BY_EXAMINERS);
        studentModality.setUpdatedAt(LocalDateTime.now());
        studentModalityRepository.save(studentModality);

        historyRepository.save(ModalityProcessStatusHistory.builder()
                .studentModality(studentModality)
                .status(ModalityProcessStatus.DOCUMENTS_APPROVED_BY_EXAMINERS)
                .changeDate(LocalDateTime.now())
                .responsible(responsible)
                .observations("Los documentos de propuesta obligatorios han sido aprobados por los jurados.")
                .build());

        // → PROPOSAL_APPROVED automático
        studentModality.setStatus(ModalityProcessStatus.PROPOSAL_APPROVED);
        studentModality.setUpdatedAt(LocalDateTime.now());
        studentModalityRepository.save(studentModality);

        historyRepository.save(ModalityProcessStatusHistory.builder()
                .studentModality(studentModality)
                .status(ModalityProcessStatus.PROPOSAL_APPROVED)
                .changeDate(LocalDateTime.now())
                .responsible(responsible)
                .observations("Propuesta aprobada automáticamente por consenso de jurados.")
                .build());

        List<StudentModalityMember> activeMembers = studentModalityMemberRepository
                .findByStudentModalityIdAndStatus(studentModality.getId(), MemberStatus.ACTIVE);
        for (StudentModalityMember member : activeMembers) {
            notificationEventPublisher.publish(
                    new ModalityApprovedByExaminers(studentModality.getId(), member.getStudent().getId())
            );
        }
    }

    /**
     * Verifica que todos los documentos SECONDARY con requiresProposalEvaluation=true
     * estén en ACCEPTED_FOR_EXAMINER_REVIEW.
     * Solo los documentos SECONDARY marcados para evaluación por jurado son verificados aquí.
     * Si todos están aprobados → SECONDARY_DOCUMENTS_APPROVED_BY_EXAMINERS → FINAL_REVIEW_COMPLETED
     * + ExaminerFinalReviewCompletedEvent (notifica al director para programar sustentación).
     */
    private void checkAndTransitionIfAllSecondaryApprovedByExaminers(StudentModality studentModality, User responsible) {
        // Válido en fases de revisión final (READY_FOR_DEFENSE o CORRECTIONS_SUBMITTED_TO_EXAMINERS)
        boolean isInFinalReviewPhase = studentModality.getStatus() == ModalityProcessStatus.READY_FOR_DEFENSE ||
                studentModality.getStatus() == ModalityProcessStatus.CORRECTIONS_SUBMITTED_TO_EXAMINERS;
        if (!isInFinalReviewPhase) {
            return;
        }

        Long modalityId = studentModality.getProgramDegreeModality().getDegreeModality().getId();

        // Solo documentos SECONDARY que requieren evaluación por jurado
        List<RequiredDocument> evaluableSecondaryDocs = requiredDocumentRepository
                .findByModalityIdAndActiveTrue(modalityId)
                .stream()
                .filter(req -> req.getDocumentType() == DocumentType.SECONDARY
                        && Boolean.TRUE.equals(req.isRequiresProposalEvaluation()))
                .toList();

        if (evaluableSecondaryDocs.isEmpty()) return;

        for (RequiredDocument reqDoc : evaluableSecondaryDocs) {
            StudentDocument doc = studentDocumentRepository
                    .findByStudentModalityIdAndDocumentConfigId(studentModality.getId(), reqDoc.getId())
                    .orElse(null);
            if (doc == null || doc.getStatus() != DocumentStatus.ACCEPTED_FOR_EXAMINER_REVIEW) {
                return; // Aún hay documentos SECONDARY evaluables sin aprobar
            }
        }

        // ✅ Todos los SECONDARY aprobados → estado intermedio trazable
        studentModality.setStatus(ModalityProcessStatus.SECONDARY_DOCUMENTS_APPROVED_BY_EXAMINERS);
        studentModality.setUpdatedAt(LocalDateTime.now());
        studentModalityRepository.save(studentModality);

        historyRepository.save(ModalityProcessStatusHistory.builder()
                .studentModality(studentModality)
                .status(ModalityProcessStatus.SECONDARY_DOCUMENTS_APPROVED_BY_EXAMINERS)
                .changeDate(LocalDateTime.now())
                .responsible(responsible)
                .observations("Todos los documentos finales han sido aprobados por consenso de jurados.")
                .build());

        // → FINAL_REVIEW_COMPLETED automático
        studentModality.setStatus(ModalityProcessStatus.FINAL_REVIEW_COMPLETED);
        studentModality.setUpdatedAt(LocalDateTime.now());
        studentModalityRepository.save(studentModality);

        historyRepository.save(ModalityProcessStatusHistory.builder()
                .studentModality(studentModality)
                .status(ModalityProcessStatus.FINAL_REVIEW_COMPLETED)
                .changeDate(LocalDateTime.now())
                .responsible(responsible)
                .observations("Revisión final completada automáticamente por aprobación de jurados. " +
                        "Notificando al director de proyecto para programar la sustentación.")
                .build());

        // → Notificar al director para que programe la sustentación
        User projectDirector = studentModality.getProjectDirector();
        if (projectDirector != null) {
            notificationEventPublisher.publish(
                    new ExaminerFinalReviewCompletedEvent(
                            studentModality.getId(),
                            projectDirector.getId()
                    )
            );
        }
    }

    @Transactional
    public ResponseEntity<?> approveModalityByExaminers(Long studentModalityId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User examiner = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        StudentModality studentModality = studentModalityRepository.findById(studentModalityId)
                .orElseThrow(() -> new RuntimeException("Modality not found"));

        Long academicProgramId = studentModality.getAcademicProgram().getId();

        boolean isAuthorized =
                programAuthorityRepository.existsByUser_IdAndAcademicProgram_IdAndRole(
                        examiner.getId(),
                        academicProgramId,
                        ProgramRole.EXAMINER
                );

        if (!isAuthorized) {
            return ResponseEntity.status(403).body(
                    Map.of(
                            "approved", false,
                            "message", "No tienes permisos para aprobar modalidades de este programa académico"
                    )
            );
        }


        if (studentModality.getStatus() != ModalityProcessStatus.EXAMINERS_ASSIGNED &&
            studentModality.getStatus() != ModalityProcessStatus.DOCUMENTS_APPROVED_BY_EXAMINERS &&
            studentModality.getStatus() != ModalityProcessStatus.CANCELLATION_REJECTED) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "approved", false,
                            "message", "La modalidad debe estar en estado EXAMINERS_ASSIGNED o DOCUMENTS_APPROVED_BY_EXAMINERS. " +
                                       "Todos los documentos obligatorios deben haber sido aceptados por los jurados.",
                            "currentStatus", studentModality.getStatus().name(),
                            "requiredStatus", ModalityProcessStatus.EXAMINERS_ASSIGNED.name()
                    )
            );
        }

        Long modalityId =
                studentModality
                        .getProgramDegreeModality()
                        .getDegreeModality()
                        .getId();

        // Solo se validan los documentos MANDATORY que requieren evaluación de propuesta por el jurado
        List<RequiredDocument> mandatoryDocuments =
                requiredDocumentRepository.findByModalityIdAndActiveTrueAndDocumentType(modalityId, DocumentType.MANDATORY)
                        .stream()
                        .filter(RequiredDocument::isRequiresProposalEvaluation)
                        .toList();

        List<StudentDocument> uploadedDocuments =
                studentDocumentRepository.findByStudentModalityId(studentModalityId);

        Map<Long, StudentDocument> uploadedMap =
                uploadedDocuments.stream()
                        .collect(Collectors.toMap(
                                doc -> doc.getDocumentConfig().getId(),
                                doc -> doc
                        ));

        List<Map<String, Object>> invalidDocuments = new ArrayList<>();

        for (RequiredDocument required : mandatoryDocuments) {
            StudentDocument uploaded = uploadedMap.get(required.getId());
            if (uploaded == null) {
                invalidDocuments.add(
                        Map.of(
                                "documentName", required.getDocumentName(),
                                "status", "NOT_UPLOADED"
                        )
                );
                continue;
            }
            if (uploaded.getStatus() != DocumentStatus.ACCEPTED_FOR_EXAMINER_REVIEW) {
                invalidDocuments.add(
                        Map.of(
                                "documentName", required.getDocumentName(),
                                "status", uploaded.getStatus()
                        )
                );
            }
        }

        if (!invalidDocuments.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "approved", false,
                            "message", "Para poder aprobar la modalidad, todos los documentos de propuesta de grado evaluables por los jurados deben estar aceptados",
                            "documents", invalidDocuments
                    )
            );
        }

        studentModality.setStatus(ModalityProcessStatus.PROPOSAL_APPROVED);
        studentModality.setUpdatedAt(LocalDateTime.now());
        studentModalityRepository.save(studentModality);

        historyRepository.save(
                ModalityProcessStatusHistory.builder()
                        .studentModality(studentModality)
                        .status(ModalityProcessStatus.PROPOSAL_APPROVED)
                        .changeDate(LocalDateTime.now())
                        .responsible(examiner)
                        .observations("Modalidad aprobada por los jurados")
                        .build()
        );

        // Notificar a todos los estudiantes miembros activos
        List<StudentModalityMember> activeMembers = studentModalityMemberRepository
                .findByStudentModalityIdAndStatus(studentModality.getId(), MemberStatus.ACTIVE);
        for (StudentModalityMember member : activeMembers) {
            notificationEventPublisher.publish(
                    new ModalityApprovedByExaminers(
                            studentModality.getId(),
                            member.getStudent().getId()
                    )
            );
        }

        return ResponseEntity.ok(
                Map.of(
                        "approved", true,
                        "newStatus", ModalityProcessStatus.PROPOSAL_APPROVED,
                        "message", "Modalidad aprobada correctamente por los jurados"
                )
        );
    }


    public ResponseEntity<?> reviewStudentDocumentByCommittee(Long studentDocumentId, DocumentReviewDTO request) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User committeeMember = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        StudentDocument document = studentDocumentRepository.findById(studentDocumentId)
                .orElseThrow(() -> new RuntimeException("Documento no encontrado"));

        StudentModality studentModality = document.getStudentModality();
        Long academicProgramId = studentModality.getAcademicProgram().getId();
        if (document.getStatus() == DocumentStatus.REJECTED_FOR_PROGRAM_HEAD_REVIEW ||
            document.getStatus() == DocumentStatus.CORRECTIONS_REQUESTED_BY_PROGRAM_HEAD) {
            return ResponseEntity.badRequest().body(
                Map.of(
                    "success", false,
                    "message", "No se puede cambiar el estado del documento porque está en estado " + document.getStatus() + ". El estudiante debe primero corregir y resubir el documento para que pueda ser revisado por el comité de currículo de programa."
                )
            );
        }

        if ( document.getStatus() == DocumentStatus.ACCEPTED_FOR_EXAMINER_REVIEW){
            return ResponseEntity.badRequest().body(
                Map.of(
                    "success", false,
                    "message", "No se puede cambiar el estado del documento porque ya fue aprobado por los jurados. El comité de currículo de programa solo puede revisar documentos que aún no han sido aprobados por los jurados."
                )
            );
        }

        // Validación: no permitir revisión si la modalidad está en un estado propio de la jefatura de programa
        ModalityProcessStatus modalityStatus = studentModality.getStatus();
        if (modalityStatus == ModalityProcessStatus.UNDER_REVIEW_PROGRAM_HEAD ||
            modalityStatus == ModalityProcessStatus.CORRECTIONS_REQUESTED_PROGRAM_HEAD ||
            modalityStatus == ModalityProcessStatus.CORRECTIONS_SUBMITTED_TO_PROGRAM_HEAD) {
            return ResponseEntity.badRequest().body(
                Map.of(
                    "success", false,
                    "message", "No se puede revisar el documento en este momento. La modalidad se encuentra en estado '" +
                               describeModalityStatus(modalityStatus) + "', que corresponde a una etapa de revisión por parte de la Jefatura de Programa. El comité podrá revisar el documento una vez la jefatura finalice su proceso."
                )
            );
        }

        if (modalityStatus == ModalityProcessStatus.CORRECTIONS_SUBMITTED_TO_EXAMINERS) {
            return ResponseEntity.badRequest().body(
                Map.of(
                    "success", false,
                    "message", "No se puede revisar el documento en este momento. La modalidad se encuentra en estado '" +
                               describeModalityStatus(modalityStatus) + "', que corresponde a una etapa de correcciones ya resubmited por parte del estudiante. El comité podrá revisar el documento una vez el estudiante resubmita las correcciones y la modalidad vuelva a un estado de revisión."
                )
            );
        }



        boolean isAuthorized =
                programAuthorityRepository
                        .existsByUser_IdAndAcademicProgram_IdAndRole(
                                committeeMember.getId(),
                                academicProgramId,
                                ProgramRole.PROGRAM_CURRICULUM_COMMITTEE
                        );

        if (!isAuthorized) {
            return ResponseEntity.status(403).body(
                    Map.of(
                            "success", false,
                            "message", "No tienes permisos para revisar documentos de este programa académico"
                    )
            );
        }


        if ((request.getStatus() == DocumentStatus.REJECTED_FOR_PROGRAM_CURRICULUM_COMMITTEE_REVIEW ||
                request.getStatus() == DocumentStatus.CORRECTIONS_REQUESTED_BY_PROGRAM_CURRICULUM_COMMITTEE)
                && (request.getNotes() == null || request.getNotes().isBlank())) {

            return ResponseEntity.badRequest().body(
                    "Debe proporcionar notas al rechazar o solicitar correcciones"
            );
        }






        document.setStatus(request.getStatus());
        document.setNotes(request.getNotes());
        document.setUploadDate(LocalDateTime.now());
        studentDocumentRepository.save(document);


        documentHistoryRepository.save(
                StudentDocumentStatusHistory.builder()
                        .studentDocument(document)
                        .status(request.getStatus())
                        .changeDate(LocalDateTime.now())
                        .responsible(committeeMember)
                        .observations(request.getNotes())
                        .build()
        );


        if (request.getStatus() ==
                DocumentStatus.CORRECTIONS_REQUESTED_BY_PROGRAM_CURRICULUM_COMMITTEE) {


            LocalDateTime now = LocalDateTime.now();

            studentModality.setStatus(ModalityProcessStatus.CORRECTIONS_REQUESTED_PROGRAM_CURRICULUM_COMMITTEE);
            studentModality.setCorrectionRequestDate(now);
            studentModality.setCorrectionDeadline(now.plusDays(30));
            studentModality.setCorrectionReminderSent(false);
            studentModality.setUpdatedAt(now);
            studentModalityRepository.save(studentModality);

            // Registrar cambio de estado en el historial
            historyRepository.save(
                    ModalityProcessStatusHistory.builder()
                            .studentModality(studentModality)
                            .status(ModalityProcessStatus.CORRECTIONS_REQUESTED_PROGRAM_CURRICULUM_COMMITTEE)
                            .changeDate(now)
                            .responsible(committeeMember)
                            .observations("Comité de currículo solicitó correcciones en documento: " +
                                    document.getDocumentConfig().getDocumentName() +
                                    ". Notas: " + request.getNotes())
                            .build()
            );


            List<StudentModalityMember> activeMembers = studentModalityMemberRepository
                    .findByStudentModalityIdAndStatus(studentModality.getId(), MemberStatus.ACTIVE);


            for (StudentModalityMember member : activeMembers) {
                notificationEventPublisher.publish(
                        new DocumentCorrectionsRequestedEvent(
                                document.getId(),
                                member.getStudent().getId(),
                                request.getNotes(),
                                NotificationRecipientType.PROGRAM_CURRICULUM_COMMITTEE,
                                committeeMember.getId()
                        )
                );
            }
        }

        // ========== VERIFICAR SI TODOS LOS MANDATORY HAN SIDO APROBADOS POR EL COMITÉ ==========
        if (request.getStatus() == DocumentStatus.ACCEPTED_FOR_PROGRAM_CURRICULUM_COMMITTEE_REVIEW) {

            DegreeModality degreeModality = studentModality.getProgramDegreeModality().getDegreeModality();

            // Obtener todos los documentos MANDATORY configurados para esta modalidad
            List<RequiredDocument> mandatoryDocs = requiredDocumentRepository
                    .findByModalityIdAndActiveTrueAndDocumentType(degreeModality.getId(), DocumentType.MANDATORY);

            // Obtener los documentos subidos por el estudiante para esta modalidad
            List<StudentDocument> uploadedDocs = studentDocumentRepository
                    .findByStudentModalityId(studentModality.getId());

            // Verificar si todos los MANDATORY están en estado ACCEPTED_FOR_PROGRAM_CURRICULUM_COMMITTEE_REVIEW
            boolean allMandatoryApproved = !mandatoryDocs.isEmpty() && mandatoryDocs.stream().allMatch(req ->
                    uploadedDocs.stream().anyMatch(uploaded ->
                            uploaded.getDocumentConfig().getId().equals(req.getId()) &&
                            uploaded.getStatus() == DocumentStatus.ACCEPTED_FOR_PROGRAM_CURRICULUM_COMMITTEE_REVIEW
                    )
            );

            if (allMandatoryApproved &&
                studentModality.getStatus() != ModalityProcessStatus.READY_FOR_DIRECTOR_ASSIGNMENT &&
                studentModality.getStatus() != ModalityProcessStatus.APPROVED_BY_PROGRAM_CURRICULUM_COMMITTEE &&
                studentModality.getStatus() != ModalityProcessStatus.CANCELLATION_REJECTED) {

                // Determinar el siguiente estado según si la modalidad requiere proceso de sustentación
                boolean requiresDefenseProcess = studentModality.getProgramDegreeModality().isRequiresDefenseProcess();

                if (requiresDefenseProcess) {
                    // Flujo completo: requiere director, jurados y sustentación
                    studentModality.setStatus(ModalityProcessStatus.READY_FOR_DIRECTOR_ASSIGNMENT);
                    studentModality.setUpdatedAt(LocalDateTime.now());
                    studentModalityRepository.save(studentModality);

                    historyRepository.save(
                            ModalityProcessStatusHistory.builder()
                                    .studentModality(studentModality)
                                    .status(ModalityProcessStatus.READY_FOR_DIRECTOR_ASSIGNMENT)
                                    .changeDate(LocalDateTime.now())
                                    .responsible(committeeMember)
                                    .observations("Todos los documentos obligatorios han sido aprobados por el Comité de Currículo. " +
                                            "La modalidad está lista para la asignación del Director de Proyecto.")
                                    .build()
                    );

                    return ResponseEntity.ok(
                            Map.of(
                                    "success", true,
                                    "documentId", document.getId(),
                                    "documentName", document.getDocumentConfig().getDocumentName(),
                                    "newStatus", document.getStatus(),
                                    "newModalityStatus", ModalityProcessStatus.READY_FOR_DIRECTOR_ASSIGNMENT.name(),
                                    "message", "Documento aprobado. Todos los documentos obligatorios han sido aprobados. " +
                                               "La modalidad está lista para la asignación del Director de Proyecto."
                            )
                    );
                } else {
                    // Flujo simplificado: el comité toma decisión final directamente
                    studentModality.setStatus(ModalityProcessStatus.APPROVED_BY_PROGRAM_CURRICULUM_COMMITTEE);
                    studentModality.setUpdatedAt(LocalDateTime.now());
                    studentModalityRepository.save(studentModality);

                    historyRepository.save(
                            ModalityProcessStatusHistory.builder()
                                    .studentModality(studentModality)
                                    .status(ModalityProcessStatus.APPROVED_BY_PROGRAM_CURRICULUM_COMMITTEE)
                                    .changeDate(LocalDateTime.now())
                                    .responsible(committeeMember)
                                    .observations("Todos los documentos obligatorios han sido aprobados por el Comité de Currículo. " +
                                            "Puedes continuar con el proceso de la modalidad ")
                                    .build()
                    );

                    // Notificar a los estudiantes sobre el nuevo estado
                    List<StudentModalityMember> activeMembers = studentModalityMemberRepository
                            .findByStudentModalityIdAndStatus(studentModality.getId(), MemberStatus.ACTIVE);
                    for (StudentModalityMember member : activeMembers) {
                        notificationEventPublisher.publish(
                                new DocumentCorrectionsRequestedEvent(
                                        document.getId(),
                                        member.getStudent().getId(),
                                        "Todos tus documentos han sido aprobados. El Comité de Currículo tomará la decisión final sobre tu modalidad.",
                                        NotificationRecipientType.PROGRAM_CURRICULUM_COMMITTEE,
                                        committeeMember.getId()
                                )
                        );
                    }

                    return ResponseEntity.ok(
                            Map.of(
                                    "success", true,
                                    "documentId", document.getId(),
                                    "documentName", document.getDocumentConfig().getDocumentName(),
                                    "newStatus", document.getStatus(),
                                    "newModalityStatus", ModalityProcessStatus.APPROVED_BY_PROGRAM_CURRICULUM_COMMITTEE.name(),
                                    "message", "Documento aprobado. Todos los documentos obligatorios han sido aprobados. " +
                                               "Puedes continuar con el proceso de la modalidad."
                            )
                    );
                }
            }
        }

        return ResponseEntity.ok(
                Map.of(
                        "success", true,
                        "documentId", document.getId(),
                        "documentName", document.getDocumentConfig().getDocumentName(),
                        "newStatus", document.getStatus(),
                        "message", "Documento revisado correctamente por el comité de currículo de programa"
                )
        );
    }

    private boolean checkIfAllMandatoryDocumentsAcceptedByAllExaminers(Long studentModalityId) {

        StudentModality sm = studentModalityRepository.findById(studentModalityId).orElse(null);
        if (sm == null) return false;

        Long modalityId = sm.getProgramDegreeModality().getDegreeModality().getId();

        // Obtener todos los documentos requeridos MANDATORY activos para esta modalidad
        List<RequiredDocument> mandatoryDocuments = requiredDocumentRepository
                .findByModalityIdAndActiveTrueAndDocumentType(modalityId, DocumentType.MANDATORY);

        if (mandatoryDocuments.isEmpty()) {
            return false;
        }

        // Verificar que haya al menos un jurado asignado
        List<DefenseExaminer> assignedExaminers = defenseExaminerRepository
                .findByStudentModalityId(studentModalityId);

        if (assignedExaminers.isEmpty()) {
            return false;
        }

        // Para cada documento MANDATORY verificar que su estado ACTUAL sea ACCEPTED_FOR_EXAMINER_REVIEW
        for (RequiredDocument reqDoc : mandatoryDocuments) {
            StudentDocument document = studentDocumentRepository
                    .findByStudentModalityIdAndDocumentConfigId(studentModalityId, reqDoc.getId())
                    .orElse(null);

            // Si no fue subido aún, no están todos aceptados
            if (document == null) {
                return false;
            }

            // Si el estado actual del documento NO es ACCEPTED_FOR_EXAMINER_REVIEW, no están todos aceptados
            if (document.getStatus() != DocumentStatus.ACCEPTED_FOR_EXAMINER_REVIEW) {
                return false;
            }
        }

        return true;
    }

    private boolean checkIfAllDocumentsAcceptedByAllExaminers(Long studentModalityId) {


        List<StudentDocument> documents = studentDocumentRepository.findByStudentModalityId(studentModalityId);

        if (documents.isEmpty()) {
            return false;
        }


        List<DefenseExaminer> assignedExaminers = defenseExaminerRepository
                .findByStudentModalityId(studentModalityId);

        if (assignedExaminers.isEmpty()) {
            return false;
        }


        for (StudentDocument document : documents) {


            List<StudentDocumentStatusHistory> documentHistory = documentHistoryRepository
                    .findByStudentDocumentIdOrderByChangeDateDesc(document.getId());


            Set<Long> examinersWhoAccepted = new HashSet<>();

            for (StudentDocumentStatusHistory history : documentHistory) {
                if (history.getStatus() == DocumentStatus.ACCEPTED_FOR_EXAMINER_REVIEW &&
                    history.getResponsible() != null) {
                    examinersWhoAccepted.add(history.getResponsible().getId());
                }
            }


            for (DefenseExaminer examiner : assignedExaminers) {
                if (!examinersWhoAccepted.contains(examiner.getExaminer().getId())) {

                    return false;
                }
            }
        }


        return true;
    }


    private Map<String, Object> validateAllRequiredDocumentsUploaded(Long studentModalityId) {
        StudentModality studentModality = studentModalityRepository.findById(studentModalityId)
                .orElseThrow(() -> new RuntimeException("Modalidad no encontrada"));

        Long modalityId = studentModality.getProgramDegreeModality().getDegreeModality().getId();


        List<RequiredDocument> requiredDocuments = requiredDocumentRepository
                .findByModalityIdAndActiveTrueAndDocumentTypeIn(
                        modalityId,
                        List.of(DocumentType.MANDATORY, DocumentType.SECONDARY)
                );


        List<StudentDocument> uploadedDocuments = studentDocumentRepository
                .findByStudentModalityId(studentModalityId);


        Map<Long, StudentDocument> uploadedMap = uploadedDocuments.stream()
                .collect(Collectors.toMap(
                        d -> d.getDocumentConfig().getId(),
                        d -> d
                ));


        List<Map<String, Object>> missingDocuments = new ArrayList<>();

        for (RequiredDocument required : requiredDocuments) {
            if (!uploadedMap.containsKey(required.getId())) {
                Map<String, Object> docInfo = new HashMap<>();
                docInfo.put("documentId", required.getId());
                docInfo.put("documentName", required.getDocumentName());
                docInfo.put("documentType", required.getDocumentType().toString());
                docInfo.put("description", required.getDescription() != null ? required.getDescription() : "Sin descripción");
                missingDocuments.add(docInfo);
            }
        }

        boolean allUploaded = missingDocuments.isEmpty();

        Map<String, Object> result = new HashMap<>();
        result.put("allDocumentsUploaded", allUploaded);
        result.put("totalRequired", requiredDocuments.size());
        result.put("totalUploaded", uploadedDocuments.size());
        result.put("missingDocuments", missingDocuments);
        result.put("missingCount", missingDocuments.size());

        return result;
    }

    /**
     * Valida que todos los documentos MANDATORY y SECONDARY de la modalidad
     * tengan estado ACCEPTED_FOR_PROGRAM_CURRICULUM_COMMITTEE_REVIEW.
     */
    private Map<String, Object> validateAllDocumentsAcceptedForCommittee(Long studentModalityId) {
        StudentModality studentModality = studentModalityRepository.findById(studentModalityId)
                .orElseThrow(() -> new RuntimeException("Modalidad no encontrada"));

        Long modalityId = studentModality.getProgramDegreeModality().getDegreeModality().getId();

        List<RequiredDocument> requiredDocuments = requiredDocumentRepository
                .findByModalityIdAndActiveTrueAndDocumentTypeIn(
                        modalityId,
                        List.of(DocumentType.MANDATORY, DocumentType.SECONDARY)
                );

        List<StudentDocument> uploadedDocuments = studentDocumentRepository
                .findByStudentModalityId(studentModalityId);

        Map<Long, StudentDocument> uploadedMap = uploadedDocuments.stream()
                .collect(Collectors.toMap(
                        d -> d.getDocumentConfig().getId(),
                        d -> d
                ));

        List<Map<String, Object>> notAcceptedDocuments = new ArrayList<>();

        for (RequiredDocument required : requiredDocuments) {
            StudentDocument uploaded = uploadedMap.get(required.getId());
            boolean accepted = uploaded != null &&
                    uploaded.getStatus() == DocumentStatus.ACCEPTED_FOR_PROGRAM_CURRICULUM_COMMITTEE_REVIEW;
            if (!accepted) {
                Map<String, Object> docInfo = new HashMap<>();
                docInfo.put("documentId", required.getId());
                docInfo.put("documentName", required.getDocumentName());
                docInfo.put("documentType", required.getDocumentType().toString());
                docInfo.put("currentStatus", uploaded != null ? uploaded.getStatus().toString() : "NO_SUBIDO");
                notAcceptedDocuments.add(docInfo);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("allAccepted", notAcceptedDocuments.isEmpty());
        result.put("notAcceptedDocuments", notAcceptedDocuments);
        result.put("notAcceptedCount", notAcceptedDocuments.size());
        result.put("totalRequired", requiredDocuments.size());

        return result;
    }

    private String describeModalityStatus(ModalityProcessStatus status) {
        return switch (status) {
            case MODALITY_SELECTED ->
                    "Haz seleccionado una modalidad de grado. Debes cargar los documentos requeridos para esta modalidad.";
            case UNDER_REVIEW_PROGRAM_HEAD ->
                    "La jefatura del programa y/o coordinación de modalidades está revisando la modalidad de grado. Asegúrate de que todos los documentos obligatorios estén cargados.";
            case CORRECTIONS_REQUESTED_PROGRAM_HEAD ->
                    "La jefatura del programa y/o coordinación de modalidades solicitó correcciones. Debes ajustar la información requerida.";
            case CORRECTIONS_SUBMITTED ->
                    "Las correcciones solicitadas han sido enviadas. Pendiente de aprobación o nuevas observaciones.";
            case CORRECTIONS_SUBMITTED_TO_PROGRAM_HEAD ->
                    "Las correcciones han sido enviadas a la Jefatura de Programa y/o coordinador de modalidades para su revisión.";
            case CORRECTIONS_SUBMITTED_TO_COMMITTEE ->
                    "Las correcciones han sido enviadas al Comité de Currículo de Programa para su revisión.";
            case CORRECTIONS_SUBMITTED_TO_EXAMINERS ->
                    "Las correcciones han sido enviadas a los Jurados evaluadores para su revisión.";
            case CORRECTIONS_APPROVED ->
                    "Las correcciones enviadas han sido aprobadas por la jefatura del programa. El proceso continúa con la siguiente etapa.";
            case CORRECTIONS_REJECTED_FINAL ->
                    "Uno o más documentos no fueron aprobados y/o agotaste el límite de intentos (3). El proceso ha sido cerrado o cancelado.";
            case READY_FOR_PROGRAM_CURRICULUM_COMMITTEE ->
                    "La jefatura de programa y/o cordinación de modalidades aprobó la modalidad de grado. Está pendiente de revisión por el comité de currículo de programa.";
            case UNDER_REVIEW_PROGRAM_CURRICULUM_COMMITTEE ->
                    "El comité de currículo de programa está revisando la modalidad de grado.";
            case CORRECTIONS_REQUESTED_PROGRAM_CURRICULUM_COMMITTEE ->
                    "El comité de currículo de programa solicitó correcciones. Debes ajustar la información requerida.";
            case READY_FOR_DIRECTOR_ASSIGNMENT ->
                    "Todos los documentos obligatorios han sido aprobados por el Comité de Currículo. El comité procederá a asignar el Director de Proyecto (si la modalidad lo requiere).";
            case READY_FOR_APPROVED_BY_PROGRAM_CURRICULUM_COMMITTEE ->
                    "El Director de Proyecto ha sido asignado. El comité de currículo puede proceder con la aprobación formal de la propuesta.";
            case APPROVED_BY_PROGRAM_CURRICULUM_COMMITTEE ->
                    "EL comité de currículo de programa ha aprobado los documentos iniciales, por lo que la modalidad avanza a la siguiente etapa del proceso. El estudiante puede continuar con el proceso de la modalidad de grado.";
            case PROPOSAL_APPROVED ->
                    "La modalidad fue aprobada por el comité de currículo de programa y los jurados asignados. Por favor, continua con el desarrollo normal de tu modalidad de grado.";
            case PENDING_PROGRAM_HEAD_FINAL_REVIEW ->
                    "El director de proyecto notificó a jefatura de programa que los documentos finales están listos. Jefatura revisará los documentos antes de notificar a los jurados.";
            case APPROVED_BY_PROGRAM_HEAD_FINAL_REVIEW ->
                    "Jefatura de programa aprobó los documentos finales. Próximamente los jurados serán notificados para revisión de la sustentación.";
            case DEFENSE_REQUESTED_BY_PROJECT_DIRECTOR ->
                    "El director de proyecto ha propuesto fecha y lugar de sustentación. Pendiente de confirmación...";
            case DEFENSE_SCHEDULED ->
                    "La sustentación ha sido programada por el director de proyecto";
            case EXAMINERS_ASSIGNED ->
                    "Los jurados han sido asignados a la modalidad. Próximo paso: revisión de documentos por parte de los jurados.";
            case READY_FOR_EXAMINERS ->
                    "La modalidad está lista para asignar a los jurados asignados. Próximo paso: Revisión de documentos por parte de los jurados.";
            case DOCUMENTS_APPROVED_BY_EXAMINERS ->
                    "Todos los documentos obligatorios de la propuesta han sido aprobados por los jurados. La modalidad avanza a aprobación de propuesta.";
            case SECONDARY_DOCUMENTS_APPROVED_BY_EXAMINERS ->
                    "Todos los documentos finales han sido aprobados por los jurados. La modalidad avanza a revisión final completada.";
            case DOCUMENT_REVIEW_TIEBREAKER_REQUIRED ->
                    "Los jurados principales tienen decisiones divididas sobre un documento. Se requiere un jurado de desempate para resolver el conflicto.";
            case EDIT_REQUESTED_BY_STUDENT ->
                    "Has solicitado la edición de un documento previamente aprobado. Los jurados evaluadores están evaluando la solicitud. Recibirás una notificación con el resultado.";
            case CORRECTIONS_REQUESTED_EXAMINERS ->
                    "Uno o más jurados solicitaron correcciones en la documentación. Por favor, ajusta los documentos según las observaciones recibidas.";
            case READY_FOR_DEFENSE ->
                    "Jefatura de programa y/o el coordinador de modalidades ha marcado la modalidad como lista para sustentar. Esperando que los jurados designados, revisen los documentos finales y den su aprobación.";
            case FINAL_REVIEW_COMPLETED ->
                    "La revisión final de los jurados ha sido completada. Próximo paso: Director de proyecto programa la sustentación.";
            case DEFENSE_COMPLETED ->
                    "La sustentación se ha completado. Pendiente de calificación final.";
            case UNDER_EVALUATION_PRIMARY_EXAMINERS ->
                    "Los jurados principales están evaluando la sustentación. Cada jurado registra su calificación y decisión de forma independiente.";
            case DISAGREEMENT_REQUIRES_TIEBREAKER ->
                    "Los jurados principales no llegaron a un acuerdo. Se requiere asignar un tercer jurado (desempate).";
            case UNDER_EVALUATION_TIEBREAKER ->
                    "El jurado de desempate está evaluando la sustentación. Su decisión será definitiva.";
            case EVALUATION_COMPLETED ->
                    "La evaluación de la sustentación ha sido completada por los jurados. Próximo paso: resultado final.";
            case PENDING_DISTINCTION_COMMITTEE_REVIEW ->
                    "La modalidad ha sido APROBADA en calificación. Los jurados han propuesto una distinción honorífica (Meritoria o Laureada). El Comité de Currículo debe revisar y decidir si acepta o rechaza la distinción propuesta.";
            case GRADED_APPROVED ->
                    "¡Felicitaciones! Tu modalidad de grado ha sido aprobada.";
            case GRADED_FAILED ->
                    "La modalidad de grado no fue aprobada.";
            case MODALITY_CLOSED ->
                    "La modalidad fue cerrada.";
            case SEMINAR_CANCELED ->
                    "El seminario asociado a la modalidad fue cancelado por la jefatura o el comité correspondiente.";
            case MODALITY_CANCELLED ->
                    "La modalidad fue cancelada.";
            case CANCELLATION_REQUESTED ->
                    "Solicitud de cancelación enviada. Pendiente de revisión.";
            case CANCELLATION_APPROVED_BY_PROJECT_DIRECTOR ->
                    "La solicitud de cancelación fue aprobada por el director de proyecto. Pendiente de revisión por el comité de currículo.";
            case CANCELLATION_REJECTED_BY_PROJECT_DIRECTOR ->
                    "La solicitud de cancelación fue rechazada por el director de proyecto.";
            case CANCELLED_WITHOUT_REPROVAL ->
                    "La modalidad fue cancelada sin reprobación.";
            case CANCELLATION_REJECTED ->
                    "La solicitud de cancelación fue rechazada por el comité de currículo.";
            case CANCELLED_BY_CORRECTION_TIMEOUT ->
                    "La modalidad fue cancelada automáticamente por no entregar las correcciones en el plazo establecido.";
            default ->
                    "Estado del proceso no definido.";
        };
    }

    private String describeDocumentStatus(DocumentStatus status) {
        return switch (status) {
            case PENDING ->
                    "El documento ha sido cargado y está pendiente de revisión.";
            case ACCEPTED_FOR_PROGRAM_HEAD_REVIEW ->
                    "El documento fue aceptado para revisión por la jefatura del programa.";
            case REJECTED_FOR_PROGRAM_HEAD_REVIEW ->
                    "El documento fue rechazado por la jefatura del programa. Revisa las observaciones.";
            case CORRECTIONS_REQUESTED_BY_PROGRAM_HEAD ->
                    "La jefatura del programa solicitó correcciones. Revisa las observaciones y carga una nueva versión.";
            case CORRECTION_RESUBMITTED ->
                    "La corrección ha sido reenviada y está pendiente de revisión.";
            case ACCEPTED_FOR_PROGRAM_CURRICULUM_COMMITTEE_REVIEW ->
                    "El documento fue aceptado para revisión por el comité de currículo del programa.";
            case REJECTED_FOR_PROGRAM_CURRICULUM_COMMITTEE_REVIEW ->
                    "El documento fue rechazado por el comité de currículo del programa. Revisa las observaciones.";
            case CORRECTIONS_REQUESTED_BY_PROGRAM_CURRICULUM_COMMITTEE ->
                    "El comité de currículo del programa solicitó correcciones. Revisa las observaciones y carga una nueva versión.";
            default ->
                    "Estado del documento no definido.";
        };
    }

    public ResponseEntity<?> getCurrentStudentModality() {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User student = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        StudentModality studentModality = studentModalityRepository
                .findTopByStudentIdOrderByUpdatedAtDesc(student.getId())
                .orElseThrow(() ->
                        new RuntimeException("Not current modality found for the student")
                );


        DegreeModality modality = studentModality.getProgramDegreeModality().getDegreeModality();


        StudentProfile studentProfile = studentProfileRepository.findByUserId(student.getId())
                .orElse(null);


        List<ModalityProcessStatusHistory> historyEntities =
                historyRepository.findByStudentModalityIdOrderByChangeDateAsc(
                        studentModality.getId()
                );

        // Ordenar el historial de más reciente a más antiguo
        List<ModalityStatusHistoryDTO> history = historyEntities.stream()
                .map(h -> ModalityStatusHistoryDTO.builder()
                        .status(h.getStatus().name())
                        .description(describeModalityStatus(h.getStatus()))
                        .changeDate(h.getChangeDate())
                        .responsible(
                                h.getResponsible() != null
                                        ? h.getResponsible().getEmail()
                                        : "Sistema"
                        )
                        .observations(h.getObservations())
                        .build()
                )
                .sorted((h1, h2) -> h2.getChangeDate().compareTo(h1.getChangeDate())) // Más reciente arriba
                .toList();


        List<StudentDocument> documents = studentDocumentRepository
                .findByStudentModalityId(studentModality.getId());

        List<DetailDocumentDTO> documentDTOs = documents.stream()
                .map(doc -> DetailDocumentDTO.builder()
                        .requiredDocumentId(doc.getDocumentConfig().getId())
                        .studentDocumentId(doc.getId())
                        .documentName(doc.getDocumentConfig().getDocumentName())
                        .documentType(doc.getDocumentConfig().getDocumentType())
                        .status(doc.getStatus().name())
                        .statusDescription(describeDocumentStatus(doc.getStatus()))
                        .notes(doc.getNotes())
                        .lastUpdate(doc.getUploadDate())
                        .uploaded(true)
                        .build()
                )
                .toList();


        long approvedDocs = documents.stream()
                .filter(d -> d.getStatus() == DocumentStatus.ACCEPTED_FOR_PROGRAM_CURRICULUM_COMMITTEE_REVIEW)
                .count();
        long pendingDocs = documents.stream()
                .filter(d -> d.getStatus() == DocumentStatus.PENDING ||
                        d.getStatus() == DocumentStatus.ACCEPTED_FOR_PROGRAM_HEAD_REVIEW ||
                        d.getStatus() == DocumentStatus.CORRECTION_RESUBMITTED)
                .count();
        long rejectedDocs = documents.stream()
                .filter(d -> d.getStatus() == DocumentStatus.REJECTED_FOR_PROGRAM_HEAD_REVIEW ||
                        d.getStatus() == DocumentStatus.REJECTED_FOR_PROGRAM_CURRICULUM_COMMITTEE_REVIEW)
                .count();


        Long daysRemaining = null;
        if (studentModality.getCorrectionDeadline() != null) {
            daysRemaining = ChronoUnit.DAYS.between(
                    LocalDateTime.now(),
                    studentModality.getCorrectionDeadline()
            );
        }


        ModalityProcessStatus status = studentModality.getStatus();
        boolean canUploadDocuments = status == ModalityProcessStatus.MODALITY_SELECTED ||
                status == ModalityProcessStatus.UNDER_REVIEW_PROGRAM_HEAD ||
                status == ModalityProcessStatus.CORRECTIONS_REQUESTED_PROGRAM_HEAD ||
                status == ModalityProcessStatus.CORRECTIONS_REQUESTED_PROGRAM_CURRICULUM_COMMITTEE;

        boolean canRequestCancellation = status != ModalityProcessStatus.MODALITY_CLOSED &&
                status != ModalityProcessStatus.GRADED_APPROVED &&
                status != ModalityProcessStatus.GRADED_FAILED &&
                !status.name().startsWith("CANCELLED");

        boolean canSubmitCorrections = (status == ModalityProcessStatus.CORRECTIONS_REQUESTED_PROGRAM_HEAD ||
                status == ModalityProcessStatus.CORRECTIONS_REQUESTED_PROGRAM_CURRICULUM_COMMITTEE) &&
                studentModality.getCorrectionDeadline() != null &&
                LocalDateTime.now().isBefore(studentModality.getCorrectionDeadline());

        boolean hasDefenseScheduled = studentModality.getDefenseDate() != null;

        boolean requiresAction = canUploadDocuments || canSubmitCorrections ||
                status == ModalityProcessStatus.CORRECTIONS_REQUESTED_PROGRAM_HEAD ||
                status == ModalityProcessStatus.CORRECTIONS_REQUESTED_PROGRAM_CURRICULUM_COMMITTEE;


        User projectDirector = studentModality.getProjectDirector();
        String defenseProposedBy = null;
        if (status == ModalityProcessStatus.DEFENSE_REQUESTED_BY_PROJECT_DIRECTOR) {
            defenseProposedBy = "El director de proyecto ha propuesto una fecha de sustentación";
        }

        return ResponseEntity.ok(
                StudentModalityDTO.builder()

                        .studentId(student.getId())
                        .studentName(student.getName())
                        .studentLastName(student.getLastName())
                        .studentEmail(student.getEmail())
                        .studentCode(studentProfile != null ? studentProfile.getStudentCode() : null)
                        .approvedCredits(studentProfile != null ? studentProfile.getApprovedCredits() : null)
                        .gpa(studentProfile != null ? studentProfile.getGpa() : null)
                        .semester(studentProfile != null ? studentProfile.getSemester() : null)


                        .facultyName(studentModality.getProgramDegreeModality()
                                .getAcademicProgram().getFaculty().getName())
                        .academicProgramName(studentModality.getProgramDegreeModality()
                                .getAcademicProgram().getName())


                        .studentModalityId(studentModality.getId())
                        .modalityId(modality.getId())
                        .modalityName(modality.getName())
                        .modalityDescription(modality.getDescription())
                        .creditsRequired(studentModality.getProgramDegreeModality()
                                .getCreditsRequired())
                        .modalityType(null)


                        .currentStatus(status.name())
                        .currentStatusDescription(describeModalityStatus(status))
                        .selectionDate(studentModality.getSelectionDate())
                        .lastUpdatedAt(studentModality.getUpdatedAt())


                        .projectDirectorId(projectDirector != null ? projectDirector.getId() : null)
                        .projectDirectorName(projectDirector != null
                                ? projectDirector.getName() + " " + projectDirector.getLastName()
                                : null)
                        .projectDirectorEmail(projectDirector != null ? projectDirector.getEmail() : null)


                        .defenseDate(studentModality.getDefenseDate())
                        .defenseLocation(studentModality.getDefenseLocation())
                        .defenseProposedByProjectDirector(defenseProposedBy)


                        .academicDistinction(studentModality.getAcademicDistinction() != null
                                ? studentModality.getAcademicDistinction().name()
                                : null)


                        .correctionRequestDate(studentModality.getCorrectionRequestDate())
                        .correctionDeadline(studentModality.getCorrectionDeadline())
                        .correctionReminderSent(studentModality.getCorrectionReminderSent())
                        .daysRemainingForCorrection(daysRemaining)


                        .documents(documentDTOs)
                        .totalDocuments(documents.size())
                        .approvedDocuments((int) approvedDocs)
                        .pendingDocuments((int) pendingDocs)
                        .rejectedDocuments((int) rejectedDocs)


                        .history(history)


                        .canUploadDocuments(canUploadDocuments)
                        .canRequestCancellation(canRequestCancellation)
                        .canSubmitCorrections(canSubmitCorrections)
                        .hasDefenseScheduled(hasDefenseScheduled)
                        .requiresAction(requiresAction)

                        .build()
        );
    }

    public ResponseEntity<?> getAllStudentModalitiesForProgramHead(List<ModalityProcessStatus> statuses, String name) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User programHead = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        List<Long> programIds = programAuthorityRepository
                        .findByUser_Id(programHead.getId())
                        .stream()
                        .filter(pa -> pa.getRole() == ProgramRole.PROGRAM_HEAD)
                        .map(pa -> pa.getAcademicProgram().getId())
                        .toList();

        if (programIds.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        boolean hasStatusFilter = statuses != null && !statuses.isEmpty();
        boolean hasNameFilter = name != null && !name.isBlank();

        List<StudentModality> modalities;

        if (hasStatusFilter && hasNameFilter) {

            modalities =
                    studentModalityRepository.findForProgramHeadWithStatusAndName(programIds, statuses, name);

        } else if (hasStatusFilter) {

            modalities =
                    studentModalityRepository
                            .findForProgramHeadWithStatus(programIds, statuses);

        } else if (hasNameFilter) {

            modalities =
                    studentModalityRepository
                            .findForProgramHeadWithName(programIds, name);

        } else {

            modalities =
                    studentModalityRepository
                            .findForProgramHead(programIds);
        }

        List<ModalityListDTO> response =
                modalities.stream()
                        .map(sm -> {

                            ModalityProcessStatus status = sm.getStatus();

                            boolean pending =
                                    status == ModalityProcessStatus.MODALITY_SELECTED ||
                                            status == ModalityProcessStatus.CORRECTIONS_REQUESTED_PROGRAM_HEAD;

                            // Obtener todos los miembros activos de la modalidad
                            List<StudentModalityMember> activeMembers =
                                studentModalityMemberRepository.findByStudentModalityIdAndStatus(
                                    sm.getId(),
                                    MemberStatus.ACTIVE
                                );

                            // Concatenar nombres de los miembros (solo nombres, no apellidos)
                            String studentNames = activeMembers.stream()
                                    .map(member -> member.getStudent().getName() + " " + member.getStudent().getLastName())
                                    .collect(Collectors.joining(", "));

                            // Concatenar emails de los miembros
                            String studentEmails = activeMembers.stream()
                                    .map(member -> member.getStudent().getEmail())
                                    .collect(Collectors.joining(", "));

                            return ModalityListDTO.builder()
                                    .studentModalityId(sm.getId())
                                    .studentName(studentNames)
                                    .studentEmail(studentEmails)
                                    .modalityName(
                                            sm.getProgramDegreeModality()
                                                    .getDegreeModality()
                                                    .getName()
                                    )
                                    .currentStatus(status.name())
                                    .currentStatusDescription(describeModalityStatus(status))
                                    .lastUpdatedAt(sm.getUpdatedAt())
                                    .hasPendingActions(pending)
                                    .build();
                        })
                        .sorted(Comparator.comparing(ModalityListDTO::getLastUpdatedAt).reversed())
                        .toList();

        return ResponseEntity.ok(response);
    }

    public ResponseEntity<?> getAllStudentModalitiesForProgramCurriculumCommittee(List<ModalityProcessStatus> statuses, String name) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User committeeMember = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        List<Long> programIds = programAuthorityRepository
                .findByUser_Id(committeeMember.getId())
                .stream()
                .filter(pa -> pa.getRole() == ProgramRole.PROGRAM_CURRICULUM_COMMITTEE)
                .map(pa -> pa.getAcademicProgram().getId())
                .toList();

        if (programIds.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }


        List<ModalityProcessStatus> committeeRelevantStatuses = Arrays.asList(ModalityProcessStatus.values());


        List<ModalityProcessStatus> finalStatuses;
        if (statuses != null && !statuses.isEmpty()) {

            finalStatuses = statuses.stream()
                    .filter(committeeRelevantStatuses::contains)
                    .toList();


            if (finalStatuses.isEmpty()) {
                return ResponseEntity.ok(List.of());
            }
        } else {

            finalStatuses = committeeRelevantStatuses;
        }

        boolean hasNameFilter = name != null && !name.isBlank();

        List<StudentModality> modalities;

        if (hasNameFilter) {
            modalities = studentModalityRepository
                    .findForProgramHeadWithStatusAndName(programIds, finalStatuses, name);
        } else {
            modalities = studentModalityRepository
                    .findForProgramHeadWithStatus(programIds, finalStatuses);
        }

        List<ModalityListDTO> response =
                modalities.stream()
                        .map(sm -> {

                            ModalityProcessStatus status = sm.getStatus();

                            boolean pending =
                                    status == ModalityProcessStatus.READY_FOR_PROGRAM_CURRICULUM_COMMITTEE ||
                                            status == ModalityProcessStatus.UNDER_REVIEW_PROGRAM_CURRICULUM_COMMITTEE ||
                                            status == ModalityProcessStatus.DEFENSE_REQUESTED_BY_PROJECT_DIRECTOR;

                            // Obtener todos los miembros activos de la modalidad
                            List<StudentModalityMember> activeMembers =
                                    studentModalityMemberRepository.findByStudentModalityIdAndStatus(
                                            sm.getId(),
                                            MemberStatus.ACTIVE
                                    );

                            // Concatenar nombres de los miembros (solo nombres, no apellidos)
                            String studentNames = activeMembers.stream()
                                    .map(member -> member.getStudent().getName() + " " + member.getStudent().getLastName())
                                    .collect(Collectors.joining(", "));

                            // Concatenar emails de los miembros
                            String studentEmails = activeMembers.stream()
                                    .map(member -> member.getStudent().getEmail())
                                    .collect(Collectors.joining(", "));

                            return ModalityListDTO.builder()
                                    .studentModalityId(sm.getId())
                                    .studentName(studentNames)
                                    .studentEmail(studentEmails)
                                    .modalityName(
                                            sm.getProgramDegreeModality()
                                                    .getDegreeModality()
                                                    .getName()
                                    )
                                    .currentStatus(status.name())
                                    .currentStatusDescription(describeModalityStatus(status))
                                    .lastUpdatedAt(sm.getUpdatedAt())
                                    .hasPendingActions(pending)
                                    .build();
                        })
                        .sorted(Comparator.comparing(ModalityListDTO::getLastUpdatedAt).reversed())
                        .toList();



        return ResponseEntity.ok(response);
    }

    public ResponseEntity<?> getAllStudentModalitiesForProjectDirector(List<ModalityProcessStatus> statuses, String name) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User projectDirector = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));


        List<ProgramAuthority> directorAuthorities = programAuthorityRepository
                .findByUser_Id(projectDirector.getId())
                .stream()
                .filter(pa -> pa.getRole() == ProgramRole.PROJECT_DIRECTOR)
                .toList();

        if (directorAuthorities.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("El usuario no tiene el rol de PROJECT_DIRECTOR");
        }

        boolean hasStatusFilter = statuses != null && !statuses.isEmpty();
        boolean hasNameFilter = name != null && !name.isBlank();

        List<StudentModality> modalities;

        if (hasStatusFilter && hasNameFilter) {
            modalities = studentModalityRepository
                    .findForProjectDirectorWithStatusAndName(projectDirector.getId(), statuses, name);
        } else if (hasStatusFilter) {
            modalities = studentModalityRepository
                    .findForProjectDirectorWithStatus(projectDirector.getId(), statuses);
        } else if (hasNameFilter) {
            modalities = studentModalityRepository
                    .findForProjectDirectorWithName(projectDirector.getId(), name);
        } else {
            modalities = studentModalityRepository
                    .findForProjectDirector(projectDirector.getId());
        }

        List<ModalityListDTO> response = modalities.stream()
                .map(sm -> {
                    ModalityProcessStatus status = sm.getStatus();


                    boolean pending =
                            status == ModalityProcessStatus.PROPOSAL_APPROVED ||
                            status == ModalityProcessStatus.CANCELLATION_REQUESTED;


                    // Obtener todos los miembros activos de la modalidad
                    List<StudentModalityMember> activeMembers =
                            studentModalityMemberRepository.findByStudentModalityIdAndStatus(
                                    sm.getId(),
                                    MemberStatus.ACTIVE
                            );

                    // Concatenar nombres de los miembros (solo nombres, no apellidos)
                    String studentNames = activeMembers.stream()
                            .map(member -> member.getStudent().getName() + " " + member.getStudent().getLastName())
                            .collect(Collectors.joining(", "));

                    // Concatenar emails de los miembros
                    String studentEmails = activeMembers.stream()
                            .map(member -> member.getStudent().getEmail())
                            .collect(Collectors.joining(", "));


                    return ModalityListDTO.builder()
                            .studentModalityId(sm.getId())
                            .studentName(studentNames)
                            .studentEmail(studentEmails)
                            .modalityName(sm.getProgramDegreeModality().getDegreeModality().getName())
                            .currentStatus(status.name())
                            .currentStatusDescription(describeModalityStatus(status))
                            .lastUpdatedAt(sm.getUpdatedAt())
                            .hasPendingActions(pending)
                            .build();
                })
                .sorted(Comparator.comparing(ModalityListDTO::getLastUpdatedAt).reversed())
                .toList();

        return ResponseEntity.ok(response);
    }

    public ResponseEntity<?> getAllStudentModalitiesForExaminer(List<ModalityProcessStatus> statuses, String name) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User examiner = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));


        List<DefenseExaminer> examinerAssignments = defenseExaminerRepository
                .findByExaminerId(examiner.getId());

        if (examinerAssignments.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        boolean hasStatusFilter = statuses != null && !statuses.isEmpty();
        boolean hasNameFilter = name != null && !name.isBlank();

        List<StudentModality> modalities;

        if (hasStatusFilter && hasNameFilter) {
            modalities = studentModalityRepository
                    .findForExaminerWithStatusAndName(examiner.getId(), statuses, name);
        } else if (hasStatusFilter) {
            modalities = studentModalityRepository
                    .findForExaminerWithStatus(examiner.getId(), statuses);
        } else if (hasNameFilter) {
            modalities = studentModalityRepository
                    .findForExaminerWithName(examiner.getId(), name);
        } else {
            modalities = studentModalityRepository
                    .findForExaminer(examiner.getId());
        }

        List<ModalityListDTO> response = modalities.stream()
                .map(sm -> {
                    ModalityProcessStatus status = sm.getStatus();


                    boolean pending =
                            status == ModalityProcessStatus.DEFENSE_SCHEDULED;

                    // Obtener todos los miembros activos de la modalidad
                    List<StudentModalityMember> activeMembers =
                            studentModalityMemberRepository.findByStudentModalityIdAndStatus(
                                    sm.getId(),
                                    MemberStatus.ACTIVE
                            );

                    // Concatenar nombres de los miembros (solo nombres, no apellidos)
                    String studentNames = activeMembers.stream()
                            .map(member -> member.getStudent().getName() + " " + member.getStudent().getLastName())
                            .collect(Collectors.joining(", "));

                    // Concatenar emails de los miembros
                    String studentEmails = activeMembers.stream()
                            .map(member -> member.getStudent().getEmail())
                            .collect(Collectors.joining(", "));



                    return ModalityListDTO.builder()
                            .studentModalityId(sm.getId())
                            .studentName(studentNames)
                            .studentEmail(studentEmails)
                            .modalityName(sm.getProgramDegreeModality().getDegreeModality().getName())
                            .currentStatus(status.name())
                            .currentStatusDescription(describeModalityStatus(status))
                            .lastUpdatedAt(sm.getUpdatedAt())
                            .hasPendingActions(pending)
                            .build();
                })
                .sorted(Comparator.comparing(ModalityListDTO::getLastUpdatedAt).reversed())
                .toList();

        return ResponseEntity.ok(response);
    }

    public ResponseEntity<?> getStudentModalityDetailForProgramHead(Long studentModalityId) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User programHead = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        StudentModality studentModality = studentModalityRepository.findById(studentModalityId)
                .orElseThrow(() -> new RuntimeException("Modality not found"));

        AcademicProgram academicProgram = studentModality.getProgramDegreeModality().getAcademicProgram();

        boolean authorized =
                programAuthorityRepository.existsByUser_IdAndAcademicProgram_IdAndRole(
                        programHead.getId(),
                        academicProgram.getId(),
                        ProgramRole.PROGRAM_HEAD
                );

        if (!authorized) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("No tiene permiso para ver esta modalidad");
        }

        // Obtener todos los miembros activos de la modalidad
        List<StudentModalityMember> activeMembers =
            studentModalityMemberRepository.findByStudentModalityIdAndStatus(
                studentModalityId,
                MemberStatus.ACTIVE
            );

        // Para compatibilidad, usar el líder como estudiante principal
        User student = studentModality.getLeader();
        DegreeModality modality =
                studentModality.getProgramDegreeModality().getDegreeModality();


        StudentProfile studentProfile = studentProfileRepository.findByUserId(student.getId())
                .orElse(null);


        List<ModalityStatusHistoryDTO> history =
                historyRepository
                        .findByStudentModalityIdOrderByChangeDateAsc(studentModalityId)
                        .stream()
                        .map(h -> ModalityStatusHistoryDTO.builder()
                                .status(h.getStatus().name())
                                .description(describeModalityStatus(h.getStatus()))
                                .changeDate(h.getChangeDate())
                                .responsible(
                                        h.getResponsible() != null
                                                ? h.getResponsible().getEmail()
                                                : "Sistema"
                                )
                                .observations(h.getObservations())
                                .build()
                        )
                        .sorted((h1, h2) -> h2.getChangeDate().compareTo(h1.getChangeDate())) // Ordenar de más reciente a más antiguo
                        .toList();


        List<RequiredDocument> requiredDocuments =
                requiredDocumentRepository
                        .findByModalityIdAndActiveTrue(modality.getId());

        List<StudentDocument> uploadedDocuments =
                studentDocumentRepository
                        .findByStudentModalityId(studentModalityId);

        Map<Long, StudentDocument> uploadedMap =
                uploadedDocuments.stream()
                        .collect(Collectors.toMap(
                                d -> d.getDocumentConfig().getId(),
                                d -> d
                        ));

        List<DetailDocumentDTO> documents =
                requiredDocuments.stream()
                        .map(req -> {
                            StudentDocument uploaded = uploadedMap.get(req.getId());

                            return DetailDocumentDTO.builder()
                                    .requiredDocumentId(req.getId())
                                    .studentDocumentId(
                                            uploaded != null ? uploaded.getId() : null
                                    )
                                    .documentName(req.getDocumentName())
                                    .documentType(req.getDocumentType())
                                    .uploaded(uploaded != null)
                                    .status(
                                            uploaded != null
                                                    ? uploaded.getStatus().name()
                                                    : "NOT_UPLOADED"
                                    )
                                    .statusDescription(
                                            uploaded != null
                                                    ? describeDocumentStatus(uploaded.getStatus())
                                                    : "Documento aún no cargado por el estudiante."
                                    )
                                    .notes(
                                            uploaded != null ? uploaded.getNotes() : null
                                    )
                                    .lastUpdate(
                                            uploaded != null ? uploaded.getUploadDate() : null
                                    )
                                    .build();
                        })
                        .toList();


        long approvedDocs = uploadedDocuments.stream()
                .filter(d -> d.getStatus() == DocumentStatus.ACCEPTED_FOR_PROGRAM_CURRICULUM_COMMITTEE_REVIEW)
                .count();
        long pendingDocs = uploadedDocuments.stream()
                .filter(d -> d.getStatus() == DocumentStatus.PENDING ||
                            d.getStatus() == DocumentStatus.ACCEPTED_FOR_PROGRAM_HEAD_REVIEW ||
                            d.getStatus() == DocumentStatus.CORRECTION_RESUBMITTED)
                .count();
        long rejectedDocs = uploadedDocuments.stream()
                .filter(d -> d.getStatus() == DocumentStatus.REJECTED_FOR_PROGRAM_HEAD_REVIEW ||
                            d.getStatus() == DocumentStatus.REJECTED_FOR_PROGRAM_CURRICULUM_COMMITTEE_REVIEW)
                .count();


        Long daysRemaining = null;
        if (studentModality.getCorrectionDeadline() != null) {
            daysRemaining = ChronoUnit.DAYS.between(
                    LocalDateTime.now(),
                    studentModality.getCorrectionDeadline()
            );
        }


        ModalityProcessStatus status = studentModality.getStatus();
        boolean canUploadDocuments = status == ModalityProcessStatus.MODALITY_SELECTED ||
                                    status == ModalityProcessStatus.UNDER_REVIEW_PROGRAM_HEAD ||
                                    status == ModalityProcessStatus.CORRECTIONS_REQUESTED_PROGRAM_HEAD ||
                                    status == ModalityProcessStatus.CORRECTIONS_REQUESTED_PROGRAM_CURRICULUM_COMMITTEE;

        boolean canRequestCancellation = status != ModalityProcessStatus.MODALITY_CLOSED &&
                                        status != ModalityProcessStatus.GRADED_APPROVED &&
                                        status != ModalityProcessStatus.GRADED_FAILED &&
                                        !status.name().startsWith("CANCELLED");

        boolean canSubmitCorrections = (status == ModalityProcessStatus.CORRECTIONS_REQUESTED_PROGRAM_HEAD ||
                                       status == ModalityProcessStatus.CORRECTIONS_REQUESTED_PROGRAM_CURRICULUM_COMMITTEE) &&
                                      studentModality.getCorrectionDeadline() != null &&
                                      LocalDateTime.now().isBefore(studentModality.getCorrectionDeadline());

        boolean hasDefenseScheduled = studentModality.getDefenseDate() != null;

        boolean requiresAction = canUploadDocuments || canSubmitCorrections ||
                                status == ModalityProcessStatus.CORRECTIONS_REQUESTED_PROGRAM_HEAD ||
                                status == ModalityProcessStatus.CORRECTIONS_REQUESTED_PROGRAM_CURRICULUM_COMMITTEE;


        User projectDirector = studentModality.getProjectDirector();
        String defenseProposedBy = null;
        if (status == ModalityProcessStatus.DEFENSE_REQUESTED_BY_PROJECT_DIRECTOR) {
            defenseProposedBy = "El director de proyecto ha propuesto una fecha de sustentación";
        }


        List<ModalityMemberDTO> memberDTOs = activeMembers.stream()
                .map(member -> {
                    StudentProfile memberProfile = studentProfileRepository
                            .findByUserId(member.getStudent().getId())
                            .orElse(null);


                    return ModalityMemberDTO.builder()
                        .memberId(member.getId())
                        .studentId(member.getStudent().getId())
                        .studentName(member.getStudent().getName())
                        .studentLastName(member.getStudent().getLastName())
                        .studentEmail(member.getStudent().getEmail())
                        .studentCode(memberProfile != null ? memberProfile.getStudentCode() : null)
                        .approvedCredits(memberProfile != null ? (memberProfile.getApprovedCredits() != null ? memberProfile.getApprovedCredits().intValue() : null) : null)
                        .gpa(memberProfile != null ? (memberProfile.getGpa() != null ? memberProfile.getGpa().doubleValue() : null) : null)
                        .semester(memberProfile != null ? (memberProfile.getSemester() != null ? memberProfile.getSemester().toString() : null) : null)
                        .isLeader(member.getIsLeader())
                        .status(member.getStatus().name())
                        .joinedAt(member.getJoinedAt())
                        .build();

                })
                .toList();

        return ResponseEntity.ok(
                StudentModalityDTO.builder()
                        .studentId(student.getId())
                        .studentName(student.getName())
                        .studentLastName(student.getLastName())
                        .studentEmail(student.getEmail())
                        .studentCode(studentProfile != null ? studentProfile.getStudentCode() : null)
                        .approvedCredits(studentProfile != null ? studentProfile.getApprovedCredits() : null)
                        .gpa(studentProfile != null ? studentProfile.getGpa() : null)
                        .semester(studentProfile != null ? studentProfile.getSemester() : null)


                        .facultyName(academicProgram.getFaculty().getName())
                        .academicProgramName(academicProgram.getName())


                        .studentModalityId(studentModality.getId())
                        .modalityName(modality.getName())
                        .modalityDescription(modality.getDescription())
                        .creditsRequired(studentModality.getProgramDegreeModality().getCreditsRequired())
                        .modalityType(studentModality.getModalityType() != null
                                ? studentModality.getModalityType().name()
                                : null)
                        .members(memberDTOs)


                        .currentStatus(status.name())
                        .currentStatusDescription(describeModalityStatus(status))
                        .selectionDate(studentModality.getSelectionDate())
                        .lastUpdatedAt(studentModality.getUpdatedAt())


                        .projectDirectorId(projectDirector != null ? projectDirector.getId() : null)
                        .projectDirectorName(projectDirector != null
                                ? projectDirector.getName() + " " + projectDirector.getLastName()
                                : null)
                        .projectDirectorEmail(projectDirector != null ? projectDirector.getEmail() : null)


                        .defenseDate(studentModality.getDefenseDate())
                        .defenseLocation(studentModality.getDefenseLocation())
                        .defenseProposedByProjectDirector(defenseProposedBy)


                        .academicDistinction(studentModality.getAcademicDistinction() != null
                                ? studentModality.getAcademicDistinction().name()
                                : null)


                        .correctionRequestDate(studentModality.getCorrectionRequestDate())
                        .correctionDeadline(studentModality.getCorrectionDeadline())
                        .correctionReminderSent(studentModality.getCorrectionReminderSent())
                        .daysRemainingForCorrection(daysRemaining)


                        .documents(documents)
                        .totalDocuments(uploadedDocuments.size())
                        .approvedDocuments((int) approvedDocs)
                        .pendingDocuments((int) pendingDocs)
                        .rejectedDocuments((int) rejectedDocs)


                        .history(history)


                        .canUploadDocuments(canUploadDocuments)
                        .canRequestCancellation(canRequestCancellation)
                        .canSubmitCorrections(canSubmitCorrections)
                        .hasDefenseScheduled(hasDefenseScheduled)
                        .requiresAction(requiresAction)

                        .build()
        );
    }

    public ResponseEntity<?> getStudentModalityDetailForCommittee(Long studentModalityId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User committeeMember = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        StudentModality studentModality = studentModalityRepository.findById(studentModalityId)
                .orElseThrow(() -> new RuntimeException("Modality not found"));

        AcademicProgram academicProgram = studentModality.getProgramDegreeModality().getAcademicProgram();

        boolean authorized =
                programAuthorityRepository.existsByUser_IdAndAcademicProgram_IdAndRole(
                        committeeMember.getId(),
                        academicProgram.getId(),
                        ProgramRole.PROGRAM_CURRICULUM_COMMITTEE
                );

        if (!authorized) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("No tiene permiso para ver esta modalidad");
        }

        // Obtener todos los miembros activos de la modalidad
        List<StudentModalityMember> activeMembers =
            studentModalityMemberRepository.findByStudentModalityIdAndStatus(
                studentModalityId,
                MemberStatus.ACTIVE
            );

        // Usar el líder como estudiante principal
        User student = studentModality.getLeader();
        DegreeModality modality =
                studentModality.getProgramDegreeModality().getDegreeModality();

        StudentProfile studentProfile = studentProfileRepository.findByUserId(student.getId())
                .orElse(null);

        List<ModalityStatusHistoryDTO> history =
                historyRepository
                        .findByStudentModalityIdOrderByChangeDateAsc(studentModalityId)
                        .stream()
                        .map(h -> ModalityStatusHistoryDTO.builder()
                                .status(h.getStatus().name())
                                .description(describeModalityStatus(h.getStatus()))
                                .changeDate(h.getChangeDate())
                                .responsible(
                                        h.getResponsible() != null
                                                ? h.getResponsible().getEmail()
                                                : "Sistema"
                                )
                                .observations(h.getObservations())
                                .build()
                        )
                        .sorted((h1, h2) -> h2.getChangeDate().compareTo(h1.getChangeDate())) // Ordenar de más reciente a más antiguo
                        .toList();

        // El comité puede ver TODOS los documentos requeridos (sin filtro por requiresProposalEvaluation)
        List<RequiredDocument> requiredDocuments =
                requiredDocumentRepository
                        .findByModalityIdAndActiveTrue(modality.getId());

        List<StudentDocument> uploadedDocuments =
                studentDocumentRepository
                        .findByStudentModalityId(studentModalityId);


        Map<Long, StudentDocument> uploadedMap =
                uploadedDocuments.stream()
                        .collect(Collectors.toMap(
                                d -> d.getDocumentConfig().getId(),
                                d -> d
                        ));

        List<DetailDocumentDTO> documents =
                requiredDocuments.stream()
                        .map(req -> {
                            StudentDocument uploaded = uploadedMap.get(req.getId());
                            return DetailDocumentDTO.builder()
                                    .requiredDocumentId(req.getId())
                                    .studentDocumentId(
                                            uploaded != null ? uploaded.getId() : null
                                    )
                                    .documentName(req.getDocumentName())
                                    .documentType(req.getDocumentType())
                                    .uploaded(uploaded != null)
                                    .status(
                                            uploaded != null
                                                    ? uploaded.getStatus().name()
                                                    : "NOT_UPLOADED"
                                    )
                                    .statusDescription(
                                            uploaded != null
                                                    ? describeDocumentStatus(uploaded.getStatus())
                                                    : "Documento aún no cargado por el estudiante."
                                    )
                                    .notes(
                                            uploaded != null ? uploaded.getNotes() : null
                                    )
                                    .lastUpdate(
                                            uploaded != null ? uploaded.getUploadDate() : null
                                    )
                                    .build();
                        })
                        .toList();

        long approvedDocs = uploadedDocuments.stream()
                .filter(d -> d.getStatus() == DocumentStatus.ACCEPTED_FOR_PROGRAM_CURRICULUM_COMMITTEE_REVIEW)
                .count();
        long pendingDocs = uploadedDocuments.stream()
                .filter(d -> d.getStatus() == DocumentStatus.PENDING ||
                            d.getStatus() == DocumentStatus.ACCEPTED_FOR_PROGRAM_HEAD_REVIEW ||
                            d.getStatus() == DocumentStatus.CORRECTION_RESUBMITTED)
                .count();
        long rejectedDocs = uploadedDocuments.stream()
                .filter(d -> d.getStatus() == DocumentStatus.REJECTED_FOR_PROGRAM_HEAD_REVIEW ||
                            d.getStatus() == DocumentStatus.REJECTED_FOR_PROGRAM_CURRICULUM_COMMITTEE_REVIEW)
                .count();

        Long daysRemaining = null;
        if (studentModality.getCorrectionDeadline() != null) {
            daysRemaining = ChronoUnit.DAYS.between(
                    LocalDateTime.now(),
                    studentModality.getCorrectionDeadline()
            );
        }

        ModalityProcessStatus status = studentModality.getStatus();
        boolean canUploadDocuments = status == ModalityProcessStatus.MODALITY_SELECTED ||
                                    status == ModalityProcessStatus.UNDER_REVIEW_PROGRAM_HEAD ||
                                    status == ModalityProcessStatus.CORRECTIONS_REQUESTED_PROGRAM_HEAD ||
                                    status == ModalityProcessStatus.CORRECTIONS_REQUESTED_PROGRAM_CURRICULUM_COMMITTEE;

        boolean canRequestCancellation = status != ModalityProcessStatus.MODALITY_CLOSED &&
                                        status != ModalityProcessStatus.GRADED_APPROVED &&
                                        status != ModalityProcessStatus.GRADED_FAILED &&
                                        !status.name().startsWith("CANCELLED");

        boolean canSubmitCorrections = (status == ModalityProcessStatus.CORRECTIONS_REQUESTED_PROGRAM_HEAD ||
                                       status == ModalityProcessStatus.CORRECTIONS_REQUESTED_PROGRAM_CURRICULUM_COMMITTEE) &&
                                      studentModality.getCorrectionDeadline() != null &&
                                      LocalDateTime.now().isBefore(studentModality.getCorrectionDeadline());

        boolean hasDefenseScheduled = studentModality.getDefenseDate() != null;

        boolean requiresAction = canUploadDocuments || canSubmitCorrections ||
                                status == ModalityProcessStatus.CORRECTIONS_REQUESTED_PROGRAM_HEAD ||
                                status == ModalityProcessStatus.CORRECTIONS_REQUESTED_PROGRAM_CURRICULUM_COMMITTEE;

        User projectDirector = studentModality.getProjectDirector();
        String defenseProposedBy = null;
        if (status == ModalityProcessStatus.DEFENSE_REQUESTED_BY_PROJECT_DIRECTOR) {
            defenseProposedBy = "El director de proyecto ha propuesto una fecha de sustentación";
        }

        // Convertir miembros activos a DTOs (incluyendo créditos, promedio y semestre)
        List<ModalityMemberDTO> memberDTOs = activeMembers.stream()
                .map(member -> {
                    StudentProfile memberProfile = studentProfileRepository
                            .findByUserId(member.getStudent().getId())
                            .orElse(null);

                    return ModalityMemberDTO.builder()
                        .memberId(member.getId())
                        .studentId(member.getStudent().getId())
                        .studentName(member.getStudent().getName())
                        .studentLastName(member.getStudent().getLastName())
                        .studentEmail(member.getStudent().getEmail())
                        .studentCode(memberProfile != null ? memberProfile.getStudentCode() : null)
                        .approvedCredits(memberProfile != null ? (memberProfile.getApprovedCredits() != null ? memberProfile.getApprovedCredits().intValue() : null) : null)
                        .gpa(memberProfile != null ? (memberProfile.getGpa() != null ? memberProfile.getGpa().doubleValue() : null) : null)
                        .semester(memberProfile != null ? (memberProfile.getSemester() != null ? memberProfile.getSemester().toString() : null) : null)
                        .isLeader(member.getIsLeader())
                        .status(member.getStatus().name())
                        .joinedAt(member.getJoinedAt())
                        .build();
                })
                .toList();

        return ResponseEntity.ok(
                StudentModalityDTO.builder()
                        .studentId(student.getId())
                        .studentName(student.getName())
                        .studentLastName(student.getLastName())
                        .studentEmail(student.getEmail())
                        .studentCode(studentProfile != null ? studentProfile.getStudentCode() : null)
                        .approvedCredits(studentProfile != null ? studentProfile.getApprovedCredits() : null)
                        .gpa(studentProfile != null ? studentProfile.getGpa() : null)
                        .semester(studentProfile != null ? studentProfile.getSemester() : null)
                        .facultyName(academicProgram.getFaculty().getName())
                        .academicProgramName(academicProgram.getName())
                        .studentModalityId(studentModality.getId())
                        .modalityName(modality.getName())
                        .modalityDescription(modality.getDescription())
                        .creditsRequired(studentModality.getProgramDegreeModality().getCreditsRequired())
                        .modalityType(studentModality.getModalityType() != null
                                ? studentModality.getModalityType().name()
                                : null)
                        .members(memberDTOs)
                        .currentStatus(status.name())
                        .currentStatusDescription(describeModalityStatus(status))
                        .selectionDate(studentModality.getSelectionDate())
                        .lastUpdatedAt(studentModality.getUpdatedAt())
                        .projectDirectorId(projectDirector != null ? projectDirector.getId() : null)
                        .projectDirectorName(projectDirector != null
                                ? projectDirector.getName() + " " + projectDirector.getLastName()
                                : null)
                        .projectDirectorEmail(projectDirector != null ? projectDirector.getEmail() : null)
                        .defenseDate(studentModality.getDefenseDate())
                        .defenseLocation(studentModality.getDefenseLocation())
                        .defenseProposedByProjectDirector(defenseProposedBy)
                        .academicDistinction(studentModality.getAcademicDistinction() != null
                                ? studentModality.getAcademicDistinction().name()
                                : null)
                        .correctionRequestDate(studentModality.getCorrectionRequestDate())
                        .correctionDeadline(studentModality.getCorrectionDeadline())
                        .correctionReminderSent(studentModality.getCorrectionReminderSent())
                        .daysRemainingForCorrection(daysRemaining)
                        .documents(documents)
                        .totalDocuments(uploadedDocuments.size())
                        .approvedDocuments((int) approvedDocs)
                        .pendingDocuments((int) pendingDocs)
                        .rejectedDocuments((int) rejectedDocs)
                        .history(history)
                        .canUploadDocuments(canUploadDocuments)
                        .canRequestCancellation(canRequestCancellation)
                        .canSubmitCorrections(canSubmitCorrections)
                        .hasDefenseScheduled(hasDefenseScheduled)
                        .requiresAction(requiresAction)

                        .build()
        );
    }

    public ResponseEntity<?> getStudentModalityDetailForProjectDirector(Long studentModalityId) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User projectDirector = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        StudentModality studentModality = studentModalityRepository.findById(studentModalityId)
                .orElseThrow(() -> new RuntimeException("Modalidad no encontrada"));

        if (studentModality.getProjectDirector() == null ||
                !studentModality.getProjectDirector().getId().equals(projectDirector.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("No tiene permiso para ver esta modalidad. No es el director asignado.");
        }

        // Obtener todos los miembros activos de la modalidad
        List<StudentModalityMember> activeMembers =
            studentModalityMemberRepository.findByStudentModalityIdAndStatus(
                studentModalityId,
                MemberStatus.ACTIVE
            );

        // Usar el líder como estudiante principal
        User student = studentModality.getLeader();

        AcademicProgram academicProgram = studentModality.getProgramDegreeModality().getAcademicProgram();

        DegreeModality modality = studentModality.getProgramDegreeModality().getDegreeModality();


        StudentProfile studentProfile = studentProfileRepository.findByUserId(student.getId())
                .orElse(null);

        List<ModalityStatusHistoryDTO> history =
                historyRepository
                        .findByStudentModalityIdOrderByChangeDateAsc(studentModalityId)
                        .stream()
                        .map(h -> ModalityStatusHistoryDTO.builder()
                                .status(h.getStatus().name())
                                .description(describeModalityStatus(h.getStatus()))
                                .changeDate(h.getChangeDate())
                                .responsible(
                                        h.getResponsible() != null
                                                ? h.getResponsible().getEmail()
                                                : "Sistema"
                                )
                                .observations(h.getObservations())
                                .build()
                        )
                        .toList();


        List<RequiredDocument> requiredDocuments =
                requiredDocumentRepository
                        .findByModalityIdAndActiveTrue(modality.getId());

        List<StudentDocument> uploadedDocuments =
                studentDocumentRepository
                        .findByStudentModalityId(studentModalityId);

        Map<Long, StudentDocument> uploadedMap =
                uploadedDocuments.stream()
                        .collect(Collectors.toMap(
                                d -> d.getDocumentConfig().getId(),
                                d -> d
                        ));

        List<DetailDocumentDTO> documents =
                requiredDocuments.stream()
                        .map(req -> {
                            StudentDocument uploaded = uploadedMap.get(req.getId());

                            return DetailDocumentDTO.builder()
                                    .requiredDocumentId(req.getId())
                                    .studentDocumentId(
                                            uploaded != null ? uploaded.getId() : null
                                    )
                                    .documentName(req.getDocumentName())
                                    .documentType(req.getDocumentType())
                                    .uploaded(uploaded != null)
                                    .status(
                                            uploaded != null
                                                    ? uploaded.getStatus().name()
                                                    : "NOT_UPLOADED"
                                    )
                                    .statusDescription(
                                            uploaded != null
                                                    ? describeDocumentStatus(uploaded.getStatus())
                                                    : "Documento aún no cargado por el estudiante."
                                    )
                                    .notes(
                                            uploaded != null ? uploaded.getNotes() : null
                                    )
                                    .lastUpdate(
                                            uploaded != null ? uploaded.getUploadDate() : null
                                    )
                                    .build();
                        })
                        .toList();


        long approvedDocs = uploadedDocuments.stream()
                .filter(d -> d.getStatus() == DocumentStatus.ACCEPTED_FOR_PROGRAM_CURRICULUM_COMMITTEE_REVIEW)
                .count();
        long pendingDocs = uploadedDocuments.stream()
                .filter(d -> d.getStatus() == DocumentStatus.PENDING ||
                            d.getStatus() == DocumentStatus.ACCEPTED_FOR_PROGRAM_HEAD_REVIEW ||
                            d.getStatus() == DocumentStatus.CORRECTION_RESUBMITTED)
                .count();
        long rejectedDocs = uploadedDocuments.stream()
                .filter(d -> d.getStatus() == DocumentStatus.REJECTED_FOR_PROGRAM_HEAD_REVIEW ||
                            d.getStatus() == DocumentStatus.REJECTED_FOR_PROGRAM_CURRICULUM_COMMITTEE_REVIEW)
                .count();


        Long daysRemaining = null;
        if (studentModality.getCorrectionDeadline() != null) {
            daysRemaining = ChronoUnit.DAYS.between(
                    LocalDateTime.now(),
                    studentModality.getCorrectionDeadline()
            );
        }


        ModalityProcessStatus status = studentModality.getStatus();
        boolean canUploadDocuments = status == ModalityProcessStatus.MODALITY_SELECTED ||
                                    status == ModalityProcessStatus.UNDER_REVIEW_PROGRAM_HEAD ||
                                    status == ModalityProcessStatus.CORRECTIONS_REQUESTED_PROGRAM_HEAD ||
                                    status == ModalityProcessStatus.CORRECTIONS_REQUESTED_PROGRAM_CURRICULUM_COMMITTEE;

        boolean canRequestCancellation = status != ModalityProcessStatus.MODALITY_CLOSED &&
                                        status != ModalityProcessStatus.GRADED_APPROVED &&
                                        status != ModalityProcessStatus.GRADED_FAILED &&
                                        !status.name().startsWith("CANCELLED");

        boolean canSubmitCorrections = (status == ModalityProcessStatus.CORRECTIONS_REQUESTED_PROGRAM_HEAD ||
                                       status == ModalityProcessStatus.CORRECTIONS_REQUESTED_PROGRAM_CURRICULUM_COMMITTEE) &&
                                      studentModality.getCorrectionDeadline() != null &&
                                      LocalDateTime.now().isBefore(studentModality.getCorrectionDeadline());

        boolean hasDefenseScheduled = studentModality.getDefenseDate() != null;

        boolean requiresAction = canUploadDocuments || canSubmitCorrections ||
                                status == ModalityProcessStatus.CORRECTIONS_REQUESTED_PROGRAM_HEAD ||
                                status == ModalityProcessStatus.CORRECTIONS_REQUESTED_PROGRAM_CURRICULUM_COMMITTEE;


        String defenseProposedBy = null;
        if (status == ModalityProcessStatus.DEFENSE_REQUESTED_BY_PROJECT_DIRECTOR) {
            defenseProposedBy = "Usted ha propuesto una fecha de sustentación. Pendiente de aprobación del comité.";
        }

        // Convertir miembros activos a DTOs
        List<ModalityMemberDTO> memberDTOs = activeMembers.stream()
            .map(member -> {
                StudentProfile memberProfile = studentProfileRepository
                    .findByUserId(member.getStudent().getId())
                    .orElse(null);
                return ModalityMemberDTO.builder()
                    .memberId(member.getId())
                    .studentId(member.getStudent().getId())
                    .studentName(member.getStudent().getName())
                    .studentLastName(member.getStudent().getLastName())
                    .studentEmail(member.getStudent().getEmail())
                    .studentCode(memberProfile != null ? memberProfile.getStudentCode() : null)
                    .approvedCredits(memberProfile != null ? (memberProfile.getApprovedCredits() != null ? memberProfile.getApprovedCredits().intValue() : null) : null)
                    .gpa(memberProfile != null ? (memberProfile.getGpa() != null ? memberProfile.getGpa().doubleValue() : null) : null)
                    .semester(memberProfile != null ? (memberProfile.getSemester() != null ? memberProfile.getSemester().toString() : null) : null)
                    .isLeader(member.getIsLeader())
                    .status(member.getStatus().name())
                    .joinedAt(member.getJoinedAt())
                    .build();
            })
            .toList();

        return ResponseEntity.ok(
                StudentModalityDTO.builder()

                        .studentId(student.getId())
                        .studentName(student.getName())
                        .studentLastName(student.getLastName())
                        .studentEmail(student.getEmail())
                        .studentCode(studentProfile != null ? studentProfile.getStudentCode() : null)
                        .approvedCredits(studentProfile != null ? studentProfile.getApprovedCredits() : null)
                        .gpa(studentProfile != null ? studentProfile.getGpa() : null)
                        .semester(studentProfile != null ? studentProfile.getSemester() : null)


                        .facultyName(academicProgram.getFaculty().getName())
                        .academicProgramName(academicProgram.getName())


                        .studentModalityId(studentModality.getId())
                        .modalityName(modality.getName())
                        .modalityDescription(modality.getDescription())
                        .creditsRequired(studentModality.getProgramDegreeModality().getCreditsRequired())
                        .modalityType(studentModality.getModalityType() != null
                                ? studentModality.getModalityType().name()
                                : null)
                        .members(memberDTOs)


                        .currentStatus(status.name())
                        .currentStatusDescription(describeModalityStatus(status))
                        .selectionDate(studentModality.getSelectionDate())
                        .lastUpdatedAt(studentModality.getUpdatedAt())


                        .projectDirectorId(projectDirector.getId())
                        .projectDirectorName(projectDirector.getName() + " " + projectDirector.getLastName())
                        .projectDirectorEmail(projectDirector.getEmail())


                        .defenseDate(studentModality.getDefenseDate())
                        .defenseLocation(studentModality.getDefenseLocation())
                        .defenseProposedByProjectDirector(defenseProposedBy)


                        .academicDistinction(studentModality.getAcademicDistinction() != null
                                ? studentModality.getAcademicDistinction().name()
                                : null)


                        .correctionRequestDate(studentModality.getCorrectionRequestDate())
                        .correctionDeadline(studentModality.getCorrectionDeadline())
                        .correctionReminderSent(studentModality.getCorrectionReminderSent())
                        .daysRemainingForCorrection(daysRemaining)


                        .documents(documents)
                        .totalDocuments(uploadedDocuments.size())
                        .approvedDocuments((int) approvedDocs)
                        .pendingDocuments((int) pendingDocs)
                        .rejectedDocuments((int) rejectedDocs)


                        .history(history)


                        .canUploadDocuments(canUploadDocuments)
                        .canRequestCancellation(canRequestCancellation)
                        .canSubmitCorrections(canSubmitCorrections)
                        .hasDefenseScheduled(hasDefenseScheduled)
                        .requiresAction(requiresAction)

                        .build()
        );
    }

    public ResponseEntity<?> getStudentModalityDetailForExaminer(Long studentModalityId) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User examiner = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        StudentModality studentModality = studentModalityRepository.findById(studentModalityId)
                .orElseThrow(() -> new RuntimeException("Modalidad no encontrada"));

        // Verificar si el usuario es un examinador asignado a esta modalidad
        DefenseExaminer defenseExaminer = defenseExaminerRepository
                .findByStudentModalityIdAndExaminerId(studentModalityId, examiner.getId())
                .orElseThrow(() -> new RuntimeException(
                        "No tiene permiso para ver esta modalidad. No está asignado como examinador."
                ));

        // Obtener todos los miembros activos de la modalidad
        List<StudentModalityMember> activeMembers =
            studentModalityMemberRepository.findByStudentModalityIdAndStatus(
                studentModalityId,
                MemberStatus.ACTIVE
            );

        AcademicProgram academicProgram = studentModality.getProgramDegreeModality().getAcademicProgram();

        // Usar el líder como estudiante principal
        User student = studentModality.getLeader();
        DegreeModality modality = studentModality.getProgramDegreeModality().getDegreeModality();

        // Información del perfil del estudiante
        StudentProfile studentProfile = studentProfileRepository.findByUserId(student.getId())
                .orElse(null);

        // Historial de estados
        List<ModalityStatusHistoryDTO> history =
                historyRepository
                        .findByStudentModalityIdOrderByChangeDateAsc(studentModalityId)
                        .stream()
                        .map(h -> ModalityStatusHistoryDTO.builder()
                                .status(h.getStatus().name())
                                .description(describeModalityStatus(h.getStatus()))
                                .changeDate(h.getChangeDate())
                                .responsible(
                                        h.getResponsible() != null
                                                ? h.getResponsible().getEmail()
                                                : "Sistema"
                                )
                                .observations(h.getObservations())
                                .build()
                        )
                        .sorted((h1, h2) -> h2.getChangeDate().compareTo(h1.getChangeDate())) // Ordenar de más reciente a más antiguo
                        .toList();


        // Documentos requeridos y cargados
        // Para la vista del examinador, solo se muestran documentos (MANDATORY o SECONDARY) que requieren evaluación de propuesta
        List<RequiredDocument> allRequiredDocuments =
                requiredDocumentRepository
                        .findByModalityIdAndActiveTrue(modality.getId());

        List<RequiredDocument> requiredDocuments = allRequiredDocuments.stream()
                .filter(req -> (req.getDocumentType() == DocumentType.MANDATORY || req.getDocumentType() == DocumentType.SECONDARY)
                        && Boolean.TRUE.equals(req.isRequiresProposalEvaluation()))
                .toList();

        List<StudentDocument> uploadedDocuments =
                studentDocumentRepository
                        .findByStudentModalityId(studentModalityId);

        Map<Long, StudentDocument> uploadedMap =
                uploadedDocuments.stream()
                        .collect(Collectors.toMap(
                                d -> d.getDocumentConfig().getId(),
                                d -> d
                        ));

        List<DetailDocumentDTO> documents =
                requiredDocuments.stream()
                        .map(req -> {
                            StudentDocument uploaded = uploadedMap.get(req.getId());

                            return DetailDocumentDTO.builder()
                                    .requiredDocumentId(req.getId())
                                    .studentDocumentId(
                                            uploaded != null ? uploaded.getId() : null
                                    )
                                    .documentName(req.getDocumentName())
                                    .documentType(req.getDocumentType())
                                    .uploaded(uploaded != null)
                                    .status(
                                            uploaded != null
                                                    ? uploaded.getStatus().name()
                                                    : "NOT_UPLOADED"
                                    )
                                    .statusDescription(
                                            uploaded != null
                                                    ? describeDocumentStatus(uploaded.getStatus())
                                                    : "Documento aún no cargado por el estudiante."
                                    )
                                    .notes(
                                            uploaded != null ? uploaded.getNotes() : null
                                    )
                                    .lastUpdate(
                                            uploaded != null ? uploaded.getUploadDate() : null
                                    )
                                    .build();
                        })
                        .toList();

        // Estadísticas de documentos (solo sobre los documentos evaluables por el jurado)
        List<StudentDocument> evaluableUploadedDocs = uploadedDocuments.stream()
                .filter(d -> {
                    RequiredDocument reqDoc = d.getDocumentConfig();
                    return (reqDoc.getDocumentType() == DocumentType.MANDATORY || reqDoc.getDocumentType() == DocumentType.SECONDARY)
                            && Boolean.TRUE.equals(reqDoc.isRequiresProposalEvaluation());
                })
                .toList();

        long approvedDocs = evaluableUploadedDocs.stream()
                .filter(d -> d.getStatus() == DocumentStatus.ACCEPTED_FOR_EXAMINER_REVIEW)
                .count();
        long pendingDocs = evaluableUploadedDocs.stream()
                .filter(d -> d.getStatus() == DocumentStatus.PENDING ||
                        d.getStatus() == DocumentStatus.ACCEPTED_FOR_PROGRAM_HEAD_REVIEW ||
                        d.getStatus() == DocumentStatus.ACCEPTED_FOR_PROGRAM_CURRICULUM_COMMITTEE_REVIEW ||
                        d.getStatus() == DocumentStatus.CORRECTION_RESUBMITTED)
                .count();
        long rejectedDocs = evaluableUploadedDocs.stream()
                .filter(d -> d.getStatus() == DocumentStatus.REJECTED_FOR_EXAMINER_REVIEW ||
                        d.getStatus() == DocumentStatus.CORRECTIONS_REQUESTED_BY_EXAMINER)
                .count();

        // Obtener todos los examinadores asignados
        List<DefenseExaminer> allExaminers = defenseExaminerRepository
                .findByStudentModalityId(studentModalityId);

        List<StudentModalityExaminerDTO.ExaminerInfo> examinersList = allExaminers.stream()
                .map(de -> {
                    // Verificar si este examinador ya evaluó
                    boolean hasEvaluated = defenseEvaluationCriteriaRepository
                            .findByDefenseExaminerId(de.getId())
                            .isPresent();

                    return StudentModalityExaminerDTO.ExaminerInfo.builder()
                            .examinerId(de.getExaminer().getId())
                            .examinerName(de.getExaminer().getName() + " " + de.getExaminer().getLastName())
                            .examinerEmail(de.getExaminer().getEmail())
                            .examinerType(de.getExaminerType().name())
                            .assignmentDate(de.getAssignmentDate())
                            .hasEvaluated(hasEvaluated)
                            .build();
                })
                .toList();

        // Obtener la evaluación del examinador actual (si existe)
        StudentModalityExaminerDTO.ExaminerEvaluationInfo myEvaluationInfo = null;
        boolean hasEvaluated = false;

        DefenseEvaluationCriteria myEvaluation = defenseEvaluationCriteriaRepository
                .findByDefenseExaminerId(defenseExaminer.getId())
                .orElse(null);

        if (myEvaluation != null) {
            hasEvaluated = true;
            myEvaluationInfo = StudentModalityExaminerDTO.ExaminerEvaluationInfo.builder()
                    .evaluationId(myEvaluation.getId())
                    .grade(myEvaluation.getGrade())
                    .decision(myEvaluation.getGrade() != null ? (myEvaluation.getGrade() >= 3.5 ? "APPROVED" : "REJECTED") : null)
                    .observations(myEvaluation.getObservations())
                    .evaluationDate(myEvaluation.getEvaluatedAt())
                    .isFinalDecision(myEvaluation.getIsFinalDecision())
                    .build();
        }

        // Determinar permisos y acciones
        ModalityProcessStatus status = studentModality.getStatus();

        // El examinador puede evaluar si la defensa está programada o si es jurado de desempate y necesita evaluar
        boolean canEvaluate = (status == ModalityProcessStatus.DEFENSE_SCHEDULED && !hasEvaluated) ||
                             (defenseExaminer.getExaminerType() == ExaminerType.TIEBREAKER_EXAMINER && !hasEvaluated);

        boolean requiresAction = canEvaluate;

        boolean defenseCompleted = status == ModalityProcessStatus.GRADED_APPROVED ||
                                  status == ModalityProcessStatus.GRADED_FAILED;

        // Director del proyecto
        User projectDirector = studentModality.getProjectDirector();

        // Convertir miembros activos a DTOs (incluyendo créditos, promedio y semestre)
        List<ModalityMemberDTO> memberDTOs = activeMembers.stream()
            .map(member -> {
                StudentProfile memberProfile = studentProfileRepository
                    .findByUserId(member.getStudent().getId())
                    .orElse(null);
                return ModalityMemberDTO.builder()
                    .memberId(member.getId())
                    .studentId(member.getStudent().getId())
                    .studentName(member.getStudent().getName())
                    .studentLastName(member.getStudent().getLastName())
                    .studentEmail(member.getStudent().getEmail())
                    .studentCode(memberProfile != null ? memberProfile.getStudentCode() : null)
                    .approvedCredits(memberProfile != null ? (memberProfile.getApprovedCredits() != null ? memberProfile.getApprovedCredits().intValue() : null) : null)
                    .gpa(memberProfile != null ? (memberProfile.getGpa() != null ? memberProfile.getGpa().doubleValue() : null) : null)
                    .semester(memberProfile != null ? (memberProfile.getSemester() != null ? memberProfile.getSemester().toString() : null) : null)
                    .isLeader(member.getIsLeader())
                    .status(member.getStatus().name())
                    .joinedAt(member.getJoinedAt())
                    .build();
            })
            .toList();

        return ResponseEntity.ok(
                StudentModalityExaminerDTO.builder()
                        // Información del estudiante
                        .studentId(student.getId())
                        .studentName(student.getName())
                        .studentLastName(student.getLastName())
                        .studentEmail(student.getEmail())
                        .studentCode(studentProfile != null ? studentProfile.getStudentCode() : null)
                        .approvedCredits(studentProfile != null ? studentProfile.getApprovedCredits() : null)
                        .gpa(studentProfile != null ? studentProfile.getGpa() : null)
                        .semester(studentProfile != null ? studentProfile.getSemester() : null)

                        // Información académica
                        .facultyName(academicProgram.getFaculty().getName())
                        .academicProgramName(academicProgram.getName())

                        // Información de la modalidad
                        .studentModalityId(studentModality.getId())
                        .modalityName(modality.getName())
                        .modalityDescription(modality.getDescription())
                        .creditsRequired(studentModality.getProgramDegreeModality().getCreditsRequired())
                        .modalityType(studentModality.getModalityType() != null
                                ? studentModality.getModalityType().name()
                                : null)
                        .members(memberDTOs)

                        // Estado actual
                        .currentStatus(status.name())
                        .currentStatusDescription(describeModalityStatus(status))
                        .selectionDate(studentModality.getSelectionDate())
                        .lastUpdatedAt(studentModality.getUpdatedAt())

                        // Director del proyecto
                        .projectDirectorId(projectDirector != null ? projectDirector.getId() : null)
                        .projectDirectorName(projectDirector != null
                                ? projectDirector.getName() + " " + projectDirector.getLastName()
                                : null)
                        .projectDirectorEmail(projectDirector != null ? projectDirector.getEmail() : null)

                        // Información de la defensa
                        .defenseDate(studentModality.getDefenseDate())
                        .defenseLocation(studentModality.getDefenseLocation())

                        // Examinadores
                        .examiners(examinersList)
                        .myEvaluation(myEvaluationInfo)

                        // Distinción académica y calificación final
                        .academicDistinction(studentModality.getAcademicDistinction() != null
                                ? studentModality.getAcademicDistinction().name()
                                : null)
                        .finalGrade(studentModality.getFinalGrade())

                        // Documentos (solo los evaluables por el jurado: MANDATORY con requiresProposalEvaluation=true)
                        .documents(documents)
                        .totalDocuments(evaluableUploadedDocs.size())
                        .approvedDocuments((int) approvedDocs)
                        .pendingDocuments((int) pendingDocs)
                        .rejectedDocuments((int) rejectedDocs)

                        // Historial
                        .history(history)

                        // Permisos y acciones
                        .canEvaluate(canEvaluate)
                        .hasEvaluated(hasEvaluated)
                        .requiresAction(requiresAction)
                        .defenseCompleted(defenseCompleted)

                        .build()
        );
    }

    public ResponseEntity<?> requestCancellation(Long studentModalityId) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User student = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        StudentModality studentModality = studentModalityRepository.findById(studentModalityId)
                .orElseThrow(() -> new RuntimeException("Modalidad no encontrada"));

        // Validar que el usuario sea miembro activo de la modalidad
        boolean isActiveMember = studentModalityMemberRepository.isActiveMember(
                studentModalityId,
                student.getId()
        );

        if (!isActiveMember) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("No autorizado para solicitar cancelación de esta modalidad");
        }

        // Validar que tenga director de proyecto asignado
        if (studentModality.getProjectDirector() == null) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", "No puede solicitar la cancelación aún. Debe tener un director de proyecto asignado a su modalidad antes de solicitar la cancelación."
                    )
            );
        }

        // Validar que la modalidad no esté ya en proceso de cancelación
        if (studentModality.getStatus() == ModalityProcessStatus.CANCELLATION_REQUESTED ||
                studentModality.getStatus() == ModalityProcessStatus.MODALITY_CANCELLED) {

            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", "La modalidad ya tiene una solicitud de cancelación"
                    )
            );
        }

        // CORRECCIÓN CRÍTICA: Validar específicamente documento de tipo CANCELLATION
        List<StudentDocument> documents =
                studentDocumentRepository.findByStudentModalityId(studentModalityId);

        boolean hasCancellationDocument = documents.stream()
                .anyMatch(doc -> doc.getDocumentConfig().getDocumentType() == DocumentType.CANCELLATION);

        if (!hasCancellationDocument) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", "Debe subir el documento de justificación de cancelación antes de solicitar la cancelación de la modalidad"
                    )
            );
        }

        // Validar que el documento de cancelación esté en estado válido
        Optional<StudentDocument> cancellationDoc = documents.stream()
                .filter(doc -> doc.getDocumentConfig().getDocumentType() == DocumentType.CANCELLATION)
                .findFirst();

        if (cancellationDoc.isPresent()) {
            DocumentStatus docStatus = cancellationDoc.get().getStatus();
            if (docStatus == DocumentStatus.REJECTED_FOR_PROGRAM_HEAD_REVIEW ||
                docStatus == DocumentStatus.REJECTED_FOR_PROGRAM_CURRICULUM_COMMITTEE_REVIEW) {
                return ResponseEntity.badRequest().body(
                        Map.of(
                                "success", false,
                                "message", "El documento de cancelación fue rechazado. Debe subir una nueva versión antes de solicitar la cancelación."
                        )
                );
            }
        }


        // Cambiar estado de la modalidad
        studentModality.setStatus(ModalityProcessStatus.CANCELLATION_REQUESTED);
        studentModality.setUpdatedAt(LocalDateTime.now());
        studentModalityRepository.save(studentModality);

        // Registrar en historial
        historyRepository.save(
                ModalityProcessStatusHistory.builder()
                        .studentModality(studentModality)
                        .status(ModalityProcessStatus.CANCELLATION_REQUESTED)
                        .changeDate(LocalDateTime.now())
                        .responsible(student)
                        .observations("Solicitud de cancelación enviada por el estudiante con documento justificativo")
                        .build()
        );

        // Notificar a las partes interesadas
        notificationEventPublisher.publish(
                new CancellationRequestedEvent(studentModality.getId(), student.getId())
        );

        return ResponseEntity.ok(
                Map.of(
                        "success", true,
                        "message", "Solicitud de cancelación enviada correctamente",
                        "studentModalityId", studentModalityId,
                        "newStatus", ModalityProcessStatus.CANCELLATION_REQUESTED
                )
        );
    }

    @Transactional
    public ResponseEntity<?> approveModalityCancellationByDirector(Long studentModalityId) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User projectDirector = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        StudentModality studentModality = studentModalityRepository.findById(studentModalityId)
                .orElseThrow(() -> new RuntimeException("Modalidad no encontrada"));


        if (studentModality.getProjectDirector() == null ||
                !studentModality.getProjectDirector().getId().equals(projectDirector.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("No tiene permiso para aprobar la cancelación. No es el director asignado a esta modalidad");
        }


        if (studentModality.getStatus() != ModalityProcessStatus.CANCELLATION_REQUESTED) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", "La modalidad no tiene una solicitud de cancelación pendiente",
                            "currentStatus", studentModality.getStatus()
                    )
            );
        }


        studentModality.setStatus(ModalityProcessStatus.CANCELLATION_APPROVED_BY_PROJECT_DIRECTOR);
        studentModality.setUpdatedAt(LocalDateTime.now());
        studentModalityRepository.save(studentModality);


        historyRepository.save(
                ModalityProcessStatusHistory.builder()
                        .studentModality(studentModality)
                        .status(ModalityProcessStatus.CANCELLATION_APPROVED_BY_PROJECT_DIRECTOR)
                        .changeDate(LocalDateTime.now())
                        .responsible(projectDirector)
                        .observations("El director de proyecto aprobó la solicitud de cancelación. Pendiente de revisión del comité de currículo")
                        .build()
        );


        notificationEventPublisher.publish(
                new CancellationApprovedEvent(studentModality.getId(), projectDirector.getId())
        );

        return ResponseEntity.ok(
                Map.of(
                        "success", true,
                        "message", "Solicitud de cancelación aprobada. Será enviada al comité de currículo para aprobación final"
                )
        );
    }

    @Transactional
    public ResponseEntity<?> rejectModalityCancellationByDirector(Long studentModalityId, String reason) {

        if (reason == null || reason.isBlank()) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", "Debe indicar el motivo del rechazo de la cancelación"
                    )
            );
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User projectDirector = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        StudentModality studentModality = studentModalityRepository.findById(studentModalityId)
                .orElseThrow(() -> new RuntimeException("Modalidad no encontrada"));


        if (studentModality.getProjectDirector() == null ||
                !studentModality.getProjectDirector().getId().equals(projectDirector.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("No tiene permiso para rechazar la cancelación. No es el director asignado a esta modalidad");
        }


        if (studentModality.getStatus() != ModalityProcessStatus.CANCELLATION_REQUESTED) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", "La modalidad no tiene una solicitud de cancelación pendiente",
                            "currentStatus", studentModality.getStatus()
                    )
            );
        }

        ModalityProcessStatus previousStatus = ModalityProcessStatus.PROPOSAL_APPROVED;


        List<ModalityProcessStatusHistory> history = historyRepository
                .findByStudentModalityIdOrderByChangeDateAsc(studentModalityId);

        if (history.size() >= 2) {

            previousStatus = history.get(history.size() - 2).getStatus();
        }


        studentModality.setStatus(ModalityProcessStatus.CANCELLATION_REJECTED_BY_PROJECT_DIRECTOR);
        studentModality.setUpdatedAt(LocalDateTime.now());
        studentModalityRepository.save(studentModality);


        historyRepository.save(
                ModalityProcessStatusHistory.builder()
                        .studentModality(studentModality)
                        .status(ModalityProcessStatus.CANCELLATION_REJECTED_BY_PROJECT_DIRECTOR)
                        .changeDate(LocalDateTime.now())
                        .responsible(projectDirector)
                        .observations("El director de proyecto rechazó la solicitud de cancelación. Motivo: " + reason)
                        .build()
        );


        studentModality.setStatus(previousStatus);
        studentModality.setUpdatedAt(LocalDateTime.now());
        studentModalityRepository.save(studentModality);


        historyRepository.save(
                ModalityProcessStatusHistory.builder()
                        .studentModality(studentModality)
                        .status(previousStatus)
                        .changeDate(LocalDateTime.now())
                        .responsible(projectDirector)
                        .observations("Modalidad restaurada al estado anterior tras rechazo de cancelación")
                        .build()
        );


        notificationEventPublisher.publish(
                new CancellationRejectedEvent(studentModality.getId(), reason, projectDirector.getId())
        );

        return ResponseEntity.ok(
                Map.of(
                        "success", true,
                        "message", "Solicitud de cancelación rechazada. La modalidad continúa en proceso normal",
                        "restoredStatus", previousStatus
                )
        );
    }

    @Transactional
    public ResponseEntity<?> approveCancellation(Long studentModalityId) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User committeeMember = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        StudentModality modality = studentModalityRepository.findById(studentModalityId)
                .orElseThrow(() -> new RuntimeException("Modalidad no encontrada"));


        AcademicProgram academicProgram = modality.getProgramDegreeModality().getAcademicProgram();

        boolean authorized =
                programAuthorityRepository.existsByUser_IdAndAcademicProgram_IdAndRole(
                        committeeMember.getId(),
                        academicProgram.getId(),
                        ProgramRole.PROGRAM_CURRICULUM_COMMITTEE
                );

        if (!authorized) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("No tiene permiso para aprobar la cancelación de esta modalidad");
        }


        if (modality.getStatus() != ModalityProcessStatus.CANCELLATION_APPROVED_BY_PROJECT_DIRECTOR) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", "La cancelación debe ser aprobada primero por el director de proyecto",
                            "currentStatus", modality.getStatus()
                    )
            );
        }

        modality.setStatus(ModalityProcessStatus.MODALITY_CANCELLED);
        modality.setUpdatedAt(LocalDateTime.now());
        studentModalityRepository.save(modality);

        historyRepository.save(
                ModalityProcessStatusHistory.builder()
                        .studentModality(modality)
                        .status(ModalityProcessStatus.MODALITY_CANCELLED)
                        .changeDate(LocalDateTime.now())
                        .responsible(committeeMember)
                        .observations("Cancelación aprobada por el comité de currículo del programa")
                        .build()
        );

        notificationEventPublisher.publish(
                new CancellationApprovedEvent(modality.getId(), committeeMember.getId())
        );

        return ResponseEntity.ok(
                Map.of(
                        "success", true,
                        "message", "La modalidad fue cancelada correctamente"
                )
        );
    }

    @Transactional
    public ResponseEntity<?> rejectCancellation(Long studentModalityId, String reason) {

        if (reason == null || reason.isBlank()) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", "Debe indicar el motivo del rechazo"
                    )
            );
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User committeeMember = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        StudentModality modality = studentModalityRepository.findById(studentModalityId)
                .orElseThrow(() -> new RuntimeException("Modalidad no encontrada"));


        AcademicProgram academicProgram =
                modality
                        .getProgramDegreeModality()
                        .getAcademicProgram();

        boolean authorized =
                programAuthorityRepository.existsByUser_IdAndAcademicProgram_IdAndRole(
                        committeeMember.getId(),
                        academicProgram.getId(),
                        ProgramRole.PROGRAM_CURRICULUM_COMMITTEE
                );

        if (!authorized) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("No tiene permiso para rechazar la cancelación de esta modalidad");
        }


        if (modality.getStatus() != ModalityProcessStatus.CANCELLATION_APPROVED_BY_PROJECT_DIRECTOR) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", "Solo se pueden rechazar cancelaciones aprobadas por el director de proyecto",
                            "currentStatus", modality.getStatus()
                    )
            );
        }

        // ===== OBTENER ESTADO PREVIO A LA SOLICITUD DE CANCELACIÓN =====
        // El historial de cancelación tiene esta secuencia:
        //   CANCELLATION_APPROVED_BY_PROJECT_DIRECTOR  ← estado actual
        //   CANCELLATION_REQUESTED
        //   <estado real previo a la cancelación>  ← este es el que queremos restaurar
        // Filtramos todos los estados de cancelación y tomamos el más reciente que NO sea de cancelación.
        List<ModalityProcessStatusHistory> fullHistory = historyRepository
                .findByStudentModalityIdOrderByChangeDateAsc(studentModalityId);

        List<ModalityProcessStatus> cancellationStatuses = List.of(
                ModalityProcessStatus.CANCELLATION_REQUESTED,
                ModalityProcessStatus.CANCELLATION_APPROVED_BY_PROJECT_DIRECTOR,
                ModalityProcessStatus.CANCELLATION_REJECTED_BY_PROJECT_DIRECTOR,
                ModalityProcessStatus.CANCELLATION_REJECTED
        );

        // Filtrar estados que no son de cancelación, del más reciente al más antiguo
        List<ModalityProcessStatusHistory> nonCancellationHistory = fullHistory.stream()
                .filter(h -> !cancellationStatuses.contains(h.getStatus()))
                .sorted((h1, h2) -> h2.getChangeDate().compareTo(h1.getChangeDate()))
                .toList();

        // El estado a restaurar es el último estado previo a cualquier estado de cancelación
        ModalityProcessStatus stateToRestore = nonCancellationHistory.isEmpty()
                ? ModalityProcessStatus.MODALITY_SELECTED  // fallback de seguridad
                : nonCancellationHistory.get(0).getStatus();

        // 1. Registrar el rechazo en el historial
        modality.setStatus(ModalityProcessStatus.CANCELLATION_REJECTED);
        modality.setUpdatedAt(LocalDateTime.now());
        studentModalityRepository.save(modality);

        historyRepository.save(
                ModalityProcessStatusHistory.builder()
                        .studentModality(modality)
                        .status(ModalityProcessStatus.CANCELLATION_REJECTED)
                        .changeDate(LocalDateTime.now())
                        .responsible(committeeMember)
                        .observations("Solicitud de cancelación rechazada por el comité de currículo. Motivo: " + reason)
                        .build()
        );

        // 2. Restaurar automáticamente al estado previo a la solicitud de cancelación
        modality.setStatus(stateToRestore);
        modality.setUpdatedAt(LocalDateTime.now());
        studentModalityRepository.save(modality);

        historyRepository.save(
                ModalityProcessStatusHistory.builder()
                        .studentModality(modality)
                        .status(stateToRestore)
                        .changeDate(LocalDateTime.now())
                        .responsible(committeeMember)
                        .observations("Modalidad restaurada automáticamente al estado previo a la solicitud de cancelación: " +
                                stateToRestore.name() + ". La modalidad continúa su proceso normal.")
                        .build()
        );

        notificationEventPublisher.publish(
                new CancellationRejectedEvent(
                        modality.getId(),
                        reason,
                        committeeMember.getId()
                )
        );

        return ResponseEntity.ok(
                Map.of(
                        "success", true,
                        "message", "Solicitud de cancelación rechazada. La modalidad ha sido restaurada a su estado previo.",
                        "restoredStatus", stateToRestore.name(),
                        "restoredStatusDescription", describeModalityStatus(stateToRestore)
                )
        );
    }

    public List<CancellationList> getPendingCancellations() {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User committeeMember = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));


        List<Long> academicProgramIds =
                programAuthorityRepository
                        .findByUser_IdAndRole(
                                committeeMember.getId(),
                                ProgramRole.PROGRAM_CURRICULUM_COMMITTEE
                        )
                        .stream()
                        .map(pa -> pa.getAcademicProgram().getId())
                        .toList();

        if (academicProgramIds.isEmpty()) {
            return List.of();
        }


        List<StudentModality> modalities =
                studentModalityRepository
                        .findByStatusAndProgramDegreeModality_AcademicProgram_IdIn(
                                ModalityProcessStatus.CANCELLATION_APPROVED_BY_PROJECT_DIRECTOR,
                                academicProgramIds
                        );

        return modalities.stream()
                .map(sm -> CancellationList.builder()
                        .studentModalityId(sm.getId())
                        .studentName(
                                sm.getLeader().getName() + " " +
                                        sm.getLeader().getLastName()
                        )
                        .email(sm.getLeader().getEmail())
                        .modalityName(
                                sm.getProgramDegreeModality()
                                        .getDegreeModality()
                                        .getName()
                        )
                        .requestDate(sm.getUpdatedAt())
                        .build()
                )
                .toList();
    }


    @Transactional
    public ResponseEntity<?> assignProjectDirector(Long studentModalityId, Long directorId) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User committeeMember = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        StudentModality studentModality = studentModalityRepository.findById(studentModalityId)
                .orElseThrow(() -> new RuntimeException("Modalidad del estudiante no encontrada"));

        User director = userRepository.findById(directorId)
                .orElseThrow(() -> new RuntimeException("Director no encontrado"));


        Long academicProgramId =
                studentModality
                        .getProgramDegreeModality()
                        .getAcademicProgram()
                        .getId();


        boolean committeeAuthorized =
                programAuthorityRepository.existsByUser_IdAndAcademicProgram_IdAndRole(
                        committeeMember.getId(),
                        academicProgramId,
                        ProgramRole.PROGRAM_CURRICULUM_COMMITTEE
                );

        if (!committeeAuthorized) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("No tiene permiso para asignar director en este programa académico");
        }


        boolean hasDirectorRole =
                director.getRoles().stream()
                        .anyMatch(role -> role.getName().equalsIgnoreCase("PROJECT_DIRECTOR"));

        if (!hasDirectorRole) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", "El usuario seleccionado no tiene rol de Director de Proyecto"
                    )
            );
        }


        boolean directorAuthorized =
                programAuthorityRepository.existsByUser_IdAndAcademicProgram_IdAndRole(
                        director.getId(),
                        academicProgramId,
                        ProgramRole.PROJECT_DIRECTOR
                );

        if (!directorAuthorized) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", "El director no pertenece a este programa académico"
                    )
            );
        }


        if (studentModality.getStatus() != ModalityProcessStatus.READY_FOR_DIRECTOR_ASSIGNMENT &&
                studentModality.getStatus() != ModalityProcessStatus.CANCELLATION_REJECTED) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", "No se puede asignar un Director de Proyecto en este momento. " +
                                       "La modalidad debe estar en estado 'Listo para asignar Director' " +
                                       "(todos los documentos obligatorios aprobados por el Comité de Currículo).",
                            "currentStatus", studentModality.getStatus().name(),
                            "requiredStatus", ModalityProcessStatus.READY_FOR_DIRECTOR_ASSIGNMENT.name()
                    )
            );
        }


        User previousDirector = studentModality.getProjectDirector();

        studentModality.setProjectDirector(director);
        studentModality.setUpdatedAt(LocalDateTime.now());
        // Cambiar estado a READY_FOR_APPROVED_BY_PROGRAM_CURRICULUM_COMMITTEE
        studentModality.setStatus(ModalityProcessStatus.READY_FOR_APPROVED_BY_PROGRAM_CURRICULUM_COMMITTEE);
        studentModalityRepository.save(studentModality);

        String observation =
                previousDirector == null
                        ? "Director asignado: " + director.getEmail()
                        : "Cambio de Director: " +
                        previousDirector.getEmail() +
                        " → " +
                        director.getEmail();

        historyRepository.save(
                ModalityProcessStatusHistory.builder()
                        .studentModality(studentModality)
                        .status(ModalityProcessStatus.READY_FOR_APPROVED_BY_PROGRAM_CURRICULUM_COMMITTEE)
                        .changeDate(LocalDateTime.now())
                        .responsible(committeeMember)
                        .observations(observation)
                        .build()
        );

        notificationEventPublisher.publish(
                new DirectorAssignedEvent(
                        studentModality.getId(),
                        director.getId(),
                        committeeMember.getId()
                )
        );

        return ResponseEntity.ok(
                Map.of(
                        "success", true,
                        "studentModalityId", studentModality.getId(),
                        "directorAssigned", director.getEmail(),
                        "message", "Director asignado correctamente a la modalidad"
                )
        );
    }


    @Transactional
    public ResponseEntity<?> changeProjectDirector(Long studentModalityId, Long newDirectorId, String reason) {


        if (reason == null || reason.isBlank()) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", "Debe proporcionar una razón para el cambio de director"
                    )
            );
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User committeeMember = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        StudentModality studentModality = studentModalityRepository.findById(studentModalityId)
                .orElseThrow(() -> new RuntimeException("Modalidad del estudiante no encontrada"));

        User newDirector = userRepository.findById(newDirectorId)
                .orElseThrow(() -> new RuntimeException("Director no encontrado"));


        User currentDirector = studentModality.getProjectDirector();
        if (currentDirector == null) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", "La modalidad no tiene un director asignado actualmente. Use el método de asignación inicial."
                    )
            );
        }


        if (currentDirector.getId().equals(newDirectorId)) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", "El director seleccionado ya está asignado a esta modalidad",
                            "currentDirector", currentDirector.getEmail()
                    )
            );
        }

        Long academicProgramId = studentModality.getProgramDegreeModality().getAcademicProgram().getId();


        boolean committeeAuthorized = programAuthorityRepository.existsByUser_IdAndAcademicProgram_IdAndRole(committeeMember.getId(), academicProgramId,
                ProgramRole.PROGRAM_CURRICULUM_COMMITTEE);

        if (!committeeAuthorized) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "success", false,
                            "message", "No tiene permiso para cambiar director en este programa académico"
                    ));
        }


        boolean hasDirectorRole = newDirector.getRoles().stream()
                .anyMatch(role -> role.getName().equalsIgnoreCase("PROJECT_DIRECTOR"));

        if (!hasDirectorRole) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", "El usuario seleccionado no tiene rol de Director de Proyecto"
                    )
            );
        }


        boolean newDirectorAuthorized = programAuthorityRepository.existsByUser_IdAndAcademicProgram_IdAndRole(
                newDirector.getId(),
                academicProgramId,
                ProgramRole.PROJECT_DIRECTOR
        );

        if (!newDirectorAuthorized) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", "El nuevo director no pertenece a este programa académico"
                    )
            );
        }


        if (studentModality.getStatus() == ModalityProcessStatus.MODALITY_CLOSED ||
                studentModality.getStatus() == ModalityProcessStatus.MODALITY_CANCELLED ||
                studentModality.getStatus() == ModalityProcessStatus.GRADED_APPROVED ||
                studentModality.getStatus() == ModalityProcessStatus.GRADED_FAILED ||
                studentModality.getStatus() == ModalityProcessStatus.CORRECTIONS_REJECTED_FINAL ||
                studentModality.getStatus() == ModalityProcessStatus.CANCELLATION_REJECTED) {

            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", "No se puede cambiar el director en modalidades finalizadas, cerradas o canceladas",
                            "currentStatus", studentModality.getStatus()
                    )
            );
        }


        studentModality.setProjectDirector(newDirector);
        studentModality.setUpdatedAt(LocalDateTime.now());
        studentModalityRepository.save(studentModality);


        String observation = String.format(
                "CAMBIO DE DIRECTOR DE PROYECTO. Director anterior: %s (%s %s). Nuevo director: %s (%s %s). Razón: %s",
                currentDirector.getEmail(),
                currentDirector.getName(),
                currentDirector.getLastName(),
                newDirector.getEmail(),
                newDirector.getName(),
                newDirector.getLastName(),
                reason
        );

        historyRepository.save(
                ModalityProcessStatusHistory.builder()
                        .studentModality(studentModality)
                        .status(studentModality.getStatus())
                        .changeDate(LocalDateTime.now())
                        .responsible(committeeMember)
                        .observations(observation)
                        .build()
        );


        notificationEventPublisher.publish(
                new DirectorAssignedEvent(
                        studentModality.getId(),
                        newDirector.getId(),
                        committeeMember.getId()
                )
        );


        List<StudentModalityMember> activeMembers = studentModalityMemberRepository
                .findByStudentModalityIdAndStatus(studentModality.getId(), MemberStatus.ACTIVE);

        for (StudentModalityMember member : activeMembers) {
            notificationEventPublisher.publish(
                    new DirectorChangedEvent(
                            studentModality.getId(),
                            member.getStudent().getId(),
                            currentDirector.getId(),
                            newDirector.getId(),
                            reason
                    )
            );
        }


        return ResponseEntity.ok(
                Map.of(
                        "success", true,
                        "studentModalityId", studentModality.getId(),
                        "previousDirector", Map.of(
                                "id", currentDirector.getId(),
                                "email", currentDirector.getEmail(),
                                "name", currentDirector.getName() + " " + currentDirector.getLastName()
                        ),
                        "newDirector", Map.of(
                                "id", newDirector.getId(),
                                "email", newDirector.getEmail(),
                                "name", newDirector.getName() + " " + newDirector.getLastName()
                        ),
                        "reason", reason,
                        "message", "Director de proyecto cambiado exitosamente"
                )
        );
    }

    @Transactional
    public ResponseEntity<?> scheduleDefense(Long studentModalityId, ScheduleDefenseDTO request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        User projectDirector = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        StudentModality studentModality = studentModalityRepository.findById(studentModalityId)
                .orElseThrow(() -> new RuntimeException("Modalidad no encontrada"));
        if (studentModality.getProjectDirector() == null ||
                !studentModality.getProjectDirector().getId().equals(projectDirector.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("No tiene permiso para proponer sustentación. No es el director asignado a esta modalidad");
        }
        if (studentModality.getStatus() != ModalityProcessStatus.FINAL_REVIEW_COMPLETED) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", "La modalidad no se encuentra en estado válido para proponer sustentación",
                            "currentStatus", studentModality.getStatus()
                    )
            );
        }
        if (request.getDefenseDate() == null ||
                request.getDefenseLocation() == null ||
                request.getDefenseLocation().isBlank()) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", "Debe ingresar fecha y lugar válidos para la sustentación propuesta"
                    )
            );
        }
        studentModality.setDefenseDate(request.getDefenseDate());
        studentModality.setDefenseLocation(request.getDefenseLocation());
        studentModality.setStatus(ModalityProcessStatus.DEFENSE_SCHEDULED);
        studentModality.setUpdatedAt(LocalDateTime.now());
        studentModalityRepository.save(studentModality);
        historyRepository.save(
                ModalityProcessStatusHistory.builder()
                        .studentModality(studentModality)
                        .status(ModalityProcessStatus.DEFENSE_SCHEDULED)
                        .changeDate(LocalDateTime.now())
                        .responsible(projectDirector)
                        .observations(
                                "Director de proyecto programó la sustentación para el "
                                        + request.getDefenseDate()
                                        + " en "
                                        + request.getDefenseLocation()
                        )
                        .build()
        );
        // Notificar al estudiante líder
        User student = studentModality.getLeader();
        notificationEventPublisher.publish(
                new DefenseScheduledEvent(
                        studentModality.getId(),
                        request.getDefenseDate(),
                        request.getDefenseLocation(),
                        student.getId()
                )
        );
        // Notificar a los jurados asociados
        List<DefenseExaminer> examiners = defenseExaminerRepository.findByStudentModalityId(studentModalityId);
        for (DefenseExaminer examinerAssignment : examiners) {
            User examiner = examinerAssignment.getExaminer();
            notificationEventPublisher.publish(
                    new DefenseScheduledEvent(
                            studentModality.getId(),
                            request.getDefenseDate(),
                            request.getDefenseLocation(),
                            examiner.getId()
                    )
            );
        }
        // Notificar al director (ya se hace arriba, pero si se requiere explícitamente)
        notificationEventPublisher.publish(
                new DefenseScheduledEvent(
                        studentModality.getId(),
                        request.getDefenseDate(),
                        request.getDefenseLocation(),
                        projectDirector.getId()
                )
        );
        return ResponseEntity.ok(
                Map.of(
                        "success", true,
                        "studentModalityId", studentModalityId,
                        "defenseDate", request.getDefenseDate(),
                        "defenseLocation", request.getDefenseLocation(),
                        "newStatus", ModalityProcessStatus.DEFENSE_SCHEDULED,
                        "message", "Sustentación programada correctamente por el director de proyecto"
                )
        );
    }


    @Transactional
    public ResponseEntity<?> getPendingDefenseProposals() {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User committeeMember = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));


        List<Long> academicProgramIds = programAuthorityRepository
                .findByUser_Id(committeeMember.getId())
                .stream()
                .filter(pa -> pa.getRole() == ProgramRole.PROGRAM_CURRICULUM_COMMITTEE)
                .map(pa -> pa.getAcademicProgram().getId())
                .toList();

        if (academicProgramIds.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("El usuario no tiene el rol de PROGRAM_CURRICULUM_COMMITTEE");
        }


        List<StudentModality> pendingProposals = studentModalityRepository
                .findByStatusAndProgramDegreeModality_AcademicProgram_IdIn(
                        ModalityProcessStatus.DEFENSE_REQUESTED_BY_PROJECT_DIRECTOR,
                        academicProgramIds
                );


        List<DefenseProposalDTO> proposals = pendingProposals.stream()
                .map(sm -> {
                    User student = sm.getLeader();
                    User director = sm.getProjectDirector();


                    String studentCode = null;
                    Optional<StudentProfile> profile = studentProfileRepository.findByUserId(student.getId());
                    if (profile.isPresent()) {
                        studentCode = profile.get().getStudentCode();
                    }

                    return DefenseProposalDTO.builder()
                            .studentModalityId(sm.getId())
                            .studentName(student.getName() + " " + student.getLastName())
                            .studentEmail(student.getEmail())
                            .studentCode(studentCode)
                            .modalityName(sm.getProgramDegreeModality().getDegreeModality().getName())
                            .academicProgram(sm.getProgramDegreeModality().getAcademicProgram().getName())
                            .faculty(sm.getProgramDegreeModality().getAcademicProgram().getFaculty().getName())
                            .projectDirectorId(director != null ? director.getId() : null)
                            .projectDirectorName(director != null ? director.getName() + " " + director.getLastName() : "No asignado")
                            .projectDirectorEmail(director != null ? director.getEmail() : null)
                            .proposedDefenseDate(sm.getDefenseDate())
                            .proposedDefenseLocation(sm.getDefenseLocation())
                            .proposalSubmittedAt(sm.getUpdatedAt())
                            .currentStatus(sm.getStatus().name())
                            .statusDescription(describeModalityStatus(sm.getStatus()))
                            .build();
                })
                .toList();

        return ResponseEntity.ok(
                Map.of(
                        "success", true,
                        "totalProposals", proposals.size(),
                        "proposals", proposals
                )
        );
    }

    @Transactional
    public ResponseEntity<?> approveDefenseProposal(Long studentModalityId) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User committeeMember = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        StudentModality studentModality = studentModalityRepository.findById(studentModalityId)
                .orElseThrow(() -> new RuntimeException("Modalidad no encontrada"));

        Long academicProgramId = studentModality
                .getProgramDegreeModality()
                .getAcademicProgram()
                .getId();


        boolean authorized = programAuthorityRepository.existsByUser_IdAndAcademicProgram_IdAndRole(
                committeeMember.getId(),
                academicProgramId,
                ProgramRole.PROGRAM_CURRICULUM_COMMITTEE
        );

        if (!authorized) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("No tiene permiso para aprobar sustentaciones en este programa académico");
        }


        if (studentModality.getStatus() != ModalityProcessStatus.DEFENSE_REQUESTED_BY_PROJECT_DIRECTOR) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", "La modalidad no tiene una propuesta de sustentación pendiente de aprobación",
                            "currentStatus", studentModality.getStatus()
                    )
            );
        }


        if (studentModality.getDefenseDate() == null ||
                studentModality.getDefenseLocation() == null ||
                studentModality.getDefenseLocation().isBlank()) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", "No hay fecha y lugar propuestos para aprobar"
                    )
            );
        }


        LocalDateTime approvedDate = studentModality.getDefenseDate();
        String approvedLocation = studentModality.getDefenseLocation();


        studentModality.setStatus(ModalityProcessStatus.DEFENSE_SCHEDULED);
        studentModality.setUpdatedAt(LocalDateTime.now());
        studentModalityRepository.save(studentModality);


        historyRepository.save(
                ModalityProcessStatusHistory.builder()
                        .studentModality(studentModality)
                        .status(ModalityProcessStatus.DEFENSE_SCHEDULED)
                        .changeDate(LocalDateTime.now())
                        .responsible(committeeMember)
                        .observations(
                                String.format(
                                        "Comité de currículo aprobó la propuesta del director de proyecto. " +
                                        "Sustentación programada para el %s en %s",
                                        approvedDate,
                                        approvedLocation
                                )
                        )
                        .build()
        );


        notificationEventPublisher.publish(
                new DefenseScheduledEvent(
                        studentModality.getId(),
                        approvedDate,
                        approvedLocation,
                        committeeMember.getId()
                )
        );

        return ResponseEntity.ok(
                Map.of(
                        "success", true,
                        "studentModalityId", studentModalityId,
                        "defenseDate", approvedDate,
                        "defenseLocation", approvedLocation,
                        "newStatus", ModalityProcessStatus.DEFENSE_SCHEDULED,
                        "action", "APROBADA",
                        "message", "Propuesta de sustentación aprobada correctamente"
                )
        );
    }

    @Transactional
    public ResponseEntity<?> rescheduleDefense(Long studentModalityId, ScheduleDefenseDTO request) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User committeeMember = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        StudentModality studentModality = studentModalityRepository.findById(studentModalityId)
                .orElseThrow(() -> new RuntimeException("Modalidad no encontrada"));

        Long academicProgramId = studentModality
                .getProgramDegreeModality()
                .getAcademicProgram()
                .getId();


        boolean authorized = programAuthorityRepository.existsByUser_IdAndAcademicProgram_IdAndRole(
                committeeMember.getId(),
                academicProgramId,
                ProgramRole.PROGRAM_CURRICULUM_COMMITTEE
        );

        if (!authorized) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("No tiene permiso para reprogramar sustentaciones en este programa académico");
        }


        if (studentModality.getStatus() != ModalityProcessStatus.DEFENSE_REQUESTED_BY_PROJECT_DIRECTOR &&
                studentModality.getStatus() != ModalityProcessStatus.PROPOSAL_APPROVED) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", "La modalidad no se encuentra en estado válido para reprogramar sustentación",
                            "currentStatus", studentModality.getStatus()
                    )
            );
        }


        if (request.getDefenseDate() == null ||
                request.getDefenseLocation() == null ||
                request.getDefenseLocation().isBlank()) {

            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", "Debe ingresar fecha y lugar válidos para la reprogramación"
                    )
            );
        }


        LocalDateTime originalProposedDate = studentModality.getDefenseDate();
        String originalProposedLocation = studentModality.getDefenseLocation();
        boolean hadProposal = studentModality.getStatus() == ModalityProcessStatus.DEFENSE_REQUESTED_BY_PROJECT_DIRECTOR;


        studentModality.setDefenseDate(request.getDefenseDate());
        studentModality.setDefenseLocation(request.getDefenseLocation());
        studentModality.setStatus(ModalityProcessStatus.DEFENSE_SCHEDULED);
        studentModality.setUpdatedAt(LocalDateTime.now());
        studentModalityRepository.save(studentModality);


        String observation;
        if (hadProposal && originalProposedDate != null && originalProposedLocation != null) {
            observation = String.format(
                    "Comité de currículo reprogramó la sustentación. " +
                    "Propuesta original del director: %s en %s. " +
                    "Nueva programación: %s en %s",
                    originalProposedDate,
                    originalProposedLocation,
                    request.getDefenseDate(),
                    request.getDefenseLocation()
            );
        } else {
            observation = String.format(
                    "Comité de currículo programó la sustentación para el %s en %s",
                    request.getDefenseDate(),
                    request.getDefenseLocation()
            );
        }


        historyRepository.save(
                ModalityProcessStatusHistory.builder()
                        .studentModality(studentModality)
                        .status(ModalityProcessStatus.DEFENSE_SCHEDULED)
                        .changeDate(LocalDateTime.now())
                        .responsible(committeeMember)
                        .observations(observation)
                        .build()
        );


        notificationEventPublisher.publish(
                new DefenseScheduledEvent(
                        studentModality.getId(),
                        request.getDefenseDate(),
                        request.getDefenseLocation(),
                        committeeMember.getId()
                )
        );

        return ResponseEntity.ok(
                Map.of(
                        "success", true,
                        "studentModalityId", studentModalityId,
                        "defenseDate", request.getDefenseDate(),
                        "defenseLocation", request.getDefenseLocation(),
                        "newStatus", ModalityProcessStatus.DEFENSE_SCHEDULED,
                        "action", hadProposal ? "REPROGRAMADA" : "PROGRAMADA",
                        "hadProposal", hadProposal,
                        "message", hadProposal ? "Sustentación reprogramada correctamente" : "Sustentación programada correctamente"
                )
        );
    }

    @Transactional
    public ResponseEntity<?> assignExaminers(Long studentModalityId, ScheduleDefenseDTO request) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User committeeMember = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        StudentModality studentModality = studentModalityRepository.findById(studentModalityId)
                .orElseThrow(() -> new RuntimeException("Modalidad no encontrada"));

        Long academicProgramId = studentModality
                .getProgramDegreeModality()
                .getAcademicProgram()
                .getId();


        boolean authorized = programAuthorityRepository.existsByUser_IdAndAcademicProgram_IdAndRole(
                committeeMember.getId(),
                academicProgramId,
                ProgramRole.PROGRAM_CURRICULUM_COMMITTEE
        );

        if (!authorized) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "success", false,
                            "message", "No tiene permiso para asignar jurados en este programa académico"
                    ));
        }


        if (studentModality.getStatus() != ModalityProcessStatus.READY_FOR_EXAMINERS) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", "La modalidad debe estar en estado 'Listo para jurados' para asignar jurados",
                            "currentStatus", studentModality.getStatus()
                    )
            );
        }


        if (request.getPrimaryExaminer1Id() == null &&
                request.getPrimaryExaminer2Id() == null &&
                request.getTiebreakerExaminerId() == null) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", "Debe proporcionar al menos un jurado para asignar"
                    )
            );
        }


        List<Long> examinerIds = new ArrayList<>();
        if (request.getPrimaryExaminer1Id() != null) examinerIds.add(request.getPrimaryExaminer1Id());
        if (request.getPrimaryExaminer2Id() != null) examinerIds.add(request.getPrimaryExaminer2Id());
        if (request.getTiebreakerExaminerId() != null) examinerIds.add(request.getTiebreakerExaminerId());


        Set<Long> uniqueIds = new HashSet<>(examinerIds);
        if (uniqueIds.size() != examinerIds.size()) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", "No se pueden asignar el mismo jurado más de una vez"
                    )
            );
        }

        List<String> examinerAssignmentMessages = new ArrayList<>();


        if (request.getPrimaryExaminer1Id() != null) {
            User examiner1 = userRepository.findById(request.getPrimaryExaminer1Id())
                    .orElseThrow(() -> new RuntimeException("Jurado principal 1 no encontrado"));


            boolean hasExaminerRole = examiner1.getRoles().stream()
                    .anyMatch(role -> role.getName().equals("EXAMINER"));

            if (!hasExaminerRole) {
                return ResponseEntity.badRequest().body(
                        Map.of(
                                "success", false,
                                "message", "El usuario seleccionado como jurado principal 1 no tiene el rol EXAMINER"
                        )
                );
            }



            if (studentModality.getProjectDirector() != null &&
                    studentModality.getProjectDirector().getId().equals(examiner1.getId())) {
                return ResponseEntity.badRequest().body(
                        Map.of(
                                "success", false,
                                "message", "El director del proyecto no puede ser jurado de la misma modalidad"
                        )
                );
            }


            defenseExaminerRepository
                    .findByStudentModalityIdAndExaminerType(studentModalityId, ExaminerType.PRIMARY_EXAMINER_1)
                    .ifPresent(defenseExaminerRepository::delete);


            DefenseExaminer defenseExaminer = DefenseExaminer.builder()
                    .studentModality(studentModality)
                    .examiner(examiner1)
                    .examinerType(ExaminerType.PRIMARY_EXAMINER_1)
                    .assignmentDate(LocalDateTime.now())
                    .assignedBy(committeeMember)
                    .build();

            defenseExaminerRepository.save(defenseExaminer);
            examinerAssignmentMessages.add("Jurado Principal 1: " + examiner1.getName() + " " + examiner1.getLastName());
        }


        if (request.getPrimaryExaminer2Id() != null) {
            User examiner2 = userRepository.findById(request.getPrimaryExaminer2Id())
                    .orElseThrow(() -> new RuntimeException("Jurado principal 2 no encontrado"));


            boolean hasExaminerRole = examiner2.getRoles().stream()
                    .anyMatch(role -> role.getName().equals("EXAMINER"));

            if (!hasExaminerRole) {
                return ResponseEntity.badRequest().body(
                        Map.of(
                                "success", false,
                                "message", "El usuario seleccionado como jurado principal 2 no tiene el rol EXAMINER"
                        )
                );
            }



            if (studentModality.getProjectDirector() != null &&
                    studentModality.getProjectDirector().getId().equals(examiner2.getId())) {
                return ResponseEntity.badRequest().body(
                        Map.of(
                                "success", false,
                                "message", "El director del proyecto no puede ser jurado de la misma modalidad"
                        )
                );
            }


            defenseExaminerRepository
                    .findByStudentModalityIdAndExaminerType(studentModalityId, ExaminerType.PRIMARY_EXAMINER_2)
                    .ifPresent(defenseExaminerRepository::delete);


            DefenseExaminer defenseExaminer = DefenseExaminer.builder()
                    .studentModality(studentModality)
                    .examiner(examiner2)
                    .examinerType(ExaminerType.PRIMARY_EXAMINER_2)
                    .assignmentDate(LocalDateTime.now())
                    .assignedBy(committeeMember)
                    .build();

            defenseExaminerRepository.save(defenseExaminer);
            examinerAssignmentMessages.add("Jurado Principal 2: " + examiner2.getName() + " " + examiner2.getLastName());
        }


        if (request.getTiebreakerExaminerId() != null) {
            User examiner3 = userRepository.findById(request.getTiebreakerExaminerId())
                    .orElseThrow(() -> new RuntimeException("Jurado de desempate no encontrado"));


            boolean hasExaminerRole = examiner3.getRoles().stream()
                    .anyMatch(role -> role.getName().equals("EXAMINER"));

            if (!hasExaminerRole) {
                return ResponseEntity.badRequest().body(
                        Map.of(
                                "success", false,
                                "message", "El usuario seleccionado como jurado de desempate no tiene el rol EXAMINER"
                        )
                );
            }



            if (studentModality.getProjectDirector() != null &&
                    studentModality.getProjectDirector().getId().equals(examiner3.getId())) {
                return ResponseEntity.badRequest().body(
                        Map.of(
                                "success", false,
                                "message", "El director del proyecto no puede ser jurado de la misma modalidad"
                        )
                );
            }


            defenseExaminerRepository
                    .findByStudentModalityIdAndExaminerType(studentModalityId, ExaminerType.TIEBREAKER_EXAMINER)
                    .ifPresent(defenseExaminerRepository::delete);


            DefenseExaminer defenseExaminer = DefenseExaminer.builder()
                    .studentModality(studentModality)
                    .examiner(examiner3)
                    .examinerType(ExaminerType.TIEBREAKER_EXAMINER)
                    .assignmentDate(LocalDateTime.now())
                    .assignedBy(committeeMember)
                    .build();

            defenseExaminerRepository.save(defenseExaminer);
            examinerAssignmentMessages.add("Jurado de Desempate: " + examiner3.getName() + " " + examiner3.getLastName());
        }



        studentModality.setStatus(ModalityProcessStatus.EXAMINERS_ASSIGNED);
        studentModality.setUpdatedAt(LocalDateTime.now());
        studentModalityRepository.save(studentModality);

        String observationMessage = "Jurados asignados por el comité de currículo:\n" +
                String.join("\n", examinerAssignmentMessages);

        historyRepository.save(
                ModalityProcessStatusHistory.builder()
                        .studentModality(studentModality)
                        .status(ModalityProcessStatus.EXAMINERS_ASSIGNED)
                        .changeDate(LocalDateTime.now())
                        .responsible(committeeMember)
                        .observations(observationMessage)
                        .build()
        );

        // Notificar a los jurados asignados
        examinerNotificationListener.notifyExaminersAssignment(studentModalityId);

        // Publicar evento para notificar a estudiantes y director
        ExaminersAssignedEvent event = new ExaminersAssignedEvent(studentModalityId);
        applicationEventPublisher.publishEvent(event);

        return ResponseEntity.ok(
                Map.of(
                        "success", true,
                        "studentModalityId", studentModalityId,
                        "newStatus", ModalityProcessStatus.EXAMINERS_ASSIGNED,
                        "examinersAssigned", examinerAssignmentMessages,
                        "message", "Jurados asignados correctamente a la sustentación"
                )
        );
    }


    @Transactional
    public ResponseEntity<?> registerFinalDefenseEvaluation(Long studentModalityId, ExaminerEvaluationDTO evaluationDTO) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User examiner = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        StudentModality studentModality = studentModalityRepository.findById(studentModalityId)
                .orElseThrow(() -> new RuntimeException("Modalidad no encontrada"));

        DefenseExaminer defenseExaminer = defenseExaminerRepository
                .findByStudentModalityIdAndExaminerId(studentModalityId, examiner.getId())
                .orElseThrow(() -> new RuntimeException(
                        "No está asignado como jurado de esta sustentación"
                ));

        if (defenseEvaluationCriteriaRepository.existsByDefenseExaminerId(defenseExaminer.getId())) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", "Ya ha registrado su evaluación para esta sustentación"
                    )
            );
        }

        if (studentModality.getStatus() != ModalityProcessStatus.DEFENSE_COMPLETED &&
                studentModality.getStatus() != ModalityProcessStatus.READY_FOR_DEFENSE &&
                studentModality.getStatus() != ModalityProcessStatus.EXAMINERS_ASSIGNED &&
                studentModality.getStatus() != ModalityProcessStatus.UNDER_EVALUATION_PRIMARY_EXAMINERS &&
                studentModality.getStatus() != ModalityProcessStatus.UNDER_EVALUATION_TIEBREAKER &&
                studentModality.getStatus() != ModalityProcessStatus.DEFENSE_SCHEDULED &&
                studentModality.getStatus() != ModalityProcessStatus.DISAGREEMENT_REQUIRES_TIEBREAKER) {

            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", "La modalidad no está en estado válido para registrar evaluaciones",
                            "currentStatus", studentModality.getStatus()
                    )
            );
        }

        // Punto 3: El jurado de desempate SOLO puede evaluar si hay desacuerdo entre primarios
        if (defenseExaminer.getExaminerType() == ExaminerType.TIEBREAKER_EXAMINER &&
                studentModality.getStatus() != ModalityProcessStatus.DISAGREEMENT_REQUIRES_TIEBREAKER) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", "El jurado de desempate solo puede evaluar cuando existe desacuerdo entre los jurados principales (un jurado aprueba y el otro rechaza).",
                            "currentStatus", studentModality.getStatus()
                    )
            );
        }

        // Los jurados primarios no pueden evaluar si ya hay desacuerdo resuelto al desempate
        if (defenseExaminer.getExaminerType() != ExaminerType.TIEBREAKER_EXAMINER &&
                studentModality.getStatus() == ModalityProcessStatus.DISAGREEMENT_REQUIRES_TIEBREAKER) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", "Existe desacuerdo entre los jurados principales. Solo el jurado de desempate puede evaluar en este momento.",
                            "currentStatus", studentModality.getStatus()
                    )
            );
        }

        // Validar nota
        if (evaluationDTO.getGrade() == null || evaluationDTO.getGrade() < 0.0 || evaluationDTO.getGrade() > 5.0) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "La calificación debe estar entre 0.0 y 5.0"
            ));
        }

        // Construir la entidad DefenseEvaluationCriteria con toda la información
        DefenseEvaluationCriteria.DefenseEvaluationCriteriaBuilder criteriaBuilder =
                DefenseEvaluationCriteria.builder()
                        .defenseExaminer(defenseExaminer)
                        .grade(evaluationDTO.getGrade())
                        .observations(evaluationDTO.getObservations())
                        .isFinalDecision(false)
                        .evaluatedAt(LocalDateTime.now());

        DefenseRubricType expectedRubricType = resolveDefenseRubricType(studentModality);
        DefenseEvaluationCriteriaDTO criteriaDTO = evaluationDTO.getEvaluationCriteria();

        if (criteriaDTO == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Debe enviar la rúbrica de evaluación en el campo evaluationCriteria.",
                    "expectedRubricType", expectedRubricType.name()
            ));
        }

        if (criteriaDTO.getRubricType() != null && criteriaDTO.getRubricType() != expectedRubricType) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "El tipo de rúbrica enviado no coincide con la modalidad evaluada.",
                    "expectedRubricType", expectedRubricType.name(),
                    "receivedRubricType", criteriaDTO.getRubricType().name()
            ));
        }

        if (expectedRubricType == DefenseRubricType.ENTREPRENEURSHIP) {
            if (criteriaDTO.getEntrepreneurshipPresentationSupportMaterial() == null
                    || criteriaDTO.getEntrepreneurshipCoherentBusinessObjectives() == null
                    || criteriaDTO.getEntrepreneurshipMethodologyTechnicalApproach() == null
                    || criteriaDTO.getEntrepreneurshipAnalyticalCreativeCapacity() == null
                    || criteriaDTO.getEntrepreneurshipDefenseSustentation() == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Para la modalidad de Emprendimiento y fortalecimiento de empresa debe enviar los 5 criterios específicos de la rúbrica empresarial.",
                        "expectedRubricType", expectedRubricType.name()
                ));
            }

            criteriaBuilder
                    .rubricType(DefenseRubricType.ENTREPRENEURSHIP)
                    .entrepreneurshipPresentationSupportMaterial(criteriaDTO.getEntrepreneurshipPresentationSupportMaterial())
                    .entrepreneurshipCoherentBusinessObjectives(criteriaDTO.getEntrepreneurshipCoherentBusinessObjectives())
                    .entrepreneurshipMethodologyTechnicalApproach(criteriaDTO.getEntrepreneurshipMethodologyTechnicalApproach())
                    .entrepreneurshipAnalyticalCreativeCapacity(criteriaDTO.getEntrepreneurshipAnalyticalCreativeCapacity())
                    .entrepreneurshipDefenseSustentation(criteriaDTO.getEntrepreneurshipDefenseSustentation())
                    .proposedMention(criteriaDTO.getProposedMention() != null
                            ? criteriaDTO.getProposedMention()
                            : ProposedMention.NONE)
                    // Se mapean también a la rúbrica estándar para mantener compatibilidad histórica
                    // con reportes/queries existentes que leen los 5 campos legacy.
                    .domainAndClarity(criteriaDTO.getEntrepreneurshipCoherentBusinessObjectives())
                    .synthesisAndCommunication(criteriaDTO.getEntrepreneurshipPresentationSupportMaterial())
                    .argumentationAndResponse(criteriaDTO.getEntrepreneurshipDefenseSustentation())
                    .innovationAndImpact(criteriaDTO.getEntrepreneurshipAnalyticalCreativeCapacity())
                    .professionalPresentation(criteriaDTO.getEntrepreneurshipMethodologyTechnicalApproach());
        } else {
            if (criteriaDTO.getDomainAndClarity() == null
                    || criteriaDTO.getSynthesisAndCommunication() == null
                    || criteriaDTO.getArgumentationAndResponse() == null
                    || criteriaDTO.getInnovationAndImpact() == null
                    || criteriaDTO.getProfessionalPresentation() == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Para esta modalidad debe enviar los 5 criterios estándar de la rúbrica.",
                        "expectedRubricType", expectedRubricType.name()
                ));
            }

            criteriaBuilder
                    .rubricType(DefenseRubricType.STANDARD)
                    .domainAndClarity(criteriaDTO.getDomainAndClarity())
                    .synthesisAndCommunication(criteriaDTO.getSynthesisAndCommunication())
                    .argumentationAndResponse(criteriaDTO.getArgumentationAndResponse())
                    .innovationAndImpact(criteriaDTO.getInnovationAndImpact())
                    .professionalPresentation(criteriaDTO.getProfessionalPresentation())
                    .proposedMention(criteriaDTO.getProposedMention() != null
                            ? criteriaDTO.getProposedMention()
                            : ProposedMention.NONE);
        }

        DefenseEvaluationCriteria evaluation = criteriaBuilder.build();
        defenseEvaluationCriteriaRepository.save(evaluation);

        if (defenseExaminer.getExaminerType() == ExaminerType.TIEBREAKER_EXAMINER) {
            return processTiebreakerEvaluation(studentModality, evaluation, examiner);
        } else {

            return processPrimaryExaminerEvaluation(studentModality, evaluation, examiner);
        }
    }

    @Transactional
    public ResponseEntity<?> getFinalDefenseEvaluationForExaminer(Long studentModalityId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        User examiner = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        StudentModality studentModality = studentModalityRepository.findById(studentModalityId)
                .orElseThrow(() -> new RuntimeException("Modalidad no encontrada"));

        DefenseExaminer defenseExaminer = defenseExaminerRepository
                .findByStudentModalityIdAndExaminerId(studentModalityId, examiner.getId())
                .orElseThrow(() -> new RuntimeException(
                        "No está asignado como jurado de esta sustentación"
                ));

        DefenseEvaluationCriteria evaluation = defenseEvaluationCriteriaRepository
                .findByDefenseExaminerId(defenseExaminer.getId())
                .orElse(null);

        if (evaluation == null) {
            return ResponseEntity.ok(
                    Map.of(
                            "success", false,
                            "message", "No hay evaluación registrada para este jurado en esta modalidad"
                    )
            );
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("evaluationId", evaluation.getId());
        response.put("grade", evaluation.getGrade());
        response.put("approved", evaluation.getGrade() != null && evaluation.getGrade() >= 3.5);
        response.put("observations", evaluation.getObservations());
        response.put("evaluationDate", evaluation.getEvaluatedAt());
        response.put("isFinalDecision", evaluation.getIsFinalDecision());
        response.put("examinerType", defenseExaminer.getExaminerType());

        response.put("evaluationCriteria", buildDefenseCriteriaResponse(evaluation));

        return ResponseEntity.ok(response);
    }


    private ResponseEntity<?> processPrimaryExaminerEvaluation(StudentModality studentModality, DefenseEvaluationCriteria currentEvaluation, User examiner) {

        if (studentModality.getStatus() == ModalityProcessStatus.DEFENSE_COMPLETED) {
            studentModality.setStatus(ModalityProcessStatus.UNDER_EVALUATION_PRIMARY_EXAMINERS);
            studentModality.setUpdatedAt(LocalDateTime.now());
            studentModalityRepository.save(studentModality);
        }

        boolean bothEvaluated = defenseEvaluationCriteriaRepository
                .bothPrimaryExaminersHaveEvaluated(studentModality.getId());

        if (!bothEvaluated) {



            return ResponseEntity.ok(
                    Map.of(
                            "success", true,
                            "message", "Evaluación registrada correctamente. Esperando evaluación del otro jurado principal.",
                            "grade", currentEvaluation.getGrade(),
                            "approved", currentEvaluation.getGrade() >= 3.5
                    )
            );
        }


        List<DefenseEvaluationCriteria> primaryEvaluations = defenseEvaluationCriteriaRepository
                .findPrimaryEvaluationsByStudentModalityId(studentModality.getId());

        boolean hasConsensus = defenseEvaluationCriteriaRepository
                .primaryExaminersHaveConsensus(studentModality.getId());

        if (hasConsensus) {
            return applyFinalResultWithConsensus(studentModality, primaryEvaluations, examiner);
        } else {

            return requestTiebreakerExaminer(studentModality, primaryEvaluations, examiner);
        }
    }


    private ResponseEntity<?> applyFinalResultWithConsensus(StudentModality studentModality, List<DefenseEvaluationCriteria> primaryEvaluations, User examiner) {

        // La nota final es el promedio de las dos notas de los jurados principales (punto 4)
        Double averageGrade = defenseEvaluationCriteriaRepository
                .calculateAverageGradeOfPrimaryExaminers(studentModality.getId());

        primaryEvaluations.forEach(eval -> {
            eval.setIsFinalDecision(true);
            defenseEvaluationCriteriaRepository.save(eval);
        });

        // La aprobación se determina por nota: >= 3.5 = aprobado, < 3.5 = reprobado
        boolean approved = averageGrade != null && averageGrade >= 3.5;

        AcademicDistinction distinction;
        ModalityProcessStatus finalStatus;
        boolean pendingDistinctionReview = false;

        if (!approved) {
            distinction = AcademicDistinction.AGREED_REJECTED;
            finalStatus = ModalityProcessStatus.GRADED_FAILED;
        } else {
            // La mención solo se propone si AMBOS jurados coinciden unánimemente
            ProposedMention mention1 = primaryEvaluations.get(0).getProposedMention();
            ProposedMention mention2 = primaryEvaluations.get(1).getProposedMention();

            if (mention1 != null && mention2 != null && mention1 == mention2
                    && mention1 == ProposedMention.LAUREATE) {
                // Los jurados PROPONEN la mención Laureada → el comité debe decidir
                distinction = AcademicDistinction.PENDING_COMMITTEE_LAUREATE;
                finalStatus = ModalityProcessStatus.PENDING_DISTINCTION_COMMITTEE_REVIEW;
                pendingDistinctionReview = true;
            } else if (mention1 != null && mention2 != null && mention1 == mention2
                    && mention1 == ProposedMention.MERITORIOUS) {
                // Los jurados PROPONEN la mención Meritoria → el comité debe decidir
                distinction = AcademicDistinction.PENDING_COMMITTEE_MERITORIOUS;
                finalStatus = ModalityProcessStatus.PENDING_DISTINCTION_COMMITTEE_REVIEW;
                pendingDistinctionReview = true;
            } else {
                distinction = AcademicDistinction.AGREED_APPROVED;
                finalStatus = ModalityProcessStatus.GRADED_APPROVED;
            }
        }

        studentModality.setStatus(finalStatus);
        studentModality.setAcademicDistinction(distinction);
        studentModality.setFinalGrade(averageGrade);
        studentModality.setUpdatedAt(LocalDateTime.now());
        studentModalityRepository.save(studentModality);

        // Construir la observación con los argumentos de los jurados sobre la mención
        String mentionNotes = primaryEvaluations.stream()
                .filter(e -> e.getObservations() != null && !e.getObservations().isBlank())
                .map(e -> "Jurado " + (e.getDefenseExaminer().getExaminerType() != null
                        ? e.getDefenseExaminer().getExaminerType().name() : "") + ": " + e.getObservations())
                .collect(Collectors.joining(" | "));

        String observations;
        if (pendingDistinctionReview) {
            observations = String.format(
                    "CONSENSO entre jurados principales. Calificación final (promedio): %.2f. " +
                    "Resultado: APROBADO. Los jurados proponen la distinción: %s. " +
                    "PENDIENTE DE REVISIÓN por el Comité de Currículo. Argumentos: %s",
                    averageGrade,
                    translateAcademicDistinction(distinction),
                    mentionNotes.isBlank() ? "Sin argumentos adicionales" : mentionNotes
            );
        } else {
            observations = String.format(
                    "CONSENSO entre jurados principales. Calificación final (promedio): %.2f. " +
                    "Resultado: %s. Distinción: %s",
                    averageGrade,
                    approved ? "APROBADO" : "REPROBADO",
                    translateAcademicDistinction(distinction)
            );
        }

        historyRepository.save(
                ModalityProcessStatusHistory.builder()
                        .studentModality(studentModality)
                        .status(finalStatus)
                        .changeDate(LocalDateTime.now())
                        .responsible(examiner)
                        .observations(observations)
                        .build()
        );

        // Publicar siempre: incluso si la distinción queda pendiente de comité,
        // el estudiante debe recibir correo y acta de aprobación inicial.
        notificationEventPublisher.publish(
                new FinalDefenseResultEvent(
                        studentModality.getId(),
                        finalStatus,
                        distinction,
                        observations,
                        examiner.getId()
                )
        );

        String message;
        if (pendingDistinctionReview) {
            message = "¡Felicitaciones! Tu modalidad de grado ha sido aprobada por consenso de los jurados. Los jurados han propuesto una distinción honorífica (" +
                    translateAcademicDistinction(distinction) + "). El Comité de Currículo debe revisar y decidir si acepta o rechaza la distinción.";
        } else {
            message = approved ? "¡Felicitaciones! Tu modalidad de grado ha sido aprobada por consenso de los jurados." : "Tu modalidad de grado ha sido reprobada por consenso de los jurados.";
        }

        return ResponseEntity.ok(
                Map.of(
                        "exito", true,
                        "consenso", true,
                        "estadoFinal", finalStatus.name(),
                        "distincionAcademica", translateAcademicDistinction(distinction),
                        "calificacionFinal", averageGrade,
                        "distincionPendienteRevision", pendingDistinctionReview,
                        "mensaje", message
                )
        );
    }

    private ResponseEntity<?> requestTiebreakerExaminer(StudentModality studentModality, List<DefenseEvaluationCriteria> primaryEvaluations, User examiner) {

        studentModality.setStatus(ModalityProcessStatus.DISAGREEMENT_REQUIRES_TIEBREAKER);
        studentModality.setAcademicDistinction(AcademicDistinction.DISAGREEMENT_PENDING_TIEBREAKER);
        studentModality.setUpdatedAt(LocalDateTime.now());
        studentModalityRepository.save(studentModality);


        String observations = String.format(
                "DESACUERDO entre jurados principales. Jurado 1: %s (%.2f). Jurado 2: %s (%.2f). " +
                "Se requiere asignar un tercer jurado para desempatar.",
                primaryEvaluations.get(0).getGrade() >= 3.5 ? "APROBADO" : "REPROBADO",
                primaryEvaluations.get(0).getGrade(),
                primaryEvaluations.get(1).getGrade() >= 3.5 ? "APROBADO" : "REPROBADO",
                primaryEvaluations.get(1).getGrade()
        );

        historyRepository.save(
                ModalityProcessStatusHistory.builder()
                        .studentModality(studentModality)
                        .status(ModalityProcessStatus.DISAGREEMENT_REQUIRES_TIEBREAKER)
                        .changeDate(LocalDateTime.now())
                        .responsible(examiner)
                        .observations(observations)
                        .build()
        );

        return ResponseEntity.ok(
                Map.of(
                        "success", true,
                        "hasConsensus", false,
                        "requiresTiebreaker", true,
                        "status", ModalityProcessStatus.DISAGREEMENT_REQUIRES_TIEBREAKER,
                        "message", "No hay consenso entre los jurados principales. Se requiere asignar un tercer jurado para desempatar."
                )
        );
    }


    private ResponseEntity<?> processTiebreakerEvaluation(StudentModality studentModality, DefenseEvaluationCriteria tiebreakerEvaluation, User examiner) {

        tiebreakerEvaluation.setIsFinalDecision(true);
        defenseEvaluationCriteriaRepository.save(tiebreakerEvaluation);

        // La aprobación se determina por nota: >= 3.5 = aprobado (punto 2 y 3)
        // La nota final es la del jurado de desempate (punto 5)
        double tiebreakerGrade = tiebreakerEvaluation.getGrade();
        boolean approved = tiebreakerGrade >= 3.5;

        AcademicDistinction distinction;
        ModalityProcessStatus finalStatus;
        boolean pendingDistinctionReview = false;

        if (!approved) {
            distinction = AcademicDistinction.TIEBREAKER_REJECTED;
            finalStatus = ModalityProcessStatus.GRADED_FAILED;
        } else {
            // La mención la determina el proposedMention del jurado de desempate
            ProposedMention tiebreakerMention = tiebreakerEvaluation.getProposedMention();
            if (tiebreakerMention == ProposedMention.LAUREATE) {
                // El jurado de desempate PROPONE la mención Laureada → el comité debe decidir
                distinction = AcademicDistinction.TIEBREAKER_PENDING_COMMITTEE_LAUREATE;
                finalStatus = ModalityProcessStatus.PENDING_DISTINCTION_COMMITTEE_REVIEW;
                pendingDistinctionReview = true;
            } else if (tiebreakerMention == ProposedMention.MERITORIOUS) {
                // El jurado de desempate PROPONE la mención Meritoria → el comité debe decidir
                distinction = AcademicDistinction.TIEBREAKER_PENDING_COMMITTEE_MERITORIOUS;
                finalStatus = ModalityProcessStatus.PENDING_DISTINCTION_COMMITTEE_REVIEW;
                pendingDistinctionReview = true;
            } else {
                distinction = AcademicDistinction.TIEBREAKER_APPROVED;
                finalStatus = ModalityProcessStatus.GRADED_APPROVED;
            }
        }

        // La nota final en studentModality es la del tercer jurado (punto 5)
        studentModality.setStatus(finalStatus);
        studentModality.setAcademicDistinction(distinction);
        studentModality.setFinalGrade(tiebreakerGrade);
        studentModality.setUpdatedAt(LocalDateTime.now());
        studentModalityRepository.save(studentModality);

        String observations;
        if (pendingDistinctionReview) {
            String mentionNote = tiebreakerEvaluation.getObservations() != null
                    ? tiebreakerEvaluation.getObservations() : "Sin argumentos adicionales";
            observations = String.format(
                    "DESEMPATE resuelto por tercer jurado. Calificación final: %.2f. " +
                    "Resultado: APROBADO. El jurado de desempate propone la distinción: %s. " +
                    "PENDIENTE DE REVISIÓN por el Comité de Currículo. Argumento: %s",
                    tiebreakerGrade,
                    translateProposedDistinction(distinction),
                    mentionNote
            );
        } else {
            observations = String.format(
                    "DESEMPATE resuelto por tercer jurado. Calificación final: %.2f. " +
                    "Resultado: %s. Distinción: %s",
                    tiebreakerGrade,
                    approved ? "APROBADO" : "REPROBADO",
                    translateProposedDistinction(distinction)
            );
        }

        historyRepository.save(
                ModalityProcessStatusHistory.builder()
                        .studentModality(studentModality)
                        .status(finalStatus)
                        .changeDate(LocalDateTime.now())
                        .responsible(examiner)
                        .observations(observations)
                        .build()
        );

        // Publicar siempre: si queda pendiente de comité también se debe enviar acta inicial.
        notificationEventPublisher.publish(
                new FinalDefenseResultEvent(
                        studentModality.getId(),
                        finalStatus,
                        distinction,
                        observations,
                        examiner.getId()
                )
        );

        String message;
        if (pendingDistinctionReview) {
            message = "Modalidad APROBADA por decisión del jurado de desempate. El jurado ha PROPUESTO la distinción (" +
                    translateProposedDistinction(distinction) + "). El Comité de Currículo debe revisar y decidir si acepta o rechaza la distinción.";
        } else {
            message = approved ? "Modalidad APROBADA por decisión del jurado de desempate"
                    : "Modalidad REPROBADA por decisión del jurado de desempate";
        }

        return ResponseEntity.ok(
                Map.of(
                        "success", true,
                        "isTiebreaker", true,
                        "finalStatus", finalStatus,
                        "academicDistinction", distinction,
                        "finalGrade", tiebreakerGrade,
                        "pendingDistinctionReview", pendingDistinctionReview,
                        "message", message
                )
        );
    }

    public ResponseEntity<?> getFinalDefenseResult(Long studentModalityId) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        StudentModality studentModality = studentModalityRepository.findById(studentModalityId)
                .orElseThrow(() -> new RuntimeException("Modalidad no encontrada"));


        Long academicProgramId =
                studentModality
                        .getProgramDegreeModality()
                        .getAcademicProgram()
                        .getId();


        boolean authorized =
                programAuthorityRepository
                        .existsByUser_IdAndAcademicProgram_IdAndRoleIn(
                                user.getId(),
                                academicProgramId,
                                List.of(
                                        ProgramRole.PROGRAM_HEAD,
                                        ProgramRole.PROGRAM_CURRICULUM_COMMITTEE
                                )
                        );

        if (!authorized) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("No tiene permiso para consultar el resultado final de esta modalidad");
        }


        if (studentModality.getStatus() != ModalityProcessStatus.GRADED_APPROVED &&
                studentModality.getStatus() != ModalityProcessStatus.GRADED_FAILED) {

            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", "La modalidad aún no tiene un resultado final registrado",
                            "currentStatus", studentModality.getStatus()
                    )
            );
        }

        ModalityProcessStatus finalStatus = studentModality.getStatus();

        ModalityProcessStatusHistory history =
                historyRepository
                        .findTopByStudentModalityAndStatusOrderByChangeDateDesc(
                                studentModality,
                                finalStatus
                        )
                        .orElseThrow(() ->
                                new RuntimeException("No se encontró historial de evaluación final")
                        );


        List<DefenseExaminer> defenseExaminers = defenseExaminerRepository
                .findByStudentModalityId(studentModalityId);

        List<FinalDefenseResponse.ExaminerEvaluationDetail> examinerEvaluations = defenseExaminers.stream()
                .map(defenseExaminer -> {
                    DefenseEvaluationCriteria evaluation = defenseEvaluationCriteriaRepository
                            .findByDefenseExaminerId(defenseExaminer.getId())
                            .orElse(null);

                    if (evaluation == null) {
                        return null;
                    }

                    FinalDefenseResponse.CriteriaDetail criteriaDetail = buildFinalDefenseCriteriaDetail(evaluation);

                    return FinalDefenseResponse.ExaminerEvaluationDetail.builder()
                            .examinerName(defenseExaminer.getExaminer().getName() + " " +
                                        defenseExaminer.getExaminer().getLastName())
                            .examinerType(defenseExaminer.getExaminerType().name())
                            .grade(evaluation.getGrade())
                            .approved(evaluation.getGrade() != null && evaluation.getGrade() >= 3.5)
                            .observations(evaluation.getObservations())
                            .evaluationDate(evaluation.getEvaluatedAt())
                            .isFinalDecision(evaluation.getIsFinalDecision())
                            .evaluationCriteria(criteriaDetail)
                            .build();
                })
                .filter(detail -> detail != null)
                .toList();


        boolean hasConsensus = studentModality.getAcademicDistinction() != null &&
                              (studentModality.getAcademicDistinction().name().startsWith("AGREED_"));

        boolean wasTiebreaker = studentModality.getAcademicDistinction() != null &&
                               (studentModality.getAcademicDistinction().name().startsWith("TIEBREAKER_"));

        return ResponseEntity.ok(
                FinalDefenseResponse.builder()
                        .studentModalityId(studentModality.getId())
                        .studentName(
                                studentModality.getLeader().getName() + " " +
                                        studentModality.getLeader().getLastName()
                        )
                        .studentEmail(studentModality.getLeader().getEmail())
                        .finalStatus(finalStatus)
                        .approved(finalStatus == ModalityProcessStatus.GRADED_APPROVED)
                        .academicDistinction(studentModality.getAcademicDistinction())
                        .finalGrade(studentModality.getFinalGrade())
                        .observations(history.getObservations())
                        .evaluationDate(history.getChangeDate())
                        .evaluatedBy(
                                history.getResponsible() != null
                                        ? history.getResponsible().getName()
                                        : "Comité de currículo de programa"
                        )
                        .hasConsensus(hasConsensus)
                        .wasTiebreaker(wasTiebreaker)
                        .examinerEvaluations(examinerEvaluations)
                        .build()
        );
    }

    private DefenseRubricType resolveDefenseRubricType(StudentModality studentModality) {
        String modalityName = studentModality.getProgramDegreeModality().getDegreeModality().getName();
        String normalizedName = normalizeText(modalityName);
        if ("emprendimiento y fortalecimiento de empresa".equals(normalizedName)) {
            return DefenseRubricType.ENTREPRENEURSHIP;
        }
        return DefenseRubricType.STANDARD;
    }

    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return normalized.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }

    private Map<String, Object> buildDefenseCriteriaResponse(DefenseEvaluationCriteria evaluation) {
        if (evaluation == null) {
            return null;
        }

        Map<String, Object> criteriaMap = new LinkedHashMap<>();
        DefenseRubricType rubricType = evaluation.getRubricType() != null
                ? evaluation.getRubricType()
                : DefenseRubricType.STANDARD;

        criteriaMap.put("id", evaluation.getId());
        criteriaMap.put("rubricType", rubricType.name());
        criteriaMap.put("proposedMention", evaluation.getProposedMention());
        criteriaMap.put("evaluatedAt", evaluation.getEvaluatedAt());

        if (rubricType == DefenseRubricType.ENTREPRENEURSHIP) {
            criteriaMap.put("entrepreneurshipPresentationSupportMaterial", evaluation.getEntrepreneurshipPresentationSupportMaterial());
            criteriaMap.put("entrepreneurshipCoherentBusinessObjectives", evaluation.getEntrepreneurshipCoherentBusinessObjectives());
            criteriaMap.put("entrepreneurshipMethodologyTechnicalApproach", evaluation.getEntrepreneurshipMethodologyTechnicalApproach());
            criteriaMap.put("entrepreneurshipAnalyticalCreativeCapacity", evaluation.getEntrepreneurshipAnalyticalCreativeCapacity());
            criteriaMap.put("entrepreneurshipDefenseSustentation", evaluation.getEntrepreneurshipDefenseSustentation());
        } else {
            criteriaMap.put("domainAndClarity", evaluation.getDomainAndClarity());
            criteriaMap.put("synthesisAndCommunication", evaluation.getSynthesisAndCommunication());
            criteriaMap.put("argumentationAndResponse", evaluation.getArgumentationAndResponse());
            criteriaMap.put("innovationAndImpact", evaluation.getInnovationAndImpact());
            criteriaMap.put("professionalPresentation", evaluation.getProfessionalPresentation());
        }

        return criteriaMap;
    }

    private FinalDefenseResponse.CriteriaDetail buildFinalDefenseCriteriaDetail(DefenseEvaluationCriteria evaluation) {
        if (evaluation == null) {
            return null;
        }

        return FinalDefenseResponse.CriteriaDetail.builder()
                .rubricType(evaluation.getRubricType() != null ? evaluation.getRubricType() : DefenseRubricType.STANDARD)
                .domainAndClarity(evaluation.getDomainAndClarity())
                .synthesisAndCommunication(evaluation.getSynthesisAndCommunication())
                .argumentationAndResponse(evaluation.getArgumentationAndResponse())
                .innovationAndImpact(evaluation.getInnovationAndImpact())
                .professionalPresentation(evaluation.getProfessionalPresentation())
                .entrepreneurshipPresentationSupportMaterial(evaluation.getEntrepreneurshipPresentationSupportMaterial())
                .entrepreneurshipCoherentBusinessObjectives(evaluation.getEntrepreneurshipCoherentBusinessObjectives())
                .entrepreneurshipMethodologyTechnicalApproach(evaluation.getEntrepreneurshipMethodologyTechnicalApproach())
                .entrepreneurshipAnalyticalCreativeCapacity(evaluation.getEntrepreneurshipAnalyticalCreativeCapacity())
                .entrepreneurshipDefenseSustentation(evaluation.getEntrepreneurshipDefenseSustentation())
                .proposedMention(evaluation.getProposedMention())
                .evaluatedAt(evaluation.getEvaluatedAt())
                .build();
    }

    public ResponseEntity<?> getMyFinalDefenseResult() {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User student = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        StudentModality studentModality = studentModalityRepository
                .findByStudent(student)
                .orElseThrow(() -> new RuntimeException(
                        "No se encontró una modalidad asociada al estudiante"
                ));

        if (studentModality.getStatus() != ModalityProcessStatus.GRADED_APPROVED &&
                studentModality.getStatus() != ModalityProcessStatus.GRADED_FAILED) {

            return ResponseEntity.ok(
                    Map.of(
                            "hasResult", false,
                            "message", "Tu modalidad aún no tiene un resultado final"
                    )
            );
        }

        ModalityProcessStatus finalStatus = studentModality.getStatus();

        ModalityProcessStatusHistory history = historyRepository
                .findTopByStudentModalityAndStatusOrderByChangeDateDesc(
                        studentModality,
                        finalStatus
                )
                .orElseThrow(() -> new RuntimeException(
                        "No se encontró historial de evaluación final"
                ));


        List<DefenseExaminer> defenseExaminers = defenseExaminerRepository
                .findByStudentModalityId(studentModality.getId());

        List<FinalDefenseResponse.ExaminerEvaluationDetail> examinerEvaluations = defenseExaminers.stream()
                .map(defenseExaminer -> {
                    DefenseEvaluationCriteria evaluation = defenseEvaluationCriteriaRepository
                            .findByDefenseExaminerId(defenseExaminer.getId())
                            .orElse(null);

                    if (evaluation == null) {
                        return null;
                    }

                    return FinalDefenseResponse.ExaminerEvaluationDetail.builder()
                            .examinerName(defenseExaminer.getExaminer().getName() + " " +
                                        defenseExaminer.getExaminer().getLastName())
                            .examinerType(defenseExaminer.getExaminerType().name())
                            .grade(evaluation.getGrade())
                            .approved(evaluation.getGrade() != null && evaluation.getGrade() >= 3.5)
                            .observations(evaluation.getObservations())
                            .evaluationDate(evaluation.getEvaluatedAt())
                            .isFinalDecision(evaluation.getIsFinalDecision())
                            .evaluationCriteria(buildFinalDefenseCriteriaDetail(evaluation))
                            .build();
                })
                .filter(detail -> detail != null)
                .toList();


        boolean hasConsensus = studentModality.getAcademicDistinction() != null &&
                              (studentModality.getAcademicDistinction().name().startsWith("AGREED_"));

        boolean wasTiebreaker = studentModality.getAcademicDistinction() != null &&
                               (studentModality.getAcademicDistinction().name().startsWith("TIEBREAKER_"));

        return ResponseEntity.ok(
                FinalDefenseResponse.builder()
                        .studentModalityId(studentModality.getId())
                        .studentName(student.getName() + " " + student.getLastName())
                        .studentEmail(student.getEmail())
                        .finalStatus(finalStatus)
                        .approved(finalStatus == ModalityProcessStatus.GRADED_APPROVED)
                        .academicDistinction(studentModality.getAcademicDistinction())
                        .finalGrade(studentModality.getFinalGrade())
                        .observations(history.getObservations())
                        .evaluationDate(history.getChangeDate())
                        .evaluatedBy(
                                history.getResponsible() != null
                                        ? history.getResponsible().getName()
                                        : "Comité de currículo de programa"
                        )
                        .hasConsensus(hasConsensus)
                        .wasTiebreaker(wasTiebreaker)
                        .examinerEvaluations(examinerEvaluations)
                        .build()
        );
    }

    // =========================================================================
    // GESTIÓN DE DISTINCIONES HONORÍFICAS PROPUESTAS POR JURADOS
    // =========================================================================

    /**
     * Lista las modalidades en las que los jurados han propuesto unánimemente
     * una distinción honorífica (Meritoria o Laureada) y que están pendientes
     * de revisión y decisión por parte del Comité de Currículo.
     *
     * Solo el comité del programa académico correspondiente puede ver estas modalidades.
     */
    public ResponseEntity<?> getPendingDistinctionProposals() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User committeeMember = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        List<Long> programIds = programAuthorityRepository
                .findByUser_Id(committeeMember.getId())
                .stream()
                .filter(pa -> pa.getRole() == ProgramRole.PROGRAM_CURRICULUM_COMMITTEE)
                .map(pa -> pa.getAcademicProgram().getId())
                .toList();

        if (programIds.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "success", false,
                    "message", "El usuario no tiene el rol de Comité de Currículo en ningún programa académico."
            ));
        }

        // Buscar modalidades con estado PENDING_DISTINCTION_COMMITTEE_REVIEW en los programas del comité
        List<StudentModality> pendingModalities = studentModalityRepository
                .findByStatusAndProgramDegreeModality_AcademicProgram_IdIn(
                        ModalityProcessStatus.PENDING_DISTINCTION_COMMITTEE_REVIEW,
                        programIds
                );

        List<Map<String, Object>> result = pendingModalities.stream()
                .sorted(Comparator.comparing(StudentModality::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(sm -> {
                    User leader = sm.getLeader();
                    StudentProfile leaderProfile = studentProfileRepository.findByUserId(leader.getId()).orElse(null);

                    // Obtener las evaluaciones de los jurados para ver los argumentos
                    List<DefenseExaminer> examiners = defenseExaminerRepository.findByStudentModalityId(sm.getId());
                    List<Map<String, Object>> examinerDetails = examiners.stream()
                            .map(de -> {
                                DefenseEvaluationCriteria eval = defenseEvaluationCriteriaRepository
                                        .findByDefenseExaminerId(de.getId())
                                        .orElse(null);
                                Map<String, Object> examinerMap = new LinkedHashMap<>();
                                examinerMap.put("examinerId", de.getExaminer().getId());
                                examinerMap.put("examinerName", de.getExaminer().getName() + " " + de.getExaminer().getLastName());
                                examinerMap.put("examinerType", de.getExaminerType() != null ? de.getExaminerType().name() : null);
                                examinerMap.put("proposedMention", eval != null ? (eval.getProposedMention() != null ? eval.getProposedMention().name() : "NONE") : null);
                                examinerMap.put("grade", eval != null ? eval.getGrade() : null);
                                examinerMap.put("observations", eval != null ? eval.getObservations() : null);
                                return examinerMap;
                            })
                            .toList();

                    // Traducir la distinción propuesta
                    String proposedDistinctionLabel = translateProposedDistinction(sm.getAcademicDistinction());

                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("studentModalityId", sm.getId());
                    row.put("studentId", leader.getId());
                    row.put("studentName", leader.getName() + " " + leader.getLastName());
                    row.put("studentEmail", leader.getEmail());
                    row.put("studentCode", leaderProfile != null ? leaderProfile.getStudentCode() : null);
                    row.put("modalityName", sm.getProgramDegreeModality().getDegreeModality().getName());
                    row.put("academicProgram", sm.getAcademicProgram().getName());
                    row.put("finalGrade", sm.getFinalGrade());
                    row.put("currentStatus", sm.getStatus().name());
                    row.put("proposedDistinction", sm.getAcademicDistinction() != null ? sm.getAcademicDistinction().name() : null);
                    row.put("proposedDistinctionLabel", proposedDistinctionLabel);
                    row.put("lastUpdatedAt", sm.getUpdatedAt());
                    row.put("examinerEvaluations", examinerDetails);
                    row.put("projectDirector", sm.getProjectDirector() != null
                            ? sm.getProjectDirector().getName() + " " + sm.getProjectDirector().getLastName()
                            : null);
                    return row;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "totalPending", result.size(),
                "pendingDistinctionProposals", result
        ));
    }

    /**
     * El Comité de Currículo ACEPTA la distinción honorífica propuesta por los jurados.
     * La modalidad pasa a GRADED_APPROVED con la distinción confirmada.
     *
     * @param studentModalityId ID de la modalidad
     * @param notes             Notas/observaciones del comité al aceptar (opcional)
     */
    @Transactional
    public ResponseEntity<?> acceptDistinctionProposal(Long studentModalityId, String notes) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User committeeMember = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        StudentModality studentModality = studentModalityRepository.findById(studentModalityId)
                .orElseThrow(() -> new RuntimeException("Modalidad no encontrada"));

        Long academicProgramId = studentModality.getProgramDegreeModality().getAcademicProgram().getId();

        boolean authorized = programAuthorityRepository.existsByUser_IdAndAcademicProgram_IdAndRole(
                committeeMember.getId(), academicProgramId, ProgramRole.PROGRAM_CURRICULUM_COMMITTEE);

        if (!authorized) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "success", false,
                    "message", "No tiene permiso para revisar distinciones en este programa académico."
            ));
        }

        if (studentModality.getStatus() != ModalityProcessStatus.PENDING_DISTINCTION_COMMITTEE_REVIEW) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "La modalidad no está en estado de revisión de distinción por el comité.",
                    "currentStatus", studentModality.getStatus()
            ));
        }

        // Convertir la distinción propuesta en la distinción definitiva aceptada
        AcademicDistinction proposedDistinction = studentModality.getAcademicDistinction();
        AcademicDistinction confirmedDistinction = resolveAcceptedDistinction(proposedDistinction);

        if (confirmedDistinction == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "No se puede determinar la distinción a confirmar. Estado de distinción inválido: " + proposedDistinction
            ));
        }

        studentModality.setStatus(ModalityProcessStatus.GRADED_APPROVED);
        studentModality.setAcademicDistinction(confirmedDistinction);
        studentModality.setUpdatedAt(LocalDateTime.now());
        studentModalityRepository.save(studentModality);

        String observations = String.format(
                "El Comité de Currículo ACEPTÓ la distinción honorífica propuesta por los jurados. " +
                "Distinción propuesta: %s → Distinción confirmada: %s. %s",
                translateAcademicDistinction(proposedDistinction),
                translateAcademicDistinction(confirmedDistinction),
                notes != null && !notes.isBlank() ? "Observaciones del comité: " + notes : ""
        );

        historyRepository.save(ModalityProcessStatusHistory.builder()
                .studentModality(studentModality)
                .status(ModalityProcessStatus.GRADED_APPROVED)
                .changeDate(LocalDateTime.now())
                .responsible(committeeMember)
                .observations(observations)
                .build());

        // Notificar resultado final definitivo
        notificationEventPublisher.publish(new FinalDefenseResultEvent(
                studentModality.getId(),
                ModalityProcessStatus.GRADED_APPROVED,
                confirmedDistinction,
                observations,
                committeeMember.getId()
        ));

        return ResponseEntity.ok(Map.of(
                "success", true,
                "studentModalityId", studentModalityId,
                "newStatus", ModalityProcessStatus.GRADED_APPROVED,
                "confirmedDistinction", confirmedDistinction,
                "message", "Distinción honorífica aceptada correctamente. La modalidad queda APROBADA con distinción " +
                        translateProposedDistinction(confirmedDistinction) + "."
        ));
    }

    /**
     * El Comité de Currículo RECHAZA la distinción honorífica propuesta por los jurados.
     * La modalidad pasa a GRADED_APPROVED sin distinción especial (AGREED_APPROVED o TIEBREAKER_APPROVED).
     *
     * @param studentModalityId ID de la modalidad
     * @param reason            Razón del rechazo (obligatorio)
     */
    @Transactional
    public ResponseEntity<?> rejectDistinctionProposal(Long studentModalityId, String reason) {
        if (reason == null || reason.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Debe proporcionar una razón para rechazar la distinción propuesta."
            ));
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User committeeMember = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        StudentModality studentModality = studentModalityRepository.findById(studentModalityId)
                .orElseThrow(() -> new RuntimeException("Modalidad no encontrada"));

        Long academicProgramId = studentModality.getProgramDegreeModality().getAcademicProgram().getId();

        boolean authorized = programAuthorityRepository.existsByUser_IdAndAcademicProgram_IdAndRole(
                committeeMember.getId(), academicProgramId, ProgramRole.PROGRAM_CURRICULUM_COMMITTEE);

        if (!authorized) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "success", false,
                    "message", "No tiene permiso para revisar distinciones en este programa académico."
            ));
        }

        if (studentModality.getStatus() != ModalityProcessStatus.PENDING_DISTINCTION_COMMITTEE_REVIEW) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "La modalidad no está en estado de revisión de distinción por el comité.",
                    "currentStatus", studentModality.getStatus()
            ));
        }

        // Al rechazar, la distinción se convierte en aprobada sin mención especial
        AcademicDistinction proposedDistinction = studentModality.getAcademicDistinction();
        AcademicDistinction rejectedDistinction = resolveRejectedDistinction(proposedDistinction);

        studentModality.setStatus(ModalityProcessStatus.GRADED_APPROVED);
        studentModality.setAcademicDistinction(rejectedDistinction);
        studentModality.setUpdatedAt(LocalDateTime.now());
        studentModalityRepository.save(studentModality);

        String observations = String.format(
                "El Comité de Currículo RECHAZÓ la distinción honorífica propuesta por los jurados. " +
                "Distinción propuesta: %s → Distinción final: %s (sin mención especial). " +
                "Razón del rechazo: %s",
                translateAcademicDistinction(proposedDistinction),
                translateAcademicDistinction(rejectedDistinction),
                reason
        );

        historyRepository.save(ModalityProcessStatusHistory.builder()
                .studentModality(studentModality)
                .status(ModalityProcessStatus.GRADED_APPROVED)
                .changeDate(LocalDateTime.now())
                .responsible(committeeMember)
                .observations(observations)
                .build());

        // Notificar resultado final definitivo sin mención
        notificationEventPublisher.publish(new FinalDefenseResultEvent(
                studentModality.getId(),
                ModalityProcessStatus.GRADED_APPROVED,
                rejectedDistinction,
                observations,
                committeeMember.getId()
        ));

        return ResponseEntity.ok(Map.of(
                "success", true,
                "studentModalityId", studentModalityId,
                "newStatus", ModalityProcessStatus.GRADED_APPROVED,
                "finalDistinction", rejectedDistinction,
                "reason", reason,
                "message", "Distinción honorífica rechazada. La modalidad queda APROBADA sin distinción especial."
        ));
    }

    /**
     * Resuelve cuál es la distinción definitiva al ACEPTAR la propuesta de los jurados.
     */
    private AcademicDistinction resolveAcceptedDistinction(AcademicDistinction proposed) {
        if (proposed == null) return null;
        return switch (proposed) {
            case PENDING_COMMITTEE_MERITORIOUS -> AcademicDistinction.AGREED_MERITORIOUS;
            case PENDING_COMMITTEE_LAUREATE -> AcademicDistinction.AGREED_LAUREATE;
            case TIEBREAKER_PENDING_COMMITTEE_MERITORIOUS -> AcademicDistinction.TIEBREAKER_MERITORIOUS;
            case TIEBREAKER_PENDING_COMMITTEE_LAUREATE -> AcademicDistinction.TIEBREAKER_LAUREATE;
            default -> null;
        };
    }

    /**
     * Resuelve cuál es la distinción definitiva al RECHAZAR la propuesta de los jurados.
     * La modalidad queda aprobada sin mención especial.
     */
    private AcademicDistinction resolveRejectedDistinction(AcademicDistinction proposed) {
        if (proposed == null) return AcademicDistinction.AGREED_APPROVED;
        return switch (proposed) {
            case TIEBREAKER_PENDING_COMMITTEE_MERITORIOUS, TIEBREAKER_PENDING_COMMITTEE_LAUREATE ->
                    AcademicDistinction.TIEBREAKER_APPROVED;
            default -> AcademicDistinction.AGREED_APPROVED;
        };
    }

    /**
     * Traduce el nombre interno de la distinción a una etiqueta legible en español.
     */
    private String translateProposedDistinction(AcademicDistinction distinction) {
        if (distinction == null) return "Sin distinción";
        return switch (distinction) {
            case PENDING_COMMITTEE_MERITORIOUS, AGREED_MERITORIOUS -> "Meritoria";
            case PENDING_COMMITTEE_LAUREATE, AGREED_LAUREATE -> "Laureada";
            case TIEBREAKER_PENDING_COMMITTEE_MERITORIOUS, TIEBREAKER_MERITORIOUS -> "Meritoria (desempate)";
            case TIEBREAKER_PENDING_COMMITTEE_LAUREATE, TIEBREAKER_LAUREATE -> "Laureada (desempate)";
            case AGREED_APPROVED -> "Aprobada";
            case TIEBREAKER_APPROVED -> "Aprobada (desempate)";
            case AGREED_REJECTED, TIEBREAKER_REJECTED -> "Reprobada";
            case REJECTED_BY_COMMITTEE -> "Rechazada por el comité";
            default -> distinction.name();
        };
    }

    private String translateAcademicDistinction(AcademicDistinction distinction) {
        if (distinction == null) return "Sin distinción";
        return switch (distinction) {
            case NO_DISTINCTION -> "Sin distinción";
            case AGREED_APPROVED -> "Aprobado por consenso";
            case AGREED_MERITORIOUS -> "Mención Meritoria";
            case AGREED_LAUREATE -> "Mención Laureada";
            case AGREED_REJECTED -> "Reprobado por consenso";
            case DISAGREEMENT_PENDING_TIEBREAKER -> "Desacuerdo – Pendiente de desempate";
            case TIEBREAKER_APPROVED -> "Aprobado por desempate";
            case TIEBREAKER_MERITORIOUS -> "Mención Meritoria (desempate)";
            case TIEBREAKER_LAUREATE -> "Mención Laureada (desempate)";
            case TIEBREAKER_REJECTED -> "Reprobado por desempate";
            case REJECTED_BY_COMMITTEE -> "Rechazado por el comité";
            case PENDING_COMMITTEE_MERITORIOUS -> "Mención Meritoria propuesta (pendiente del comité)";
            case PENDING_COMMITTEE_LAUREATE -> "Mención Laureada propuesta (pendiente del comité)";
            case TIEBREAKER_PENDING_COMMITTEE_MERITORIOUS -> "Mención Meritoria por desempate (pendiente del comité)";
            case TIEBREAKER_PENDING_COMMITTEE_LAUREATE -> "Mención Laureada por desempate (pendiente del comité)";
        };
    }

    public List<ProjectDirectorResponse> getProjectDirectors() {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User currentUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));


        List<ProgramAuthority> committeeAuthorities = programAuthorityRepository
                .findByUser_IdAndRole(currentUser.getId(), ProgramRole.PROGRAM_CURRICULUM_COMMITTEE);

        if (committeeAuthorities.isEmpty()) {
            throw new RuntimeException("El usuario no tiene el rol de PROGRAM_CURRICULUM_COMMITTEE");
        }


        Set<Long> userProgramIds = committeeAuthorities.stream()
                .map(authority -> authority.getAcademicProgram().getId())
                .collect(Collectors.toSet());


        List<com.SIGMA.USCO.Users.Entity.ProgramAuthority> projectDirectorAuthorities = programAuthorityRepository
                .findByAcademicProgram_IdAndRole(userProgramIds.iterator().next(),
                        ProgramRole.PROJECT_DIRECTOR
                );


        return projectDirectorAuthorities.stream()
                .map(authority -> new ProjectDirectorResponse(
                        authority.getUser().getId(),
                        authority.getUser().getName(),
                        authority.getUser().getLastName(),
                        authority.getUser().getEmail()
                ))
                .distinct()
                .collect(Collectors.toList());
    }

    public List<ProjectDirectorResponse> getProgramHeads() {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User currentUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));


        List<ProgramAuthority> committeeAuthorities = programAuthorityRepository
                .findByUser_IdAndRole(currentUser.getId(), ProgramRole.PROGRAM_CURRICULUM_COMMITTEE);

        if (committeeAuthorities.isEmpty()) {
            throw new RuntimeException("El usuario no tiene el rol de PROGRAM_CURRICULUM_COMMITTEE");
        }


        Set<Long> userProgramIds = committeeAuthorities.stream()
                .map(authority -> authority.getAcademicProgram().getId())
                .collect(Collectors.toSet());


        List<com.SIGMA.USCO.Users.Entity.ProgramAuthority> programHeadAuthorities = programAuthorityRepository
                .findByAcademicProgram_IdAndRole(userProgramIds.iterator().next(),
                        ProgramRole.PROGRAM_HEAD
                );


        return programHeadAuthorities.stream()
                .map(authority -> new ProjectDirectorResponse(
                        authority.getUser().getId(),
                        authority.getUser().getName(),
                        authority.getUser().getLastName(),
                        authority.getUser().getEmail()
                ))
                .distinct()
                .collect(Collectors.toList());
    }

    public List<ProjectDirectorResponse> getProgramCurriculumCommittee(Long academicProgramId, Long facultyId) {


        List<ProgramAuthority> committeeAuthorities = programAuthorityRepository.findAll()
                .stream()
                .filter(authority -> authority.getRole() == ProgramRole.PROGRAM_CURRICULUM_COMMITTEE)
                .toList();


        if (academicProgramId != null) {
            committeeAuthorities = committeeAuthorities.stream()
                    .filter(authority -> authority.getAcademicProgram().getId().equals(academicProgramId))
                    .toList();
        }

        if (facultyId != null) {
            committeeAuthorities = committeeAuthorities.stream()
                    .filter(authority -> authority.getAcademicProgram().getFaculty().getId().equals(facultyId))
                    .toList();
        }


        return committeeAuthorities.stream()
                .map(authority -> new ProjectDirectorResponse(
                        authority.getUser().getId(),
                        authority.getUser().getName(),
                        authority.getUser().getLastName(),
                        authority.getUser().getEmail()
                ))
                .distinct()
                .collect(Collectors.toList());
    }


    public List<ProjectDirectorResponse> getExaminers(Long academicProgramId, Long facultyId) {


        List<User> examiners = userRepository.findAll()
                .stream()
                .filter(user -> user.getRoles().stream()
                        .anyMatch(role -> role.getName().equals("EXAMINER")))
                .toList();


        if (academicProgramId != null || facultyId != null) {
            List<ProgramAuthority> examinerAuthorities = programAuthorityRepository.findAll()
                    .stream()
                    .filter(authority -> examiners.stream()
                            .anyMatch(examiner -> examiner.getId().equals(authority.getUser().getId())))
                    .toList();

            if (academicProgramId != null) {
                examinerAuthorities = examinerAuthorities.stream()
                        .filter(authority -> authority.getAcademicProgram().getId().equals(academicProgramId))
                        .toList();
            }

            if (facultyId != null) {
                examinerAuthorities = examinerAuthorities.stream()
                        .filter(authority -> authority.getAcademicProgram().getFaculty().getId().equals(facultyId))
                        .toList();
            }

            return examinerAuthorities.stream()
                    .map(authority -> new ProjectDirectorResponse(
                            authority.getUser().getId(),
                            authority.getUser().getName(),
                            authority.getUser().getLastName(),
                            authority.getUser().getEmail()
                    ))
                    .distinct()
                    .collect(Collectors.toList());
        }


        return examiners.stream()
                .map(examiner -> new ProjectDirectorResponse(
                        examiner.getId(),
                        examiner.getName(),
                        examiner.getLastName(),
                        examiner.getEmail()
                ))
                .distinct()
                .collect(Collectors.toList());
    }


    public List<ProjectDirectorResponse> getExaminersForCommittee() {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User currentUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));


        List<ProgramAuthority> committeeAuthorities = programAuthorityRepository
                .findByUser_IdAndRole(currentUser.getId(), ProgramRole.PROGRAM_CURRICULUM_COMMITTEE);

        if (committeeAuthorities.isEmpty()) {
            throw new RuntimeException("El usuario no tiene el rol de PROGRAM_CURRICULUM_COMMITTEE");
        }


        Set<Long> userProgramIds = committeeAuthorities.stream()
                .map(authority -> authority.getAcademicProgram().getId())
                .collect(Collectors.toSet());


        List<User> allExaminers = userRepository.findAll()
                .stream()
                .filter(user -> user.getRoles().stream()
                        .anyMatch(role -> role.getName().equals("EXAMINER")))
                .toList();


        List<ProgramAuthority> examinerAuthorities = new ArrayList<>();

        for (Long programId : userProgramIds) {
            List<ProgramAuthority> programExaminers = programAuthorityRepository.findAll()
                    .stream()
                    .filter(authority -> authority.getAcademicProgram().getId().equals(programId))
                    .filter(authority -> allExaminers.stream()
                            .anyMatch(examiner -> examiner.getId().equals(authority.getUser().getId())))
                    .toList();

            examinerAuthorities.addAll(programExaminers);
        }


        return examinerAuthorities.stream()
                .map(authority -> new ProjectDirectorResponse(
                        authority.getUser().getId(),
                        authority.getUser().getName(),
                        authority.getUser().getLastName(),
                        authority.getUser().getEmail()
                ))
                .distinct()
                .collect(Collectors.toList());
    }


    @Transactional
    public ResponseEntity<?> resubmitCorrectedDocument(Long studentModalityId, Long documentId, MultipartFile file) throws IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User student = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        StudentModality studentModality = studentModalityRepository.findById(studentModalityId)
                .orElseThrow(() -> new RuntimeException("Modalidad no encontrada"));

        // Validar que el usuario sea miembro activo de la modalidad
        boolean isActiveMember = studentModalityMemberRepository.isActiveMember(
                studentModalityId,
                student.getId()
        );

        if (!isActiveMember) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "success", false,
                            "message", "No tienes permiso para modificar esta modalidad"
                    ));
        }


        if (studentModality.getStatus() != ModalityProcessStatus.CORRECTIONS_REQUESTED_PROGRAM_HEAD &&
                studentModality.getStatus() != ModalityProcessStatus.CORRECTIONS_REQUESTED_PROGRAM_CURRICULUM_COMMITTEE) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "La modalidad no está en estado de correcciones solicitadas",
                    "currentStatus", studentModality.getStatus()
            ));
        }


        if (studentModality.getCorrectionDeadline() != null &&
                LocalDateTime.now().isAfter(studentModality.getCorrectionDeadline())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "El plazo de 30 días para entregar las correcciones ha vencido. La modalidad ha sido cancelada.",
                    "deadline", studentModality.getCorrectionDeadline()
            ));
        }


        StudentDocument document = studentDocumentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Documento no encontrado"));


        if (!document.getStudentModality().getId().equals(studentModalityId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "success", false,
                            "message", "El documento no pertenece a esta modalidad"
                    ));
        }


        if (document.getStatus() != DocumentStatus.CORRECTIONS_REQUESTED_BY_PROGRAM_HEAD &&
                document.getStatus() != DocumentStatus.CORRECTIONS_REQUESTED_BY_PROGRAM_CURRICULUM_COMMITTEE) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "El documento no está en estado de correcciones solicitadas",
                    "currentStatus", document.getStatus()
            ));
        }


        String originalFilename = file.getOriginalFilename();
        String finalFileName = UUID.randomUUID() + "_" + originalFilename;

        String modalityPath = document.getStudentModality()
                .getProgramDegreeModality()
                .getDegreeModality()
                .getName()
                .replaceAll("[^a-zA-Z0-9]", "_");

        String studentPath = student.getName() + student.getLastName() + "_" +
                student.getLastName() + "_" + studentModalityId;

        Path basePath = Paths.get(uploadDir, modalityPath, studentPath);
        Files.createDirectories(basePath);

        Path fullPath = basePath.resolve(finalFileName);
        Files.copy(file.getInputStream(), fullPath, StandardCopyOption.REPLACE_EXISTING);


        document.setFileName(originalFilename);
        document.setFilePath(fullPath.toString());
        document.setStatus(DocumentStatus.CORRECTION_RESUBMITTED);
        document.setUploadDate(LocalDateTime.now());
        studentDocumentRepository.save(document);


        studentModality.setStatus(ModalityProcessStatus.CORRECTIONS_SUBMITTED);
        studentModality.setUpdatedAt(LocalDateTime.now());
        studentModalityRepository.save(studentModality);


        documentHistoryRepository.save(
                StudentDocumentStatusHistory.builder()
                        .studentDocument(document)
                        .status(DocumentStatus.CORRECTION_RESUBMITTED)
                        .changeDate(LocalDateTime.now())
                        .responsible(student)
                        .observations("Documento corregido reenviado por el estudiante dentro del plazo establecido")
                        .build()
        );


        historyRepository.save(
                ModalityProcessStatusHistory.builder()
                        .studentModality(studentModality)
                        .status(ModalityProcessStatus.CORRECTIONS_SUBMITTED)
                        .changeDate(LocalDateTime.now())
                        .responsible(student)
                        .observations("Correcciones enviadas por el estudiante para revisión")
                        .build()
        );


        notificationEventPublisher.publish(
                new CorrectionResubmittedEvent(
                        studentModalityId,
                        documentId,
                        student.getId(),
                        document.getDocumentConfig().getDocumentName(),
                        student.getId()
                )
        );

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Documento corregido enviado exitosamente. Será revisado por el jurado correspondiente.",
                "documentId", documentId,
                "newStatus", document.getStatus()
        ));
    }

    @Transactional
    public ResponseEntity<?> approveCorrectedDocument(Long documentId) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User reviewer = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        StudentDocument document = studentDocumentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Documento no encontrado"));

        StudentModality studentModality = document.getStudentModality();
        Long academicProgramId = studentModality.getAcademicProgram().getId();


        boolean authorized = false;
        ModalityProcessStatus newModalityStatus = null;
        DocumentStatus newDocumentStatus = null;

        ModalityProcessStatus currentStatus = studentModality.getStatus();
        boolean isCorrectionsSubmitted =
                currentStatus == ModalityProcessStatus.CORRECTIONS_SUBMITTED ||
                currentStatus == ModalityProcessStatus.CORRECTIONS_SUBMITTED_TO_PROGRAM_HEAD ||
                currentStatus == ModalityProcessStatus.CORRECTIONS_SUBMITTED_TO_COMMITTEE ||
                currentStatus == ModalityProcessStatus.CORRECTIONS_SUBMITTED_TO_EXAMINERS;

        if (isCorrectionsSubmitted) {

            if (document.getStatus() == DocumentStatus.CORRECTION_RESUBMITTED) {

                if (currentStatus == ModalityProcessStatus.CORRECTIONS_SUBMITTED_TO_PROGRAM_HEAD ||
                    currentStatus == ModalityProcessStatus.CORRECTIONS_SUBMITTED) {
                    // Verificar si fue solicitado por program head mediante historial
                    List<StudentDocumentStatusHistory> history =
                            documentHistoryRepository.findByStudentDocumentIdOrderByChangeDateDesc(documentId);
                    boolean wasRequestedByProgramHead = history.stream()
                            .anyMatch(h -> h.getStatus() == DocumentStatus.CORRECTIONS_REQUESTED_BY_PROGRAM_HEAD);

                    if (wasRequestedByProgramHead || currentStatus == ModalityProcessStatus.CORRECTIONS_SUBMITTED_TO_PROGRAM_HEAD) {
                        authorized = programAuthorityRepository.existsByUser_IdAndAcademicProgram_IdAndRole(
                                reviewer.getId(), academicProgramId, ProgramRole.PROGRAM_HEAD);
                        newModalityStatus = ModalityProcessStatus.CORRECTIONS_APPROVED;
                        newDocumentStatus = DocumentStatus.ACCEPTED_FOR_PROGRAM_HEAD_REVIEW;
                    } else {
                        authorized = programAuthorityRepository.existsByUser_IdAndAcademicProgram_IdAndRole(
                                reviewer.getId(), academicProgramId, ProgramRole.PROGRAM_CURRICULUM_COMMITTEE);
                        newModalityStatus = ModalityProcessStatus.CORRECTIONS_APPROVED;
                        newDocumentStatus = DocumentStatus.ACCEPTED_FOR_PROGRAM_CURRICULUM_COMMITTEE_REVIEW;
                    }
                } else if (currentStatus == ModalityProcessStatus.CORRECTIONS_SUBMITTED_TO_COMMITTEE) {
                    authorized = programAuthorityRepository.existsByUser_IdAndAcademicProgram_IdAndRole(
                            reviewer.getId(), academicProgramId, ProgramRole.PROGRAM_CURRICULUM_COMMITTEE);
                    newModalityStatus = ModalityProcessStatus.CORRECTIONS_APPROVED;
                    newDocumentStatus = DocumentStatus.ACCEPTED_FOR_PROGRAM_CURRICULUM_COMMITTEE_REVIEW;
                } else if (currentStatus == ModalityProcessStatus.CORRECTIONS_SUBMITTED_TO_EXAMINERS) {
                    // Verificar que el revisor es un jurado asignado a esta modalidad
                    authorized = defenseExaminerRepository
                            .findByStudentModalityIdAndExaminerId(studentModality.getId(), reviewer.getId())
                            .isPresent();
                    newModalityStatus = ModalityProcessStatus.CORRECTIONS_APPROVED;
                    newDocumentStatus = DocumentStatus.ACCEPTED_FOR_EXAMINER_REVIEW;
                }
            }
        }

        if (!authorized) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "success", false,
                            "message", "No tienes permiso para aprobar este documento"
                    ));
        }


        document.setStatus(newDocumentStatus);
        document.setUploadDate(LocalDateTime.now());
        studentDocumentRepository.save(document);


        studentModality.setCorrectionRequestDate(null);
        studentModality.setCorrectionDeadline(null);
        studentModality.setCorrectionReminderSent(null);


        if (newDocumentStatus == DocumentStatus.ACCEPTED_FOR_PROGRAM_HEAD_REVIEW) {
            studentModality.setStatus(ModalityProcessStatus.UNDER_REVIEW_PROGRAM_HEAD);
        } else {
            studentModality.setStatus(ModalityProcessStatus.UNDER_REVIEW_PROGRAM_CURRICULUM_COMMITTEE);
        }

        studentModality.setUpdatedAt(LocalDateTime.now());
        studentModalityRepository.save(studentModality);


        documentHistoryRepository.save(
                StudentDocumentStatusHistory.builder()
                        .studentDocument(document)
                        .status(newDocumentStatus)
                        .changeDate(LocalDateTime.now())
                        .responsible(reviewer)
                        .observations("Correcciones aprobadas. El documento cumple con los requisitos.")
                        .build()
        );

        historyRepository.save(
                ModalityProcessStatusHistory.builder()
                        .studentModality(studentModality)
                        .status(studentModality.getStatus())
                        .changeDate(LocalDateTime.now())
                        .responsible(reviewer)
                        .observations("Correcciones aprobadas. Continúa el proceso de revisión.")
                        .build()
        );


        notificationEventPublisher.publish(
                new CorrectionApprovedEvent(
                        studentModality.getId(),
                        documentId,
                        studentModality.getLeader().getId(),
                        document.getDocumentConfig().getDocumentName(),
                        reviewer.getId()
                )
        );

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Correcciones aprobadas exitosamente. La modalidad continúa su proceso normal.",
                "documentId", documentId,
                "newDocumentStatus", newDocumentStatus,
                "newModalityStatus", studentModality.getStatus()
        ));
    }

    @Transactional
    public ResponseEntity<?> rejectCorrectedDocumentFinal(Long documentId, String reason) {

        if (reason == null || reason.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Debe proporcionar el motivo del rechazo definitivo"
            ));
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User reviewer = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        StudentDocument document = studentDocumentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Documento no encontrado"));

        StudentModality studentModality = document.getStudentModality();
        Long academicProgramId = studentModality.getAcademicProgram().getId();


        boolean authorized = false;
        ModalityProcessStatus currentStatus = studentModality.getStatus();
        boolean isCorrectionsSubmitted =
                currentStatus == ModalityProcessStatus.CORRECTIONS_SUBMITTED ||
                currentStatus == ModalityProcessStatus.CORRECTIONS_SUBMITTED_TO_PROGRAM_HEAD ||
                currentStatus == ModalityProcessStatus.CORRECTIONS_SUBMITTED_TO_COMMITTEE ||
                currentStatus == ModalityProcessStatus.CORRECTIONS_SUBMITTED_TO_EXAMINERS;

        if (isCorrectionsSubmitted) {
            if (currentStatus == ModalityProcessStatus.CORRECTIONS_SUBMITTED_TO_PROGRAM_HEAD) {
                authorized = programAuthorityRepository.existsByUser_IdAndAcademicProgram_IdAndRole(
                        reviewer.getId(), academicProgramId, ProgramRole.PROGRAM_HEAD);
            } else if (currentStatus == ModalityProcessStatus.CORRECTIONS_SUBMITTED_TO_COMMITTEE) {
                authorized = programAuthorityRepository.existsByUser_IdAndAcademicProgram_IdAndRole(
                        reviewer.getId(), academicProgramId, ProgramRole.PROGRAM_CURRICULUM_COMMITTEE);
            } else if (currentStatus == ModalityProcessStatus.CORRECTIONS_SUBMITTED_TO_EXAMINERS) {
                authorized = defenseExaminerRepository
                        .findByStudentModalityIdAndExaminerId(studentModality.getId(), reviewer.getId())
                        .isPresent();
            } else {
                // CORRECTIONS_SUBMITTED genérico: verificar por historial
                List<StudentDocumentStatusHistory> history =
                        documentHistoryRepository.findByStudentDocumentIdOrderByChangeDateDesc(documentId);
                boolean wasRequestedByProgramHead = history.stream()
                        .anyMatch(h -> h.getStatus() == DocumentStatus.CORRECTIONS_REQUESTED_BY_PROGRAM_HEAD);
                if (wasRequestedByProgramHead) {
                    authorized = programAuthorityRepository.existsByUser_IdAndAcademicProgram_IdAndRole(
                            reviewer.getId(), academicProgramId, ProgramRole.PROGRAM_HEAD);
                } else {
                    authorized = programAuthorityRepository.existsByUser_IdAndAcademicProgram_IdAndRole(
                            reviewer.getId(), academicProgramId, ProgramRole.PROGRAM_CURRICULUM_COMMITTEE);
                }
            }
        }

        if (!authorized) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "success", false,
                            "message", "No tienes permiso para rechazar este documento"
                    ));
        }


        document.setStatus(DocumentStatus.REJECTED_FOR_PROGRAM_CURRICULUM_COMMITTEE_REVIEW);
        document.setNotes(reason);
        document.setUploadDate(LocalDateTime.now());
        studentDocumentRepository.save(document);


        studentModality.setStatus(ModalityProcessStatus.CORRECTIONS_REJECTED_FINAL);
        studentModality.setUpdatedAt(LocalDateTime.now());
        studentModalityRepository.save(studentModality);


        documentHistoryRepository.save(
                StudentDocumentStatusHistory.builder()
                        .studentDocument(document)
                        .status(DocumentStatus.REJECTED_FOR_PROGRAM_CURRICULUM_COMMITTEE_REVIEW)
                        .changeDate(LocalDateTime.now())
                        .responsible(reviewer)
                        .observations("Rechazo definitivo: " + reason)
                        .build()
        );

        historyRepository.save(
                ModalityProcessStatusHistory.builder()
                        .studentModality(studentModality)
                        .status(ModalityProcessStatus.CORRECTIONS_REJECTED_FINAL)
                        .changeDate(LocalDateTime.now())
                        .responsible(reviewer)
                        .observations("Modalidad cancelada por rechazo definitivo de correcciones. Motivo: " + reason)
                        .build()
        );


        notificationEventPublisher.publish(
                new CorrectionRejectedFinalEvent(
                        studentModality.getId(),
                        documentId,
                        studentModality.getLeader().getId(),
                        document.getDocumentConfig().getDocumentName(),
                        reason,
                        reviewer.getId()
                )
        );

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Correcciones rechazadas definitivamente. La modalidad ha sido cancelada.",
                "documentId", documentId,
                "finalStatus", ModalityProcessStatus.CORRECTIONS_REJECTED_FINAL
        ));
    }


    public ResponseEntity<?> getCorrectionDeadlineStatus(Long studentModalityId) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        StudentModality studentModality = studentModalityRepository.findById(studentModalityId)
                .orElseThrow(() -> new RuntimeException("Modalidad no encontrada"));

        // Validar que el usuario sea miembro activo de la modalidad o un revisor autorizado
        boolean isStudent = studentModalityMemberRepository.isActiveMember(
                studentModalityId,
                user.getId()
        );
        boolean isAuthorizedReviewer = programAuthorityRepository.existsByUser_IdAndAcademicProgram_Id(
                user.getId(),
                studentModality.getAcademicProgram().getId()
        );

        if (!isStudent && !isAuthorizedReviewer) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "success", false,
                            "message", "No tienes permiso para consultar esta información"
                    ));
        }


        if (studentModality.getStatus() != ModalityProcessStatus.CORRECTIONS_REQUESTED_PROGRAM_HEAD &&
                studentModality.getStatus() != ModalityProcessStatus.CORRECTIONS_REQUESTED_PROGRAM_CURRICULUM_COMMITTEE) {
            return ResponseEntity.ok(Map.of(
                    "hasCorrectionRequest", false,
                    "currentStatus", studentModality.getStatus(),
                    "message", "No hay correcciones solicitadas actualmente"
            ));
        }


        LocalDateTime now = LocalDateTime.now();
        long daysRemaining = 0;
        boolean isExpired = false;

        if (studentModality.getCorrectionDeadline() != null) {
            daysRemaining = ChronoUnit.DAYS.between(now, studentModality.getCorrectionDeadline());
            isExpired = daysRemaining < 0;
        }

        return ResponseEntity.ok(Map.of(
                "hasCorrectionRequest", true,
                "currentStatus", studentModality.getStatus(),
                "correctionRequestDate", studentModality.getCorrectionRequestDate(),
                "correctionDeadline", studentModality.getCorrectionDeadline(),
                "daysRemaining", Math.max(0, daysRemaining),
                "isExpired", isExpired,
                "reminderSent", studentModality.getCorrectionReminderSent() != null ? studentModality.getCorrectionReminderSent() : false
        ));
    }


    @Transactional
    public ResponseEntity<?> closeModalityByCommittee(Long studentModalityId, String reason) {


        if (reason == null || reason.isBlank()) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", "Debe proporcionar el motivo del cierre de la modalidad"
                    )
            );
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User committeeMember = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        StudentModality studentModality = studentModalityRepository.findById(studentModalityId)
                .orElseThrow(() -> new RuntimeException("Modalidad no encontrada"));


        Long academicProgramId = studentModality.getAcademicProgram().getId();

        boolean isAuthorized = programAuthorityRepository
                .existsByUser_IdAndAcademicProgram_IdAndRole(
                        committeeMember.getId(),
                        academicProgramId,
                        ProgramRole.PROGRAM_CURRICULUM_COMMITTEE
                );

        if (!isAuthorized) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "success", false,
                            "message", "No tiene permiso para cerrar modalidades de este programa académico. Debe ser miembro del comité de currículo del programa."
                    ));
        }


        if (studentModality.getStatus() == ModalityProcessStatus.MODALITY_CLOSED) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", "La modalidad ya se encuentra cerrada",
                            "currentStatus", studentModality.getStatus()
                    )
            );
        }


        ModalityProcessStatus previousStatus = studentModality.getStatus();


        studentModality.setStatus(ModalityProcessStatus.MODALITY_CLOSED);
        studentModality.setUpdatedAt(LocalDateTime.now());
        studentModalityRepository.save(studentModality);


        historyRepository.save(
                ModalityProcessStatusHistory.builder()
                        .studentModality(studentModality)
                        .status(ModalityProcessStatus.MODALITY_CLOSED)
                        .changeDate(LocalDateTime.now())
                        .responsible(committeeMember)
                        .observations(String.format(
                                "Modalidad cerrada por el comité de currículo del programa.  Motivo: %s",
                                previousStatus,
                                reason
                        ))
                        .build()
        );


        notificationEventPublisher.publish(
                new ModalityClosedByCommitteeEvent(
                        studentModality.getId(),
                        studentModality.getLeader().getId(),
                        reason,
                        committeeMember.getId()
                )
        );

        return ResponseEntity.ok(
                Map.of(
                        "success", true,
                        "studentModalityId", studentModalityId,
                        "previousStatus", previousStatus,
                        "newStatus", ModalityProcessStatus.MODALITY_CLOSED,
                        "closedBy", committeeMember.getName() + " " + committeeMember.getLastName(),
                        "reason", reason,
                        "message", "Modalidad cerrada exitosamente. El estudiante ha sido notificado por correo electrónico."
                )
        );
    }


    @Transactional
    public ResponseEntity<?> approveFinalModalityByCommittee(Long studentModalityId, String observations) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User committeeMember = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        StudentModality studentModality = studentModalityRepository.findById(studentModalityId)
                .orElseThrow(() -> new RuntimeException("Modalidad no encontrada"));


        Long academicProgramId = studentModality.getAcademicProgram().getId();

        boolean isAuthorized = programAuthorityRepository
                .existsByUser_IdAndAcademicProgram_IdAndRole(
                        committeeMember.getId(),
                        academicProgramId,
                        ProgramRole.PROGRAM_CURRICULUM_COMMITTEE
                );

        if (!isAuthorized) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "success", false,
                            "message", "No tiene permiso para aprobar modalidades de este programa académico. Debe ser miembro del comité de currículo del programa."
                    ));
        }


        Map<String, Object> documentValidation = validateAllRequiredDocumentsUploaded(studentModalityId);
        boolean allDocumentsUploaded = (boolean) documentValidation.get("allDocumentsUploaded");

        if (!allDocumentsUploaded) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", "No se puede aprobar la modalidad porque faltan documentos por subir",
                            "missingDocumentsCount", documentValidation.get("missingCount"),
                            "totalRequired", documentValidation.get("totalRequired"),
                            "totalUploaded", documentValidation.get("totalUploaded"),
                            "missingDocuments", documentValidation.get("missingDocuments")
                    )
            );
        }

        // Validar que TODOS los documentos MANDATORY y SECONDARY estén en estado ACCEPTED_FOR_PROGRAM_CURRICULUM_COMMITTEE_REVIEW
        Map<String, Object> acceptedValidation = validateAllDocumentsAcceptedForCommittee(studentModalityId);
        boolean allAccepted = (boolean) acceptedValidation.get("allAccepted");

        if (!allAccepted) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", "No se puede aprobar la modalidad. Todos los documentos iniciales y complementarios deben estar en estado 'ACEPTADO POR COMITÉ'. Revise los documentos del estudiante.",
                            "documentosNoAceptados", acceptedValidation.get("notAcceptedCount"),
                            "totalRequeridos", acceptedValidation.get("totalRequired"),
                            "documentosPendientes", acceptedValidation.get("notAcceptedDocuments")
                    )
            );
        }

        if (!(studentModality.getStatus() == ModalityProcessStatus.READY_FOR_DIRECTOR_ASSIGNMENT ||
              studentModality.getStatus() == ModalityProcessStatus.APPROVED_BY_PROGRAM_CURRICULUM_COMMITTEE)) {

            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", "La modalidad no está en estado válido para aprobación final por el comité",
                            "currentStatus", studentModality.getStatus()
                    )
            );
        }


        if (studentModality.getStatus() == ModalityProcessStatus.GRADED_APPROVED ||
                studentModality.getStatus() == ModalityProcessStatus.GRADED_FAILED) {

            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", "La modalidad ya ha sido calificada definitivamente",
                            "currentStatus", studentModality.getStatus()
                    )
            );
        }

        ModalityProcessStatus previousStatus = studentModality.getStatus();


        studentModality.setStatus(ModalityProcessStatus.GRADED_APPROVED);
        studentModality.setAcademicDistinction(AcademicDistinction.NO_DISTINCTION);
        studentModality.setFinalGrade(null);
        studentModality.setUpdatedAt(LocalDateTime.now());
        studentModalityRepository.save(studentModality);


        historyRepository.save(
                ModalityProcessStatusHistory.builder()
                        .studentModality(studentModality)
                        .status(ModalityProcessStatus.GRADED_APPROVED)
                        .changeDate(LocalDateTime.now())
                        .responsible(committeeMember)
                        .observations(String.format(
                                "Modalidad aprobada definitivamente por el comité de currículo del programa. " +
                                observations != null ? "Observaciones: " + observations : ""
                        ))
                        .build()
        );


        List<StudentModalityMember> activeMembers = studentModalityMemberRepository
                .findByStudentModalityIdAndStatus(studentModality.getId(), MemberStatus.ACTIVE);

        for (StudentModalityMember member : activeMembers) {
            notificationEventPublisher.publish(
                    new ModalityFinalApprovedByCommitteeEvent(
                            studentModality.getId(),
                            member.getStudent().getId(),
                            observations,
                            committeeMember.getId()
                    )
            );
        }

        return ResponseEntity.ok(
                Map.of(
                        "success", true,
                        "studentModalityId", studentModalityId,
                        "previousStatus", previousStatus,
                        "newStatus", ModalityProcessStatus.GRADED_APPROVED,
                        "academicDistinction", AcademicDistinction.NO_DISTINCTION,
                        "finalGrade", "N/A",
                        "approvedBy", committeeMember.getName() + " " + committeeMember.getLastName(),
                        "observations", observations != null ? observations : "Sin observaciones",
                        "message", "Modalidad aprobada definitivamente. Todos los estudiantes han sido notificados."
                )
        );
    }


    @Transactional
    public ResponseEntity<?> rejectFinalModalityByCommittee(Long studentModalityId, String reason) {


        if (reason == null || reason.isBlank()) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", "Debe proporcionar la razón del rechazo de la modalidad"
                    )
            );
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User committeeMember = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        StudentModality studentModality = studentModalityRepository.findById(studentModalityId)
                .orElseThrow(() -> new RuntimeException("Modalidad no encontrada"));


        Long academicProgramId = studentModality.getAcademicProgram().getId();

        boolean isAuthorized = programAuthorityRepository
                .existsByUser_IdAndAcademicProgram_IdAndRole(
                        committeeMember.getId(),
                        academicProgramId,
                        ProgramRole.PROGRAM_CURRICULUM_COMMITTEE
                );

        if (!isAuthorized) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "success", false,
                            "message", "No tiene permiso para rechazar modalidades de este programa académico. Debe ser miembro del comité de currículo del programa."
                    ));
        }

        // Validar que todos los documentos MANDATORY y SECONDARY estén subidos
        Map<String, Object> documentValidation = validateAllRequiredDocumentsUploaded(studentModalityId);
        boolean allDocumentsUploaded = (boolean) documentValidation.get("allDocumentsUploaded");

        if (!allDocumentsUploaded) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", "No se puede rechazar la modalidad porque faltan documentos por subir. " +
                                    "El estudiante debe completar la documentación antes de que el comité pueda tomar una decisión definitiva.",
                            "missingDocumentsCount", documentValidation.get("missingCount"),
                            "totalRequired", documentValidation.get("totalRequired"),
                            "totalUploaded", documentValidation.get("totalUploaded"),
                            "missingDocuments", documentValidation.get("missingDocuments")
                    )
            );
        }

        // Validar que TODOS los documentos MANDATORY y SECONDARY estén en estado ACCEPTED_FOR_PROGRAM_CURRICULUM_COMMITTEE_REVIEW
        Map<String, Object> acceptedValidation = validateAllDocumentsAcceptedForCommittee(studentModalityId);
        boolean allAccepted = (boolean) acceptedValidation.get("allAccepted");

        if (!allAccepted) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", "No se puede rechazar la modalidad. Todos los documentos obligatorios y complementarios deben estar en estado 'Aceptado para revisión del comité de currículo' (ACCEPTED_FOR_PROGRAM_CURRICULUM_COMMITTEE_REVIEW). Revise los documentos indicados.",
                            "documentosNoAceptados", acceptedValidation.get("notAcceptedCount"),
                            "totalRequeridos", acceptedValidation.get("totalRequired"),
                            "documentosPendientes", acceptedValidation.get("notAcceptedDocuments")
                    )
            );
        }

        if (!(studentModality.getStatus() == ModalityProcessStatus.READY_FOR_DIRECTOR_ASSIGNMENT ||
              studentModality.getStatus() == ModalityProcessStatus.APPROVED_BY_PROGRAM_CURRICULUM_COMMITTEE)) {

            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", "La modalidad no está en estado válido para rechazo por el comité",
                            "currentStatus", studentModality.getStatus(),
                            "validStates", "READY_FOR_DIRECTOR_ASSIGNMENT o PENDING_COMMITTEE_FINAL_DECISION"
                    )
            );
        }


        if (studentModality.getStatus() == ModalityProcessStatus.GRADED_APPROVED ||
                studentModality.getStatus() == ModalityProcessStatus.GRADED_FAILED) {

            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", "La modalidad ya ha sido calificada definitivamente",
                            "currentStatus", studentModality.getStatus()
                    )
            );
        }

        ModalityProcessStatus previousStatus = studentModality.getStatus();


        studentModality.setStatus(ModalityProcessStatus.GRADED_FAILED);
        studentModality.setFinalGrade(null);
        studentModality.setAcademicDistinction(AcademicDistinction.REJECTED_BY_COMMITTEE);
        studentModality.setUpdatedAt(LocalDateTime.now());
        studentModalityRepository.save(studentModality);


        historyRepository.save(
                ModalityProcessStatusHistory.builder()
                        .studentModality(studentModality)
                        .status(ModalityProcessStatus.GRADED_FAILED)
                        .changeDate(LocalDateTime.now())
                        .responsible(committeeMember)
                        .observations(String.format(
                                "Modalidad rechazada definitivamente por el comité de currículo del programa. " +
                                " Motivo: %s",
                                previousStatus,
                                reason
                        ))
                        .build()
        );


        List<StudentModalityMember> activeMembers = studentModalityMemberRepository
                .findByStudentModalityIdAndStatus(studentModality.getId(), MemberStatus.ACTIVE);

        for (StudentModalityMember member : activeMembers) {
            notificationEventPublisher.publish(
                    new ModalityRejectedByCommitteeEvent(
                            studentModality.getId(),
                            member.getStudent().getId(),
                            reason,
                            committeeMember.getId()
                    )
            );
        }

        return ResponseEntity.ok(
                Map.of(
                        "success", true,
                        "studentModalityId", studentModalityId,
                        "previousStatus", previousStatus,
                        "newStatus", ModalityProcessStatus.GRADED_FAILED,
                        "rejectedBy", committeeMember.getName() + " " + committeeMember.getLastName(),
                        "reason", reason,
                        "message", "Modalidad rechazada definitivamente. Todos los estudiantes han sido notificados."
                )
        );
    }




    @Transactional
    public ResponseEntity<?> createSeminar(SeminarDTO request) {
        try {

            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String email = authentication.getName();
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));


            ProgramAuthority programAuthority = programAuthorityRepository
                    .findByUser_IdAndRole(user.getId(), ProgramRole.PROGRAM_HEAD)
                    .stream()
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "El usuario no tiene el rol de jefe de programa (PROGRAM_HEAD)"
                    ));

            AcademicProgram academicProgram = programAuthority.getAcademicProgram();


            if (request.getMinParticipants() < 15) {
                throw new IllegalArgumentException(
                        "El número mínimo de participantes debe ser al menos 15 según el Artículo 43"
                );
            }

            if (request.getMaxParticipants() > 35) {
                throw new IllegalArgumentException(
                        "El número máximo de participantes no puede exceder 35 según el Artículo 43"
                );
            }

            if (request.getMinParticipants() > request.getMaxParticipants()) {
                throw new IllegalArgumentException(
                        "El número mínimo de participantes no puede ser mayor al máximo"
                );
            }

            if (request.getTotalHours() < 160) {
                throw new IllegalArgumentException(
                        "La intensidad horaria mínima debe ser de 160 horas según el Artículo 42"
                );
            }


            Seminar seminar = Seminar.builder()
                    .academicProgram(academicProgram)
                    .name(request.getName())
                    .description(request.getDescription())
                    .totalCost(request.getTotalCost())
                    .minParticipants(request.getMinParticipants())
                    .maxParticipants(request.getMaxParticipants())
                    .totalHours(request.getTotalHours())
                    .currentParticipants(0)
                    .active(true)
                    .status(SeminarStatus.OPEN)
                    .startDate(request.getStartDate())
                    .endDate(request.getEndDate())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            seminar = seminarRepository.save(seminar);

            return ResponseEntity.status(HttpStatus.CREATED).body(
                    Map.of(
                            "success", true,
                            "message", "Seminario creado exitosamente",
                            "seminarId", seminar.getId(),
                            "programName", academicProgram.getName(),
                            "seminarName", seminar.getName()
                    )
            );

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "error", e.getMessage()
                    )
            );
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    Map.of(
                            "success", false,
                            "error", "Error al crear el seminario: " + e.getMessage()
                    )
            );
        }
    }

    public ResponseEntity<?> listActiveSeminarsWithSeats() {
        try {

            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String email = authentication.getName();
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));


            StudentProfile studentProfile = studentProfileRepository.findById(user.getId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No se encontró el perfil de estudiante para este usuario"
                    ));

            AcademicProgram studentProgram = studentProfile.getAcademicProgram();

            List<Seminar> seminars = seminarRepository.findActiveWithAvailableSeatsByProgram(
                    studentProgram.getId()
            );


            List<SeminarResponseDTO> seminarDTOs = seminars.stream()
                    .map(seminar -> {
                        int availableSeats = seminar.getMaxParticipants() - seminar.getCurrentParticipants();

                        String statusDescription;
                        if (availableSeats > 0) {
                            statusDescription = "Cupos disponibles: " + availableSeats;
                        } else {
                            statusDescription = "Sin cupos disponibles";
                        }

                        return SeminarResponseDTO.builder()
                                .id(seminar.getId())
                                .name(seminar.getName())
                                .description(seminar.getDescription())
                                .totalCost(seminar.getTotalCost())
                                .minParticipants(seminar.getMinParticipants())
                                .maxParticipants(seminar.getMaxParticipants())
                                .currentParticipants(seminar.getCurrentParticipants())
                                .availableSpots(availableSeats)
                                .totalHours(seminar.getTotalHours())
                                .status(seminar.getStatus() != null ? seminar.getStatus().name() : null)
                                .statusDescription(statusDescription)
                                .academicProgramId(seminar.getAcademicProgram().getId())
                                .academicProgramName(seminar.getAcademicProgram().getName())
                                .facultyName(seminar.getAcademicProgram().getFaculty().getName())
                                .startDate(seminar.getStartDate())
                                .endDate(seminar.getEndDate())
                                .createdAt(seminar.getCreatedAt())
                                .updatedAt(seminar.getUpdatedAt())
                                .canEnroll(null)
                                .build();
                    })
                    .toList();

            return ResponseEntity.ok(
                    Map.of(
                            "success", true,
                            "seminars", seminarDTOs
                    )
            );

        } catch (IllegalArgumentException e) {
            log.error("Error de validación al listar seminarios: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "error", e.getMessage()
                    )
            );
        } catch (Exception e) {
            log.error("Error inesperado al listar seminarios: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    Map.of(
                            "success", false,
                            "error", "Error al listar los seminarios: " + e.getMessage()
                    )
            );
        }
    }

    @Transactional
    public ResponseEntity<?> enrollInSeminar(Long seminarId) {
        try {

            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String email = authentication.getName();
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));


            StudentProfile studentProfile = studentProfileRepository.findById(user.getId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No se encontró el perfil de estudiante para este usuario"
                    ));


            List<StudentModality> leaderModalities = studentModalityRepository
                    .findByLeaderId(studentProfile.getId());

            boolean hasSeminarioGradoModality = leaderModalities.stream()
                    .anyMatch(sm -> {
                        ProgramDegreeModality pdm = sm.getProgramDegreeModality();
                        String modalityName = pdm.getDegreeModality().getName();
                        ModalityProcessStatus status = sm.getStatus();

                        return modalityName.equalsIgnoreCase("SEMINARIO DE GRADO") &&
                               (status == ModalityProcessStatus.MODALITY_SELECTED ||
                               status == ModalityProcessStatus.UNDER_REVIEW_PROGRAM_HEAD);
                    });

            if (!hasSeminarioGradoModality) {
                throw new IllegalArgumentException(
                        "Para inscribirse en un seminario, debes tener iniciada la modalidad 'SEMINARIO DE GRADO'. " +
                        "Por favor, solicita primero esta modalidad de grado."
                );
            }

            Seminar seminar = seminarRepository.findById(seminarId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "El seminario con ID " + seminarId + " no existe"
                    ));


            if (!seminar.isActive()) {
                throw new IllegalArgumentException("El seminario no está activo");
            }


            if (seminar.getStatus() != SeminarStatus.OPEN) {
                throw new IllegalArgumentException(
                        "El seminario no está abierto para inscripciones. Estado actual: " + seminar.getStatus()
                );
            }


            if (!seminar.getAcademicProgram().getId().equals(studentProfile.getAcademicProgram().getId())) {
                throw new IllegalArgumentException(
                        "El seminario no pertenece a tu programa académico"
                );
            }


            boolean alreadyEnrolled = seminarRepository.isStudentEnrolled(seminarId, studentProfile.getId());
            if (alreadyEnrolled) {

                throw new IllegalArgumentException("Ya estás inscrito en este seminario");
            }


            if (seminar.getCurrentParticipants() >= seminar.getMaxParticipants()) {
                throw new IllegalArgumentException(
                        "No hay cupos disponibles. El seminario ha alcanzado el máximo de " +
                        seminar.getMaxParticipants() + " participantes"
                );
            }



            seminarRepository.enrollStudent(seminarId, studentProfile.getId());


            seminar.setCurrentParticipants(seminar.getCurrentParticipants() + 1);
            seminar.setUpdatedAt(LocalDateTime.now());
            seminarRepository.save(seminar);



            int availableSeats = seminar.getMaxParticipants() - seminar.getCurrentParticipants();

            return ResponseEntity.status(HttpStatus.CREATED).body(
                    Map.of(
                            "success", true,
                            "message", "Te has inscrito exitosamente en el seminario",
                            "seminarName", seminar.getName(),
                            "enrollmentDate", LocalDateTime.now(),
                            "currentParticipants", seminar.getCurrentParticipants(),
                            "maxParticipants", seminar.getMaxParticipants(),
                            "availableSeats", availableSeats
                    )
            );

        } catch (IllegalArgumentException e) {

            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "error", e.getMessage()
                    )
            );
        } catch (Exception e) {

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    Map.of(
                            "success", false,
                            "error", "Error al inscribirse en el seminario: " + e.getMessage()
                    )
            );
        }
    }


    public ResponseEntity<?> FgetSeminarDetailForProgramHead(Long seminarId) {
        try {

            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String email = authentication.getName();
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));


            Seminar seminar = seminarRepository.findById(seminarId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "El seminario con ID " + seminarId + " no existe"
                    ));


            Long userProgramId = programAuthorityRepository
                    .findByUser_IdAndRole(user.getId(), ProgramRole.PROGRAM_HEAD)
                    .stream()
                    .findFirst()
                    .map(pa -> pa.getAcademicProgram().getId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No tienes permisos de jefe de programa"
                    ));

            if (!seminar.getAcademicProgram().getId().equals(userProgramId)) {
                throw new IllegalArgumentException(
                        "Este seminario no pertenece a tu programa académico"
                );
            }


            List<StudentProfile> enrolledStudents = seminarRepository.findEnrolledStudentsBySeminarId(seminarId);


            List<SeminarDetailDTO.EnrolledStudentDTO> enrolledStudentDTOs = enrolledStudents.stream()
                    .map(studentProfile -> {
                        User student = userRepository.findById(studentProfile.getId())
                                .orElse(null);

                        if (student == null) {
                            return null;
                        }


                        List<StudentModality> modalities = studentModalityRepository
                                .findByLeaderId(studentProfile.getId());

                        StudentModality seminarioModality = modalities.stream()
                                .filter(sm -> {
                                    String modalityName = sm.getProgramDegreeModality()
                                            .getDegreeModality().getName();
                                    return modalityName.equalsIgnoreCase("SEMINARIO DE GRADO");
                                })
                                .findFirst()
                                .orElse(null);

                        SeminarDetailDTO.ModalityInfoDTO modalityInfo = null;
                        if (seminarioModality != null) {
                            modalityInfo = SeminarDetailDTO.ModalityInfoDTO.builder()
                                    .modalityId(seminarioModality.getId())
                                    .modalityName(seminarioModality.getProgramDegreeModality()
                                            .getDegreeModality().getName())
                                    .modalityType(seminarioModality.getModalityType().name())
                                    .status(seminarioModality.getStatus().name())
                                    .selectionDate(seminarioModality.getSelectionDate())
                                    .build();
                        }

                        return SeminarDetailDTO.EnrolledStudentDTO.builder()
                                .studentId(studentProfile.getId())
                                .studentCode(studentProfile.getStudentCode())
                                .name(student.getName())
                                .lastName(student.getLastName())
                                .email(student.getEmail())



                                .approvedCredits(studentProfile.getApprovedCredits() != null ? studentProfile.getApprovedCredits().intValue() : null)

                                .build();
                    })
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toList());

            int availableSeats = seminar.getMaxParticipants() - seminar.getCurrentParticipants();
            double fillPercentage = (seminar.getCurrentParticipants() * 100.0) / seminar.getMaxParticipants();
            boolean hasMinimumParticipants = seminar.getCurrentParticipants() >= seminar.getMinParticipants();

            SeminarDetailDTO detailDTO = SeminarDetailDTO.builder()
                    .id(seminar.getId())
                    .name(seminar.getName())
                    .description(seminar.getDescription())
                    .totalCost(seminar.getTotalCost())
                    .minParticipants(seminar.getMinParticipants())
                    .maxParticipants(seminar.getMaxParticipants())
                    .currentParticipants(seminar.getCurrentParticipants())
                    .totalHours(seminar.getTotalHours())
                    .active(seminar.isActive())
                    .status(seminar.getStatus() != null ? seminar.getStatus().name() : null)
                    .startDate(seminar.getStartDate())
                    .endDate(seminar.getEndDate())
                    .createdAt(seminar.getCreatedAt())
                    .updatedAt(seminar.getUpdatedAt())
                    .academicProgramId(seminar.getAcademicProgram().getId())
                    .academicProgramName(seminar.getAcademicProgram().getName())
                    .facultyName(seminar.getAcademicProgram().getFaculty().getName())
                    .availableSeats(availableSeats)
                    .fillPercentage(fillPercentage)
                    .hasMinimumParticipants(hasMinimumParticipants)
                    .enrolledStudents(enrolledStudentDTOs)
                    .build();

            return ResponseEntity.ok(
                    Map.of(
                            "success", true,
                            "seminar", detailDTO
                    )
            );

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "error", e.getMessage()
                    )
            );
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    Map.of(
                            "success", false,
                            "error", "Error al obtener el detalle del seminario: " + e.getMessage()
                    )
            );
        }
    }


    public ResponseEntity<?> listSeminarsForProgramHead(String status, Boolean active) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String email = authentication.getName();
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

            Long userProgramId = programAuthorityRepository
                    .findByUser_IdAndRole(user.getId(), ProgramRole.PROGRAM_HEAD)
                    .stream()
                    .findFirst()
                    .map(pa -> pa.getAcademicProgram().getId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No tienes permisos de jefe de programa"
                    ));

            List<Seminar> seminars;

            if (status != null && active != null) {
                SeminarStatus seminarStatus = SeminarStatus.valueOf(status.toUpperCase());
                seminars = seminarRepository.findByAcademicProgramIdAndStatusAndActiveOrderByCreatedAtDesc(
                        userProgramId, seminarStatus, active
                );
            } else if (status != null) {
                SeminarStatus seminarStatus = SeminarStatus.valueOf(status.toUpperCase());
                seminars = seminarRepository.findByAcademicProgramIdAndStatusOrderByCreatedAtDesc(
                        userProgramId, seminarStatus
                );
            } else if (active != null) {
                seminars = seminarRepository.findByAcademicProgramIdAndActiveOrderByCreatedAtDesc(
                        userProgramId, active
                );
            } else {
                seminars = seminarRepository.findByAcademicProgramIdOrderByCreatedAtDesc(userProgramId);
            }

            List<SeminarListDTO> seminarDTOs = seminars.stream()
                    .map(seminar -> {
                        int availableSeats = seminar.getMaxParticipants() - seminar.getCurrentParticipants();
                        double fillPercentage = (seminar.getCurrentParticipants() * 100.0) / seminar.getMaxParticipants();
                        boolean hasMinimumParticipants = seminar.getCurrentParticipants() >= seminar.getMinParticipants();
                        boolean isFull = seminar.getCurrentParticipants() >= seminar.getMaxParticipants();

                        return SeminarListDTO.builder()
                                .id(seminar.getId())
                                .name(seminar.getName())
                                .description(seminar.getDescription())
                                .totalCost(seminar.getTotalCost())
                                .minParticipants(seminar.getMinParticipants())
                                .maxParticipants(seminar.getMaxParticipants())
                                .currentParticipants(seminar.getCurrentParticipants())
                                .totalHours(seminar.getTotalHours())
                                .active(seminar.isActive())
                                .status(seminar.getStatus() != null ? seminar.getStatus().name() : null)
                                .startDate(seminar.getStartDate())
                                .endDate(seminar.getEndDate())
                                .createdAt(seminar.getCreatedAt())
                                .updatedAt(seminar.getUpdatedAt())
                                .availableSeats(availableSeats)
                                .fillPercentage(fillPercentage)
                                .hasMinimumParticipants(hasMinimumParticipants)
                                .isFull(isFull)
                                .build();
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(
                    Map.of(
                            "success", true,
                            "seminars", seminarDTOs,
                            "total", seminarDTOs.size()
                    )
            );

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "error", e.getMessage()
                    )
            );
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    Map.of(
                            "success", false,
                            "error", "Error al listar seminarios: " + e.getMessage()
                    )
            );
        }
    }


    @Transactional
    public ResponseEntity<?> startSeminar(Long seminarId) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String email = authentication.getName();
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

            Long userProgramId = programAuthorityRepository
                    .findByUser_IdAndRole(user.getId(), ProgramRole.PROGRAM_HEAD)
                    .stream()
                    .findFirst()
                    .map(pa -> pa.getAcademicProgram().getId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No tienes permisos de jefe de programa"
                    ));

            Seminar seminar = seminarRepository.findById(seminarId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "El seminario con ID " + seminarId + " no existe"
                    ));

            if (!seminar.getAcademicProgram().getId().equals(userProgramId)) {
                throw new IllegalArgumentException(
                        "Este seminario no pertenece a tu programa académico"
                );
            }

            if (!seminar.isActive()) {
                throw new IllegalArgumentException("El seminario no está activo");
            }

            if (seminar.getStatus() == SeminarStatus.IN_PROGRESS) {
                throw new IllegalArgumentException("El seminario ya está en progreso");
            }

            if (seminar.getStatus() == SeminarStatus.COMPLETED) {
                throw new IllegalArgumentException("El seminario ya ha sido completado");
            }

            if (seminar.getCurrentParticipants() < seminar.getMinParticipants()) {
                throw new IllegalArgumentException(
                        "No se puede iniciar el seminario. Se requieren al menos " +
                        seminar.getMinParticipants() + " participantes. Actualmente hay " +
                        seminar.getCurrentParticipants()
                );
            }

            seminar.setStatus(SeminarStatus.IN_PROGRESS);
            seminar.setStartDate(LocalDateTime.now());
            seminar.setUpdatedAt(LocalDateTime.now());
            seminarRepository.save(seminar);

            List<StudentProfile> enrolledStudents = seminarRepository.findEnrolledStudentsBySeminarId(seminarId);

            int emailsSent = 0;
            for (StudentProfile studentProfile : enrolledStudents) {
                User student = userRepository.findById(studentProfile.getId()).orElse(null);
                if (student != null && student.getEmail() != null) {
                    try {
                        SeminarStartedEvent event = SeminarStartedEvent.builder()
                                .recipientEmail(student.getEmail())
                                .recipientName(student.getName() + " " + student.getLastName())
                                .seminarName(seminar.getName())
                                .startDate(seminar.getStartDate())
                                .totalHours(seminar.getTotalHours())
                                .programName(seminar.getAcademicProgram().getName())
                                .build();

                        notificationEventPublisher.publishSeminarStartedEvent(event);
                        emailsSent++;
                    } catch (Exception e) {
                        // Continuar con el siguiente estudiante si falla el envío
                    }
                }
            }

            return ResponseEntity.ok(
                    Map.of(
                            "success", true,
                            "message", "Seminario iniciado exitosamente",
                            "seminarId", seminar.getId(),
                            "seminarName", seminar.getName(),
                            "status", seminar.getStatus().name(),
                            "startDate", seminar.getStartDate(),
                            "enrolledStudents", enrolledStudents.size(),
                            "emailsSent", emailsSent
                    )
            );

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "error", e.getMessage()
                    )
            );
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    Map.of(
                            "success", false,
                            "error", "Error al iniciar el seminario: " + e.getMessage()
                    )
            );
        }
    }



    public ResponseEntity<?> cancelSeminar(Long seminarId, String reason) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String email = authentication.getName();
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

            Long userProgramId = programAuthorityRepository
                    .findByUser_IdAndRole(user.getId(), ProgramRole.PROGRAM_HEAD)
                    .stream()
                    .findFirst()
                    .map(pa -> pa.getAcademicProgram().getId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No tienes permisos de jefe de programa"
                    ));

            Seminar seminar = seminarRepository.findById(seminarId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "El seminario con ID " + seminarId + " no existe"
                    ));

            if (!seminar.getAcademicProgram().getId().equals(userProgramId)) {
                throw new IllegalArgumentException(
                        "Este seminario no pertenece a tu programa académico"
                );
            }

            if (seminar.getStatus() != SeminarStatus.OPEN) {
                throw new IllegalArgumentException(
                        "Solo se pueden cancelar seminarios que estén en estado ABIERTO (OPEN). " +
                        "Estado actual: " + seminar.getStatus() + ". " +
                        "No se puede cancelar un seminario que ya ha iniciado o está completado."
                );
            }

            List<StudentProfile> enrolledStudents = seminarRepository.findEnrolledStudentsBySeminarId(seminarId);

            // Cambiar el status de la modalidad de cada estudiante a SEMINAR_CANCELED
            for (StudentProfile studentProfile : enrolledStudents) {
                List<StudentModality> modalities = studentModalityRepository.findByLeaderId(studentProfile.getId());
                for (StudentModality modality : modalities) {
                    modality.setStatus(ModalityProcessStatus.MODALITY_CANCELLED);
                    studentModalityRepository.save(modality);
                }
            }

            seminar.setStatus(SeminarStatus.CLOSED);
            seminar.setActive(false);
            seminar.setUpdatedAt(LocalDateTime.now());
            seminar.getEnrolledStudents().clear();
            seminarRepository.save(seminar);

            int emailsSent = 0;
            for (StudentProfile studentProfile : enrolledStudents) {
                User student = userRepository.findById(studentProfile.getId()).orElse(null);
                if (student != null && student.getEmail() != null) {
                    try {
                        SeminarCancelledEvent event = SeminarCancelledEvent.builder()
                                .recipientEmail(student.getEmail())
                                .recipientName(student.getName() + " " + student.getLastName())
                                .seminarName(seminar.getName())
                                .cancelledDate(LocalDateTime.now())
                                .programName(seminar.getAcademicProgram().getName())
                                .reason(reason)
                                .build();

                        notificationEventPublisher.publishSeminarCancelledEvent(event);
                        emailsSent++;
                    } catch (Exception e) {
                        // Continuar con el siguiente estudiante si falla el envío
                    }
                }
            }

            return ResponseEntity.ok(
                    Map.of(
                            "success", true,
                            "message", "Seminario cancelado exitosamente",
                            "seminarId", seminar.getId(),
                            "seminarName", seminar.getName(),
                            "status", seminar.getStatus().name(),
                            "previouslyEnrolledStudents", enrolledStudents.size(),
                            "emailsSent", emailsSent
                    )
            );

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "error", e.getMessage()
                    )
            );
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    Map.of(
                            "success", false,
                            "error", "Error al cancelar el seminario: " + e.getMessage()
                    )
            );
        }
    }

    @Transactional
    public ResponseEntity<?> updateSeminar(Long seminarId, SeminarDTO request) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String email = authentication.getName();
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

            Long userProgramId = programAuthorityRepository
                    .findByUser_IdAndRole(user.getId(), ProgramRole.PROGRAM_HEAD)
                    .stream()
                    .findFirst()
                    .map(pa -> pa.getAcademicProgram().getId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No tienes permisos de jefe de programa"
                    ));

            Seminar seminar = seminarRepository.findById(seminarId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "El seminario con ID " + seminarId + " no existe"
                    ));

            if (!seminar.getAcademicProgram().getId().equals(userProgramId)) {
                throw new IllegalArgumentException(
                        "Este seminario no pertenece a tu programa académico"
                );
            }

            if (seminar.getStatus() == SeminarStatus.COMPLETED) {
                throw new IllegalArgumentException(
                        "No se puede editar un seminario que ya ha sido completado"
                );
            }

            if (seminar.getStatus() == SeminarStatus.CLOSED) {
                throw new IllegalArgumentException(
                        "No se puede editar un seminario que ha sido cancelado"
                );
            }

            if (request.getMinParticipants() != null && request.getMaxParticipants() != null) {
                if (request.getMinParticipants() > request.getMaxParticipants()) {
                    throw new IllegalArgumentException(
                            "El mínimo de participantes no puede ser mayor al máximo"
                    );
                }
            }

            if (request.getMaxParticipants() != null && seminar.getCurrentParticipants() > request.getMaxParticipants()) {
                throw new IllegalArgumentException(
                        "No se puede reducir el máximo de participantes por debajo del número actual de inscritos (" +
                        seminar.getCurrentParticipants() + ")"
                );
            }

            if (request.getName() != null && !request.getName().isBlank()) {
                seminar.setName(request.getName());
            }

            if (request.getDescription() != null) {
                seminar.setDescription(request.getDescription());
            }

            if (request.getTotalCost() != null) {
                seminar.setTotalCost(request.getTotalCost());
            }

            if (request.getMinParticipants() != null) {
                seminar.setMinParticipants(request.getMinParticipants());
            }

            if (request.getMaxParticipants() != null) {
                seminar.setMaxParticipants(request.getMaxParticipants());
            }

            if (request.getTotalHours() != null) {
                seminar.setTotalHours(request.getTotalHours());
            }

            seminar.setUpdatedAt(LocalDateTime.now());
            seminarRepository.save(seminar);

            Map<String, Object> seminarData = new HashMap<>();
            seminarData.put("id", seminar.getId());
            seminarData.put("name", seminar.getName());
            seminarData.put("description", seminar.getDescription() != null ? seminar.getDescription() : "");
            seminarData.put("totalCost", seminar.getTotalCost());
            seminarData.put("minParticipants", seminar.getMinParticipants());
            seminarData.put("maxParticipants", seminar.getMaxParticipants());
            seminarData.put("currentParticipants", seminar.getCurrentParticipants());
            seminarData.put("totalHours", seminar.getTotalHours());
            seminarData.put("status", seminar.getStatus().name());
            seminarData.put("active", seminar.isActive());
            seminarData.put("updatedAt", seminar.getUpdatedAt());

            return ResponseEntity.ok(
                    Map.of(
                            "success", true,
                            "message", "Seminario actualizado exitosamente",
                            "seminar", seminarData
                    )
            );

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "error", e.getMessage()
                    )
            );
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    Map.of(
                            "success", false,
                            "error", "Error al actualizar el seminario: " + e.getMessage()
                    )
            );
        }
    }

    @Transactional
    public ResponseEntity<?> closeRegistrations(Long seminarId) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String email = authentication.getName();
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

            Long userProgramId = programAuthorityRepository
                    .findByUser_IdAndRole(user.getId(), ProgramRole.PROGRAM_HEAD)
                    .stream()
                    .findFirst()
                    .map(pa -> pa.getAcademicProgram().getId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No tienes permisos de jefe de programa"
                    ));

            Seminar seminar = seminarRepository.findById(seminarId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "El seminario con ID " + seminarId + " no existe"
                    ));

            if (!seminar.getAcademicProgram().getId().equals(userProgramId)) {
                throw new IllegalArgumentException(
                        "Este seminario no pertenece a tu programa académico"
                );
            }

            if (seminar.getStatus() == SeminarStatus.COMPLETED) {
                throw new IllegalArgumentException(
                        "No se pueden cerrar inscripciones de un seminario ya completado"
                );
            }

            if (seminar.getStatus() == SeminarStatus.CLOSED) {
                throw new IllegalArgumentException(
                        "No se pueden cerrar inscripciones de un seminario cancelado"
                );
            }

            if (seminar.getStatus() == SeminarStatus.REGISTRATION_CLOSED) {
                throw new IllegalArgumentException(
                        "Las inscripciones de este seminario ya están cerradas"
                );
            }

            seminar.setStatus(SeminarStatus.REGISTRATION_CLOSED);
            seminar.setUpdatedAt(LocalDateTime.now());
            seminarRepository.save(seminar);

            return ResponseEntity.ok(
                    Map.of(
                            "success", true,
                            "message", "Inscripciones cerradas exitosamente",
                            "seminarId", seminar.getId(),
                            "seminarName", seminar.getName(),
                            "status", seminar.getStatus().name(),
                            "currentParticipants", seminar.getCurrentParticipants(),
                            "maxParticipants", seminar.getMaxParticipants(),
                            "updatedAt", seminar.getUpdatedAt()
                    )
            );

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "error", e.getMessage()
                    )
            );
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    Map.of(
                            "success", false,
                            "error", "Error al cerrar inscripciones del seminario: " + e.getMessage()
                    )
            );
        }
    }

    @Transactional
    public ResponseEntity<?> completeSeminar(Long seminarId) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String email = authentication.getName();
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

            Long userProgramId = programAuthorityRepository
                    .findByUser_IdAndRole(user.getId(), ProgramRole.PROGRAM_HEAD)
                    .stream()
                    .findFirst()
                    .map(pa -> pa.getAcademicProgram().getId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No tienes permisos de jefe de programa"
                    ));

            Seminar seminar = seminarRepository.findById(seminarId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "El seminario con ID " + seminarId + " no existe"
                    ));

            if (!seminar.getAcademicProgram().getId().equals(userProgramId)) {
                throw new IllegalArgumentException(
                        "Este seminario no pertenece a tu programa académico"
                );
            }

            if (seminar.getStatus() != SeminarStatus.IN_PROGRESS) {
                throw new IllegalArgumentException(
                        "Solo se pueden completar seminarios que estén en estado EN PROGRESO (IN_PROGRESS). " +
                        "Estado actual: " + seminar.getStatus()
                );
            }

            seminar.setStatus(SeminarStatus.COMPLETED);
            seminar.setActive(false);
            seminar.setEndDate(LocalDateTime.now());
            seminar.setUpdatedAt(LocalDateTime.now());
            seminarRepository.save(seminar);

            return ResponseEntity.ok(
                    Map.of(
                            "success", true,
                            "message", "Seminario completado exitosamente",
                            "seminarId", seminar.getId(),
                            "seminarName", seminar.getName(),
                            "status", seminar.getStatus().name(),
                            "startDate", seminar.getStartDate(),
                            "endDate", seminar.getEndDate(),
                            "totalParticipants", seminar.getCurrentParticipants()
                    )
            );

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "error", e.getMessage()
                    )
            );
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    Map.of(
                            "success", false,
                            "error", "Error al completar el seminario: " + e.getMessage()
                    )
            );
        }
    }

    @Transactional
    public ResponseEntity<?> modalityReadyForDefenseByDirector(Long studentModalityId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User projectDirector = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        StudentModality studentModality = studentModalityRepository.findById(studentModalityId)
                .orElseThrow(() -> new RuntimeException("Modalidad no encontrada"));

        // Validación de documentos subidos (excepto para "Emprendimiento y fortalecimiento de empresa")
        String modalidadNombre = studentModality.getProgramDegreeModality().getDegreeModality().getName();
        if (!modalidadNombre.equalsIgnoreCase("Emprendimiento y fortalecimiento de empresa")) {
            Long degreeModalityId = studentModality.getProgramDegreeModality().getDegreeModality().getId();
            List<RequiredDocument> mandatoryDocs = requiredDocumentRepository.findByModalityIdAndActiveTrueAndDocumentType(degreeModalityId, DocumentType.MANDATORY);
            List<RequiredDocument> secondaryDocs = requiredDocumentRepository.findByModalityIdAndActiveTrueAndDocumentType(degreeModalityId, DocumentType.SECONDARY);
            List<RequiredDocument> requiredDocs = new ArrayList<>();
            requiredDocs.addAll(mandatoryDocs);
            requiredDocs.addAll(secondaryDocs);
            List<StudentDocument> uploadedDocs = studentDocumentRepository.findByStudentModalityId(studentModalityId);
            for (RequiredDocument reqDoc : requiredDocs) {
                StudentDocument doc = uploadedDocs.stream()
                    .filter(d -> d.getDocumentConfig().getId().equals(reqDoc.getId()))
                    .findFirst()
                    .orElse(null);
                // Si no existe documento o está vacío (por ejemplo, fileName es null o vacío)
                if (doc == null || doc.getFileName() == null || doc.getFileName().isBlank()) {
                    return ResponseEntity.badRequest().body(
                        Map.of(
                            "success", false,
                            "message", "El estudiante debe subir todos los documentos para marcar la modalidad como lista para defensa"
                        )
                    );
                }
            }
        }

        if (studentModality.getProjectDirector() == null ||
                !studentModality.getProjectDirector().getId().equals(projectDirector.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "success", false,
                            "message", "No tiene permiso para marcar la modalidad como lista para defensa. No es el director asignado a esta modalidad"
                    ));
        }

        // Validar estado actual
        if (studentModality.getStatus() != ModalityProcessStatus.PROPOSAL_APPROVED &&
            studentModality.getStatus() != ModalityProcessStatus.DEFENSE_REQUESTED_BY_PROJECT_DIRECTOR) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", "La modalidad no se encuentra en estado válido para notificar a jefatura de programa",
                            "currentStatus", studentModality.getStatus()
                    )
            );
        }

        // Cambiar estado al paso intermedio: jefatura debe revisar antes de notificar a jurados
        studentModality.setStatus(ModalityProcessStatus.PENDING_PROGRAM_HEAD_FINAL_REVIEW);
        studentModality.setUpdatedAt(LocalDateTime.now());
        studentModalityRepository.save(studentModality);

        historyRepository.save(
                ModalityProcessStatusHistory.builder()
                        .studentModality(studentModality)
                        .status(ModalityProcessStatus.PENDING_PROGRAM_HEAD_FINAL_REVIEW)
                        .changeDate(LocalDateTime.now())
                        .responsible(projectDirector)
                        .observations("Director de proyecto notificó a jefatura que los documentos finales están listos para revisión previa a la sustentación")
                        .build()
        );

        // Notificar a jefatura de programa (no a jurados - eso lo hará jefatura en el paso siguiente)
        notificationEventPublisher.publish(
            new DirectorNotifiesProgramHeadForFinalReviewEvent(
                studentModality.getId(),
                projectDirector.getId()
            )
        );

        return ResponseEntity.ok(
                Map.of(
                        "success", true,
                        "studentModalityId", studentModalityId,
                        "newStatus", ModalityProcessStatus.PENDING_PROGRAM_HEAD_FINAL_REVIEW,
                        "message", "Jefatura de programa ha sido notificada para revisar los documentos finales. Una vez aprobados, jefatura notificará a los jurados."
                )
        );
    }

    /**
     * Método para que jefatura de programa apruebe los documentos finales y notifique a los jurados.
     * Paso intermedio entre la notificación del director y la revisión de jurados.
     */
    @Transactional
    public ResponseEntity<?> programHeadApprovesAndNotifiesExaminers(Long studentModalityId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User programHead = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        StudentModality studentModality = studentModalityRepository.findById(studentModalityId)
                .orElseThrow(() -> new RuntimeException("Modalidad no encontrada"));

        // Validar que sea jefatura de programa
        Long academicProgramId = studentModality.getAcademicProgram().getId();
        boolean isProgramHead = programAuthorityRepository.existsByUser_IdAndAcademicProgram_IdAndRole(
                programHead.getId(), academicProgramId, ProgramRole.PROGRAM_HEAD);
        if (!isProgramHead) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    Map.of(
                            "success", false,
                            "message", "Solo jefatura de programa puede aprobar y notificar a los jurados en este paso"
                    )
            );
        }

        // Validar estado actual
        if (studentModality.getStatus() != ModalityProcessStatus.PENDING_PROGRAM_HEAD_FINAL_REVIEW) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", "La modalidad no está en espera de revisión de jefatura de programa",
                            "currentStatus", studentModality.getStatus()
                    )
            );
        }

        // Validar que TODOS los documentos SECONDARY estén aprobados por jefatura o en estado superior
        // EXCEPCIÓN: Para la modalidad "Emprendimiento y fortalecimiento de empresa", 
        // se permite avanzar sin validar que los documentos SECONDARY estén subidos
        String modalityName = studentModality.getProgramDegreeModality().getDegreeModality().getName();
        boolean isEmprendimientoModality = modalityName != null && 
                modalityName.equalsIgnoreCase("Emprendimiento y fortalecimiento de empresa");

        List<Map<String, Object>> invalidDocuments = new ArrayList<>();

        // Solo validar documentos SECONDARY si NO es la modalidad especial
        if (!isEmprendimientoModality) {
            Long degreeModalityId = studentModality.getProgramDegreeModality().getDegreeModality().getId();
            List<RequiredDocument> secondaryDocs = requiredDocumentRepository
                    .findByModalityIdAndActiveTrueAndDocumentType(degreeModalityId, DocumentType.SECONDARY);
            List<StudentDocument> uploadedDocs = studentDocumentRepository.findByStudentModalityId(studentModalityId);

            for (RequiredDocument reqDoc : secondaryDocs) {
                StudentDocument doc = uploadedDocs.stream()
                        .filter(d -> d.getDocumentConfig().getId().equals(reqDoc.getId()))
                        .findFirst()
                        .orElse(null);

                // Validar que el documento exista
                if (doc == null) {
                    invalidDocuments.add(Map.of(
                            "documentName", reqDoc.getDocumentName(),
                            "status", "NOT_UPLOADED"
                    ));
                    continue;
                }

                DocumentStatus status = doc.getStatus();

                // Estados inválidos: PENDING, REJECTED_FOR_PROGRAM_HEAD_REVIEW, CORRECTIONS_REQUESTED_BY_PROGRAM_HEAD
                if (status == DocumentStatus.PENDING ||
                    status == DocumentStatus.REJECTED_FOR_PROGRAM_HEAD_REVIEW ||
                    status == DocumentStatus.CORRECTIONS_REQUESTED_BY_PROGRAM_HEAD) {
                    invalidDocuments.add(Map.of(
                            "documentName", reqDoc.getDocumentName(),
                            "currentStatus", status.name(),
                            "reason", "Documento no aprobado por jefatura o requiere correcciones"
                    ));
                }
            }

            // Si hay documentos inválidos, retornar error sin permitir avanzar
            if (!invalidDocuments.isEmpty()) {
                return ResponseEntity.badRequest().body(
                        Map.of(
                                "success", false,
                                "message", "No se puede notificar a los jurados. Existen documentos que no están aprobados por jefatura o requieren correcciones:",
                                "invalidDocuments", invalidDocuments,
                                "totalInvalid", invalidDocuments.size()
                        )
                );
            }
        }

        // Cambiar estado a READY_FOR_DEFENSE (jurados pueden revisar)
        studentModality.setStatus(ModalityProcessStatus.READY_FOR_DEFENSE);
        studentModality.setUpdatedAt(LocalDateTime.now());
        studentModalityRepository.save(studentModality);

        historyRepository.save(
                ModalityProcessStatusHistory.builder()
                        .studentModality(studentModality)
                        .status(ModalityProcessStatus.READY_FOR_DEFENSE)
                        .changeDate(LocalDateTime.now())
                        .responsible(programHead)
                        .observations("Jefatura de programa aprobó todos los documentos y notificó a los jurados para revisión de la sustentación")
                        .build()
        );

        // Notificar a los jurados asignados
        List<DefenseExaminer> examiners = defenseExaminerRepository.findByStudentModalityId(studentModalityId);
        for (DefenseExaminer examinerAssignment : examiners) {
            User examiner = examinerAssignment.getExaminer();
            notificationEventPublisher.publish(
                new DefenseReadyByDirectorEvent(
                    studentModality.getId(),
                    examiner.getId(),
                    programHead.getId(),
                    null,
                    null
                )
            );
        }

        return ResponseEntity.ok(
                Map.of(
                        "success", true,
                        "studentModalityId", studentModalityId,
                        "newStatus", ModalityProcessStatus.READY_FOR_DEFENSE,
                        "message", "Todos los documentos fueron validados. Jurados notificados para revisión de la sustentación."
                )
        );
    }

    @Transactional
    public ResponseEntity<?> examinerFinalReviewCompleted(Long studentModalityId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User examiner = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        StudentModality studentModality = studentModalityRepository.findById(studentModalityId)
                .orElseThrow(() -> new RuntimeException("Modalidad no encontrada"));

        Long academicProgramId = studentModality.getAcademicProgram().getId();

        boolean isAuthorized =
                programAuthorityRepository.existsByUser_IdAndAcademicProgram_IdAndRole(
                        examiner.getId(),
                        academicProgramId,
                        ProgramRole.EXAMINER
                );

        if (!isAuthorized) {
            return ResponseEntity.status(403).body(
                    Map.of(
                            "success", false,
                            "message", "No tienes permisos para finalizar la revisión como jurado en este programa académico"
                    )
            );
        }

        // Validar estado actual
        if (studentModality.getStatus() != ModalityProcessStatus.READY_FOR_DEFENSE) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", "La modalidad no está en estado válido para finalizar revisión de jurado",
                            "currentStatus", studentModality.getStatus()
                    )
            );
        }

        // Validar que todos los documentos que requieren evaluación de propuesta estén aceptados por el jurado
        // Se validan documentos MANDATORY y SECONDARY que tengan requiresProposalEvaluation = true
        Long modalityId = studentModality.getProgramDegreeModality().getDegreeModality().getId();

        // Obtener documentos MANDATORY que requieren evaluación de propuesta
        List<RequiredDocument> mandatoryDocumentsWithEval =
                requiredDocumentRepository.findByModalityIdAndActiveTrueAndDocumentTypeAndRequiresProposalEvaluationTrue(
                        modalityId, DocumentType.MANDATORY);

        // Obtener documentos SECONDARY que requieren evaluación de propuesta
        List<RequiredDocument> secondaryDocumentsWithEval =
                requiredDocumentRepository.findByModalityIdAndActiveTrueAndDocumentTypeAndRequiresProposalEvaluationTrue(
                        modalityId, DocumentType.SECONDARY);

        // Combinar ambas listas
        List<RequiredDocument> documentsRequiringEvaluation = new ArrayList<>();
        documentsRequiringEvaluation.addAll(mandatoryDocumentsWithEval);
        documentsRequiringEvaluation.addAll(secondaryDocumentsWithEval);

        List<StudentDocument> uploadedDocuments =
                studentDocumentRepository.findByStudentModalityId(studentModalityId);
        Map<Long, StudentDocument> uploadedMap =
                uploadedDocuments.stream()
                        .collect(Collectors.toMap(
                                doc -> doc.getDocumentConfig().getId(),
                                doc -> doc
                        ));

        List<Map<String, Object>> invalidDocuments = new ArrayList<>();
        for (RequiredDocument required : documentsRequiringEvaluation) {
            StudentDocument uploaded = uploadedMap.get(required.getId());
            if (uploaded == null) {
                invalidDocuments.add(
                        Map.of(
                                "documentName", required.getDocumentName(),
                                "status", "NOT_UPLOADED"
                        )
                );
                continue;
            }
            if (uploaded.getStatus() != DocumentStatus.ACCEPTED_FOR_EXAMINER_REVIEW) {
                invalidDocuments.add(
                        Map.of(
                                "documentName", required.getDocumentName(),
                                "status", uploaded.getStatus()
                        )
                );
            }
        }
        if (!invalidDocuments.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", "Para finalizar la revisión, todos los documentos que requieren evaluación deben estar aceptados por los jurados",
                            "documents", invalidDocuments
                    )
            );
        }

        // Cambiar estado
        studentModality.setStatus(ModalityProcessStatus.FINAL_REVIEW_COMPLETED);
        studentModality.setUpdatedAt(LocalDateTime.now());
        studentModalityRepository.save(studentModality);

        // Registrar en historial
        historyRepository.save(
                ModalityProcessStatusHistory.builder()
                        .studentModality(studentModality)
                        .status(ModalityProcessStatus.FINAL_REVIEW_COMPLETED)
                        .changeDate(LocalDateTime.now())
                        .responsible(examiner)
                        .observations("Jurado finalizó la revisión de documentos. Modalidad lista para programación de sustentación.")
                        .build()
        );

        // Notificar al director de proyecto
        User projectDirector = studentModality.getProjectDirector();
        if (projectDirector != null) {
            notificationEventPublisher.publish(
                    new ExaminerFinalReviewCompletedEvent(
                            studentModality.getId(),
                            projectDirector.getId()
                    )
            );
        }

        return ResponseEntity.ok(
                Map.of(
                        "success", true,
                        "studentModalityId", studentModalityId,
                        "newStatus", ModalityProcessStatus.FINAL_REVIEW_COMPLETED,
                        "message", "Revisión final completada por el jurado. Se notificó al director para programar la sustentación."
                )
        );
    }

    /**
     * Devuelve un calendario de próximas sustentaciones para el jurado autenticado.
     * Solo incluye modalidades en estado DEFENSE_SCHEDULED, ordenadas por fecha de defensa ascendente.
     */
    public ResponseEntity<?> getExaminerDefenseCalendar() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        User examiner = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // Buscar todas las modalidades asignadas al jurado en estado DEFENSE_SCHEDULED
        List<StudentModality> modalities = studentModalityRepository.findForExaminerWithStatus(
                examiner.getId(),
                List.of(ModalityProcessStatus.DEFENSE_SCHEDULED)
        );

        // Filtrar solo las que tienen fecha de defensa futura o igual a hoy
        LocalDateTime now = LocalDateTime.now();
        List<ModalityListDTO> calendar = modalities.stream()
                .filter(sm -> sm.getDefenseDate() != null && !sm.getDefenseDate().isBefore(now))
                .sorted(Comparator.comparing(StudentModality::getDefenseDate))
                .map(sm -> {
                    List<StudentModalityMember> activeMembers = studentModalityMemberRepository.findByStudentModalityIdAndStatus(
                            sm.getId(), MemberStatus.ACTIVE);
                    String studentNames = activeMembers.stream()
                            .map(m -> m.getStudent().getName() + " " + m.getStudent().getLastName())
                            .collect(Collectors.joining(", "));
                    String studentEmails = activeMembers.stream()
                            .map(m -> m.getStudent().getEmail())
                            .collect(Collectors.joining(", "));
                    return ModalityListDTO.builder()
                            .studentModalityId(sm.getId())
                            .studentName(studentNames)
                            .studentEmail(studentEmails)
                            .modalityName(sm.getProgramDegreeModality().getDegreeModality().getName())
                            .currentStatus(sm.getStatus().name())
                            .currentStatusDescription(describeModalityStatus(sm.getStatus()))
                            .defenseDate(sm.getDefenseDate())
                            .defenseLocation(sm.getDefenseLocation())
                            .lastUpdatedAt(sm.getUpdatedAt())
                            .build();
                })
                .toList();
        return ResponseEntity.ok(calendar);
    }

    @Transactional
    public ResponseEntity<?> getExaminerTypeForModality(Long studentModalityId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        User examiner = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        DefenseExaminer defenseExaminer = defenseExaminerRepository
                .findByStudentModalityIdAndExaminerId(studentModalityId, examiner.getId())
                .orElse(null);

        if (defenseExaminer == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                        "success", false,
                        "message", "No está asignado como jurado a esta modalidad"
                    ));
        }

        return ResponseEntity.ok(Map.of(
            "success", true,
            "examinerType", defenseExaminer.getExaminerType().name()
        ));
    }

    @Transactional
    public ResponseEntity<?> getExaminerEvaluationForModality(Long studentModalityId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        User examiner = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        DefenseExaminer defenseExaminer = defenseExaminerRepository
                .findByStudentModalityIdAndExaminerId(studentModalityId, examiner.getId())
                .orElse(null);

        if (defenseExaminer == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                        "success", false,
                        "message", "No está asignado como jurado a esta modalidad"
                    ));
        }

        DefenseEvaluationCriteria evaluation = defenseEvaluationCriteriaRepository
                .findByDefenseExaminerId(defenseExaminer.getId())
                .orElse(null);

        if (evaluation == null) {
            return ResponseEntity.ok(Map.of(
                "success", false,
                "message", "No ha registrado evaluación para esta modalidad"
            ));
        }

        ExaminerEvaluationDTO dto = ExaminerEvaluationDTO.builder()
                .grade(evaluation.getGrade())
                .observations(evaluation.getObservations())
                .evaluationDate(evaluation.getEvaluatedAt())
                .build();

        return ResponseEntity.ok(Map.of(
            "success", true,
            "evaluation", dto
        ));
    }

    /**
     * El jurado autenticado obtiene su veredicto sobre documentos MANDATORY (propuesta de grado).
     * Devuelve la decisión individual del jurado, notas y evaluación de propuesta (si aplica).
     * Ruta: GET /modalities/documents/{studentDocumentId}/examiner-proposal-evaluation
     */
    public ResponseEntity<?> getMyProposalEvaluation(Long studentDocumentId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User examiner = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        StudentDocument document = studentDocumentRepository.findById(studentDocumentId)
                .orElseThrow(() -> new RuntimeException("Documento no encontrado"));

        StudentModality studentModality = document.getStudentModality();

        // Validar que sea un documento MANDATORY
        if (document.getDocumentConfig().getDocumentType() != DocumentType.MANDATORY) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Este documento no es de tipo inicial."
            ));
        }

        // Validar que el examiner esté asignado a la modalidad
        DefenseExaminer defenseExaminer = defenseExaminerRepository
                .findByStudentModalityIdAndExaminerId(studentModality.getId(), examiner.getId())
                .orElse(null);

        if (defenseExaminer == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "success", false,
                    "message", "No estás asignado como jurado a esta modalidad"
            ));
        }

        // Obtener la review del jurado para este documento
        ExaminerDocumentReview review = examinerDocumentReviewRepository
                .findByStudentDocumentIdAndExaminerId(studentDocumentId, examiner.getId())
                .orElse(null);

        if (review == null) {
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "No has emitido veredicto para este documento aún"
            ));
        }

        // Obtener la evaluación de propuesta si existe
        ProposalEvaluation proposalEvaluation = proposalEvaluationRepository
                .findByStudentDocumentIdAndExaminerId(studentDocumentId, examiner.getId())
                .orElse(null);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("documentId", document.getId());
        response.put("documentName", document.getDocumentConfig().getDocumentName());
        response.put("documentType", DocumentType.MANDATORY.name());
        response.put("examinerName", examiner.getName() + " " + examiner.getLastName());
        response.put("examinerEmail", examiner.getEmail());
        response.put("examinerType", defenseExaminer.getExaminerType().name());
        response.put("isTiebreaker", defenseExaminer.getExaminerType() == ExaminerType.TIEBREAKER_EXAMINER);

        // Veredicto individual
        response.put("decision", review.getDecision().name());
        response.put("decisionDescription", translateExaminerDocumentDecision(review.getDecision()));
        response.put("notes", review.getNotes());
        response.put("reviewedAt", review.getReviewedAt());

        // Evaluación de propuesta si existe
        if (proposalEvaluation != null) {
            Map<String, Object> evaluationData = new LinkedHashMap<>();
            evaluationData.put("summary", proposalEvaluation.getSummary());
            evaluationData.put("backgroundJustification", proposalEvaluation.getBackgroundJustification());
            evaluationData.put("problemStatement", proposalEvaluation.getProblemStatement());
            evaluationData.put("objectives", proposalEvaluation.getObjectives());
            evaluationData.put("methodology", proposalEvaluation.getMethodology());
            evaluationData.put("bibliographyReferences", proposalEvaluation.getBibliographyReferences());
            evaluationData.put("documentOrganization", proposalEvaluation.getDocumentOrganization());
            evaluationData.put("evaluatedAt", proposalEvaluation.getEvaluatedAt());
            response.put("proposalEvaluation", evaluationData);
        } else {
            response.put("proposalEvaluation", null);
        }

        // Estado actual del documento
        response.put("documentStatus", document.getStatus().name());
        response.put("documentNotes", document.getNotes());

        return ResponseEntity.ok(response);
    }

    /**
     * El jurado autenticado obtiene su veredicto sobre documentos SECONDARY (documento final).
     * Devuelve la decisión individual del jurado, notas y evaluación final (si aplica).
     * Ruta: GET /modalities/documents/{studentDocumentId}/examiner-final-evaluation
     */
    public ResponseEntity<?> getMyFinalDocumentEvaluation(Long studentDocumentId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User examiner = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        StudentDocument document = studentDocumentRepository.findById(studentDocumentId)
                .orElseThrow(() -> new RuntimeException("Documento no encontrado"));

        StudentModality studentModality = document.getStudentModality();

        // Validar que sea un documento SECONDARY
        if (document.getDocumentConfig().getDocumentType() != DocumentType.SECONDARY) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Este documento no es de tipo final."
            ));
        }

        // Validar que el examiner esté asignado a la modalidad
        DefenseExaminer defenseExaminer = defenseExaminerRepository
                .findByStudentModalityIdAndExaminerId(studentModality.getId(), examiner.getId())
                .orElse(null);

        if (defenseExaminer == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "success", false,
                    "message", "No estás asignado como jurado a esta modalidad"
            ));
        }

        // Obtener la review del jurado para este documento
        ExaminerDocumentReview review = examinerDocumentReviewRepository
                .findByStudentDocumentIdAndExaminerId(studentDocumentId, examiner.getId())
                .orElse(null);

        if (review == null) {
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "No has emitido veredicto para este documento aún"
            ));
        }

        // Obtener la evaluación final (FinalDocumentEvaluation) si existe
        FinalDocumentEvaluation finalEvaluation = secondaryDocumentEvaluationRepository
                .findByStudentDocumentIdAndExaminerId(studentDocumentId, examiner.getId())
                .orElse(null);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("documentId", document.getId());
        response.put("documentName", document.getDocumentConfig().getDocumentName());
        response.put("documentType", DocumentType.SECONDARY.name());
        response.put("examinerName", examiner.getName() + " " + examiner.getLastName());
        response.put("examinerEmail", examiner.getEmail());
        response.put("examinerType", defenseExaminer.getExaminerType().name());
        response.put("isTiebreaker", defenseExaminer.getExaminerType() == ExaminerType.TIEBREAKER_EXAMINER);

        // Veredicto individual
        response.put("decision", review.getDecision().name());
        response.put("decisionDescription", translateExaminerDocumentDecision(review.getDecision()));
        response.put("notes", review.getNotes());
        response.put("reviewedAt", review.getReviewedAt());

        // Evaluación final si existe
        if (finalEvaluation != null) {
            response.put("finalEvaluation", buildFinalEvaluationInfoMap(finalEvaluation));
        } else {
            response.put("finalEvaluation", null);
        }

        // Estado actual del documento
        response.put("documentStatus", document.getStatus().name());
        response.put("documentNotes", document.getNotes());

        return ResponseEntity.ok(response);
    }
    // =========================================================================
    // SOLICITUD DE EDICIÓN DE PROPUESTA APROBADA (STUDENT → EXAMINER)
    // =========================================================================

    /**
     * Permite al estudiante autenticado solicitar la edición de un documento
     * que ya fue aprobado por los jurados (estado ACCEPTED_FOR_EXAMINER_REVIEW).
     * Solo se permite si la modalidad no está cerrada/calificada.
     */
    @Transactional
    public ResponseEntity<?> requestDocumentEdit(Long studentDocumentId, DocumentEditRequestDTO request) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User student = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        StudentDocument document = studentDocumentRepository.findById(studentDocumentId)
                .orElseThrow(() -> new RuntimeException("Documento no encontrado"));

        StudentModality studentModality = document.getStudentModality();

        // Validar que el estudiante sea miembro activo de la modalidad
        boolean isActiveMember = studentModalityMemberRepository.isActiveMember(
                studentModality.getId(), student.getId());
        if (!isActiveMember) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "success", false,
                    "message", "No eres miembro activo de esta modalidad"
            ));
        }

        // Validar que sea un documento MANDATORY
        if (document.getDocumentConfig().getDocumentType() != DocumentType.MANDATORY) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Solo puedes solicitar edición de documentos de tipo MANDATORY (obligatorios)"
            ));
        }

        // Validar que el documento esté aprobado por jurados
        if (document.getStatus() != DocumentStatus.ACCEPTED_FOR_EXAMINER_REVIEW) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Solo puedes solicitar edición de documentos que hayan sido aprobados por los jurados. Estado actual: " + document.getStatus()
            ));
        }

        // Validar que la modalidad no esté en un estado final
        ModalityProcessStatus modalityStatus = studentModality.getStatus();
        if (modalityStatus == ModalityProcessStatus.GRADED_APPROVED ||
                modalityStatus == ModalityProcessStatus.GRADED_FAILED ||
                modalityStatus == ModalityProcessStatus.MODALITY_CLOSED ||
                modalityStatus == ModalityProcessStatus.MODALITY_CANCELLED ||
                modalityStatus == ModalityProcessStatus.SEMINAR_CANCELED ||
                modalityStatus == ModalityProcessStatus.CANCELLED_BY_CORRECTION_TIMEOUT) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "No se puede solicitar edición de documentos en modalidades cerradas o calificadas"
            ));
        }

        // Validar que no haya ya una solicitud pendiente o en desempate para este documento
        boolean hasPending = documentEditRequestRepository.existsByStudentDocumentIdAndStatus(
                studentDocumentId, DocumentEditRequestStatus.PENDING);
        boolean hasTiebreaker = documentEditRequestRepository.existsByStudentDocumentIdAndStatus(
                studentDocumentId, DocumentEditRequestStatus.TIEBREAKER_REQUIRED);
        if (hasPending || hasTiebreaker) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Ya existe una solicitud de edición pendiente para este documento"
            ));
        }

        // Guardar el estado previo de la modalidad para trazabilidad
        ModalityProcessStatus previousModalityStatus = studentModality.getStatus();

        // Cambiar el estado del documento a EDIT_REQUESTED
        document.setStatus(DocumentStatus.EDIT_REQUESTED);
        document.setUploadDate(LocalDateTime.now());
        studentDocumentRepository.save(document);

        // Historial del DOCUMENTO
        documentHistoryRepository.save(
                StudentDocumentStatusHistory.builder()
                        .studentDocument(document)
                        .status(DocumentStatus.EDIT_REQUESTED)
                        .changeDate(LocalDateTime.now())
                        .responsible(student)
                        .observations("Estudiante solicitó edición del documento aprobado. Motivo: " + request.getReason())
                        .build()
        );

        // Cambiar el estado de la MODALIDAD a EDIT_REQUESTED_BY_STUDENT
        studentModality.setStatus(ModalityProcessStatus.EDIT_REQUESTED_BY_STUDENT);
        studentModality.setUpdatedAt(LocalDateTime.now());
        studentModalityRepository.save(studentModality);

        // Crear la solicitud de edición
        DocumentEditRequest editRequest = DocumentEditRequest.builder()
                .studentDocument(document)
                .requester(student)
                .reason(request.getReason())
                .status(DocumentEditRequestStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
        documentEditRequestRepository.save(editRequest);

        // Trazabilidad en el historial de la MODALIDAD
        historyRepository.save(
                ModalityProcessStatusHistory.builder()
                        .studentModality(studentModality)
                        .status(ModalityProcessStatus.EDIT_REQUESTED_BY_STUDENT)
                        .changeDate(LocalDateTime.now())
                        .responsible(student)
                        .observations("Estudiante solicitó edición del documento '" +
                                document.getDocumentConfig().getDocumentName() +
                                ". Motivo: " + request.getReason() +
                                ". La solicitud fue enviada a los jurados para su evaluación.")
                        .build()
        );

        // Notificar a los jurados
        notificationEventPublisher.publish(
                new DocumentEditRequestedEvent(
                        studentModality.getId(),
                        studentDocumentId,
                        editRequest.getId(),
                        request.getReason(),
                        document.getDocumentConfig().getDocumentName(),
                        student.getId()
                )
        );

        return ResponseEntity.ok(Map.of(
                "success", true,
                "editRequestId", editRequest.getId(),
                "documentId", document.getId(),
                "documentName", document.getDocumentConfig().getDocumentName(),
                "newDocumentStatus", DocumentStatus.EDIT_REQUESTED.name(),
                "newModalityStatus", ModalityProcessStatus.EDIT_REQUESTED_BY_STUDENT.name(),
                "message", "Solicitud de edición registrada correctamente. Los jurados evaluadores serán notificados para votar."
        ));
    }

    /**
     * Permite a un jurado votar sobre una solicitud de edición de documento.
     *
     * Lógica de consenso (igual que revisión de documentos):
     * - AMBOS jurados primarios aprueban → solicitud APPROVED, documento pasa a EDIT_REQUEST_APPROVED
     * - AMBOS rechazan → solicitud REJECTED, documento vuelve a ACCEPTED_FOR_EXAMINER_REVIEW
     * - UNO aprueba + UNO rechaza → TIEBREAKER_REQUIRED, el jurado de desempate decide
     * - JURADO DE DESEMPATE vota → su decisión es definitiva
     */
    @Transactional
    public ResponseEntity<?> resolveDocumentEditRequest(Long editRequestId, DocumentEditResolutionDTO request) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User examiner = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        boolean hasExaminerRole = examiner.getRoles().stream()
                .anyMatch(role -> role.getName().equals("EXAMINER"));
        if (!hasExaminerRole) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "success", false,
                    "message", "Solo los jurados pueden votar sobre solicitudes de edición de documentos"
            ));
        }

        if (request.getApproved() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Debe indicar si aprueba (approved: true) o rechaza (approved: false) la solicitud"
            ));
        }

        // Si rechaza, las notas son obligatorias
        if (Boolean.FALSE.equals(request.getApproved()) &&
                (request.getResolutionNotes() == null || request.getResolutionNotes().isBlank())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Debe proporcionar notas al rechazar una solicitud de edición"
            ));
        }

        DocumentEditRequest editRequest = documentEditRequestRepository.findById(editRequestId)
                .orElseThrow(() -> new RuntimeException("Solicitud de edición no encontrada"));

        // Solo se puede votar si la solicitud está PENDING o TIEBREAKER_REQUIRED
        if (editRequest.getStatus() != DocumentEditRequestStatus.PENDING &&
                editRequest.getStatus() != DocumentEditRequestStatus.TIEBREAKER_REQUIRED) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Esta solicitud ya fue resuelta. Estado actual: " + editRequest.getStatus()
            ));
        }

        StudentDocument document = editRequest.getStudentDocument();
        StudentModality studentModality = document.getStudentModality();

        // Validar que el jurado esté asignado a la modalidad
        DefenseExaminer defenseExaminer = defenseExaminerRepository
                .findByStudentModalityIdAndExaminerId(studentModality.getId(), examiner.getId())
                .orElse(null);
        if (defenseExaminer == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "success", false,
                    "message", "No estás asignado como jurado a esta modalidad"
            ));
        }

        ExaminerType examinerType = defenseExaminer.getExaminerType();
        boolean isTiebreaker = examinerType == ExaminerType.TIEBREAKER_EXAMINER;

        // Validar que no haya ya votado
        if (documentEditRequestVoteRepository.existsByEditRequestIdAndExaminerId(editRequestId, examiner.getId())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Ya registraste tu veredicto sobre esta solicitud de edición"
            ));
        }

        // Si es jurado de desempate y la solicitud no está en TIEBREAKER_REQUIRED → no puede votar aún
        if (isTiebreaker && editRequest.getStatus() != DocumentEditRequestStatus.TIEBREAKER_REQUIRED) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "El jurado de desempate solo interviene cuando los jurados principales tienen veredictos divididos"
            ));
        }

        // Si es jurado primario y la solicitud está en TIEBREAKER_REQUIRED → ya no puede votar
        if (!isTiebreaker && editRequest.getStatus() == DocumentEditRequestStatus.TIEBREAKER_REQUIRED) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Los jurados principales ya votaron. Esta solicitud está en espera del jurado de desempate"
            ));
        }

        EditRequestVoteDecision voteDecision = Boolean.TRUE.equals(request.getApproved())
                ? EditRequestVoteDecision.APPROVED
                : EditRequestVoteDecision.REJECTED;

        // Guardar el voto
        DocumentEditRequestVote vote = DocumentEditRequestVote.builder()
                .editRequest(editRequest)
                .examiner(examiner)
                .decision(voteDecision)
                .notes(request.getResolutionNotes())
                .isTiebreakerVote(isTiebreaker)
                .votedAt(LocalDateTime.now())
                .build();
        documentEditRequestVoteRepository.save(vote);

        // ===== LÓGICA DE CONSENSO =====
        return processEditRequestConsensus(editRequest, document, studentModality, examiner, isTiebreaker, voteDecision, request.getResolutionNotes());
    }

    /**
     * Procesa el consenso de votos sobre una solicitud de edición.
     */
    private ResponseEntity<?> processEditRequestConsensus(
            DocumentEditRequest editRequest,
            StudentDocument document,
            StudentModality studentModality,
            User examiner,
            boolean isTiebreaker,
            EditRequestVoteDecision currentVote,
            String notes) {

        Long editRequestId = editRequest.getId();

        // Si es el jurado de desempate → su voto es definitivo
        if (isTiebreaker) {
            return applyFinalEditRequestDecision(
                    editRequest, document, studentModality, examiner,
                    currentVote == EditRequestVoteDecision.APPROVED, notes, true
            );
        }

        // Obtener los jurados primarios de la modalidad
        List<DefenseExaminer> primaryExaminers = defenseExaminerRepository
                .findPrimaryExaminersByStudentModalityId(studentModality.getId());

        if (primaryExaminers.size() < 2) {
            // Solo hay un jurado primario → su voto es suficiente
            return applyFinalEditRequestDecision(
                    editRequest, document, studentModality, examiner,
                    currentVote == EditRequestVoteDecision.APPROVED, notes, false
            );
        }

        // Obtener todos los votos de jurados primarios para esta solicitud
        List<DocumentEditRequestVote> primaryVotes = documentEditRequestVoteRepository
                .findByEditRequestId(editRequestId)
                .stream()
                .filter(v -> !v.getIsTiebreakerVote())
                .collect(Collectors.toList());

        // Si aún no han votado todos los jurados primarios, esperar
        if (primaryVotes.size() < 2) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "editRequestId", editRequestId,
                    "message", "Veredicto registrado. Esperando el veredicto del otro jurado principal.",
                    "votesReceived", primaryVotes.size(),
                    "votesRequired", 2
            ));
        }

        // Ambos han votado — analizar resultado
        long approvedCount = primaryVotes.stream()
                .filter(v -> v.getDecision() == EditRequestVoteDecision.APPROVED)
                .count();
        long rejectedCount = primaryVotes.stream()
                .filter(v -> v.getDecision() == EditRequestVoteDecision.REJECTED)
                .count();

        if (approvedCount == 2) {
            // Ambos aprueban → APPROVED
            return applyFinalEditRequestDecision(editRequest, document, studentModality, examiner, true, null, false);
        }

        if (rejectedCount == 2) {
            // Ambos rechazan → REJECTED
            String combinedNotes = primaryVotes.stream()
                    .filter(v -> v.getNotes() != null && !v.getNotes().isBlank())
                    .map(DocumentEditRequestVote::getNotes)
                    .collect(Collectors.joining(" | "));
            return applyFinalEditRequestDecision(editRequest, document, studentModality, examiner, false,
                    combinedNotes.isBlank() ? notes : combinedNotes, false);
        }

        // Votos divididos (uno aprueba, uno rechaza) → TIEBREAKER_REQUIRED
        editRequest.setStatus(DocumentEditRequestStatus.TIEBREAKER_REQUIRED);
        documentEditRequestRepository.save(editRequest);

        // Trazabilidad en historial del DOCUMENTO
        documentHistoryRepository.save(
                StudentDocumentStatusHistory.builder()
                        .studentDocument(document)
                        .status(DocumentStatus.EDIT_REQUESTED)
                        .changeDate(LocalDateTime.now())
                        .responsible(examiner)
                        .observations("Votos de jurados primarios divididos sobre la solicitud de edición. Se requiere jurado de desempate.")
                        .build()
        );

        // Trazabilidad en historial de la MODALIDAD
        historyRepository.save(
                ModalityProcessStatusHistory.builder()
                        .studentModality(studentModality)
                        .status(studentModality.getStatus())
                        .changeDate(LocalDateTime.now())
                        .responsible(examiner)
                        .observations("Solicitud de edición del documento '" +
                                document.getDocumentConfig().getDocumentName() +
                                "': votos de jurados principales divididos. Se requiere veredicto del jurado de desempate para resolver.")
                        .build()
        );

        // Notificar al jurado de desempate
        notificationEventPublisher.publish(
                new DocumentEditRequestedEvent(
                        studentModality.getId(),
                        document.getId(),
                        editRequest.getId(),
                        editRequest.getReason() + " [REQUIERE DESEMPATE: votos divididos entre jurados principales]",
                        document.getDocumentConfig().getDocumentName(),
                        examiner.getId()
                )
        );

        return ResponseEntity.ok(Map.of(
                "success", true,
                "editRequestId", editRequestId,
                "newStatus", DocumentEditRequestStatus.TIEBREAKER_REQUIRED.name(),
                "message", "Los jurados principales tienen votos divididos. El jurado de desempate deberá resolver la solicitud.",
                "votes", buildVotesSummary(documentEditRequestVoteRepository.findByEditRequestId(editRequestId))
        ));
    }

    /**
     * Aplica la decisión final sobre la solicitud de edición (aprobada o rechazada).
     */
    private ResponseEntity<?> applyFinalEditRequestDecision(
            DocumentEditRequest editRequest,
            StudentDocument document,
            StudentModality studentModality,
            User responsible,
            boolean approved,
            String finalNotes,
            boolean wasTiebreaker) {

        DocumentEditRequestStatus finalStatus = approved
                ? DocumentEditRequestStatus.APPROVED
                : DocumentEditRequestStatus.REJECTED;

        editRequest.setStatus(finalStatus);
        editRequest.setResolvedBy(responsible);
        editRequest.setResolutionNotes(finalNotes);
        editRequest.setResolvedAt(LocalDateTime.now());
        documentEditRequestRepository.save(editRequest);

        if (approved) {
            document.setStatus(DocumentStatus.EDIT_REQUEST_APPROVED);
            document.setNotes("Solicitud de edición aprobada" + (wasTiebreaker ? " por el jurado de desempate" : " por consenso de jurados") +
                    ". Puedes resubir el documento con los cambios necesarios.");
        } else {
            document.setStatus(DocumentStatus.ACCEPTED_FOR_EXAMINER_REVIEW);
            document.setNotes("Solicitud de edición rechazada" + (wasTiebreaker ? " por el jurado de desempate" : " por consenso de jurados") +
                    (finalNotes != null ? ". Motivo: " + finalNotes : ""));
        }
        document.setUploadDate(LocalDateTime.now());
        studentDocumentRepository.save(document);

        // Actualizar el estado de la MODALIDAD
        // - Si se APRUEBA: la modalidad permanece en EDIT_REQUESTED_BY_STUDENT hasta que el estudiante resuba
        // - Si se RECHAZA: la modalidad vuelve a EXAMINERS_ASSIGNED (estado antes de la solicitud)
        if (!approved) {
            studentModality.setStatus(ModalityProcessStatus.PROPOSAL_APPROVED);
            studentModality.setUpdatedAt(LocalDateTime.now());
            studentModalityRepository.save(studentModality);
        }
        // Si se aprueba, el estado sigue en EDIT_REQUESTED_BY_STUDENT; cambiará a EXAMINERS_ASSIGNED
        // cuando el estudiante resuba el documento (en uploadRequiredDocument)

        // Trazabilidad en historial del DOCUMENTO
        documentHistoryRepository.save(
                StudentDocumentStatusHistory.builder()
                        .studentDocument(document)
                        .status(approved ? DocumentStatus.EDIT_REQUEST_APPROVED : DocumentStatus.ACCEPTED_FOR_EXAMINER_REVIEW)
                        .changeDate(LocalDateTime.now())
                        .responsible(responsible)
                        .observations("Solicitud de edición " + (approved ? "APROBADA" : "RECHAZADA") +
                                (wasTiebreaker ? " por jurado de desempate" : " por consenso de jurados") +
                                (finalNotes != null ? ". Notas: " + finalNotes : ""))
                        .build()
        );

        // Trazabilidad en historial de la MODALIDAD
        ModalityProcessStatus newModalityStatus = approved
                ? ModalityProcessStatus.EDIT_REQUESTED_BY_STUDENT
                : ModalityProcessStatus.EXAMINERS_ASSIGNED;

        historyRepository.save(
                ModalityProcessStatusHistory.builder()
                        .studentModality(studentModality)
                        .status(newModalityStatus)
                        .changeDate(LocalDateTime.now())
                        .responsible(responsible)
                        .observations("Solicitud de edición del documento '" +
                                document.getDocumentConfig().getDocumentName() + "' " +
                                (approved ? "APROBADA" : "RECHAZADA") +
                                (wasTiebreaker ? " por el jurado de desempate" : " por consenso de jurados principales") +
                                (approved
                                        ? ". El estudiante puede resubir el documento con los cambios necesarios."
                                        : ". El documento permanece aprobado y la modalidad vuelve a su estado anterior.") +
                                (finalNotes != null && !finalNotes.isBlank() ? " Observaciones: " + finalNotes : ""))
                        .build()
        );

        // Notificar a los estudiantes del resultado
        notificationEventPublisher.publish(
                new DocumentEditResolvedEvent(
                        studentModality.getId(),
                        document.getId(),
                        editRequest.getId(),
                        approved,
                        finalNotes,
                        document.getDocumentConfig().getDocumentName(),
                        responsible.getId()
                )
        );

        List<Map<String, Object>> votesSummary = buildVotesSummary(
                documentEditRequestVoteRepository.findByEditRequestId(editRequest.getId())
        );

        return ResponseEntity.ok(Map.of(
                "success", true,
                "editRequestId", editRequest.getId(),
                "documentId", document.getId(),
                "documentName", document.getDocumentConfig().getDocumentName(),
                "finalStatus", finalStatus.name(),
                "newDocumentStatus", document.getStatus().name(),
                "newModalityStatus", studentModality.getStatus().name(),
                "resolvedByTiebreaker", wasTiebreaker,
                "votes", votesSummary,
                "message", approved
                        ? "Solicitud aprobada. El estudiante puede resubir el documento con los cambios."
                        : "Solicitud rechazada. El documento permanece en estado aprobado."
        ));
    }

    /**
     * Construye un resumen de los votos de los jurados.
     */
    private List<Map<String, Object>> buildVotesSummary(List<DocumentEditRequestVote> votes) {
        return votes.stream().map(v -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("examinerName", v.getExaminer().getName() + " " + v.getExaminer().getLastName());
            m.put("examinerEmail", v.getExaminer().getEmail());
            m.put("decision", v.getDecision().name());
            m.put("notes", v.getNotes());
            m.put("isTiebreakerVote", v.getIsTiebreakerVote());
            m.put("votedAt", v.getVotedAt());
            return m;
        }).collect(Collectors.toList());
    }

    /**
     * El jurado autenticado obtiene TODAS las solicitudes de edición de documentos
     * asociadas a una modalidad, con información completa del proceso:
     * - Información del documento y del solicitante
     * - Estado actual de la solicitud
     * - Votos ya registrados por los jurados (nombre, tipo, decisión, notas)
     * - Si el jurado autenticado ya ha votado o no
     * - Resultado final (si ya está resuelto)
     * Visible para todos los estados (PENDING, TIEBREAKER_REQUIRED, APPROVED, REJECTED).
     */
    @Transactional
    public ResponseEntity<?> getAllEditRequestsForExaminer(Long studentModalityId) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User examiner = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        DefenseExaminer defenseExaminer = defenseExaminerRepository
                .findByStudentModalityIdAndExaminerId(studentModalityId, examiner.getId())
                .orElse(null);

        if (defenseExaminer == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "success", false,
                    "message", "No estás asignado como jurado a esta modalidad"
            ));
        }

        StudentModality studentModality = studentModalityRepository.findById(studentModalityId)
                .orElseThrow(() -> new RuntimeException("Modalidad no encontrada"));

        // Obtener todos los miembros activos
        List<StudentModalityMember> activeMembers = studentModalityMemberRepository
                .findByStudentModalityIdAndStatus(studentModalityId, MemberStatus.ACTIVE);

        List<String> studentNames = activeMembers.stream()
                .map(m -> m.getStudent().getName() + " " + m.getStudent().getLastName() +
                        " (" + m.getStudent().getEmail() + ")" +
                        (Boolean.TRUE.equals(m.getIsLeader()) ? " – Líder" : ""))
                .collect(Collectors.toList());

        // Obtener TODAS las solicitudes de edición de la modalidad (todos los estados)
        List<DocumentEditRequest> allRequests = documentEditRequestRepository
                .findByStudentModalityId(studentModalityId);

        List<Map<String, Object>> requestDTOs = new ArrayList<>();

        for (DocumentEditRequest req : allRequests) {
            StudentDocument doc = req.getStudentDocument();

            List<DocumentEditRequestVote> votes = documentEditRequestVoteRepository
                    .findByEditRequestId(req.getId());

            boolean alreadyVoted = votes.stream()
                    .anyMatch(v -> v.getExaminer().getId().equals(examiner.getId()));

            DocumentEditRequestVote myVote = votes.stream()
                    .filter(v -> v.getExaminer().getId().equals(examiner.getId()))
                    .findFirst()
                    .orElse(null);

            List<Map<String, Object>> voteDTOs = votes.stream().map(v -> {
                Map<String, Object> voteMap = new LinkedHashMap<>();
                voteMap.put("examinerName", v.getExaminer().getName() + " " + v.getExaminer().getLastName());
                voteMap.put("examinerEmail", v.getExaminer().getEmail());
                String examinerTypeLabel = defenseExaminerRepository
                        .findByStudentModalityIdAndExaminerId(studentModalityId, v.getExaminer().getId())
                        .map(de -> de.getExaminerType().toSpanish())
                        .orElse("Jurado");
                voteMap.put("examinerTypeLabel", examinerTypeLabel);
                voteMap.put("decision", v.getDecision().name());
                voteMap.put("decisionLabel", v.getDecision() == com.SIGMA.USCO.documents.entity.enums.EditRequestVoteDecision.APPROVED
                        ? "Aprobado" : "Rechazado");
                voteMap.put("notes", v.getNotes());
                voteMap.put("isTiebreakerVote", v.getIsTiebreakerVote());
                voteMap.put("votedAt", v.getVotedAt());
                return voteMap;
            }).collect(Collectors.toList());

            String statusDesc = switch (req.getStatus()) {
                case PENDING -> "Pendiente de votación por los jurados principales";
                case TIEBREAKER_REQUIRED -> "Votos de jurados principales divididos – en espera del jurado de desempate";
                case APPROVED -> "Solicitud aprobada – el estudiante puede resubir el documento con los cambios";
                case REJECTED -> "Solicitud rechazada – el documento permanece en estado aprobado";
            };

            boolean canVote;
            if (defenseExaminer.getExaminerType() == ExaminerType.TIEBREAKER_EXAMINER) {
                canVote = req.getStatus() == DocumentEditRequestStatus.TIEBREAKER_REQUIRED && !alreadyVoted;
            } else {
                canVote = req.getStatus() == DocumentEditRequestStatus.PENDING && !alreadyVoted;
            }

            Map<String, Object> requestMap = new LinkedHashMap<>();
            requestMap.put("editRequestId", req.getId());
            requestMap.put("documentId", doc.getId());
            requestMap.put("documentName", doc.getDocumentConfig().getDocumentName());
            requestMap.put("documentType", doc.getDocumentConfig().getDocumentType().name());
            requestMap.put("currentDocumentStatus", doc.getStatus().name());
            requestMap.put("requesterName", req.getRequester().getName() + " " + req.getRequester().getLastName());
            requestMap.put("requesterEmail", req.getRequester().getEmail());
            requestMap.put("reason", req.getReason());
            requestMap.put("status", req.getStatus().name());
            requestMap.put("statusDescription", statusDesc);
            requestMap.put("createdAt", req.getCreatedAt());
            requestMap.put("resolvedAt", req.getResolvedAt());
            requestMap.put("finalResolutionNotes", req.getResolutionNotes());
            requestMap.put("totalVotes", votes.size());
            requestMap.put("votes", voteDTOs);
            requestMap.put("authenticatedExaminerAlreadyVoted", alreadyVoted);
            requestMap.put("authenticatedExaminerCanVote", canVote);

            if (myVote != null) {
                Map<String, Object> myVoteMap = new LinkedHashMap<>();
                myVoteMap.put("decision", myVote.getDecision().name());
                myVoteMap.put("decisionLabel", myVote.getDecision() == com.SIGMA.USCO.documents.entity.enums.EditRequestVoteDecision.APPROVED
                        ? "Aprobado" : "Rechazado");
                myVoteMap.put("notes", myVote.getNotes());
                myVoteMap.put("votedAt", myVote.getVotedAt());
                requestMap.put("myVote", myVoteMap);
            } else {
                requestMap.put("myVote", null);
            }

            requestDTOs.add(requestMap);
        }

        // Información del jurado autenticado en contexto de esta modalidad
        Map<String, Object> examinerContext = new LinkedHashMap<>();
        examinerContext.put("examinerId", examiner.getId());
        examinerContext.put("examinerName", examiner.getName() + " " + examiner.getLastName());
        examinerContext.put("examinerEmail", examiner.getEmail());
        examinerContext.put("examinerType", defenseExaminer.getExaminerType().name());
        examinerContext.put("examinerTypeLabel", defenseExaminer.getExaminerType().toSpanish());
        examinerContext.put("isTiebreaker", defenseExaminer.getExaminerType() == ExaminerType.TIEBREAKER_EXAMINER);

        // Información de la modalidad
        Map<String, Object> modalityInfo = new LinkedHashMap<>();
        modalityInfo.put("studentModalityId", studentModality.getId());
        modalityInfo.put("modalityName", studentModality.getProgramDegreeModality().getDegreeModality().getName());
        modalityInfo.put("academicProgram", studentModality.getProgramDegreeModality().getAcademicProgram().getName());
        modalityInfo.put("currentModalityStatus", studentModality.getStatus().name());
        modalityInfo.put("students", studentNames);

        long pending = allRequests.stream().filter(r -> r.getStatus() == DocumentEditRequestStatus.PENDING).count();
        long tiebreakerRequired = allRequests.stream().filter(r -> r.getStatus() == DocumentEditRequestStatus.TIEBREAKER_REQUIRED).count();
        long approvedCount = allRequests.stream().filter(r -> r.getStatus() == DocumentEditRequestStatus.APPROVED).count();
        long rejectedCount = allRequests.stream().filter(r -> r.getStatus() == DocumentEditRequestStatus.REJECTED).count();

        return ResponseEntity.ok(Map.of(
                "success", true,
                "examiner", examinerContext,
                "modality", modalityInfo,
                "summary", Map.of(
                        "total", allRequests.size(),
                        "pending", pending,
                        "tiebreakerRequired", tiebreakerRequired,
                        "approved", approvedCount,
                        "rejected", rejectedCount
                ),
                "editRequests", requestDTOs
        ));
    }

    /**
     * Lista todas las solicitudes de edición pendientes para una modalidad,
     * para que el jurado autenticado pueda revisarlas.
     * Incluye el estado de votación actual y los votos ya registrados.
     */
    @Transactional
    public ResponseEntity<?> getPendingEditRequestsForExaminer(Long studentModalityId) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User examiner = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        DefenseExaminer defenseExaminer = defenseExaminerRepository
                .findByStudentModalityIdAndExaminerId(studentModalityId, examiner.getId())
                .orElse(null);
        if (defenseExaminer == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "success", false,
                    "message", "No estás asignado como jurado a esta modalidad"
            ));
        }

        boolean isTiebreaker = defenseExaminer.getExaminerType() == ExaminerType.TIEBREAKER_EXAMINER;

        StudentModality studentModality = studentModalityRepository.findById(studentModalityId)
                .orElseThrow(() -> new RuntimeException("Modalidad no encontrada"));

        List<StudentDocument> docs = studentDocumentRepository.findByStudentModalityId(studentModalityId);
        List<DocumentEditRequestResponseDTO> result = new ArrayList<>();

        for (StudentDocument doc : docs) {
            // Mostrar pendientes y en desempate (el de desempate solo ve las que son TIEBREAKER_REQUIRED)
            List<DocumentEditRequest> requests = documentEditRequestRepository
                    .findByStudentDocumentId(doc.getId())
                    .stream()
                    .filter(req -> {
                        if (isTiebreaker) {
                            return req.getStatus() == DocumentEditRequestStatus.TIEBREAKER_REQUIRED;
                        } else {
                            return req.getStatus() == DocumentEditRequestStatus.PENDING;
                        }
                    })
                    .collect(Collectors.toList());

            for (DocumentEditRequest req : requests) {
                List<DocumentEditRequestVote> votes = documentEditRequestVoteRepository
                        .findByEditRequestId(req.getId());

                boolean alreadyVoted = votes.stream()
                        .anyMatch(v -> v.getExaminer().getId().equals(examiner.getId()));

                List<DocumentEditRequestResponseDTO.EditVoteDTO> voteDTOs = votes.stream()
                        .map(v -> DocumentEditRequestResponseDTO.EditVoteDTO.builder()
                                .examinerName(v.getExaminer().getName() + " " + v.getExaminer().getLastName())
                                .examinerEmail(v.getExaminer().getEmail())
                                .decision(v.getDecision().name())
                                .notes(v.getNotes())
                                .isTiebreakerVote(v.getIsTiebreakerVote())
                                .votedAt(v.getVotedAt())
                                .build())
                        .collect(Collectors.toList());

                String statusDesc = switch (req.getStatus()) {
                    case PENDING -> "Pendiente de votación por los jurados principales";
                    case TIEBREAKER_REQUIRED -> "Veredictos divididos – requiere veredicto del jurado de desempate";
                    case APPROVED -> "Solicitud aprobada";
                    case REJECTED -> "Solicitud rechazada";
                };

                result.add(DocumentEditRequestResponseDTO.builder()
                        .editRequestId(req.getId())
                        .studentDocumentId(doc.getId())
                        .documentName(doc.getDocumentConfig().getDocumentName())
                        .documentType(doc.getDocumentConfig().getDocumentType().name())
                        .requesterName(req.getRequester().getName() + " " + req.getRequester().getLastName())
                        .requesterEmail(req.getRequester().getEmail())
                        .reason(req.getReason())
                        .status(req.getStatus().name())
                        .statusDescription(statusDesc)
                        .createdAt(req.getCreatedAt())
                        .resolvedAt(req.getResolvedAt())
                        .votes(voteDTOs)
                        .finalResolutionNotes(req.getResolutionNotes())
                        .build());

                // Agregar flag de si ya votó en la respuesta
                // (lo añadimos al map de la respuesta principal)
                log.info("Solicitud {} - Jurado {} ya votó: {}", req.getId(), examiner.getEmail(), alreadyVoted);
            }
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "studentModalityId", studentModalityId,
                "examinerType", defenseExaminer.getExaminerType().name(),
                "isTiebreaker", isTiebreaker,
                "pendingEditRequests", result
        ));
    }

    // =========================================================================
    // MÉTODOS GET PARA EL ESTUDIANTE – SOLICITUDES DE EDICIÓN
    // =========================================================================

    /**
     * El estudiante autenticado obtiene TODAS sus solicitudes de edición de documentos,
     * agrupadas por modalidad, con el estado de votación de cada una.
     */
    @Transactional
    public ResponseEntity<?> getMyDocumentEditRequests() {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User student = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        List<DocumentEditRequest> requests = documentEditRequestRepository.findByRequesterId(student.getId());

        List<DocumentEditRequestResponseDTO> result = requests.stream()
                .map(req -> buildEditRequestResponseDTO(req))
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "totalRequests", result.size(),
                "editRequests", result
        ));
    }

    /**
     * El estudiante autenticado obtiene todas las solicitudes de edición
     * asociadas a una modalidad específica (por studentModalityId).
     * Útil para ver el estado actualizado de sus solicitudes en la modalidad actual.
     */
    @Transactional
    public ResponseEntity<?> getMyDocumentEditRequestsByModality(Long studentModalityId) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User student = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // Validar que el estudiante sea miembro activo de la modalidad
        boolean isActiveMember = studentModalityMemberRepository.isActiveMember(studentModalityId, student.getId());
        if (!isActiveMember) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "success", false,
                    "message", "No eres miembro activo de esta modalidad"
            ));
        }

        List<DocumentEditRequest> requests = documentEditRequestRepository
                .findByStudentModalityId(studentModalityId);

        List<DocumentEditRequestResponseDTO> result = requests.stream()
                .map(req -> buildEditRequestResponseDTO(req))
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "studentModalityId", studentModalityId,
                "totalRequests", result.size(),
                "editRequests", result
        ));
    }

    /**
     * El estudiante autenticado obtiene el detalle de una solicitud de edición específica por ID.
     */
    @Transactional
    public ResponseEntity<?> getDocumentEditRequestDetail(Long editRequestId) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User student = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        DocumentEditRequest request = documentEditRequestRepository.findById(editRequestId)
                .orElseThrow(() -> new RuntimeException("Solicitud de edición no encontrada"));

        StudentModality studentModality = request.getStudentDocument().getStudentModality();

        // Validar que el estudiante sea miembro activo de la modalidad o el solicitante
        boolean isActiveMember = studentModalityMemberRepository.isActiveMember(
                studentModality.getId(), student.getId());
        if (!isActiveMember && !request.getRequester().getId().equals(student.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "success", false,
                    "message", "No tienes permiso para ver esta solicitud de edición"
            ));
        }

        DocumentEditRequestResponseDTO dto = buildEditRequestResponseDTO(request);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "editRequest", dto
        ));
    }

    /**
     * Construye el DTO de respuesta para una solicitud de edición.
     */
    private DocumentEditRequestResponseDTO buildEditRequestResponseDTO(DocumentEditRequest req) {

        StudentDocument doc = req.getStudentDocument();

        List<DocumentEditRequestVote> votes = documentEditRequestVoteRepository
                .findByEditRequestId(req.getId());

        List<DocumentEditRequestResponseDTO.EditVoteDTO> voteDTOs = votes.stream()
                .map(v -> DocumentEditRequestResponseDTO.EditVoteDTO.builder()
                        .examinerName(v.getExaminer().getName() + " " + v.getExaminer().getLastName())
                        .examinerEmail(v.getExaminer().getEmail())
                        .decision(v.getDecision().name())
                        .notes(v.getNotes())
                        .isTiebreakerVote(v.getIsTiebreakerVote())
                        .votedAt(v.getVotedAt())
                        .build())
                .collect(Collectors.toList());

        String statusDesc = switch (req.getStatus()) {
            case PENDING -> "Pendiente de votación por los jurados evaluadores";
            case TIEBREAKER_REQUIRED -> "Votos de jurados principales divididos – en espera del jurado de desempate";
            case APPROVED -> "Solicitud aprobada – puedes resubir el documento con los cambios";
            case REJECTED -> "Solicitud rechazada – el documento permanece en estado aprobado";
        };

        return DocumentEditRequestResponseDTO.builder()
                .editRequestId(req.getId())
                .studentDocumentId(doc.getId())
                .documentName(doc.getDocumentConfig().getDocumentName())
                .documentType(doc.getDocumentConfig().getDocumentType().name())
                .requesterName(req.getRequester().getName() + " " + req.getRequester().getLastName())
                .requesterEmail(req.getRequester().getEmail())
                .reason(req.getReason())
                .status(req.getStatus().name())
                .statusDescription(statusDesc)
                .createdAt(req.getCreatedAt())
                .resolvedAt(req.getResolvedAt())
                .votes(voteDTOs)
                .finalResolutionNotes(req.getResolutionNotes())
                .build();
    }

    /**
     * Obtiene la lista de jurados (examinadores) asociados a una modalidad específica.
     * Retorna información detallada de cada jurado incluyendo su tipo (primario 1, primario 2, desempate).
     *
     * @param studentModalityId ID de la modalidad del estudiante
     * @return ResponseEntity con lista de jurados o error si la modalidad no existe
     */
    public ResponseEntity<?> getExaminersForModality(Long studentModalityId) {
        try {
            StudentModality studentModality = studentModalityRepository.findById(studentModalityId)
                    .orElseThrow(() -> new RuntimeException("Modalidad no encontrada"));

            List<DefenseExaminer> examiners = defenseExaminerRepository
                    .findByStudentModalityId(studentModalityId);

            if (examiners.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "studentModalityId", studentModalityId,
                        "examiners", List.of(),
                        "message", "No hay jurados asignados a esta modalidad"
                ));
            }

            List<Map<String, Object>> examinersList = examiners.stream()
                    .map(examiner -> Map.<String, Object>of(
                            "examinerId", examiner.getExaminer().getId(),
                            "examinerName", examiner.getExaminer().getName(),
                            "examinerLastName", examiner.getExaminer().getLastName(),
                            "examinerEmail", examiner.getExaminer().getEmail(),
                            "examinerType", examiner.getExaminerType().name(),
                            "examinerTypeDescription", translateExaminerType(examiner.getExaminerType()),
                            "assignmentDate", examiner.getAssignmentDate()
                    ))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "studentModalityId", studentModalityId,
                    "modalityName", studentModality.getProgramDegreeModality().getDegreeModality().getName(),
                    "modalityStatus", studentModality.getStatus().name(),
                    "examinersCount", examinersList.size(),
                    "examiners", examinersList
            ));

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Error al obtener los jurados: " + e.getMessage()
            ));
        }
    }

    /**
     * Traduce el enum ExaminerDocumentDecision al español para mejor legibilidad.
     */
    private String translateExaminerDocumentDecision(ExaminerDocumentDecision decision) {
        if (decision == null) return "Sin decisión";
        return switch (decision) {
            case ACCEPTED -> "Aprobado";
            case REJECTED -> "Rechazado";
            case CORRECTIONS_REQUESTED -> "Correcciones Solicitadas";
        };
    }

    /**
     * Traduce el enum ExaminerType al español para mejor legibilidad.
     */
    private String translateExaminerType(ExaminerType type) {
        return switch (type) {
            case PRIMARY_EXAMINER_1 -> "Jurado Principal 1";
            case PRIMARY_EXAMINER_2 -> "Jurado Principal 2";
            case TIEBREAKER_EXAMINER -> "Jurado de Desempate";
        };
    }

    /**
     * Devuelve la lista completa de estudiantes del programa académico al que
     * pertenece el comité autenticado, con filtro opcional por nombre del estudiante.
     * El listado se ordena por ID de usuario DESC (más reciente primero).
     *
     * El usuario autenticado se resuelve desde el SecurityContext (mismo patrón
     * que el resto del servicio), por lo que el controller no necesita extraer
     * ni pasar ningún objeto User.
     *
     * GET /modalities/committee/program-students?studentName=raul
     *
     * @param studentName (opcional) filtro parcial por nombre, apellido o nombre completo
     */
    public ResponseEntity<?> getProgramStudentsForCommittee(String studentName) {
        try {
            // 1. Resolver usuario autenticado desde el contexto de seguridad
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String email = auth.getName();

            User currentUser = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Usuario autenticado no encontrado"));

            // 2. Verificar que tiene rol COMMITTEE en al menos un programa
            List<ProgramAuthority> authorities = programAuthorityRepository
                    .findByUser_IdAndRole(currentUser.getId(), ProgramRole.PROGRAM_CURRICULUM_COMMITTEE);

            if (authorities.isEmpty()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                        "success", false,
                        "message", "El usuario no pertenece a ningún comité de programa académico."
                ));
            }

            // 3. Obtener el programa académico del comité
            AcademicProgram program = authorities.get(0).getAcademicProgram();

            // 4. Obtener todos los perfiles de estudiantes del programa
            List<StudentProfile> profiles = studentProfileRepository
                    .findByAcademicProgramId(program.getId());

            // 4.1 Filtrar por nombre si se proporciona (nombre, apellido o nombre completo)
            java.util.stream.Stream<StudentProfile> profileStream = profiles.stream();
            if (studentName != null && !studentName.trim().isEmpty()) {
                String nameLower = studentName.trim().toLowerCase();
                profileStream = profileStream.filter(sp -> {
                    User u = sp.getUser();
                    String fullName = (u.getName() + " " + u.getLastName()).toLowerCase();
                    return u.getName().toLowerCase().contains(nameLower)
                            || u.getLastName().toLowerCase().contains(nameLower)
                            || fullName.contains(nameLower);
                });
            }

            // 5. Construir la respuesta — ordenado por ID de usuario DESC (más reciente arriba)
            List<Map<String, Object>> students = profileStream
                    .sorted(Comparator.comparing((StudentProfile sp) -> sp.getUser().getId()).reversed())
                    .map(sp -> {
                        User u = sp.getUser();

                        // Modalidades donde el estudiante es líder
                        List<StudentModality> leaderModalities =
                                studentModalityRepository.findByLeaderId(sp.getId());

                        // Modalidades donde es miembro (grupales)
                        List<StudentModality> memberModalities =
                                studentModalityMemberRepository.findActiveModalitiesByUserId(u.getId());

                        // Unión sin duplicados
                        Set<Long> seen = new HashSet<>();
                        List<StudentModality> allModalities = new ArrayList<>();
                        for (StudentModality sm : leaderModalities) {
                            if (seen.add(sm.getId())) allModalities.add(sm);
                        }
                        for (StudentModality sm : memberModalities) {
                            if (seen.add(sm.getId())) allModalities.add(sm);
                        }

                        // Modalidad activa más reciente (si existe)
                        StudentModality activeModality = allModalities.stream()
                                .filter(sm -> sm.getStatus() != ModalityProcessStatus.MODALITY_CLOSED
                                        && sm.getStatus() != ModalityProcessStatus.MODALITY_CANCELLED
                                        && sm.getStatus() != ModalityProcessStatus.GRADED_APPROVED
                                        && sm.getStatus() != ModalityProcessStatus.GRADED_FAILED)
                                .max(Comparator.comparing(StudentModality::getUpdatedAt,
                                        Comparator.nullsLast(Comparator.naturalOrder())))
                                .orElse(null);

                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("studentId", u.getId());
                        row.put("studentCode", sp.getStudentCode());
                        row.put("name", u.getName());
                        row.put("lastName", u.getLastName());
                        row.put("fullName", u.getName() + " " + u.getLastName());
                        row.put("email", u.getEmail());
                        row.put("semester", sp.getSemester());
                        row.put("gpa", sp.getGpa());
                        row.put("approvedCredits", sp.getApprovedCredits());
                        row.put("totalModalities", allModalities.size());

                        if (activeModality != null) {
                            row.put("activeModalityId", activeModality.getId());
                            row.put("activeModalityName",
                                    activeModality.getProgramDegreeModality().getDegreeModality().getName());
                            row.put("activeModalityStatus", activeModality.getStatus().name());
                            row.put("activeModalityStatusDescription",
                                    describeModalityStatus(activeModality.getStatus()));
                            row.put("activeModalityDirector",
                                    activeModality.getProjectDirector() != null
                                            ? activeModality.getProjectDirector().getName() + " "
                                              + activeModality.getProjectDirector().getLastName()
                                            : null);
                        } else {
                            row.put("activeModalityId", null);
                            row.put("activeModalityName", null);
                            row.put("activeModalityStatus", null);
                            row.put("activeModalityStatusDescription", null);
                            row.put("activeModalityDirector", null);
                        }

                        return row;
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "academicProgramId", program.getId(),
                    "academicProgramName", program.getName(),
                    "totalStudents", students.size(),
                    "students", students
            ));

        } catch (RuntimeException e) {
            log.error("Error al obtener estudiantes del programa para comité: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Error inesperado al obtener estudiantes del programa: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Error al obtener los estudiantes: " + e.getMessage()
            ));
        }
    }

    /**
     * Obtiene los PDFs de historial académico de todos los estudiantes
     * asociados a una modalidad específica
     * @param modalityId ID de la modalidad
     * @return Lista de AcademicHistoryPdf de los estudiantes en la modalidad
     */
    public List<AcademicHistoryPdf> getAcademicHistoryPdfsByModality(Long modalityId) {
        log.info("Obteniendo PDFs de historial académico para la modalidad: {}", modalityId);
        try {
            return academicHistoryPdfRepository.findByStudentModalityId(modalityId);
        } catch (Exception e) {
            log.error("Error al obtener PDFs de historial académico: {}", e.getMessage(), e);
            throw new RuntimeException("Error al obtener los PDFs de historial académico: " + e.getMessage());
        }
    }

    /**
     * Descarga/visualiza un PDF de historial académico específico
     * @param academicHistoryPdfId ID del documento PDF
     * @return ResponseEntity con el archivo PDF para descargar o visualizar
     */
    public ResponseEntity<?> downloadAcademicHistoryPdf(Long academicHistoryPdfId) throws MalformedURLException {
        log.info("Descargando PDF de historial académico: {}", academicHistoryPdfId);
        
        AcademicHistoryPdf pdf = academicHistoryPdfRepository.findById(academicHistoryPdfId)
                .orElseThrow(() -> new RuntimeException("PDF de historial académico no encontrado con ID: " + academicHistoryPdfId));
        
        Path filePath = Paths.get(pdf.getFilePath());
        
        if (!Files.exists(filePath)) {
            log.error("Archivo PDF no existe en la ruta: {}", pdf.getFilePath());
            throw new RuntimeException("Archivo PDF no encontrado en el servidor: " + pdf.getOriginalFileName());
        }
        
        try {
            Resource resource = new UrlResource(filePath.toUri());
            
            if (!resource.exists()) {
                throw new RuntimeException("No se puede acceder al archivo PDF");
            }
            
            String contentType = "application/pdf";
            
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, 
                            "inline; filename=\"" + pdf.getOriginalFileName() + "\"")
                    .body(resource);
                    
        } catch (MalformedURLException e) {
            log.error("Error al crear URL del recurso: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error al descargar PDF: {}", e.getMessage(), e);
            throw new RuntimeException("Error al descargar el archivo PDF: " + e.getMessage());
        }
    }

}



