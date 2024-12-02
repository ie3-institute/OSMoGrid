/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package utils

import com.typesafe.scalalogging.LazyLogging
import edu.ie3.datamodel.models.StandardUnits
import edu.ie3.datamodel.models.input.NodeInput
import edu.ie3.datamodel.models.input.connector.LineInput
import edu.ie3.datamodel.models.input.connector.`type`.Transformer2WTypeInput
import edu.ie3.datamodel.models.input.system.LoadInput
import edu.ie3.osmogrid.exception.ClusterException
import edu.ie3.osmogrid.lv.LvGridGeneratorSupport.GridElements
import tech.units.indriya.ComparableQuantity
import tech.units.indriya.quantity.Quantities
import utils.Clustering.{Cluster, isImprovement}

import java.util.concurrent.ThreadLocalRandom
import javax.measure.quantity.Power
import scala.collection.parallel.CollectionConverters.ImmutableIterableIsParallelizable

final case class Clustering(
    connections: Connections[NodeInput],
    osmSubstations: Set[NodeInput],
    additionalSubstations: Set[NodeInput],
    nodes: Set[NodeInput],
    nodeCount: Int,
    substationCount: Int,
) extends LazyLogging {

  /** Method to run the algorithm. This algorithm is based on PAM (Partitioning
    * Around Medoids).
    *
    * @param maxIteration
    *   maximal number of iterations
    * @return
    *   a list of [[Cluster]]s
    */
  def run(implicit maxIteration: Int = 50): List[Cluster] = {
    // creates the initial clusters
    val initialClusters: Set[Cluster] = createClusters(
      osmSubstations ++ additionalSubstations,
      nodes,
    )
    val initialSubstations = Set(initialClusters.map(_.substation))

    val (clusters, _) = Range.Int
      .apply(0, maxIteration, 1)
      .foldLeft(initialClusters, initialSubstations) {
        case ((currentClusters, substationCombinations), _) =>
          // calculate a swap for all swappable substations
          val swaps = currentClusters
            .filter(cluster => !osmSubstations.contains(cluster.substation))
            .map { cluster =>
              val node =
                cluster.nodes.find(_ => true).getOrElse(cluster.substation)
              (cluster.substation, node)
            }

          calculateStep(swaps, substationCombinations) match {
            case Some(maybeUpdate) =>
              if (isImprovement(currentClusters, maybeUpdate)) {
                val substations = Set(maybeUpdate.map(_.substation))

                val updatedFoundCombinations: Set[Set[NodeInput]] =
                  substationCombinations ++ substations

                (maybeUpdate, updatedFoundCombinations)
              } else {
                (currentClusters, substationCombinations)
              }
            case None =>
              (currentClusters, substationCombinations)
          }
      }

    clusters.toList
  }

  /** Calculates the best next step.
    *
    * @param swaps
    *   a set of substation and node swaps
    * @return
    *   an option
    */
  private def calculateStep(
      swaps: Set[(NodeInput, NodeInput)],
      substationCombinations: Set[Set[NodeInput]],
  ): Option[Set[Cluster]] = {
    val (updatedSubstations, updatedNodes) =
      swaps.foldLeft((osmSubstations ++ additionalSubstations, nodes)) {
        case ((substations, allNodes), swap) =>
          val newSubstations = Set(swap._2)
          val newNode = Set(swap._1)

          val currentSubstations = substations -- newNode ++ newSubstations
          val currentNodes = allNodes -- newSubstations ++ newNode

          (currentSubstations, currentNodes)
      }

    if (substationCombinations.contains(updatedSubstations)) {
      None
    } else {
      val clusters = createClusters(updatedSubstations, updatedNodes)

      Option.when(clusters.nonEmpty)(clusters)
    }
  }

  /** Creates [[Cluster]] based on the given data.
    *
    * @param substations
    *   substations
    * @param others
    *   all other nodes
    * @return
    *   a set of [[Cluster]]
    */
  private def createClusters(
      substations: Set[NodeInput],
      others: Set[NodeInput],
  ): Set[Cluster] = {
    if (substations.size + nodes.size != nodeCount) {
      logger.debug(
        s"The number of found nodes ${substations.size + nodes.size} does not equal the expected number $nodeCount! Discarding the found option."
      )
      Set.empty
    } else {
      val nodeToSubstation = findClosestSubstation(substations, others)
      val updatedSubstations = nodeToSubstation.map(_._2)

      val updatedNodeToSubstation =
        if (updatedSubstations.size != substationCount) {
          val newNodes = substations.diff(updatedSubstations)

          val unusedIds = newNodes.map(_.getId)
          logger.info(
            s"Unused substations exists. Converted these substations to normal nodes. Unused substations: $unusedIds"
          )

          findClosestSubstation(updatedSubstations, others ++ newNodes)
        } else nodeToSubstation

      val groupedNodes: Map[NodeInput, Set[NodeInput]] =
        updatedNodeToSubstation.groupMap(_._2)(_._1)

      groupedNodes.par
        .map { case (substation, connectedNodes) =>
          // calculate all distances
          val distances = connectedNodes.map(node =>
            connections
              .getDistance(substation, node)
              .map(_.getValue.doubleValue())
              .getOrElse(Double.MaxValue)
          )

          distances.reduceOption { (a, b) => a + b } match {
            case Some(distance) =>
              Cluster(substation, connectedNodes, distance)
            case None => Cluster(substation, connectedNodes, Double.MaxValue)
          }
        }
        .seq
        .toSet
    }
  }

  /** Method for finding the closest substation for the given nodes.
    * @param substations
    *   a set of all known substations
    * @param others
    *   all other nodes
    * @return
    *   a set of nodes to closest substation
    */
  private def findClosestSubstation(
      substations: Set[NodeInput],
      others: Set[NodeInput],
  ): Set[(NodeInput, NodeInput)] =
    others.map { n =>
      val closest = substations
        .map { s => s -> connections.getDistance(n, s) }
        .minByOption(_._2)
        .map(_._1)
        .getOrElse(
          throw ClusterException(s"No substation found for node: $n")
        )

      (n, closest)
    }
}

