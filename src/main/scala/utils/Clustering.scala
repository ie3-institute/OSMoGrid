/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package utils

import edu.ie3.datamodel.models.StandardUnits
import edu.ie3.datamodel.models.input.NodeInput
import edu.ie3.datamodel.models.input.connector.LineInput
import edu.ie3.datamodel.models.input.connector.`type`.Transformer2WTypeInput
import edu.ie3.datamodel.models.input.system.LoadInput
import edu.ie3.osmogrid.exception.ClusterException
import edu.ie3.osmogrid.lv.LvGridGeneratorSupport.GridElements
import tech.units.indriya.ComparableQuantity
import tech.units.indriya.quantity.Quantities
import utils.Clustering.{Cluster, isImprovement, totalDistance}

import java.util.concurrent.ThreadLocalRandom
import javax.measure.quantity.Power
import scala.collection.parallel.CollectionConverters.ImmutableIterableIsParallelizable

final case class Clustering(
    connections: Connections[NodeInput],
    osmSubstations: Set[NodeInput],
    additionalSubstations: Set[NodeInput],
    nodes: Set[NodeInput],
    nodeSize: Int
) {

  /** Method to run the algorithm. This algorithm is based on PAM (Partitioning
    * Around Medoids).
    * @param maxIteration
    *   maximal number of iterations
    * @return
    *   a list of [[Cluster]]s
    */
  def run(implicit maxIteration: Int = 10): List[Cluster] = {
    // creates the initial clusters
    var clusters: List[Cluster] = createClusters(
      osmSubstations ++ additionalSubstations,
      nodes
    )
    var i = 0
    var finished = false

    // tries to improve the cluster
    while (!finished && i < maxIteration) {
      calculateStep(clusters) match {
        case Some(current) =>
          if (isImprovement(clusters, current)) {
            clusters = current
            i += 1
          } else {
            finished = true
          }
        case None =>
          finished = true
      }
    }

    clusters
  }

  /** Calculates the best next step.
    * @param clusters
    *   last step
    * @return
    *   an option
    */
  private def calculateStep(clusters: List[Cluster]): Option[List[Cluster]] = {
    // calculates all swaps and returns the best as an option
    additionalSubstations.par
      .flatMap { substation =>
        nodes.map { node =>
          val currentSubstation = Set(substation)
          val currentNode = Set(node)

          // swaps two nodes
          val updatedSubstations =
            additionalSubstations -- currentSubstation ++ currentNode
          val updatedNodes = nodes -- currentNode ++ currentSubstation

          val clusters =
            createClusters(updatedSubstations ++ osmSubstations, updatedNodes)

          (clusters, totalDistance(clusters))
        }
      }
      .seq
      .minByOption(_._2)
      .map(_._1)
  }

  /** Creates [[Cluster]] based on the given data.
    * @param substations
    *   substations
    * @param nodes
    *   all other nodes
    * @return
    *   a list of [[Cluster]]
    */
  private def createClusters(
      substations: Set[NodeInput],
      nodes: Set[NodeInput]
  ): List[Cluster] = {
    if (substations.size + nodes.size != nodeSize) {
      List.empty
    } else {
      nodes
        .map { n =>
          val closest = substations
            .map { s => s -> connections.getDistance(n, s) }
            .minByOption(_._2)
            .map(_._1)
            .getOrElse(
              throw ClusterException(s"No substation found for node: $n")
            )

          (n, closest)
        }
        .groupMap(_._2)(_._1)
        .map { case (substation, connectedNodes) =>
          // calculate all distances
          val distances = connectedNodes.map(node =>
            connections
              .getDistance(substation, node)
              .map(_.getValue.doubleValue())
              .getOrElse(Double.MaxValue)
          )

          distances.reduceOption { (a, b) => a + b } match {
            case Some(distance) => Cluster(substation, connectedNodes, distance)
            case None => Cluster(substation, connectedNodes, Double.MaxValue)
          }
        }
        .toList
    }
  }
}

object Clustering {
  def setup(
      gridElements: GridElements,
      lines: Set[LineInput],
      transformer2WTypeInput: Transformer2WTypeInput,
      loadSimultaneousFactor: Double
  ): Clustering = {
    val substationCount = getSubstationCount(
      gridElements.loads.toSet,
      loadSimultaneousFactor,
      transformer2WTypeInput
    )

    val connections: Connections[NodeInput] =
      Connections(gridElements, lines.toSeq)
    val osmSubstations = gridElements.substations.values.toSet

    val additionalSubstationCount = substationCount - osmSubstations.size

    val additionalSubstations: Set[NodeInput] =
      if (additionalSubstationCount > 0) {
        val maxNr = gridElements.nodes.size
        val nodes = gridElements.nodes.values.toList

        // finds random nodes
        Range
          .Int(0, additionalSubstationCount, 1)
          .map { _ => nodes(ThreadLocalRandom.current().nextInt(0, maxNr)) }
          .toSet
      } else {
        Set.empty[NodeInput]
      }

    Clustering(
      connections,
      osmSubstations,
      additionalSubstations,
      gridElements.nodes.values.toSet.diff(additionalSubstations),
      gridElements.nodes.size + gridElements.substations.size
    )
  }

  /** Calculates the number of substations that are needed.
    * @param loads
    *   all loads
    * @param loadSimultaneousFactor
    *   for loads
    * @param transformer2WTypeInput
    *   type of transformer
    * @return
    *   a number of substations
    */
  private def getSubstationCount(
      loads: Set[LoadInput],
      loadSimultaneousFactor: Double,
      transformer2WTypeInput: Transformer2WTypeInput
  ): Int = {
    // calculates the maximum power
    val maxPower: ComparableQuantity[Power] =
      loads.toList.map { l => l.getsRated() }.reduceOption { (powerA, powerB) =>
        powerA.add(powerB)
      } match {
        case Some(power) => power.multiply(loadSimultaneousFactor)
        case None        => Quantities.getQuantity(0d, StandardUnits.S_RATED)
      }

    // calculates the number of substations we need
    if (transformer2WTypeInput.getsRated().isGreaterThan(maxPower)) {
      1 // if one substation is enough
    } else {
      // rounds up the number of substations
      Math
        .ceil(
          maxPower
            .divide(transformer2WTypeInput.getsRated())
            .getValue
            .doubleValue()
        )
        .toInt
    }
  }

  /** Checks if there are still improvements greater than 1 %.
    *
    * @param old
    *   clusters
    * @param current
    *   new clusters
    * @return
    *   true if the are still improvements
    */
  def isImprovement(
      old: List[Cluster],
      current: List[Cluster]
  ): Boolean = totalDistance(current) <= totalDistance(old) * 0.99

  /** Calculates the total connection distance of a list of [[Cluster]]s.
    *
    * @param list
    *   of clusters
    * @return
    *   either the total distance or [[Double.MaxValue]]
    */
  private def totalDistance(list: List[Cluster]): Double = {
    if (list.isEmpty) {
      Double.MaxValue
    } else {
      list.map { l => l.distances }.reduceOption { (a, b) => a + b } match {
        case Some(value) => value
        case None        => Double.MaxValue
      }
    }
  }

  final case class Cluster(
      substation: NodeInput,
      nodes: Set[NodeInput],
      distances: Double
  )
}
