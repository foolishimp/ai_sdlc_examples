package cdme.taxagent

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Interactive runner for generating unique business instances.
 *
 * Creates a complete business data package in a dedicated folder with:
 * - profile.jsonl         - Business metadata
 * - cost_code_mappings.jsonl - Org codes → Tax category mappings
 * - ledger_entries.jsonl  - Atomic transaction ledger (source grain)
 * - tax_form_sections.jsonl - Aggregated tax form (target grain)
 * - generation_summary.json - Statistics and generation metadata
 *
 * Usage:
 *   sbt "Test / runMain cdme.taxagent.TaxAgentRunner"
 *   sbt "Test / runMain cdme.taxagent.TaxAgentRunner MANUFACTURING 'Acme Industries' 2024"
 *   sbt "Test / runMain cdme.taxagent.TaxAgentRunner CONGLOMERATE 'MegaCorp Holdings' 2024 100"
 *
 * Business Types:
 *   PERSONAL        - Individual taxpayer (Form 1040)
 *   MANUFACTURING   - Manufacturing company (Form 1120)
 *   FINANCIAL       - Financial services (Form 1120)
 *   RETAIL_MFG      - Retail + Manufacturing hybrid
 *   TECH_FIN        - Technology + Financial (Fintech) hybrid
 *   CONGLOMERATE    - All business types combined
 */
object TaxAgentRunner {

  private val businessTypes = Map(
    "PERSONAL" -> ("Individual Taxpayer", "Form 1040 - Personal income, deductions, credits"),
    "MANUFACTURING" -> ("Manufacturing Company", "Form 1120 - COGS, inventory, depreciation, R&D"),
    "FINANCIAL" -> ("Financial Services", "Form 1120 - Interest income, trading, provisions, regulatory capital"),
    "RETAIL_MFG" -> ("Retail-Manufacturing Hybrid", "Form 1120 - Both retail sales and manufacturing operations"),
    "TECH_FIN" -> ("Tech-Financial (Fintech)", "Form 1120 - SaaS revenue + lending/payments"),
    "CONGLOMERATE" -> ("Diversified Conglomerate", "Form 1120 - Industrial, financial, retail, tech divisions")
  )

  def main(args: Array[String]): Unit = {
    printBanner()

    val (businessTypeCode, businessName, taxYear, entriesPerAccount) = parseArgs(args)

    // Generate unique seed from timestamp
    val seed = System.currentTimeMillis()

    // Create output directory with timestamp
    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
    val safeName = businessName.toLowerCase.replaceAll("[^a-z0-9]", "-").replaceAll("-+", "-")
    val outputDir = s"test-data/tax/businesses/${timestamp}_$safeName"

    // Look up business type
    val generator = new TaxAgentDataGenerator(seed)
    val businessType = lookupBusinessType(generator, businessTypeCode)

    println(s"\n${"=" * 60}")
    println(s"GENERATING BUSINESS INSTANCE")
    println(s"${"=" * 60}")
    println(s"  Name:              $businessName")
    println(s"  Type:              ${businessType.name} (${businessType.code})")
    println(s"  Tax Form:          ${businessType.taxFormType}")
    println(s"  Tax Year:          $taxYear")
    println(s"  Entries/Account:   $entriesPerAccount")
    println(s"  Seed:              $seed")
    println(s"  Output:            $outputDir")
    println(s"${"=" * 60}\n")

    // Generate the business
    generator.generateBusiness(
      businessType = businessType,
      businessName = businessName,
      taxYear = taxYear,
      outputDir = outputDir,
      entriesPerAccount = entriesPerAccount
    )

    printUsageHints(outputDir)
  }

  private def printBanner(): Unit = {
    println()
    println("╔══════════════════════════════════════════════════════════════╗")
    println("║           TAX AGENT TEST DATA GENERATOR                      ║")
    println("║           Unique Business Instance Creator                   ║")
    println("╚══════════════════════════════════════════════════════════════╝")
    println()
    println("Available Business Types:")
    println("─" * 60)
    businessTypes.foreach { case (code, (name, desc)) =>
      println(f"  $code%-15s $name")
      println(f"  ${" " * 15} └─ $desc")
    }
    println("─" * 60)
  }

  private def parseArgs(args: Array[String]): (String, String, Int, Int) = {
    val businessTypeCode = args.lift(0).getOrElse {
      println("\nNo business type specified. Using RETAIL_MFG (hybrid example)")
      "RETAIL_MFG"
    }

    val businessName = args.lift(1).getOrElse {
      val timestamp = System.currentTimeMillis() % 10000
      s"Demo Business $timestamp"
    }

    val taxYear = args.lift(2).map(_.toInt).getOrElse(2024)
    val entriesPerAccount = args.lift(3).map(_.toInt).getOrElse(12)

    (businessTypeCode.toUpperCase, businessName, taxYear, entriesPerAccount)
  }

