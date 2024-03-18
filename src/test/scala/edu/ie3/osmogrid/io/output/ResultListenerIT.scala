/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.io.output

import com.typesafe.config.ConfigFactory
import edu.ie3.datamodel.io.source.csv.CsvJointGridContainerSource
import edu.ie3.osmogrid.cfg.ConfigFailFastSpec.viableConfigurationString
import edu.ie3.osmogrid.cfg.OsmoGridConfig.$TsCfgValidator
import edu.ie3.osmogrid.cfg.{OsmoGridConfig, OsmoGridConfigFactory}
import edu.ie3.osmogrid.exception.IllegalConfigException
import edu.ie3.osmogrid.io.output.PersistenceListenerEvent.{
  InitComplete,
  InitFailed,
  ResultHandlingSucceeded
}
import edu.ie3.test.common.{ThreeWindingTestData, UnitSpec}
import edu.ie3.util.io.FileIOUtils
import org.apache.pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.apache.pekko.actor.typed.scaladsl.Behaviors

import java.nio.file.{Files, Path}
import java.util.UUID
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import scala.jdk.CollectionConverters._

class ResultListenerIT
    extends ScalaTestWithActorTestKit
    with UnitSpec
    with ThreeWindingTestData {

  private val testProbe = testKit.createTestProbe[ResultListenerProtocol]()
  private val runId = UUID.randomUUID()

  private val validConfig = OsmoGridConfigFactory.defaultTestConfig.output
  private val maliciousConfig = OsmoGridConfigFactory
    .parseWithoutFallback(
      viableConfigurationString.replaceAll("(?m)^.*output.csv.*$", "")
    )
    .getOrElse(fail("Unable to parse malicious config"))
    .output

  val tmpDir: Path = Files.createTempDirectory("tmpDir")

  override protected def afterAll(): Unit = {
    super.afterAll()
    FileIOUtils.deleteRecursively(tmpDir)
  }

  "A ResultListener" when {
    "initializing its sinks" should {
      val initSinks = PrivateMethod[Future[ResultSink]](Symbol("initSinks"))

      "provide sinks correctly" in {
        val sinkFuture =
          ResultListener invokePrivate initSinks(runId, validConfig)

        val sink = Await.result(sinkFuture, 5.seconds)

        sink shouldBe a[ResultSink]
      }

      "throw exception in case of unsupported config" in {
        val sinkFuture =
          ResultListener invokePrivate initSinks(runId, maliciousConfig)

        assertThrows[IllegalConfigException] {
          Await.result(sinkFuture, 5.seconds)
        }
      }
    }

    "failing initialization" should {
      "terminate the actor correctly" in {
        val testActor = testKit.spawn(
          Behaviors.monitor(
            testProbe.ref,
            ResultListener(runId, maliciousConfig)
          )
        )

        testProbe.expectMessageType[InitFailed]
        testProbe.expectTerminated(testActor)
      }
    }

    "handling a grid result" should {
      "write the grid data correctly into csv files" in {

        val parsedCfg = ConfigFactory.parseMap(
          Map("csv.directory" -> tmpDir.toFile.getAbsolutePath).asJava
        )
        val config =
          OsmoGridConfig.Output(parsedCfg, "output", new $TsCfgValidator())

        val testActor = testKit.spawn(
          Behaviors.monitor(
            testProbe.ref,
            ResultListener(runId, config)
          )
        )

        testProbe.expectMessageType[InitComplete]

        val jointGrid = threeWindingTestGrid
        testActor ! GridResult(jointGrid)

        testProbe.expectMessageType[GridResult]
        testProbe.expectMessage(ResultHandlingSucceeded)
        testProbe.expectTerminated(testActor)

        val gridData = CsvJointGridContainerSource.read(
          jointGrid.getGridName,
          ";",
          tmpDir,
          false
        )

        gridData shouldBe jointGrid
      }
    }
  }
}
