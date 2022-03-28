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
        val inputResource = getClass.getResource("/Witten_Stockum.pbf")
        assert(inputResource != null)
        val resourcePath =
          Paths.get(inputResource.toURI).toAbsolutePath.toString

        val parsedCfg = ConfigFactory.parseMap(
          Map("osm.pbf.file" -> resourcePath).asJava
        )
        val config =
          OsmoGridConfig.Input(parsedCfg, "input", new $TsCfgValidator())

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
  }
}
