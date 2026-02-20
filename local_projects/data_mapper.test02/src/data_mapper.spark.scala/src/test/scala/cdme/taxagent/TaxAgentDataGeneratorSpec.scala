package cdme.taxagent

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.io.File
import scala.io.Source

/**
 * Unit tests for TaxAgentDataGenerator
 *
 * Validates:
 * - All 6 business types generate correctly
 * - Cost code hierarchy follows dot notation
 * - Tax category mappings are valid
 * - Ledger entries have correct structure
 * - Aggregated tax form sections sum correctly
 * - Deterministic generation (same seed = same data)
 */
class TaxAgentDataGeneratorSpec extends AnyFlatSpec with Matchers {

  // ============================================
  // Business Type Tests
  // ============================================

  "TaxAgentDataGenerator" should "define 6 business types (3 pure, 3 hybrid)" in {
    val generator = new TaxAgentDataGenerator(seed = 42L)

    generator.BusinessType.all.size shouldBe 6

    // Pure types
    generator.BusinessType.Personal.code shouldBe "PERSONAL"
    generator.BusinessType.Manufacturing.code shouldBe "MANUFACTURING"
    generator.BusinessType.FinancialServices.code shouldBe "FINANCIAL"

    // Hybrid types
    generator.BusinessType.RetailManufacturing.code shouldBe "RETAIL_MFG"
    generator.BusinessType.TechFinancial.code shouldBe "TECH_FIN"
    generator.BusinessType.Conglomerate.code shouldBe "CONGLOMERATE"
  }

  it should "assign correct tax form types" in {
    val generator = new TaxAgentDataGenerator(seed = 42L)

    generator.BusinessType.Personal.taxFormType shouldBe "1040"
    generator.BusinessType.Manufacturing.taxFormType shouldBe "1120"
    generator.BusinessType.FinancialServices.taxFormType shouldBe "1120"
    generator.BusinessType.RetailManufacturing.taxFormType shouldBe "1120"
    generator.BusinessType.TechFinancial.taxFormType shouldBe "1120"
    generator.BusinessType.Conglomerate.taxFormType shouldBe "1120"
  }

  it should "lookup business types by code" in {
    val generator = new TaxAgentDataGenerator(seed = 42L)

    generator.BusinessType.fromCode("PERSONAL") shouldBe Some(generator.BusinessType.Personal)
    generator.BusinessType.fromCode("MANUFACTURING") shouldBe Some(generator.BusinessType.Manufacturing)
    generator.BusinessType.fromCode("INVALID") shouldBe None
  }

  // ============================================
  // Division/Department Hierarchy Tests
  // ============================================

  "Personal business type" should "have income, deductions, and credits divisions" in {
    val generator = new TaxAgentDataGenerator(seed = 42L)
    val personal = generator.BusinessType.Personal

    personal.divisions.map(_.code) should contain allOf ("income", "deductions", "credits")
  }

  "Manufacturing business type" should "have sales, COGS, and opex divisions" in {
    val generator = new TaxAgentDataGenerator(seed = 42L)
    val mfg = generator.BusinessType.Manufacturing

    mfg.divisions.map(_.code) should contain allOf ("sales", "cogs", "opex")
  }

  "Financial services business type" should "have interest, trading, fees, provisions, and capital divisions" in {
    val generator = new TaxAgentDataGenerator(seed = 42L)
    val fin = generator.BusinessType.FinancialServices

    fin.divisions.map(_.code) should contain allOf ("interest", "trading", "fees", "provisions", "capital")
  }

  "Conglomerate business type" should "have multiple subsidiary divisions" in {
    val generator = new TaxAgentDataGenerator(seed = 42L)
    val conglom = generator.BusinessType.Conglomerate

    conglom.divisions.map(_.code) should contain allOf (
      "industrial", "finance_sub", "retail_sub", "tech_sub", "holding"
    )
  }

  // ============================================
  // Cost Code Mapping Tests
  // ============================================

