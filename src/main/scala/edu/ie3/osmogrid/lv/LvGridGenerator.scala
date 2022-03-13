/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv

import akka.actor.typed.scaladsl.Behaviors
import edu.ie3.datamodel.graph.{DistanceWeightedEdge, DistanceWeightedGraph}
import edu.ie3.osmogrid.model.OsmoGridModel.LvOsmoGridModel
import edu.ie3.util.geo.GeoUtils
import edu.ie3.util.osm.model.OsmEntity.Way.OpenWay
import org.jgrapht.graph.SimpleWeightedGraph

object LvGridGenerator {
  sealed trait Request

  def apply(): Behaviors.Receive[Request] = idle

  private def idle: Behaviors.Receive[Request] = Behaviors.receive {
    case (ctx, unsupported) =>
      ctx.log.warn(s"Received unsupported message '$unsupported'.")
      Behaviors.stopped
  }

  private def buildStreetGraph(lvOsmoGridModel: LvOsmoGridModel): DistanceWeightedGraph = {

    val graph = ways.map(way => {
      val start = way.nodes.head
      val end = way.nodes(-1)
      val distance = GeoUtils.calcHaversine()
    })
  }
}
