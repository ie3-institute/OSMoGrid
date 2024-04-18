/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.mv

import edu.ie3.datamodel.models.input.NodeInput
import edu.ie3.datamodel.models.input.connector.LineInput
import edu.ie3.datamodel.models.input.connector.`type`.LineTypeInput
import edu.ie3.datamodel.models.input.container.SubGridContainer
import edu.ie3.datamodel.models.input.system.LoadInput
import edu.ie3.osmogrid.exception.IllegalStateException
import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.osmogrid.io.input.AssetInformation
import tech.units.indriya.ComparableQuantity
import utils.GridConversion.{NodeConversion, buildGridContainer, buildLine}

import java.util
import java.util.UUID
import javax.measure.quantity.ElectricPotential
import scala.jdk.CollectionConverters._

object MvGridGeneratorSupport {

  /** Method to convert an [[OsmGraph]] into a [[SubGridContainer]] and a
    * sequence of changed [[NodeInput]]s.
    *
    * @param n
    *   subnet number
    * @param graph
    *   grid graph
    * @param hvNode
    *   node to the hv grid
    * @param nodeConversion
    *   conversion between [[edu.ie3.util.osm.model.OsmEntity.Node]]s and
    *   [[NodeInput]]s
    * @return
    *   a [[SubGridContainer]] and node changes
    */
  def buildGrid(
      n: Int,
      graph: OsmGraph,
      hvNode: NodeInput,
      nodeConversion: NodeConversion,
      assetInformation: AssetInformation
  ): (SubGridContainer, Map[UUID, NodeInput]) = {
    // converting the osm nodes to psdm nodes
    val nodes: Map[UUID, NodeInput] =
      graph
        .vertexSet()
        .asScala
        .map { node =>
          val nodeInput = nodeConversion.getPSDMNode(node)
          val copy = nodeInput.copy().subnet(n)

          // only the hv node should be a slack node
          val updatedNode = if (nodeInput.getUuid != hvNode.getUuid) {
            copy.slack(false).build()
          } else {
            copy.slack(true).build()
          }

          updatedNode.getUuid -> updatedNode
        }
        .toMap

    val lineTypes
        : Map[ComparableQuantity[ElectricPotential], Seq[LineTypeInput]] =
      assetInformation.lineTypes.groupBy { lt => lt.getvRated() }

    // converting the edges into psdm lines
    val lines: Set[LineInput] = graph
      .edgeSet()
      .asScala
      .zipWithIndex
      .map { case (e, index) =>
        val uuidA = nodeConversion.getPSDMNode(graph.getEdgeSource(e)).getUuid
        val uuidB = nodeConversion.getPSDMNode(graph.getEdgeTarget(e)).getUuid

        // getting the updated nodes
        val nodeA = nodes(uuidA)
        val nodeB = nodes(uuidB)

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

    val subGridContainer = buildGridContainer(
      s"Subnet_$n",
      nodes.values.toSet.asJava,
      lines.asJava,
      new util.HashSet[LoadInput]()
    )(n)

    // returning the finished data
    (subGridContainer, nodes)
  }
}
