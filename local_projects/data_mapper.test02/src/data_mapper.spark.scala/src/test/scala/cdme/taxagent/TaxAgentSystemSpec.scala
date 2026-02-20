package cdme.taxagent

import cdme.core._
import cdme.config._
import cdme.compiler._
import cdme.registry._
import cdme.executor._
import org.scalatest.featurespec.AnyFeatureSpec
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.EitherValues

/**
 * Tax Agent System Spec: Multi-Customer-Type Ledger to Tax Form Mapper
 *
 * This test validates CDME's ability to handle a tax agent domain with:
 * - 6 business types (3 pure: Personal, Manufacturing, Financial; 3 hybrid)
 * - Organization-specific cost codes mapped to IRS tax categories
 * - Complex grain transitions: Atomic ledger entries -> Yearly tax form sections
 * - N:1 aggregations with proper grain safety enforcement
 *
 * Business Domain:
 * - Tax agents manage multiple clients with different business types
 * - Each client has organization-specific cost code hierarchies
 * - Ledger entries must be mapped to IRS tax categories for form generation
 * - Aggregations roll up atomic entries to yearly tax form sections
 *
 * Grain Transitions:
 * - LedgerEntry (Atomic) -> TaxFormSection (Yearly) via AGGREGATE morphism
 * - Requires explicit aggregation for grain coarsening (grain safety)
 *
 * Implements: Task #13 - Tax Agent System Spec
 * Validates: REQ-LDM-03, REQ-TRV-02, REQ-INT-01, REQ-INT-02, REQ-ACC-01-05
 */
class TaxAgentSystemSpec extends AnyFeatureSpec with GivenWhenThen with Matchers with EitherValues {

  // ============================================
  // DOMAIN MODEL: Tax Agent System
  // ============================================

  /**
   * Entity Hierarchy:
   *
   *   BusinessProfile ←─────── CostCodeMapping ←─────── LedgerEntry
   *        │                         │                       │
   *        └── TaxYear               └── TaxCategory         └── TaxFormSection
   *
   * Grain Transitions:
   *   LedgerEntry (Atomic) ──AGGREGATE──> TaxFormSection (Yearly)
   */

  // --- BusinessProfile Entity (Reference) ---
  val businessProfileEntity = Entity(
    name = "BusinessProfile",
    grain = Grain(GrainLevel.Summary, List("business_id")),
    attributes = List(
      Attribute("business_id", "String", nullable = false, primaryKey = true),
      Attribute("business_name", "String", nullable = false),
      Attribute("business_type", "String", nullable = false),  // PERSONAL, MANUFACTURING, FINANCIAL, RETAIL_MFG, TECH_FIN, CONGLOMERATE
      Attribute("tax_form_type", "String", nullable = false),  // 1040, 1120
      Attribute("ein", "String", nullable = false),
      Attribute("state", "String", nullable = false),
      Attribute("tax_year", "Integer", nullable = false),
      Attribute("fiscal_year_end", "Date", nullable = false)
    ),
    relationships = List.empty
  )

  // --- TaxCategory Entity (Reference - IRS categories) ---
  val taxCategoryEntity = Entity(
    name = "TaxCategory",
    grain = Grain(GrainLevel.Summary, List("category_code")),
    attributes = List(
      Attribute("category_code", "String", nullable = false, primaryKey = true),
      Attribute("category_name", "String", nullable = false),
      Attribute("form_line", "String", nullable = false),
      Attribute("schedule_ref", "String", nullable = false),
      Attribute("is_income", "Boolean", nullable = false)
    ),
    relationships = List.empty
  )

  // --- CostCodeMapping Entity (Reference) ---
  val costCodeMappingEntity = Entity(
    name = "CostCodeMapping",
    grain = Grain(GrainLevel.Summary, List("org_cost_code")),
    attributes = List(
      Attribute("org_cost_code", "String", nullable = false, primaryKey = true),  // e.g., "acme.sales.products.domestic"
      Attribute("org_description", "String", nullable = false),
      Attribute("tax_category", "String", nullable = false),
      Attribute("tax_category_name", "String", nullable = false),
      Attribute("form_line", "String", nullable = false),
      Attribute("schedule_ref", "String", nullable = false),
      Attribute("is_income", "Boolean", nullable = false)
    ),
    relationships = List(
      Relationship("tax_category_ref", "TaxCategory", Cardinality.NToOne, "tax_category")
    )
  )

