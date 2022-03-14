/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.io.input

import akka.actor.testkit.typed.scaladsl.{
  ActorTestKit,
  ScalaTestWithActorTestKit
}
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import edu.ie3.osmogrid.io.input.OsmSource.PbfFileSource
import edu.ie3.osmogrid.io.input.OsmSourceSpec.SourceTestActor.SourceTestActorMsg
import edu.ie3.osmogrid.model.OsmoGridModel.LvOsmoGridModel
import edu.ie3.osmogrid.model.{OsmoGridModel, SourceFilter}
import edu.ie3.osmogrid.model.SourceFilter.{Filter, LvFilter}
import edu.ie3.test.common.UnitSpec
import org.scalatest.BeforeAndAfterAll

import java.nio.file.Paths
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import scala.concurrent.Await
import scala.concurrent.duration.{Duration, FiniteDuration}

class OsmSourceSpec extends UnitSpec with BeforeAndAfterAll {
  private val testKit = ActorTestKit("OsmSourceSpec")

  "Reading input data from pbf file" when {
    "having proper input data" should {
      "provide full data set correctly" in {
        val inputResource = getClass.getResource("/Witten_Stockum.pbf")
        assert(inputResource != null)
        val resourcePath =
          Paths.get(inputResource.toURI).toAbsolutePath.toString

        val testProbe = testKit.createTestProbe[SourceTestActorMsg]()
        val testActor = testKit.spawn(
          OsmSourceSpec.SourceTestActor(resourcePath)
        )

        testActor ! OsmSourceSpec.SourceTestActor.Read(testProbe.ref)
        testProbe
          .expectMessageType[OsmSourceSpec.SourceTestActor.Provide](
            FiniteDuration(120L, TimeUnit.SECONDS)
          ) match {
          case OsmSourceSpec.SourceTestActor.Provide(
                LvOsmoGridModel(
                  buildings,
                  highways,
                  landuses,
                  boundaries,
                  existingSubstations,
                  filter
                )
              ) =>
            landuses should have length 38
            landuses.map(_.allSubEntities.size).sum shouldBe 705

            highways should have length 1424
            highways.map(_.allSubEntities.size).sum shouldBe 3947

            buildings should have length 2512
            buildings.map(_.allSubEntities.size).sum shouldBe 16367

            boundaries should have length 7
            boundaries.map(_.allSubEntities.size).sum shouldBe 0

            existingSubstations should have length 10
            existingSubstations.map(_.allSubEntities.size).sum shouldBe 40

          case unexpected => fail("Found unexpected osm data.")
        }
      }
    }
  }

  override protected def afterAll(): Unit = {
    testKit.shutdownTestKit()
    super.afterAll()
  }
}

private object OsmSourceSpec {
  object SourceTestActor {
    sealed trait SourceTestActorMsg
    final case class Read(replyTo: ActorRef[SourceTestActorMsg])
        extends SourceTestActorMsg
    final case class Provide(osm: OsmoGridModel) extends SourceTestActorMsg

    private def idle(source: OsmSource) =
      Behaviors.receive[SourceTestActorMsg] {
        case (ctx, Read(replyTo)) =>
          val result = Await.result(
            source.read(
              LvFilter()
            ),
            Duration.Inf
          )

          replyTo ! Provide(result)
          Behaviors.stopped
        case (ctx, unsupported) =>
          ctx.log.error(
            s"Received unsupported message '$unsupported'. Stop test actor"
          )
          Behaviors.stopped
      }

    def apply(pbfFilePath: String): Behavior[SourceTestActorMsg] =
      Behaviors.setup[SourceTestActorMsg] { ctx =>
        val source = PbfFileSource(
          pbfFilePath,
          ctx
        )
        idle(source)
      }
  }
}
