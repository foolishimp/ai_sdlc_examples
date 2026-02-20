package cdme.taxagent

import java.time.{LocalDate, LocalDateTime}
import java.time.format.DateTimeFormatter
import scala.util.Random
import scala.collection.mutable.ListBuffer
import java.io.{File, PrintWriter}
import io.circe.syntax._
import io.circe.generic.auto._
import io.circe.{Encoder, Json}

/**
 * Tax Agent Test Data Generator
 *
 * Generates realistic business ledger data for 6 business models:
 *
 * PURE BUSINESS TYPES:
 * 1. Personal (individuals) - Salary, investments, deductions
 * 2. Manufacturing (industrial) - COGS, inventory, depreciation
 * 3. Financial Services (banks/investment firms) - Trading, provisions, capital
 *
 * HYBRID BUSINESS TYPES:
 * 4. Retail-Manufacturing (makes and sells products) - Both retail revenue and COGS
 * 5. Tech-Financial (fintech companies) - Software revenue + trading/lending
 * 6. Conglomerate (multi-division) - All business types in one organization
 *
 * KEY FEATURES:
 * - Organization-specific cost codes (org.division.department.account)
 * - Tax department classification mapping (IRS Schedule categories)
 * - Deterministic generation (seed-based)
 * - Creates unique business instance per run
 *
 * GRAIN TRANSITIONS DEMONSTRATED:
 * - Atomic ledger entries → Yearly tax form (N:1 aggregation)
 * - Org cost codes → Tax classifications (many-to-one mapping)
 *
 * Usage:
 *   val generator = new TaxAgentDataGenerator(seed = 42L)
 *   generator.generateBusiness(
 *     businessType = BusinessType.RetailManufacturing,
 *     businessName = "Acme Corp",
 *     taxYear = 2024,
 *     outputDir = "test-data/tax/acme-corp"
 *   )
 */
class TaxAgentDataGenerator(seed: Long = System.currentTimeMillis()) {

  private val random = new Random(seed)

  // ============================================
  // Business Type Definitions
  // ============================================

  sealed trait BusinessType {
    def code: String
    def name: String
    def taxFormType: String
    def divisions: List[Division]
  }

  case class Division(
    code: String,
    name: String,
    departments: List[Department]
  )

  case class Department(
    code: String,
    name: String,
    accountTypes: List[AccountType]
  )

  case class AccountType(
    code: String,
    name: String,
    taxCategory: String,      // IRS category this maps to
    isIncome: Boolean,        // true = income, false = expense/deduction
    typicalRange: (BigDecimal, BigDecimal)
  )

  // ============================================
  // 6 Business Models
  // ============================================

  object BusinessType {

    // === PURE TYPE 1: Personal (Individual) ===
    case object Personal extends BusinessType {
      val code = "PERSONAL"
      val name = "Individual Taxpayer"
      val taxFormType = "1040"
      val divisions = List(
        Division("income", "Income Sources", List(
          Department("employment", "Employment", List(
            AccountType("salary", "Base Salary", "W2_WAGES", isIncome = true, (50000, 500000)),
            AccountType("bonus", "Annual Bonus", "W2_WAGES", isIncome = true, (5000, 100000)),
            AccountType("rsu", "RSU Vesting", "W2_WAGES", isIncome = true, (10000, 200000))
          )),
          Department("investments", "Investment Income", List(
            AccountType("div_qualified", "Qualified Dividends", "QUALIFIED_DIVIDENDS", isIncome = true, (100, 50000)),
            AccountType("div_ordinary", "Ordinary Dividends", "ORDINARY_DIVIDENDS", isIncome = true, (100, 20000)),
            AccountType("interest_bank", "Bank Interest", "INTEREST_INCOME", isIncome = true, (10, 5000)),
            AccountType("cap_gain_st", "Short-Term Capital Gains", "ST_CAPITAL_GAINS", isIncome = true, (-50000, 100000)),
            AccountType("cap_gain_lt", "Long-Term Capital Gains", "LT_CAPITAL_GAINS", isIncome = true, (-100000, 500000))
          )),
          Department("other", "Other Income", List(
            AccountType("rental", "Rental Income", "RENTAL_INCOME", isIncome = true, (0, 100000)),
            AccountType("freelance", "Freelance Income", "SELF_EMPLOYMENT", isIncome = true, (0, 50000))
          ))
        )),
        Division("deductions", "Deductions", List(
          Department("standard", "Standard Deductions", List(
            AccountType("std_deduction", "Standard Deduction", "STANDARD_DEDUCTION", isIncome = false, (14600, 29200))
          )),
          Department("itemized", "Itemized Deductions", List(
            AccountType("mortgage_int", "Mortgage Interest", "MORTGAGE_INTEREST", isIncome = false, (5000, 50000)),
            AccountType("property_tax", "Property Tax", "STATE_LOCAL_TAX", isIncome = false, (2000, 10000)),
            AccountType("state_tax", "State Income Tax", "STATE_LOCAL_TAX", isIncome = false, (1000, 50000)),
            AccountType("charity", "Charitable Contributions", "CHARITABLE", isIncome = false, (100, 50000))
          ))
        )),
        Division("credits", "Tax Credits", List(
          Department("family", "Family Credits", List(
            AccountType("child_credit", "Child Tax Credit", "CHILD_TAX_CREDIT", isIncome = false, (0, 6000)),
            AccountType("dependent_care", "Dependent Care Credit", "DEPENDENT_CARE_CREDIT", isIncome = false, (0, 6000))
          )),
          Department("education", "Education Credits", List(
            AccountType("aotc", "American Opportunity Credit", "EDUCATION_CREDIT", isIncome = false, (0, 2500)),
            AccountType("llc", "Lifetime Learning Credit", "EDUCATION_CREDIT", isIncome = false, (0, 2000))
          ))
        ))
      )
    }

