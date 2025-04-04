/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.io.input

import org.apache.pekko.actor.testkit.typed.scaladsl.ActorTestKit
import com.typesafe.config.ConfigFactory
import edu.ie3.datamodel.exceptions.SourceException
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.cfg.OsmoGridConfig.$TsCfgValidator
import edu.ie3.osmogrid.exception.{InputDataException, PbfReadFailedException}
import edu.ie3.osmogrid.model.OsmoGridModel.LvOsmoGridModel
import edu.ie3.osmogrid.model.SourceFilter.LvFilter
import edu.ie3.test.common.{InputDataCheck, UnitSpec}

import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters._
import scala.language.postfixOps

class InputDataProviderIT extends UnitSpec with InputDataCheck {
  private val testKit = ActorTestKit("InputDataProviderIT")

  "Reading input data from pbf file" when {
    "having proper input data" should {
      "provide full data set correctly" in {
        val config: OsmoGridConfig.Input =
          createConfig("/Witten_Stockum.pbf", "/lv_assets")

        val requestProbe = testKit.createTestProbe[InputResponse]()
        val testActor = testKit.spawn(
          InputDataProvider(config)
        )

        val filter = LvFilter()

        testActor ! ReqOsm(requestProbe.ref, filter = filter)

        requestProbe
          .expectMessageType[InputResponse](
            30 seconds
          ) match {
          case RepOsm(lvModel: LvOsmoGridModel) =>
            checkInputDataResult(lvModel)

          case OsmReadFailed(exception) =>
            fail(s"Failed with exception: $exception")

          case unexpected => fail(s"Unexpected message: $unexpected")
        }
      }

      "provide asset information correctly" in {
        val config: OsmoGridConfig.Input =
          createConfig("/Witten_Stockum.pbf", "/lv_assets")

        val requestProbe = testKit.createTestProbe[InputResponse]()
        val testActor = testKit.spawn(
          InputDataProvider(config)
        )

        testActor ! ReqAssetTypes(requestProbe.ref)

        requestProbe
          .expectMessageType[RepAssetTypes](
            30 seconds
          ) match {
          case RepAssetTypes(
                assetInformation: AssetInformation
              ) =>
            assetInformation.lineTypes.length shouldBe 1
            assetInformation.transformerTypes.length shouldBe 1
        }
      }
    }

    "having empty input data" should {
      "return with failure for missing osm data" in {
        val config: OsmoGridConfig.Input =
          createConfig("/Empty_Osm.pbf", "/lv_assets")

        val requestProbe = testKit.createTestProbe[InputResponse]()
        val testActor = testKit.spawn(
          InputDataProvider(config)
        )

        testActor ! ReqOsm(
          requestProbe.ref,
          filter = LvFilter(),
        )

        requestProbe
          .expectMessageType[InputResponse](
            30 seconds
          ) match {
          case OsmReadFailed(exception) =>
            exception shouldBe PbfReadFailedException(
              "Reading failed due to: org.openstreetmap.osmosis.core.OsmosisRuntimeException: PBF stream ended before a header could be found."
            )
          case RepOsm(lvModel: LvOsmoGridModel) =>
            fail(s"Provided OsmoGridModel $lvModel although it shouldn't")

          case unexpected => fail(s"Unexpected message: $unexpected")
        }
      }
      "return with failure for missing asset data" in {
        val resourceName = "/"
        val config: OsmoGridConfig.Input =
          createConfig("/Witten_Stockum.pbf", resourceName)
        val assetDir = getResourcePath(resourceName)

        val requestProbe = testKit.createTestProbe[InputResponse]()
        val testActor = testKit.spawn(
          InputDataProvider(config)
        )

        testActor ! ReqAssetTypes(
          requestProbe.ref
        )
        requestProbe
          .expectMessageType[InputResponse](
            3 seconds
          ) match {
          case AssetReadFailed(exception) =>
            exception shouldBe InputDataException(
              s"There are no or corrupt transformer types at: $assetDir"
            )
          case RepAssetTypes(
                assetInformation: AssetInformation
              ) =>
            fail(
              s"Provided asset information $assetInformation although it shouldn't"
            )

          case unexpected => fail(s"Unexpected message: $unexpected")
        }
      }
    }

    "having corrupt input data" should {
      "return with failure for corrupt osm data" in {
        val config: OsmoGridConfig.Input =
          createConfig("/Corrupted_Osm.pbf", "/lv_assets")

        val requestProbe = testKit.createTestProbe[InputResponse]()
        val testActor = testKit.spawn(
          InputDataProvider(config)
        )

        testActor ! ReqOsm(
          requestProbe.ref,
          filter = LvFilter(),
        )

        requestProbe
          .expectMessageType[InputResponse](
            30 seconds
          ) match {
          case OsmReadFailed(exception) =>
            exception.getMessage shouldBe "Reading failed due to: org.openstreetmap.osmosis.core.OsmosisRuntimeException: Unable to get next blob from PBF stream."
          case RepOsm(lvModel: LvOsmoGridModel) =>
            fail(s"Provided OsmoGridModel $lvModel although it shouldn't")

          case unexpected => fail(s"Unexpected message: $unexpected")
        }
      }
      "return with failure for corrupt assets" in {
        val resourceName = "/corrupted_lv_assets"
        val config: OsmoGridConfig.Input =
          createConfig("/Witten_Stockum.pbf", resourceName)
        val assetDir = getResourcePath(resourceName)

        val requestProbe = testKit.createTestProbe[InputResponse]()
        val testActor = testKit.spawn(
          InputDataProvider(config)
        )

        testActor ! ReqAssetTypes(
          requestProbe.ref
        )
        requestProbe
          .expectMessageType[InputResponse](
            3 seconds
          ) match {
          case AssetReadFailed(exception) =>
            exception.getClass shouldBe classOf[SourceException]
            exception.getMessage shouldBe s"edu.ie3.datamodel.exceptions.FailureException: 1 exception(s) occurred within \"Transformer2WTypeInput\" data, one is: " +
              s"edu.ie3.datamodel.exceptions.FactoryException: An error occurred when creating instance of Transformer2WTypeInput.class."
          case RepAssetTypes(
                assetInformation: AssetInformation
              ) =>
            fail(
              s"Provided asset information $assetInformation although it shouldn't"
            )

          case unexpected => fail(s"Unexpected message: $unexpected")

        }
      }
    }
  }

  private def createConfig(
      pbfFilePath: String,
      assetDirPath: String,
      assetSep: String = ",",
      assetHierarchic: Boolean = false,
  ) = {
    val parsedCfg = ConfigFactory.parseMap(
      Map(
        "osm.pbf.file" -> getResourcePath(pbfFilePath),
        "asset.file.directory" -> getResourcePath(assetDirPath),
        "asset.file.separator" -> assetSep,
        "asset.file.hierarchic" -> assetHierarchic,
      ).asJava
    )
    val config =
      OsmoGridConfig.Input(parsedCfg, "input", new $TsCfgValidator())
    config
  }
}