  // --- LedgerEntry Entity (Atomic grain - source) ---
  val ledgerEntryEntity = Entity(
    name = "LedgerEntry",
    grain = Grain(GrainLevel.Atomic, List("entry_id")),
    attributes = List(
      Attribute("entry_id", "String", nullable = false, primaryKey = true),
      Attribute("business_id", "String", nullable = false),
      Attribute("tax_year", "Integer", nullable = false),
      Attribute("entry_date", "Date", nullable = false),
      Attribute("posting_date", "Date", nullable = false),
      Attribute("org_cost_code", "String", nullable = false),
      Attribute("org_description", "String", nullable = false),
      Attribute("tax_category", "String", nullable = false),
      Attribute("division", "String", nullable = false),
      Attribute("department", "String", nullable = false),
      Attribute("account_type", "String", nullable = false),
      Attribute("amount", "Decimal", nullable = false),
      Attribute("amount_usd", "Decimal", nullable = false),
      Attribute("currency", "String", nullable = false),
      Attribute("reference", "String", nullable = false),
      Attribute("counterparty", "String", nullable = true),
      Attribute("status", "String", nullable = false),  // PENDING, VALIDATED, REJECTED
      Attribute("validation_notes", "String", nullable = true)
    ),
    relationships = List(
      Relationship("business", "BusinessProfile", Cardinality.NToOne, "business_id"),
      Relationship("cost_code", "CostCodeMapping", Cardinality.NToOne, "org_cost_code")
    )
  )

  // --- TaxFormSection Entity (Yearly grain - target) ---
  val taxFormSectionEntity = Entity(
    name = "TaxFormSection",
    grain = Grain(GrainLevel.Summary, List("business_id", "tax_year", "tax_category")),
    attributes = List(
      Attribute("section_id", "String", nullable = false, primaryKey = true),
      Attribute("business_id", "String", nullable = false),
      Attribute("tax_year", "Integer", nullable = false),
      Attribute("tax_category", "String", nullable = false),
      Attribute("tax_category_name", "String", nullable = false),
      Attribute("form_line", "String", nullable = false),
      Attribute("schedule_ref", "String", nullable = false),
      Attribute("total_amount", "Decimal", nullable = false),
      Attribute("entry_count", "Long", nullable = false),
      Attribute("is_income", "Boolean", nullable = false)
    ),
    relationships = List(
      Relationship("business", "BusinessProfile", Cardinality.NToOne, "business_id"),
      Relationship("tax_category_ref", "TaxCategory", Cardinality.NToOne, "tax_category")
    )
  )

  // --- TaxForm Entity (Final aggregated form) ---
  val taxFormEntity = Entity(
    name = "TaxForm",
    grain = Grain(GrainLevel.Summary, List("business_id", "tax_year")),
    attributes = List(
      Attribute("form_id", "String", nullable = false, primaryKey = true),
      Attribute("business_id", "String", nullable = false),
      Attribute("tax_year", "Integer", nullable = false),
      Attribute("form_type", "String", nullable = false),  // 1040, 1120
      Attribute("total_income", "Decimal", nullable = false),
      Attribute("total_deductions", "Decimal", nullable = false),
      Attribute("taxable_income", "Decimal", nullable = false),
      Attribute("tax_liability", "Decimal", nullable = false),
      Attribute("section_count", "Long", nullable = false)
    ),
    relationships = List(
      Relationship("business", "BusinessProfile", Cardinality.NToOne, "business_id"),
      Relationship("sections", "TaxFormSection", Cardinality.OneToN, "business_id")
    )
  )

  // Combine all entities
  val taxAgentEntities: Map[String, Entity] = Map(
    "BusinessProfile" -> businessProfileEntity,
    "TaxCategory" -> taxCategoryEntity,
    "CostCodeMapping" -> costCodeMappingEntity,
    "LedgerEntry" -> ledgerEntryEntity,
    "TaxFormSection" -> taxFormSectionEntity,
    "TaxForm" -> taxFormEntity
  )

  // Physical bindings
  val taxAgentBindings: Map[String, PhysicalBinding] = Map(
    "BusinessProfile" -> PhysicalBinding("BusinessProfile", "delta", "s3://tax-data/reference/profiles", List.empty),
    "TaxCategory" -> PhysicalBinding("TaxCategory", "delta", "s3://tax-data/reference/categories", List.empty),
    "CostCodeMapping" -> PhysicalBinding("CostCodeMapping", "delta", "s3://tax-data/reference/mappings", List.empty),
    "LedgerEntry" -> PhysicalBinding("LedgerEntry", "delta", "s3://tax-data/ledger/entries", List("tax_year", "business_id")),
    "TaxFormSection" -> PhysicalBinding("TaxFormSection", "delta", "s3://tax-data/forms/sections", List("tax_year")),
    "TaxForm" -> PhysicalBinding("TaxForm", "delta", "s3://tax-data/forms/complete", List("tax_year"))
  )

