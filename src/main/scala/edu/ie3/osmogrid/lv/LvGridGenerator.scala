/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv

import akka.actor.typed.scaladsl.Behaviors
import edu.ie3.datamodel.graph.{DistanceWeightedEdge, DistanceWeightedGraph}
import edu.ie3.datamodel.models.input.container.SubGridContainer
import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.osmogrid.model.OsmoGridModel.EnhancedOsmEntity
import edu.ie3.util.geo.GeoUtils
import edu.ie3.util.osm.model.OsmEntity.Way
import edu.ie3.util.osm.model.OsmEntity.Way.OpenWay
import edu.ie3.util.osm.model.OsmEntity.Node
import tech.units.indriya.ComparableQuantity

import javax.measure.quantity.Length
import scala.collection.parallel.{ParSeq, immutable}

object LvGridGenerator {
  sealed trait Request

  sealed trait Response
  final case class RepLvGrid(grid: SubGridContainer) extends Response

  def apply(): Behaviors.Receive[Request] = idle

  private def idle: Behaviors.Receive[Request] = Behaviors.receive {
    case (ctx, unsupported) =>
      ctx.log.warn(s"Received unsupported message '$unsupported'.")
      Behaviors.stopped
  }

  private def buildStreetGraph(enhancedEntities: Seq[EnhancedOsmEntity]): OsmGraph = {
      val graph = new OsmGraph()
      enhancedEntities.foreach(enhancedEntity => {
        // todo: unsafe
        val way = enhancedEntity.entity.asInstanceOf[Way]
        val nodeIds = way.nodes
        for (i <- 1 until nodeIds.size) {
          // todo: unsafe
          val nodeA = enhancedEntity.subEntities(nodeIds(i - 1)).asInstanceOf[Node]
          val nodeB = enhancedEntity.subEntities(nodeIds(i)).asInstanceOf[Node]
          graph.addVertex(nodeA)
          graph.addVertex(nodeB)
          // calculate edge weight
          val weight = GeoUtils.calcHaversine(nodeA.latitude, nodeA.longitude, nodeB.latitude, nodeB.longitude)
          // create edge and add edge to rawGraph
          val e = new DistanceWeightedEdge()
          graph.setEdgeWeight(e, weight.getValue.doubleValue)
          graph.addEdge(nodeA, nodeB, e) // TODO: consider checking boolean from this method
        }})
    graph
  }

}
