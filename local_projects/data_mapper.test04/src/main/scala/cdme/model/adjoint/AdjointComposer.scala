// Implements: REQ-F-ADJ-007, REQ-BR-ADJ-001
package cdme.model.adjoint

import cdme.model.category.{MorphismId, EntityId, CardinalityType, ContainmentType}
import cdme.model.access.AccessRule
import cdme.model.error.{ErrorObject, ValidationError}

/**
 * Composes adjoint morphisms contravariantly.
 *
 * For two morphisms f: A -> B and g: B -> C:
 *   - Forward composition:  (g . f)(x) = g(f(x))           (covariant)
 *   - Backward composition: (g . f)-(z) = f-(g-(z))         (contravariant)
 *
 * Contravariant composition is validated at definition time by checking that
 * the composed backward path type-checks (REQ-BR-ADJ-001).
 */
object AdjointComposer:

  /**
   * Compose two adjoint morphisms contravariantly.
   *
   * @tparam A domain of f
   * @tparam B codomain of f / domain of g
   * @tparam C codomain of g
   * @param f first morphism (A -> B)
   * @param g second morphism (B -> C)
   * @return Right(composed morphism) or Left(error) if composition is invalid
   */
  def compose[A, B, C](
      f: AdjointMorphism[A, B],
      g: AdjointMorphism[B, C]
  ): Either[ValidationError, AdjointMorphism[A, C]] =
    if f.codomain != g.domain then
      Left(ValidationError.TypeMismatch(
        s"Cannot compose: codomain of ${f.id.show} (${f.codomain.show}) " +
        s"does not match domain of ${g.id.show} (${g.domain.show})"
      ))
    else
      Right(ComposedAdjoint(f, g))

  /**
   * A morphism formed by composing two adjoint morphisms.
   *
   * Forward:  g(f(x))
   * Backward: f-(g-(z))  (contravariant)
   *
   * @tparam A domain of the first morphism
   * @tparam B intermediate type
   * @tparam C codomain of the second morphism
   */
  private final case class ComposedAdjoint[A, B, C](
      first: AdjointMorphism[A, B],
      second: AdjointMorphism[B, C]
  ) extends AdjointMorphism[A, C]:

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

    override def backward(output: C): BackwardResult[A] =
      val intermediateResult = second.backward(output)
      val finalRecords = intermediateResult.records.flatMap { b =>
        first.backward(b).records
      }
      val mergedMetadata = intermediateResult.metadata ++
        Map("composedFrom" -> s"${second.id.value},${first.id.value}")
      BackwardResult(finalRecords, mergedMetadata)

  /**
   * Compose cardinality types for the resulting morphism.
   */
  private def composeCardinality(
      c1: CardinalityType,
      c2: CardinalityType
  ): CardinalityType =
    (c1, c2) match
      case (CardinalityType.OneToOne, other)   => other
      case (other, CardinalityType.OneToOne)   => other
      case (CardinalityType.ManyToOne, CardinalityType.ManyToOne) => CardinalityType.ManyToOne
      case (CardinalityType.OneToMany, CardinalityType.OneToMany) => CardinalityType.OneToMany
      case _ => CardinalityType.ManyToMany

  /**
   * Compose containment types. The result is the "weaker" containment.
   */
  private def composeContainment(
      c1: ContainmentType,
      c2: ContainmentType
  ): ContainmentType =
    (c1, c2) match
      case (ContainmentType.Isomorphic, other) => other
      case (other, ContainmentType.Isomorphic) => other
      case (ContainmentType.Opaque, _) | (_, ContainmentType.Opaque) => ContainmentType.Opaque
      case _ => c1 // conservative: take the first