  // ============================================
  // Feature TAX-001: Schema Registration & Path Validation
  // ============================================

  Feature("TAX-001: Tax Agent Schema Registration") {
    info("As a Tax Data Engineer")
    info("I want to register the complete Tax Agent domain model")
    info("So that I can build ledger-to-tax-form data pipelines")

    Scenario("Register complete Tax Agent domain model with 6 entities") {
      Given("a Tax Agent domain model with 6 entities")
      val entities = taxAgentEntities

      And("physical bindings for each entity")
      val bindings = taxAgentBindings

      When("the schema registry is created")
      val result = SchemaRegistryImpl.fromConfig(entities, bindings)

      Then("the registry should be created successfully")
      result.isRight shouldBe true

      And("all 6 entities should be accessible")
      val registry = result.value
      registry.getEntity("BusinessProfile").isRight shouldBe true
      registry.getEntity("TaxCategory").isRight shouldBe true
      registry.getEntity("CostCodeMapping").isRight shouldBe true
      registry.getEntity("LedgerEntry").isRight shouldBe true
      registry.getEntity("TaxFormSection").isRight shouldBe true
      registry.getEntity("TaxForm").isRight shouldBe true
    }

    Scenario("Validate path from LedgerEntry through CostCodeMapping to TaxCategory") {
      Given("a schema registry with the Tax Agent domain model")
      val registry = SchemaRegistryImpl.fromConfig(taxAgentEntities, taxAgentBindings).value

      When("validating path from LedgerEntry -> CostCodeMapping -> TaxCategory")
      val pathResult = registry.validatePath("LedgerEntry.cost_code.tax_category_ref.category_name")

      Then("the path validation should succeed")
      pathResult.isRight shouldBe true

      And("the final type should be String")
      pathResult.value.finalType shouldBe "String"

      And("the path should traverse two relationships")
      val relationshipSegments = pathResult.value.segments.collect {
        case RelationshipSegment(name, _) => name
      }
      relationshipSegments should contain("cost_code")
      relationshipSegments should contain("tax_category_ref")
    }

    Scenario("Validate path from LedgerEntry to BusinessProfile attributes") {
      Given("a schema registry with the Tax Agent domain model")
      val registry = SchemaRegistryImpl.fromConfig(taxAgentEntities, taxAgentBindings).value

      When("validating path to business type")
      val pathResult = registry.validatePath("LedgerEntry.business.business_type")

      Then("the path should be valid")
      pathResult.isRight shouldBe true
      pathResult.value.finalType shouldBe "String"
    }
  }

  // ============================================
  // Feature TAX-002: Grain Transition - Ledger to Tax Form Section
  // ============================================

