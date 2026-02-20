package com.cdme.compiler.synthesis
// Implements: REQ-F-SYN-001, REQ-F-SYN-004

import com.cdme.model._
import com.cdme.model.morphism.{Expression, ColumnRef, Literal, FunctionCall, Conditional, Priority}
import com.cdme.model.types.{CdmeType, PrimitiveType, PrimitiveKind,
  IntegerKind, LongKind, FloatKind, DoubleKind, DecimalKind, StringKind, BooleanKind}
import com.cdme.compiler.{SynthesisError, UnreachableAttribute, SynthesisTypeError}

/**
 * Validates synthesis expressions against the set of reachable attributes.
 *
 * A synthesis expression may reference column names, apply functions,
 * use conditionals, and reference priority chains. Every attribute
 * referenced must exist in the available attribute map, and every
 * function application must be type-consistent.
 */
object SynthesisValidator {

  /**
   * Validate a synthesis expression and infer its result type.
   *
   * @param expression          the expression to validate
   * @param availableAttributes map of attribute name to its CDME type
   * @param graph               the LDM graph (for resolving entity-qualified references)
   * @return the inferred result type on success, or a [[SynthesisError]] on failure
   */
  def validate(
    expression: Expression,
    availableAttributes: Map[String, CdmeType],
    graph: LdmGraph
  ): Either[SynthesisError, CdmeType] = {
    expression match {
      case ColumnRef(name) =>
        // Look up by name (may be qualified like "entity.attr" or simple "attr")
        availableAttributes.get(name)
          .orElse(availableAttributes.find(_._1.endsWith(s".$name")).map(_._2)) match {
          case Some(cdmeType) => Right(cdmeType)
          case None =>
            Left(UnreachableAttribute(name, availableAttributes.keySet))
        }

      case Literal(_, cdmeType) =>
        Right(cdmeType)

      case FunctionCall(functionName, args) =>
        validateFunctionCall(functionName, args, availableAttributes, graph)

      case Conditional(condition, thenExpr, elseExpr) =>
        validateConditional(condition, thenExpr, elseExpr, availableAttributes, graph)

      case Priority(exprs) =>
        validatePriority(exprs, availableAttributes, graph)
    }
  }

  // ---------------------------------------------------------------------------
  // Private validation methods
  // ---------------------------------------------------------------------------

  private def validateFunctionCall(
    functionName: String,
    args: List[Expression],
    available: Map[String, CdmeType],
    graph: LdmGraph
  ): Either[SynthesisError, CdmeType] = {
    // Validate all arguments first
    val argTypes = args.foldLeft[Either[SynthesisError, List[CdmeType]]](Right(Nil)) {
      case (Left(err), _) => Left(err)
      case (Right(acc), arg) =>
        validate(arg, available, graph).map(t => acc :+ t)
    }

    argTypes.flatMap { resolvedArgTypes =>
      // Function type resolution is domain-specific. For now, we implement
      // common patterns and use ??? for complex logic that needs TDD.
      functionName.toLowerCase match {
        case "concat" | "coalesce" =>
          // Return type = type of first argument
          resolvedArgTypes.headOption.toRight(
            SynthesisTypeError(functionName, Nil, Nil): SynthesisError
          )

        case "sum" | "avg" | "min" | "max" | "count" =>
          // Aggregation functions: return numeric type
          resolvedArgTypes.headOption match {
            case Some(numType) if isNumeric(numType) => Right(numType)
            case Some(_) if functionName == "count" =>
              Right(PrimitiveType(LongKind))
            case Some(_) =>
              Left(SynthesisTypeError(
                functionName,
                List(PrimitiveType(DoubleKind)),
                resolvedArgTypes
              ))
            case None =>
              Left(SynthesisTypeError(functionName, Nil, Nil))
          }

        case "cast" =>
          // Cast is handled by type unifier; assume valid here
          // Complex validation deferred to TDD
          resolvedArgTypes.lastOption.toRight(
            SynthesisTypeError(functionName, Nil, Nil): SynthesisError
          )

        case _ =>
          // Unknown function: infer return type as first argument type
          // Full function registry to be implemented via TDD
          resolvedArgTypes.headOption.toRight(
            SynthesisTypeError(functionName, Nil, Nil): SynthesisError
          )
      }
    }
  }

  private def validateConditional(
    condition: Expression,
    thenExpr: Expression,
    elseExpr: Expression,
    available: Map[String, CdmeType],
    graph: LdmGraph
  ): Either[SynthesisError, CdmeType] = {
    for {
      _ <- validate(condition, available, graph)
      thenType <- validate(thenExpr, available, graph)
      _ <- validate(elseExpr, available, graph)
    } yield {
      // Both branches should have compatible types.
      // For simplicity, return thenType; full unification deferred to TDD.
      thenType
    }
  }

  private def validatePriority(
    exprs: List[Expression],
    available: Map[String, CdmeType],
    graph: LdmGraph
  ): Either[SynthesisError, CdmeType] = {
    // All priority branches must be valid; return type of first
    val validated = exprs.foldLeft[Either[SynthesisError, Option[CdmeType]]](Right(None)) {
      case (Left(err), _) => Left(err)
      case (Right(acc), expr) =>
        validate(expr, available, graph).map { t =>
          acc.orElse(Some(t))
        }
    }
    validated.flatMap {
      case Some(t) => Right(t)
      case None => Left(SynthesisTypeError("PRIORITY", Nil, Nil))
    }
  }

  private def isNumeric(t: CdmeType): Boolean = t match {
    case PrimitiveType(kind) => kind match {
      case IntegerKind | LongKind | FloatKind | DoubleKind | DecimalKind => true
      case _ => false
    }
    case _ => false
  }
}
