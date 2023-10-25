/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package utils

import edu.ie3.datamodel.models.input.connector.LineInput
import edu.ie3.datamodel.models.input.connector.`type`.LineTypeInput
import edu.ie3.datamodel.models.input.container.{
  GraphicElements,
  RawGridElements,
  SubGridContainer,
  SystemParticipants
}
import edu.ie3.datamodel.models.input.system.characteristic.OlmCharacteristicInput
import edu.ie3.datamodel.models.input.{AssetInput, NodeInput}
import edu.ie3.osmogrid.exception.IllegalStateException
import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.osmogrid.io.input.AssetInformation
import edu.ie3.util.geo.GeoUtils
import edu.ie3.util.osm.model.OsmEntity.Node
import tech.units.indriya.ComparableQuantity
import tech.units.indriya.quantity.Quantities
import tech.units.indriya.unit.Units

import java.util.UUID
import javax.measure.Quantity
import javax.measure.quantity.{ElectricPotential, Length}
import scala.jdk.CollectionConverters._

object GridConversion {

  /** Method to convert an [[OsmGraph]] into a set of [[NodeInput]]s and a set
    * of [[LineInput]]s.
    * @param n
    *   subnet number
    * @param graph
    *   grid graph
    * @param nodeConversion
    *   conversion between [[Node]]s and [[NodeInput]]s
    * @return
    *   a [[SubGridContainer]] and node changes
    */
  def convertMv(
      n: Int,
      graph: OsmGraph,
      nodeConversion: NodeConversion,
      assetInformation: AssetInformation
  ): (SubGridContainer, Seq[NodeInput]) = {
    // converting the osm nodes to psdm nodes
    val nodes: Seq[NodeInput] =
      graph
        .vertexSet()
        .asScala
        .map { node =>
          nodeConversion.getPSDMNode(node).copy().subnet(n).build()
        }
        .toSeq

    val lineTypes
        : Map[ComparableQuantity[ElectricPotential], Seq[LineTypeInput]] =
      assetInformation.lineTypes.groupBy { lt => lt.getvRated() }

    // converting the edges into psdm lines
    val lines: Set[LineInput] = graph
      .edgeSet()
      .asScala
      .zipWithIndex
      .map { case (e, index) =>
        val nodeA = nodeConversion.getPSDMNode(graph.getEdgeSource(e))
        val nodeB = nodeConversion.getPSDMNode(graph.getEdgeTarget(e))

        val lineType =
          lineTypes(nodeA.getVoltLvl.getNominalVoltage).headOption.getOrElse(
            throw IllegalStateException(
              "There are no line types within received asset types. Can not build the grid!"
            )
          )

        // creating a new PSDM line
        buildLine(
          s"${n}_$index",
          nodeA,
          nodeB,
          1,
          lineType,
          e.getDistance
        )
      }
      .toSet

    val elements: List[AssetInput] = List(nodes, lines).flatten

    // creating sub grid container
    val rawGridElements = new RawGridElements(elements.asJava)
    val participants = new SystemParticipants(
      Set.empty[SystemParticipants].asJava
    )
    val graphics = new GraphicElements(Set.empty[GraphicElements].asJava)
    val subGridContainer = new SubGridContainer(
      s"Subnet_$n",
      n,
      rawGridElements,
      participants,
      graphics
    )

    // returning the finished data
    (subGridContainer, nodes)
  }

  /** Method for creating a [[LineInput]].
    * @param id
    *   of the line
    * @param nodeA
    *   start point of the line
    * @param nodeB
    *   end point of the line
    * @param lineType
    *   type of the line
    * @param length
    *   of the line
    * @return
    *   a new [[LineInput]]
    */
  def buildLine(
      id: String,
      nodeA: NodeInput,
      nodeB: NodeInput,
      parallel: Int,
      lineType: LineTypeInput,
      length: Quantity[Length]
  ): LineInput = {
    new LineInput(
      UUID.randomUUID(),
      id,
      nodeA,
      nodeB,
      parallel,
      lineType,
      Quantities.getQuantity(length.getValue, Units.METRE),
      GeoUtils.buildSafeLineStringBetweenCoords(
        nodeA.getGeoPosition.getCoordinate,
        nodeB.getGeoPosition.getCoordinate
      ),
      OlmCharacteristicInput.CONSTANT_CHARACTERISTIC
    )
  }

  /** This utility object is used to easily convert [[NodeInput]]s and
    * corresponding [[Node]]s into each other.
    *
    * @param conversionToOsm
    *   conversion [[NodeInput]] -> [[Node]]
    * @param conversionToPSDM
    *   conversion [[Node]] -> [[NodeInput]]
    */
  final case class NodeConversion(
      conversionToOsm: Map[NodeInput, Node],
      conversionToPSDM: Map[Node, NodeInput]
  ) {

    /** Returns all [[NodeInput]]s.
      */
    def allPsdmNodes: List[NodeInput] = conversionToPSDM.values.toList

    /** Returns all [[Node]]s.
      */
    def allOsmNodes: List[Node] = conversionToOsm.values.toList

    /** Converts a given [[NodeInput]] into a [[Node]].
      *
      * @param node
      *   given psdm node
      * @return
      *   a osm node
      */
    def getOsmNode(node: NodeInput): Node = {
      conversionToOsm(node)
    }

    /** Converts multiple given [[NodeInput]]s into corresponding [[Node]]s.
      *
      * @param nodes
      *   list of psdm nodes
      * @return
      *   list of osm nodes
      */
    def getOsmNodes(nodes: List[NodeInput]): List[Node] = {
      nodes.map { node => conversionToOsm(node) }
    }

    /** Converts a given [[Node]] into a [[NodeInput]].
      *
      * @param node
      *   given osm node
      * @return
      *   a psdm node
      */
    def getPSDMNode(node: Node): NodeInput = {
      conversionToPSDM(node)
    }

    /** Converts multiple given [[Node]]s into corresponding [[NodeInput]]s.
      *
      * @param nodes
      *   list of osm nodes
      * @return
      *   list of psdm nodes
      */
    def getPSDMNodes(nodes: List[Node]): List[NodeInput] = {
      nodes.map { node => conversionToPSDM(node) }
    }
  }

  object NodeConversion {

    /** Method for building [[NodeConversion]].
      *
      * @param nodes
      *   all psdm nodes
      * @param osmNodes
      *   all osm nodes
      * @return
      *   a new [[NodeConversion]]
      */
    def apply(
        nodes: List[NodeInput],
        osmNodes: List[Node]
    ): NodeConversion = {
      val conversion: Map[NodeInput, Node] = nodes.map { node =>
        val coordinate = node.getGeoPosition.getCoordinate

        // calculate the distance to each osm node
        val sortedList = osmNodes
          .map { node: Node =>
            (
              node,
              GeoUtils.calcHaversine(coordinate, node.coordinate.getCoordinate)
            )
          }
          .sortBy(_._2)

        // map the osm node with the shortest distance
        node -> sortedList(0)._1
      }.toMap

      // creating the NodeConversion object
      NodeConversion(conversion, conversion.map { case (k, v) => v -> k })
    }
  }

}
