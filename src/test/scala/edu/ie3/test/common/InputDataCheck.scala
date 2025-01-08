/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */
package edu.ie3.test.common

import edu.ie3.osmogrid.model.OsmoGridModel.LvOsmoGridModel
import org.scalatest.matchers.should.Matchers

trait InputDataCheck extends Matchers {

  /** Check results of Witten_Stockum.pbf with standard filter
    * @param model
    *   The LvOsmoGridModel to check
    */
  protected def checkInputDataResult(model: LvOsmoGridModel): Unit = {
    model.landuses should have length 38
    model.landuses.map(_.allSubEntities.size).sum shouldBe 705

    model.highways should have length 1424
    model.highways.map(_.allSubEntities.size).sum shouldBe 3947

    model.buildings should have length 2512
    model.buildings.map(_.allSubEntities.size).sum shouldBe 16367

    model.boundaries should have length 9
    model.boundaries.map(_.allSubEntities.size).sum shouldBe 0

    model.existingSubstations should have length 10
    model.existingSubstations.map(_.allSubEntities.size).sum shouldBe 40
  }
}
