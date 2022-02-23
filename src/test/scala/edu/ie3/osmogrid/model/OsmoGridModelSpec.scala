/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.model

import edu.ie3.osmogrid.model.OsmoGridModel.LvOsmoGridModel
import edu.ie3.osmogrid.model.PbfFilter.{Filter, LvFilter}
import edu.ie3.test.common.UnitSpec
import edu.ie3.util.osm.model.OsmContainer.ParOsmContainer
import edu.ie3.util.osm.model.OsmEntity.{Node, Relation, Way}
import edu.ie3.util.osm.model.OsmEntity.Relation.{
  RelationMember,
  RelationMemberType
}
import edu.ie3.util.osm.model.OsmEntity.Way.{ClosedWay, OpenWay}
import org.scalatest.GivenWhenThen

import scala.collection.parallel.CollectionConverters.*

class OsmoGridModelSpec extends UnitSpec with OsmTestData {

  "An LvOsmoGridModel" should {

    "be created from OsmModel correctly" in {

      Given("an exemplary osm model and filter")
      val testNodes = Seq(
        nodes.building1Node1,
        nodes.building1Node2,
        nodes.building1Node3,
        nodes.building1Node4,
        nodes.highwayNode1,
        nodes.highwayNode2,
        nodes.landuseNode1,
        nodes.landuseNode2,
        nodes.landuseNode3,
        nodes.landuseNode4,
        nodes.boundaryNode1,
        nodes.boundaryNode2,
        nodes.boundaryNode3,
        nodes.boundaryNode4,
        nodes.substation
      )

      val testWays = Seq(
        ways.building1,
        ways.highway,
        ways.landuse,
        ways.boundaryWay1,
        ways.boundaryWay2
      )

      val testRelations = Seq(
        relations.boundary
      )

      val osmContainer = ParOsmContainer(
        testNodes.par,
        testWays.par,
        testRelations.par
      )

      val filter = LvFilter(
        Filter("building", Set.empty),
        Filter("highway", Set.empty),
        Filter("landuse", Set.empty),
        PbfFilter.standardBoundaryFilter,
        PbfFilter.substationFilter
      )

      When("a LvOsmoGridModel is created")
      val result = LvOsmoGridModel(osmContainer, filter)

      Then("the LvOsmoGridModel should have appropriately filtered fields")
      result.buildings.size shouldBe 2
      result.buildings.seq should contain(ways.building1)
      result.buildings.seq should contain(nodes.substation)

      result.highways.size shouldBe 1
      result.highways.seq should contain(ways.highway)

      result.landuses.size shouldBe 1
      result.landuses.seq should contain(ways.landuse)

      result.boundaries.size shouldBe 1
      result.boundaries.seq should contain(relations.boundary)

      result.existingSubstations.size shouldBe 1
      result.existingSubstations.seq should contain(nodes.substation)
    }

    "be merged correctly" in {
      // TODO LvOsmoGridModel.+
    }

    "not be merged if filters are different" in {
      // TODO LvOsmoGridModel.+
    }

  }

  "OsmoGridModel" should {
    "filter OsmContainer properly with a single filter" in {

      Given("an exemplary osm model and filter")
      val testNodes = Seq(
        nodes.building1Node1,
        nodes.building1Node2,
        nodes.building1Node3,
        nodes.building1Node4,
        nodes.highwayNode1,
        nodes.highwayNode2
      )

      val testWays = Seq(
        ways.building1,
        ways.highway
      )

      val testRelations = Seq.empty[Relation]

      val osmContainer = ParOsmContainer(
        testNodes.par,
        testWays.par,
        testRelations.par
      )

      val filter = Filter("building", Set.empty)

      When("the OsmContainer is filtered")
      val result = OsmoGridModel.filter(osmContainer, filter)

      Then("a properly filtered set of entities should be returned")
      result.size shouldBe 1
      result.seq should contain(ways.building1)

    }

    "filter OsmContainer properly with multiple disjunctive filters" in {

      Given("an exemplary osm model and filter")
      val testNodes = Seq(
        nodes.building1Node1,
        nodes.building1Node2,
        nodes.building1Node3,
        nodes.building1Node4,
        nodes.highwayNode1,
        nodes.highwayNode2,
        nodes.landuseNode1,
        nodes.landuseNode2,
        nodes.landuseNode3,
        nodes.landuseNode4
      )

      val testWays = Seq(
        ways.building1,
        ways.highway,
        ways.landuse
      )

      val testRelations = Seq.empty[Relation]

      val osmContainer = ParOsmContainer(
        testNodes.par,
        testWays.par,
        testRelations.par
      )

      val filterOr =
        Set(Filter("building", Set.empty), Filter("landuse", Set.empty))

      When("the OsmContainer is filtered")
      val result = OsmoGridModel.filterOr(osmContainer, filterOr)

      Then("a properly filtered set of entities should be returned")
      result.size shouldBe 2
      result.seq should contain(ways.building1)
      result.seq should contain(ways.landuse)

    }
  }
}
