package lk.ijse.eca.patientservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class PatientResponseDTO {

    private String nic;
    private String name;
    private String address;
    private String mobile;
    private String email;
    private String picture;
}


