/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.test.common

import akka.actor.testkit.typed.scaladsl.LogCapturing
import org.scalatest.{
  GivenWhenThen,
  OptionValues,
  PrivateMethodTester,
  TryValues
}
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.wordspec.AnyWordSpecLike

trait UnitSpec
    extends AnyWordSpecLike
    with Matchers
    with PrivateMethodTester
    with TableDrivenPropertyChecks
    with OptionValues
    with TryValues
    with GivenWhenThen
    with LogCapturing
