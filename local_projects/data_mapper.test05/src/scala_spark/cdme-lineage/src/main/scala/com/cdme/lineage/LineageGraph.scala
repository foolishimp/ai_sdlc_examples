package com.cdme.lineage

// Implements: REQ-F-SYN-003

import com.cdme.model.config.Epoch
import com.cdme.model.entity.EntityId
import com.cdme.model.morphism.MorphismId

/**
 * A node in the lineage graph representing a dataset at a specific epoch.
 *
 * Each node corresponds to a logical entity (as defined in the LDM) at a
 * particular processing epoch, with the list of attributes that were
 * materialized.
 *
 * @param entityId   the logical entity identifier
 * @param epoch      the epoch at which this dataset was produced or consumed
 * @param attributes the attribute names present in this dataset
 */
case class LineageNode(
  entityId: EntityId,
  epoch: Epoch,
  attributes: List[String]
)

/**
 * An edge in the lineage graph representing a transformation between datasets.
 *
 * Each edge corresponds to one or more morphism executions that transformed
 * data from the source node to the target node.  The `morphismPath` records
 * the full chain of morphisms when multiple are composed.
 *
 * @param source       identifier of the source node in the graph
 * @param target       identifier of the target node in the graph
 * @param morphismId   the primary morphism that produced this edge
 * @param morphismPath the full path of composed morphisms (for multi-hop transforms)
 */
case class LineageEdge(
  source: String,
  target: String,
  morphismId: MorphismId,
  morphismPath: List[MorphismId]
)

/**
 * The complete lineage graph for a pipeline run.
 *
 * Captures the full data flow topology: which datasets (nodes) were read,
 * transformed, and written, and the morphisms (edges) that connected them.
 * This graph is emitted as part of the OpenLineage COMPLETE event and can
 * be used for auditing, impact analysis, and backward traversal.
 *
 * @param nodes map from node identifier to [[LineageNode]]
 * @param edges list of [[LineageEdge]] representing data flow relationships
 */
case class LineageGraph(
  nodes: Map[String, LineageNode],
  edges: List[LineageEdge]
)
