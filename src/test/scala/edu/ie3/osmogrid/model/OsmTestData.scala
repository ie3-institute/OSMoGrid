/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.model

import edu.ie3.util.osm.model.OsmEntity.{Node, Relation}
import edu.ie3.util.osm.model.OsmEntity.Relation.{
  RelationMember,
  RelationMemberType
}
import edu.ie3.util.osm.model.OsmEntity.Way.{ClosedWay, OpenWay}

trait OsmTestData {

  object nodes {
    val building1Node1: Node =
      Node(1L, 51.49281d, 7.41197d, Map.empty[String, String], None)
    val building1Node2: Node =
      Node(2L, 51.49259d, 7.41204d, Map.empty[String, String], None)
    val building1Node3: Node =
      Node(3L, 51.49264d, 7.41240d, Map.empty[String, String], None)
    val building1Node4: Node =
      Node(4L, 51.49286d, 7.41232d, Map.empty[String, String], None)

    val highway1Node1: Node =
      Node(11L, 51.4955d, 7.4063d, Map.empty[String, String], None)
    val highway1Node2: Node =
      Node(12L, 51.498d, 7.4255d, Map.empty[String, String], None)

    val highway2Node1: Node = highway1Node2
    val highway2Node2: Node = Node(13L, 51.538d, 7.4265d, Map.empty[String, String], None)
    val highway2Node3: Node = Node(14L, 51.578d, 7.4265d, Map.empty[String, String], None)

    val landuseNode1: Node =
      Node(21L, 51.49378d, 7.4105d, Map.empty[String, String], None)
    val landuseNode2: Node =
      Node(22L, 51.49420d, 7.41371d, Map.empty[String, String], None)
    val landuseNode3: Node =
      Node(23L, 51.49222d, 7.41457d, Map.empty[String, String], None)
    val landuseNode4: Node =
      Node(24L, 51.49202d, 7.41116d, Map.empty[String, String], None)

    val boundaryNode1: Node =
      Node(31L, 51.5720d, 7.3911d, Map.empty[String, String], None)
    val boundaryNode2: Node =
      Node(32L, 51.4781d, 7.3911d, Map.empty[String, String], None)
    val boundaryNode3: Node =
      Node(33L, 51.4738d, 7.6492d, Map.empty[String, String], None)
    val boundaryNode4: Node =
      Node(34L, 51.5874d, 7.5600d, Map.empty[String, String], None)

    val substation: Node = Node(
      41L,
      51.4895185d,
      7.4058116d,
      Map("power" -> "substation", "building" -> "service"),
      None
    )

    val nodesMap: Map[Long, Node] = Map(
      1L -> building1Node1,
      2L -> building1Node2,
      3L -> building1Node3,
      4L -> building1Node4,
      11L -> highway1Node1,
      12L -> highway1Node2,
      13L -> highway2Node2,
      14L -> highway2Node3,
      21L -> landuseNode1,
      22L -> landuseNode2,
      23L -> landuseNode3,
      24L -> landuseNode4,
      31L -> boundaryNode1,
      32L -> boundaryNode2,
      33L -> boundaryNode3,
      34L -> boundaryNode4,
      41L -> substation
    )

//    def getNode(id: Long): Node = {
//      nodesMap.getOrElse(throw new IllegalArgumentException(s"Id: $id is not within the test nodes map"))
//    }

  }

  object ways {
    val building1: ClosedWay =
      ClosedWay(
        101L,
        Seq(
          nodes.building1Node1.id,
          nodes.building1Node2.id,
          nodes.building1Node3.id,
          nodes.building1Node4.id,
          nodes.building1Node1.id
        ),
        Map("building" -> "yes"),
        None
      )

    val highway1: OpenWay =
      OpenWay(
        111L,
        Seq(nodes.highway1Node1.id, nodes.highway1Node2.id),
        Map("highway" -> "motorway"),
        None
      )

    val highway2: OpenWay =
      OpenWay(
        111L,
        Seq(nodes.highway2Node1.id, nodes.highway2Node2.id, nodes.highway2Node3.id),
        Map("highway" -> "motorway"),
        None
      )

    val landuse: ClosedWay = ClosedWay(
      121L,
      Seq(
        nodes.landuseNode1.id,
        nodes.landuseNode2.id,
        nodes.landuseNode3.id,
        nodes.landuseNode4.id
      ),
      Map("landuse" -> "education"),
      None
    )

    val boundaryWay1: OpenWay = OpenWay(
      131L,
      Seq(
        nodes.boundaryNode1.id,
        nodes.boundaryNode2.id,
        nodes.boundaryNode3.id
      ),
      Map.empty[String, String],
      None
    )
    val boundaryWay2: OpenWay = OpenWay(
      132L,
      Seq(
        nodes.boundaryNode3.id,
        nodes.boundaryNode4.id,
        nodes.boundaryNode1.id
      ),
      Map.empty[String, String],
      None
    )
  }

  object relations {
    val boundary: Relation = Relation(
      231L,
      Seq(
        RelationMember(ways.boundaryWay1.id, RelationMemberType.Way, "outer"),
        RelationMember(ways.boundaryWay2.id, RelationMemberType.Way, "outer")
      ),
      Map("boundary" -> "administrative"),
      None
    )
  }

}
