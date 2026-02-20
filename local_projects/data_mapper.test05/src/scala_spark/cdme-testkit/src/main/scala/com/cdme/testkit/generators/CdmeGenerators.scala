package com.cdme.testkit.generators

// Implements: (test infrastructure for validating all REQ-* keys)

import com.cdme.model.adjoint.{AdjointPair, ContainmentType, LossyContainment, SelfAdjoint}
import com.cdme.model.config.Epoch
import com.cdme.model.entity.{AccessControlList, Attribute, Entity, EntityId, Role}
import com.cdme.model.error.CdmeError
import com.cdme.model.grain._
import com.cdme.model.monoid.{CountMonoid, Monoid, SumMonoid}
import com.cdme.model.morphism._
import com.cdme.model.types._
import com.cdme.model.version.ArtifactVersion
import org.scalacheck.{Arbitrary, Gen}

/**
 * ScalaCheck [[Arbitrary]] instances for all core CDME model types.
 *
 * These generators enable property-based testing across the entire CDME
 * type hierarchy. Each generator produces valid, well-formed instances
 * suitable for testing invariants, monoid laws, adjoint properties, and
 * type system rules.
 *
 * Usage:
 * {{{
 * import com.cdme.testkit.generators.CdmeGenerators._
 * import org.scalacheck.Arbitrary.arbitrary
 *
 * val randomEntity: Entity = arbitrary[Entity].sample.get
 * }}}
 */
object CdmeGenerators {

  // -------------------------------------------------------------------------
  // Identity types
  // -------------------------------------------------------------------------

  /** Generator for [[EntityId]]: lowercase alphanumeric identifiers. */
  implicit val arbEntityId: Arbitrary[EntityId] = Arbitrary {
    Gen.listOfN(8, Gen.alphaLowerChar).map(cs => EntityId(cs.mkString))
  }

  /** Generator for [[MorphismId]]: descriptive identifiers with underscores. */
  implicit val arbMorphismId: Arbitrary[MorphismId] = Arbitrary {
    for {
      prefix <- Gen.listOfN(4, Gen.alphaLowerChar).map(_.mkString)
      suffix <- Gen.listOfN(4, Gen.alphaLowerChar).map(_.mkString)
    } yield MorphismId(s"${prefix}_to_$suffix")
  }

  // -------------------------------------------------------------------------
  // Version
  // -------------------------------------------------------------------------

  /** Generator for [[ArtifactVersion]]: semantic version strings. */
  implicit val arbArtifactVersion: Arbitrary[ArtifactVersion] = Arbitrary {
    for {
      artifactId <- Gen.listOfN(6, Gen.alphaLowerChar).map(_.mkString)
      major      <- Gen.choose(0, 9)
      minor      <- Gen.choose(0, 99)
      patch      <- Gen.choose(0, 99)
    } yield ArtifactVersion(artifactId, s"$major.$minor.$patch")
  }

  // -------------------------------------------------------------------------
  // Grain
  // -------------------------------------------------------------------------

  /** Generator for [[Grain]]: all built-in grains plus custom grains. */
  implicit val arbGrain: Arbitrary[Grain] = Arbitrary {
    Gen.oneOf(
      Gen.const(Atomic),
      Gen.const(DailyAggregate),
      Gen.const(MonthlyAggregate),
      Gen.const(YearlyAggregate),
      for {
        name <- Gen.listOfN(5, Gen.alphaLowerChar).map(_.mkString)
        rank <- Gen.choose(1, 50)
      } yield CustomGrain(name, rank)
    )
  }

  // -------------------------------------------------------------------------
  // Type system
  // -------------------------------------------------------------------------

  /** Generator for [[PrimitiveKind]]: all 9 primitive kinds. */
  implicit val arbPrimitiveKind: Arbitrary[PrimitiveKind] = Arbitrary {
    Gen.oneOf(
      IntegerKind, LongKind, FloatKind, DoubleKind, DecimalKind,
      StringKind, BooleanKind, DateKind, TimestampKind
    )
  }

  /**
   * Generator for [[CdmeType]]: recursive generator with depth limit.
   *
   * To avoid infinite recursion, nested types (Sum, Product, Refinement,
   * Semantic, Option, List) are only generated at depth 0; beyond that,
   * only primitive types are produced.
   */
  implicit val arbCdmeType: Arbitrary[CdmeType] = Arbitrary {
    genCdmeType(maxDepth = 2)
  }