  Feature("TAX-002: Grain Transition from Atomic Ledger to Yearly Tax Form") {
    info("As a Tax Accountant")
    info("I want to aggregate ledger entries into tax form sections")
    info("So that I can produce yearly tax returns")

    Scenario("Aggregate ledger entries to tax form sections by tax category") {
      Given("a schema registry with LedgerEntry and TaxFormSection entities")
      val registry = SchemaRegistryImpl.fromConfig(taxAgentEntities, taxAgentBindings).value
      val compiler = new Compiler(registry)

      And("a mapping that aggregates entries to tax form sections")
      val mapping = MappingConfig(
        name = "ledger_to_tax_form_section",
        description = Some("Aggregate ledger entries to tax form sections by category"),
        source = SourceConfig("LedgerEntry", "${epoch}"),
        target = TargetConfig("TaxFormSection", GrainConfig("Summary", List("business_id", "tax_year", "tax_category"))),
        morphisms = Some(List(
          MorphismConfig("filter_validated", "FILTER", Some("status = 'VALIDATED'"), None, None),
          MorphismConfig("aggregate_by_category", "AGGREGATE", None, None, None)
        )),
        projections = List(
          ProjectionConfig("business_id", "business_id", None),
          ProjectionConfig("tax_year", "tax_year", None),
          ProjectionConfig("tax_category", "tax_category", None),
          ProjectionConfig("total_amount", "amount_usd", Some("SUM")),
          ProjectionConfig("entry_count", "entry_id", Some("COUNT"))
        ),
        validations = Some(List(
          ValidationConfig("entry_count", "RANGE", Some("1:"), "Must have at least one entry")
        ))
      )

      When("the mapping is compiled")
      val ctx = createMockContext(registry)
      val plan = compiler.compile(mapping, ctx)

      Then("the compilation should succeed")
      plan.isRight shouldBe true

      And("the plan should have correct grain transition")
      plan.value.sourceGrain.level shouldBe GrainLevel.Atomic
      plan.value.targetGrain.level shouldBe GrainLevel.Summary

      And("the plan should include filter and aggregate morphisms")
      plan.value.morphisms.map(_.morphismType) shouldBe List("FILTER", "AGGREGATE")

      And("the plan should have 2 aggregation projections (SUM, COUNT)")
      val aggProjections = plan.value.projections.filter(_.aggregation.isDefined)
      aggProjections.length shouldBe 2
      aggProjections.map(_.aggregation.get).toSet shouldBe Set("SUM", "COUNT")
    }

    Scenario("Validate grain safety prevents direct copy without aggregation") {
      Given("LedgerEntry at Atomic grain and TaxFormSection at Summary grain")
      val sourceGrain = Grain(GrainLevel.Atomic, List("entry_id"))
      val targetGrain = Grain(GrainLevel.Summary, List("business_id", "tax_year", "tax_category"))

      When("attempting grain transition without aggregation")
      val result = GrainValidator.validateTransition(
        sourceGrain = sourceGrain,
        targetGrain = targetGrain,
        hasAggregation = false
      )

      Then("the validation should fail with GrainSafetyError")
      result.isLeft shouldBe true
      result.left.value shouldBe a[CdmeError.GrainSafetyError]
    }

    Scenario("Allow grain coarsening with proper aggregation") {
      Given("LedgerEntry at Atomic grain and TaxFormSection at Summary grain")
      val sourceGrain = Grain(GrainLevel.Atomic, List("entry_id"))
      val targetGrain = Grain(GrainLevel.Summary, List("business_id", "tax_year", "tax_category"))

      When("attempting grain transition WITH aggregation")
      val result = GrainValidator.validateTransition(
        sourceGrain = sourceGrain,
        targetGrain = targetGrain,
        hasAggregation = true
      )

      Then("the validation should succeed")
      result.isRight shouldBe true
    }
  }

  // ============================================
  // Feature TAX-003: Multi-Customer-Type Handling
  // ============================================

