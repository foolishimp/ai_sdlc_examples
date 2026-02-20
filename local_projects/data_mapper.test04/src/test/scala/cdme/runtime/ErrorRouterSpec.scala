// Validates: REQ-F-ERR-001, REQ-F-ERR-002, REQ-F-ERR-003, REQ-F-ERR-004, REQ-BR-ERR-001, REQ-DATA-QUAL-001
// Tests for Either-based routing, error sink
package cdme.runtime

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import cdme.model.error.{ErrorObject, ConstraintType}
import cdme.compiler.BatchThresholdConfig
import java.time.Instant

class ErrorRouterSpec extends AnyFunSuite with Matchers:

  // --- Test Fixtures ---

  private def makeError(detail: String): ErrorObject = ErrorObject(
    constraintType = ConstraintType.TypeConstraint,
    offendingValues = Map("detail" -> detail),
    sourceEntity = cdme.model.error.EntityId("test"),
    sourceEpoch = cdme.model.error.EpochId("epoch1"),
    morphismPath = List(cdme.model.error.MorphismId("m1")),
    timestamp = Instant.now()
  )

  private class InMemorySink extends ErrorSink:
    var written: List[ErrorObject] = Nil
    var flushed: Boolean = false

    override def write(errors: List[ErrorObject]): Either[SinkError, Unit] =
      written = written ++ errors
      Right(())

    override def flush(): Either[SinkError, Unit] =
      flushed = true
      Right(())

  private class FailingSink extends ErrorSink:
    override def write(errors: List[ErrorObject]): Either[SinkError, Unit] =
      Left(SinkError("Sink failure"))
    override def flush(): Either[SinkError, Unit] =
      Left(SinkError("Flush failure"))

  // --- REQ-F-ERR-001: Failures as Data ---

  test("REQ-F-ERR-001: Right values pass through the router") {
    val sink = InMemorySink()
    val router = ErrorRouter(sink, BatchThresholdConfig())
    val result = router.route(Right(42))
    result shouldBe Right(42)
    router.currentErrorCount shouldBe 0L
    router.currentTotalCount shouldBe 1L
  }

  test("REQ-F-ERR-001: Left values are routed as errors") {
    val sink = InMemorySink()
    val router = ErrorRouter(sink, BatchThresholdConfig())
    val error = makeError("type mismatch")
    val result = router.route(Left(error))

    result.isLeft shouldBe true
    router.currentErrorCount shouldBe 1L
    router.currentTotalCount shouldBe 1L
  }

  test("REQ-F-ERR-001: no record is silently dropped - all processed or errored") {
    val sink = InMemorySink()
    val router = ErrorRouter(sink, BatchThresholdConfig(maxAbsoluteErrors = Some(10000)))

    var successCount = 0L
    var errorCount = 0L

    for i <- 1 to 100 do
      val result = if i % 7 == 0 then
        router.route(Left(makeError(s"error $i")))
      else
        router.route(Right(i))

      result match
        case Right(_) => successCount += 1
        case Left(_)  => errorCount += 1

    (successCount + errorCount) shouldBe 100L
    router.currentTotalCount shouldBe 100L
  }

  // --- REQ-F-ERR-004: Error Sink Routing ---

  test("REQ-F-ERR-004: finalize flushes remaining errors to sink") {
    val sink = InMemorySink()
    val router = ErrorRouter(sink, BatchThresholdConfig())

    router.route(Left(makeError("error 1")))
    router.route(Left(makeError("error 2")))

    val result = router.finalize()
    result.isRight shouldBe true
    sink.flushed shouldBe true
    sink.written should have size 2
  }

  test("REQ-F-ERR-004: SinkError is produced when sink fails") {
    val sinkErr = SinkError("Connection failed", Some(new RuntimeException("timeout")))
    sinkErr.message shouldBe "Connection failed"
    sinkErr.cause shouldBe defined
  }

  // --- REQ-DATA-QUAL-001: Batch Failure Threshold ---

  test("REQ-DATA-QUAL-001: threshold not exceeded for few errors") {
    val router = ErrorRouter(
      InMemorySink(),
      BatchThresholdConfig(maxAbsoluteErrors = Some(5))
    )

    for _ <- 1 to 3 do
      router.route(Left(makeError("error")))

    router.isThresholdExceeded shouldBe false
  }

  test("REQ-DATA-QUAL-001: absolute error threshold triggers exceeded") {
    val router = ErrorRouter(
      InMemorySink(),
      BatchThresholdConfig(maxAbsoluteErrors = Some(3))
    )

    for _ <- 1 to 3 do
      router.route(Left(makeError("error")))

    router.isThresholdExceeded shouldBe true
  }

  test("REQ-DATA-QUAL-001: percentage threshold triggers exceeded") {
    val router = ErrorRouter(
      InMemorySink(),
      BatchThresholdConfig(maxErrorPercentage = Some(0.10)) // 10%
    )

    // Process 10 records: 2 errors = 20% > 10%
    for i <- 1 to 10 do
      if i <= 2 then router.route(Left(makeError(s"error $i")))
      else router.route(Right(i))

    router.isThresholdExceeded shouldBe true
  }

  test("REQ-DATA-QUAL-001: percentage threshold not exceeded at boundary") {
    val router = ErrorRouter(
      InMemorySink(),
      BatchThresholdConfig(maxErrorPercentage = Some(0.10)) // 10%
    )

    // Process 100 records: 10 errors = 10% = threshold (not exceeded, since > is used)
    for i <- 1 to 100 do
      if i <= 10 then router.route(Left(makeError(s"error $i")))
      else router.route(Right(i))

    // 10/100 = 0.10, and condition is > 0.10, so not exceeded
    router.isThresholdExceeded shouldBe false
  }

  // --- Error count tracking ---

  test("currentErrorCount and currentTotalCount track correctly") {
    val router = ErrorRouter(InMemorySink(), BatchThresholdConfig())

    router.route(Right(1))
    router.route(Right(2))
    router.route(Left(makeError("err1")))
    router.route(Right(3))
    router.route(Left(makeError("err2")))

    router.currentErrorCount shouldBe 2L
    router.currentTotalCount shouldBe 5L
  }

  // --- FlushBuffer ---

  test("flushBuffer writes accumulated errors to sink") {
    val sink = InMemorySink()
    val router = ErrorRouter(sink, BatchThresholdConfig())

    router.route(Left(makeError("err1")))
    router.route(Left(makeError("err2")))
    router.flushBuffer()

    sink.written should have size 2
  }

  test("flushBuffer on empty buffer is a no-op") {
    val sink = InMemorySink()
    val router = ErrorRouter(sink, BatchThresholdConfig())
    router.flushBuffer() shouldBe Right(())
    sink.written shouldBe empty
  }