  "Cost code mappings" should "follow dot hierarchy pattern" in {
    val generator = new TaxAgentDataGenerator(seed = 42L)
    val mappings = generator.generateCostCodeMappings("ACME", generator.BusinessType.Manufacturing)

    mappings should not be empty

    // Check dot hierarchy format: businessid.division.department.account
    mappings.foreach { mapping =>
      val parts = mapping.org_cost_code.split("\\.")
      parts.length shouldBe 4
      parts(0) shouldBe "acme" // lowercased business id
    }
  }

  it should "map to valid tax categories" in {
    val generator = new TaxAgentDataGenerator(seed = 42L)
    val mappings = generator.generateCostCodeMappings("TEST", generator.BusinessType.Manufacturing)

    // All mappings should have valid tax categories
    mappings.foreach { mapping =>
      generator.taxCategories.keys should contain(mapping.tax_category)
    }
  }

  it should "include form line and schedule references" in {
    val generator = new TaxAgentDataGenerator(seed = 42L)
    val mappings = generator.generateCostCodeMappings("TEST", generator.BusinessType.Personal)

    // Personal type should have Schedule A references for itemized deductions
    val mortgageMapping = mappings.find(_.org_cost_code.contains("mortgage_int"))
    mortgageMapping shouldBe defined
    mortgageMapping.get.schedule_ref shouldBe "Schedule A"
  }

  // ============================================
  // Ledger Entry Generation Tests
  // ============================================

  "Ledger entry generation" should "create entries for all account types" in {
    val generator = new TaxAgentDataGenerator(seed = 42L)
    val entries = generator.generateLedgerEntries("TEST", generator.BusinessType.Manufacturing, 2024, entriesPerAccount = 4)

    entries should not be empty

    // Should have entries for multiple divisions
    val divisions = entries.map(_.division).distinct
    divisions should contain allOf ("Sales & Revenue", "Cost of Goods Sold", "Operating Expenses")
  }

  it should "use correct org cost code format" in {
    val generator = new TaxAgentDataGenerator(seed = 42L)
    val entries = generator.generateLedgerEntries("ACME", generator.BusinessType.Personal, 2024, entriesPerAccount = 2)

    entries.foreach { entry =>
      entry.org_cost_code should startWith("acme.")
      entry.org_cost_code.split("\\.").length shouldBe 4
    }
  }

  it should "generate ~95% validated entries and ~5% pending" in {
    val generator = new TaxAgentDataGenerator(seed = 42L)
    val entries = generator.generateLedgerEntries("TEST", generator.BusinessType.Manufacturing, 2024, entriesPerAccount = 100)

    val validatedCount = entries.count(_.status == "VALIDATED")
    val pendingCount = entries.count(_.status == "PENDING")

    // Should be roughly 95% validated (allow ±10% variance due to randomness)
    val validatedRatio = validatedCount.toDouble / entries.size
    validatedRatio should be > 0.85
    validatedRatio should be < 1.0

    pendingCount should be > 0
  }

  it should "assign correct tax categories to entries" in {
    val generator = new TaxAgentDataGenerator(seed = 42L)
    val entries = generator.generateLedgerEntries("TEST", generator.BusinessType.Personal, 2024, entriesPerAccount = 2)

    // Salary entries should map to W2_WAGES
    val salaryEntries = entries.filter(_.account_type == "salary")
    salaryEntries.foreach { entry =>
      entry.tax_category shouldBe "W2_WAGES"
    }

    // Dividend entries should map to appropriate dividend categories
    val divEntries = entries.filter(_.account_type.startsWith("div_"))
    divEntries.foreach { entry =>
      entry.tax_category should (be("QUALIFIED_DIVIDENDS") or be("ORDINARY_DIVIDENDS"))
    }
  }

  it should "generate entries within the tax year" in {
    val generator = new TaxAgentDataGenerator(seed = 42L)
    val entries = generator.generateLedgerEntries("TEST", generator.BusinessType.Personal, 2024, entriesPerAccount = 12)

    entries.foreach { entry =>
      entry.tax_year shouldBe 2024
      entry.entry_date should startWith("2024-")
    }
  }

  // ============================================
  // Tax Form Section Aggregation Tests
  // ============================================