    // === PURE TYPE 2: Manufacturing ===
    case object Manufacturing extends BusinessType {
      val code = "MANUFACTURING"
      val name = "Manufacturing Company"
      val taxFormType = "1120"
      val divisions = List(
        Division("sales", "Sales & Revenue", List(
          Department("products", "Product Sales", List(
            AccountType("product_domestic", "Domestic Product Sales", "GROSS_RECEIPTS", isIncome = true, (100000, 50000000)),
            AccountType("product_export", "Export Sales", "GROSS_RECEIPTS", isIncome = true, (50000, 20000000)),
            AccountType("spare_parts", "Spare Parts Revenue", "GROSS_RECEIPTS", isIncome = true, (10000, 5000000))
          )),
          Department("services", "Service Revenue", List(
            AccountType("maintenance", "Maintenance Contracts", "SERVICE_INCOME", isIncome = true, (10000, 2000000)),
            AccountType("installation", "Installation Services", "SERVICE_INCOME", isIncome = true, (5000, 1000000))
          ))
        )),
        Division("cogs", "Cost of Goods Sold", List(
          Department("materials", "Raw Materials", List(
            AccountType("raw_materials", "Raw Materials", "COGS_MATERIALS", isIncome = false, (50000, 20000000)),
            AccountType("components", "Purchased Components", "COGS_MATERIALS", isIncome = false, (20000, 10000000)),
            AccountType("packaging", "Packaging Materials", "COGS_MATERIALS", isIncome = false, (5000, 500000))
          )),
          Department("labor", "Direct Labor", List(
            AccountType("production_wages", "Production Wages", "COGS_LABOR", isIncome = false, (100000, 10000000)),
            AccountType("production_benefits", "Production Benefits", "COGS_LABOR", isIncome = false, (20000, 2000000))
          )),
          Department("overhead", "Manufacturing Overhead", List(
            AccountType("factory_rent", "Factory Rent/Lease", "COGS_OVERHEAD", isIncome = false, (50000, 2000000)),
            AccountType("factory_utilities", "Factory Utilities", "COGS_OVERHEAD", isIncome = false, (20000, 500000)),
            AccountType("equipment_maint", "Equipment Maintenance", "COGS_OVERHEAD", isIncome = false, (10000, 500000)),
            AccountType("depreciation_equip", "Equipment Depreciation", "DEPRECIATION", isIncome = false, (50000, 5000000))
          )),
          Department("inventory", "Inventory Adjustments", List(
            AccountType("inv_adjustment", "Inventory Adjustment", "INVENTORY_ADJ", isIncome = false, (-500000, 500000)),
            AccountType("inv_obsolescence", "Obsolescence Reserve", "INVENTORY_ADJ", isIncome = false, (0, 200000))
          ))
        )),
        Division("opex", "Operating Expenses", List(
          Department("admin", "General & Administrative", List(
            AccountType("admin_salaries", "Admin Salaries", "SALARIES_WAGES", isIncome = false, (100000, 5000000)),
            AccountType("office_rent", "Office Rent", "RENT_EXPENSE", isIncome = false, (20000, 500000)),
            AccountType("professional_fees", "Professional Fees", "PROFESSIONAL_FEES", isIncome = false, (10000, 500000)),
            AccountType("insurance", "Business Insurance", "INSURANCE", isIncome = false, (20000, 500000))
          )),
          Department("sales_marketing", "Sales & Marketing", List(
            AccountType("sales_salaries", "Sales Salaries", "SALARIES_WAGES", isIncome = false, (50000, 2000000)),
            AccountType("advertising", "Advertising", "ADVERTISING", isIncome = false, (10000, 1000000)),
            AccountType("trade_shows", "Trade Shows", "ADVERTISING", isIncome = false, (5000, 200000))
          )),
          Department("rd", "Research & Development", List(
            AccountType("rd_salaries", "R&D Salaries", "RD_EXPENSE", isIncome = false, (50000, 5000000)),
            AccountType("rd_materials", "R&D Materials", "RD_EXPENSE", isIncome = false, (10000, 1000000)),
            AccountType("patents", "Patent Costs", "AMORTIZATION", isIncome = false, (5000, 200000))
          ))
        ))
      )
    }

