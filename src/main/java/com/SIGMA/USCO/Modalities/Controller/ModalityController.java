package com.SIGMA.USCO.Modalities.Controller;

import com.SIGMA.USCO.Modalities.Entity.DefenseExaminer;
import com.SIGMA.USCO.Modalities.Entity.DegreeModality;
import com.SIGMA.USCO.Modalities.Entity.StudentModality;
import com.SIGMA.USCO.Modalities.Entity.StudentModalityMember;
import com.SIGMA.USCO.Modalities.Entity.enums.MemberStatus;
import com.SIGMA.USCO.Modalities.Entity.enums.ModalityProcessStatus;
import com.SIGMA.USCO.Modalities.dto.*;
import com.SIGMA.USCO.Modalities.dto.response.ProjectDirectorResponse;
import com.SIGMA.USCO.Modalities.service.ModalityService;
import com.SIGMA.USCO.Users.Entity.User;
import com.SIGMA.USCO.Users.Entity.enums.ProgramRole;
import com.SIGMA.USCO.academic.entity.AcademicHistoryPdf;
import com.SIGMA.USCO.academic.entity.AcademicProgram;
import com.SIGMA.USCO.academic.entity.StudentProfile;
import com.SIGMA.USCO.documents.dto.DetailDocumentDTO;
import com.SIGMA.USCO.documents.entity.enums.DocumentStatus;
import com.SIGMA.USCO.documents.entity.RequiredDocument;
import com.SIGMA.USCO.documents.entity.StudentDocument;
import com.SIGMA.USCO.documents.service.DocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/modalities")
@RequiredArgsConstructor
@Slf4j
public class ModalityController {

    private final ModalityService modalityService;
    private final DocumentService documentService;

