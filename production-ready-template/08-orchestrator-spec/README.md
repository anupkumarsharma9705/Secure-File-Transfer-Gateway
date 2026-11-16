# Orchestrator Spec — Saga Pattern for File Processing

**Reusable for any multi-step async workflow.**

---

## Why Orchestrator?

| Without | With |
|---------|------|
| Callback hell / distributed transactions | Explicit state machine |
| No visibility into in-flight work | Dashboard: running/compensating/failed |
| Manual retry logic per step | Centralized retry/backoff policy |
| No compensation on failure | Automatic rollback (delete, revoke, notify) |
| Hard to debug | Full execution history per workflow |

---

## Options

| Tool | Pros | Cons |
|------|------|------|
| **Temporal** | Built-in retries, visibility, Go/Java/TS SDKs | Extra infra, learning curve |
| **Camunda** | BPMN standard, modeling tools | Heavy, Java-centric |
| **Custom (DB + Workers)** | Zero deps, full control | Reinvent retry/visibility |
| **AWS Step Functions** | Managed, visual | Vendor lock-in, cost |

**Recommendation:** Temporal (open-source, self-hostable, great SDK) or Custom for simplicity.

---

## Custom Orchestrator — Minimal Implementation

### Schema

```sql
CREATE TABLE workflows (
  id            UUID PRIMARY KEY,
  type          TEXT NOT NULL,           -- 'ProcessUpload', 'DeleteFile', etc.
  status        TEXT NOT NULL,           -- RUNNING, COMPLETED, FAILED, COMPENSATING
  payload       JSONB NOT NULL,          -- Input data
  current_step  INT DEFAULT 0,
  created_at    TIMESTAMPTZ DEFAULT now(),
  updated_at    TIMESTAMPTZ DEFAULT now(),
  completed_at  TIMESTAMPTZ
);

CREATE TABLE workflow_steps (
  id            UUID PRIMARY KEY,
  workflow_id   UUID REFERENCES workflows(id),
  step_name     TEXT NOT NULL,
  step_order    INT NOT NULL,
  status        TEXT NOT NULL,           -- PENDING, RUNNING, COMPLETED, FAILED, COMPENSATED
  input         JSONB,
  output        JSONB,
  error         TEXT,
  retry_count   INT DEFAULT 0,
  started_at    TIMESTAMPTZ,
  completed_at  TIMESTAMPTZ
);
```

### Worker Interface

```java
public interface SagaStep {
    String name();
    StepResult execute(JsonNode input);
    StepResult compensate(JsonNode input, JsonNode output);
    RetryPolicy retryPolicy();
}

public record StepResult(boolean success, JsonNode output, String error) {}
public record RetryPolicy(int maxAttempts, Duration backoff, boolean exponential) {}
```

### Engine (Simplified)

```java
@Service
public class SagaEngine {
    private final Map<String, List<SagaStep>> workflows = new HashMap<>();

    public void register(String workflowType, List<SagaStep> steps) {
        workflows.put(workflowType, steps);
    }

    @Transactional
    public UUID start(String workflowType, JsonNode payload) {
        UUID wfId = UUID.randomUUID();
        workflowRepo.save(new Workflow(wfId, workflowType, payload));
        stepRepo.saveAll(steps.stream().map(s -> new WorkflowStep(wfId, s.name(), order)).toList());
        executeNext(wfId);
        return wfId;
    }

    private void executeNext(UUID wfId) {
        WorkflowStep next = stepRepo.findNextPending(wfId);
        if (next == null) {
            workflowRepo.complete(wfId);
            return;
        }
        SagaStep step = getStep(next.getStepName());
        next.markRunning();
        StepResult result = step.execute(next.getInput());
        if (result.success()) {
            next.complete(result.output());
            executeNext(wfId);
        } else {
            handleFailure(wfId, next, result);
        }
    }

    private void handleFailure(UUID wfId, WorkflowStep failed, StepResult result) {
        if (failed.getRetryCount() < step.retryPolicy().maxAttempts()) {
            scheduleRetry(wfId, failed, result.retryPolicy());
        } else {
            compensate(wfId, failed.getStepOrder());
        }
    }

    private void compensate(UUID wfId, int failedOrder) {
        workflowRepo.markCompensating(wfId);
        List<WorkflowStep> completed = stepRepo.findCompletedBefore(wfId, failedOrder);
        for (WorkflowStep step : completed.reversed()) {
            SagaStep sagaStep = getStep(step.getStepName());
            StepResult comp = sagaStep.compensate(step.getInput(), step.getOutput());
            step.markCompensated(comp.success());
            if (!comp.success()) {
                // Alert: manual intervention needed
                alertingService.alert("Compensation failed", wfId, step.getStepName());
            }
        }
        workflowRepo.markFailed(wfId);
    }
}
```

---

## ProcessUpload Saga Definition

```java
@Configuration
public class SagaConfig {
    @Bean
    public SagaEngine sagaEngine(List<SagaStep> steps) {
        SagaEngine engine = new SagaEngine();
        engine.register("ProcessUpload", List.of(
            new AssemblePartsStep(),      // 1
            new VirusScanStep(),          // 2
            new GenerateThumbnailsStep(), // 3
            new VerifyChecksumStep(),     // 4
            new IndexMetadataStep(),      // 5
            new NotifyRecipientsStep()    // 6
        ));
        return engine;
    }
}
```

### Step Implementations (Key Points)

| Step | Execute | Compensate | Retry |
|------|---------|------------|-------|
| `AssembleParts` | Download parts → single object | Delete assembled object | 3×, 10s exp |
| `VirusScan` | ClamAV scan | Quarantine object | 2×, 30s |
| `GenerateThumbnails` | libvips/ffmpeg | Delete thumbnails | 3×, 15s |
| `VerifyChecksum` | SHA-256 compare | Mark corrupted | 1× |
| `IndexMetadata` | POST metadata-api | Soft delete file | 3×, 10s |
| `NotifyRecipients` | Webhook/Email/Push | Log failure | 5×, 60s |

---

## Visibility API

| Endpoint | Description |
|----------|-------------|
| `GET /orchestrator/workflows` | List with filters (status, type, date) |
| `GET /orchestrator/workflows/{id}` | Full execution tree |
| `GET /orchestrator/workflows/{id}/steps` | Step timeline |
| `POST /orchestrator/workflows/{id}/retry` | Manual retry from failed step |
| `GET /orchestrator/metrics` | Prometheus: duration, success rate, queue depth |

---

## Dashboard (Grafana / Custom)

| Panel | Query |
|-------|-------|
| Active workflows | `count(workflow_status{status="RUNNING"})` |
| Success rate (5m) | `rate(workflow_completed_total[5m]) / rate(workflow_started_total[5m])` |
| Avg duration p95 | `histogram_quantile(0.95, workflow_duration_seconds_bucket)` |
| Compensation rate | `rate(workflow_compensated_total[5m])` |
| Queue depth by step | `workflow_step_queue_depth{step=~".*"}` |

---

## Testing

| Test | Approach |
|------|----------|
| Unit | Mock steps, verify execute/compensate order |
| Integration | Testcontainers Postgres, real engine |
| Chaos | Kill worker mid-step → verify compensation |
| Load | 1000 concurrent workflows, measure throughput |

---

## Exit Criteria

| Metric | Target |
|--------|--------|
| Workflow throughput | ≥ 500/min |
| Saga p95 duration | < 30s (upload) |
| Compensation success rate | 100% (alert on failure) |
| Visibility API p99 | < 100ms |
| Zero lost workflows | Verified by audit |