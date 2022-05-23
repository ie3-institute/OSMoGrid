/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.model

import edu.ie3.osmogrid.model.OsmoGridModel.{EnhancedOsmEntity, LvOsmoGridModel}
import edu.ie3.osmogrid.model.SourceFilter.{Filter, LvFilter}
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
import scala.collection.parallel.ParSeq

class OsmoGridModelSpec extends UnitSpec with OsmTestData {

  "An LvOsmoGridModel" should {

    "be created from OsmModel correctly" in {

      Given("an exemplary osm model and filter")
      val testNodes = Seq(
        nodes.building1Node1,
        nodes.building1Node2,
        nodes.building1Node3,
        nodes.building1Node4,
        nodes.highway1Node1,
        nodes.highway1Node2,
        nodes.landuse1Node1,
        nodes.landuse1Node2,
        nodes.landuse1Node3,
        nodes.landuse1Node4,
        nodes.boundaryNode1,
        nodes.boundaryNode2,
        nodes.boundaryNode3,
        nodes.boundaryNode4,
        nodes.substation
      )

      val testWays = Seq(
        ways.building1,
        ways.highway1,
        ways.landuse1,
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
        SourceFilter.standardBoundaryFilter,
        SourceFilter.substationFilter
      )

      When("a LvOsmoGridModel is created")
      val result = LvOsmoGridModel(osmContainer, filter)

      Then("the LvOsmoGridModel should have appropriately filtered fields")
      result.buildings.size shouldBe 2
      result.buildings.seq should contain(
        EnhancedOsmEntity(
          ways.building1,
          Iterable(
            nodes.building1Node1,
            nodes.building1Node2,
            nodes.building1Node3,
            nodes.building1Node4
          )
        )
      )
      result.buildings.seq should contain(
        EnhancedOsmEntity(nodes.substation, Iterable.empty)
      )

      result.highways.size shouldBe 1
      result.highways.seq should contain(
        EnhancedOsmEntity(
          ways.highway1,
          Iterable(
            nodes.highway1Node1,
            nodes.highway1Node2
          )
        )
      )

      result.landuses.size shouldBe 1
      result.landuses.seq should contain(
        EnhancedOsmEntity(
          ways.landuse1,
          Iterable(
            nodes.landuse1Node1,
            nodes.landuse1Node2,
            nodes.landuse1Node3,
            nodes.landuse1Node4
          )
        )
      )

      result.boundaries.size shouldBe 1
      result.boundaries.seq should contain(
        EnhancedOsmEntity(
          relations.boundary,
          Iterable(
            ways.boundaryWay1,
            ways.boundaryWay2,
            nodes.boundaryNode1,
            nodes.boundaryNode2,
            nodes.boundaryNode3,
            nodes.boundaryNode4
          )
        )
      )

      result.existingSubstations.size shouldBe 1
      result.existingSubstations.seq should contain(
        EnhancedOsmEntity(nodes.substation, Iterable.empty)
      )

    }

    "be merged correctly" in {
      Given("two exemplary LvOsmoGridModels")
      val highwayEnhanced = EnhancedOsmEntity(
        ways.highway1,
        Iterable(
          nodes.highway1Node1,
          nodes.highway1Node2
        )
      )

      val boundaryEnhanced = EnhancedOsmEntity(
        relations.boundary,
        Iterable(
          ways.boundaryWay1,
          ways.boundaryWay2,
          nodes.boundaryNode1,
          nodes.boundaryNode2,
          nodes.boundaryNode3,
          nodes.boundaryNode4
        )
      )

      val filter = LvFilter(
        Filter("building", Set.empty),
        Filter("highway", Set.empty),
        Filter("landuse", Set.empty),
        SourceFilter.standardBoundaryFilter,
        SourceFilter.substationFilter
      )

      val osmoGridModel1 = LvOsmoGridModel(
        ParSeq.empty,
        ParSeq(highwayEnhanced),
        ParSeq.empty,
        ParSeq.empty,
        ParSeq.empty,
        filter
      )

      val osmoGridModel2 = LvOsmoGridModel(
        ParSeq.empty,
        ParSeq.empty,
        ParSeq.empty,
        ParSeq(boundaryEnhanced),
        ParSeq.empty,
        filter
      )

      When("the models are merged")
      val result = osmoGridModel1 + osmoGridModel2

      Then("the result is correct")
      result shouldBe Some(
        LvOsmoGridModel(
          ParSeq.empty,
          ParSeq(highwayEnhanced),
          ParSeq.empty,
          ParSeq(boundaryEnhanced),
          ParSeq.empty,
          filter
        )
      )
    }

    "not be merged if filters are different" in {
      Given("two exemplary LvOsmoGridModels")
      val highwayEnhanced = EnhancedOsmEntity(
        ways.highway1,
        Iterable(
          nodes.highway1Node1,
          nodes.highway1Node2
        )
      )

      val boundaryEnhanced = EnhancedOsmEntity(
        relations.boundary,
        Iterable(
          ways.boundaryWay1,
          ways.boundaryWay2,
          nodes.boundaryNode1,
          nodes.boundaryNode2,
          nodes.boundaryNode3,
          nodes.boundaryNode4
        )
      )

      val filter1 = LvFilter(
        Filter("building", Set("test")),
        Filter("highway", Set.empty),
        Filter("landuse", Set.empty),
        SourceFilter.standardBoundaryFilter,
        SourceFilter.substationFilter
      )

      val filter2 = LvFilter(
        Filter("building", Set.empty),
        Filter("highway", Set.empty),
        Filter("landuse", Set.empty),
        SourceFilter.standardBoundaryFilter,
        SourceFilter.substationFilter
      )

      val osmoGridModel1 = LvOsmoGridModel(
        ParSeq.empty,
        ParSeq(highwayEnhanced),
        ParSeq.empty,
        ParSeq.empty,
        ParSeq.empty,
        filter1
      )

      val osmoGridModel2 = LvOsmoGridModel(
        ParSeq.empty,
        ParSeq.empty,
        ParSeq.empty,
        ParSeq(boundaryEnhanced),
        ParSeq.empty,
        filter2
      )

      When("the models are merged")
      val result = osmoGridModel1 + osmoGridModel2

      Then("None should be returned")
      result shouldBe None
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
        nodes.highway1Node1,
        nodes.highway1Node2
      )

      val testWays = Seq(
        ways.building1,
        ways.highway1
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
      result.seq should contain(
        EnhancedOsmEntity(
          ways.building1,
          Iterable(
            nodes.building1Node1,
            nodes.building1Node2,
            nodes.building1Node3,
            nodes.building1Node4
          )
        )
      )

    }

    "filter OsmContainer properly with multiple disjunctive filters" in {

      Given("an exemplary osm model and filter")
      val testNodes = Seq(
        nodes.building1Node1,
        nodes.building1Node2,
        nodes.building1Node3,
        nodes.building1Node4,
        nodes.highway1Node1,
        nodes.highway1Node2,
        nodes.landuse1Node1,
        nodes.landuse1Node2,
        nodes.landuse1Node3,
        nodes.landuse1Node4
      )

      val testWays = Seq(
        ways.building1,
        ways.highway1,
        ways.landuse1
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
      result.seq should contain(
        EnhancedOsmEntity(
          ways.building1,
          Iterable(
            nodes.building1Node1,
            nodes.building1Node2,
            nodes.building1Node3,
            nodes.building1Node4
          )
        )
      )
      result.seq should contain(
        EnhancedOsmEntity(
          ways.landuse1,
          Iterable(
            nodes.landuse1Node1,
            nodes.landuse1Node2,
            nodes.landuse1Node3,
            nodes.landuse1Node4
          )
        )
      )

    }
  }
}
