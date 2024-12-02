/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.utils

import edu.ie3.datamodel.models.StandardUnits
import edu.ie3.datamodel.models.voltagelevels.VoltageLevel
import edu.ie3.osmogrid.cfg.OsmoGridConfig.Voltage.{Hv, Lv, Mv}
import edu.ie3.test.common.UnitSpec
import tech.units.indriya.quantity.Quantities
import utils.VoltageUtils
import utils.VoltageUtils.parse

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

    "parse a lv config correctly" in {
      val cases = Table(
        ("cfg", "expected"),
        (
          Lv(0.4, "lv", None),
          List(
            Quantities.getQuantity(0.4, StandardUnits.RATED_VOLTAGE_MAGNITUDE)
          ),
        ),
        (
          Lv(0.4, "lv", Some(List(1d, 0.32, 0.7))),
          List(
            Quantities.getQuantity(1d, StandardUnits.RATED_VOLTAGE_MAGNITUDE),
            Quantities.getQuantity(0.32, StandardUnits.RATED_VOLTAGE_MAGNITUDE),
            Quantities.getQuantity(0.7, StandardUnits.RATED_VOLTAGE_MAGNITUDE),
          ),
        ),
      )

      forAll(cases) { (cfg, expected) =>
        parse(cfg) shouldBe expected
      }
    }

    "parse a mv config correctly" in {
      val cases = Table(
        ("cfg", "expected"),
        (
          Mv(10.0, "mv", None),
          List(
            Quantities.getQuantity(10d, StandardUnits.RATED_VOLTAGE_MAGNITUDE)
          ),
        ),
        (
          Mv(10.0, "mv", Some(List(10d, 20d, 30d))),
          List(
            Quantities.getQuantity(10d, StandardUnits.RATED_VOLTAGE_MAGNITUDE),
            Quantities.getQuantity(20d, StandardUnits.RATED_VOLTAGE_MAGNITUDE),
            Quantities.getQuantity(30d, StandardUnits.RATED_VOLTAGE_MAGNITUDE),
          ),
        ),
      )

      forAll(cases) { (cfg, expected) =>
        parse(cfg) shouldBe expected
      }
    }

    "parse a hv config correctly" in {
      val cases = Table(
        ("cfg", "expected"),
        (
          Hv(110.0, "hvh", None),
          List(
            Quantities.getQuantity(110d, StandardUnits.RATED_VOLTAGE_MAGNITUDE)
          ),
        ),
        (
          Hv(110.0, "hv", Some(List(100d, 120d, 60d))),
          List(
            Quantities.getQuantity(100d, StandardUnits.RATED_VOLTAGE_MAGNITUDE),
            Quantities.getQuantity(120d, StandardUnits.RATED_VOLTAGE_MAGNITUDE),
            Quantities.getQuantity(60d, StandardUnits.RATED_VOLTAGE_MAGNITUDE),
          ),
        ),
      )

      forAll(cases) { (cfg, expected) =>
        parse(cfg) shouldBe expected
      }
    }

    "convert multiple given input to the correct voltage level" in {
      val cases = Table(
        ("id", "voltages", "default", "expectedVoltLvl"),
        ("lv", Some(List(0.23, 1.0)), 0.4, List(lv230, lv1000)),
        ("lv", None, 0.4, List(lv400)),
      )

      forAll(cases) { (id, voltages, default, expectedVoltLvl) =>
        val voltLvl: List[VoltageLevel] =
          VoltageUtils.toVoltLvl(id, voltages, default)

        voltLvl.size shouldBe expectedVoltLvl.size
        voltLvl.foreach(lvl => expectedVoltLvl.contains(lvl))
      }
    }

    "convert multiple given values into correct quantities" in {
      val cases = Table(
        ("voltages", "default", "expectedVoltLvl"),
        (Some(List(0.23, 1.0)), 0.4, List(q230, q1000)),
        (None, 0.4, List(q400)),
      )

      forAll(cases) { (voltages, default, expectedVoltLvl) =>
        val quantities = VoltageUtils.toQuantities(voltages, default)

        quantities.size shouldBe expectedVoltLvl.size
        quantities.foreach(q => expectedVoltLvl.contains(q))
      }
    }

    "convert a given input to the correct voltage level" in {
      val cases = Table(
        ("voltage", "expectedVoltLvl"),
        (0.4, q400),
        (1.0, q1000),
      )

      forAll(cases) { (voltage, expectedVoltLvl) =>
        val quantity = VoltageUtils.toQuantity(voltage)

        quantity shouldBe expectedVoltLvl
      }
    }
  }
}
