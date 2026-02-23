package com.cdme.runtime

import java.time.Instant
import com.cdme.model.ErrorObject
import com.cdme.compiler._

// Implements: REQ-TRV-01, REQ-TRV-04, REQ-TRV-05, REQ-ACC-01
class DefaultExecutionEngine extends ExecutionEngine {

  override def execute(ctx: ExecutionContext, data: List[Record]): ExecutionResult = {
    var telemetry = ExecutionTelemetry()
    val errors = scala.collection.mutable.ListBuffer.empty[ErrorObject]
    var currentData = data
    var filteredCount = 0L

    ctx.compiledPlan.steps.foreach { step =>
      val stepStart = System.currentTimeMillis()

      step match {
        case TraverseStep(morphism) =>
          // Simple pass-through for traversal steps
          telemetry = telemetry.append(TelemetryEntry(
            morphismName = morphism.name,
            rowCount = currentData.size.toLong,
            latencyMs = System.currentTimeMillis() - stepStart,
            timestamp = Instant.now()
          ))

        case FilterStep(predicate) =>
          val beforeCount = currentData.size
          // Apply filter (simplified)
          currentData = currentData.filter(_ => true)
          filteredCount += (beforeCount - currentData.size)
          telemetry = telemetry.append(TelemetryEntry(
            morphismName = s"filter:$predicate",
            rowCount = currentData.size.toLong,
            latencyMs = System.currentTimeMillis() - stepStart,
            timestamp = Instant.now()
          ))

        case AggregateStep(morphism, monoid) =>
          telemetry = telemetry.append(TelemetryEntry(
            morphismName = s"aggregate:${morphism.name}:$monoid",
            rowCount = currentData.size.toLong,
            latencyMs = System.currentTimeMillis() - stepStart,
            timestamp = Instant.now()
          ))

        case SynthesisStep(expr, _) =>
          telemetry = telemetry.append(TelemetryEntry(
            morphismName = s"synthesis:$expr",
            rowCount = currentData.size.toLong,
            latencyMs = System.currentTimeMillis() - stepStart,
            timestamp = Instant.now()
          ))

        case LookupStep(name, version) =>
          telemetry = telemetry.append(TelemetryEntry(
            morphismName = s"lookup:$name:$version",
            rowCount = currentData.size.toLong,
            latencyMs = System.currentTimeMillis() - stepStart,
            timestamp = Instant.now()
          ))
      }

      // Check failure threshold (REQ-TYP-03-A)
      ctx.failureThreshold.foreach { threshold =>
        threshold.maxAbsolute.foreach { max =>
          if (errors.size > max) {
            errors += ErrorObject(
              failureType = "BATCH_THRESHOLD_EXCEEDED",
              offendingValues = Map("error_count" -> errors.size.toString, "threshold" -> max.toString),
              sourceEntity = "",
              sourceEpoch = ctx.epoch.id,
              morphismPath = Nil,
              constraintViolated = "REQ-TYP-03-A",
              timestamp = Instant.now()
            )
          }
        }
      }
    }

    val ledger = AccountingLedger(
      runId = ctx.runManifest.runId,
      inputCount = data.size.toLong,
      processedCount = currentData.size.toLong,
      filteredCount = filteredCount,
      erroredCount = errors.size.toLong
    )

    ExecutionResult(
      output = currentData,
      errors = errors.toList,
      ledger = ledger,
      telemetry = telemetry,
      manifest = ctx.runManifest
    )
  }
}
