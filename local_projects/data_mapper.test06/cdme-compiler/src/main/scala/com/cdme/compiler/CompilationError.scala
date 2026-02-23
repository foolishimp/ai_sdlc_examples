package com.cdme.compiler

import com.cdme.model.{Grain, SemanticType}

// Implements: REQ-LDM-03 (errors), REQ-TRV-02 (grain), REQ-TYP-06 (type), REQ-AI-01 (hallucination)
sealed trait CompilationError {
  def message: String
}

object CompilationError {
  case class InvalidPath(path: String, reason: String) extends CompilationError {
    def message: String = s"Invalid path '$path': $reason"
  }

  case class GrainViolation(source: Grain, target: Grain, path: String) extends CompilationError {
    def message: String = s"Grain violation at '$path': cannot combine $source with $target without aggregation"
  }

  case class TypeMismatch(expected: SemanticType, actual: SemanticType, at: String) extends CompilationError {
    def message: String = s"Type mismatch at '$at': expected $expected but got $actual"
  }

  case class AccessDenied(morphism: String, principal: String) extends CompilationError {
    def message: String = s"Access denied: principal '$principal' cannot traverse morphism '$morphism'"
  }

  case class HallucinatedMorphism(name: String) extends CompilationError {
    def message: String = s"Hallucinated morphism: '$name' does not exist in the topology"
  }

  case class HallucinatedEntity(name: String) extends CompilationError {
    def message: String = s"Hallucinated entity: '$name' does not exist in the topology"
  }

  case class MonoidLawViolation(morphism: String, reason: String) extends CompilationError {
    def message: String = s"Monoid law violation in '$morphism': $reason"
  }

  case class BudgetExceeded(estimated: Long, budget: Long) extends CompilationError {
    def message: String = s"Cost budget exceeded: estimated $estimated rows exceeds budget of $budget"
  }

  case class SheafInconsistency(leftEpoch: String, rightEpoch: String) extends CompilationError {
    def message: String = s"Sheaf inconsistency: cannot join data from epoch '$leftEpoch' with epoch '$rightEpoch' without explicit temporal semantics"
  }

  case class MissingLookupVersion(lookupName: String) extends CompilationError {
    def message: String = s"Lookup '$lookupName' used without version specification (REQ-INT-06)"
  }

  case class ImplicitCast(from: SemanticType, to: SemanticType, at: String) extends CompilationError {
    def message: String = s"Implicit cast from $from to $to at '$at' is forbidden (REQ-TYP-05)"
  }
}