  /** Depth-limited CdmeType generator. */
  private def genCdmeType(maxDepth: Int): Gen[CdmeType] = {
    val genPrimitive: Gen[CdmeType] =
      Arbitrary.arbitrary[PrimitiveKind].map(PrimitiveType)

    if (maxDepth <= 0) {
      genPrimitive
    } else {
      Gen.oneOf(
        genPrimitive,
        // ProductType — 1 to 3 fields
        for {
          name   <- Gen.listOfN(5, Gen.alphaLowerChar).map(_.mkString)
          nFields <- Gen.choose(1, 3)
          fields <- Gen.listOfN(nFields, genNamedField(maxDepth - 1))
        } yield ProductType(name, fields),
        // SumType — 2 to 3 variants
        for {
          name     <- Gen.listOfN(5, Gen.alphaLowerChar).map(_.mkString)
          nVariants <- Gen.choose(2, 3)
          variants <- Gen.listOfN(nVariants, genCdmeType(maxDepth - 1))
        } yield SumType(name, variants),
        // RefinementType
        for {
          base      <- genCdmeType(maxDepth - 1)
          predicate <- genPredicate
        } yield RefinementType(base, predicate),
        // SemanticType
        for {
          name       <- Gen.listOfN(6, Gen.alphaLowerChar).map(_.mkString)
          underlying <- genCdmeType(maxDepth - 1)
        } yield SemanticType(name, underlying),
        // OptionType
        genCdmeType(maxDepth - 1).map(OptionType),
        // ListType
        genCdmeType(maxDepth - 1).map(ListType)
      )
    }
  }

  /** Generator for [[NamedField]]. */
  private def genNamedField(maxDepth: Int): Gen[NamedField] =
    for {
      name     <- Gen.listOfN(5, Gen.alphaLowerChar).map(_.mkString)
      cdmeType <- genCdmeType(maxDepth)
    } yield NamedField(name, cdmeType)

  /** Generator for [[Predicate]]. */
  private def genPredicate: Gen[Predicate] = Gen.oneOf(
    Gen.choose(0, 100).map(v => GreaterThan(v)),
    Gen.choose(0, 100).map(v => LessThan(v)),
    for {
      min <- Gen.choose(0, 50)
      max <- Gen.choose(51, 100)
    } yield InRange(min, max),
    Gen.const(NonEmpty())
  )

  // -------------------------------------------------------------------------
  // Attribute
  // -------------------------------------------------------------------------

  /** Generator for [[Attribute]]. */
  implicit val arbAttribute: Arbitrary[Attribute] = Arbitrary {
    for {
      name     <- Gen.listOfN(6, Gen.alphaLowerChar).map(_.mkString)
      cdmeType <- genCdmeType(1)
      nullable <- Gen.oneOf(true, false)
    } yield Attribute(name, cdmeType, nullable)
  }

  // -------------------------------------------------------------------------
  // Cardinality
  // -------------------------------------------------------------------------

  /** Generator for [[Cardinality]]. */
  implicit val arbCardinality: Arbitrary[Cardinality] = Arbitrary {
    Gen.oneOf(OneToOne, ManyToOne, OneToMany)
  }

  // -------------------------------------------------------------------------
  // ContainmentType
  // -------------------------------------------------------------------------

  /** Generator for [[ContainmentType]]. */
  implicit val arbContainmentType: Arbitrary[ContainmentType] = Arbitrary {
    Gen.oneOf(SelfAdjoint, LossyContainment)
  }

  // -------------------------------------------------------------------------
  // Entity
  // -------------------------------------------------------------------------

  /** Generator for [[Entity]]: 1 to 5 attributes, optional ACL. */
  implicit val arbEntity: Arbitrary[Entity] = Arbitrary {
    for {
      id         <- Arbitrary.arbitrary[EntityId]
      name       <- Gen.listOfN(8, Gen.alphaLowerChar).map(_.mkString)
      grain      <- Arbitrary.arbitrary[Grain]
      nAttrs     <- Gen.choose(1, 5)
      attributes <- Gen.listOfN(nAttrs, Arbitrary.arbitrary[Attribute])
      hasAcl     <- Gen.oneOf(true, false)
      acl        <- if (hasAcl) genAcl.map(Some(_)) else Gen.const(None)
      version    <- Arbitrary.arbitrary[ArtifactVersion]
    } yield Entity(id, name, grain, attributes, acl, version)
  }

  /** Generator for [[AccessControlList]]. */
  private def genAcl: Gen[AccessControlList] =
    for {
      nRoles <- Gen.choose(1, 3)
      roles  <- Gen.listOfN(nRoles, Gen.listOfN(6, Gen.alphaLowerChar).map(cs => Role(cs.mkString)))
    } yield AccessControlList(roles.toSet)

