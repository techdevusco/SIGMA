package com.SIGMA.USCO.notifications.listeners;

import com.SIGMA.USCO.Modalities.Entity.StudentModality;
import com.SIGMA.USCO.Modalities.Entity.enums.ModalityProcessStatus;
import com.SIGMA.USCO.Modalities.Repository.StudentModalityRepository;
import com.SIGMA.USCO.Users.Entity.User;
import com.SIGMA.USCO.Users.repository.UserRepository;
import com.SIGMA.USCO.notifications.entity.Notification;
import com.SIGMA.USCO.notifications.entity.enums.NotificationRecipientType;
import com.SIGMA.USCO.notifications.entity.enums.NotificationType;
import com.SIGMA.USCO.notifications.event.DefenseReadyByDirectorEvent;
import com.SIGMA.USCO.notifications.event.DefenseScheduledEvent;
import com.SIGMA.USCO.notifications.service.NotificationDispatcherService;
import com.SIGMA.USCO.notifications.repository.NotificationRepository;
import com.SIGMA.USCO.Modalities.Entity.DefenseExaminer;
import com.SIGMA.USCO.Modalities.Repository.DefenseExaminerRepository;
import com.SIGMA.USCO.notifications.event.DocumentEditRequestedEvent;
import com.SIGMA.USCO.notifications.event.DocumentEditResolvedEvent;
import com.SIGMA.USCO.notifications.event.DefenseReadyByDirectorEvent;
import com.SIGMA.USCO.notifications.event.DefenseScheduledEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import com.SIGMA.USCO.Modalities.Entity.StudentModalityMember;
import com.SIGMA.USCO.Modalities.Entity.enums.MemberStatus;
import com.SIGMA.USCO.Modalities.Repository.StudentModalityMemberRepository;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExaminerNotificationListener {

    private final DefenseExaminerRepository defenseExaminerRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationDispatcherService dispatcher;
    private final UserRepository userRepository;
    private final StudentModalityRepository studentModalityRepository;
    private final StudentModalityMemberRepository studentModalityMemberRepository;

    @Async("notificationTaskExecutor")
    public void notifyExaminersAssignment(Long studentModalityId) {
        StudentModality modality = studentModalityRepository.findById(studentModalityId)
                .orElseThrow(() -> new RuntimeException("Modalidad no encontrada"));

        List<DefenseExaminer> examiners = defenseExaminerRepository.findByStudentModalityId(studentModalityId);

        // Obtener todos los miembros ACTIVOS de la modalidad
        List<StudentModalityMember> activeMembers = studentModalityMemberRepository
                .findByStudentModalityIdAndStatus(studentModalityId, MemberStatus.ACTIVE);

        String studentsString = activeMembers.isEmpty()
                ? (modality.getLeader() != null
                        ? modality.getLeader().getName() + " " + modality.getLeader().getLastName()
                                + " (" + modality.getLeader().getEmail() + ")"
                        : "-")
                : activeMembers.stream()
                        .map(m -> m.getStudent().getName() + " " + m.getStudent().getLastName()
                                + " (" + m.getStudent().getEmail() + ")")
                        .collect(Collectors.joining("\n                        "));

        String directorName = modality.getProjectDirector() != null
                ? modality.getProjectDirector().getName() + " " + modality.getProjectDirector().getLastName()
                : "No asignado";

        String programName = modality.getProgramDegreeModality().getAcademicProgram().getName();
        String facultyName = modality.getProgramDegreeModality().getAcademicProgram().getFaculty() != null
                ? modality.getProgramDegreeModality().getAcademicProgram().getFaculty().getName()
                : "";

        String modalityName = modality.getProgramDegreeModality().getDegreeModality().getName();

        for (DefenseExaminer examinerAssignment : examiners) {
            User examiner = examinerAssignment.getExaminer();

            String examinerRoleLabel = switch (examinerAssignment.getExaminerType()) {
                case PRIMARY_EXAMINER_1, PRIMARY_EXAMINER_2 -> "Jurado Principal";
                case TIEBREAKER_EXAMINER -> "Jurado de Desempate";
            };

            String subject = "Designación oficial como Jurado Evaluador – Modalidad de Grado";

            String message = """
                    Estimado/a %s %s,

                    Reciba un cordial saludo de parte del Sistema de Gestión Académica de la Universidad Surcolombiana.

                    Por medio de la presente, le informamos que ha sido designado/a oficialmente como **%s** en el proceso de evaluación de la siguiente modalidad de grado:

                    ───────────────────────────────
                    INFORMACIÓN DE LA MODALIDAD
                    ───────────────────────────────
                    Modalidad de grado:
                    "%s"

                    Programa académico:
                    %s

                    Facultad:
                    %s

                    ───────────────────────────────
                    ESTUDIANTE(S) ASOCIADO(S)
                    ───────────────────────────────
                    %s

                    ───────────────────────────────
                    DIRECTOR DE PROYECTO
                    ───────────────────────────────
                    %s

                    ───────────────────────────────
                    FECHA DE ASIGNACIÓN
                    ───────────────────────────────
                    %s

                    ───────────────────────────────
                    RESPONSABILIDADES
                    ───────────────────────────────
                    En su calidad de jurado evaluador, le solicitamos:

                    1. Revisar y evaluar la documentación académica asociada a la modalidad en el sistema.
                    2. Verificar el cumplimiento de los requisitos establecidos por el programa académico.
                    3. Emitir su concepto evaluativo conforme a la normativa institucional vigente.

                    Para acceder a la información completa de la modalidad y gestionar sus responsabilidades como jurado, le invitamos a ingresar a la plataforma.

                    Agradecemos su disposición y valioso aporte al proceso académico.

                    Cordialmente,
                    Sistema de Gestión Académica
                    Universidad Surcolombiana
                    """.formatted(
                    examiner.getName(),
                    examiner.getLastName(),
                    examinerRoleLabel,
                    modalityName,
                    programName,
                    facultyName,
                    studentsString,
                    directorName,
                    LocalDateTime.now().toLocalDate().toString()
            );

            Notification notification = Notification.builder()
                    .type(NotificationType.EXAMINER_ASSIGNED)
                    .recipientType(NotificationRecipientType.EXAMINER)
                    .recipient(examiner)
                    .triggeredBy(null)
                    .studentModality(modality)
                    .subject(subject)
                    .message(message)
                    .createdAt(LocalDateTime.now())
                    .build();
            notificationRepository.save(notification);
            dispatcher.dispatch(notification);
        }

        // ── Construir resumen de jurados asignados para el mensaje de estudiantes y director ──
        String examinersListForOthers = examiners.stream()
                .map(e -> {
                    String roleLabel = switch (e.getExaminerType()) {
                        case PRIMARY_EXAMINER_1, PRIMARY_EXAMINER_2 -> "Jurado Principal";
                        case TIEBREAKER_EXAMINER -> "Jurado de Desempate";
                    };
                    return "- " + e.getExaminer().getName() + " " + e.getExaminer().getLastName()
                            + " (" + roleLabel + ")";
                })
                .collect(Collectors.joining("\n"));

        // ── Notificar a todos los estudiantes activos de la modalidad ──
        List<User> studentsToNotify = activeMembers.isEmpty()
                ? (modality.getLeader() != null ? List.of(modality.getLeader()) : List.of())
                : activeMembers.stream().map(StudentModalityMember::getStudent).toList();

        for (User student : studentsToNotify) {
            String studentSubject = "Jurados asignados a tu modalidad de grado – SIGMA";
            String studentMessage = """
                    Estimado/a %s,

                    Reciba un cordial saludo de parte del Sistema de Gestión Académica de la Universidad Surcolombiana.

                    Le informamos que el Comité de Currículo del programa académico ha designado oficialmente los jurados evaluadores para su modalidad de grado:

                    ───────────────────────────────
                    INFORMACIÓN DE LA MODALIDAD
                    ───────────────────────────────
                    Modalidad de grado:
                    "%s"

                    Programa académico:
                    %s

                    Facultad:
                    %s

                    ───────────────────────────────
                    DIRECTOR DE PROYECTO
                    ───────────────────────────────
                    %s

                    ───────────────────────────────
                    JURADOS ASIGNADOS
                    ───────────────────────────────
                    %s

                    ───────────────────────────────
                    FECHA DE ASIGNACIÓN
                    ───────────────────────────────
                    %s

                    ───────────────────────────────
                    PRÓXIMOS PASOS
                    ───────────────────────────────
                    Los jurados designados procederán a revisar y evaluar la documentación académica asociada a su modalidad. Le recomendamos:

                    1. Asegurarse de que todos los documentos requeridos estén correctamente cargados en el sistema.
                    2. Mantenerse atento/a a las notificaciones del sistema, ya que los jurados podrán solicitar correcciones o emitir conceptos sobre la documentación.
                    3. Ante cualquier duda, comunicarse oportunamente con su Director de Proyecto.

                    Esta notificación se genera automáticamente como parte del procedimiento institucional de asignación de jurados.

                    Cordialmente,
                    Sistema de Gestión Académica
                    Universidad Surcolombiana
                    """.formatted(
                    student.getName(),
                    modalityName,
                    programName,
                    facultyName,
                    directorName,
                    examinersListForOthers.isBlank() ? "No asignados aún" : examinersListForOthers,
                    LocalDateTime.now().toLocalDate().toString()
            );

            Notification studentNotification = Notification.builder()
                    .type(NotificationType.EXAMINER_ASSIGNED)
                    .recipientType(NotificationRecipientType.STUDENT)
                    .recipient(student)
                    .triggeredBy(null)
                    .studentModality(modality)
                    .subject(studentSubject)
                    .message(studentMessage)
                    .createdAt(LocalDateTime.now())
                    .build();
            notificationRepository.save(studentNotification);
            dispatcher.dispatch(studentNotification);
        }

        // ── Notificar al director de proyecto si está asignado ──
        User director = modality.getProjectDirector();
        if (director != null) {
            String directorSubject = "Jurados asignados a modalidad bajo su dirección – SIGMA";
            String directorMessage = """
                    Estimado/a %s,

                    Reciba un cordial saludo de parte del Sistema de Gestión Académica de la Universidad Surcolombiana.

                    Le informamos que el Comité de Currículo del programa académico ha designado oficialmente los jurados evaluadores para la siguiente modalidad de grado que se encuentra bajo su dirección:

                    ───────────────────────────────
                    INFORMACIÓN DE LA MODALIDAD
                    ───────────────────────────────
                    Modalidad de grado:
                    "%s"

                    Programa académico:
                    %s

                    Facultad:
                    %s

                    ───────────────────────────────
                    ESTUDIANTE(S) ASOCIADO(S)
                    ───────────────────────────────
                    %s

                    ───────────────────────────────
                    JURADOS ASIGNADOS
                    ───────────────────────────────
                    %s

                    ───────────────────────────────
                    FECHA DE ASIGNACIÓN
                    ───────────────────────────────
                    %s

                    ───────────────────────────────
                    INFORMACIÓN PARA EL DIRECTOR
                    ───────────────────────────────
                    Los jurados asignados iniciarán el proceso de revisión y evaluación de la documentación académica de la modalidad. Como Director/a de Proyecto, le recomendamos:

                    1. Coordinar con los estudiantes la correcta presentación de todos los documentos requeridos.
                    2. Estar disponible para atender las observaciones que los jurados puedan generar durante el proceso.
                    3. Una vez los jurados aprueben la documentación, podrá proceder con la programación formal de la sustentación a través de la plataforma SIGMA.

                    Esta notificación se genera automáticamente como parte del procedimiento institucional de asignación de jurados.

                    Cordialmente,
                    Sistema de Gestión Académica
                    Universidad Surcolombiana
                    """.formatted(
                    director.getName() + " " + director.getLastName(),
                    modalityName,
                    programName,
                    facultyName,
                    studentsString,
                    examinersListForOthers.isBlank() ? "No asignados aún" : examinersListForOthers,
                    LocalDateTime.now().toLocalDate().toString()
            );

            Notification directorNotification = Notification.builder()
                    .type(NotificationType.EXAMINER_ASSIGNED)
                    .recipientType(NotificationRecipientType.PROJECT_DIRECTOR)
                    .recipient(director)
                    .triggeredBy(null)
                    .studentModality(modality)
                    .subject(directorSubject)
                    .message(directorMessage)
                    .createdAt(LocalDateTime.now())
                    .build();
            notificationRepository.save(directorNotification);
            dispatcher.dispatch(directorNotification);
        }
    }

    @EventListener
    public void handleDefenseReadyByDirectorEvent(DefenseReadyByDirectorEvent event) {
        StudentModality modality = studentModalityRepository.findById(event.getStudentModalityId())
                .orElseThrow(() -> new RuntimeException("Modalidad no encontrada"));

        User examiner = userRepository.findById(event.getExaminerId())
                .orElseThrow(() -> new RuntimeException("Jurado no encontrado"));


        List<StudentModalityMember> members = studentModalityMemberRepository.findByStudentModalityIdAndStatus(modality.getId(), MemberStatus.ACTIVE);
        String miembros = members.stream()
            .map(m -> m.getStudent().getName() + " " + m.getStudent().getLastName() + " (" + m.getStudent().getEmail() + ")")
            .collect(Collectors.joining(", "));

        String subject = "Notificación de modalidad lista para sustentación";

        String message = """
            Estimado/a %s %s,
            
            Reciba un cordial saludo.
            
            Le informamos que la siguiente modalidad de grado ha sido marcada oficialmente como lista para sustentación por parte del Director de Proyecto:
            
            Estudiantes:
            %s
            
            Modalidad de grado:
            "%s"
            
            A partir de este momento, el proceso se encuentra disponible para su revisión en calidad de jurado evaluador.
            
            Le solicitamos ingresar a la plataforma institucional para:
            - Revisar la documentación final presentada.
            - Verificar el cumplimiento de los requisitos académicos.
            - Continuar con las etapas correspondientes al proceso de sustentación.
            
            Agradecemos su disposición y compromiso con el proceso evaluativo.
            
            Cordialmente,
            Sistema de Gestión Académica
            """.formatted(
                examiner.getName(),
                examiner.getLastName(),
                miembros,
                modality.getProgramDegreeModality().getDegreeModality().getName()
        );

        Notification notification = Notification.builder()
                .type(NotificationType.READY_FOR_DEFENSE_REQUESTED)
                .recipientType(NotificationRecipientType.EXAMINER)
                .recipient(examiner)
                .triggeredBy(null)
                .studentModality(modality)
                .subject(subject)
                .message(message)
                .createdAt(LocalDateTime.now())
                .build();

        notificationRepository.save(notification);
        dispatcher.dispatch(notification);
    }

    @EventListener
    public void handleExaminerFinalReviewCompletedEvent(
            com.SIGMA.USCO.notifications.event.ExaminerFinalReviewCompletedEvent event) {

        StudentModality modality = studentModalityRepository.findById(event.getStudentModalityId())
                .orElseThrow(() -> new RuntimeException("Modalidad no encontrada"));

        User director = userRepository.findById(event.getProjectDirectorId())
                .orElseThrow(() -> new RuntimeException("Director de proyecto no encontrado"));

        // Obtener todos los miembros ACTIVOS de la modalidad
        List<StudentModalityMember> members = studentModalityMemberRepository.findByStudentModalityIdAndStatus(modality.getId(), MemberStatus.ACTIVE);
        String miembros = members.stream()
            .map(m -> m.getStudent().getName() + " " + m.getStudent().getLastName() + " (" + m.getStudent().getEmail() + ")")
            .collect(Collectors.joining(", "));

        String subject = "Aprobación final de documentos – Puede programar la sustentación";

        String message = """
            Estimado/a %s %s,
            
            Reciba un cordial saludo.
            
            Le informamos que el jurado evaluador ha aprobado la totalidad de los documentos requeridos para la siguiente modalidad de grado:
            
            Estudiantes:
            %s
            
            Modalidad de grado:
            "%s"
            
            Con esta aprobación, el proceso académico cumple los requisitos necesarios para avanzar a la etapa de sustentación.
            
            En su calidad de Director/a de Proyecto, ahora puede:
            - Programar la fecha y hora de la sustentación.
            - Definir el lugar correspondiente.
            - Continuar con la gestión formal del cierre del proceso.
            
            Le invitamos a ingresar al sistema para realizar la programación y dar continuidad al procedimiento institucional.
            
            Cordialmente,
            Sistema de Gestión Académica
            """.formatted(
                director.getName(),
                director.getLastName(),
                miembros,
                modality.getProgramDegreeModality().getDegreeModality().getName()
        );

        Notification notification = Notification.builder()
                .type(NotificationType.FINAL_APPROVED)
                .recipientType(NotificationRecipientType.PROJECT_DIRECTOR)
                .recipient(director)
                .triggeredBy(null)
                .studentModality(modality)
                .subject(subject)
                .message(message)
                .createdAt(LocalDateTime.now())
                .build();

        notificationRepository.save(notification);
        dispatcher.dispatch(notification);
    }


    @EventListener
    public void handleDefenseScheduled(DefenseScheduledEvent event) {
        StudentModality modality = studentModalityRepository.findById(event.getStudentModalityId())
                .orElseThrow(() -> new RuntimeException("Modalidad no encontrada"));

        List<DefenseExaminer> examiners = defenseExaminerRepository.findByStudentModalityId(event.getStudentModalityId());
        for (DefenseExaminer examinerAssignment : examiners) {
            User examiner = examinerAssignment.getExaminer();
            String subject = "Sustentación programada – Modalidad de Grado";
            String message = String.format(
                """
                          Estimado/a %s %s,
                        
                                             Reciba un cordial saludo.
                        
                                             Le informamos que ha sido programada la sustentación de la siguiente modalidad de grado:
                        
                                             Modalidad:
                                             "%s"
                        
                                             Fecha y hora:
                                             %s
                        
                                             Lugar:
                                             %s
                        
                                             Director/a asignado/a:
                                             %s
                        
                                             Estudiantes asociados:
                                             %s
                        
                                             En su calidad de jurado evaluador, le solicitamos ingresar al sistema SIGMA para revisar la documentación final, verificar los lineamientos académicos y prepararse para la jornada de sustentación.
                        
                                             Agradecemos su compromiso con el proceso evaluativo.
                        
                                             Cordialmente,
                                             Sistema de Gestión Académica
                """,
                examiner.getName(),
                examiner.getLastName(),
                modality.getProgramDegreeModality().getDegreeModality().getName(),
                event.getDefenseDate(),
                event.getDefenseLocation(),
                modality.getProjectDirector() != null ? modality.getProjectDirector().getName() + " " + modality.getProjectDirector().getLastName() : "No asignado",
                modality.getMembers() != null && !modality.getMembers().isEmpty() ? modality.getMembers().stream().map(member -> member.getStudent().getName() + " " + member.getStudent().getLastName()).reduce((a, b) -> a + ", " + b).orElse("") : modality.getLeader().getName() + " " + modality.getLeader().getLastName()
            );

            Notification notification = Notification.builder()
                    .type(NotificationType.DEFENSE_SCHEDULED)
                    .recipientType(NotificationRecipientType.EXAMINER)
                    .recipient(examiner)
                    .triggeredBy(null)
                    .studentModality(modality)
                    .subject(subject)
                    .message(message)
                    .createdAt(LocalDateTime.now())
                    .build();
            notificationRepository.save(notification);
            dispatcher.dispatch(notification);
        }

        // Notificar a todos los estudiantes asociados
        List<User> students;
        if (modality.getMembers() != null && !modality.getMembers().isEmpty()) {
            students = modality.getMembers().stream().map(member -> member.getStudent()).toList();
        } else {
            students = List.of(modality.getLeader());
        }
        for (User student : students) {
            String subject = "Sustentación programada – Modalidad de Grado";
            String message = String.format(
                """
                         Estimado/a %s,

                                Reciba un cordial saludo.

                                Le informamos que la sustentación de su modalidad de grado ha sido programada con los siguientes detalles:

                                Modalidad:
                                "%s"

                                Fecha y hora:
                                %s

                                Lugar:
                                %s

                                Director/a asignado/a:
                                %s

                                Le recomendamos presentarse con la debida antelación y cumplir con los lineamientos académicos establecidos para la sustentación.

                                Puede consultar la información completa en el sistema SIGMA.

                                Cordialmente,
                                Sistema de Gestión Académica
                """,
                student.getName(),
                modality.getProgramDegreeModality().getDegreeModality().getName(),
                event.getDefenseDate(),
                event.getDefenseLocation(),
                modality.getProjectDirector() != null ? modality.getProjectDirector().getName() + " " + modality.getProjectDirector().getLastName() : "No asignado"
            );

            Notification notification = Notification.builder()
                    .type(NotificationType.DEFENSE_SCHEDULED)
                    .recipientType(NotificationRecipientType.STUDENT)
                    .recipient(student)
                    .triggeredBy(null)
                    .studentModality(modality)
                    .subject(subject)
                    .message(message)
                    .createdAt(LocalDateTime.now())
                    .build();
            notificationRepository.save(notification);
            dispatcher.dispatch(notification);
        }
    }

    /**
     * Notifica a los jurados asignados a la modalidad cuando un estudiante solicita
     * editar un documento previamente aprobado.
     */
    @EventListener
    public void onDocumentEditRequested(DocumentEditRequestedEvent event) {
        StudentModality modality = studentModalityRepository.findById(event.getStudentModalityId())
                .orElseThrow(() -> new RuntimeException("Modalidad no encontrada"));

        List<DefenseExaminer> examiners = defenseExaminerRepository.findByStudentModalityId(event.getStudentModalityId());

        String studentNames = studentModalityMemberRepository
                .findByStudentModalityIdAndStatus(modality.getId(), MemberStatus.ACTIVE)
                .stream()
                .map(m -> m.getStudent().getName() + " " + m.getStudent().getLastName())
                .collect(Collectors.joining(", "));

        String subject = "Solicitud de edición de documento aprobado – Modalidad de grado";

        for (DefenseExaminer examinerAssignment : examiners) {
            User examiner = examinerAssignment.getExaminer();
            String message = """
                    Estimado/a %s %s,

                    Reciba un cordial saludo.

                    Le informamos que el/los estudiante(s) de la siguiente modalidad de grado
                    ha(n) solicitado editar un documento que ya fue previamente aprobado:

                    ───────────────────────────────
                    INFORMACIÓN DE LA MODALIDAD
                    ───────────────────────────────
                    Modalidad de grado:
                    "%s"

                    Programa académico:
                    "%s"

                    Estudiante(s):
                    %s

                    ───────────────────────────────
                    DOCUMENTO
                    ───────────────────────────────
                    Nombre del documento:
                    "%s"

                    ID de solicitud: %d

                    ───────────────────────────────
                    MOTIVO DE LA SOLICITUD
                    ───────────────────────────────
                    %s

                    ───────────────────────────────
                    ACCIÓN REQUERIDA
                    ───────────────────────────────
                    Como jurado evaluador, le solicitamos ingresar al sistema SIGMA
                    y aprobar o rechazar la solicitud de edición del estudiante,
                    según su criterio académico.

                    Cordialmente,
                    Sistema de Gestión Académica – SIGMA
                    """.formatted(
                    examiner.getName(),
                    examiner.getLastName(),
                    modality.getProgramDegreeModality().getDegreeModality().getName(),
                    modality.getProgramDegreeModality().getAcademicProgram().getName(),
                    studentNames.isBlank() ? "No registrado" : studentNames,
                    event.getDocumentName(),
                    event.getEditRequestId(),
                    event.getReason()
            );

            Notification notification = Notification.builder()
                    .type(NotificationType.DOCUMENT_EDIT_REQUESTED)
                    .recipientType(NotificationRecipientType.EXAMINER)
                    .recipient(examiner)
                    .triggeredBy(null)
                    .studentModality(modality)
                    .subject(subject)
                    .message(message)
                    .createdAt(LocalDateTime.now())
                    .build();
            notificationRepository.save(notification);
            dispatcher.dispatch(notification);
        }
    }
}