    // === PURE TYPE 3: Financial Services ===
    case object FinancialServices extends BusinessType {
      val code = "FINANCIAL"
      val name = "Financial Services Company"
      val taxFormType = "1120"
      val divisions = List(
        Division("interest", "Interest Income", List(
          Department("lending", "Lending Operations", List(
            AccountType("loan_interest_consumer", "Consumer Loan Interest", "INTEREST_INCOME_FI", isIncome = true, (1000000, 100000000)),
            AccountType("loan_interest_commercial", "Commercial Loan Interest", "INTEREST_INCOME_FI", isIncome = true, (5000000, 500000000)),
            AccountType("loan_interest_mortgage", "Mortgage Interest", "INTEREST_INCOME_FI", isIncome = true, (2000000, 200000000))
          )),
          Department("securities", "Securities Portfolio", List(
            AccountType("bond_interest", "Bond Interest", "INTEREST_INCOME_FI", isIncome = true, (500000, 50000000)),
            AccountType("repo_income", "Repo Income", "INTEREST_INCOME_FI", isIncome = true, (100000, 10000000))
          ))
        )),
        Division("trading", "Trading Operations", List(
          Department("equities", "Equity Trading", List(
            AccountType("equity_gains", "Equity Trading Gains", "TRADING_GAINS", isIncome = true, (-10000000, 50000000)),
            AccountType("equity_dividends", "Dividend Income", "DIVIDEND_INCOME_FI", isIncome = true, (100000, 10000000))
          )),
          Department("fixed_income", "Fixed Income Trading", List(
            AccountType("bond_trading_gains", "Bond Trading Gains", "TRADING_GAINS", isIncome = true, (-5000000, 20000000))
          )),
          Department("derivatives", "Derivatives", List(
            AccountType("derivatives_gains", "Derivatives Gains/Losses", "TRADING_GAINS", isIncome = true, (-20000000, 30000000)),
            AccountType("hedging_gains", "Hedging Gains/Losses", "TRADING_GAINS", isIncome = true, (-10000000, 10000000))
          ))
        )),
        Division("fees", "Fee Income", List(
          Department("advisory", "Advisory Services", List(
            AccountType("wealth_mgmt_fees", "Wealth Management Fees", "FEE_INCOME", isIncome = true, (1000000, 100000000)),
            AccountType("advisory_fees", "Advisory Fees", "FEE_INCOME", isIncome = true, (500000, 50000000))
          )),
          Department("banking", "Banking Fees", List(
            AccountType("account_fees", "Account Fees", "FEE_INCOME", isIncome = true, (100000, 10000000)),
            AccountType("transaction_fees", "Transaction Fees", "FEE_INCOME", isIncome = true, (500000, 50000000)),
            AccountType("card_fees", "Card Fees", "FEE_INCOME", isIncome = true, (200000, 20000000))
          ))
        )),
        Division("provisions", "Provisions & Reserves", List(
          Department("credit", "Credit Provisions", List(
            AccountType("loan_loss_provision", "Loan Loss Provision", "BAD_DEBT_EXPENSE", isIncome = false, (500000, 50000000)),
            AccountType("specific_reserves", "Specific Reserves", "BAD_DEBT_EXPENSE", isIncome = false, (100000, 10000000))
          )),
          Department("trading_reserves", "Trading Reserves", List(
            AccountType("trading_loss_reserve", "Trading Loss Reserve", "TRADING_LOSSES", isIncome = false, (100000, 5000000)),
            AccountType("cva_adjustment", "CVA Adjustment", "TRADING_LOSSES", isIncome = false, (50000, 2000000))
          ))
        )),
        Division("capital", "Regulatory Capital", List(
          Department("tier1", "Tier 1 Capital", List(
            AccountType("cet1_capital", "CET1 Capital", "REGULATORY_CAPITAL", isIncome = false, (100000000, 10000000000L)),
            AccountType("additional_tier1", "Additional Tier 1", "REGULATORY_CAPITAL", isIncome = false, (10000000, 1000000000))
          )),
          Department("rwa", "Risk-Weighted Assets", List(
            AccountType("credit_rwa", "Credit RWA", "REGULATORY_CAPITAL", isIncome = false, (500000000, 50000000000L)),
            AccountType("market_rwa", "Market RWA", "REGULATORY_CAPITAL", isIncome = false, (50000000, 5000000000L))
          ))
        ))
      )
    }

    // === HYBRID TYPE 4: Retail-Manufacturing ===
    case object RetailManufacturing extends BusinessType {
      val code = "RETAIL_MFG"
      val name = "Retail-Manufacturing Hybrid"
      val taxFormType = "1120"
      val divisions = List(
        // Manufacturing division (subset of Manufacturing)
        Division("manufacturing", "Manufacturing Operations", List(
          Department("production", "Production", List(
            AccountType("mfg_sales", "Manufactured Product Sales", "GROSS_RECEIPTS", isIncome = true, (500000, 20000000)),
            AccountType("mfg_raw_materials", "Raw Materials", "COGS_MATERIALS", isIncome = false, (200000, 8000000)),
            AccountType("mfg_labor", "Production Labor", "COGS_LABOR", isIncome = false, (100000, 4000000)),
            AccountType("mfg_overhead", "Factory Overhead", "COGS_OVERHEAD", isIncome = false, (50000, 2000000))
          ))
        )),
        // Retail division (pure retail operations)
        Division("retail", "Retail Operations", List(
          Department("stores", "Store Sales", List(
            AccountType("store_sales", "In-Store Sales", "GROSS_RECEIPTS", isIncome = true, (1000000, 50000000)),
            AccountType("online_sales", "Online Sales", "GROSS_RECEIPTS", isIncome = true, (500000, 30000000))
          )),
          Department("merchandise", "Merchandise", List(
            AccountType("purchased_goods", "Purchased Goods for Resale", "COGS_MERCHANDISE", isIncome = false, (400000, 25000000)),
            AccountType("freight_in", "Freight-In", "COGS_MATERIALS", isIncome = false, (20000, 1000000))
          )),
          Department("store_ops", "Store Operations", List(
            AccountType("store_rent", "Store Rent", "RENT_EXPENSE", isIncome = false, (100000, 5000000)),
            AccountType("store_labor", "Store Labor", "SALARIES_WAGES", isIncome = false, (200000, 10000000)),
            AccountType("store_utilities", "Store Utilities", "UTILITIES", isIncome = false, (20000, 500000))
          ))
        )),
        // Shared services
        Division("corporate", "Corporate", List(
          Department("admin", "Administration", List(
            AccountType("corp_salaries", "Corporate Salaries", "SALARIES_WAGES", isIncome = false, (100000, 3000000)),
            AccountType("corp_rent", "Corporate Office Rent", "RENT_EXPENSE", isIncome = false, (50000, 500000))
          ))
        ))
      )
    }

