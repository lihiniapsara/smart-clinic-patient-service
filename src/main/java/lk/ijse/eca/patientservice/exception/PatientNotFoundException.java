package lk.ijse.eca.patientservice.exception;

public class PatientNotFoundException extends RuntimeException {

    public PatientNotFoundException(String nic) {
        super("Patient with NIC '" + nic + "' not found");
    }
}


