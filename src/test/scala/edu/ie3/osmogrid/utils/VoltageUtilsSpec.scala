/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.utils

import edu.ie3.datamodel.models.StandardUnits
import edu.ie3.datamodel.models.voltagelevels.VoltageLevel
import edu.ie3.test.common.UnitSpec
import tech.units.indriya.quantity.Quantities
import tech.units.indriya.unit.Units.VOLT
import utils.VoltageUtils

class VoltageUtilsSpec extends UnitSpec {

  "The VoltageUtils" should {
    val q400 =
      Quantities.getQuantity(0.4, StandardUnits.RATED_VOLTAGE_MAGNITUDE)
    val q230 =
      Quantities.getQuantity(0.23, StandardUnits.RATED_VOLTAGE_MAGNITUDE)
    val q1000 =
      Quantities.getQuantity(1.0, StandardUnits.RATED_VOLTAGE_MAGNITUDE)

    val lv400 = new VoltageLevel("lv", q400)
    val lv230 = new VoltageLevel("lv", q230)
    val lv1000 = new VoltageLevel("lv", q1000)

    "should convert multiple given input to the correct voltage level" in {
      val cases = Table(
        ("id", "voltages", "default", "expectedVoltLvl"),
        ("lv", Some(List(0.23, 1.0)), 0.4, List(lv230, lv1000)),
        ("lv", None, 0.4, List(lv400))
      )

      forAll(cases) { (id, voltages, default, expectedVoltLvl) =>
        val voltLvl: List[VoltageLevel] =
          VoltageUtils.toVoltLvl(id, voltages, default)

        voltLvl.size shouldBe expectedVoltLvl.size
        voltLvl.foreach(lvl => expectedVoltLvl.contains(lvl))
      }
    }

    "should convert multiple given values into correct quantities" in {
      val cases = Table(
        ("voltages", "default", "expectedVoltLvl"),
        (Some(List(0.23, 1.0)), 0.4, List(q230, q1000)),
        (None, 0.4, List(q400))
      )

      forAll(cases) { (voltages, default, expectedVoltLvl) =>
        val quantities = VoltageUtils.toQuantities(voltages, default)

        quantities.size shouldBe expectedVoltLvl.size
        quantities.foreach(q => expectedVoltLvl.contains(q))
      }
    }

    "should convert a given input to the correct voltage level" in {
      val cases = Table(
        ("voltage", "expectedVoltLvl"),
        (0.4, q400),
        (1.0, q1000)
      )

      forAll(cases) { (voltage, expectedVoltLvl) =>
        val quantity = VoltageUtils.toQuantity(voltage)

        quantity shouldBe expectedVoltLvl
      }
    }
  }
}
