/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.io.output

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import com.typesafe.config.ConfigFactory
import edu.ie3.datamodel.io.source.csv.CsvJointGridContainerSource
import edu.ie3.osmogrid.cfg.{OsmoGridConfig, OsmoGridConfigFactory}
import edu.ie3.osmogrid.cfg.OsmoGridConfig.$TsCfgValidator
import edu.ie3.osmogrid.cfg.ConfigFailFastSpec.viableConfigurationString
import edu.ie3.osmogrid.exception.IllegalConfigException
import edu.ie3.osmogrid.io.output.ResultListenerProtocol._
import edu.ie3.osmogrid.io.output.ResultListenerProtocol.PersistenceListenerEvent._
import edu.ie3.util.io.FileIOUtils
import edu.ie3.test.common.{IOTestCommons, ThreeWindingTestData, UnitSpec}

import scala.concurrent.duration.DurationInt
import scala.concurrent.Future
import scala.concurrent.Await
import scala.jdk.CollectionConverters._
import java.util.UUID

class ResultListenerIT
    extends ScalaTestWithActorTestKit
    with UnitSpec
    with IOTestCommons
    with ThreeWindingTestData {

  private val testProbe = testKit.createTestProbe[ResultListenerProtocol]()
  private val runId = UUID.randomUUID()

  private val validConfig = OsmoGridConfigFactory.defaultTestConfig.output
  private val maliciousConfig = OsmoGridConfigFactory
    .parseWithoutFallback(viableConfigurationString.replaceAll("(?m)^.*output.csv.*$", ""))
    .getOrElse(fail("Unable to parse malicious config"))
    .output

  override protected def afterAll(): Unit = {
    super.afterAll()
    FileIOUtils.deleteRecursively(testTmpDir)
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
        createDir(testTmpDir)
        val parsedCfg = ConfigFactory.parseMap(
          Map("csv.directory" -> testTmpDir).asJava
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
          testTmpDir
        )

        gridData shouldBe jointGrid
      }
    }
  }
}
