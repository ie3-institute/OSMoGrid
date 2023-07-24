/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.utils

import edu.ie3.datamodel.models.voltagelevels.VoltageLevel
import edu.ie3.test.common.UnitSpec
import tech.units.indriya.quantity.Quantities
import tech.units.indriya.unit.Units.VOLT
import utils.VoltageLevelUtils

class VoltageLevelUtilsSpec extends UnitSpec {

  "The VoltageLevelUtils" should {
    val lv400 = new VoltageLevel("lv", Quantities.getQuantity(400, VOLT))
    val lv230 = new VoltageLevel("lv", Quantities.getQuantity(230, VOLT))
    val lv1000 = new VoltageLevel("lv", Quantities.getQuantity(1000, VOLT))

    "should convert multiple given input to the correct voltage level" in {
      val cases = Table(
        ("id", "voltages", "default", "expectedVoltLvl"),
        ("lv", Some(List(0.23, 1.0)), 0.4, List(lv230, lv1000)),
        ("lv", None, 0.4, List(lv400))
      )

      forAll(cases) { (id, voltages, default, expectedVoltLvl) =>
        val voltLvl: List[VoltageLevel] =
          VoltageLevelUtils.toVoltLvl(id, voltages, default)

        voltLvl.size shouldBe expectedVoltLvl.size
        voltLvl.foreach(lvl => expectedVoltLvl.contains(lvl))
      }
    }

    "should convert a given input to the correct voltage level" in {
      val cases = Table(
        ("id", "voltage", "expectedVoltLvl"),
        ("lv", 0.4, lv400),
        ("lv", 1.0, lv1000)
      )

      forAll(cases) { (id, voltage, expectedVoltLvl) =>
        val voltLvl: VoltageLevel = VoltageLevelUtils.toVoltLvl(id, voltage)

        voltLvl.getId shouldBe expectedVoltLvl.getId
        voltLvl.getNominalVoltage shouldBe expectedVoltLvl.getNominalVoltage
      }
    }
  }
}