    // === HYBRID TYPE 5: Tech-Financial (Fintech) ===
    case object TechFinancial extends BusinessType {
      val code = "TECH_FIN"
      val name = "Fintech Company"
      val taxFormType = "1120"
      val divisions = List(
        // Technology/Software division
        Division("technology", "Technology Platform", List(
          Department("saas", "SaaS Revenue", List(
            AccountType("subscription_revenue", "Subscription Revenue", "SERVICE_INCOME", isIncome = true, (1000000, 100000000)),
            AccountType("platform_fees", "Platform Transaction Fees", "FEE_INCOME", isIncome = true, (500000, 50000000)),
            AccountType("api_revenue", "API Access Revenue", "SERVICE_INCOME", isIncome = true, (100000, 10000000))
          )),
          Department("tech_costs", "Technology Costs", List(
            AccountType("cloud_hosting", "Cloud Hosting", "TECH_EXPENSE", isIncome = false, (100000, 10000000)),
            AccountType("software_licenses", "Software Licenses", "TECH_EXPENSE", isIncome = false, (50000, 5000000)),
            AccountType("tech_salaries", "Engineering Salaries", "SALARIES_WAGES", isIncome = false, (500000, 30000000))
          ))
        )),
        // Financial services division
        Division("financial", "Financial Services", List(
          Department("lending", "Lending", List(
            AccountType("loan_interest", "Loan Interest Income", "INTEREST_INCOME_FI", isIncome = true, (500000, 50000000)),
            AccountType("loan_origination_fees", "Origination Fees", "FEE_INCOME", isIncome = true, (100000, 10000000))
          )),
          Department("payments", "Payments", List(
            AccountType("payment_processing_rev", "Payment Processing Revenue", "FEE_INCOME", isIncome = true, (200000, 20000000)),
            AccountType("interchange_rev", "Interchange Revenue", "FEE_INCOME", isIncome = true, (100000, 10000000))
          )),
          Department("provisions", "Credit Provisions", List(
            AccountType("fintech_loan_loss", "Loan Loss Provision", "BAD_DEBT_EXPENSE", isIncome = false, (50000, 5000000))
          ))
        )),
        // Shared
        Division("corporate", "Corporate", List(
          Department("general", "General & Admin", List(
            AccountType("corp_admin", "Corporate Admin", "SALARIES_WAGES", isIncome = false, (200000, 5000000)),
            AccountType("legal_compliance", "Legal & Compliance", "PROFESSIONAL_FEES", isIncome = false, (100000, 3000000))
          ))
        ))
      )
    }

    // === HYBRID TYPE 6: Conglomerate (All Types) ===
    case object Conglomerate extends BusinessType {
      val code = "CONGLOMERATE"
      val name = "Diversified Conglomerate"
      val taxFormType = "1120"
      val divisions = List(
        // Manufacturing subsidiary
        Division("industrial", "Industrial Division", List(
          Department("heavy_mfg", "Heavy Manufacturing", List(
            AccountType("industrial_sales", "Industrial Product Sales", "GROSS_RECEIPTS", isIncome = true, (10000000, 500000000)),
            AccountType("industrial_cogs", "Industrial COGS", "COGS_MATERIALS", isIncome = false, (5000000, 250000000)),
            AccountType("industrial_labor", "Industrial Labor", "COGS_LABOR", isIncome = false, (2000000, 100000000))
          ))
        )),
        // Financial subsidiary
        Division("finance_sub", "Financial Services Division", List(
          Department("banking_ops", "Banking Operations", List(
            AccountType("net_interest_income", "Net Interest Income", "INTEREST_INCOME_FI", isIncome = true, (5000000, 200000000)),
            AccountType("banking_fees", "Banking Fees", "FEE_INCOME", isIncome = true, (1000000, 50000000))
          )),
          Department("insurance_ops", "Insurance Operations", List(
            AccountType("premium_income", "Premium Income", "INSURANCE_INCOME", isIncome = true, (10000000, 500000000)),
            AccountType("claims_expense", "Claims Expense", "INSURANCE_EXPENSE", isIncome = false, (5000000, 300000000))
          ))
        )),
        // Retail subsidiary
        Division("retail_sub", "Retail Division", List(
          Department("consumer_retail", "Consumer Retail", List(
            AccountType("retail_sales", "Retail Sales", "GROSS_RECEIPTS", isIncome = true, (5000000, 200000000)),
            AccountType("retail_cogs", "Retail COGS", "COGS_MERCHANDISE", isIncome = false, (2500000, 120000000))
          ))
        )),
        // Technology subsidiary
        Division("tech_sub", "Technology Division", List(
          Department("software", "Software & Services", List(
            AccountType("software_revenue", "Software Revenue", "SERVICE_INCOME", isIncome = true, (2000000, 100000000)),
            AccountType("tech_rd", "R&D Expense", "RD_EXPENSE", isIncome = false, (500000, 30000000))
          ))
        )),
        // Corporate/Holding
        Division("holding", "Corporate Holding", List(
          Department("intercompany", "Intercompany", List(
            AccountType("interco_dividends", "Intercompany Dividends", "INTERCOMPANY_ELIM", isIncome = true, (1000000, 100000000)),
            AccountType("interco_interest", "Intercompany Interest", "INTERCOMPANY_ELIM", isIncome = true, (500000, 50000000)),
            AccountType("interco_mgmt_fees", "Intercompany Mgmt Fees", "INTERCOMPANY_ELIM", isIncome = false, (500000, 50000000))
          )),
          Department("corporate_hq", "Corporate HQ", List(
            AccountType("corp_overhead", "Corporate Overhead", "SALARIES_WAGES", isIncome = false, (5000000, 50000000))
          ))
        ))
      )
    }

