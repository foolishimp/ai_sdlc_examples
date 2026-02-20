// Implements: REQ-F-TYP-003, REQ-F-TYP-004, REQ-BR-TYP-001
package cdme.model.types

import cdme.model.error.ValidationError

/**
 * Type unification engine for morphism composition.
 *
 * Checks type compatibility when composing morphisms. All type conversions
 * must be explicit named morphisms (REQ-BR-TYP-001). No implicit casting
 * is performed.
 */
object TypeUnifier:

  /**
   * Attempt to unify two types for morphism composition.
   *
   * The output type of the first morphism must be compatible with the input
   * type of the second morphism. Returns the unified type or a type error.
   *
   * @param output   the output type of the preceding morphism
   * @param input    the input type of the following morphism
   * @param registry the subtype registry for declared subtype relationships
   * @return Right(unifiedType) if compatible, Left(error) otherwise
   */
  def unify(
      output: CdmeType,
      input: CdmeType,
      registry: SubtypeRegistry
  ): Either[ValidationError, CdmeType] =
    if output == input then
      Right(input)
    else if registry.isSubtypeOf(output, input) then
      // Output is a subtype of input -- widening is allowed
      Right(input)
    else
      (output, input) match
        case (OptionType(innerOut), OptionType(innerIn)) =>
          unify(innerOut, innerIn, registry).map(OptionType.apply)

        case (ListType(elemOut), ListType(elemIn)) =>
          unify(elemOut, elemIn, registry).map(ListType.apply)

        case (ProductType(_, fieldsOut), ProductType(nameIn, fieldsIn)) =>
          unifyProductFields(fieldsOut, fieldsIn, registry).map(unified =>
            ProductType(nameIn, unified)
          )

        case (SemanticType(baseOut, tagOut), SemanticType(baseIn, tagIn)) if tagOut == tagIn =>
          unify(baseOut, baseIn, registry).map(SemanticType(_, tagOut))

        case _ =>
          Left(ValidationError.TypeMismatch(
            s"Cannot unify ${output.typeName} with ${input.typeName}: " +
            "no implicit casting allowed; use an explicit ConversionMorphism"
          ))

  /**
   * Unify product type fields. All fields in the input must be present in
   * the output with compatible types.
   */
  private def unifyProductFields(
      outputFields: Vector[(String, CdmeType)],
      inputFields: Vector[(String, CdmeType)],
      registry: SubtypeRegistry
  ): Either[ValidationError, Vector[(String, CdmeType)]] =
    val outputMap = outputFields.toMap
    val errors = inputFields.collect:
      case (name, inputType) if !outputMap.contains(name) =>
        s"Missing field '$name' of type ${inputType.typeName}"
      case (name, inputType) if outputMap.contains(name) &&
          unify(outputMap(name), inputType, registry).isLeft =>
        s"Field '$name': cannot unify ${outputMap(name).typeName} with ${inputType.typeName}"

    if errors.isEmpty then Right(inputFields)
    else Left(ValidationError.TypeMismatch(errors.mkString("; ")))

  /**
   * Check whether a type is compatible with another (without producing unified type).
   *
   * @param from     source type
   * @param to       target type
   * @param registry subtype registry
   * @return true if types are compatible
   */
  def isCompatible(
      from: CdmeType,
      to: CdmeType,
      registry: SubtypeRegistry
  ): Boolean =
    unify(from, to, registry).isRight
