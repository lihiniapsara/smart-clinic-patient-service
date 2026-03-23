package lk.ijse.eca.patientservice.exception;

public class DuplicatePatientException extends RuntimeException {

    public DuplicatePatientException(String nic) {
        super("Patient with NIC '" + nic + "' already exists");
    }
}


