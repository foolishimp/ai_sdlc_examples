// Implements: REQ-F-ADJ-007, REQ-BR-ADJ-001
package cdme.model.adjoint

import cdme.model.category.{MorphismId, EntityId, CardinalityType, ContainmentType}
import cdme.model.access.AccessRule
import cdme.model.error.{ErrorObject, ValidationError}

object AdjointComposer {

  def compose[A, B, C](
      f: AdjointMorphism[A, B],
      g: AdjointMorphism[B, C]
  ): Either[ValidationError, AdjointMorphism[A, C]] =
    if (f.codomain != g.domain)
      Left(ValidationError.TypeMismatch(
        s"Cannot compose: codomain of ${f.id.show} (${f.codomain.show}) " +
        s"does not match domain of ${g.id.show} (${g.domain.show})"
      ))
    else
      Right(ComposedAdjoint(f, g))

  private final case class ComposedAdjoint[A, B, C](
      first: AdjointMorphism[A, B],
      second: AdjointMorphism[B, C]
  ) extends AdjointMorphism[A, C] {

    override val id: MorphismId =
      MorphismId(s"${first.id.value}.${second.id.value}")

    override val name: String =
      s"${second.name} . ${first.name}"

    override val domain: EntityId = first.domain

    override val codomain: EntityId = second.codomain

    override val cardinality: CardinalityType =
      composeCardinality(first.cardinality, second.cardinality)

    override val containment: ContainmentType =
      composeContainment(first.containment, second.containment)

    override val accessRules: List[AccessRule] =
      first.accessRules ++ second.accessRules

    override def forward(input: A): Either[ErrorObject, C] =
      first.forward(input).flatMap(second.forward)

    override def backward(output: C): BackwardResult[A] = {
      val intermediateResult = second.backward(output)
      val finalRecords = intermediateResult.records.flatMap { b =>
        first.backward(b).records
      }
      val mergedMetadata = intermediateResult.metadata ++
        Map("composedFrom" -> s"${second.id.value},${first.id.value}")
      BackwardResult(finalRecords, mergedMetadata)
    }
  }

  private def composeCardinality(
      c1: CardinalityType,
      c2: CardinalityType
  ): CardinalityType =
    (c1, c2) match {
      case (CardinalityType.OneToOne, other)   => other
      case (other, CardinalityType.OneToOne)   => other
      case (CardinalityType.ManyToOne, CardinalityType.ManyToOne) => CardinalityType.ManyToOne
      case (CardinalityType.OneToMany, CardinalityType.OneToMany) => CardinalityType.OneToMany
      case _ => CardinalityType.ManyToMany
    }

  private def composeContainment(
      c1: ContainmentType,
      c2: ContainmentType
  ): ContainmentType =
    (c1, c2) match {
      case (ContainmentType.Isomorphic, other) => other
      case (other, ContainmentType.Isomorphic) => other
      case (ContainmentType.Opaque, _) | (_, ContainmentType.Opaque) => ContainmentType.Opaque
      case _ => c1
    }
}
