/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv

import akka.actor.typed.scaladsl.Behaviors
import edu.ie3.datamodel.graph.DistanceWeightedGraph
import edu.ie3.datamodel.models.input.container.SubGridContainer
import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.osmogrid.model.OsmoGridModel.EnhancedOsmEntity
import edu.ie3.util.geo.GeoUtils
import edu.ie3.util.osm.model.OsmEntity.Way
import edu.ie3.util.osm.model.OsmEntity.Way.OpenWay
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

  private def buildStreetGraph(ways: Seq[EnhancedOsmEntity]): DistanceWeightedGraph =
      val graph = new OsmGraph()
      ways.map(way =>
        val nodes = way.nodes
        for (i <- 1 until nodes.size) {
          val nodeA = nodes(i - 1)
          val nodeB = nodes(i)
          graph.addVertex(nodeA)
          graph.addVertex(nodeB)
          // calculate edge weight
          val weight = GeoUtils.calcHaversine(nodeA.getLatlon.getLat, nodeA.getLatlon.getLon, nodeB.getLatlon.getLat, nodeB.getLatlon.getLon)
          // create edge and add edge to rawGraph
          val e = new DistanceWeightedOsmEdge
          rawGraph.setEdgeWeight(e, weight.getValue.doubleValue)
          rawGraph.addEdge(nodeA, nodeB, e) // TODO: consider checking boolean from this method
        })

    case _ => throw new IllegalArgumentException("We expect ways to be of type way")

}