    val all: List[BusinessType] = List(
      Personal, Manufacturing, FinancialServices,
      RetailManufacturing, TechFinancial, Conglomerate
    )

    def fromCode(code: String): Option[BusinessType] = all.find(_.code == code)
  }

  // ============================================
  // Tax Classification Mapping
  // ============================================

  /**
   * IRS Tax Categories - The tax department's classification system.
   * Each org cost code maps to one of these.
   */
  case class TaxCategory(
    code: String,
    name: String,
    formLine: String,        // Line on tax form
    scheduleRef: String,     // Schedule reference (e.g., "Schedule C", "Form 1120")
    isAddition: Boolean      // true = adds to income, false = reduces income
  )

  val taxCategories: Map[String, TaxCategory] = Map(
    // Income categories
    "W2_WAGES" -> TaxCategory("W2_WAGES", "Wages, Salaries, Tips", "1040.1", "W-2", isAddition = true),
    "QUALIFIED_DIVIDENDS" -> TaxCategory("QUALIFIED_DIVIDENDS", "Qualified Dividends", "1040.3a", "1099-DIV", isAddition = true),
    "ORDINARY_DIVIDENDS" -> TaxCategory("ORDINARY_DIVIDENDS", "Ordinary Dividends", "1040.3b", "1099-DIV", isAddition = true),
    "INTEREST_INCOME" -> TaxCategory("INTEREST_INCOME", "Interest Income", "1040.2b", "1099-INT", isAddition = true),
    "ST_CAPITAL_GAINS" -> TaxCategory("ST_CAPITAL_GAINS", "Short-Term Capital Gains", "Sch D.7", "8949", isAddition = true),
    "LT_CAPITAL_GAINS" -> TaxCategory("LT_CAPITAL_GAINS", "Long-Term Capital Gains", "Sch D.15", "8949", isAddition = true),
    "RENTAL_INCOME" -> TaxCategory("RENTAL_INCOME", "Rental Income", "Sch E.3", "Schedule E", isAddition = true),
    "SELF_EMPLOYMENT" -> TaxCategory("SELF_EMPLOYMENT", "Self-Employment Income", "Sch C.7", "Schedule C", isAddition = true),
    "GROSS_RECEIPTS" -> TaxCategory("GROSS_RECEIPTS", "Gross Receipts", "1120.1a", "Form 1120", isAddition = true),
    "SERVICE_INCOME" -> TaxCategory("SERVICE_INCOME", "Service Income", "1120.1a", "Form 1120", isAddition = true),
    "INTEREST_INCOME_FI" -> TaxCategory("INTEREST_INCOME_FI", "Interest Income (FI)", "1120.5", "Form 1120", isAddition = true),
    "TRADING_GAINS" -> TaxCategory("TRADING_GAINS", "Trading Gains", "1120.8", "Form 1120", isAddition = true),
    "FEE_INCOME" -> TaxCategory("FEE_INCOME", "Fee Income", "1120.1a", "Form 1120", isAddition = true),
    "DIVIDEND_INCOME_FI" -> TaxCategory("DIVIDEND_INCOME_FI", "Dividend Income (FI)", "1120.4", "Form 1120", isAddition = true),
    "INSURANCE_INCOME" -> TaxCategory("INSURANCE_INCOME", "Insurance Premium Income", "1120.1a", "Form 1120", isAddition = true),
    "INTERCOMPANY_ELIM" -> TaxCategory("INTERCOMPANY_ELIM", "Intercompany Elimination", "1120.Elim", "Consolidated", isAddition = true),

    // Deduction categories
    "STANDARD_DEDUCTION" -> TaxCategory("STANDARD_DEDUCTION", "Standard Deduction", "1040.12", "Standard", isAddition = false),
    "MORTGAGE_INTEREST" -> TaxCategory("MORTGAGE_INTEREST", "Mortgage Interest", "Sch A.8", "Schedule A", isAddition = false),
    "STATE_LOCAL_TAX" -> TaxCategory("STATE_LOCAL_TAX", "State & Local Taxes", "Sch A.5", "Schedule A", isAddition = false),
    "CHARITABLE" -> TaxCategory("CHARITABLE", "Charitable Contributions", "Sch A.11", "Schedule A", isAddition = false),
    "CHILD_TAX_CREDIT" -> TaxCategory("CHILD_TAX_CREDIT", "Child Tax Credit", "1040.19", "Sch 8812", isAddition = false),
    "DEPENDENT_CARE_CREDIT" -> TaxCategory("DEPENDENT_CARE_CREDIT", "Dependent Care Credit", "1040.Sch3.2", "Form 2441", isAddition = false),
    "EDUCATION_CREDIT" -> TaxCategory("EDUCATION_CREDIT", "Education Credit", "1040.Sch3.3", "Form 8863", isAddition = false),
    "COGS_MATERIALS" -> TaxCategory("COGS_MATERIALS", "Cost of Goods Sold - Materials", "1120.2", "Form 1125-A", isAddition = false),
    "COGS_LABOR" -> TaxCategory("COGS_LABOR", "Cost of Goods Sold - Labor", "1120.2", "Form 1125-A", isAddition = false),
    "COGS_OVERHEAD" -> TaxCategory("COGS_OVERHEAD", "Cost of Goods Sold - Overhead", "1120.2", "Form 1125-A", isAddition = false),
    "COGS_MERCHANDISE" -> TaxCategory("COGS_MERCHANDISE", "Cost of Goods Sold - Merchandise", "1120.2", "Form 1125-A", isAddition = false),
    "INVENTORY_ADJ" -> TaxCategory("INVENTORY_ADJ", "Inventory Adjustment", "1120.2", "Form 1125-A", isAddition = false),
    "SALARIES_WAGES" -> TaxCategory("SALARIES_WAGES", "Salaries & Wages", "1120.13", "Form 1120", isAddition = false),
    "RENT_EXPENSE" -> TaxCategory("RENT_EXPENSE", "Rent Expense", "1120.16", "Form 1120", isAddition = false),
    "PROFESSIONAL_FEES" -> TaxCategory("PROFESSIONAL_FEES", "Professional Fees", "1120.14", "Form 1120", isAddition = false),
    "INSURANCE" -> TaxCategory("INSURANCE", "Insurance Expense", "1120.15", "Form 1120", isAddition = false),
    "ADVERTISING" -> TaxCategory("ADVERTISING", "Advertising", "1120.12", "Form 1120", isAddition = false),
    "DEPRECIATION" -> TaxCategory("DEPRECIATION", "Depreciation", "1120.20", "Form 4562", isAddition = false),
    "AMORTIZATION" -> TaxCategory("AMORTIZATION", "Amortization", "1120.21", "Form 4562", isAddition = false),
    "RD_EXPENSE" -> TaxCategory("RD_EXPENSE", "R&D Expense", "1120.26", "Form 6765", isAddition = false),
    "BAD_DEBT_EXPENSE" -> TaxCategory("BAD_DEBT_EXPENSE", "Bad Debt Expense", "1120.15", "Form 1120", isAddition = false),
    "TRADING_LOSSES" -> TaxCategory("TRADING_LOSSES", "Trading Losses", "1120.8", "Form 1120", isAddition = false),
    "REGULATORY_CAPITAL" -> TaxCategory("REGULATORY_CAPITAL", "Regulatory Capital (Non-Deductible)", "N/A", "N/A", isAddition = false),
    "INSURANCE_EXPENSE" -> TaxCategory("INSURANCE_EXPENSE", "Insurance Claims Expense", "1120.15", "Form 1120", isAddition = false),
    "TECH_EXPENSE" -> TaxCategory("TECH_EXPENSE", "Technology Expense", "1120.26", "Form 1120", isAddition = false),
    "UTILITIES" -> TaxCategory("UTILITIES", "Utilities Expense", "1120.26", "Form 1120", isAddition = false)
  )

