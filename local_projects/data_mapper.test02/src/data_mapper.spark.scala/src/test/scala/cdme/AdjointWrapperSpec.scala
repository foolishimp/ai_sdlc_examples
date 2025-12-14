package cdme

import cdme.executor._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.EitherValues

/**
 * Adjoint Wrapper unit tests following TDD.
 * Tests the metadata capture for backward transformations.
 *
 * Validates: REQ-ADJ-04, REQ-ADJ-05, REQ-ADJ-06, REQ-ADJ-11
 */
class AdjointWrapperSpec extends AnyFlatSpec with Matchers with EitherValues {

  // ============================================
  // Unit Tests for Adjoint Classification
  // ============================================

  "AdjointClassification" should "define ISOMORPHISM for 1:1 mappings" in {
    import AdjointClassification._
    ISOMORPHISM.toString shouldBe "ISOMORPHISM"
  }

  it should "define EMBEDDING for N:1 mappings (filters)" in {
    import AdjointClassification._
    EMBEDDING.toString shouldBe "EMBEDDING"
  }

  it should "define PROJECTION for 1:N mappings (aggregations)" in {
    import AdjointClassification._
    PROJECTION.toString shouldBe "PROJECTION"
  }

  it should "define LOSSY for operations without metadata" in {
    import AdjointClassification._
    LOSSY.toString shouldBe "LOSSY"
  }

  // ============================================
  // Unit Tests for Adjoint Metadata Types
  // ============================================

  "AdjointMetadata" should "support NoMetadata for lossy operations" in {
    val metadata: AdjointMetadata = NoMetadata
    metadata shouldBe NoMetadata
  }

  it should "support ReverseJoinMetadata for aggregations" in {
    // Mock reverse join data structure
    val mockReverseJoinData = Map(
      "group_key" -> "customer_123",
      "source_keys" -> List(1L, 2L, 3L)
    )

    val metadata = ReverseJoinMetadata(mockReverseJoinData)
    metadata.reverseJoinData shouldBe mockReverseJoinData
  }

  it should "support FilteredKeysMetadata for filters" in {
    val filteredKeys = List(10L, 20L, 30L)
    val metadata = FilteredKeysMetadata(filteredKeys)
    metadata.filteredKeys shouldBe filteredKeys
  }

  it should "support ParentChildMetadata for explode" in {
    val parentChildMapping = Map(
      100L -> 1L,  // child_key 100 -> parent_key 1
      101L -> 1L,  // child_key 101 -> parent_key 1
      102L -> 2L   // child_key 102 -> parent_key 2
    )

    val metadata = ParentChildMetadata(parentChildMapping)
    metadata.parentChildMapping shouldBe parentChildMapping
  }

  // ============================================
  // Unit Tests for AdjointResult
  // ============================================

  "AdjointResult" should "wrap output with metadata and classification" in {
    val output = Map("result" -> "data")
    val metadata: AdjointMetadata = NoMetadata
    val classification = AdjointClassification.LOSSY

    val result = AdjointResult(
      output = output,
      metadata = metadata,
      classification = classification
    )

    result.output shouldBe output
    result.metadata shouldBe metadata
    result.classification shouldBe classification
  }

  it should "support ReverseJoinMetadata in result" in {
    val output = Map("aggregated" -> "value")
    val reverseJoinData = Map("group" -> "keys")
    val metadata = ReverseJoinMetadata(reverseJoinData)

    val result = AdjointResult(
      output = output,
      metadata = metadata,
      classification = AdjointClassification.PROJECTION
    )

    result.classification shouldBe AdjointClassification.PROJECTION
    result.metadata shouldBe a[ReverseJoinMetadata]
  }

  // ============================================
  // Unit Tests for SparkAdjointWrapper Logic
  // ============================================

  "SparkAdjointWrapper.groupByWithAdjoint" should "capture reverse-join metadata (REQ-ADJ-04)" in {
    // Mock input data
    val inputData = List(
      Map("customer_id" -> "C1", "amount" -> 100.0, "_row_id" -> 1L),
      Map("customer_id" -> "C1", "amount" -> 200.0, "_row_id" -> 2L),
      Map("customer_id" -> "C2", "amount" -> 300.0, "_row_id" -> 3L)
    )

    val groupCols = List("customer_id")

    // This test will verify the concept - actual Spark DataFrame testing
    // would require SparkSession which is incompatible with Java 25
    // For now, we test the data structure logic

    val result = SparkAdjointWrapper.groupByWithAdjoint(
      inputData = inputData,
      groupCols = groupCols
    )

    result.classification shouldBe AdjointClassification.PROJECTION
    result.metadata shouldBe a[ReverseJoinMetadata]
  }

