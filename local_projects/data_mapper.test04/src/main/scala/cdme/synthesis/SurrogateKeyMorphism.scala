// Implements: REQ-F-INT-006
package cdme.synthesis

import cdme.model.category.{MorphismId, EntityId, CardinalityType, ContainmentType}
import cdme.model.adjoint.{AdjointMorphism, BackwardResult}
import cdme.model.access.AccessRule
import cdme.model.error.ErrorObject

/**
 * Surrogate key morphism: deterministic hash-based key generation.
 *
 * Generates surrogate keys from input record data using a configurable
 * hash algorithm. Deterministic: same input always produces the same key.
 *
 * @tparam A the input record type
 * @tparam K the key type (typically String or Long)
 * @param id            morphism identifier
 * @param name          human-readable name
 * @param domain        source entity
 * @param codomain      target entity (with surrogate key attribute)
 * @param keyExtractor  extracts the key material from the input
 * @param hashAlgorithm the hash algorithm (e.g., "SHA-256", "MurmurHash3")
 * @param seed          optional seed for determinism
 * @param accessRules   access control rules
 */
final case class SurrogateKeyMorphism[A, K](
    id: MorphismId,
    name: String,
    domain: EntityId,
    codomain: EntityId,
    keyExtractor: A => String,
    hashFn: String => K,
    hashAlgorithm: String = "SHA-256",
    seed: Option[Long] = None,
    accessRules: List[AccessRule] = Nil
) extends AdjointMorphism[A, K]:

  override val cardinality: CardinalityType = CardinalityType.OneToOne
  override val containment: ContainmentType = ContainmentType.Isomorphic

  override def forward(input: A): Either[ErrorObject, K] =
    val keyMaterial = keyExtractor(input)
    Right(hashFn(keyMaterial))

  override def backward(output: K): BackwardResult[A] =
    // Surrogate keys are one-way hashes; backward is not meaningful
    // for recovery, but can be used to look up the original record
    // via an index maintained at runtime.
    BackwardResult(
      records = Vector.empty,
      metadata = Map(
        "adjointType" -> "surrogateKey",
        "algorithm" -> hashAlgorithm,
        "reverseLookupRequired" -> "true"
      )
    )

object SurrogateKeyMorphism:
  /**
   * Default SHA-256 hash function producing a hex string.
   */
  def sha256Hash(input: String): String =
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    val hashBytes = digest.digest(input.getBytes("UTF-8"))
    hashBytes.map(b => String.format("%02x", b)).mkString
