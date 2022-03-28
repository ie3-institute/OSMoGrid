/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.cfg

import edu.ie3.osmogrid.exception.IllegalConfigException
import edu.ie3.test.common.UnitSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.util.{Failure, Success}

class ConfigFailFastSpec extends UnitSpec {
  "Checking a given config for validity" when {
    "having malicious input definition" should {
      "fail on missing osm input definition" in {
        OsmoGridConfigFactory.parseWithoutFallback {
          """input.asset.file.directory = "asset_input_dir"
            |input.asset.file.hierarchic = false
            |output.file.directory = "output_file_path"
            |generation.lv.distinctHouseConnections = true""".stripMargin
        } match {
          case Success(cfg) =>
            ConfigFailFast.check(cfg) match {
              case Failure(exception) =>
                exception.getMessage shouldBe "You have to provide at least one input data type for open street map information!"
              case Success(_) =>
                fail("Config check succeeded, but was meant to fail.")
            }
          case Failure(exception) =>
            fail(s"Config generation failed with an exception: '$exception'")
        }
      }

      "fail on empty osm input definition" in {
        OsmoGridConfigFactory.parseWithoutFallback {
          """input.osm.pbf.file = ""
            |input.asset.file.directory = "asset_input_dir"
            |input.asset.file.hierarchic = false
            |output.file.directory = "output_file_path"
            |generation.lv.distinctHouseConnections = true""".stripMargin
        } match {
          case Success(cfg) =>
            ConfigFailFast.check(cfg) match {
              case Failure(exception) =>
                exception.getMessage shouldBe "Pbf file may be set!"
              case Success(_) =>
                fail("Config check succeeded, but was meant to fail.")
            }
          case Failure(exception) =>
            fail(s"Config generation failed with an exception: '$exception'")
        }
      }

      "fail on missing asset input definition" in {
        OsmoGridConfigFactory.parseWithoutFallback {
          """input.osm.pbf.file = "input_file_path"
            |output.file.directory = "output_file_path"
            |generation.lv.distinctHouseConnections = true""".stripMargin
        } match {
          case Success(cfg) =>
            ConfigFailFast.check(cfg) match {
              case Failure(exception) =>
                exception.getMessage shouldBe "You have to provide at least one input data type for asset information!"
              case Success(_) =>
                fail("Config check succeeded, but was meant to fail.")
            }
          case Failure(exception) =>
            fail(s"Config generation failed with an exception: '$exception'")
        }
      }

      "fail on empty asset directory definition" in {
        OsmoGridConfigFactory.parseWithoutFallback {
          """input.osm.pbf.file = "input_file_path"
            |input.asset.file.directory = ""
            |input.asset.file.hierarchic = false
            |output.file.directory = "output_file_path"
            |generation.lv.distinctHouseConnections = true""".stripMargin
        } match {
          case Success(cfg) =>
            ConfigFailFast.check(cfg) match {
              case Failure(exception) =>
                exception.getMessage shouldBe "Asset input directory may be set!"
              case Success(_) =>
                fail("Config check succeeded, but was meant to fail.")
            }
          case Failure(exception) =>
            fail(s"Config generation failed with an exception: '$exception'")
        }
      }
    }

    "having missing generation configs" should {
      "fail" in {
        OsmoGridConfigFactory.parseWithoutFallback {
          """input.osm.pbf.file = "input_file_path"
            |input.asset.file.directory = "asset_input_dir"
            |input.asset.file.hierarchic = false
            |output.csv.directory = "output_file_path"""".stripMargin
        } match {
          case Success(cfg) =>
            ConfigFailFast.check(cfg) match {
              case Failure(exception) =>
                exception.getMessage shouldBe "At least one voltage level generation config has to be defined."
              case Success(_) =>
                fail("Config check succeeded, but was meant to fail.")
            }
          case Failure(exception) =>
            fail(s"Config generation failed with an exception: '$exception'")
        }
      }
    }

    "having a valid config" should {
      "pass" in {
        OsmoGridConfigFactory.parseWithoutFallback {
          """input.osm.pbf.file = "input_file_path"
          |input.asset.file.directory = "asset_input_dir"
          |input.asset.file.hierarchic = false
          |output.csv.directory = "output_file_path"
          |generation.lv.distinctHouseConnections = true""".stripMargin
        } match {
          case Success(cfg) => ConfigFailFast.check(cfg) shouldBe Success(cfg)
          case Failure(exception) =>
            fail(s"Config generation failed with an exception: '$exception'")
        }
      }
    }
  }
}
