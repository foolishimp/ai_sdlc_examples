import sbt._

/**
 * Centralized dependency and version management for all CDME modules.
 *
 * All library versions are pinned here to ensure consistency across
 * the multi-module build. No module should declare ad-hoc version strings.
 */
object Dependencies {

  // ---------------------------------------------------------------------------
  // Version constants
  // ---------------------------------------------------------------------------
  object Versions {
    val spark        = "3.5.0"
    val scalaTest    = "3.2.17"
    val scalaCheck   = "1.17.0"
    val scalaTestPlus = "3.2.17.0"
  }

  // ---------------------------------------------------------------------------
  // Spark (provided scope — supplied by the cluster at runtime)
  // ---------------------------------------------------------------------------
  val sparkSql = "org.apache.spark" %% "spark-sql" % Versions.spark

  // ---------------------------------------------------------------------------
  // Testing
  // ---------------------------------------------------------------------------
  val scalaTest          = "org.scalatest"     %% "scalatest"          % Versions.scalaTest
  val scalaCheck         = "org.scalacheck"    %% "scalacheck"         % Versions.scalaCheck
  val scalaTestPlusCheck = "org.scalatestplus" %% "scalacheck-1-17"    % Versions.scalaTestPlus
}
