/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.util

import edu.ie3.util.CollectionUtils.RichList
import edu.ie3.test.common.UnitSpec

class CollectionUtilsSpec extends UnitSpec {
  "Collection utilities" when {
    "dealing with lists" when {
      "rotating lists" should {
        val testList: List[Int] = List(1, 2, 3, 4, 5)

        "provide correct entries, if not rotating at all" in {
          testList.rotate(0) shouldBe List(1, 2, 3, 4, 5)
        }

        "provide correct entries, if rotating forward" in {
          testList.rotate(2) shouldBe List(3, 4, 5, 1, 2)
        }

        "provide correct entries, if rotating forward by more elements, than the list is long" in {
          testList.rotate(7) shouldBe List(3, 4, 5, 1, 2)
        }

        "provide correct entries, if rotating backwards" in {
          testList.rotate(-2) shouldBe List(4, 5, 1, 2, 3)
        }

        "provide correct entries, if rotating backwards by more elements, than the list is long" in {
          testList.rotate(-7) shouldBe List(4, 5, 1, 2, 3)
        }
      }
    }
  }
}
