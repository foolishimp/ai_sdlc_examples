package com.cdme.runtime

import com.cdme.model.ErrorObject

// Implements: REQ-TRV-01, REQ-TRV-04
case class Record(fields: Map[String, Any])

case class ExecutionResult(
  output: List[Record],
  errors: List[ErrorObject],
  ledger: AccountingLedger,
  telemetry: ExecutionTelemetry,
  manifest: RunManifest
)

trait ExecutionEngine {
  def execute(ctx: ExecutionContext, data: List[Record]): ExecutionResult
}