  it should "return PROJECTION classification for aggregations" in {
    val inputData = List(
      Map("key" -> "K1", "_row_id" -> 1L),
      Map("key" -> "K1", "_row_id" -> 2L)
    )

    val result = SparkAdjointWrapper.groupByWithAdjoint(
      inputData = inputData,
      groupCols = List("key")
    )

    result.classification shouldBe AdjointClassification.PROJECTION
  }

  "SparkAdjointWrapper.filterWithAdjoint" should "capture filtered keys when enabled (REQ-ADJ-05)" in {
    val inputData = List(
      Map("status" -> "ACTIVE", "_row_id" -> 1L),
      Map("status" -> "INACTIVE", "_row_id" -> 2L),
      Map("status" -> "ACTIVE", "_row_id" -> 3L)
    )

    val result = SparkAdjointWrapper.filterWithAdjoint(
      inputData = inputData,
      predicate = (row: Map[String, Any]) => row("status") == "ACTIVE",
      captureFiltered = true
    )

    result.classification shouldBe AdjointClassification.EMBEDDING
    result.metadata shouldBe a[FilteredKeysMetadata]

    val filteredMeta = result.metadata.asInstanceOf[FilteredKeysMetadata]
    filteredMeta.filteredKeys should contain(2L)
    filteredMeta.filteredKeys should not contain 1L
    filteredMeta.filteredKeys should not contain 3L
  }

  it should "return NoMetadata when capture disabled" in {
    val inputData = List(
      Map("status" -> "ACTIVE", "_row_id" -> 1L)
    )

    val result = SparkAdjointWrapper.filterWithAdjoint(
      inputData = inputData,
      predicate = (row: Map[String, Any]) => row("status") == "ACTIVE",
      captureFiltered = false
    )

    result.classification shouldBe AdjointClassification.LOSSY
    result.metadata shouldBe NoMetadata
  }

  it should "return EMBEDDING classification when capture enabled" in {
    val inputData = List(Map("value" -> 1, "_row_id" -> 1L))

    val result = SparkAdjointWrapper.filterWithAdjoint(
      inputData = inputData,
      predicate = _ => true,
      captureFiltered = true
    )

    result.classification shouldBe AdjointClassification.EMBEDDING
  }

  it should "return LOSSY classification when capture disabled" in {
    val inputData = List(Map("value" -> 1, "_row_id" -> 1L))

    val result = SparkAdjointWrapper.filterWithAdjoint(
      inputData = inputData,
      predicate = _ => true,
      captureFiltered = false
    )

    result.classification shouldBe AdjointClassification.LOSSY
  }

  "SparkAdjointWrapper.explodeWithAdjoint" should "capture parent-child mapping (REQ-ADJ-06)" in {
    val inputData = List(
      Map("parent_id" -> "P1", "items" -> List("A", "B"), "_row_id" -> 1L),
      Map("parent_id" -> "P2", "items" -> List("C"), "_row_id" -> 2L)
    )

    val result = SparkAdjointWrapper.explodeWithAdjoint(
      inputData = inputData,
      arrayField = "items"
    )

    result.classification shouldBe AdjointClassification.PROJECTION
    result.metadata shouldBe a[ParentChildMetadata]

    val parentChildMeta = result.metadata.asInstanceOf[ParentChildMetadata]
    // Should have mappings for 3 children (A, B, C) to 2 parents (P1, P2)
    parentChildMeta.parentChildMapping.size shouldBe 3
  }

  it should "return PROJECTION classification for explode" in {
    val inputData = List(
      Map("items" -> List(1, 2), "_row_id" -> 1L)
    )

    val result = SparkAdjointWrapper.explodeWithAdjoint(
      inputData = inputData,
      arrayField = "items"
    )

    result.classification shouldBe AdjointClassification.PROJECTION
  }

  it should "handle empty arrays in explode" in {
    val inputData = List(
      Map("items" -> List(), "_row_id" -> 1L)
    )

    val result = SparkAdjointWrapper.explodeWithAdjoint(
      inputData = inputData,
      arrayField = "items"
    )

    result.metadata shouldBe a[ParentChildMetadata]
    val parentChildMeta = result.metadata.asInstanceOf[ParentChildMetadata]
    parentChildMeta.parentChildMapping shouldBe empty
  }
}
