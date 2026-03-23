package lk.ijse.eca.patientservice.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.groups.Default;
import lk.ijse.eca.patientservice.dto.PatientRequestDTO;
import lk.ijse.eca.patientservice.dto.PatientResponseDTO;
import lk.ijse.eca.patientservice.service.PatientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/patients")
@RequiredArgsConstructor
@Slf4j
@Validated
public class PatientController {

    private final PatientService patientService;

    private static final String NIC_REGEXP = "^\\d{9}[vV]$";

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PatientResponseDTO> createPatient(
            @Validated({Default.class, PatientRequestDTO.OnCreate.class}) @ModelAttribute PatientRequestDTO dto) {
        log.info("POST patient - patientId: {}", dto.getNic());
        PatientResponseDTO response = patientService.createPatient(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping(value = "/{nic}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PatientResponseDTO> updatePatient(
            @PathVariable @Pattern(regexp = NIC_REGEXP, message = "Patient ID must be 9 digits followed by V or v") String nic,
            @Valid @ModelAttribute PatientRequestDTO dto) {
        log.info("PUT patient/{}", nic);
        PatientResponseDTO response = patientService.updatePatient(nic, dto);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{nic}")
    public ResponseEntity<Void> deletePatient(
            @PathVariable @Pattern(regexp = NIC_REGEXP, message = "Patient ID must be 9 digits followed by V or v") String nic) {
        log.info("DELETE patient/{}", nic);
        patientService.deletePatient(nic);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{nic}")
    public ResponseEntity<PatientResponseDTO> getPatient(
            @PathVariable @Pattern(regexp = NIC_REGEXP, message = "Patient ID must be 9 digits followed by V or v") String nic) {
        log.info("GET patient/{}", nic);
        PatientResponseDTO response = patientService.getPatient(nic);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<PatientResponseDTO>> getAllPatients() {
        log.info("GET patients");
        List<PatientResponseDTO> patients = patientService.getAllPatients();
        return ResponseEntity.ok(patients);
    }

    @GetMapping("/{nic}/picture")
    public ResponseEntity<byte[]> getPatientPicture(
            @PathVariable @Pattern(regexp = NIC_REGEXP, message = "Patient ID must be 9 digits followed by V or v") String nic) {
        log.info("GET patient/{}/picture", nic);
        byte[] picture = patientService.getPatientPicture(nic);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .body(picture);
    }
}


