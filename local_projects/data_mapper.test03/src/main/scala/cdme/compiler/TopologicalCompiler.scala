package cdme.compiler

// Implements: REQ-F-LDM-001, REQ-F-LDM-002, REQ-F-LDM-003, REQ-F-TYP-006, REQ-F-TRV-002, REQ-F-AI-001, REQ-F-AI-003

import cdme.model.*

/** The Topological Compiler is the control plane.
  *
  * Validates mappings against the LDM topology BEFORE any data processing occurs.
  * AI-generated mappings are validated using the same rules as human-authored
  * mappings (REQ-F-AI-001) — no special fast-path.
  */
object TopologicalCompiler:

  sealed trait CompilationError
  case class PathNotFound(path: String) extends CompilationError
  case class TypeUnificationFailed(error: TypeSystem.TypeError) extends CompilationError
  case class GrainViolation(error: GrainChecker.GrainError) extends CompilationError
  case class AccessDenied(morphism: String, principal: String) extends CompilationError

  /** Compile a mapping definition against the topology.
    *
    * Steps:
    * 1. Path verification — morphisms exist in topology (REQ-F-LDM-003)
    * 2. Type unification — codomain matches domain at each step (REQ-F-TYP-006)
    * 3. Grain check — no illegal grain mixing (REQ-F-TRV-002)
    * 4. Access check — principal has permissions (REQ-F-LDM-005)
    */
  def compile(
      topology: Topology,
      path: List[String],
      principal: Option[String] = None
  ): Either[CompilationError, List[Morphism]] =
    for
      morphisms <- topology.resolvePath(path).left.map(PathNotFound(_))
      _ <- checkTypeChain(morphisms)
      _ <- GrainChecker.checkPath(morphisms).left.map(GrainViolation(_))
      _ <- checkAccess(morphisms, principal)
    yield morphisms

  /** Validate type chain: codomain of each morphism must unify with domain of next. */
  private def checkTypeChain(morphisms: List[Morphism]): Either[CompilationError, Unit] =
    // For initial implementation, entities must match by name
    morphisms.sliding(2).foreach {
      case List(m1, m2) =>
        if m1.codomain.name != m2.domain.name then
          return Left(PathNotFound(
            s"Type mismatch: ${m1.name} produces ${m1.codomain.name} but ${m2.name} expects ${m2.domain.name}"))
      case _ => ()
    }
    Right(())

  /** Check access control on morphisms (REQ-F-LDM-005). */
  private def checkAccess(
      morphisms: List[Morphism],
      principal: Option[String]
  ): Either[CompilationError, Unit] =
    principal match
      case None => Right(()) // No principal = no access check
      case Some(p) =>
        morphisms.find(m =>
          m.accessControl.roles.nonEmpty && !m.accessControl.roles.contains(p)
        ) match
          case Some(denied) => Left(AccessDenied(denied.name, p))
          case None => Right(())

  /** Dry-run mode: validate only, no execution (REQ-F-AI-003). */
  def dryRun(
      topology: Topology,
      path: List[String],
      principal: Option[String] = None
  ): Either[CompilationError, ValidationReport] =
    compile(topology, path, principal).map { morphisms =>
      ValidationReport(
        valid = true,
        morphismCount = morphisms.size,
        path = path,
        grainTransitions = morphisms.map(m => (m.domain.grain, m.codomain.grain))
      )
    }

case class ValidationReport(
    valid: Boolean,
    morphismCount: Int,
    path: List[String],
    grainTransitions: List[(Grain, Grain)]
)
