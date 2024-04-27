/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.io.input

import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.cfg.OsmoGridConfig.Input.{Asset, Osm}
import edu.ie3.osmogrid.model.OsmoGridModel.LvOsmoGridModel
import edu.ie3.osmogrid.model.SourceFilter.LvFilter
import edu.ie3.test.common.{InputDataCheck, UnitSpec}
import org.apache.pekko.actor.testkit.typed.scaladsl.ActorTestKit

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

class OsmSourceIT extends UnitSpec with InputDataCheck {
  private val testKit = ActorTestKit("OsmSourceIT")

  "Reading input data from pbf file" when {
    "having proper input data" should {
      "provide full data set correctly" in {
        val resourcePath = getResourcePath("/Witten_Stockum.pbf")
        val requestProbe = testKit.createTestProbe[InputResponse]()

        val testActor = testKit.spawn(
          InputDataProvider(
            OsmoGridConfig.Input(
              Asset(Some(Asset.File("", hierarchic = false, ","))),
              Osm(Some(Osm.Pbf(resourcePath)))
            )
          )
        )

        testActor ! ReqOsm(requestProbe.ref, LvFilter())

        requestProbe
          .expectMessageType[RepOsm](
            30 seconds
          ) match {
          case RepOsm(lvModel: LvOsmoGridModel) =>
            checkInputDataResult(lvModel)

          case unsupported =>
            fail(s"Received the wrong response: $unsupported!")
        }
      }
    }
  }

  override protected def afterAll(): Unit = {
    testKit.shutdownTestKit()
  }
}