object Clustering {
  def setup(
      gridElements: GridElements,
      lines: Set[LineInput],
      transformer2WTypeInput: Transformer2WTypeInput,
      loadSimultaneousFactor: Double,
  ): Clustering = {
    if (gridElements.nodes.size + gridElements.substations.size < 2) {
      throw ClusterException("Cannot cluster a grid with less than two nodes.")
    }

    val substationCount = getSubstationCount(
      gridElements.loads.toSet,
      loadSimultaneousFactor,
      transformer2WTypeInput,
    )

    val connections: Connections[NodeInput] =
      Connections(gridElements, lines.toSeq)
    val osmSubstations = gridElements.substations.values.toSet

    val additionalSubstationCount = substationCount - osmSubstations.size

    val additionalSubstations: Set[NodeInput] =
      if (additionalSubstationCount > 0) {
        val maxNr = gridElements.nodes.size
        val nodes = gridElements.nodes.values.toIndexedSeq

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
      gridElements.nodes.size + gridElements.substations.size,
      osmSubstations.size + additionalSubstations.size,
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
      transformer2WTypeInput: Transformer2WTypeInput,
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
      old: Set[Cluster],
      current: Set[Cluster],
  ): Boolean =
    calculateTotalLineLength(current) <= calculateTotalLineLength(old) * 0.99

  /** Calculates the total connection distance of a list of [[Cluster]]s.
    *
    * @param list
    *   of clusters
    * @return
    *   either the total distance or [[Double.MaxValue]]
    */
  def calculateTotalLineLength(list: Set[Cluster]): Double = {
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
      distances: Double,
  )
}
