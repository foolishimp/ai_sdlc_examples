package cdme.taxagent

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.io.{File, PrintWriter}
import scala.util.Random

/**
 * Large-scale batch generator for Tax Agent test data.
 *
 * Generates:
 * - 20 businesses (mix of Manufacturing, Financial, Retail-Mfg, Tech-Fin, Conglomerate)
 * - 10 individuals (Personal taxpayers)
 *
 * Each business has 100K to 1M ledger entries based on business size.
 *
 * Usage:
 *   sbt "Test / runMain cdme.taxagent.TaxAgentLargeBatchRunner"
 *   sbt "Test / runMain cdme.taxagent.TaxAgentLargeBatchRunner 2024 42"
 */
object TaxAgentLargeBatchRunner {

  // Business definitions with realistic names and sizes
  case class BusinessDef(
    name: String,
    typeCode: String,
    size: String,  // SMALL, MEDIUM, LARGE, XLARGE
    entriesPerAccount: Int
  )

  // 20 Businesses - mix of types and sizes
  val businesses: List[BusinessDef] = List(
    // Manufacturing (5)
    BusinessDef("Acme Manufacturing Corp", "MANUFACTURING", "LARGE", 2000),
    BusinessDef("Pacific Steel Industries", "MANUFACTURING", "XLARGE", 4000),
    BusinessDef("Midwest Auto Parts Inc", "MANUFACTURING", "MEDIUM", 800),
    BusinessDef("Precision Instruments LLC", "MANUFACTURING", "SMALL", 400),
    BusinessDef("Global Plastics Group", "MANUFACTURING", "LARGE", 1500),

    // Financial Services (4)
    BusinessDef("First National Bank", "FINANCIAL", "XLARGE", 5000),
    BusinessDef("Coastal Credit Union", "FINANCIAL", "MEDIUM", 1000),
    BusinessDef("Summit Investment Partners", "FINANCIAL", "LARGE", 2500),
    BusinessDef("Metro Savings Bank", "FINANCIAL", "MEDIUM", 800),

    // Retail-Manufacturing Hybrid (4)
    BusinessDef("HomeGoods Direct Inc", "RETAIL_MFG", "LARGE", 1800),
    BusinessDef("Artisan Furniture Co", "RETAIL_MFG", "MEDIUM", 600),
    BusinessDef("Farm Fresh Foods", "RETAIL_MFG", "LARGE", 2200),
    BusinessDef("Outdoor Equipment Warehouse", "RETAIL_MFG", "MEDIUM", 700),

    // Tech-Financial / Fintech (4)
    BusinessDef("PayFlow Technologies", "TECH_FIN", "XLARGE", 3500),
    BusinessDef("CryptoLend Inc", "TECH_FIN", "LARGE", 2000),
    BusinessDef("QuickPay Solutions", "TECH_FIN", "MEDIUM", 900),
    BusinessDef("Digital Banking Corp", "TECH_FIN", "LARGE", 1500),

    // Conglomerates (3)
    BusinessDef("MegaCorp Holdings", "CONGLOMERATE", "XLARGE", 6000),
    BusinessDef("United Industries Group", "CONGLOMERATE", "XLARGE", 5000),
    BusinessDef("Diversified Enterprises LLC", "CONGLOMERATE", "LARGE", 2500)
  )

  // 10 Individuals with varied income profiles
  case class IndividualDef(
    name: String,
    profile: String,  // HIGH_INCOME, MIDDLE_INCOME, INVESTOR, SELF_EMPLOYED
    entriesPerAccount: Int
  )

  val individuals: List[IndividualDef] = List(
    IndividualDef("John Smith", "HIGH_INCOME", 50),
    IndividualDef("Sarah Johnson", "INVESTOR", 100),
    IndividualDef("Michael Chen", "SELF_EMPLOYED", 80),
    IndividualDef("Emily Williams", "MIDDLE_INCOME", 30),
    IndividualDef("Robert Garcia", "HIGH_INCOME", 60),
    IndividualDef("Jennifer Martinez", "INVESTOR", 120),
    IndividualDef("David Kim", "SELF_EMPLOYED", 90),
    IndividualDef("Lisa Anderson", "MIDDLE_INCOME", 25),
    IndividualDef("James Wilson", "HIGH_INCOME", 70),
    IndividualDef("Maria Rodriguez", "INVESTOR", 150)
  )

