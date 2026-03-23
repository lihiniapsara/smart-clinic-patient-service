package lk.ijse.eca.patientservice.service.impl;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import lk.ijse.eca.patientservice.dto.PatientRequestDTO;
import lk.ijse.eca.patientservice.dto.PatientResponseDTO;
import lk.ijse.eca.patientservice.entity.Patient;
import lk.ijse.eca.patientservice.exception.DuplicatePatientException;
import lk.ijse.eca.patientservice.exception.FileOperationException;
import lk.ijse.eca.patientservice.exception.PatientNotFoundException;
import lk.ijse.eca.patientservice.mapper.PatientMapper;
import lk.ijse.eca.patientservice.repository.PatientRepository;
import lk.ijse.eca.patientservice.service.PatientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PatientServiceImpl implements PatientService {

    private static final String STORAGE_PROVIDER_GCS = "gcs";

    private final PatientRepository patientRepository;
    private final PatientMapper patientMapper;

    @Value("${app.storage.provider:local}")
    private String storageProvider;

    @Value("${app.storage.path}")
    private String storagePathStr;

    @Value("${app.storage.gcs.bucket:}")
    private String gcsBucketName;

    @Value("${app.storage.gcs.project-id:}")
    private String gcsProjectId;

    private Path storagePath;
    private Storage gcsStorage;

    /**
     * Creates a new patient.
     *
     * Transaction strategy:
     *  1. Persist patient record to DB (JPA defers the INSERT until flush/commit).
     *  2. Write picture file to disk (immediate).
     *  3. If the file write fails an exception is thrown, which causes
     *     @Transactional to roll back the DB INSERT — no orphaned record.
     *  4. If the file write succeeds the method returns normally and
     *     @Transactional commits both the record and the file atomically.
     */
    @Override
    @Transactional
    public PatientResponseDTO createPatient(PatientRequestDTO dto) {
        log.debug("Creating patient with NIC: {}", dto.getNic());

        if (patientRepository.existsById(dto.getNic())) {
            log.warn("Duplicate NIC detected: {}", dto.getNic());
            throw new DuplicatePatientException(dto.getNic());
        }

        String pictureId = UUID.randomUUID().toString();

        Patient patient = patientMapper.toEntity(dto);
        patient.setPicture(pictureId);
//        patient.setAddress(patient.getAddress() + ", Sri Lanka");

        // DB operation first (deferred) — rolls back if file save below throws
        patientRepository.save(patient);
        log.debug("Patient persisted to DB: {}", dto.getNic());

        // Immediate file operation — failure triggers @Transactional rollback
        savePicture(pictureId, dto.getPicture());

        log.info("Patient created successfully: {}", dto.getNic());
        return patientMapper.toResponseDto(patient);
    }

    /**
     * Updates an existing patient.
     *
     * Transaction strategy:
     *  - If a new picture is supplied:
     *    1. Update DB record with new picture UUID (deferred).
     *    2. Write the new picture file (immediate).
     *    3. Failure at step 2 rolls back step 1 — old picture UUID stays in DB.
     *    4. On success, the old picture file is deleted (best-effort: a warning is
     *       logged on failure, but the transaction is NOT rolled back because DB and
     *       new file are already consistent).
     *  - If no new picture is supplied, only DB fields are updated.
     */
    @Override
    @Transactional
    public PatientResponseDTO updatePatient(String nic, PatientRequestDTO dto) {
        log.debug("Updating patient with NIC: {}", nic);

        Patient patient = patientRepository.findById(nic)
                .orElseThrow(() -> {
                    log.warn("Patient not found for update: {}", nic);
                    return new PatientNotFoundException(nic);
                });

        String oldPictureId = patient.getPicture();
        boolean pictureChanged = dto.getPicture() != null && !dto.getPicture().isEmpty();
        String newPictureId = pictureChanged ? UUID.randomUUID().toString() : oldPictureId;

        patientMapper.updateEntity(dto, patient);
        patient.setPicture(newPictureId);

        // DB update (deferred) — rolls back if new file save below throws
        patientRepository.save(patient);
        log.debug("Patient updated in DB: {}", nic);

        if (pictureChanged) {
            // Save new picture — failure triggers @Transactional rollback
            savePicture(newPictureId, dto.getPicture());
            // Remove old picture — best-effort; DB and new file are already consistent
            tryDeletePicture(oldPictureId);
        }

        log.info("Patient updated successfully: {}", nic);
        return patientMapper.toResponseDto(patient);
    }

    /**
     * Deletes a patient.
     *
     * Transaction strategy:
     *  1. Remove patient record from DB (JPA defers the DELETE until flush/commit).
     *  2. Delete picture file from disk (immediate).
     *  3. If the file delete fails an exception is thrown, which causes
     *     @Transactional to roll back the DB DELETE — neither the record
     *     nor the file is removed.
     *  4. If the file delete succeeds the method returns normally and
     *     @Transactional commits, removing the record from the DB.
     */
    @Override
    @Transactional
    public void deletePatient(String nic) {
        log.debug("Deleting patient with NIC: {}", nic);

        Patient patient = patientRepository.findById(nic)
                .orElseThrow(() -> {
                    log.warn("Patient not found for deletion: {}", nic);
                    return new PatientNotFoundException(nic);
                });

        String pictureId = patient.getPicture();

        // DB deletion (deferred) — rolls back if file delete below throws
        patientRepository.delete(patient);
        log.debug("Patient marked for deletion in DB: {}", nic);

        // Immediate file deletion — failure triggers @Transactional rollback
        deletePicture(pictureId);

        log.info("Patient deleted successfully: {}", nic);
    }

    @Override
    @Transactional(readOnly = true)
    public PatientResponseDTO getPatient(String nic) {
        log.debug("Fetching patient with NIC: {}", nic);
        return patientRepository.findById(nic)
            .map(patientMapper::toResponseDto)
                .orElseThrow(() -> {
                    log.warn("Patient not found: {}", nic);
                    return new PatientNotFoundException(nic);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public List<PatientResponseDTO> getAllPatients() {
        log.debug("Fetching all patients");
        List<PatientResponseDTO> patients = patientRepository.findAll()
                .stream()
            .map(patientMapper::toResponseDto)
                .collect(Collectors.toList());
        log.debug("Fetched {} patients", patients.size());
        return patients;
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] getPatientPicture(String nic) {
        log.debug("Fetching picture for patient NIC: {}", nic);
        Patient patient = patientRepository.findById(nic)
                .orElseThrow(() -> {
                    log.warn("Patient not found: {}", nic);
                    return new PatientNotFoundException(nic);
                });

        return readPicture(patient.getPicture());
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private Path storagePath() {
        if (storagePath == null) {
            storagePath = Paths.get(storagePathStr);
        }
        try {
            Files.createDirectories(storagePath);
        } catch (IOException e) {
            throw new FileOperationException(
                    "Failed to create storage directory: " + storagePath.toAbsolutePath(), e);
        }
        return storagePath;
    }

    private void savePicture(String pictureId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FileOperationException("Picture file must not be empty");
        }

        try {
            byte[] bytes = file.getBytes();
            if (isGcsProvider()) {
                BlobId blobId = BlobId.of(requiredGcsBucket(), pictureId);
                BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType(file.getContentType()).build();
                gcsStorage().create(blobInfo, bytes);
                log.debug("Picture saved to GCS: bucket={}, object={}", requiredGcsBucket(), pictureId);
                return;
            }

            Path filePath = storagePath().resolve(pictureId);
            Files.write(filePath, bytes);
            log.debug("Picture saved locally: {}", filePath);
        } catch (IOException e) {
            log.error("Failed to save picture: {}", pictureId, e);
            throw new FileOperationException("Failed to save picture file: " + pictureId, e);
        }
    }

    private void deletePicture(String pictureId) {
        if (isGcsProvider()) {
            boolean deleted = gcsStorage().delete(BlobId.of(requiredGcsBucket(), pictureId));
            if (deleted) {
                log.debug("Picture deleted from GCS: bucket={}, object={}", requiredGcsBucket(), pictureId);
            } else {
                log.warn("Picture object not found in GCS (already removed?): bucket={}, object={}",
                        requiredGcsBucket(), pictureId);
            }
            return;
        }

        Path filePath = storagePath().resolve(pictureId);
        try {
            boolean deleted = Files.deleteIfExists(filePath);
            if (deleted) {
                log.debug("Picture deleted: {}", filePath);
            } else {
                log.warn("Picture file not found on disk (already removed?): {}", filePath);
            }
        } catch (IOException e) {
            log.error("Failed to delete picture: {}", filePath, e);
            throw new FileOperationException("Failed to delete picture file: " + pictureId, e);
        }
    }

    private void tryDeletePicture(String pictureId) {
        try {
            deletePicture(pictureId);
        } catch (FileOperationException e) {
            log.warn("Could not delete old picture file '{}'. Manual cleanup may be required.", pictureId);
        }
    }

    private byte[] readPicture(String pictureId) {
        if (isGcsProvider()) {
            Blob blob = gcsStorage().get(BlobId.of(requiredGcsBucket(), pictureId));
            if (blob == null || !blob.exists()) {
                throw new FileOperationException("Picture not found in GCS for object: " + pictureId);
            }
            return blob.getContent();
        }

        Path filePath = storagePath().resolve(pictureId);
        try {
            return Files.readAllBytes(filePath);
        } catch (IOException e) {
            log.error("Failed to read picture from local storage: {}", filePath, e);
            throw new FileOperationException("Failed to read picture file: " + pictureId, e);
        }
    }

    private boolean isGcsProvider() {
        return STORAGE_PROVIDER_GCS.equalsIgnoreCase(storageProvider);
    }

    private String requiredGcsBucket() {
        if (gcsBucketName == null || gcsBucketName.isBlank()) {
            throw new FileOperationException("GCS bucket name is required when app.storage.provider=gcs");
        }
        return gcsBucketName;
    }

    private Storage gcsStorage() {
        if (gcsStorage != null) {
            return gcsStorage;
        }

        StorageOptions.Builder builder = StorageOptions.newBuilder();
        if (gcsProjectId != null && !gcsProjectId.isBlank()) {
            builder.setProjectId(gcsProjectId);
        }
        gcsStorage = builder.build().getService();
        return gcsStorage;
    }

}


