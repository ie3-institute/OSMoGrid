/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.mv

import akka.actor.testkit.typed.scaladsl.{
  ActorTestKit,
  ScalaTestWithActorTestKit
}
import edu.ie3.test.common.{MvTestData, UnitSpec}

class VoronoiCoordinatorSpec
    extends ScalaTestWithActorTestKit
    with UnitSpec
    with MvTestData {
  private val asynchronousTestKit = ActorTestKit()

}
