package com.cdme.lineage

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import com.cdme.model._

// Validates: REQ-INT-03
class LineageCollectorSpec extends AnyFlatSpec with Matchers {

  "InMemoryLineageCollector" should "capture traversal lineage" in {
    val collector = new InMemoryLineageCollector()
    val morphism = Morphism("m1", "A", "B", Cardinality.OneToOne,
      MorphismType.Structural, SemanticType.StringType, SemanticType.StringType)
    collector.captureTraversal(morphism, Set("src1", "src2"), Set("tgt1"))
    val lineage = collector.getLineage("tgt1")
    lineage.sourceKeys shouldBe Set("src1", "src2")
    lineage.morphismPath shouldBe List("m1")
  }

  it should "capture lookup versions in lineage" in {
    val collector = new InMemoryLineageCollector()
    collector.captureLookupUsage("currency_ref", "v2.3")
    val morphism = Morphism("m1", "A", "B", Cardinality.OneToOne,
      MorphismType.Structural, SemanticType.StringType, SemanticType.StringType)
    collector.captureTraversal(morphism, Set("src1"), Set("tgt1"))
    val lineage = collector.getLineage("tgt1")
    lineage.lookupVersions shouldBe Map("currency_ref" -> "v2.3")
  }

  it should "return empty lineage for unknown targets" in {
    val collector = new InMemoryLineageCollector()
    val lineage = collector.getLineage("unknown")
    lineage.sourceKeys shouldBe empty
    lineage.morphismPath shouldBe empty
  }
}
