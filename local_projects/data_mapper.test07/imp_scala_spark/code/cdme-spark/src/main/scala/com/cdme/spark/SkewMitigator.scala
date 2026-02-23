// Implements: RIC-SKW-01
// Salted join strategy for skew mitigation.
package com.cdme.spark

/**
 * Detects data skew and applies salted joins for skew mitigation.
 * When a join key has highly skewed distribution, the salted join distributes
 * the load by adding a random salt to the key.
 *
 * Implements: RIC-SKW-01
 */
object SkewMitigator {

  /** Configuration for skew detection and mitigation. */
  final case class SkewConfig(
      detectionThreshold: Double,
      saltFactor: Int,
      sampleFraction: Double
  )

  object SkewConfig {
    val default: SkewConfig = SkewConfig(
      detectionThreshold = 10.0, // key frequency > 10x average
      saltFactor = 10,
      sampleFraction = 0.01
    )
  }

  /**
   * Detect skew in a key distribution.
   * Returns true if the maximum key frequency exceeds the threshold times the average.
   */
  def detectSkew(
      keyFrequencies: Map[String, Long],
      threshold: Double
  ): Boolean = {
    if (keyFrequencies.isEmpty) return false
    val total     = keyFrequencies.values.sum
    val count     = keyFrequencies.size
    val average   = total.toDouble / count
    val maxFreq   = keyFrequencies.values.max
    maxFreq > average * threshold
  }

  /**
   * Generate salt values for a given salt factor.
   * Returns a sequence of salt values [0, saltFactor).
   */
  def generateSalts(saltFactor: Int): Seq[Int] =
    0 until saltFactor

  /**
   * Compute the salted key for a given key and salt.
   */
  def saltedKey(key: String, salt: Int): String =
    s"${key}_salt_$salt"
}
