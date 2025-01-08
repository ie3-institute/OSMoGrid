/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */
package edu.ie3.osmogrid.utils

import edu.ie3.test.common.UnitSpec
import edu.ie3.util.osm.model.OsmEntity.Node
import org.locationtech.jts.geom.LineSegment
import utils.GraphUtils

class GraphUtilsSpec extends UnitSpec {
  "The GraphUtils" should {

    "create a line segments correctly" in {
      val cases = Table(
        ("nodeA", "nodeB", "segment"),
        (
          Node(1L, 0d, 1d, Map.empty, None),
          Node(2L, 1d, 0d, Map.empty, None),
          new LineSegment(1d, 0d, 0d, 1d)
        ),
        (
          Node(1L, 1d, 0d, Map.empty, None),
          Node(2L, 5d, 7d, Map.empty, None),
          new LineSegment(0d, 1d, 7d, 5d)
        )
      )

      forAll(cases) { (nodeA, nodeB, segment) =>
        GraphUtils.getLineSegmentBetweenNodes(nodeA, nodeB) shouldBe segment
      }
    }

    "check the intersection of two line segments correctly" in {
      val cases = Table(
        ("lineA", "lineB", "result"),
        (
          new LineSegment(0d, 0d, 1d, 0d),
          new LineSegment(0d, 0d, 0d, 1d),
          false
        ),
        (
          new LineSegment(0d, 0d, 1d, 0d),
          new LineSegment(0d, 1d, 1d, 1d),
          false
        ),
        (
          new LineSegment(0d, 0d, 1d, 1d),
          new LineSegment(0d, 1d, 1d, 0d),
          true
        ),
        (new LineSegment(0d, 0d, 1d, 0d), new LineSegment(0d, 0d, 2d, 0d), true)
      )

      forAll(cases) { (lineA, lineB, result) =>
        GraphUtils.hasIntersection(lineA, lineB) shouldBe result
      }
    }

  }
}