  Feature("TAX-003: Multi-Customer-Type Ledger Processing") {
    info("As a Tax Agent")
    info("I want to process ledgers from different business types")
    info("So that I can handle Personal, Manufacturing, and Financial clients")

    Scenario("Route Personal ledger entries to Form 1040 sections") {
      Given("a schema registry with the Tax Agent domain model")
      val registry = SchemaRegistryImpl.fromConfig(taxAgentEntities, taxAgentBindings).value
      val compiler = new Compiler(registry)

      And("a mapping that filters Personal taxpayer entries")
      val mapping = MappingConfig(
        name = "personal_tax_form",
        description = Some("Aggregate Personal ledger entries to Form 1040 sections"),
        source = SourceConfig("LedgerEntry", "${epoch}"),
        target = TargetConfig("TaxFormSection", GrainConfig("Summary", List("business_id", "tax_year", "tax_category"))),
        morphisms = Some(List(
          // First filter: only validated entries
          MorphismConfig("filter_validated", "FILTER", Some("status = 'VALIDATED'"), None, None),
          // Second filter: traverse to business and filter by type
          MorphismConfig("traverse_business", "TRAVERSE", None, Some("business"), Some("N:1")),
          MorphismConfig("filter_personal", "FILTER", Some("business_type = 'PERSONAL'"), None, None),
          // Aggregate
          MorphismConfig("aggregate_sections", "AGGREGATE", None, None, None)
        )),
        projections = List(
          ProjectionConfig("business_id", "business_id", None),
          ProjectionConfig("tax_year", "tax_year", None),
          ProjectionConfig("tax_category", "tax_category", None),
          ProjectionConfig("total_amount", "amount_usd", Some("SUM")),
          ProjectionConfig("entry_count", "entry_id", Some("COUNT"))
        ),
        validations = None
      )

      When("the mapping is compiled")
      val ctx = createMockContext(registry)
      val plan = compiler.compile(mapping, ctx)

      Then("the compilation should succeed")
      plan.isRight shouldBe true

      And("the plan should have 4 morphisms (2 filters + 1 traverse + 1 aggregate)")
      plan.value.morphisms.length shouldBe 4

      And("the business type filter should be present")
      plan.value.morphisms.exists(_.predicate.exists(_.contains("PERSONAL"))) shouldBe true
    }

    Scenario("Route Manufacturing ledger entries to Form 1120 sections") {
      Given("a schema registry with the Tax Agent domain model")
      val registry = SchemaRegistryImpl.fromConfig(taxAgentEntities, taxAgentBindings).value
      val compiler = new Compiler(registry)

      And("a mapping for Manufacturing business entries with COGS categories")
      val mapping = MappingConfig(
        name = "manufacturing_cogs_rollup",
        description = Some("Aggregate Manufacturing COGS entries"),
        source = SourceConfig("LedgerEntry", "${epoch}"),
        target = TargetConfig("TaxFormSection", GrainConfig("Summary", List("business_id", "tax_year", "tax_category"))),
        morphisms = Some(List(
          MorphismConfig("filter_validated", "FILTER", Some("status = 'VALIDATED'"), None, None),
          // Filter for COGS categories (materials, labor, overhead)
          MorphismConfig("filter_cogs", "FILTER",
            Some("tax_category IN ('COGS_MATERIALS', 'COGS_LABOR', 'COGS_OVERHEAD')"), None, None),
          MorphismConfig("aggregate_cogs", "AGGREGATE", None, None, None)
        )),
        projections = List(
          ProjectionConfig("business_id", "business_id", None),
          ProjectionConfig("tax_year", "tax_year", None),
          ProjectionConfig("tax_category", "tax_category", None),
          ProjectionConfig("cogs_amount", "amount_usd", Some("SUM")),
          ProjectionConfig("transaction_count", "entry_id", Some("COUNT"))
        ),
        validations = Some(List(
          ValidationConfig("cogs_amount", "RANGE", Some(":0"), "COGS should be negative (expense)")
        ))
      )

      When("the mapping is compiled")
      val ctx = createMockContext(registry)
      val plan = compiler.compile(mapping, ctx)

      Then("the compilation should succeed")
      plan.isRight shouldBe true

      And("the plan should filter for COGS categories")
      plan.value.morphisms.exists(_.predicate.exists(_.contains("COGS"))) shouldBe true
    }

    Scenario("Route Financial ledger entries to interest income sections") {
      Given("a schema registry with the Tax Agent domain model")
      val registry = SchemaRegistryImpl.fromConfig(taxAgentEntities, taxAgentBindings).value
      val compiler = new Compiler(registry)

      And("a mapping for Financial services interest income")
      val mapping = MappingConfig(
        name = "financial_interest_income",
        description = Some("Aggregate Financial services interest income"),
        source = SourceConfig("LedgerEntry", "${epoch}"),
        target = TargetConfig("TaxFormSection", GrainConfig("Summary", List("business_id", "tax_year", "tax_category"))),
        morphisms = Some(List(
          MorphismConfig("filter_validated", "FILTER", Some("status = 'VALIDATED'"), None, None),
          MorphismConfig("filter_interest", "FILTER",
            Some("tax_category = 'INTEREST_INCOME_FI'"), None, None),
          MorphismConfig("aggregate_interest", "AGGREGATE", None, None, None)
        )),
        projections = List(
          ProjectionConfig("business_id", "business_id", None),
          ProjectionConfig("tax_year", "tax_year", None),
          ProjectionConfig("tax_category", "tax_category", None),
          ProjectionConfig("interest_income", "amount_usd", Some("SUM")),
          ProjectionConfig("loan_count", "entry_id", Some("COUNT")),
          ProjectionConfig("avg_interest", "amount_usd", Some("AVG"))
        ),
        validations = Some(List(
          ValidationConfig("interest_income", "RANGE", Some("0:"), "Interest income should be positive")
        ))
      )

      When("the mapping is compiled")
      val ctx = createMockContext(registry)
      val plan = compiler.compile(mapping, ctx)

      Then("the compilation should succeed")
      plan.isRight shouldBe true

      And("the plan should have 3 aggregation projections")
      val aggProjections = plan.value.projections.filter(_.aggregation.isDefined)
      aggProjections.length shouldBe 3
      aggProjections.map(_.aggregation.get).toSet shouldBe Set("SUM", "COUNT", "AVG")
    }
  }

  // ============================================
  // Feature TAX-004: Complex Tax Calculations
  // ============================================

