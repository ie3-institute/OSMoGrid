/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */
package edu.ie3.osmogrid.io.input

import org.apache.pekko.actor.typed.{ActorSystem, Behavior}
import edu.ie3.osmogrid.cfg.OsmoGridConfig.Input.Asset
import edu.ie3.osmogrid.cfg.OsmoGridConfig.Input.Asset.File
import edu.ie3.test.common.UnitSpec
import org.scalatestplus.mockito.MockitoSugar.mock

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success, Try}

class AssetSourceSpec extends UnitSpec {

  "a asset file source should read asset data successfully" in {
    val dir = getResourcePath("/lv_assets")
    val assetCfg = Asset(Some(File(dir, hierarchic = false, ",")))
    val system = ActorSystem(mock[Behavior[String]], "test_system")
    val assetSource = AssetSource(system.executionContext, assetCfg)
    Try(Await.result(assetSource.read(), 15.seconds)) match {
      case Success(assetInformation: AssetInformation) =>
        assetInformation.lineTypes.headOption
          .getOrElse(fail("No line types."))
          .getUuid
          .toString shouldBe "803c298b-61c6-412c-9b60-21cabc5bd945"
        assetInformation.transformerTypes.headOption
          .getOrElse(fail("No transformer types."))
          .getUuid
          .toString shouldBe "4984f493-d6e5-4201-a040-c10722b30362"
      case Failure(exc) =>
        fail("Could not read data.", exc)
    }

  }

}
