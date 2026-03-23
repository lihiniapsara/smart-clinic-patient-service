# Patient-Service

A microservice responsible for managing patient records, including profile information and profile pictures. It exposes a REST API consumed by the API Gateway.

## Tech Stack

| Technology | Details |
|---|---|
| Java | 25 |
| Spring Boot | 4.0.3 |
| Spring Cloud | 2025.1.0 |
| Spring Data JPA | Persistence layer |
| PostgreSQL | Relational database (port `12500`) |
| MapStruct | DTO <-> Entity mapping |
| Spring Validation | Bean validation |
| Eureka Client | Service discovery |
| Config Client | Externalized configuration |

## Service Details

| Property | Value |
|---|---|
| Port | `8000` |
| Artifact ID | `Patient-Service` |
| Group ID | `lk.ijse.eca` |
| Base Path | `/api/v1/patients` |
| Picture Storage | `local path or GCS bucket (configurable)` |

## Cloud Storage Configuration

Patient pictures can be stored either in local disk (default) or Google Cloud Storage.

Set these environment variables in GCP for bucket storage:

```env
PATIENT_STORAGE_PROVIDER=gcs
GCS_BUCKET_NAME=<your_bucket_name>
GCP_PROJECT_ID=<your_project_id>
```

## API Endpoints

| Method | Path | Description | Content-Type |
|---|---|---|---|
| `POST` | `/api/v1/patients` | Create a patient | `multipart/form-data` |
| `GET` | `/api/v1/patients` | Get all patients | — |
| `GET` | `/api/v1/patients/{nic}` | Get a patient by ID | — |
| `PUT` | `/api/v1/patients/{nic}` | Update a patient | `multipart/form-data` |
| `DELETE` | `/api/v1/patients/{nic}` | Delete a patient | — |
| `GET` | `/api/v1/patients/{nic}/picture` | Get patient profile picture | — |

## Sample Response

```json
{
  "nic": "123456789V",
  "name": "Kasun Perera",
  "address": "123 Main Street, Colombo",
  "mobile": "0771234567",
  "email": "kasun@example.com",
  "picture": "/api/v1/patients/123456789V/picture"
}
```

## Startup Order

1. Config-Server (`9000`)
2. Service-Registry (`9001`)
3. Api-Gateway (`7000`)
4. Patient-Service (`8000`)