  // ============================================
  // Output Data Models
  // ============================================

  case class BusinessProfile(
    business_id: String,
    business_name: String,
    business_type: String,
    tax_form_type: String,
    ein: String,
    state: String,
    incorporation_date: String,
    tax_year: Int,
    fiscal_year_end: String,
    generated_at: String,
    seed: Long
  )

  case class CostCodeMapping(
    org_cost_code: String,          // e.g., "acme.manufacturing.materials.raw_materials"
    org_description: String,
    tax_category: String,           // e.g., "COGS_MATERIALS"
    tax_category_name: String,
    form_line: String,
    schedule_ref: String,
    is_income: Boolean
  )

  case class LedgerEntry(
    entry_id: String,
    business_id: String,
    tax_year: Int,
    entry_date: String,
    posting_date: String,
    org_cost_code: String,          // Organization's internal code
    org_description: String,
    tax_category: String,           // Mapped tax classification
    division: String,
    department: String,
    account_type: String,
    amount: BigDecimal,
    amount_usd: BigDecimal,
    currency: String,
    reference: String,              // Invoice/document reference
    counterparty: Option[String],
    status: String,                 // PENDING, VALIDATED, REJECTED
    validation_notes: Option[String]
  )

  case class TaxFormSection(
    section_id: String,
    business_id: String,
    tax_year: Int,
    tax_category: String,
    tax_category_name: String,
    form_line: String,
    schedule_ref: String,
    total_amount: BigDecimal,
    entry_count: Int,
    is_income: Boolean
  )

  // ============================================
  // Generation Logic
  // ============================================

  private val states = List("CA", "NY", "TX", "FL", "IL", "WA", "MA", "PA", "OH", "GA")

  /**
   * Generate a unique EIN (Employer Identification Number)
   */
  def generateEIN(): String = {
    val prefix = 10 + random.nextInt(90)
    val suffix = 1000000 + random.nextInt(9000000)
    f"$prefix-$suffix"
  }

  /**
   * Generate a reference number for a transaction
   */
  def generateReference(prefix: String): String = {
    f"$prefix-${random.nextInt(1000000)}%06d"
  }

  /**
   * Generate amount within typical range with some variance
   */
  def generateAmount(accountType: AccountType): BigDecimal = {
    val (min, max) = accountType.typicalRange
    val range = max - min
    val baseAmount = min + (BigDecimal(random.nextDouble()) * range)
    // Add ±20% variance
    val variance = 0.8 + random.nextDouble() * 0.4
    (baseAmount * variance).setScale(2, BigDecimal.RoundingMode.HALF_UP)
  }

