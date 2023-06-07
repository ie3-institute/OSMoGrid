/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.io.output

import akka.actor.typed.Behavior
import akka.actor.testkit.typed.scaladsl.{ActorTestKit, BehaviorTestKit}
import akka.actor.testkit.typed.CapturedLogEvent
import akka.actor.typed.internal.adapter.ActorContextAdapter
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import com.typesafe.config.ConfigFactory
import edu.ie3.datamodel.io.source.csv.CsvJointGridContainerSource
import edu.ie3.datamodel.models.input.container.RawGridElements
import edu.ie3.osmogrid.cfg.{OsmoGridConfig, OsmoGridConfigFactory}
import edu.ie3.osmogrid.cfg.OsmoGridConfig.$TsCfgValidator
import edu.ie3.osmogrid.exception.IllegalConfigException
import edu.ie3.osmogrid.io.output.ResultListenerProtocol._
import edu.ie3.osmogrid.io.output.ResultListenerProtocol.PersistenceListenerEvent._

import scala.concurrent.Await

//import edu.ie3.osmogrid.exception.PbfReadFailedException
//import edu.ie3.osmogrid.io.output.ResultListener
//import edu.ie3.osmogrid.model.OsmoGridModel.LvOsmoGridModel
//import edu.ie3.osmogrid.model.SourceFilter.LvFilter
import edu.ie3.osmogrid.util.TestGridFactory
import edu.ie3.util.io.FileIOUtils
import edu.ie3.test.common.{UnitSpec, IOTestCommons, ThreeWindingTestData}

import java.io.File
// import java.nio.file.Paths
import scala.concurrent.duration.DurationInt
import scala.concurrent.Future
import scala.util.{Failure, Success}
import scala.jdk.CollectionConverters._
//import scala.language.postfixOps
import java.util.UUID
import org.slf4j.LoggerFactory
import org.slf4j.event.Level

class ResultListenerIT
  extends UnitSpec
    with IOTestCommons
    with ThreeWindingTestData {

  private val testKit = ActorTestKit("ResultListenerIT")
  private val testProbe = testKit.createTestProbe[ResultListenerProtocol]()

  private val runId = UUID.randomUUID()
  private val validConfig = OsmoGridConfigFactory.defaultTestConfig.output
  private val maliciousConfig = OsmoGridConfigFactory
    .parseWithoutFallback("")
    .getOrElse(fail("Unable to parse malicious config"))
    .output

  "A ResultListener" when {
    "initializing its sinks" should {
      val initSinks = PrivateMethod[Future[ResultSink]](Symbol("initSinks"))

      "provide sinks correctly" in {
        val sinkFuture = ResultListener invokePrivate initSinks(runId, validConfig)

        val sink = Await.result(sinkFuture, 5.seconds)

        sink shouldBe a[ResultSink]
      }

      "throw exception in case of unsupported config" in {
        val sinkFuture = ResultListener invokePrivate initSinks(runId, maliciousConfig)

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
        val config = OsmoGridConfig.Output(parsedCfg, "output", new $TsCfgValidator())

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

        //testKit.shutdownTestKit()
        //testKit.stop(testActor)

        val gridData = CsvJointGridContainerSource.read(
          jointGrid.getGridName,
          ";",
          testTmpDir
        )

        gridData shouldBe jointGrid

        //Await.ready(testKit.system.whenTerminated, 5.seconds)

        FileIOUtils.deleteRecursively(testTmpDir)
      }

/*        createDir(testTmpDir)
        val csvSeparator = ","
        val hierarchic = false

        val resultSink = ResultCsvSink(
          runId,
          testTmpDir,
          csvSeparator,
          hierarchic
        )

        val jointGrid = threeWindingTestGrid

        val writingFuture = resultSink.handleResult(GridResult(jointGrid))
        Await.ready(writingFuture, 5.seconds)

        resultSink.close()

        val gridData = CsvJointGridContainerSource.read(
          jointGrid.getGridName,
          csvSeparator,
          testTmpDir
        )

        gridData shouldBe jointGrid

        FileIOUtils.deleteRecursively(testTmpDir)*/
    }
  }
}

/*  "A ResultListener" when {
    "setting everything up" should {
      "initialize correctly" in {
        val runId = UUID.randomUUID()
        val config: OsmoGridConfig.Output = OsmoGridConfigFactory.defaultTestConfig.output

        val testActor = testKit.spawn(
          ResultListener(runId, config)
        )


    "handling result" should {
      "provide valid grid data correctly" in {

      }
      "stop in ?" in {

      }
    }
  }*/

/*

    "completing initialization" should {
      val init = PrivateMethod[Behavior[ResultListenerProtocol]](Symbol("init"))
      val context = ActorContextAdapter[ResultListenerProtocol]
      val runningTestKit = BehaviorTestKit(
        ResultListener invokePrivate init(runGuardianData, childReferences)
      )

      "go into idle state" in {



/*        val runId = UUID.randomUUID()
        val config: OsmoGridConfig.Output = createConfig()

        val testActor = testKit.spawn(
          ResultListener(runId, config)
        )*/

        //ResultListener.idle()
      }

      "stop if initialization failed" in {

      }
    }

    "being in idle state" should {
//      val idle = PrivateMethod[Behavior[ResultListenerProtocol]](Symbol("idle"))
//      //val context = new ActorContext[ResultListenerProtocol]
//      val idleTestKit = BehaviorTestKit(
//        ResultListener invokePrivate idle(stateData)
//      )
//      "" in {

      }
    }
  }

/*  "Reading grid data in psdm format" when {
    "having proper grid data" should {
      "write full grid data set correctly into csv files" in {
        val runId = UUID.randomUUID()

        createDir(testTmpDir)
        val gridName = "Test_Grid"
        val outFilePath = testTmpDir + File.separator + gridName

        val config: OsmoGridConfig.Output = createConfig(outFilePath)

        // println(config)

        val testActor = testKit.spawn(
          ResultListener(runId, config)
        )

        // val jointGrid = TestGridFactory.createJointGrid()
        val jointGrid = threeWindingTestGrid

        testActor ! ResultListenerProtocol.GridResult(jointGrid)

        Thread.sleep(10000)

        println("Test 1")

        val gridData = CsvJointGridContainerSource.read(
          gridName,
          // config.csv.get.separator,
          ";",
          testTmpDir
        )

        println("Test 2")

        gridData shouldBe jointGrid

        FileIOUtils.deleteRecursively(testTmpDir)
      }
    }
  }*/

  private def createConfig(filePath: String = "test") = {
    val parsedCfg = ConfigFactory.parseMap(
      Map("csv.directory" -> filePath).asJava
    )

    val config =
      OsmoGridConfig.Output(parsedCfg, "output", new $TsCfgValidator())
    config
  }*/