  def main(args: Array[String]): Unit = {
    val taxYear = args.lift(0).map(_.toInt).getOrElse(2024)
    val baseSeed = args.lift(1).map(_.toLong).getOrElse(42L)

    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
    val batchDir = s"test-data/tax/large_batch_$timestamp"

    println()
    println("╔══════════════════════════════════════════════════════════════════════╗")
    println("║           TAX AGENT LARGE BATCH GENERATOR                            ║")
    println("║           20 Businesses + 10 Individuals                             ║")
    println("╚══════════════════════════════════════════════════════════════════════╝")
    println()
    println(s"Configuration:")
    println(s"  Tax Year:       $taxYear")
    println(s"  Base Seed:      $baseSeed")
    println(s"  Output:         $batchDir")
    println(s"  Businesses:     ${businesses.size}")
    println(s"  Individuals:    ${individuals.size}")
    println()

    // Estimate total entries
    val estBusinessEntries = businesses.map { b =>
      val accountCount = getAccountCount(b.typeCode)
      accountCount * b.entriesPerAccount
    }.sum
    val estIndividualEntries = individuals.map(_.entriesPerAccount * 19).sum // 19 account types for Personal

    println(s"Estimated ledger entries:")
    println(s"  Businesses:     ~${formatNumber(estBusinessEntries)} entries")
    println(s"  Individuals:    ~${formatNumber(estIndividualEntries)} entries")
    println(s"  Total:          ~${formatNumber(estBusinessEntries + estIndividualEntries)} entries")
    println()
    println("=" * 70)

    val startTime = System.currentTimeMillis()
    var totalEntries = 0L
    var totalBusinesses = 0
    var totalIndividuals = 0

    // Create batch directory
    new File(batchDir).mkdirs()
    new File(s"$batchDir/businesses").mkdirs()
    new File(s"$batchDir/individuals").mkdirs()

    // Generate businesses
    println("\n[BUSINESSES]")
    println("-" * 70)

    businesses.zipWithIndex.foreach { case (biz, idx) =>
      val seed = baseSeed + idx
      val generator = new TaxAgentDataGenerator(seed)
      val businessType = lookupBusinessType(generator, biz.typeCode)

      val safeName = biz.name.toLowerCase.replaceAll("[^a-z0-9]", "-").replaceAll("-+", "-")
      val outputDir = s"$batchDir/businesses/$safeName"

      val bizStartTime = System.currentTimeMillis()
      print(f"[${idx + 1}%2d/20] ${biz.name}%-35s (${biz.typeCode}%-12s ${biz.size}%-7s) ... ")

      generator.generateBusiness(
        businessType = businessType,
        businessName = biz.name,
        taxYear = taxYear,
        outputDir = outputDir,
        entriesPerAccount = biz.entriesPerAccount
      )

      // Count actual entries
      val entryCount = countLines(s"$outputDir/ledger_entries.jsonl")
      totalEntries += entryCount
      totalBusinesses += 1

      val elapsed = (System.currentTimeMillis() - bizStartTime) / 1000.0
      println(f"${formatNumber(entryCount)}%10s entries (${elapsed}%.1fs)")
    }

    // Generate individuals
    println("\n[INDIVIDUALS]")
    println("-" * 70)

    individuals.zipWithIndex.foreach { case (ind, idx) =>
      val seed = baseSeed + 1000 + idx
      val generator = new TaxAgentDataGenerator(seed)

      val safeName = ind.name.toLowerCase.replaceAll("[^a-z0-9]", "-").replaceAll("-+", "-")
      val outputDir = s"$batchDir/individuals/$safeName"

      val indStartTime = System.currentTimeMillis()
      print(f"[${idx + 1}%2d/10] ${ind.name}%-25s (${ind.profile}%-15s) ... ")

      generator.generateBusiness(
        businessType = generator.BusinessType.Personal,
        businessName = ind.name,
        taxYear = taxYear,
        outputDir = outputDir,
        entriesPerAccount = ind.entriesPerAccount
      )

      val entryCount = countLines(s"$outputDir/ledger_entries.jsonl")
      totalEntries += entryCount
      totalIndividuals += 1

      val elapsed = (System.currentTimeMillis() - indStartTime) / 1000.0
      println(f"${formatNumber(entryCount)}%10s entries (${elapsed}%.1fs)")
    }

    val totalElapsed = (System.currentTimeMillis() - startTime) / 1000.0

    // Write batch summary
    writeBatchSummary(batchDir, taxYear, baseSeed, totalBusinesses, totalIndividuals, totalEntries, totalElapsed)

    println()
    println("=" * 70)
    println("BATCH GENERATION COMPLETE")
    println("=" * 70)
    println()
    println(f"  Businesses generated:    $totalBusinesses")
    println(f"  Individuals generated:   $totalIndividuals")
    println(f"  Total ledger entries:    ${formatNumber(totalEntries)}")
    println(f"  Total time:              ${totalElapsed}%.1f seconds")
    println(f"  Entries/second:          ${formatNumber((totalEntries / totalElapsed).toLong)}")
    println()
    println(s"  Output directory:        $batchDir")
    println()
    println("To explore:")
    println(s"  ls -la $batchDir/businesses/")
    println(s"  ls -la $batchDir/individuals/")
    println(s"  cat $batchDir/batch_summary.json | jq .")
    println()
  }