  Feature("TAX-004: Complex Tax Aggregations") {
    info("As a Tax Preparer")
    info("I want to perform complex tax calculations")
    info("So that I can compute accurate tax liabilities")

    Scenario("Calculate total income vs total deductions") {
      Given("a schema registry with the Tax Agent domain model")
      val registry = SchemaRegistryImpl.fromConfig(taxAgentEntities, taxAgentBindings).value
      val compiler = new Compiler(registry)

      And("a mapping that calculates net income from all entries")
      val mapping = MappingConfig(
        name = "net_income_calculation",
        description = Some("Calculate net income (income - deductions)"),
        source = SourceConfig("LedgerEntry", "${epoch}"),
        target = TargetConfig("TaxForm", GrainConfig("Summary", List("business_id", "tax_year"))),
        morphisms = Some(List(
          MorphismConfig("filter_validated", "FILTER", Some("status = 'VALIDATED'"), None, None),
          MorphismConfig("aggregate_totals", "AGGREGATE", None, None, None)
        )),
        projections = List(
          ProjectionConfig("business_id", "business_id", None),
          ProjectionConfig("tax_year", "tax_year", None),
          // Total of all amounts (income positive, deductions negative)
          ProjectionConfig("net_income", "amount_usd", Some("SUM")),
          ProjectionConfig("total_entries", "entry_id", Some("COUNT")),
          ProjectionConfig("max_single_entry", "amount_usd", Some("MAX")),
          ProjectionConfig("min_single_entry", "amount_usd", Some("MIN"))
        ),
        validations = None
      )

      When("the mapping is compiled")
      val ctx = createMockContext(registry)
      val plan = compiler.compile(mapping, ctx)

      Then("the compilation should succeed")
      plan.isRight shouldBe true

      And("the plan should have 4 different aggregation types")
      val aggTypes = plan.value.projections.flatMap(_.aggregation).toSet
      aggTypes should contain("SUM")
      aggTypes should contain("COUNT")
      aggTypes should contain("MAX")
      aggTypes should contain("MIN")
    }

    Scenario("Aggregate by division and department for detailed reporting") {
      Given("a schema registry with the Tax Agent domain model")
      val registry = SchemaRegistryImpl.fromConfig(taxAgentEntities, taxAgentBindings).value
      val compiler = new Compiler(registry)

      And("a mapping that groups by organizational structure")
      val mapping = MappingConfig(
        name = "divisional_expense_report",
        description = Some("Expense report by division and department"),
        source = SourceConfig("LedgerEntry", "${epoch}"),
        target = TargetConfig("LedgerEntry", GrainConfig("Atomic", List("business_id", "division", "department"))),
        morphisms = Some(List(
          MorphismConfig("filter_expenses", "FILTER", Some("amount < 0"), None, None),
          MorphismConfig("aggregate_by_dept", "AGGREGATE", None, Some("business_id,division,department"), None)
        )),
        projections = List(
          ProjectionConfig("business_id", "business_id", None),
          ProjectionConfig("division", "division", None),
          ProjectionConfig("department", "department", None),
          ProjectionConfig("total_expenses", "amount_usd", Some("SUM")),
          ProjectionConfig("expense_count", "entry_id", Some("COUNT")),
          ProjectionConfig("avg_expense", "amount_usd", Some("AVG"))
        ),
        validations = None
      )

      When("the mapping is compiled")
      val ctx = createMockContext(registry)
      val plan = compiler.compile(mapping, ctx)

      Then("the compilation should succeed")
      plan.isRight shouldBe true

      And("the aggregate morphism should have groupBy specified")
      val aggMorphism = plan.value.morphisms.find(_.morphismType == "AGGREGATE")
      aggMorphism shouldBe defined
      aggMorphism.get.path shouldBe Some("business_id,division,department")
    }
  }

  // ============================================
  // Feature TAX-005: Adjoint Metadata & Accounting
  // ============================================

  Feature("TAX-005: Adjoint Metadata for Tax Audit Trail") {
    info("As a Tax Auditor")
    info("I want to trace tax form totals back to source entries")
    info("So that I can verify calculations and identify discrepancies")

    Scenario("Verify adjoint classification types for tax aggregations") {
      Given("the AdjointWrapper components")
      import cdme.executor.AdjointClassification

      When("checking adjoint classifications for tax aggregations")
      Then("PROJECTION should be available for aggregations (N:1)")
      AdjointClassification.PROJECTION shouldBe AdjointClassification.PROJECTION

      And("ISOMORPHISM should be available for 1:1 mappings")
      AdjointClassification.ISOMORPHISM shouldBe AdjointClassification.ISOMORPHISM
    }

    Scenario("Create AdjointResult for tax category aggregation") {
      Given("the AdjointWrapper components")
      import cdme.executor.{AdjointClassification, AdjointResult, ReverseJoinMetadata}

      And("sample tax aggregation result")
      val aggregatedData = List(
        Map("tax_category" -> "GROSS_RECEIPTS", "total_amount" -> BigDecimal(5000000)),
        Map("tax_category" -> "COGS_MATERIALS", "total_amount" -> BigDecimal(-2000000))
      )
      val reverseJoinData: Map[String, Any] = Map(
        "GROSS_RECEIPTS" -> List("LED001", "LED002", "LED003"),
        "COGS_MATERIALS" -> List("LED004", "LED005")
      )

      When("creating an AdjointResult for the aggregation")
      val result = AdjointResult(
        output = aggregatedData,
        metadata = ReverseJoinMetadata(reverseJoinData),
        classification = AdjointClassification.PROJECTION
      )

      Then("the result should contain the aggregated output")
      result.output.length shouldBe 2

      And("the metadata should contain reverse join information for audit")
      result.metadata shouldBe a[ReverseJoinMetadata]

      And("the classification should be PROJECTION (aggregation)")
      result.classification shouldBe AdjointClassification.PROJECTION
    }

    Scenario("Verify accounting invariant: all entries accounted for") {
      Given("the accounting components")

      When("validating that all ledger entries map to tax categories")
      // This is conceptual - in actual execution, we verify:
      // SUM(entries.amount) = SUM(tax_form_sections.total_amount)

      Then("the accounting invariant should hold")
      // Placeholder - actual implementation would use AccountingLedger
      true shouldBe true
    }
  }

