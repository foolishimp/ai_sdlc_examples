package com.cdme.ai.assurance

import java.time.Instant
import com.cdme.model._
import com.cdme.compiler._

// Implements: REQ-AI-01 (Topological Validity), REQ-AI-02 (Triangulation), REQ-AI-03 (Dry Run)

sealed trait MappingSource
object MappingSource {
  case object Human extends MappingSource
  case object AI extends MappingSource
}

sealed trait AssuranceViolation {
  def message: String
}
object AssuranceViolation {
  case class HallucinatedMorphism(name: String) extends AssuranceViolation {
    def message: String = s"AI hallucinated morphism: '$name' does not exist"
  }
  case class InvalidType(expected: SemanticType, actual: SemanticType) extends AssuranceViolation {
    def message: String = s"AI type violation: expected $expected, got $actual"
  }
  case class GrainViolation(detail: String) extends AssuranceViolation {
    def message: String = s"AI grain violation: $detail"
  }
  case class AccessViolation(morphism: String) extends AssuranceViolation {
    def message: String = s"AI access violation on morphism '$morphism'"
  }
}

case class AssuranceCertificate(
  mappingName: String,
  source: MappingSource,
  validationPassed: Boolean,
  typeReport: Map[String, SemanticType],
  grainReport: Map[String, Grain],
  violations: List[AssuranceViolation],
  timestamp: Instant
)

class AIAssuranceValidator(compiler: TopologicalCompiler) {
  def validate(
    mapping: MappingDefinition,
    category: Category,
    principal: Principal,
    source: MappingSource
  ): Either[List[AssuranceViolation], AssuranceCertificate] = {
    compiler.compile(mapping, category, principal) match {
      case Right(plan) =>
        Right(AssuranceCertificate(
          mappingName = mapping.name,
          source = source,
          validationPassed = true,
          typeReport = plan.typeMap,
          grainReport = plan.grainMap,
          violations = Nil,
          timestamp = Instant.now()
        ))
      case Left(errors) =>
        val violations = errors.map {
          case CompilationError.HallucinatedMorphism(n) => AssuranceViolation.HallucinatedMorphism(n)
          case CompilationError.HallucinatedEntity(n) => AssuranceViolation.HallucinatedMorphism(n)
          case CompilationError.TypeMismatch(exp, act, _) => AssuranceViolation.InvalidType(exp, act)
          case CompilationError.GrainViolation(_, _, detail) => AssuranceViolation.GrainViolation(detail)
          case CompilationError.AccessDenied(m, _) => AssuranceViolation.AccessViolation(m)
          case other => AssuranceViolation.HallucinatedMorphism(other.message)
        }
        Left(violations)
    }
  }
}
