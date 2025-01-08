/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */
package edu.ie3.test.common

import edu.ie3.util.osm.model.OsmEntity.{Node, Way}

trait OsmTestData {
  val node1: Node = buildNode(1L, 50d, 7d)
  val node2: Node = buildNode(2L, 50.5, 7d)
  val node3: Node = buildNode(3L, 51d, 7d)
  val node4: Node = buildNode(4L, 51d, 7.5)
  val node5: Node = buildNode(5L, 51d, 8d)
  val node6: Node = buildNode(6L, 50.5, 8d)

  val way1: Way = buildWay(11L, node1, node2)
  val way2: Way = buildWay(12L, node2, node3)
  val way3: Way = buildWay(13L, node3, node4)
  val way4: Way = buildWay(14L, node4, node5)
  val way5: Way = buildWay(15L, node5, node6)
  val way6: Way = buildWay(16L, node6, node1)

  def data: (Seq[Way], Map[Long, Node]) = {
    val ways = Seq(way1, way2, way3, way4, way5, way6)
    val nodes = Seq(node1, node2, node3, node4, node5, node6).map { n =>
      n.id -> n
    }.toMap

    (ways, nodes)
  }

  def buildNode(id: Long, lat: Double, lon: Double): Node = {
    Node(id, lat, lon, Map.empty, None)
  }

  def buildWay(id: Long, nodeA: Node, nodeB: Node): Way = {
    Way(
      id,
      Seq(nodeA.id, nodeB.id),
      Map.empty,
      None
    )
  }
}
