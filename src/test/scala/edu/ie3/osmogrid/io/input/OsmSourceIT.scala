/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.io.input

import edu.ie3.osmogrid.io.input.OsmSource.PbfFileSource
import edu.ie3.osmogrid.model.OsmoGridModel
import edu.ie3.osmogrid.model.OsmoGridModel.LvOsmoGridModel
import edu.ie3.osmogrid.model.SourceFilter.LvFilter
import edu.ie3.test.common.{InputDataCheck, UnitSpec}
import org.apache.pekko.actor.testkit.typed.scaladsl.ActorTestKit
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorRef, Behavior}

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

class OsmSourceIT extends UnitSpec with InputDataCheck {
  private val testKit = ActorTestKit("OsmSourceIT")

  "Reading input data from pbf file" when {
    "having proper input data" should {
      "provide full data set correctly" in {
        val resourcePath = getResourcePath("/Witten_Stockum.pbf")
        val requestProbe = testKit.createTestProbe[Try[OsmoGridModel]]()
        val testActor = testKit.spawn(
          OsmSourceIT.SourceTestActor(resourcePath)
        )

        testActor ! OsmSourceIT.SourceTestActor.Read(requestProbe.ref)
        requestProbe
          .expectMessageType[Try[OsmoGridModel]](
            30 seconds
          ) match {
          case Success(lvModel: LvOsmoGridModel) =>
            checkInputDataResult(lvModel)

          case Failure(exception) => fail(s"Failed with exception: $exception")
        }
      }
    }
  }

  override protected def afterAll(): Unit = {
    testKit.shutdownTestKit()
  }
}

private object OsmSourceIT {
  object SourceTestActor {
    sealed trait SourceTestActorMsg
    final case class Read(replyTo: ActorRef[Try[OsmoGridModel]])
        extends SourceTestActorMsg
    final case class Result(osm: Try[OsmoGridModel]) extends SourceTestActorMsg

    private def idle(source: OsmSource) =
      Behaviors.receive[SourceTestActorMsg] { case (ctx, Read(replyTo)) =>
        ctx.pipeToSelf(
          source.read(
            LvFilter()
          )
        )(Result.apply)
        receiveResult(replyTo)
      }

    private def receiveResult(replyTo: ActorRef[Try[OsmoGridModel]]) =
      Behaviors.receive[SourceTestActorMsg] { case (ctx, Result(osm)) =>
        replyTo ! osm
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
