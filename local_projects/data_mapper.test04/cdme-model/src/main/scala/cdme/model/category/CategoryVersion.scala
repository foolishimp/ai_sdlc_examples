// Implements: REQ-F-LDM-008
// ADR-006: Content-Addressed Versioning
package cdme.model.category

import java.time.Instant

case class ContentHash(value: String) {
  def show: String = s"ContentHash(${value.take(12)}...)"
}

final case class CategoryVersion(
    category: Category,
    hash: ContentHash,
    createdAt: Instant,
    label: Option[String] = None
) {
  def isSameContent(other: CategoryVersion): Boolean =
    hash == other.hash
}

object CategoryVersion {
  def create(category: Category, label: Option[String] = None): CategoryVersion = {
    val canonical = computeCanonicalForm(category)
    val hash = sha256(canonical)
    CategoryVersion(
      category = category,
      hash = ContentHash(hash),
      createdAt = Instant.now(),
      label = label
    )
  }

  private def computeCanonicalForm(category: Category): String = {
    val entityParts = category.entities.toSeq.sortBy(_._1.value).map { case (id, entity) =>
      val attrs = entity.attributes.map { case (aid, t) => s"${aid.value}:${t.typeName}" }.mkString(",")
      s"E[${id.value}|${entity.name}|${entity.grain.level}|$attrs]"
    }
    val morphismParts = category.morphisms.toSeq.sortBy(_._1.value).map { case (id, m) =>
      s"M[${id.value}|${m.name}|${m.domain.value}->${m.codomain.value}|${m.cardinality}]"
    }
    (entityParts ++ morphismParts).mkString(";")
  }

  private def sha256(input: String): String = {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    val hashBytes = digest.digest(input.getBytes("UTF-8"))
    hashBytes.map(b => String.format("%02x", b)).mkString
  }
}
