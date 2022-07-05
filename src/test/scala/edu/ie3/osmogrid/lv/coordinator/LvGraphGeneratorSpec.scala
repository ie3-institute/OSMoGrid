/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.lv.coordinator

import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.osmogrid.lv.LvGridGenerator
import edu.ie3.osmogrid.model.OsmTestData
import edu.ie3.test.common.UnitSpec
import edu.ie3.util.geo.GeoUtils
import edu.ie3.util.osm.model.OsmEntity.Node
import edu.ie3.util.quantities.QuantityMatchers.equalWithTolerance
import edu.ie3.util.quantities.QuantityUtils.RichQuantityDouble
import tech.units.indriya.ComparableQuantity
import edu.ie3.util.geo.RichGeometries.RichCoordinate
import org.locationtech.jts.geom.Coordinate
import org.scalatestplus.mockito.MockitoSugar.mock
import edu.ie3.osmogrid.lv.GraphBuildingSupport.BuildingGraphConnection
import collection.parallel.CollectionConverters.seqIsParallelizable
import javax.measure.quantity.Length
import scala.collection.parallel.ParSeq
import scala.util.{Failure, Success, Try}

class LvGridGeneratorSpec extends UnitSpec with OsmTestData {

  "A lv grid generator spec" when {
    "building a street graph" should {
      val buildStreetGraph =
        PrivateMethod[OsmGraph](Symbol("buildStreetGraph"))
      val waySeq = ParSeq(ways.highway1, ways.highway2)
      val wayNodes = ways.highway1.nodes ++ ways.highway2.nodes
      val actual: OsmGraph =
        LvGridGenerator invokePrivate buildStreetGraph(waySeq, nodes.nodesMap)

      "build a graph with all nodes and edges" in {
        actual.vertexSet().size() shouldBe 4
        actual.edgeSet().size() shouldBe 3
        wayNodes.foreach(node =>
          actual.containsVertex(
            nodes.nodesMap.getOrElse(node, fail(f"Node: $node not found"))
          ) shouldBe true
        )
      }

      "add the correct weights to the edges" in {
        val nodeA = nodes.highway1Node1
        val nodeB = nodes.highway1Node2
        val distance = GeoUtils.calcHaversine(
          nodeA.latitude,
          nodeA.longitude,
          nodeB.latitude,
          nodeB.longitude
        )
        actual.getEdge(nodeA, nodeB).getDistance should equalWithTolerance(
          distance
        )
      }

    }

    "determining building graph connections" should {
      "calculate all building graph connections" in {
        val calcBuildingGraphConnections =
          PrivateMethod[ParSeq[BuildingGraphConnection]](
            Symbol("calcBuildingGraphConnections")
          )
        val landuses = Seq(ways.landuse2).par
        val buildings = Seq(ways.building1, ways.building2).par
        val highways = Seq(ways.highway1, ways.highway2).par

        val buildingGraphConnections: ParSeq[BuildingGraphConnection] =
          LvGridGenerator invokePrivate calcBuildingGraphConnections(
            landuses,
            buildings,
            highways,
            nodes.nodesMap,
            0.5d.asKiloWattPerSquareMetre,
            0.0001.asKilometre
          )
        buildingGraphConnections.size shouldBe 2
        buildingGraphConnections.foreach {
          case bgc: BuildingGraphConnection
              if bgc.building == ways.building1 => {
            val highWayCoordinateA = GeoUtils.buildCoordinate(
              nodes.highway1Node1.latitude,
              nodes.highway1Node1.longitude
            )
            val highWayCoordinateB = GeoUtils.buildCoordinate(
              nodes.highway1Node2.latitude,
              nodes.highway1Node2.longitude
            )
            GeoUtils
              .buildCoordinate(
                bgc.graphConnectionNode.latitude,
                bgc.graphConnectionNode.longitude
              )
              .isBetween(
                highWayCoordinateA,
                highWayCoordinateB,
                1e-3
              ) shouldBe true
          }
          case bgc: BuildingGraphConnection if bgc.building == ways.building2 =>
            bgc.highwayNodeA shouldBe nodes.highway2Node1
            bgc.highwayNodeB shouldBe nodes.highway2Node2
            bgc.graphConnectionNode shouldBe nodes.highway2Node2
        }
      }

      "find the closest ways to connect the buildings to" in {
        val lineNodeA = Node(1L, 50d, 7d, Map.empty[String, String], None)
        val lineNodeB = Node(2L, 50d, 8d, Map.empty[String, String], None)
        val buildingCenter = GeoUtils.buildCoordinate(49d, 7.5)
        val expectedOrthogonal = GeoUtils.buildCoordinate(50d, 7.5)
        val nodes = Map(1L -> lineNodeA, 2L -> lineNodeB)
        val minDistance = 0.05.asKilometre
        val getClosest =
          PrivateMethod[Try[(ComparableQuantity[Length], Node)]](
            Symbol("getClosest")
          )
        LvGridGenerator invokePrivate getClosest(
          lineNodeA.id,
          lineNodeB.id,
          buildingCenter,
          nodes,
          minDistance
        ) match {
          case Success(distanceAndNode) =>
            distanceAndNode._1 should equalWithTolerance(
              buildingCenter.haversineDistance(expectedOrthogonal)
            )
            distanceAndNode._2.latitude  shouldBe (expectedOrthogonal.y +- 1e-8)
            distanceAndNode._2.longitude shouldBe (expectedOrthogonal.x +- 1e-8)
          case Failure(exc) => fail(s"Test failed due to exception: ", exc)
        }
      }

      "calculate an orthogonal projection correctly" in {
        val coordinateA = GeoUtils.buildCoordinate(50d, 7)
        val coordinateB = GeoUtils.buildCoordinate(50d, 8)
        val point = GeoUtils.buildCoordinate(49d, 7.5)
        val orthogonalProjection =
          PrivateMethod[Coordinate](Symbol("orthogonalProjection"))
        val actual = LvGridGenerator invokePrivate orthogonalProjection(
          coordinateA,
          coordinateB,
          point
        )
        actual.x shouldBe (7.5 +- 1e-9)
        actual.y shouldBe (50d +- 1e-9)
      }

      "update the street graph with all building graph connections" in {
        val osmGraph = new OsmGraph()
        osmGraph.addVertex(nodes.highway1Node1)
        osmGraph.addVertex(nodes.highway1Node2)
        val connectingNode = Node(
          99L,
          nodes.highway1Node1.latitude + nodes.highway1Node2.latitude,
          nodes.highway1Node1.longitude + nodes.highway1Node2.longitude,
          Map.empty,
          None
        )
        val buildingGraphConnection = BuildingGraphConnection(
          ways.building1,
          mock[Coordinate],
          10.asKiloWatt,
          nodes.highway1Node1,
          nodes.highway1Node2,
          connectingNode
        )
        val updateGraphWithBuildingConnections =
          PrivateMethod[OsmGraph](Symbol("updateGraphWithBuildingConnections"))
        val actual: OsmGraph =
          LvGridGenerator invokePrivate updateGraphWithBuildingConnections(
            osmGraph,
            Seq(buildingGraphConnection).par
          )
        actual.containsVertex(connectingNode) shouldBe true
        actual.containsEdge(
          nodes.highway1Node1,
          nodes.highway1Node2
        ) shouldBe false
        actual.containsEdge(nodes.highway1Node1, connectingNode) shouldBe true
        actual.containsEdge(connectingNode, nodes.highway1Node2) shouldBe true
      }
    }
  }

  "building an lv grid" should {
   "convert the osm graph to a correct subgrid" in {
     val osmoGridModel = ???
     val powerDensity = ???
     val minDistance = ???
     val considerBuildingGraphConnections = ???
     
   }
  }
}
