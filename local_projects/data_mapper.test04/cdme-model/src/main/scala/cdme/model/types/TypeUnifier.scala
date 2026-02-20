// Implements: REQ-F-TYP-003, REQ-F-TYP-004, REQ-BR-TYP-001
package cdme.model.types

import cdme.model.error.ValidationError

object TypeUnifier {

  def unify(
      output: CdmeType,
      input: CdmeType,
      registry: SubtypeRegistry
  ): Either[ValidationError, CdmeType] =
    if (output == input)
      Right(input)
    else if (registry.isSubtypeOf(output, input))
      Right(input)
    else
      (output, input) match {
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
      }

  private def unifyProductFields(
      outputFields: Vector[(String, CdmeType)],
      inputFields: Vector[(String, CdmeType)],
      registry: SubtypeRegistry
  ): Either[ValidationError, Vector[(String, CdmeType)]] = {
    val outputMap = outputFields.toMap
    val errors = inputFields.collect {
      case (name, inputType) if !outputMap.contains(name) =>
        s"Missing field '$name' of type ${inputType.typeName}"
      case (name, inputType) if outputMap.contains(name) &&
          unify(outputMap(name), inputType, registry).isLeft =>
        s"Field '$name': cannot unify ${outputMap(name).typeName} with ${inputType.typeName}"
    }

    if (errors.isEmpty) Right(inputFields)
    else Left(ValidationError.TypeMismatch(errors.mkString("; ")))
  }

  def isCompatible(
      from: CdmeType,
      to: CdmeType,
      registry: SubtypeRegistry
  ): Boolean =
    unify(from, to, registry).isRight
}