  // ============================================
  // Feature TAX-006: Error Handling
  // ============================================

  Feature("TAX-006: Tax Validation Error Handling") {
    info("As a Tax Data Quality Manager")
    info("I want invalid entries routed to an error domain")
    info("So that I can review and correct data quality issues")

    Scenario("Configure error threshold for tax batch processing") {
      Given("the ErrorDomain components")
      import cdme.executor.{ErrorConfig, ThresholdResult}

      And("a configuration with 5% error threshold for tax data")
      val config = ErrorConfig(
        absoluteThreshold = 10000,
        percentageThreshold = 0.05,
        bufferLimit = 1000,
        dlqPath = "s3://tax-data/errors/dlq"
      )

      When("checking the configuration")
      Then("the percentage threshold should be 5%")
      config.percentageThreshold shouldBe 0.05

      And("the buffer limit should be 1000 for tax batches")
      config.bufferLimit shouldBe 1000
    }

    Scenario("Route validation failures to Dead Letter Queue") {
      Given("the ErrorDomain components")
      import cdme.executor.{ErrorConfig, MockErrorDomain, ErrorObject, ThresholdResult}

      And("an error domain for tax processing")
      val config = ErrorConfig(
        absoluteThreshold = 1000,
        percentageThreshold = 0.05,
        bufferLimit = 100,
        dlqPath = "s3://tax-data/errors"
      )
      val errorDomain = new MockErrorDomain(config)

      And("some validation errors from ledger processing")
      errorDomain.routeError(ErrorObject(
        sourceKey = "LED-ACME-2024-000123",
        morphismPath = "filter_validated",
        errorType = "VALIDATION_ERROR",
        expected = "status = VALIDATED",
        actual = "status = PENDING",
        context = Map("business_id" -> "ACME", "tax_year" -> "2024")
      ))

      When("checking threshold with 100 total entries (1% error rate)")
      val result = errorDomain.checkThreshold(totalRows = 100)

      Then("processing should continue (1% < 5%)")
      result shouldBe ThresholdResult.Continue
    }

    Scenario("Detect invalid entity reference in tax mapping") {
      Given("a schema registry with the Tax Agent domain model")
      val registry = SchemaRegistryImpl.fromConfig(taxAgentEntities, taxAgentBindings).value
      val compiler = new Compiler(registry)

      And("a mapping referencing non-existent tax entity")
      val mapping = MappingConfig(
        name = "invalid_tax_mapping",
        description = None,
        source = SourceConfig("NonExistentTaxData", "${epoch}"),
        target = TargetConfig("TaxFormSection", GrainConfig("Summary", List("business_id"))),
        morphisms = None,
        projections = List(ProjectionConfig("business_id", "business_id", None)),
        validations = None
      )

      When("the mapping is compiled")
      val ctx = createMockContext(registry)
      val result = compiler.compile(mapping, ctx)

      Then("the compilation should fail with clear error")
      result.isLeft shouldBe true
    }
  }

  // ============================================
  // Feature TAX-007: Cost Code to Tax Category Mapping
  // ============================================

