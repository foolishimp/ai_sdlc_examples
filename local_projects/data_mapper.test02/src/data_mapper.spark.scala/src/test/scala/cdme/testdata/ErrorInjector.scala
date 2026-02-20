package cdme.testdata

import scala.util.Random
import io.circe.{Json, Encoder}
import io.circe.syntax._
import io.circe.parser._

/**
 * Error Injector for Test Data Generation
 *
 * Wraps clean data and injects configurable errors across various fields.
 * Keeps the core AirlineDataGenerator clean while providing error injection
 * as a composable layer.
 *
 * Error Types:
 * - Type mismatches (string where number expected, etc.)
 * - Null values in required fields
 * - Invalid formats (malformed dates, emails, codes)
 * - Out-of-range values (negative prices, impossible distances)
 * - Structural errors (missing fields, extra fields)
 * - Referential integrity violations (invalid foreign keys)
 * - Encoding issues (invalid UTF-8, special characters)
 *
 * Usage:
 *   val injector = new ErrorInjector(errorRate = 0.10, seed = 42L)
 *   val corruptedJson = injector.injectErrors(cleanJson, "segment")
 */
class ErrorInjector(
  errorRate: Double = 0.10,
  seed: Long = System.currentTimeMillis()
) {

  private val random = new Random(seed)

  // ============================================
  // Error Type Definitions
  // ============================================

  sealed trait ErrorType {
    def name: String
    def description: String
  }

  case object TypeMismatch extends ErrorType {
    val name = "TYPE_MISMATCH"
    val description = "Wrong data type (e.g., string instead of number)"
  }

  case object NullValue extends ErrorType {
    val name = "NULL_VALUE"
    val description = "Null in required field"
  }

  case object InvalidFormat extends ErrorType {
    val name = "INVALID_FORMAT"
    val description = "Malformed data (dates, emails, codes)"
  }

  case object OutOfRange extends ErrorType {
    val name = "OUT_OF_RANGE"
    val description = "Value outside valid bounds"
  }

  case object MissingField extends ErrorType {
    val name = "MISSING_FIELD"
    val description = "Required field missing"
  }

  case object ExtraField extends ErrorType {
    val name = "EXTRA_FIELD"
    val description = "Unexpected field present"
  }

  case object ReferentialIntegrity extends ErrorType {
    val name = "REFERENTIAL_INTEGRITY"
    val description = "Invalid foreign key reference"
  }

  case object EncodingError extends ErrorType {
    val name = "ENCODING_ERROR"
    val description = "Invalid characters or encoding"
  }

  case object DuplicateKey extends ErrorType {
    val name = "DUPLICATE_KEY"
    val description = "Duplicate primary key value"
  }

  val allErrorTypes: List[ErrorType] = List(
    TypeMismatch, NullValue, InvalidFormat, OutOfRange,
    MissingField, ExtraField, ReferentialIntegrity, EncodingError, DuplicateKey
  )

  // Error weights (probability distribution)
  private val errorWeights: Map[ErrorType, Double] = Map(
    TypeMismatch -> 0.20,       // 20% - Common ETL issue
    NullValue -> 0.15,          // 15% - Frequent data quality issue
    InvalidFormat -> 0.20,      // 20% - Date/format parsing
    OutOfRange -> 0.15,         // 15% - Boundary violations
    MissingField -> 0.10,       // 10% - Schema evolution
    ExtraField -> 0.05,         // 5%  - Schema mismatch
    ReferentialIntegrity -> 0.10, // 10% - FK violations
    EncodingError -> 0.03,      // 3%  - Character encoding
    DuplicateKey -> 0.02        // 2%  - Primary key violations
  )

  // ============================================
  // Field Definitions by Entity Type
  // ============================================

  case class FieldDef(
    name: String,
    fieldType: String,
    required: Boolean = true,
    applicableErrors: List[ErrorType]
  )

  val segmentFields: List[FieldDef] = List(
    FieldDef("segment_id", "string", required = true,
      List(NullValue, DuplicateKey, InvalidFormat, MissingField)),
    FieldDef("journey_id", "string", required = true,
      List(NullValue, ReferentialIntegrity, InvalidFormat, MissingField)),
    FieldDef("flight_number", "string", required = true,
      List(NullValue, InvalidFormat, EncodingError, MissingField)),
    FieldDef("airline_code", "string", required = true,
      List(NullValue, ReferentialIntegrity, InvalidFormat, MissingField)),
    FieldDef("departure_airport", "string", required = true,
      List(NullValue, ReferentialIntegrity, InvalidFormat, MissingField)),
    FieldDef("arrival_airport", "string", required = true,
      List(NullValue, ReferentialIntegrity, InvalidFormat, MissingField)),
    FieldDef("departure_datetime", "datetime", required = true,
      List(NullValue, InvalidFormat, TypeMismatch, MissingField)),
    FieldDef("arrival_datetime", "datetime", required = true,
      List(NullValue, InvalidFormat, TypeMismatch, MissingField)),
    FieldDef("flight_date", "date", required = true,
      List(NullValue, InvalidFormat, TypeMismatch, MissingField)),
    FieldDef("cabin_class", "string", required = true,
      List(NullValue, InvalidFormat, MissingField)),
    FieldDef("segment_price_local", "decimal", required = true,
      List(NullValue, TypeMismatch, OutOfRange, MissingField)),
    FieldDef("segment_price_usd", "decimal", required = true,
      List(NullValue, TypeMismatch, OutOfRange, MissingField)),
    FieldDef("segment_sequence", "integer", required = true,
      List(NullValue, TypeMismatch, OutOfRange, MissingField)),
    FieldDef("is_codeshare", "boolean", required = true,
      List(NullValue, TypeMismatch, MissingField)),
    FieldDef("operating_airline", "string", required = false,
      List(ReferentialIntegrity, InvalidFormat)),
    FieldDef("distance_km", "integer", required = true,
      List(NullValue, TypeMismatch, OutOfRange, MissingField)),
    FieldDef("status", "string", required = true,
      List(NullValue, InvalidFormat, MissingField))
  )

  val journeyFields: List[FieldDef] = List(
    FieldDef("journey_id", "string", required = true,
      List(NullValue, DuplicateKey, InvalidFormat)),
    FieldDef("customer_id", "string", required = true,
      List(NullValue, ReferentialIntegrity, InvalidFormat)),
    FieldDef("booking_date", "date", required = true,
      List(NullValue, InvalidFormat, TypeMismatch)),
    FieldDef("journey_type", "string", required = true,
      List(NullValue, InvalidFormat)),
    FieldDef("total_price_local", "decimal", required = true,
      List(NullValue, TypeMismatch, OutOfRange)),
    FieldDef("local_currency", "string", required = true,
      List(NullValue, ReferentialIntegrity, InvalidFormat)),
    FieldDef("total_price_usd", "decimal", required = true,
      List(NullValue, TypeMismatch, OutOfRange)),
    FieldDef("status", "string", required = true,
      List(NullValue, InvalidFormat)),
    FieldDef("outbound_date", "date", required = true,
      List(NullValue, InvalidFormat, TypeMismatch)),
    FieldDef("return_date", "date", required = false,
      List(InvalidFormat, TypeMismatch))
  )

  val customerFields: List[FieldDef] = List(
    FieldDef("customer_id", "string", required = true,
      List(NullValue, DuplicateKey, InvalidFormat)),
    FieldDef("first_name", "string", required = true,
      List(NullValue, EncodingError, InvalidFormat)),
    FieldDef("last_name", "string", required = true,
      List(NullValue, EncodingError, InvalidFormat)),
    FieldDef("email", "string", required = true,
      List(NullValue, InvalidFormat, EncodingError)),
    FieldDef("loyalty_tier", "string", required = false,
      List(InvalidFormat)),
    FieldDef("country_code", "string", required = true,
      List(NullValue, ReferentialIntegrity, InvalidFormat)),
    FieldDef("preferred_currency", "string", required = true,
      List(NullValue, ReferentialIntegrity, InvalidFormat))
  )

  def getFieldsForEntity(entityType: String): List[FieldDef] = entityType.toLowerCase match {
    case "segment" | "flightsegment" => segmentFields
    case "journey" => journeyFields
    case "customer" => customerFields
    case _ => segmentFields
  }

  // ============================================
  // Error Generation Logic
  // ============================================

  /**
   * Decide if this record should have an error
   */
  def shouldInjectError(): Boolean = random.nextDouble() < errorRate

  /**
   * Select an error type based on weighted distribution
   */
  def selectErrorType(): ErrorType = {
    val r = random.nextDouble()
    var cumulative = 0.0
    for ((errorType, weight) <- errorWeights) {
      cumulative += weight
      if (r < cumulative) return errorType
    }
    TypeMismatch // fallback
  }

  /**
   * Select a random field applicable to the error type
   */
  def selectField(fields: List[FieldDef], errorType: ErrorType): Option[FieldDef] = {
    val applicable = fields.filter(_.applicableErrors.contains(errorType))
    if (applicable.isEmpty) None
    else Some(applicable(random.nextInt(applicable.size)))
  }

  /**
   * Generate a corrupted value based on error type and field
   */
  def generateCorruptedValue(field: FieldDef, errorType: ErrorType, originalValue: Json): Json = {
    errorType match {
      case TypeMismatch => generateTypeMismatch(field, originalValue)
      case NullValue => Json.Null
      case InvalidFormat => generateInvalidFormat(field)
      case OutOfRange => generateOutOfRange(field)
      case MissingField => Json.Null // Will be removed from object
      case ExtraField => originalValue // Extra field added separately
      case ReferentialIntegrity => generateInvalidReference(field)
      case EncodingError => generateEncodingError(field)
      case DuplicateKey => originalValue // Handled at record level
    }
  }

  private def generateTypeMismatch(field: FieldDef, original: Json): Json = {
    field.fieldType match {
      case "integer" | "decimal" => Json.fromString("NOT_A_NUMBER")
      case "boolean" => Json.fromString("maybe")
      case "date" | "datetime" => Json.fromInt(99999)
      case "string" => Json.fromInt(-1)
      case _ => Json.fromString("TYPE_ERROR")
    }
  }

  private def generateInvalidFormat(field: FieldDef): Json = {
    field.fieldType match {
      case "date" => Json.fromString(selectRandom(List(
        "not-a-date",
        "2024/13/45",
        "32-01-2024",
        "2024-00-00",
        "YYYY-MM-DD",
        ""
      )))
      case "datetime" => Json.fromString(selectRandom(List(
        "2024-13-45T25:99:99",
        "yesterday",
        "2024-01-01 10:30",
        "invalid-datetime"
      )))
      case "string" if field.name.contains("email") => Json.fromString(selectRandom(List(
        "not-an-email",
        "@invalid.com",
        "missing@",
        "spaces in@email.com"
      )))
      case "string" if field.name.contains("code") => Json.fromString(selectRandom(List(
        "",
        "TOOLONG",
        "X",
        "12",
        "a@b"
      )))
      case "string" if field.name.contains("status") => Json.fromString(selectRandom(List(
        "UNKNOWN_STATUS",
        "invalid",
        "",
        "123"
      )))
      case "string" if field.name.contains("class") => Json.fromString(selectRandom(List(
        "PREMIUM_ECONOMY_PLUS",
        "coach",
        "",
        "123"
      )))
      case _ => Json.fromString("INVALID_FORMAT")
    }
  }

  private def generateOutOfRange(field: FieldDef): Json = {
    field.fieldType match {
      case "decimal" if field.name.contains("price") =>
        Json.fromString(selectRandom(List("-999.99", "99999999.99", "0.001")))
      case "integer" if field.name.contains("distance") =>
        Json.fromInt(selectRandom(List(-100, 0, 100000000)))
      case "integer" if field.name.contains("sequence") =>
        Json.fromInt(selectRandom(List(-1, 0, 999)))
      case "integer" => Json.fromInt(selectRandom(List(-999, 0, Int.MaxValue)))
      case "decimal" => Json.fromString("-99999.99")
      case _ => Json.fromString("OUT_OF_RANGE")
    }
  }

  private def generateInvalidReference(field: FieldDef): Json = {
    field.name match {
      case "airline_code" => Json.fromString(selectRandom(List("XX", "ZZZ", "FAKE", "00")))
      case "departure_airport" | "arrival_airport" =>
        Json.fromString(selectRandom(List("XXX", "FAKE", "000", "NONEXISTENT")))
      case "country_code" => Json.fromString(selectRandom(List("XX", "ZZZ", "00")))
      case "local_currency" | "preferred_currency" =>
        Json.fromString(selectRandom(List("XXX", "FAKE", "000")))
      case "journey_id" => Json.fromString("JRN-NONEXISTENT-000000")
      case "customer_id" => Json.fromString("CUST-NONEXISTENT-000000")
      case _ => Json.fromString("INVALID_REF")
    }
  }

  private def generateEncodingError(field: FieldDef): Json = {
    Json.fromString(selectRandom(List(
      "Caf\u00C3\u00A9",          // Mojibake (double-encoded UTF-8)
      "Test\u0000Value",          // Null byte
      "Line1\nLine2\tTab",        // Control characters
      "\uFFFD\uFFFD\uFFFD",       // Replacement characters
      "Mix\u200Bed\u200B",        // Zero-width spaces
      "<script>alert(1)</script>", // Injection attempt
      "'; DROP TABLE users; --"   // SQL injection
    )))
  }

  private def selectRandom[T](options: List[T]): T = {
    options(random.nextInt(options.size))
  }

  // ============================================
  // Main Error Injection API
  // ============================================

  /**
   * Inject errors into a JSON record
   * Returns (corruptedJson, injectedErrors)
   */
  def injectErrors(json: Json, entityType: String): (Json, List[InjectedError]) = {
    if (!shouldInjectError()) {
      return (json, List.empty)
    }

    val fields = getFieldsForEntity(entityType)
    val errors = scala.collection.mutable.ListBuffer[InjectedError]()
    var current = json

    // Inject 1-3 errors per corrupted record
    val numErrors = 1 + random.nextInt(3)

    for (_ <- 0 until numErrors) {
      val errorType = selectErrorType()

      errorType match {
        case ExtraField =>
          // Add unexpected field
          current = current.mapObject(_.add(
            s"_unexpected_field_${random.nextInt(1000)}",
            Json.fromString("unexpected_value")
          ))
          errors += InjectedError(entityType, "_extra_", ExtraField.name, "Added unexpected field")

        case MissingField =>
          // Remove a required field
          selectField(fields.filter(_.required), errorType).foreach { field =>
            current = current.mapObject(_.remove(field.name))
            errors += InjectedError(entityType, field.name, MissingField.name, s"Removed required field ${field.name}")
          }

        case _ =>
          // Corrupt an existing field
          selectField(fields, errorType).foreach { field =>
            val originalValue = current.hcursor.downField(field.name).focus.getOrElse(Json.Null)
            val corruptedValue = generateCorruptedValue(field, errorType, originalValue)
            current = current.mapObject(_.add(field.name, corruptedValue))
            errors += InjectedError(entityType, field.name, errorType.name,
              s"Changed ${field.name} from $originalValue to $corruptedValue")
          }
      }
    }

    (current, errors.toList)
  }

  /**
   * Inject errors into a JSON string
   */
  def injectErrorsString(jsonString: String, entityType: String): (String, List[InjectedError]) = {
    parse(jsonString) match {
      case Right(json) =>
        val (corrupted, errors) = injectErrors(json, entityType)
        (corrupted.noSpaces, errors)
      case Left(_) =>
        // Already malformed, return as-is
        (jsonString, List.empty)
    }
  }

  /**
   * Process a list of JSON records, injecting errors at the configured rate
   */
  def processRecords(
    records: List[Json],
    entityType: String
  ): (List[Json], List[InjectedError], ErrorStats) = {
    val corrupted = scala.collection.mutable.ListBuffer[Json]()
    val allErrors = scala.collection.mutable.ListBuffer[InjectedError]()
    var cleanCount = 0
    var errorCount = 0

    records.foreach { record =>
      val (processed, errors) = injectErrors(record, entityType)
      corrupted += processed
      allErrors ++= errors
      if (errors.isEmpty) cleanCount += 1 else errorCount += 1
    }

    val stats = ErrorStats(
      totalRecords = records.size,
      cleanRecords = cleanCount,
      errorRecords = errorCount,
      totalErrors = allErrors.size,
      errorsByType = allErrors.groupBy(_.errorType).view.mapValues(_.size).toMap,
      errorsByField = allErrors.groupBy(_.fieldName).view.mapValues(_.size).toMap
    )

    (corrupted.toList, allErrors.toList, stats)
  }
}

