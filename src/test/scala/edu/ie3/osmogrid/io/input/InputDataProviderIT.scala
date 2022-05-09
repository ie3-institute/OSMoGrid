/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.io.input

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import com.typesafe.config.ConfigFactory
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.cfg.OsmoGridConfig.$TsCfgValidator
import edu.ie3.osmogrid.exception.PbfReadFailedException
import edu.ie3.osmogrid.io.input.InputDataProvider
import edu.ie3.osmogrid.model.OsmoGridModel
import edu.ie3.osmogrid.model.OsmoGridModel.LvOsmoGridModel
import edu.ie3.osmogrid.model.SourceFilter.LvFilter
import edu.ie3.test.common.{InputDataCheck, UnitSpec}

import java.nio.file.Paths
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters.*
import scala.language.postfixOps

class InputDataProviderIT extends UnitSpec with InputDataCheck {
  private val testKit = ActorTestKit("InputDataProviderIT")

  "Reading input data from pbf file" when {
    "having proper input data" should {
      "provide full data set correctly" in {
        val config: OsmoGridConfig.Input = createConfig("/Witten_Stockum.pbf")

        val requestProbe = testKit.createTestProbe[InputDataProvider.Response]()
        val testActor = testKit.spawn(
          InputDataProvider(config)
        )

        val filter = LvFilter()

        testActor ! InputDataProvider.ReqOsm(requestProbe.ref, filter = filter)

        requestProbe
          .expectMessageType[InputDataProvider.Response](
            30 seconds
          ) match {
          case InputDataProvider.RepOsm(lvModel: LvOsmoGridModel) =>
            checkInputDataResult(lvModel)

          case InputDataProvider.OsmReadFailed(exception) =>
            fail(s"Failed with exception: $exception")

          case unexpected => fail(s"Unexpected message: $unexpected")
        }
      }
    }

    "having empty input data" should {
      "return with failure" in {
        val config: OsmoGridConfig.Input = createConfig("/Empty_Osm.pbf")

        val requestProbe = testKit.createTestProbe[InputDataProvider.Response]()
        val testActor = testKit.spawn(
          InputDataProvider(config)
        )

        testActor ! InputDataProvider.ReqOsm(
          requestProbe.ref,
          filter = LvFilter()
        )

        requestProbe
          .expectMessageType[InputDataProvider.Response](
            3 seconds
          ) match {
          case InputDataProvider.OsmReadFailed(exception) =>
            exception shouldBe PbfReadFailedException(
              "Input file is empty, stopping."
            )
          case InputDataProvider.RepOsm(lvModel: LvOsmoGridModel) =>
            fail(s"Provided OsmoGridModel $lvModel although it shouldn't")

          case unexpected => fail(s"Unexpected message: $unexpected")
        }
      }
    }

    "having corrupt input data" should {
      "return with failure" in {
        val config: OsmoGridConfig.Input = createConfig("/Corrupted_Osm.pbf")

        val requestProbe = testKit.createTestProbe[InputDataProvider.Response]()
        val testActor = testKit.spawn(
          InputDataProvider(config)
        )

        testActor ! InputDataProvider.ReqOsm(
          requestProbe.ref,
          filter = LvFilter()
        )

        requestProbe
          .expectMessageType[InputDataProvider.Response](
            3 seconds
          ) match {
          case InputDataProvider.OsmReadFailed(exception) =>
            exception.getMessage shouldBe "Reading input failed."
          case InputDataProvider.RepOsm(lvModel: LvOsmoGridModel) =>
            fail(s"Provided OsmoGridModel $lvModel although it shouldn't")

          case unexpected => fail(s"Unexpected message: $unexpected")
        }
      }
    }
  }

  private def createConfig(filePath: String) = {
    val inputResource = getClass.getResource(filePath)
    assert(inputResource != null)
    val resourcePath =
      Paths.get(inputResource.toURI).toAbsolutePath.toString

    val parsedCfg = ConfigFactory.parseMap(
      Map("osm.pbf.file" -> resourcePath).asJava
    )
    val config =
      OsmoGridConfig.Input(parsedCfg, "input", new $TsCfgValidator())
    config
  }
}