  private def getAccountCount(typeCode: String): Int = typeCode match {
    case "PERSONAL" => 19
    case "MANUFACTURING" => 22
    case "FINANCIAL" => 22
    case "RETAIL_MFG" => 13
    case "TECH_FIN" => 14
    case "CONGLOMERATE" => 15
    case _ => 15
  }

  private def lookupBusinessType(generator: TaxAgentDataGenerator, code: String): generator.BusinessType = {
    code match {
      case "PERSONAL" => generator.BusinessType.Personal
      case "MANUFACTURING" => generator.BusinessType.Manufacturing
      case "FINANCIAL" => generator.BusinessType.FinancialServices
      case "RETAIL_MFG" => generator.BusinessType.RetailManufacturing
      case "TECH_FIN" => generator.BusinessType.TechFinancial
      case "CONGLOMERATE" => generator.BusinessType.Conglomerate
      case _ => generator.BusinessType.RetailManufacturing
    }
  }

  private def countLines(filePath: String): Long = {
    val file = new File(filePath)
    if (file.exists()) {
      scala.io.Source.fromFile(file).getLines().size.toLong
    } else 0L
  }

  private def formatNumber(n: Long): String = {
    if (n >= 1000000) f"${n / 1000000.0}%.1fM"
    else if (n >= 1000) f"${n / 1000.0}%.1fK"
    else n.toString
  }

  private def writeBatchSummary(
    batchDir: String,
    taxYear: Int,
    baseSeed: Long,
    totalBusinesses: Int,
    totalIndividuals: Int,
    totalEntries: Long,
    totalElapsed: Double
  ): Unit = {
    val writer = new PrintWriter(new File(s"$batchDir/batch_summary.json"))
    writer.write(
      s"""{
         |  "batch_type": "large_batch",
         |  "tax_year": $taxYear,
         |  "base_seed": $baseSeed,
         |  "generated_at": "${LocalDateTime.now()}",
         |  "businesses": {
         |    "count": $totalBusinesses,
         |    "types": ["MANUFACTURING", "FINANCIAL", "RETAIL_MFG", "TECH_FIN", "CONGLOMERATE"]
         |  },
         |  "individuals": {
         |    "count": $totalIndividuals,
         |    "profiles": ["HIGH_INCOME", "MIDDLE_INCOME", "INVESTOR", "SELF_EMPLOYED"]
         |  },
         |  "statistics": {
         |    "total_ledger_entries": $totalEntries,
         |    "generation_time_seconds": $totalElapsed,
         |    "entries_per_second": ${(totalEntries / totalElapsed).toLong}
         |  }
         |}""".stripMargin
    )
    writer.close()
  }
}
