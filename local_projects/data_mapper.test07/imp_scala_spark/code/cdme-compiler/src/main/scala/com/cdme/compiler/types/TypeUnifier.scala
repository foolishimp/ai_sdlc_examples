// Implements: REQ-TYP-06, REQ-TYP-05
// 7-step type unification algorithm for morphism composition.
package com.cdme.compiler.types

import com.cdme.model.error.{CdmeError, TypeUnificationError}
import com.cdme.model.types.{CdmeType, SubtypeRegistry}
import com.cdme.model.types.CdmeType._

/**
 * Type unification engine: validates type compatibility when composing morphisms.
 * No implicit coercion (REQ-TYP-05). All type changes must be explicit morphisms.
 *
 * Implements: REQ-TYP-06, REQ-TYP-05
 */
object TypeUnifier {

  /**
   * Unify codomain of f with domain of g.
   *
   * Algorithm (7 steps from DESIGN.md):
   * 1. Exact equality
   * 2. Subtype relationship
   * 3. Both OptionType -> unify inners
   * 4. Both ListType -> unify elements
   * 5. Both ProductType -> unify field-by-field
   * 6. Both SemanticType -> base must unify AND labels must match
   * 7. Failure
   */
  def unify(
      codomain: CdmeType,
      domain: CdmeType,
      registry: SubtypeRegistry
  ): Either[CdmeError, CdmeType] = {
    // Step 1: Exact equality
    if (codomain == domain) {
      return Right(codomain)
    }

    // Step 2: Subtype relationship
    if (registry.isSubtypeOf(codomain, domain)) {
      return Right(domain)
    }

    // Step 3: Both OptionType
    (codomain, domain) match {
      case (OptionType(innerA), OptionType(innerB)) =>
        return unify(innerA, innerB, registry).map(OptionType)
      case _ => // continue
    }

    // Step 4: Both ListType
    (codomain, domain) match {
      case (ListType(elemA), ListType(elemB)) =>
        return unify(elemA, elemB, registry).map(ListType)
      case _ => // continue
    }

    // Step 5: Both ProductType
    (codomain, domain) match {
      case (ProductType(fieldsA), ProductType(fieldsB)) =>
        return unifyProducts(fieldsA, fieldsB, registry)
      case _ => // continue
    }

    // Step 6: Both SemanticType
    (codomain, domain) match {
      case (SemanticType(baseA, labelA), SemanticType(baseB, labelB)) =>
        if (labelA != labelB) {
          return Left(TypeUnificationError(codomain, domain, Nil))
        }
        return unify(baseA, baseB, registry).map(SemanticType(_, labelA))
      case _ => // continue
    }

    // Step 7: Failure — no implicit coercion
    Left(TypeUnificationError(codomain, domain, Nil))
  }

  /**
   * Unify two product types field by field.
   * All fields in the domain must exist in the codomain and unify.
   */
  private def unifyProducts(
      fieldsA: Map[String, CdmeType],
      fieldsB: Map[String, CdmeType],
      registry: SubtypeRegistry
  ): Either[CdmeError, CdmeType] = {
    // Domain fields must be a subset of (or equal to) codomain fields
    val unifiedFields = fieldsB.foldLeft[Either[CdmeError, Map[String, CdmeType]]](Right(Map.empty)) {
      case (Left(err), _) => Left(err)
      case (Right(acc), (name, typeB)) =>
        fieldsA.get(name) match {
          case None =>
            Left(TypeUnificationError(
              ProductType(fieldsA),
              ProductType(fieldsB),
              Nil
            ))
          case Some(typeA) =>
            unify(typeA, typeB, registry).map(unified => acc + (name -> unified))
        }
    }
    unifiedFields.map(ProductType)
  }

  /**
   * Check if two types are compatible (can be unified).
   */
  def isCompatible(
      codomain: CdmeType,
      domain: CdmeType,
      registry: SubtypeRegistry
  ): Boolean = unify(codomain, domain, registry).isRight
}
