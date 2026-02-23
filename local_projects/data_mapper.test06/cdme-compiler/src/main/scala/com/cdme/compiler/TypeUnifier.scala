package com.cdme.compiler

import com.cdme.model.SemanticType

// Implements: REQ-TYP-06 (Type Unification Rules), REQ-TYP-05 (Semantic Casting — no implicit)
object TypeUnifier {
  /**
   * Unify codomain of f with domain of g for composition f ; g.
   * Returns the unified type or an error.
   * No implicit casting allowed (REQ-TYP-05).
   */
  def unify(codomain: SemanticType, domain: SemanticType): Either[CompilationError, SemanticType] = {
    if (SemanticType.isCompatible(codomain, domain)) {
      Right(domain)
    } else {
      Left(CompilationError.TypeMismatch(domain, codomain, "composition"))
    }
  }

  /**
   * Check that an explicit cast morphism exists for type conversion.
   * REQ-TYP-05: All type changes must be explicit morphisms.
   */
  def requireExplicitCast(from: SemanticType, to: SemanticType, location: String): Either[CompilationError, Unit] = {
    if (from == to || SemanticType.isCompatible(from, to)) {
      Right(())
    } else {
      Left(CompilationError.ImplicitCast(from, to, location))
    }
  }
}
