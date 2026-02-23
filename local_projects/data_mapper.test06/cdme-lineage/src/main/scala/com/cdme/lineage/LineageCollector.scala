package com.cdme.lineage

import com.cdme.model.Morphism

// Implements: REQ-INT-03 (Traceability), RIC-LIN-01, RIC-LIN-06, RIC-LIN-07

trait LineageCollector {
  def captureTraversal(morphism: Morphism, sourceKeys: Set[String], targetKeys: Set[String]): Unit
  def captureLookupUsage(lookupName: String, version: String): Unit
  def getLineage(targetKey: String): LineageGraph
}

case class LineageGraph(
  targetKey: String,
  sourceKeys: Set[String],
  morphismPath: List[String],
  lookupVersions: Map[String, String]
)

// Implements: RIC-LIN-06 (Lossless vs Lossy)
sealed trait MorphismClassification
object MorphismClassification {
  case object Lossless extends MorphismClassification
  case object Lossy extends MorphismClassification
}

// Implements: RIC-LIN-07 (Checkpointing)
case class KeyEnvelope(
  segmentId: String,
  startKey: String,
  endKey: String,
  keyGenFunction: String,
  offset: Long,
  count: Long
)

class InMemoryLineageCollector extends LineageCollector {
  private val traversals = scala.collection.mutable.ListBuffer.empty[(String, Set[String], Set[String])]
  private val lookups = scala.collection.mutable.Map.empty[String, String]

  override def captureTraversal(morphism: Morphism, sourceKeys: Set[String], targetKeys: Set[String]): Unit = {
    traversals += ((morphism.name, sourceKeys, targetKeys))
  }

  override def captureLookupUsage(lookupName: String, version: String): Unit = {
    lookups += (lookupName -> version)
  }

  override def getLineage(targetKey: String): LineageGraph = {
    val relevantTraversals = traversals.filter(_._3.contains(targetKey))
    LineageGraph(
      targetKey = targetKey,
      sourceKeys = relevantTraversals.flatMap(_._2).toSet,
      morphismPath = relevantTraversals.map(_._1).toList,
      lookupVersions = lookups.toMap
    )
  }
}
