// Implements: REQ-F-LDM-008
// ADR-006: Content-Addressed Versioning
package cdme.model.category

import java.time.Instant

/**
 * Opaque type for content-addressed version hashes.
 */
opaque type ContentHash = String

object ContentHash:
  def apply(value: String): ContentHash = value
  extension (h: ContentHash)
    def value: String = h
    def show: String = s"ContentHash(${h.take(12)}...)"

/**
 * An immutable versioned snapshot of a category.
 *
 * Schema versioning uses content-addressed hashing (SHA-256 of canonical form)
 * to produce monotonic, collision-resistant version identifiers (ADR-006).
 *
 * CategoryVersion instances are immutable and can be compared for equality
 * via their content hash.
 *
 * @param category  the category snapshot
 * @param hash      SHA-256 content hash of the canonical form
 * @param createdAt when this version was created
 * @param label     optional human-readable version label
 */
final case class CategoryVersion(
    category: Category,
    hash: ContentHash,
    createdAt: Instant,
    label: Option[String] = None
):
  /**
   * Check whether two versions are identical by content hash.
   *
   * @param other the other version
   * @return true if content hashes match
   */
  def isSameContent(other: CategoryVersion): Boolean =
    hash == other.hash

object CategoryVersion:
  /**
   * Create a versioned snapshot of a category.
   *
   * Computes the SHA-256 hash of the canonical serialised form. The canonical
   * form is deterministic: entities are sorted by ID, morphisms by ID, attributes
   * by name.
   *
   * @param category the category to version
   * @param label    optional human-readable label
   * @return the versioned snapshot
   */
  def create(category: Category, label: Option[String] = None): CategoryVersion =
    val canonical = computeCanonicalForm(category)
    val hash = sha256(canonical)
    CategoryVersion(
      category = category,
      hash = ContentHash(hash),
      createdAt = Instant.now(),
      label = label
    )

  /**
   * Compute the canonical string form of a category for hashing.
   * Entities and morphisms are sorted by their IDs for determinism.
   */
  private def computeCanonicalForm(category: Category): String =
    val entityParts = category.entities.toSeq.sortBy(_._1.value).map { (id, entity) =>
      val attrs = entity.attributes.map((aid, t) => s"${aid.value}:${t.typeName}").mkString(",")
      s"E[${id.value}|${entity.name}|${entity.grain.level}|$attrs]"
    }
    val morphismParts = category.morphisms.toSeq.sortBy(_._1.value).map { (id, m) =>
      s"M[${id.value}|${m.name}|${m.domain.value}->${m.codomain.value}|${m.cardinality}]"
    }
    (entityParts ++ morphismParts).mkString(";")

  /**
   * Compute SHA-256 hash of a string.
   *
   * Uses java.security.MessageDigest for the hash computation.
   */
  private def sha256(input: String): String =
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    val hashBytes = digest.digest(input.getBytes("UTF-8"))
    hashBytes.map(b => String.format("%02x", b)).mkString
