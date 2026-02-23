// Implements: REQ-TRV-01, REQ-INT-01
// Execution engine: takes a ValidatedPlan and produces results.
package com.cdme.runtime

import com.cdme.model.error.CdmeError
import com.cdme.compiler.ValidatedPlan

/**
 * The execution engine: processes records through a validated morphism DAG.
 * Implements: REQ-TRV-01, REQ-INT-01
 */
object ExecutionEngine {

  /** Result of an execution run. */
  final case class ExecutionResult(
      runId: String,
      ledger: AccountingLedger,
      outputLocation: String,
      errorLocation: String,
      lineageLocation: String,
      success: Boolean
  )

  /**
   * Execute a validated plan with the given context.
   * Returns the execution result or an error.
   *
   * Note: actual execution is delegated to platform-specific engines
   * (e.g., SparkExecutor in cdme-spark). This provides the contract.
   */
  def execute(
      plan: ValidatedPlan,
      context: ExecutionContext
  ): Either[CdmeError, ExecutionResult] = {
    if (context.dryRun) {
      // Dry run mode: validate without executing
      Right(ExecutionResult(
        runId = context.runId,
        ledger = RecordAccounting.buildLedger(
          runId = context.runId,
          sourceKeyField = "dry_run",
          inputCount = 0,
          processedCount = 0,
          filteredCount = 0,
          erroredCount = 0,
          reverseJoinLocation = "dry_run",
          filteredKeysLocation = "dry_run",
          errorLocation = "dry_run"
        ),
        outputLocation = "dry_run",
        errorLocation = "dry_run",
        lineageLocation = "dry_run",
        success = true
      ))
    } else {
      // Real execution would be delegated to platform (Spark, etc.)
      // This is the stub contract for the runtime module
      Right(ExecutionResult(
        runId = context.runId,
        ledger = RecordAccounting.buildLedger(
          runId = context.runId,
          sourceKeyField = "key",
          inputCount = 0,
          processedCount = 0,
          filteredCount = 0,
          erroredCount = 0,
          reverseJoinLocation = "",
          filteredKeysLocation = "",
          errorLocation = ""
        ),
        outputLocation = "",
        errorLocation = "",
        lineageLocation = "",
        success = true
      ))
    }
  }
}
