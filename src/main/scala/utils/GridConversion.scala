/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package utils

import edu.ie3.datamodel.models.input.connector.`type`.{
  LineTypeInput,
  Transformer2WTypeInput
}
import edu.ie3.datamodel.models.input.connector.{
  LineInput,
  SwitchInput,
  Transformer2WInput,
  Transformer3WInput
}
import edu.ie3.datamodel.models.input.container.{
  GraphicElements,
  RawGridElements,
  SubGridContainer,
  SystemParticipants
}
import edu.ie3.datamodel.models.input.graphics.{
  LineGraphicInput,
  NodeGraphicInput
}
import edu.ie3.datamodel.models.input.system._
import edu.ie3.datamodel.models.input.system.characteristic.{
  CosPhiFixed,
  OlmCharacteristicInput
}
import edu.ie3.datamodel.models.input.{MeasurementUnitInput, NodeInput}
import edu.ie3.datamodel.models.profile.BdewStandardLoadProfile
import edu.ie3.datamodel.models.voltagelevels.VoltageLevel
import edu.ie3.util.geo.GeoUtils
import edu.ie3.util.osm.model.OsmEntity.Node
import edu.ie3.util.quantities.QuantityUtils._
import org.locationtech.jts.geom.impl.CoordinateArraySequence
import org.locationtech.jts.geom.{LineString, Point}
import tech.units.indriya.ComparableQuantity
import tech.units.indriya.quantity.Quantities
import tech.units.indriya.unit.Units

import java.util
import java.util.UUID
import javax.measure.Quantity
import javax.measure.quantity.{Dimensionless, Length, Power}

object GridConversion {

  /** Create a node.
    *
    * @param vTarget
    *   the target voltage of the node
    * @param voltageLevel
    *   the voltage level
    * @param id
    *   the id of the node
    * @param coordinate
    *   the coordinate of the node position
    * @return
    *   the created node
    */
  def buildNode(
      voltageLevel: VoltageLevel
  )(id: String, coordinate: Point, isSlack: Boolean)(implicit
      subnet: Int = 1,
      vTarget: ComparableQuantity[Dimensionless] = 1d.asPu
  ): NodeInput = {
    val idString =
      if (id.isEmpty) {
        val coordinatesString = coordinate.getCoordinates.mkString
        val cleanedString =
          coordinatesString
            .replace(",", "")
            .replace("NaN", "")
            .replace(" )", ")")
        "Node " + cleanedString
      } else id

    new NodeInput(
      UUID.randomUUID(),
      idString,
      vTarget,
      isSlack,
      coordinate,
      voltageLevel,
      subnet
    )
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

  /** Builds line between the nodes. Includes passed passed osm street node to
    * the geo position to track the street profile.
    *
    * @param firstNode
    *   node at which the line starts
    * @param secondNode
    *   node at which the line ends
    * @param passedStreetNodes
    *   osm street nodes the line follows along
    * @param lineType
    *   type of the line to build
    * @return
    *   the built line
    */
  def buildLine(
      firstNode: NodeInput,
      secondNode: NodeInput,
      passedStreetNodes: Seq[Node],
      lineType: LineTypeInput
  ): LineInput = {
    val lineGeoNodes = passedStreetNodes
      .map(_.coordinate.getCoordinate)
      .toArray
    val geoPosition = new LineString(
      new CoordinateArraySequence(
        lineGeoNodes
      ),
      GeoUtils.DEFAULT_GEOMETRY_FACTORY
    )
    val id = s"Line between: " + passedStreetNodes.headOption
      .getOrElse(
        throw new IllegalArgumentException(
          s"Line between $firstNode and $secondNode has no first node."
        )
      )
      .id + "-" + passedStreetNodes.lastOption
      .getOrElse(
        throw new IllegalArgumentException(
          s"Line between $firstNode and $secondNode has no last node."
        )
      )
      .id
    new LineInput(
      UUID.randomUUID(),
      id,
      firstNode,
      secondNode,
      1,
      lineType,
      GeoUtils.calcHaversine(geoPosition),
      geoPosition,
      // todo: What do we expect as OlmCharacteristic?
      null
    )
  }

  /** Creates a [[Transformer2WInput]].
    *
    * @param nodeA
    *   higher voltage node
    * @param nodeB
    *   lower voltage node
    * @param parallelDevices
    *   overall amount of parallel transformers to automatically construct (e.g.
    *   parallelDevices = 2 will build a total of two transformers using the
    *   specified parameters)
    * @param transformerType
    *   of 2W transformer
    * @param id
    *   of the asset
    * @param tapPos
    *   Tap position of this transformer
    * @param autoTap
    *   True, if the tap position of the transformer is adapted automatically
    * @return
    *   a new [[Transformer2WInput]]
    */
  def buildTransformer2W(
      nodeA: NodeInput,
      nodeB: NodeInput,
      parallelDevices: Int,
      transformerType: Transformer2WTypeInput
  )(implicit
      id: String = s"Transformer between ${nodeA.getId} and ${nodeB.getId}",
      tapPos: Int = 0,
      autoTap: Boolean = false
  ): Transformer2WInput = new Transformer2WInput(
    UUID.randomUUID(),
    id,
    nodeA,
    nodeB,
    parallelDevices,
    transformerType,
    tapPos,
    autoTap
  )

  /** Creates a load.
    *
    * @param id
    *   the id for the load to build
    * @param ratedPower
    *   the rated power of the load
    * @param node
    *   the node at which the load will be connected
    * @return
    *   the created load
    */
  def buildLoad(id: String, ratedPower: ComparableQuantity[Power])(
      node: NodeInput
  ) =
    new LoadInput(
      UUID.randomUUID(),
      id,
      node,
      CosPhiFixed.CONSTANT_CHARACTERISTIC,
      null,
      BdewStandardLoadProfile.H0,
      false,
      // todo: What to do for econsannual?
      0.asWattHour,
      ratedPower,
      1d
    )

  /** Builds a GridContainer by adding all assets together
    */
  def buildGridContainer(
      gridName: String,
      nodes: util.Set[NodeInput],
      lines: util.Set[LineInput],
      loads: util.Set[LoadInput]
  )(implicit
      subnetNr: Int = 1,
      transformer2Ws: util.Set[Transformer2WInput] =
        new util.HashSet[Transformer2WInput]
  ): SubGridContainer = {
    val rawGridElements = new RawGridElements(
      nodes,
      lines,
      transformer2Ws,
      new util.HashSet[Transformer3WInput],
      new util.HashSet[SwitchInput],
      new util.HashSet[MeasurementUnitInput]
    )
    val systemParticipants = new SystemParticipants(
      new util.HashSet[BmInput],
      new util.HashSet[ChpInput],
      new util.HashSet[EvcsInput],
      new util.HashSet[EvInput],
      new util.HashSet[FixedFeedInInput],
      new util.HashSet[HpInput],
      loads,
      new util.HashSet[PvInput],
      new util.HashSet[StorageInput],
      new util.HashSet[WecInput]
    )
    val graphicElements = new GraphicElements(
      new util.HashSet[NodeGraphicInput],
      new util.HashSet[LineGraphicInput]
    )
    new SubGridContainer(
      gridName,
      subnetNr,
      rawGridElements,
      systemParticipants,
      graphicElements
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
