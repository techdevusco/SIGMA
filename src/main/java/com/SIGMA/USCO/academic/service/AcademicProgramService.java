package com.SIGMA.USCO.academic.service;


import com.SIGMA.USCO.academic.dto.ProgramDTO;
import com.SIGMA.USCO.academic.entity.AcademicProgram;
import com.SIGMA.USCO.academic.entity.Faculty;
import com.SIGMA.USCO.academic.repository.AcademicProgramRepository;
import com.SIGMA.USCO.academic.repository.FacultyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AcademicProgramService {

    private final AcademicProgramRepository academicProgramRepository;
    private final FacultyRepository facultyRepository;

    public ProgramDTO createProgram(ProgramDTO request) {

        if (request.getName() == null || request.getName().isBlank()) {
            throw new IllegalArgumentException("El nombre del programa es obligatorio.");
        }

        if (request.getCode() == null || request.getCode().isBlank()) {
            throw new IllegalArgumentException("El código del programa es obligatorio.");
        }

        if (request.getFacultyId() == null) {
            throw new IllegalArgumentException("La facultad es obligatoria.");
        }

        Faculty faculty = facultyRepository.findById(request.getFacultyId())
                .orElseThrow(() -> new IllegalArgumentException("La facultad no existe."));

        if (academicProgramRepository.existsByNameIgnoreCase(request.getName())) {
            throw new IllegalArgumentException("El nombre del programa ya existe.");
        }

        if (academicProgramRepository.existsByCodeIgnoreCase(request.getCode())) {
            throw new IllegalArgumentException("El código del programa ya existe.");
        }

        AcademicProgram program = AcademicProgram.builder()
                .name(request.getName())
                .code(request.getCode().toUpperCase())
                .description(request.getDescription())
                .faculty(faculty)
                .totalCredits(request.getTotalCredits() != null ? request.getTotalCredits() : 0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .active(true)
                .build();

         academicProgramRepository.save(program);

        return ProgramDTO.builder()
                .id(program.getId())
                .name(program.getName())
                .code(program.getCode())
                .description(program.getDescription())
                .facultyId(program.getFaculty().getId())
                .totalCredits(program.getTotalCredits())
                .active(program.isActive())
                .build();
    }

    public ProgramDTO getProgramById(Long programId) {

        AcademicProgram program = academicProgramRepository.findById(programId)
                .orElseThrow(() -> new RuntimeException("Programa académico no encontrado."));

        ProgramDTO programDTO = ProgramDTO.builder()
                .id(program.getId())
                .name(program.getName())
                .code(program.getCode())
                .totalCredits(program.getTotalCredits())
                .active(program.isActive())
                .build();

        return programDTO;

    }

    public List<ProgramDTO> getAllPrograms() {
        List<AcademicProgram> programs = academicProgramRepository.findAll();

        return programs.stream().map(program -> ProgramDTO.builder()
                .id(program.getId())
                .name(program.getName())
                .code(program.getCode())
                .description(program.getDescription())
                .facultyId(program.getFaculty().getId())
                .totalCredits(program.getTotalCredits())
                .active(program.isActive())
                .build()).toList();
    }

    public List<ProgramDTO> getActivePrograms() {

        return facultyRepository.findByActiveTrue()
                .stream()
                .flatMap(faculty -> academicProgramRepository.findByFaculty_IdAndActiveTrue(faculty.getId()).stream())
                .map(program -> ProgramDTO.builder()
                        .id(program.getId())
                        .name(program.getName())
                        .code(program.getCode())
                        .description(program.getDescription())
                        .facultyId(program.getFaculty().getId())
                        .totalCredits(program.getTotalCredits())
                        .active(program.isActive())
                        .build())
                .toList();


    }
    public AcademicProgram updateProgram(Long programId, ProgramDTO request) {

        AcademicProgram program = academicProgramRepository.findById(programId)
                .orElseThrow(() -> new RuntimeException("Programa académico no encontrado."));

        if (request.getName() != null && !request.getName().isBlank()) {

            boolean exists =
                    academicProgramRepository.existsByNameIgnoreCaseAndIdNot(
                            request.getName(),
                            programId
                    );

            if (exists) {
                throw new RuntimeException("Ya existe un programa con ese nombre.");
            }

            program.setName(request.getName().toUpperCase());
        }

        if (request.getCode() != null && !request.getCode().isBlank()) {

            boolean exists =
                    academicProgramRepository.existsByCodeIgnoreCaseAndIdNot(
                            request.getCode(),
                            programId
                    );

            if (exists) {
                throw new RuntimeException("Ya existe un programa con ese código.");
            }

            program.setCode(request.getCode().toUpperCase());
        }

        if (request.getDescription() != null) {
            program.setDescription(request.getDescription());
        }

        if (request.getTotalCredits() != null) {
            program.setTotalCredits(request.getTotalCredits());
        }

        if (request.getFacultyId() != null &&
                !request.getFacultyId().equals(program.getFaculty().getId())) {

            Faculty faculty = facultyRepository.findById(request.getFacultyId())
                    .orElseThrow(() -> new RuntimeException("La facultad no existe."));

            program.setFaculty(faculty);
        }

        program.setUpdatedAt(LocalDateTime.now());

        return academicProgramRepository.save(program);
    }

}
