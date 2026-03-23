package lk.ijse.eca.patientservice.service;

import lk.ijse.eca.patientservice.dto.PatientRequestDTO;
import lk.ijse.eca.patientservice.dto.PatientResponseDTO;

import java.util.List;

public interface PatientService {

    PatientResponseDTO createPatient(PatientRequestDTO dto);

    PatientResponseDTO updatePatient(String nic, PatientRequestDTO dto);

    void deletePatient(String nic);

    PatientResponseDTO getPatient(String nic);

    List<PatientResponseDTO> getAllPatients();

    byte[] getPatientPicture(String nic);
}


