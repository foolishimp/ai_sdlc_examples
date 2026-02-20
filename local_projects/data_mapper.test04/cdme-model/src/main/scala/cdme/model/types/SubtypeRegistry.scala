// Implements: REQ-F-TYP-004, REQ-F-TYP-005, REQ-BR-TYP-001
package cdme.model.types

import cdme.model.error.ValidationError

final case class SubtypeRegistry(
    relationships: Set[(CdmeType, CdmeType)]
) {
  def isSubtypeOf(sub: CdmeType, sup: CdmeType): Boolean =
    if (sub == sup) true
    else
      relationships.contains((sub, sup)) ||
      relationships
        .filter(_._1 == sub)
        .exists { case (_, mid) => isSubtypeOf(mid, sup) }

  def declare(sub: CdmeType, sup: CdmeType): Either[ValidationError, SubtypeRegistry] =
    if (isSubtypeOf(sup, sub))
      Left(ValidationError.TypeMismatch(
        s"Declaring ${sub.typeName} <: ${sup.typeName} would create a cycle"
      ))
    else
      Right(copy(relationships = relationships + ((sub, sup))))

  def supertypesOf(t: CdmeType): Set[CdmeType] = {
    val direct = relationships.filter(_._1 == t).map(_._2)
    direct ++ direct.flatMap(supertypesOf)
  }
}

object SubtypeRegistry {
  val empty: SubtypeRegistry = SubtypeRegistry(Set.empty)
}