/**
 * Record of an injected error
 */
case class InjectedError(
  entityType: String,
  fieldName: String,
  errorType: String,
  description: String
)

/**
 * Statistics about injected errors
 */
case class ErrorStats(
  totalRecords: Int,
  cleanRecords: Int,
  errorRecords: Int,
  totalErrors: Int,
  errorsByType: Map[String, Int],
  errorsByField: Map[String, Int]
) {
  def errorRate: Double = errorRecords.toDouble / totalRecords

  def summary: String = {
    s"""Error Injection Statistics:
       |  Total records: $totalRecords
       |  Clean records: $cleanRecords (${f"${(1 - errorRate) * 100}%.1f"}%)
       |  Error records: $errorRecords (${f"${errorRate * 100}%.1f"}%)
       |  Total errors: $totalErrors
       |
       |  Errors by type:
       |${errorsByType.toList.sortBy(-_._2).map { case (t, c) => s"    $t: $c" }.mkString("\n")}
       |
       |  Errors by field:
       |${errorsByField.toList.sortBy(-_._2).map { case (f, c) => s"    $f: $c" }.mkString("\n")}
       |""".stripMargin
  }
}

/**
 * Configuration for error injection
 */
case class ErrorInjectionConfig(
  errorRate: Double = 0.10,
  seed: Long = System.currentTimeMillis(),
  enabledErrorTypes: Set[String] = Set.empty, // Empty = all enabled
  fieldOverrides: Map[String, Double] = Map.empty // Field-specific error rates
)

object ErrorInjectionConfig {
  val default: ErrorInjectionConfig = ErrorInjectionConfig()

  val high: ErrorInjectionConfig = ErrorInjectionConfig(errorRate = 0.25)

  val low: ErrorInjectionConfig = ErrorInjectionConfig(errorRate = 0.05)

  val typeMismatchOnly: ErrorInjectionConfig = ErrorInjectionConfig(
    errorRate = 0.10,
    enabledErrorTypes = Set("TYPE_MISMATCH")
  )
}