  "Tax form section generation" should "aggregate ledger entries by tax category" in {
    val generator = new TaxAgentDataGenerator(seed = 42L)
    val entries = generator.generateLedgerEntries("TEST", generator.BusinessType.Personal, 2024, entriesPerAccount = 12)
    val sections = generator.generateTaxFormSections("TEST", 2024, entries)

    sections should not be empty

    // Each section should have entry count
    sections.foreach { section =>
      section.entry_count should be > 0
    }
  }

  it should "only include validated entries in aggregation" in {
    val generator = new TaxAgentDataGenerator(seed = 42L)
    val entries = generator.generateLedgerEntries("TEST", generator.BusinessType.Personal, 2024, entriesPerAccount = 100)
    val sections = generator.generateTaxFormSections("TEST", 2024, entries)

    val totalAggregatedEntries = sections.map(_.entry_count).sum
    val validatedEntries = entries.count(_.status == "VALIDATED")

    totalAggregatedEntries shouldBe validatedEntries
  }

  it should "correctly sum amounts for each tax category" in {
    val generator = new TaxAgentDataGenerator(seed = 42L)
    val entries = generator.generateLedgerEntries("TEST", generator.BusinessType.Manufacturing, 2024, entriesPerAccount = 12)
    val sections = generator.generateTaxFormSections("TEST", 2024, entries)

    // Verify sum for one category
    val cogsSection = sections.find(_.tax_category == "COGS_MATERIALS")
    if (cogsSection.isDefined) {
      val expectedSum = entries
        .filter(e => e.status == "VALIDATED" && e.tax_category == "COGS_MATERIALS")
        .map(_.amount)
        .sum

      cogsSection.get.total_amount shouldBe expectedSum
    }
  }

  it should "mark income categories as is_income=true" in {
    val generator = new TaxAgentDataGenerator(seed = 42L)
    val entries = generator.generateLedgerEntries("TEST", generator.BusinessType.Personal, 2024, entriesPerAccount = 12)
    val sections = generator.generateTaxFormSections("TEST", 2024, entries)

    val incomeSection = sections.find(_.tax_category == "W2_WAGES")
    incomeSection shouldBe defined
    incomeSection.get.is_income shouldBe true
  }

  // ============================================
  // Deterministic Generation Tests
  // ============================================

  "Deterministic generation" should "produce same data with same seed" in {
    val generator1 = new TaxAgentDataGenerator(seed = 12345L)
    val generator2 = new TaxAgentDataGenerator(seed = 12345L)

    val entries1 = generator1.generateLedgerEntries("TEST", generator1.BusinessType.Personal, 2024, entriesPerAccount = 10)
    val entries2 = generator2.generateLedgerEntries("TEST", generator2.BusinessType.Personal, 2024, entriesPerAccount = 10)

    entries1.size shouldBe entries2.size

    // Compare first few entries (amounts should match exactly)
    entries1.take(5).zip(entries2.take(5)).foreach { case (e1, e2) =>
      e1.entry_id shouldBe e2.entry_id
      e1.amount shouldBe e2.amount
      e1.tax_category shouldBe e2.tax_category
    }
  }

  it should "produce different data with different seeds" in {
    val generator1 = new TaxAgentDataGenerator(seed = 11111L)
    val generator2 = new TaxAgentDataGenerator(seed = 22222L)

    val entries1 = generator1.generateLedgerEntries("TEST", generator1.BusinessType.Personal, 2024, entriesPerAccount = 10)
    val entries2 = generator2.generateLedgerEntries("TEST", generator2.BusinessType.Personal, 2024, entriesPerAccount = 10)

    // Amounts should be different (statistically extremely unlikely to match)
    val amounts1 = entries1.map(_.amount)
    val amounts2 = entries2.map(_.amount)

    amounts1 should not equal amounts2
  }

  // ============================================
  // Hybrid Business Type Tests
  // ============================================

  "RetailManufacturing hybrid" should "have both manufacturing and retail divisions" in {
    val generator = new TaxAgentDataGenerator(seed = 42L)
    val retailMfg = generator.BusinessType.RetailManufacturing

    retailMfg.divisions.map(_.code) should contain allOf ("manufacturing", "retail", "corporate")
  }

