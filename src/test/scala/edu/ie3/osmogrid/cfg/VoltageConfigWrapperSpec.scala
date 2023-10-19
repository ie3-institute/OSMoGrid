/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.cfg

import edu.ie3.osmogrid.cfg.OsmoGridConfig.Voltage
import edu.ie3.osmogrid.cfg.OsmoGridConfig.Voltage.{Hv, Lv, Mv}
import edu.ie3.test.common.UnitSpec

class VoltageConfigWrapperSpec extends UnitSpec {
  "A voltage config wrapper" should {
    "have default values if not overwritten" in {
      val cfg = VoltageConfigWrapper.cfg

      cfg.lv shouldBe Lv(0.4, "lv", None)
      cfg.mv shouldBe Mv(10.0, "mv", None)
      cfg.hv shouldBe Hv(110.0, "hv", None)
    }

    "can be overwritten" in {
      VoltageConfigWrapper.cfg = Voltage(
        Hv(90.0, "hv", None),
        Lv(0.23, "lv", None),
        Mv(5.0, "mv", None)
      )

      val cfg = VoltageConfigWrapper.cfg

      cfg.lv shouldBe Lv(0.23, "lv", None)
      cfg.mv shouldBe Mv(5.0, "mv", None)
      cfg.hv shouldBe Hv(90.0, "hv", None)
    }
  }
}