  private def lookupBusinessType(generator: TaxAgentDataGenerator, code: String): generator.BusinessType = {
    code match {
      case "PERSONAL" => generator.BusinessType.Personal
      case "MANUFACTURING" => generator.BusinessType.Manufacturing
      case "FINANCIAL" => generator.BusinessType.FinancialServices
      case "RETAIL_MFG" => generator.BusinessType.RetailManufacturing
      case "TECH_FIN" => generator.BusinessType.TechFinancial
      case "CONGLOMERATE" => generator.BusinessType.Conglomerate
      case _ =>
        println(s"Unknown business type: $code")
        println("Defaulting to RETAIL_MFG")
        generator.BusinessType.RetailManufacturing
    }
  }

  private def printUsageHints(outputDir: String): Unit = {
    println()
    println("═" * 60)
    println("NEXT STEPS")
    println("═" * 60)
    println()
    println("1. View generated data:")
    println(s"   ls -la $outputDir/")
    println()
    println("2. Examine ledger entries (source grain):")
    println(s"   head -5 $outputDir/ledger_entries.jsonl | jq .")
    println()
    println("3. View cost code → tax category mappings:")
    println(s"   cat $outputDir/cost_code_mappings.jsonl | jq -s '.[0:5]'")
    println()
    println("4. See aggregated tax form sections (target grain):")
    println(s"   cat $outputDir/tax_form_sections.jsonl | jq .")
    println()
    println("5. Run the mapper transformation (when implemented):")
    println(s"   sbt 'Test / runMain cdme.taxagent.TaxFormTransformer $outputDir'")
    println()
    println("═" * 60)
  }
}

/**
 * Batch generator for creating multiple business instances at once.
 *
 * Usage:
 *   sbt "Test / runMain cdme.taxagent.TaxAgentBatchRunner"
 *
 * Creates one instance of each business type for comprehensive testing.
 */
object TaxAgentBatchRunner {

  def main(args: Array[String]): Unit = {
    println()
    println("╔══════════════════════════════════════════════════════════════╗")
    println("║           TAX AGENT BATCH GENERATOR                          ║")
    println("║           Creating All 6 Business Types                      ║")
    println("╚══════════════════════════════════════════════════════════════╝")
    println()

    val taxYear = args.lift(0).map(_.toInt).getOrElse(2024)
    val entriesPerAccount = args.lift(1).map(_.toInt).getOrElse(12)
    val baseSeed = args.lift(2).map(_.toLong).getOrElse(42L)

    val businesses = List(
      ("PERSONAL", "John Smith Individual"),
      ("MANUFACTURING", "Acme Manufacturing Co"),
      ("FINANCIAL", "First National Bank"),
      ("RETAIL_MFG", "HomeGoods Direct"),
      ("TECH_FIN", "PayFlow Technologies"),
      ("CONGLOMERATE", "MegaCorp Holdings")
    )

    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
    val batchDir = s"test-data/tax/batch_$timestamp"

    println(s"Tax Year: $taxYear")
    println(s"Entries per account: $entriesPerAccount")
    println(s"Base seed: $baseSeed")
    println(s"Output directory: $batchDir")
    println()

    businesses.zipWithIndex.foreach { case ((typeCode, name), idx) =>
      val seed = baseSeed + idx
      val generator = new TaxAgentDataGenerator(seed)
      val businessType = typeCode match {
        case "PERSONAL" => generator.BusinessType.Personal
        case "MANUFACTURING" => generator.BusinessType.Manufacturing
        case "FINANCIAL" => generator.BusinessType.FinancialServices
        case "RETAIL_MFG" => generator.BusinessType.RetailManufacturing
        case "TECH_FIN" => generator.BusinessType.TechFinancial
        case "CONGLOMERATE" => generator.BusinessType.Conglomerate
      }

      val safeName = name.toLowerCase.replaceAll("[^a-z0-9]", "-")
      val outputDir = s"$batchDir/$safeName"

      println(s"\n[${ idx + 1 }/6] Generating: $name ($typeCode)")
      println("-" * 50)

      generator.generateBusiness(
        businessType = businessType,
        businessName = name,
        taxYear = taxYear,
        outputDir = outputDir,
        entriesPerAccount = entriesPerAccount
      )
    }

    println()
    println("=" * 60)
    println("BATCH GENERATION COMPLETE")
    println("=" * 60)
    println(s"Generated 6 business instances in: $batchDir")
    println()
    println("To explore:")
    println(s"  ls -la $batchDir/")
    println()
  }
}