    @PostMapping("/create")
    @PreAuthorize("hasAuthority('PERM_CREATE_MODALITY') or hasAuthority('PERM_UPDATE_MODALITY')")
    public ResponseEntity<?> createModality(@RequestBody ModalityDTO request) {

        try {
            DegreeModality program = modalityService.createModality(request);

            return ResponseEntity.status(HttpStatus.CREATED).body(
                    Map.of(
                            "message", " Modalidad creada exitosamente."
                    )
            );

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", e.getMessage())
            );
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    Map.of("error", "Ocurrió un error al crear la modalidad.")
            );
        }

    }
    @PutMapping("/update/{modalityId}")
    @PreAuthorize("hasAuthority('PERM_CREATE_MODALITY') or hasAuthority('PERM_UPDATE_MODALITY')")
    public ResponseEntity<?> updateModality(@PathVariable Long modalityId, @RequestBody ModalityDTO request) {
        return modalityService.updateModality(modalityId, request);
    }

    @PutMapping("delete/{modalityId}")
    @PreAuthorize("hasAuthority('PERM_DESACTIVE_MODALITY')")
    public ResponseEntity<?> deactivateModality(@PathVariable Long modalityId) {
        return modalityService.desactiveModality(modalityId);
    }



    @PostMapping("/requirements/create/{modalityId}")
    @PreAuthorize("hasAuthority('PERM_CREATE_MODALITY') or hasAuthority('PERM_UPDATE_MODALITY')")
    public ResponseEntity<?> createModalityRequirements(@PathVariable Long modalityId, @RequestBody List<RequirementDTO> requirements) {
        modalityService.createModalityRequirements(modalityId, requirements);
        return ResponseEntity.ok("Requisitos creados correctamente");
    }

    @PutMapping("/requirements/{modalityId}/update/{requirementId}")
    @PreAuthorize("hasAuthority('PERM_CREATE_MODALITY') or hasAuthority('PERM_UPDATE_MODALITY')")
    public ResponseEntity<?> updateRequirement(@PathVariable Long modalityId, @PathVariable Long requirementId, @RequestBody RequirementDTO request) {
        modalityService.updateModalityRequirement(modalityId, requirementId, request);
        return ResponseEntity.ok("Requisito actualizado correctamente");
    }


    @GetMapping("/{modalityId}/requirements")
    public ResponseEntity<List<RequirementDTO>> listRequirements(@PathVariable Long modalityId, @RequestParam(required = false) Boolean active) {
        return modalityService.getModalityRequirements(modalityId, active);
    }

    @PutMapping("/requirements/delete/{requirementId}")
    @PreAuthorize("hasAuthority('PERM_DELETE_MODALITY_REQUIREMENT')")
    public ResponseEntity<?> desactiveRequirements(@PathVariable Long requirementId) {
        return modalityService.deleteRequirement(requirementId);
    }


    @GetMapping
    public ResponseEntity<?> getAllModalities() {
        return modalityService.getAllModalities();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getModalityById(@PathVariable Long id) {
        return modalityService.getModalityDetail(id);
    }

    @PostMapping(
            value = "/{studentModalityId}/documents/{requiredDocumentId}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<?> uploadDocument(
            @PathVariable Long studentModalityId,
            @PathVariable Long requiredDocumentId,
            @RequestPart("file") MultipartFile file
    ) throws IOException {

        return modalityService.uploadRequiredDocument(
                studentModalityId,
                requiredDocumentId,
                file
        );
    }



    @PostMapping("/{modalityId}/start")
    public ResponseEntity<?> startModality(@PathVariable Long modalityId) {
        return modalityService.startStudentModalityIndividual(modalityId);
    }

    @GetMapping("/{id}/validate-documents")
    public ResponseEntity<?> validateDocuments(@PathVariable Long id) {
        return modalityService.validateAllDocumentsUploaded(id);
    }

    @GetMapping("/my-available-documents")
    public ResponseEntity<?> getMyAvailableDocuments() {
        return modalityService.getAvailableDocumentsForStudent();
    }

    @GetMapping("/{studentModalityId}/documents")
    @PreAuthorize("hasAuthority('PERM_REVIEW_DOCUMENTS')")
    public ResponseEntity<?> listStudentDocuments(@PathVariable Long studentModalityId) {
        return modalityService.getStudentDocuments(studentModalityId);
    }

    @GetMapping("/student/{studentDocumentId}/view")
    @PreAuthorize("hasAuthority('PERM_VIEW_DOCUMENTS')")
    public ResponseEntity<?> viewStudentDocument(@PathVariable Long studentDocumentId) throws MalformedURLException {
        return modalityService.viewStudentDocument(studentDocumentId);
    }
    @PutMapping("/documents/{studentDocumentId}/review")
    @PreAuthorize("hasAuthority('PERM_REVIEW_DOCUMENTS')")
    public ResponseEntity<?> reviewDocument(@PathVariable Long studentDocumentId,@RequestBody DocumentReviewDTO request) {
        return modalityService.reviewStudentDocument(studentDocumentId, request);
    }
    @PostMapping("/{studentModalityId}/approve-program-head")
    @PreAuthorize("hasAuthority('PERM_APPROVE_MODALITY')")
    public ResponseEntity<?> approveByProgramHead(@PathVariable Long studentModalityId) {
        return modalityService.approveModalityByProgramHead(studentModalityId);
    }
    @PostMapping("/{studentModalityId}/approve-committee")
    @PreAuthorize("hasAuthority('PERM_APPROVE_MODALITY')")
    public ResponseEntity<?> approveByCommittee(@PathVariable Long studentModalityId) {
        return modalityService.approveModalityByCommittee(studentModalityId);
    }

    @PostMapping("/{studentModalityId}/approve-examiners")
    @PreAuthorize("hasAuthority('PERM_APPROVE_MODALITY_BY_EXAMINER')")
    public ResponseEntity<?> approveByExaminer(@PathVariable Long studentModalityId) {
        return modalityService.approveModalityByExaminers(studentModalityId);
    }

    @PostMapping("/documents/{studentDocumentId}/review-committee")
    @PreAuthorize("hasAuthority('PERM_REVIEW_DOCUMENTS')")
    public ResponseEntity<?> reviewDocumentCommittee(@PathVariable Long studentDocumentId, @RequestBody DocumentReviewDTO request) {
        return modalityService.reviewStudentDocumentByCommittee(studentDocumentId, request);
    }

    @PutMapping("/documents/{studentDocumentId}/review-examiner")
    @PreAuthorize("hasAuthority('PERM_REVIEW_DOCUMENTS')")
    public ResponseEntity<?> reviewDocumentExaminer(@PathVariable Long studentDocumentId, @RequestBody DocumentReviewDTO request) {
        return modalityService.reviewStudentDocumentByExaminer(studentDocumentId, request);
    }

    @PutMapping("/documents/{studentDocumentId}/review-examiner-final-document")
    @PreAuthorize("hasAuthority('PERM_REVIEW_DOCUMENTS')")
    public ResponseEntity<?> reviewSecondaryDocumentExaminer(@PathVariable Long studentDocumentId, @RequestBody DocumentReviewDTO request) {
        return modalityService.reviewFinalDocumentByExaminer(studentDocumentId, request);
    }

    @GetMapping("/students")
    @PreAuthorize("hasAuthority('PERM_VIEW_ALL_MODALITIES')")
    public ResponseEntity<?> listAllModalitiesForProgramHead(@RequestParam(required = false)
                                                               List<ModalityProcessStatus> statuses, @RequestParam(required = false)
    String name) {
        return modalityService.getAllStudentModalitiesForProgramHead(statuses, name);
    }

    @GetMapping("/students/committee")
    @PreAuthorize("hasAuthority('PERM_VIEW_ALL_MODALITIES')")
    public ResponseEntity<?> listAllModalitiesForCommittee(@RequestParam(required = false)
                                                             List<ModalityProcessStatus> statuses, @RequestParam(required = false)
                                                             String name) {
        return modalityService.getAllStudentModalitiesForProgramCurriculumCommittee(statuses, name);
    }

    @GetMapping("/students/director")
    @PreAuthorize("hasAuthority('PERM_VIEW_MODALITY')")
    public ResponseEntity<?> listAllModalitiesForProjectDirector(@RequestParam(required = false)
                                                                  List<ModalityProcessStatus> statuses,
                                                                  @RequestParam(required = false) String name) {
        return modalityService.getAllStudentModalitiesForProjectDirector(statuses, name);
    }

    @GetMapping("/students/examiner")
    @PreAuthorize("hasAuthority('PERM_VIEW_EXAMINER_MODALITIES')")
    public ResponseEntity<?> listAllModalitiesForExaminer(@RequestParam(required = false)
                                                           List<ModalityProcessStatus> statuses,
                                                           @RequestParam(required = false) String name) {
        return modalityService.getAllStudentModalitiesForExaminer(statuses, name);
    }

    @GetMapping("/students/{studentModalityId}")
    @PreAuthorize("hasAuthority('PERM_VIEW_ALL_MODALITIES')")
    public ResponseEntity<?> getModalityDetailForProgramHead(@PathVariable Long studentModalityId) {
        return modalityService.getStudentModalityDetailForProgramHead(studentModalityId);
    }
    @GetMapping("/students/{studentModalityId}/committee")
    @PreAuthorize("hasAuthority('PERM_VIEW_ALL_MODALITIES')")
    public ResponseEntity<?> getModalityDetailForCommittee(@PathVariable Long studentModalityId) {
        return modalityService.getStudentModalityDetailForCommittee(studentModalityId);
    }

    @GetMapping("/students/{studentModalityId}/director")
    @PreAuthorize("hasAuthority('PERM_VIEW_MODALITY')")
    public ResponseEntity<?> getModalityDetailForProjectDirector(@PathVariable Long studentModalityId) {
        return modalityService.getStudentModalityDetailForProjectDirector(studentModalityId);
    }

    @GetMapping("/students/{studentModalityId}/examiner")
    @PreAuthorize("hasAuthority('PERM_VIEW_EXAMINER_MODALITIES')")
    public ResponseEntity<?> getModalityDetailForExaminer(@PathVariable Long studentModalityId) {
        return modalityService.getStudentModalityDetailForExaminer(studentModalityId);
    }

    @PostMapping("/{studentModalityId}/cancellation/director/approve")
    @PreAuthorize("hasAuthority('PERM_APPROVE_CANCELLATION_DIRECTOR')")
    public ResponseEntity<?> approveModalityCancellationByDirector(@PathVariable Long studentModalityId) {
        return modalityService.approveModalityCancellationByDirector(studentModalityId);
    }

    @PostMapping("/{studentModalityId}/cancellation/director/reject")
    @PreAuthorize("hasAuthority('PERM_APPROVE_CANCELLATION_DIRECTOR')")
    public ResponseEntity<?> rejectModalityCancellationByDirector(
            @PathVariable Long studentModalityId,
            @RequestBody Map<String, String> body
    ) {
        String reason = body.get("reason");
        return modalityService.rejectModalityCancellationByDirector(studentModalityId, reason);
    }

    @PostMapping("/{studentModalityId}/cancellation/approve")
    @PreAuthorize("hasAuthority('PERM_APPROVE_CANCELLATION')")
    public ResponseEntity<?> approveCancellation(@PathVariable Long studentModalityId) {
        return modalityService.approveCancellation(studentModalityId);
    }

    @PostMapping("/{studentModalityId}/cancellation/reject")
    @PreAuthorize("hasAuthority('PERM_REJECT_CANCELLATION')")
    public ResponseEntity<?> rejectCancellation(@PathVariable Long studentModalityId, @RequestBody String reason
    ) {
        return modalityService.rejectCancellation(studentModalityId, reason);
    }

    @GetMapping("/cancellation-request")
    @PreAuthorize("hasAuthority('PERM_VIEW_CANCELLATIONS')")
    public ResponseEntity<List<CancellationList>> getPendingCancellations() {

        List<CancellationList> cancellations =
                modalityService.getPendingCancellations();

        return ResponseEntity.ok(cancellations);
    }

    @GetMapping("/cancellation/document/{studentModalityId}")
    @PreAuthorize("hasAuthority('PERM_VIEW_CANCELLATIONS')")
    public ResponseEntity<Resource> getCancellationDocument(@PathVariable Long studentModalityId) throws MalformedURLException {

        StudentDocument document = documentService.getDocumentCancellation(studentModalityId);

        Path filePath = Paths.get(document.getFilePath());
        Resource resource = new UrlResource(filePath.toUri());

        if (!resource.exists() || !resource.isReadable()) {
            throw new RuntimeException("No se pudo leer el archivo");
        }

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + document.getFileName() + "\""
                )
                .body(resource);
    }

    @PostMapping("/{studentModalityId}/assign-director/{directorId}")
    @PreAuthorize("hasAuthority('PERM_ASSIGN_PROJECT_DIRECTOR')")
    public ResponseEntity<?> assignProjectDirector(@PathVariable Long studentModalityId, @PathVariable Long directorId) {
        return modalityService.assignProjectDirector(studentModalityId, directorId);
    }


    @PutMapping("/{studentModalityId}/change-director")
    @PreAuthorize("hasAuthority('PERM_ASSIGN_PROJECT_DIRECTOR')")
    public ResponseEntity<?> changeProjectDirector(@PathVariable Long studentModalityId, @RequestBody @Valid ChangeDirectorDTO request) {
        return modalityService.changeProjectDirector(studentModalityId, request.getNewDirectorId(), request.getReason());
    }

    @PostMapping("/{studentModalityId}/propose-defense-director")
    @PreAuthorize("hasAuthority('PERM_PROPOSE_DEFENSE')")
    public ResponseEntity<?> proposeDefenseByDirector(@PathVariable Long studentModalityId, @RequestBody ScheduleDefenseDTO request) {
        return modalityService.scheduleDefense(studentModalityId, request);
    }



    @GetMapping("/defense-proposals/pending")
    @PreAuthorize("hasAuthority('PERM_SCHEDULE_DEFENSE')")
    public ResponseEntity<?> getPendingDefenseProposals() {
        return modalityService.getPendingDefenseProposals();
    }

    @PostMapping("/{studentModalityId}/defense-proposals/approve")
    @PreAuthorize("hasAuthority('PERM_SCHEDULE_DEFENSE')")
    public ResponseEntity<?> approveDefenseProposal(@PathVariable Long studentModalityId) {
        return modalityService.approveDefenseProposal(studentModalityId);
    }

    @PostMapping("/{studentModalityId}/defense-proposals/reschedule")
    @PreAuthorize("hasAuthority('PERM_SCHEDULE_DEFENSE')")
    public ResponseEntity<?> rescheduleDefense(@PathVariable Long studentModalityId, @RequestBody ScheduleDefenseDTO request) {
        return modalityService.rescheduleDefense(studentModalityId, request);
    }

    @PostMapping("/{studentModalityId}/examiners/assign")
    @PreAuthorize("hasAuthority('PERM_SCHEDULE_DEFENSE')")
    public ResponseEntity<?> assignExaminers(@PathVariable Long studentModalityId, @RequestBody ScheduleDefenseDTO request) {
        return modalityService.assignExaminers(studentModalityId, request);
    }

    @PostMapping("/{studentModalityId}/final-evaluation/register")
    @PreAuthorize("hasAuthority('PERM_EVALUATE_DEFENSE')")
    public ResponseEntity<?> registerFinalDefenseEvaluation(
            @PathVariable Long studentModalityId,
            @RequestBody ExaminerEvaluationDTO evaluationDTO) {
        return modalityService.registerFinalDefenseEvaluation(studentModalityId, evaluationDTO);
    }

    @GetMapping("/project-directors")
    @PreAuthorize("hasAuthority('PERM_VIEW_PROJECT_DIRECTOR')")
    public ResponseEntity<List<ProjectDirectorResponse>> getProjectDirectors() {
        return ResponseEntity.ok(modalityService.getProjectDirectors());
    }

    @GetMapping("/program-heads")
    @PreAuthorize("hasAuthority('PERM_VIEW_PROGRAM_HEAD')")
    public ResponseEntity<List<ProjectDirectorResponse>> getProgramHeads() {
        return ResponseEntity.ok(modalityService.getProgramHeads());
    }

    @GetMapping("/committee")
    @PreAuthorize("hasAuthority('PERM_VIEW_COMMITTEE')")
    public ResponseEntity<List<ProjectDirectorResponse>> getProgramCurriculumCommittee(
            @RequestParam(required = false) Long academicProgramId,
            @RequestParam(required = false) Long facultyId
    ) {
        return ResponseEntity.ok(modalityService.getProgramCurriculumCommittee(academicProgramId, facultyId));
    }

    @GetMapping("/examiners")
    @PreAuthorize("hasAuthority('PERM_VIEW_COMMITTEE')")
    public ResponseEntity<List<ProjectDirectorResponse>> getExaminers(
            @RequestParam(required = false) Long academicProgramId,
            @RequestParam(required = false) Long facultyId
    ) {
        return ResponseEntity.ok(modalityService.getExaminers(academicProgramId, facultyId));
    }

    @GetMapping("/examiners/for-committee")
    @PreAuthorize("hasAuthority('PERM_VIEW_EXAMINER')")
    public ResponseEntity<List<ProjectDirectorResponse>> getExaminersForCommittee() {
        return ResponseEntity.ok(modalityService.getExaminersForCommittee());
    }


    @PreAuthorize("hasAuthority('PERM_VIEW_FINAL_DEFENSE_RESULT')")
    public ResponseEntity<?> getFinalDefenseResult(@PathVariable Long studentModalityId) {
        return modalityService.getFinalDefenseResult(studentModalityId);
    }

    @GetMapping("/final-evaluation/my-result")
    public ResponseEntity<?> getMyFinalDefenseResult() {
        return modalityService.getMyFinalDefenseResult();
    }



    @PostMapping("/{studentModalityId}/documents/{documentId}/resubmit-correction")
    public ResponseEntity<?> resubmitCorrectedDocument(
            @PathVariable Long studentModalityId,
            @PathVariable Long documentId,
            @RequestParam("file") MultipartFile file) {
        try {
            return modalityService.resubmitCorrectedDocument(studentModalityId, documentId, file);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "message", "Error al procesar el archivo: " + e.getMessage()
                    ));
        }
    }

    @PostMapping("/documents/{documentId}/approve-correction")
    @PreAuthorize("hasAuthority('PERM_REVIEW_DOCUMENTS')")
    public ResponseEntity<?> approveCorrectedDocument(@PathVariable Long documentId) {
        return modalityService.approveCorrectedDocument(documentId);
    }

    @PostMapping("/documents/{documentId}/reject-correction-final")
    @PreAuthorize("hasAuthority('PERM_REVIEW_DOCUMENTS')")
    public ResponseEntity<?> rejectCorrectedDocumentFinal(
            @PathVariable Long documentId,
            @RequestBody Map<String, String> request) {
        String reason = request.get("reason");
        return modalityService.rejectCorrectedDocumentFinal(documentId, reason);
    }

    @GetMapping("/{studentModalityId}/correction-deadline-status")
    public ResponseEntity<?> getCorrectionDeadlineStatus(@PathVariable Long studentModalityId) {
        return modalityService.getCorrectionDeadlineStatus(studentModalityId);
    }


    @PostMapping("/{studentModalityId}/close-by-committee")
    @PreAuthorize("hasAuthority('PERM_APPROVE_CANCELLATION') or hasAuthority('PERM_REVIEW_DOCUMENT_COMMITTEE')")
    public ResponseEntity<?> closeModalityByCommittee(
            @PathVariable Long studentModalityId,
            @RequestBody Map<String, String> request
    ) {
        String reason = request.get("reason");
        return modalityService.closeModalityByCommittee(studentModalityId, reason);
    }


    @PostMapping("/{studentModalityId}/approve-final-by-committee")
    @PreAuthorize("hasAuthority('PERM_APPROVE_MODALITY_BY_COMMITTEE')")
    public ResponseEntity<?> approveFinalModalityByCommittee(@PathVariable Long studentModalityId, @RequestBody(required = false) Map<String, String> request) {
        String observations = request != null ? request.get("observations") : null;
        return modalityService.approveFinalModalityByCommittee(studentModalityId, observations);
    }

    @PostMapping("/{studentModalityId}/reject-final-by-committee")
    @PreAuthorize("hasAuthority('PERM_REJECT_MODALITY_BY_COMMITTEE')")
    public ResponseEntity<?> rejectFinalModalityByCommittee(@PathVariable Long studentModalityId, @RequestBody Map<String, String> request) {
        String reason = request.get("reason");
        return modalityService.rejectFinalModalityByCommittee(studentModalityId, reason);
    }


    @PostMapping("/seminar/create")
    @PreAuthorize("hasAuthority('PERM_CREATE_SEMINAR')")
    public ResponseEntity<?> createSeminar(@Valid @RequestBody SeminarDTO request) {
        return modalityService.createSeminar(request);
    }

    @GetMapping("/seminar/{seminarId}/detail")
    public ResponseEntity<?> getSeminarDetail(@PathVariable Long seminarId) {
        return modalityService.FgetSeminarDetailForProgramHead(seminarId);
    }

    @GetMapping("/seminar/available")
    @PreAuthorize("hasRole('ROLE_STUDENT')")
    public ResponseEntity<?> listActiveSeminarsWithSeats() {
        return modalityService.listActiveSeminarsWithSeats();
    }


    @PostMapping("/seminar/{seminarId}/enroll")
    @PreAuthorize("hasRole('ROLE_STUDENT')")
    public ResponseEntity<?> enrollInSeminar(@PathVariable Long seminarId) {
        return modalityService.enrollInSeminar(seminarId);
    }


    @GetMapping("/seminars")
    @PreAuthorize("hasAuthority('PERM_CREATE_SEMINAR')")
    public ResponseEntity<?> listSeminars(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean active) {
        return modalityService.listSeminarsForProgramHead(status, active);
    }


    @PostMapping("/seminar/{seminarId}/start")
    @PreAuthorize("hasAuthority('PERM_CREATE_SEMINAR')")
    public ResponseEntity<?> startSeminar(@PathVariable Long seminarId) {
        return modalityService.startSeminar(seminarId);
    }

    @PostMapping("/seminar/{seminarId}/cancel")
    @PreAuthorize("hasAuthority('PERM_CREATE_SEMINAR')")
    public ResponseEntity<?> cancelSeminar(@PathVariable Long seminarId, @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        return modalityService.cancelSeminar(seminarId, reason);
    }

    @PutMapping("/seminar/{seminarId}")
    @PreAuthorize("hasAuthority('PERM_CREATE_SEMINAR')")
    public ResponseEntity<?> updateSeminar(@PathVariable Long seminarId, @Valid @RequestBody SeminarDTO request) {
        return modalityService.updateSeminar(seminarId, request);
    }

    @PostMapping("/seminar/{seminarId}/close-registrations")
    @PreAuthorize("hasAuthority('PERM_CREATE_SEMINAR')")
    public ResponseEntity<?> closeRegistrations(@PathVariable Long seminarId) {
        return modalityService.closeRegistrations(seminarId);
    }

    @PostMapping("/seminar/{seminarId}/complete")
    @PreAuthorize("hasAuthority('PERM_CREATE_SEMINAR')")
    public ResponseEntity<?> completeSeminar(@PathVariable Long seminarId) {
        return modalityService.completeSeminar(seminarId);
    }

    @PostMapping("/{studentModalityId}/ready-for-defense")
    @PreAuthorize("hasAuthority('PERM_PROPOSE_DEFENSE')")
    public ResponseEntity<?> modalityReadyForDefenseByDirector(@PathVariable Long studentModalityId) {
        return modalityService.modalityReadyForDefenseByDirector(studentModalityId);
    }

    @PostMapping("/{studentModalityId}/program-head/approve-final-and-notify-examiners")
    @PreAuthorize("hasAuthority('PERM_APPROVE_MODALITY')")
    public ResponseEntity<?> programHeadApprovesAndNotifiesExaminers(@PathVariable Long studentModalityId) {
        return modalityService.programHeadApprovesAndNotifiesExaminers(studentModalityId);
    }

    @PostMapping("/{studentModalityId}/final-review-completed")
    @PreAuthorize("hasAuthority('PERM_APPROVE_MODALITY_BY_EXAMINER')")
    public ResponseEntity<?> examinerFinalReviewCompleted(@PathVariable Long studentModalityId) {
        return modalityService.examinerFinalReviewCompleted(studentModalityId);
    }


    @GetMapping("/{studentModalityId}/examiner-evaluation")
    @PreAuthorize("hasAuthority('PERM_VIEW_EXAMINER_MODALITIES')")
    public ResponseEntity<?> getFinalDefenseEvaluationForExaminer(@PathVariable Long studentModalityId) {
        return modalityService.getFinalDefenseEvaluationForExaminer(studentModalityId);
    }

    /**
     * Endpoint para que el jurado autenticado obtenga su calendario de próximas sustentaciones.
     * Solo incluye modalidades en estado DEFENSE_SCHEDULED, ordenadas por fecha de defensa ascendente.
     */
    @GetMapping("/examiner/defense-calendar")
    @PreAuthorize("hasAuthority('PERM_VIEW_EXAMINER_MODALITIES')")
    public ResponseEntity<?> getExaminerDefenseCalendar() {
        return modalityService.getExaminerDefenseCalendar();
    }

    @GetMapping("/examiner-type/{studentModalityId}")
    @PreAuthorize( "hasAuthority('PERM_VIEW_EXAMINER_MODALITIES')")
    public ResponseEntity<?> getExaminerTypeForModality(@PathVariable Long studentModalityId) {
        return modalityService.getExaminerTypeForModality(studentModalityId);
    }

    @GetMapping("/examiner-evaluation/{studentModalityId}")
    @PreAuthorize("hasAuthority('PERM_VIEW_EXAMINER_MODALITIES')")
    public ResponseEntity<?> getExaminerEvaluationForModality(@PathVariable Long studentModalityId) {
        return modalityService.getExaminerEvaluationForModality(studentModalityId);
    }

    /**
     * El jurado autenticado obtiene su veredicto sobre documentos MANDATORY de propuesta.
     * Devuelve la decisión individual del jurado, notas y evaluación de propuesta (si aplica).
     * Ruta: GET /modalities/documents/{studentDocumentId}/examiner-proposal-evaluation
     */
    @GetMapping("/documents/{studentDocumentId}/examiner-proposal-evaluation")

    public ResponseEntity<?> getMyProposalEvaluation(@PathVariable Long studentDocumentId) {
        return modalityService.getMyProposalEvaluation(studentDocumentId);
    }

    @GetMapping("/documents/{studentDocumentId}/examiner-final-evaluation")

    public ResponseEntity<?> getMyFinalDocumentEvaluation(@PathVariable Long studentDocumentId) {
        return modalityService.getMyFinalDocumentEvaluation(studentDocumentId);
    }

    // =========================================================================
    // SOLICITUD DE EDICIÓN DE PROPUESTA APROBADA
    // =========================================================================

    /**
     * El estudiante solicita editar un documento MANDATORY que ya fue aprobado por los jurados.
     * Body: { "reason": "motivo justificado de la solicitud (mínimo 20 caracteres)" }
     */
    @PostMapping("/documents/{studentDocumentId}/request-edit")
    public ResponseEntity<?> requestDocumentEdit(
            @PathVariable Long studentDocumentId,
            @RequestBody com.SIGMA.USCO.documents.dto.DocumentEditRequestDTO request) {
        return modalityService.requestDocumentEdit(studentDocumentId, request);
    }

    /**
     * Un jurado vota sobre una solicitud de edición de documento.
     * Sigue la lógica de consenso: ambos jurados primarios deben votar;
     * si hay desacuerdo, el jurado de desempate decide.
     * Body: { "approved": true|false, "resolutionNotes": "..." }
     */
    @PostMapping("/document-edit-requests/{editRequestId}/resolve")
    @PreAuthorize("hasAuthority('PERM_REVIEW_DOCUMENTS')")
    public ResponseEntity<?> resolveDocumentEditRequest(
            @PathVariable Long editRequestId,
            @RequestBody com.SIGMA.USCO.documents.dto.DocumentEditResolutionDTO request) {
        return modalityService.resolveDocumentEditRequest(editRequestId, request);
    }

    /**
     * El jurado autenticado obtiene las solicitudes de edición pendientes de una modalidad.
     * Los jurados primarios ven las PENDING; el jurado de desempate ve las TIEBREAKER_REQUIRED.
     */
    @GetMapping("/{studentModalityId}/document-edit-requests/pending")
    @PreAuthorize("hasAuthority('PERM_VIEW_EXAMINER_MODALITIES')")
    public ResponseEntity<?> getPendingEditRequestsForExaminer(@PathVariable Long studentModalityId) {
        return modalityService.getPendingEditRequestsForExaminer(studentModalityId);
    }

    /**
     * El jurado autenticado obtiene TODAS las solicitudes de edición de documentos
     * de una modalidad (todos los estados: pendiente, desempate, aprobado, rechazado).
     * Incluye información completa: documento, solicitante, votos de cada jurado,
     * si el jurado autenticado ya votó, si puede votar y el resultado final.
     */
    @GetMapping("/{studentModalityId}/document-edit-requests/all")
    @PreAuthorize("hasAuthority('PERM_VIEW_EXAMINER_MODALITIES')")
    public ResponseEntity<?> getAllEditRequestsForExaminer(@PathVariable Long studentModalityId) {
        return modalityService.getAllEditRequestsForExaminer(studentModalityId);
    }

    // =========================================================================
    // ENDPOINTS GET PARA EL ESTUDIANTE – SOLICITUDES DE EDICIÓN
    // =========================================================================

    /**
     * El estudiante autenticado obtiene TODAS sus solicitudes de edición de documentos
     * en todas sus modalidades, con el estado de votación de cada una.
     */
    @GetMapping("/my-document-edit-requests")
    public ResponseEntity<?> getMyDocumentEditRequests() {
        return modalityService.getMyDocumentEditRequests();
    }

    /**
     * El estudiante autenticado obtiene todas las solicitudes de edición asociadas
     * a una modalidad específica (por studentModalityId).
     */
    @GetMapping("/{studentModalityId}/my-document-edit-requests")
    public ResponseEntity<?> getMyDocumentEditRequestsByModality(@PathVariable Long studentModalityId) {
        return modalityService.getMyDocumentEditRequestsByModality(studentModalityId);
    }

    /**
     * El estudiante autenticado obtiene el detalle de una solicitud de edición específica.
     */
    @GetMapping("/document-edit-requests/{editRequestId}")
    public ResponseEntity<?> getDocumentEditRequestDetail(@PathVariable Long editRequestId) {
        return modalityService.getDocumentEditRequestDetail(editRequestId);
    }

    /**
     * Obtiene la lista de jurados (examinadores) asociados a una modalidad específica.
     * Retorna información detallada de cada jurado: ID, nombre, email, tipo (primario 1, primario 2, desempate)
     * y fecha de asignación.
     * Ruta: GET /modalities/{studentModalityId}/examiners
     */
    @GetMapping("/{studentModalityId}/examiners")
    @PreAuthorize( "hasAuthority('PERM_VIEW_EXAMINER_MODALITIES')")
    public ResponseEntity<?> getExaminersForModality(@PathVariable Long studentModalityId) {
        return modalityService.getExaminersForModality(studentModalityId);
    }

    /**
     * Retorna la lista completa de todos los estudiantes que pertenecen al
     * programa académico del comité autenticado, con filtro opcional por nombre.
     *
     * GET /modalities/committee/program-students?studentName=raul
     */
    @GetMapping("/committee/program-students")
    @PreAuthorize("hasAuthority('PERM_STUDENT_LIST')")
    public ResponseEntity<?> getProgramStudentsForCommittee(@RequestParam(required = false) String studentName) {
        return modalityService.getProgramStudentsForCommittee(studentName);
    }

    // =========================================================================
    // GESTIÓN DE DISTINCIONES HONORÍFICAS PROPUESTAS POR JURADOS
    // =========================================================================

    /**
     * Lista todas las modalidades donde los jurados han propuesto unánimemente
     * una distinción honorífica (Meritoria o Laureada) pendiente de revisión
     * por el Comité de Currículo.
     *
     * Incluye los argumentos de cada jurado para que el comité pueda evaluarlos.
     *
     * GET /modalities/committee/pending-distinction-proposals
     */
    @GetMapping("/committee/pending-distinction-proposals")
    @PreAuthorize("hasAuthority('PERM_APPROVE_MODALITY')")
    public ResponseEntity<?> getPendingDistinctionProposals() {
        return modalityService.getPendingDistinctionProposals();
    }

    /**
     * El Comité de Currículo ACEPTA la distinción honorífica propuesta por los jurados.
     * La modalidad pasa a estado GRADED_APPROVED con la distinción confirmada.
     *
     * Body (opcional): { "notes": "Observaciones del comité al aceptar" }
     *
     * POST /modalities/{studentModalityId}/committee/accept-distinction
     */
    @PostMapping("/{studentModalityId}/committee/accept-distinction")
    @PreAuthorize("hasAuthority('PERM_APPROVE_MODALITY')")
    public ResponseEntity<?> acceptDistinctionProposal(
            @PathVariable Long studentModalityId,
            @RequestBody(required = false) Map<String, String> body) {
        String notes = body != null ? body.get("notes") : null;
        return modalityService.acceptDistinctionProposal(studentModalityId, notes);
    }

    /**
     * El Comité de Currículo RECHAZA la distinción honorífica propuesta por los jurados.
     * La modalidad pasa a estado GRADED_APPROVED sin mención especial.
     *
     * Body: { "reason": "Razón del rechazo (obligatorio)" }
     *
     * POST /modalities/{studentModalityId}/committee/reject-distinction
     */
    @PostMapping("/{studentModalityId}/committee/reject-distinction")
    @PreAuthorize("hasAuthority('PERM_APPROVE_MODALITY')")
    public ResponseEntity<?> rejectDistinctionProposal(
            @PathVariable Long studentModalityId,
            @RequestBody Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        return modalityService.rejectDistinctionProposal(studentModalityId, reason);
    }


