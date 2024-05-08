/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.model

import edu.ie3.osmogrid.model.OsmoGridModel.LvOsmoGridModel
import edu.ie3.osmogrid.model.SourceFilter.LvFilter
import edu.ie3.util.osm.model.OsmContainer.ParOsmContainer
import edu.ie3.util.osm.model.OsmEntity
import edu.ie3.util.osm.model.OsmEntity.{Node, Relation}
import edu.ie3.util.osm.model.OsmEntity.Relation.{
  RelationMember,
  RelationMemberType
}
import edu.ie3.util.osm.model.OsmEntity.Way.{ClosedWay, OpenWay}
import scala.collection.parallel.immutable.ParSeq

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

    val building2Node1: Node =
      Node(5L, 50.49281d, 7.41197d, Map.empty[String, String], None)
    val building2Node2: Node =
      Node(6L, 50.49259d, 7.41204d, Map.empty[String, String], None)
    val building2Node3: Node =
      Node(7L, 50.49264d, 7.41240d, Map.empty[String, String], None)
    val building2Node4: Node =
      Node(8L, 50.49286d, 7.41232d, Map.empty[String, String], None)

    val building3Node1: Node =
      Node(9L, 50.528d, 7.4055d, Map.empty[String, String], None)
    val building3Node2: Node =
      Node(10L, 50.528d, 7.4075d, Map.empty[String, String], None)
    val building3Node3: Node =
      Node(11L, 50.548d, 7.4055d, Map.empty[String, String], None)
    val building3Node4: Node =
      Node(12L, 50.548d, 7.4075d, Map.empty[String, String], None)

    val highway1Node1: Node =
      Node(21L, 51.4955d, 7.4063d, Map.empty[String, String], None)
    val highway1Node2: Node =
      Node(22L, 51.498d, 7.4255d, Map.empty[String, String], None)

    // connection between ways
    val highway2Node1: Node = highway1Node2
    val highway2Node2: Node =
      Node(23L, 50.538d, 7.4065d, Map.empty[String, String], None)
    val highway2Node2b: Node =
      Node(123L, 50.5382d, 7.40652d, Map.empty[String, String], None)
    val highway2Node3: Node =
      Node(24L, 50.578d, 7.4265d, Map.empty[String, String], None)
    val highway2Node4: Node = highway1Node1

    val landuse1Node1: Node =
      Node(40L, 51.49378d, 7.4105d, Map.empty[String, String], None)
    val landuse1Node2: Node =
      Node(41L, 51.49420d, 7.41371d, Map.empty[String, String], None)
    val landuse1Node3: Node =
      Node(42L, 51.49222d, 7.41457d, Map.empty[String, String], None)
    val landuse1Node4: Node =
      Node(43L, 51.49202d, 7.41116d, Map.empty[String, String], None)

    val landuse2Node1: Node =
      Node(44L, 40d, 7d, Map.empty[String, String], None)
    val landuse2Node2: Node =
      Node(45L, 40d, 8d, Map.empty[String, String], None)
    val landuse2Node3: Node =
      Node(46L, 60d, 8d, Map.empty[String, String], None)
    val landuse2Node4: Node =
      Node(47L, 60d, 7d, Map.empty[String, String], None)

    val landuse3Node1: Node =
      Node(48L, 50.518d, 7.4045d, Map.empty[String, String], None)
    val landuse3Node2: Node =
      Node(49L, 50.518d, 7.4085d, Map.empty[String, String], None)
    val landuse3Node3: Node =
      Node(51L, 50.558d, 7.4085d, Map.empty[String, String], None)
    val landuse3Node4: Node =
      Node(50L, 50.558d, 7.4045d, Map.empty[String, String], None)

    val boundaryNode1: Node =
      Node(61L, 51.5720d, 7.3911d, Map.empty[String, String], None)
    val boundaryNode2: Node =
      Node(62L, 51.4781d, 7.3911d, Map.empty[String, String], None)
    val boundaryNode3: Node =
      Node(63L, 51.4738d, 7.6492d, Map.empty[String, String], None)
    val boundaryNode4: Node =
      Node(64L, 51.5874d, 7.5600d, Map.empty[String, String], None)

    val substation: Node = Node(
      80L,
      51.4895185d,
      7.4058116d,
      Map("power" -> "substation", "building" -> "service"),
      None
    )

    val nodesMap: Map[Long, Node] = Map(
      building1Node1.id -> building1Node1,
      building1Node2.id -> building1Node2,
      building1Node3.id -> building1Node3,
      building1Node4.id -> building1Node4,
      building2Node1.id -> building2Node1,
      building2Node2.id -> building2Node2,
      building2Node3.id -> building2Node3,
      building2Node4.id -> building2Node4,
      building3Node1.id -> building3Node1,
      building3Node2.id -> building3Node2,
      building3Node3.id -> building3Node3,
      building3Node4.id -> building3Node4,
      highway1Node1.id -> highway1Node1,
      highway1Node2.id -> highway1Node2,
      highway2Node2.id -> highway2Node2,
      highway2Node3.id -> highway2Node3,
      landuse1Node1.id -> landuse1Node1,
      landuse1Node2.id -> landuse1Node2,
      landuse1Node3.id -> landuse1Node3,
      landuse1Node4.id -> landuse1Node4,
      landuse2Node1.id -> landuse2Node1,
      landuse2Node2.id -> landuse2Node2,
      landuse2Node3.id -> landuse2Node3,
      landuse2Node4.id -> landuse2Node4,
      boundaryNode1.id -> boundaryNode1,
      boundaryNode2.id -> boundaryNode2,
      boundaryNode3.id -> boundaryNode3,
      boundaryNode4.id -> boundaryNode4,
      substation.id -> substation
    )
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

    val building2: ClosedWay =
      ClosedWay(
        102L,
        Seq(
          nodes.building2Node1.id,
          nodes.building2Node2.id,
          nodes.building2Node3.id,
          nodes.building2Node4.id,
          nodes.building2Node1.id
        ),
        Map("building" -> "yes"),
        None
      )

    val building3: ClosedWay =
      ClosedWay(
        103L,
        Seq(
          nodes.building3Node1.id,
          nodes.building3Node2.id,
          nodes.building3Node3.id,
          nodes.building3Node4.id,
          nodes.building3Node1.id
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
        112L,
        Seq(
          nodes.highway2Node1.id,
          nodes.highway2Node2.id,
          nodes.highway2Node3.id,
          nodes.highway2Node4.id
        ),
        Map("highway" -> "motorway"),
        Some(OsmEntity.MetaInformation(Some(1), None, None, None, None, None))
      )

    val highway2oldVersion: OpenWay =
      OpenWay(
        112L,
        Seq(
          nodes.highway2Node1.id,
          nodes.highway2Node2.id,
          nodes.highway2Node2b.id,
          nodes.highway2Node3.id,
          nodes.highway2Node4.id
        ),
        Map("highway" -> "motorway"),
        Some(OsmEntity.MetaInformation(Some(0), None, None, None, None, None))
      )

    val landuse1: ClosedWay = ClosedWay(
      121L,
      Seq(
        nodes.landuse1Node1.id,
        nodes.landuse1Node2.id,
        nodes.landuse1Node3.id,
        nodes.landuse1Node4.id,
        nodes.landuse1Node1.id
      ),
      Map("landuse" -> "education"),
      None
    )

    val landuse2: ClosedWay = ClosedWay(
      122L,
      Seq(
        nodes.landuse2Node1.id,
        nodes.landuse2Node2.id,
        nodes.landuse2Node3.id,
        nodes.landuse2Node4.id,
        nodes.landuse2Node1.id
      ),
      Map.empty[String, String],
      None
    )

    val landuse3: ClosedWay = ClosedWay(
      122L,
      Seq(
        nodes.landuse3Node1.id,
        nodes.landuse3Node2.id,
        nodes.landuse3Node3.id,
        nodes.landuse3Node4.id,
        nodes.landuse3Node1.id
      ),
      Map("landuse" -> "residential"),
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

  object TestLvOsmoGridModel {
    private val nodeSeq: ParSeq[Node] = ParSeq(
      // buildings
      nodes.building1Node1,
      nodes.building1Node2,
      nodes.building1Node3,
      nodes.building1Node4,
      nodes.building2Node1,
      nodes.building2Node2,
      nodes.building2Node3,
      nodes.building2Node4,
      nodes.building3Node1,
      nodes.building3Node2,
      nodes.building3Node3,
      nodes.building3Node4,
      // highways
      nodes.highway1Node1,
      nodes.highway1Node2,
      nodes.highway2Node1,
      nodes.highway2Node2,
      nodes.highway2Node3,
      // landuses
      nodes.landuse1Node1,
      nodes.landuse1Node2,
      nodes.landuse1Node3,
      nodes.landuse1Node4,
      nodes.landuse2Node1,
      nodes.landuse2Node2,
      nodes.landuse2Node3,
      nodes.landuse2Node4,
      nodes.landuse3Node1,
      nodes.landuse3Node2,
      nodes.landuse3Node3,
      nodes.landuse3Node4,
      // boundaries
      nodes.boundaryNode1,
      nodes.boundaryNode2,
      nodes.boundaryNode3,
      nodes.boundaryNode4
    )
    private val waySeq: ParSeq[OsmEntity.Way] = ParSeq(
      ways.building1,
      ways.building2,
      ways.building3,
      ways.highway1,
      ways.highway2,
      ways.landuse1,
      ways.landuse2,
      ways.landuse3,
      ways.boundaryWay1,
      ways.boundaryWay2
    )
    private val relationSeq: ParSeq[Relation] = ParSeq(
      relations.boundary
    )
    val osmContainer: ParOsmContainer = ParOsmContainer(
      nodeSeq,
      waySeq,
      relationSeq
    )
    val lvOsmoGridModel: LvOsmoGridModel = LvOsmoGridModel(
      osmContainer,
      LvFilter()
    )
  }
}
