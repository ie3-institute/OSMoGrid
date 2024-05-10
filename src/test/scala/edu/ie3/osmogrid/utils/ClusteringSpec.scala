/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.utils

import edu.ie3.test.common.{ClusterTestData, UnitSpec}
import utils.{Clustering, Connections}
import utils.Clustering.Cluster

class ClusteringSpec extends UnitSpec with ClusterTestData {
  "A Clustering" should {
    val createClusters =
      PrivateMethod[List[Cluster]](Symbol("createClusters"))

    val calculateStep =
      PrivateMethod[Option[List[Cluster]]](Symbol("calculateStep"))

    "calculate the number of substations correctly" in {
      val getSubstationCount = PrivateMethod[Int](Symbol("getSubstationCount"))

      val loads = Set(l1_1, l1_2, l1_3, l1_4, l2_1, l2_2, l2_3, l2_4)
      val transformer2WTypeInput = trafo_10kV_to_lv

      val cases = Table(
        ("loadSimultaneousFactor", "expectedNumber"),
        (1d, 3),
        (0.5, 2),
        (0.3, 1)
      )

      forAll(cases) { (loadSimultaneousFactor, expectedNumber) =>
        Clustering invokePrivate getSubstationCount(
          loads,
          loadSimultaneousFactor,
          transformer2WTypeInput
        ) shouldBe expectedNumber
      }
    }

    "be set up correctly" in {
      val clustering = Clustering.setup(
        gridElements(List(p1_1, p2_1)),
        lines,
        trafo_10kV_to_lv,
        0.5
      )

      clustering.osmSubstations should contain allOf (p2_1, p1_1)
      clustering.additionalSubstations shouldBe Set.empty
      clustering.nodes should contain only (p2_4, p2_2, p1_3, p1_2, p2_3, p1_4)
    }

    "create clusters correctly" in {
      val elements = gridElements(List(p1_1, p2_1))
      val clustering = Clustering.setup(elements, lines, trafo_10kV_to_lv, 0.5)

      val clusters = clustering invokePrivate createClusters(
        elements.substations.values.toSet,
        elements.nodes.values.toSet
      )

      val map = clusters.map { c => c.substation -> c }.toMap
      map.keySet should contain allOf (p1_1, p2_1)
      map(p1_1).nodes should contain allOf (p1_2, p1_3, p1_4)
      map(p2_1).nodes should contain allOf (p2_2, p2_3, p2_4)
    }

    "check for improvements correctly" in {
      val old: List[Cluster] = List(Cluster(p1_1, Set(p1_2), 500))

      Clustering.isImprovement(
        old,
        List(Cluster(p1_1, Set(p1_2), 450))
      ) shouldBe true
      Clustering.isImprovement(
        old,
        List(Cluster(p1_1, Set(p1_2), 496)) // improvement is less than 1 %
      ) shouldBe false
    }

    "calculate the total distance of a list of clusters correctly" in {
      val totalDistance = PrivateMethod[Double](Symbol("totalDistance"))

      val clusters = List(
        Cluster(p1_1, Set(p1_2), 500),
        Cluster(p2_1, Set(p2_2), 200),
        Cluster(p1_3, Set(p1_4), 300)
      )

      Clustering invokePrivate totalDistance(clusters) shouldBe 1000
    }

    "calculate the next step correctly" in {
      // with one osm substation
      val elements = gridElements(List(p1_1))
      val connections = Connections(elements, lines.toSeq)

      // suboptimal additional substation found
      val additionalSubstation = Set(p1_2)

      val clustering = Clustering(
        connections,
        Set(p1_1),
        additionalSubstation,
        elements.nodes.values.toSet,
        8
      )

      val initialClusters = clustering invokePrivate createClusters(
        Set(p1_1, p1_2),
        clustering.nodes
      )
      val firstStep = clustering invokePrivate calculateStep(initialClusters)

      firstStep match {
        case Some(value) =>
          val map = value.map { c => c.substation -> c }.toMap
          map.keySet should contain allOf (p1_1, p2_3)
          map(p1_1).nodes should contain allOf (p1_2, p1_3, p1_4)
          map(p2_3).nodes should contain allOf (p2_1, p2_2, p2_4)
        case None =>
          fail("There is no next step!")
      }
    }

    "run the calculation correctly" in {
      val clustering = Clustering.setup(
        gridElements(List(p1_1, p2_1)),
        lines,
        trafo_10kV_to_lv,
        0.5
      )

      val clusters = clustering.run

      val map = clusters.map { c => c.substation -> c }.toMap
      map.keySet should contain allOf (p1_1, p2_1)
      map(p1_1).nodes should contain allOf (p1_2, p1_3, p1_4)
      map(p2_1).nodes should contain allOf (p2_2, p2_3, p2_4)
    }
  }
}
