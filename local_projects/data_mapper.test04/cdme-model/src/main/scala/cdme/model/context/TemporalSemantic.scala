// Implements: REQ-F-TRV-003, REQ-F-TRV-004, REQ-F-PDM-003
package cdme.model.context

sealed trait TemporalSemantic
object TemporalSemantic {
  case object AsOf extends TemporalSemantic
  case object Latest extends TemporalSemantic
  case object Exact extends TemporalSemantic

  val values: List[TemporalSemantic] = List(AsOf, Latest, Exact)
}