  it should "generate entries for both retail and manufacturing operations" in {
    val generator = new TaxAgentDataGenerator(seed = 42L)
    val entries = generator.generateLedgerEntries("TEST", generator.BusinessType.RetailManufacturing, 2024, entriesPerAccount = 4)

    val divisions = entries.map(_.division).distinct
    divisions should contain("Manufacturing Operations")
    divisions should contain("Retail Operations")
  }

  "TechFinancial hybrid" should "have technology and financial divisions" in {
    val generator = new TaxAgentDataGenerator(seed = 42L)
    val techFin = generator.BusinessType.TechFinancial

    techFin.divisions.map(_.code) should contain allOf ("technology", "financial", "corporate")
  }

  it should "generate both SaaS and lending entries" in {
    val generator = new TaxAgentDataGenerator(seed = 42L)
    val entries = generator.generateLedgerEntries("TEST", generator.BusinessType.TechFinancial, 2024, entriesPerAccount = 4)

    val accountTypes = entries.map(_.account_type).distinct

    // Should have tech-related accounts
    accountTypes should contain("subscription_revenue")

    // Should have financial-related accounts
    accountTypes should contain("loan_interest")
  }

  // ============================================
  // Tax Category Coverage Tests
  // ============================================

  "Tax categories" should "cover all major IRS form lines" in {
    val generator = new TaxAgentDataGenerator(seed = 42L)

    // Check for key tax categories
    generator.taxCategories.keys should contain allOf (
      "W2_WAGES",
      "INTEREST_INCOME",
      "QUALIFIED_DIVIDENDS",
      "LT_CAPITAL_GAINS",
      "STANDARD_DEDUCTION",
      "MORTGAGE_INTEREST",
      "GROSS_RECEIPTS",
      "COGS_MATERIALS",
      "DEPRECIATION"
    )
  }

  it should "have form line references for all categories" in {
    val generator = new TaxAgentDataGenerator(seed = 42L)

    generator.taxCategories.values.foreach { category =>
      category.formLine should not be empty
    }
  }

  // ============================================
  // EIN Generation Test
  // ============================================

  "EIN generation" should "produce valid format" in {
    val generator = new TaxAgentDataGenerator(seed = 42L)

    for (_ <- 1 to 10) {
      val ein = generator.generateEIN()
      ein should fullyMatch regex """[0-9]{2}-[0-9]{7}"""
    }
  }

  // ============================================
  // Amount Range Tests
  // ============================================

  "Amount generation" should "respect typical ranges for account types" in {
    val generator = new TaxAgentDataGenerator(seed = 42L)

    // Personal salary should be in reasonable range
    val personalType = generator.BusinessType.Personal
    val salaryAccount = personalType.divisions
      .flatMap(_.departments)
      .flatMap(_.accountTypes)
      .find(_.code == "salary")

    salaryAccount shouldBe defined
    val (min, max) = salaryAccount.get.typicalRange
    min should be >= BigDecimal(0)
    max should be > min
  }

  // ============================================
  // Full Generation Integration Test
  // ============================================

  "Full business generation" should "create all required files" in {
    val generator = new TaxAgentDataGenerator(seed = 42L)
    val outputDir = s"target/test-data/tax-test-${System.currentTimeMillis()}"

    generator.generateBusiness(
      businessType = generator.BusinessType.RetailManufacturing,
      businessName = "Test Company",
      taxYear = 2024,
      outputDir = outputDir,
      entriesPerAccount = 4
    )

    // Check files exist
    new File(s"$outputDir/profile.jsonl").exists() shouldBe true
    new File(s"$outputDir/cost_code_mappings.jsonl").exists() shouldBe true
    new File(s"$outputDir/ledger_entries.jsonl").exists() shouldBe true
    new File(s"$outputDir/tax_form_sections.jsonl").exists() shouldBe true
    new File(s"$outputDir/generation_summary.json").exists() shouldBe true

    // Clean up
    deleteDirectory(new File(outputDir))
  }

  private def deleteDirectory(dir: File): Unit = {
    if (dir.isDirectory) {
      dir.listFiles().foreach(deleteDirectory)
    }
    dir.delete()
  }
}
