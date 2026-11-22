# llm-gw

Gateway bất đồng bộ nhận nội dung JSON từ hệ thống A, xếp task qua Kafka, gửi JSON đến LLM và nhận lại JSON qua callback.

## Stack

- Java 21, Spring Boot 3.5.7, Maven
- PostgreSQL 16 + Flyway
- Apache Kafka
- Spring MVC cho API, WebClient cho outbound call
- Transactional outbox để không mất event giữa PostgreSQL và Kafka

## Luồng xử lý

1. A gọi `POST /api/v1/tasks` với body `application/json` và tùy chọn header `Idempotency-Key`.
2. Gateway lưu JSON và task `QUEUED` trong PostgreSQL, đồng thời ghi request event vào outbox.
3. Outbox phát `{taskId,status}` lên `llm.task.requested.v1`.
4. Consumer chiếm một trong đúng 5 slot toàn cục trong PostgreSQL rồi gửi JSON (`taskId`, `callbackUrl`, `input`) tới `LLM_URL`.
5. LLM trả HTTP 2xx để xác nhận đã nhận. Sau khi xử lý, LLM gọi callback với file output JSON.
6. Gateway lưu kết quả, giải phóng slot và phát event gồm kết quả lên `llm.task.completed.v1`.

Năm slot nằm trong DB nên giới hạn 5 task đang chạy vẫn đúng khi scale nhiều instance gateway. Một task giữ slot từ lúc được gửi tới LLM cho tới callback (không chỉ trong thời gian của HTTP dispatch).

## Chạy local

Yêu cầu Java 21+, Maven 3.9+ và Docker.

```bash
docker compose up -d
mvn spring-boot:run
```

Các biến cấu hình chính:

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5433/llmgw` | PostgreSQL JDBC URL |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka brokers |
| `LLM_URL` | `http://localhost:8081/v1/tasks` | API nhận task của LLM |
| `PUBLIC_BASE_URL` | `http://localhost:8080` | URL LLM dùng để callback |
| `LLM_DISPATCH_TIMEOUT` | `30s` | Timeout gửi task |
| `MAX_JSON_FILE_SIZE` | `10MB` | Kích thước JSON tối đa |

## API examples

Gửi task:

```bash
curl -i -X POST http://localhost:8080/api/v1/tasks \
  -H 'Idempotency-Key: order-123' \
  -H 'Content-Type: application/json' \
  --data-binary '@input.json'
```

Tra cứu trạng thái và kết quả:

```bash
curl http://localhost:8080/api/v1/tasks/{taskId}
curl http://localhost:8080/api/v1/tasks/{taskId}/result
```

Callback từ LLM:

```bash
curl -i -X POST http://localhost:8080/api/v1/llm/callbacks/{taskId} \
  -H 'Content-Type: application/json' \
  --data-binary '@output.json'
```

`GET /result` trả `202 Accepted` khi chưa có kết quả và `200 OK` cùng JSON khi đã hoàn tất. Callback là idempotent sau lần hoàn tất đầu tiên. Với production, đặt gateway sau mTLS/API gateway hoặc bổ sung chữ ký callback theo cơ chế xác thực của LLM.

## Kafka contracts

Request topic (`llm.task.requested.v1`):

```json
{"taskId":"uuid","status":"QUEUED"}
```

Result topic (`llm.task.completed.v1`):

```json
{"taskId":"uuid","status":"COMPLETED","result":{"any":"json"}}
```

Nếu dispatch thất bại, result event có `status=FAILED` và trường `error`.

## Audio file storage

Audio có đúng ba API: upload, xem trạng thái và download. Request upload được stream thẳng xuống disk nên hỗ trợ file vài trăm MB mà không giữ toàn bộ nội dung trong RAM.

Mặc định gateway lưu file ngay và chỉ trả response khi hoàn tất (`async=false`):

```bash
curl -sS -X POST 'http://localhost:8080/api/v1/audio' \
  -H 'Content-Type: application/octet-stream' \
  -H 'X-File-Name: meeting.wav' \
  -H 'X-Content-Type: audio/wav' \
  --data-binary '@meeting.wav'
```

Chế độ hàng đợi trả `202 Accepted` sau khi nhận file tạm, sau đó Kafka consumer hoàn tất việc lưu file:

```bash
curl -sS -X POST 'http://localhost:8080/api/v1/audio?async=true' \
  -H 'Content-Type: application/octet-stream' \
  -H 'X-File-Name: meeting.wav' \
  -H 'X-Content-Type: audio/wav' \
  --data-binary '@meeting.wav'
```

Kiểm tra trạng thái bằng `audioId` trong response:

```bash
curl -sS http://localhost:8080/api/v1/audio/{audioId}/status
```

Khi status là `COMPLETED`, download file:

```bash
curl -OJ http://localhost:8080/api/v1/audio/{audioId}/download
```

File được lưu tại `{AUDIO_STORAGE_ROOT}/{audioId}/{originalFilename}`. Giới hạn mặc định là 512 MiB mỗi file và có thể đổi bằng `AUDIO_MAX_CHUNK_BYTES`.

Thư mục mặc định là `./data/audio` tính từ thư mục chạy ứng dụng. Production nên mount một persistent volume và đặt `AUDIO_STORAGE_ROOT` thành đường dẫn tuyệt đối.