  /**
   * Generate ledger entries for a business
   */
  def generateLedgerEntries(
    businessId: String,
    businessType: BusinessType,
    taxYear: Int,
    entriesPerAccount: Int = 12
  ): List[LedgerEntry] = {
    val entries = ListBuffer[LedgerEntry]()
    val startDate = LocalDate.of(taxYear, 1, 1)
    val endDate = LocalDate.of(taxYear, 12, 31)
    var entryNum = 1

    for {
      division <- businessType.divisions
      department <- division.departments
      accountType <- department.accountTypes
    } {
      // Generate entries throughout the year
      val numEntries = if (accountType.code.contains("annual") || accountType.code.contains("deduction")) {
        1 // Annual entries
      } else if (accountType.code.contains("quarterly") || accountType.code.contains("capital")) {
        4 // Quarterly entries
      } else {
        entriesPerAccount // Monthly entries
      }

      for (i <- 1 to numEntries) {
        val dayOfYear = (i * 365 / numEntries).min(364)
        val entryDate = startDate.plusDays(dayOfYear)
        val postingDate = entryDate.plusDays(random.nextInt(5)) // 0-4 days posting lag

        val orgCostCode = s"${businessId.toLowerCase}.${division.code}.${department.code}.${accountType.code}"
        val amount = generateAmount(accountType)

        // 95% valid, 5% with issues
        val (status, validationNotes) = if (random.nextDouble() < 0.95) {
          ("VALIDATED", None)
        } else {
          val issue = random.nextInt(4) match {
            case 0 => "Missing documentation"
            case 1 => "Amount exceeds threshold"
            case 2 => "Duplicate entry suspected"
            case 3 => "Counterparty verification pending"
          }
          ("PENDING", Some(issue))
        }

        entries += LedgerEntry(
          entry_id = f"LED-$businessId-$taxYear-$entryNum%06d",
          business_id = businessId,
          tax_year = taxYear,
          entry_date = entryDate.toString,
          posting_date = postingDate.toString,
          org_cost_code = orgCostCode,
          org_description = accountType.name,
          tax_category = accountType.taxCategory,
          division = division.name,
          department = department.name,
          account_type = accountType.code,
          amount = if (accountType.isIncome) amount else -amount.abs, // Expenses are negative
          amount_usd = if (accountType.isIncome) amount else -amount.abs,
          currency = "USD",
          reference = generateReference(division.code.toUpperCase.take(3)),
          counterparty = if (random.nextDouble() < 0.7) Some(s"Vendor-${random.nextInt(1000)}") else None,
          status = status,
          validation_notes = validationNotes
        )

        entryNum += 1
      }
    }

    entries.toList
  }

  /**
   * Generate cost code mappings for a business
   */
  def generateCostCodeMappings(
    businessId: String,
    businessType: BusinessType
  ): List[CostCodeMapping] = {
    val mappings = ListBuffer[CostCodeMapping]()

    for {
      division <- businessType.divisions
      department <- division.departments
      accountType <- department.accountTypes
    } {
      val orgCostCode = s"${businessId.toLowerCase}.${division.code}.${department.code}.${accountType.code}"
      val taxCat = taxCategories.getOrElse(accountType.taxCategory,
        TaxCategory(accountType.taxCategory, accountType.taxCategory, "N/A", "N/A", accountType.isIncome))

      mappings += CostCodeMapping(
        org_cost_code = orgCostCode,
        org_description = accountType.name,
        tax_category = accountType.taxCategory,
        tax_category_name = taxCat.name,
        form_line = taxCat.formLine,
        schedule_ref = taxCat.scheduleRef,
        is_income = accountType.isIncome
      )
    }

    mappings.toList
  }

  /**
   * Generate aggregated tax form sections (target grain)
   */
  def generateTaxFormSections(
    businessId: String,
    taxYear: Int,
    entries: List[LedgerEntry]
  ): List[TaxFormSection] = {
    // Only include validated entries
    val validatedEntries = entries.filter(_.status == "VALIDATED")

    // Group by tax category and aggregate
    validatedEntries.groupBy(_.tax_category).map { case (taxCat, catEntries) =>
      val taxCategory = taxCategories.getOrElse(taxCat,
        TaxCategory(taxCat, taxCat, "N/A", "N/A", catEntries.head.amount > 0))

      TaxFormSection(
        section_id = s"TAX-$businessId-$taxYear-$taxCat",
        business_id = businessId,
        tax_year = taxYear,
        tax_category = taxCat,
        tax_category_name = taxCategory.name,
        form_line = taxCategory.formLine,
        schedule_ref = taxCategory.scheduleRef,
        total_amount = catEntries.map(_.amount).sum.setScale(2, BigDecimal.RoundingMode.HALF_UP),
        entry_count = catEntries.size,
        is_income = taxCategory.isAddition
      )
    }.toList.sortBy(_.tax_category)
  }

