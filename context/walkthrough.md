Submission Service Architecture Upgrade - Walkthrough
Summary
Upgraded AlgoCrack-SubmissionService from a Kafka-based synchronous architecture to an async HTTP-based architecture that communicates with the new CodeExecutionService microservice.

Architecture Before vs After
Aspect	Before	After
Execution Model	Sync (Kafka consumer blocked)	Async (HTTP + polling)
CXE Integration	Maven library	REST microservice (port 8081)
Queue	Kafka	CXE's internal Redis queue
Real-time Updates	Kafka logs (unused)	WebSocket
Submission Persistence	Basic (no status tracking)	Full lifecycle tracking
Files Created (14 files)
Configuration
WebClientConfig.java
 - WebClient for CXE HTTP calls
WebSocketConfig.java
 - STOMP WebSocket broker
AsyncConfig.java
 - Thread pool executor
application.yml
 - New YAML config
DTOs
ExecutionRequest.java
 - CXE request
ExecutionResponse.java
 - CXE immediate response
SubmissionStatusDto.java
 - CXE polling DTO
SubmissionRequestDto.java
 - API request
SubmissionResponseDto.java
 - API response
SubmissionDetailDto.java
 - Detailed response
Services
CodeExecutionClientService.java
 - HTTP client for CXE
ResultValidationService.java
 - Output comparison
WebSocketService.java
 - Real-time updates
SubmissionService.java
 - Orchestration
SubmissionProcessingService.java
 - Async processing
Repositories
SubmissionRepository.java
 - Submission queries
QuestionStatisticsRepository.java
 - Statistics
Files Modified (2 files)
build.gradle
 - Removed Kafka, CXE library; added WebFlux
AlgoCrackSubmissionServiceApplication.java
 - Added @EnableAsync, @EnableJpaAuditing
SubmissionController.java
 - New REST API
Files Deleted (9 files)
File	Reason
config/KafkaConfiguration.java
Kafka no longer used
producer/SubmissionProducer.java
CXE handles queueing
producer/LogsProducer.java
WebSocket replaces
consumer/SubmissionConsumer.java
Async processing replaces
consumer/LogsConsumer.java
WebSocket replaces
dto/SubmissionDto.java
Replaced by new DTOs
service/CodeRunnerService.java
Replaced by SubmissionProcessingService
config/AppConfig.java
RestTemplate not needed
application.properties
Replaced by application.yml
New REST API
POST /api/v1/submissions          → Submit code (202 Accepted)
GET  /api/v1/submissions/{id}     → Get submission details
GET  /api/v1/submissions/user/{id}→ Get user history
WebSocket Endpoint
ws://localhost:8080/ws
Subscribe: /topic/submission/{submissionId}
⚠️ Next Steps Required
IMPORTANT

The EntityService must be updated before this service can compile.

1. Update EntityService
The following entities/enums need to exist in AlgoCrack-EntityService:

Submission.java with fields: submissionId, status, verdict, runtimeMs, memoryKb, testResults, queuedAt, startedAt, completedAt, workerId, etc.
SubmissionStatus.java enum: QUEUED, COMPILING, RUNNING, COMPLETED, FAILED, CANCELLED
SubmissionVerdict.java enum: ACCEPTED, WRONG_ANSWER, TLE, MLE, RE, CE, INTERNAL_ERROR
QuestionStatistics.java entity for analytics
2. Publish EntityService
cd /path/to/AlgoCrack-EntityService
./gradlew publishToMavenLocal
3. Build SubmissionService
cd /home/hrishabh/codebases/java/leetcode/AlgoCrack-SubmissionService
./gradlew clean build
4. Start Services in Order
MySQL
Redis
CodeExecutionService (port 8081)
SubmissionService (port 8080)