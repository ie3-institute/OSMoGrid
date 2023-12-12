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
import edu.ie3.osmogrid.lv.LvGridGeneratorSupport.GridElements
import tech.units.indriya.ComparableQuantity
import tech.units.indriya.quantity.Quantities
import utils.Clustering.{Cluster, isImprovement, totalDistance}

import java.util.concurrent.ThreadLocalRandom
import javax.measure.quantity.Power

final case class Clustering(
    connections: Connections[NodeInput],
    osmSubstations: List[NodeInput],
    additionalSubstations: List[NodeInput],
    nodes: List[NodeInput]
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
    * @param list
    *   last step
    * @return
    *   an option
    */
  private def calculateStep(list: List[Cluster]): Option[List[Cluster]] = {
    // list of changeable substations
    val changeable: List[NodeInput] =
      list.filter { l => !osmSubstations.contains(l.substation) }.map { c =>
        c.substation
      }

    // calculates all swaps and returns the best as an option
    changeable
      .flatMap { s =>
        nodes.map { n =>
          // swaps two nodes
          val substations = changeable.filter { c => c != s } :+ n
          val other = nodes.filter { c => c != n } :+ s

          val clusters = createClusters(substations :++ osmSubstations, other)

          (clusters, totalDistance(clusters))
        }
      }
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
      substations: List[NodeInput],
      nodes: List[NodeInput]
  ): List[Cluster] = {
    nodes
      .map { n =>
        val closest = substations
          .map { s => s -> connections.getDistance(n, s) }
          .sortBy(_._2)
          .toList(0)
          ._1

        (n, closest)
      }
      .groupMap(_._2)(_._1)
      .map { case (s, list) =>
        list
          .map { n => connections.getDistance(s, n).getValue.doubleValue() }
          .reduceOption { (a, b) => a + b } match {
          case Some(value) => Cluster(s, list, value)
          case None        => Cluster(s, list, Double.MaxValue)
        }
      }
      .toList
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
    val osmSubstations = gridElements.substations.values.toList

    val additionalSubstationCount = substationCount - osmSubstations.size

    val additionalSubstations: List[NodeInput] =
      if (additionalSubstationCount > 0) {
        val maxNr = gridElements.nodes.size
        val nodes = gridElements.nodes.values.toList

        // finds random nodes
        Range
          .Int(0, additionalSubstationCount, 1)
          .map { _ => nodes(ThreadLocalRandom.current().nextInt(0, maxNr)) }
          .toList
      } else {
        List.empty[NodeInput]
      }

    Clustering(
      connections,
      osmSubstations,
      additionalSubstations,
      gridElements.nodes.values.toList.diff(additionalSubstations)
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

  /** Checks if there are still improvements
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
  ): Boolean = totalDistance(current) <= totalDistance(old) * 0.95

  /** Calculates the total connection distance of a list of [[Cluster]]s.
    *
    * @param list
    *   of clusters
    * @return
    *   either the total distance or [[Double.MaxValue]]
    */
  private def totalDistance(list: List[Cluster]): Double = {
    list.map { l => l.distances }.reduceOption { (a, b) => a + b } match {
      case Some(value) => value
      case None        => Double.MaxValue
    }
  }

  final case class Cluster(
      substation: NodeInput,
      nodes: List[NodeInput],
      distances: Double
  )
}
