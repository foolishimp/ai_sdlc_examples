// Implements: REQ-F-API-001, REQ-F-ADJ-001, REQ-F-LDM-002
// ADR-010: Programmatic API over Configuration Files
package cdme.api

import cdme.model.category.{MorphismId, EntityId, CardinalityType, ContainmentType}
import cdme.model.adjoint._
import cdme.model.access.AccessRule
import cdme.model.error.{ErrorObject, ValidationError}

/**
 * Fluent API for defining morphisms with adjoint implementations.
 *
 * Usage:
 * {{{
 *   val morphism = MorphismBuilder[Trade, Counterparty]("tradeToCounterparty")
 *     .from("Trade")
 *     .to("Counterparty")
 *     .cardinality(CardinalityType.ManyToOne)
 *     .forward(trade => Right(trade.counterparty))
 *     .backward(cp => Vector(/* preimage records */))
 *     .build()
 * }}}
 *
 * @tparam A domain type
 * @tparam B codomain type
 * @param morphismName the morphism name
 */
final case class MorphismBuilder[A, B](
    morphismName: String,
    private val domainEntity: Option[String] = None,
    private val codomainEntity: Option[String] = None,
    private val cardinalityType: Option[CardinalityType] = None,
    private val forwardFn: Option[A => Either[ErrorObject, B]] = None,
    private val backwardFn: Option[B => BackwardResult[A]] = None,
    private val morphismAccessRules: List[AccessRule] = Nil
) {
  /** Set the domain (source) entity. */
  def from(entity: String): MorphismBuilder[A, B] =
    copy(domainEntity = Some(entity))

  /** Set the codomain (target) entity. */
  def to(entity: String): MorphismBuilder[A, B] =
    copy(codomainEntity = Some(entity))

  /** Set the cardinality type. */
  def cardinality(c: CardinalityType): MorphismBuilder[A, B] =
    copy(cardinalityType = Some(c))

  /** Set the forward transformation function. */
  def forward(fn: A => Either[ErrorObject, B]): MorphismBuilder[A, B] =
    copy(forwardFn = Some(fn))

  /** Set the backward (adjoint) function. */
  def backward(fn: B => BackwardResult[A]): MorphismBuilder[A, B] =
    copy(backwardFn = Some(fn))

  /** Add an access rule. */
  def withAccessRule(rule: AccessRule): MorphismBuilder[A, B] =
    copy(morphismAccessRules = morphismAccessRules :+ rule)

  /**
   * Build the adjoint morphism.
   *
   * @return Right(morphism) or Left(error) if required fields are missing
   */
  def build(): Either[ValidationError, AdjointMorphism[A, B]] =
    for {
      domain <- domainEntity.toRight(
        ValidationError.General(s"Morphism '$morphismName' requires a domain entity")
      )
      codomain <- codomainEntity.toRight(
        ValidationError.General(s"Morphism '$morphismName' requires a codomain entity")
      )
      card <- cardinalityType.toRight(
        ValidationError.General(s"Morphism '$morphismName' requires a cardinality type")
      )
      fwd <- forwardFn.toRight(
        ValidationError.General(s"Morphism '$morphismName' requires a forward function")
      )
      bwd <- backwardFn.toRight(
        ValidationError.General(s"Morphism '$morphismName' requires a backward function")
      )
    } yield {
      GenericAdjointMorphism(
        id = MorphismId(morphismName),
        name = morphismName,
        domain = EntityId(domain.toLowerCase.replaceAll("\\s+", "_")),
        codomain = EntityId(codomain.toLowerCase.replaceAll("\\s+", "_")),
        cardinality = card,
        containment = cardinalityToContainment(card),
        forwardImpl = fwd,
        backwardImpl = bwd,
        accessRules = morphismAccessRules
      )
    }

  private def cardinalityToContainment(c: CardinalityType): ContainmentType =
    c match {
      case CardinalityType.OneToOne   => ContainmentType.Isomorphic
      case CardinalityType.ManyToOne  => ContainmentType.Preimage
      case CardinalityType.OneToMany  => ContainmentType.Expansion
      case CardinalityType.ManyToMany => ContainmentType.Preimage
    }
}

/**
 * Generic adjoint morphism built by the MorphismBuilder.
 */
private final case class GenericAdjointMorphism[A, B](
    id: MorphismId,
    name: String,
    domain: EntityId,
    codomain: EntityId,
    cardinality: CardinalityType,
    containment: ContainmentType,
    forwardImpl: A => Either[ErrorObject, B],
    backwardImpl: B => BackwardResult[A],
    accessRules: List[AccessRule]
) extends AdjointMorphism[A, B] {
  override def forward(input: A): Either[ErrorObject, B] = forwardImpl(input)
  override def backward(output: B): BackwardResult[A] = backwardImpl(output)
}

object MorphismBuilder {
  /** Start building a morphism with the given name. */
  def apply[A, B](name: String): MorphismBuilder[A, B] = new MorphismBuilder[A, B](name)
}