  // -------------------------------------------------------------------------
  // Morphism
  // -------------------------------------------------------------------------

  /**
   * Generator for [[Morphism]]: produces one of the three subtypes.
   *
   * The adjoint pair uses trivial forward/backward functions suitable
   * for property testing (not for semantic correctness).
   */
  implicit val arbMorphism: Arbitrary[Morphism] = Arbitrary {
    Gen.oneOf(genStructuralMorphism, genComputationalMorphism, genAlgebraicMorphism)
  }

  /** Generator for [[StructuralMorphism]]. */
  private def genStructuralMorphism: Gen[StructuralMorphism] =
    for {
      id          <- Arbitrary.arbitrary[MorphismId]
      domain      <- Arbitrary.arbitrary[EntityId]
      codomain    <- Arbitrary.arbitrary[EntityId]
      cardinality <- Arbitrary.arbitrary[Cardinality]
      containment <- Arbitrary.arbitrary[ContainmentType]
      leftCol     <- Gen.listOfN(4, Gen.alphaLowerChar).map(_.mkString)
      rightCol    <- Gen.listOfN(4, Gen.alphaLowerChar).map(_.mkString)
      version     <- Arbitrary.arbitrary[ArtifactVersion]
    } yield StructuralMorphism(
      id = id,
      domain = domain,
      codomain = codomain,
      cardinality = cardinality,
      adjoint = trivialAdjoint(containment),
      joinCondition = ColumnEquality(leftCol, rightCol),
      acl = None,
      version = version
    )

  /** Generator for [[ComputationalMorphism]]. */
  private def genComputationalMorphism: Gen[ComputationalMorphism] =
    for {
      id            <- Arbitrary.arbitrary[MorphismId]
      domain        <- Arbitrary.arbitrary[EntityId]
      codomain      <- Arbitrary.arbitrary[EntityId]
      cardinality   <- Arbitrary.arbitrary[Cardinality]
      containment   <- Arbitrary.arbitrary[ContainmentType]
      colName       <- Gen.listOfN(5, Gen.alphaLowerChar).map(_.mkString)
      deterministic <- Gen.oneOf(true, false)
      version       <- Arbitrary.arbitrary[ArtifactVersion]
    } yield ComputationalMorphism(
      id = id,
      domain = domain,
      codomain = codomain,
      cardinality = cardinality,
      adjoint = trivialAdjoint(containment),
      expression = ColumnRef(colName),
      deterministic = deterministic,
      acl = None,
      version = version
    )

  /** Generator for [[AlgebraicMorphism]]. */
  private def genAlgebraicMorphism: Gen[AlgebraicMorphism] =
    for {
      id          <- Arbitrary.arbitrary[MorphismId]
      domain      <- Arbitrary.arbitrary[EntityId]
      codomain    <- Arbitrary.arbitrary[EntityId]
      containment <- Arbitrary.arbitrary[ContainmentType]
      targetGrain <- Arbitrary.arbitrary[Grain]
      version     <- Arbitrary.arbitrary[ArtifactVersion]
    } yield AlgebraicMorphism(
      id = id,
      domain = domain,
      codomain = codomain,
      cardinality = ManyToOne,
      adjoint = trivialAdjoint(containment),
      monoid = SumMonoid.asInstanceOf[Monoid[Any]],
      targetGrain = targetGrain,
      acl = None,
      version = version
    )

  /**
   * Create a trivial adjoint pair for testing. The forward function
   * always succeeds with the input unchanged; the backward function
   * returns a singleton set.
   */
  private def trivialAdjoint(containment: ContainmentType): AdjointPair[Any, Any] =
    AdjointPair[Any, Any](
      forward = (a: Any) => Right(a),
      backward = (b: Any) => Set(b),
      containmentType = containment
    )

  // -------------------------------------------------------------------------
  // Epoch
  // -------------------------------------------------------------------------

  /** Generator for [[Epoch]]. */
  implicit val arbEpoch: Arbitrary[Epoch] = Arbitrary {
    for {
      id     <- Gen.listOfN(10, Gen.alphaNumChar).map(_.mkString)
      dayOff <- Gen.choose(0, 365)
    } yield {
      val start = java.time.Instant.parse("2024-01-01T00:00:00Z").plusSeconds(dayOff * 86400L)
      val end   = start.plusSeconds(86400L)
      Epoch(id, start, end, Map("business_date" -> id))
    }
  }
}