  /**
   * Generate complete business data package
   */
  def generateBusiness(
    businessType: BusinessType,
    businessName: String,
    taxYear: Int,
    outputDir: String,
    entriesPerAccount: Int = 12
  ): Unit = {
    val dir = new File(outputDir)
    if (!dir.exists()) dir.mkdirs()

    // Generate business ID from name
    val businessId = businessName.toUpperCase.replaceAll("[^A-Z0-9]", "_").take(20)

    println(s"Generating business: $businessName ($businessId)")
    println(s"  Type: ${businessType.name} (${businessType.code})")
    println(s"  Tax Year: $taxYear")
    println(s"  Tax Form: ${businessType.taxFormType}")

    // Generate business profile
    val profile = BusinessProfile(
      business_id = businessId,
      business_name = businessName,
      business_type = businessType.code,
      tax_form_type = businessType.taxFormType,
      ein = generateEIN(),
      state = states(random.nextInt(states.size)),
      incorporation_date = LocalDate.of(2000 + random.nextInt(20), 1 + random.nextInt(12), 1 + random.nextInt(28)).toString,
      tax_year = taxYear,
      fiscal_year_end = s"$taxYear-12-31",
      generated_at = LocalDateTime.now().toString,
      seed = seed
    )

    // Generate cost code mappings
    val mappings = generateCostCodeMappings(businessId, businessType)

    // Generate ledger entries
    val entries = generateLedgerEntries(businessId, businessType, taxYear, entriesPerAccount)

    // Generate tax form sections (aggregated)
    val taxFormSections = generateTaxFormSections(businessId, taxYear, entries)

    // Write files
    writeJsonLines(s"$outputDir/profile.jsonl", List(profile))
    writeJsonLines(s"$outputDir/cost_code_mappings.jsonl", mappings)
    writeJsonLines(s"$outputDir/ledger_entries.jsonl", entries)
    writeJsonLines(s"$outputDir/tax_form_sections.jsonl", taxFormSections)

    // Write summary
    val validEntries = entries.count(_.status == "VALIDATED")
    val pendingEntries = entries.count(_.status == "PENDING")
    val totalIncome = taxFormSections.filter(_.is_income).map(_.total_amount).sum
    val totalDeductions = taxFormSections.filterNot(_.is_income).map(_.total_amount.abs).sum
    val netIncome = totalIncome - totalDeductions

    val summaryWriter = new PrintWriter(new File(s"$outputDir/generation_summary.json"))
    summaryWriter.write(
      s"""{
         |  "business_id": "$businessId",
         |  "business_name": "$businessName",
         |  "business_type": "${businessType.code}",
         |  "tax_year": $taxYear,
         |  "total_ledger_entries": ${entries.size},
         |  "validated_entries": $validEntries,
         |  "pending_entries": $pendingEntries,
         |  "cost_code_mappings": ${mappings.size},
         |  "tax_form_sections": ${taxFormSections.size},
         |  "total_income": $totalIncome,
         |  "total_deductions": $totalDeductions,
         |  "net_income": $netIncome,
         |  "seed": $seed,
         |  "generated_at": "${LocalDateTime.now()}"
         |}""".stripMargin
    )
    summaryWriter.close()

    println(s"\nGeneration complete!")
    println(s"  Ledger entries: ${entries.size} ($validEntries validated, $pendingEntries pending)")
    println(s"  Cost code mappings: ${mappings.size}")
    println(s"  Tax form sections: ${taxFormSections.size}")
    println(s"  Total income: $$${totalIncome.setScale(0, BigDecimal.RoundingMode.HALF_UP)}")
    println(s"  Total deductions: $$${totalDeductions.setScale(0, BigDecimal.RoundingMode.HALF_UP)}")
    println(s"  Net income: $$${netIncome.setScale(0, BigDecimal.RoundingMode.HALF_UP)}")
    println(s"  Output directory: $outputDir")
  }

  /**
   * Write data as JSON Lines format
   */
  private def writeJsonLines[T: Encoder](path: String, data: List[T]): Unit = {
    val writer = new PrintWriter(new File(path))
    data.foreach { item =>
      writer.println(item.asJson.noSpaces)
    }
    writer.close()
  }
}

/**
 * Command-line entry point for Tax Agent data generation
 */
object TaxAgentDataGenerator {

  def main(args: Array[String]): Unit = {
    println("Tax Agent Test Data Generator")
    println("==============================")
    println()
    println("Available business types:")
    println("  1. PERSONAL        - Individual taxpayer")
    println("  2. MANUFACTURING   - Manufacturing company")
    println("  3. FINANCIAL       - Financial services company")
    println("  4. RETAIL_MFG      - Retail-Manufacturing hybrid")
    println("  5. TECH_FIN        - Tech-Financial (Fintech) hybrid")
    println("  6. CONGLOMERATE    - Diversified conglomerate (all types)")
    println()

    val businessTypeCode = if (args.length > 0) args(0) else "RETAIL_MFG"
    val businessName = if (args.length > 1) args(1) else "Demo Company"
    val taxYear = if (args.length > 2) args(2).toInt else 2024
    val outputDir = if (args.length > 3) args(3) else s"test-data/tax/${businessName.toLowerCase.replace(" ", "-")}"
    val seed = if (args.length > 4) args(4).toLong else System.currentTimeMillis()
    val entriesPerAccount = if (args.length > 5) args(5).toInt else 12

    val generator = new TaxAgentDataGenerator(seed)

    val businessType = generator.BusinessType.fromCode(businessTypeCode).getOrElse {
      println(s"Unknown business type: $businessTypeCode")
      println("Using default: RETAIL_MFG")
      generator.BusinessType.RetailManufacturing
    }

    println(s"Configuration:")
    println(s"  Business type: $businessTypeCode")
    println(s"  Business name: $businessName")
    println(s"  Tax year: $taxYear")
    println(s"  Output: $outputDir")
    println(s"  Seed: $seed")
    println(s"  Entries/account: $entriesPerAccount")
    println()

    generator.generateBusiness(businessType, businessName, taxYear, outputDir, entriesPerAccount)
  }

}