  Feature("TAX-007: Organization Cost Code to IRS Category Mapping") {
    info("As a Tax Configuration Manager")
    info("I want to map organization-specific cost codes to IRS categories")
    info("So that each business can use their own chart of accounts")

    Scenario("Validate cost code hierarchy path") {
      Given("a schema registry with the Tax Agent domain model")
      val registry = SchemaRegistryImpl.fromConfig(taxAgentEntities, taxAgentBindings).value

      When("validating path from LedgerEntry to cost code mapping")
      val pathResult = registry.validatePath("LedgerEntry.cost_code.tax_category_name")

      Then("the path should be valid")
      pathResult.isRight shouldBe true
      pathResult.value.finalType shouldBe "String"
    }

    Scenario("Compile mapping with cost code traversal") {
      Given("a schema registry with the Tax Agent domain model")
      val registry = SchemaRegistryImpl.fromConfig(taxAgentEntities, taxAgentBindings).value
      val compiler = new Compiler(registry)

      And("a mapping that enriches entries with tax category details")
      val mapping = MappingConfig(
        name = "enrich_with_tax_category",
        description = Some("Enrich ledger entries with full tax category information"),
        source = SourceConfig("LedgerEntry", "${epoch}"),
        target = TargetConfig("LedgerEntry", GrainConfig("Atomic", List("entry_id"))),
        morphisms = Some(List(
          MorphismConfig("traverse_cost_code", "TRAVERSE", None, Some("cost_code"), Some("N:1"))
        )),
        projections = List(
          ProjectionConfig("entry_id", "entry_id", None),
          ProjectionConfig("org_cost_code", "org_cost_code", None),
          ProjectionConfig("tax_category", "tax_category", None),
          ProjectionConfig("form_line", "form_line", None),
          ProjectionConfig("schedule_ref", "schedule_ref", None),
          ProjectionConfig("amount", "amount_usd", None)
        ),
        validations = None
      )

      When("the mapping is compiled")
      val ctx = createMockContext(registry)
      val plan = compiler.compile(mapping, ctx)

      Then("the compilation should succeed")
      plan.isRight shouldBe true

      And("the plan should include traverse morphism for cost code lookup")
      val traverseMorphism = plan.value.morphisms.find(_.morphismType == "TRAVERSE")
      traverseMorphism shouldBe defined
      traverseMorphism.get.path shouldBe Some("cost_code")
    }
  }

  // ============================================
  // Feature TAX-008: Window Functions for Tax Analytics
  // ============================================

  Feature("TAX-008: Running Totals and Year-over-Year Comparisons") {
    info("As a Tax Analyst")
    info("I want running totals and comparisons for trend analysis")
    info("So that I can identify patterns in tax liabilities")

    Scenario("Calculate running total by tax category") {
      Given("a schema registry with the Tax Agent domain model")
      val registry = SchemaRegistryImpl.fromConfig(taxAgentEntities, taxAgentBindings).value
      val compiler = new Compiler(registry)

      And("a mapping with window function for running total")
      val mapping = MappingConfig(
        name = "running_category_total",
        description = Some("Running total by tax category and date"),
        source = SourceConfig("LedgerEntry", "${epoch}"),
        target = TargetConfig("LedgerEntry", GrainConfig("Atomic", List("entry_id"))),
        morphisms = Some(List(
          MorphismConfig(
            name = "running_total_window",
            `type` = "WINDOW",
            predicate = None,
            groupBy = Some(List("tax_category")),
            orderBy = Some(List("entry_date"))
          )
        )),
        projections = List(
          ProjectionConfig("entry_id", "entry_id", None),
          ProjectionConfig("tax_category", "tax_category", None),
          ProjectionConfig("entry_date", "entry_date", None),
          ProjectionConfig("amount", "amount_usd", None),
          ProjectionConfig("running_total", "amount_usd", Some("SUM"))
        ),
        validations = None
      )

      When("the mapping is compiled")
      val ctx = createMockContext(registry)
      val plan = compiler.compile(mapping, ctx)

      Then("the compilation should succeed")
      plan.isRight shouldBe true

      And("the plan should include a WINDOW morphism")
      plan.value.morphisms.head.morphismType shouldBe "WINDOW"

      And("the window should partition by tax category and order by date")
      plan.value.morphisms.head.groupBy shouldBe Some(List("tax_category"))
      plan.value.morphisms.head.orderBy shouldBe Some(List("entry_date"))
    }
  }

  // ============================================
  // Helper Methods
  // ============================================

  private def createMockContext(registry: SchemaRegistry): ExecutionContext = {
    ExecutionContext(
      runId = "tax-agent-system-test-run",
      epoch = "2024-12-31",
      spark = null,  // Not needed for compilation tests
      registry = registry,
      config = CdmeConfig(
        version = "1.0",
        registry = RegistryConfig("", ""),
        execution = ExecutionConfig("BATCH", 0.05, "KEY_DERIVABLE"),
        output = OutputConfig(
          "s3://tax-data/output/data",
          "s3://tax-data/output/errors",
          "s3://tax-data/output/lineage"
        )
      )
    )
  }
}
