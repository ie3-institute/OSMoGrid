/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.io.input

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer}
import akka.actor.typed.{ActorRef, Behavior}
import com.acervera.osm4scala.EntityIterator.fromPbf
import com.acervera.osm4scala.model.{NodeEntity, RelationEntity, WayEntity}
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.guardian.OsmoGridGuardian.OsmoGridGuardianEvent
import edu.ie3.osmogrid.model.{OsmoGridModel, PbfFilter}
import edu.ie3.util.osm.model.OsmEntity.{Node, Relation, Way}
import org.locationtech.jts.geom.{
  Coordinate,
  LinearRing,
  Point,
  Polygon,
  PrecisionModel
}

import java.io.{FileInputStream, InputStream}
import java.time.ZonedDateTime
import java.util.UUID
import scala.collection.mutable.ListBuffer
import scala.util.{Failure, Success, Try, Using}

object InputDataProvider {

  // external requests
  sealed trait InputRequest

  final case class ReqOsm(
      runId: UUID,
      replyTo: ActorRef[InputDataProvider.Response],
      filter: PbfFilter
  ) extends InputRequest
      with InputDataEvent

  final case class ReqAssetTypes(
      runId: UUID,
      replyTo: ActorRef[InputDataProvider.Response]
  ) extends InputRequest
      with InputDataEvent

  case object Terminate extends InputRequest with InputDataEvent

  // external responses
  sealed trait Response
  final case class RepOsm(runId: UUID, osmModel: OsmoGridModel) extends Response
  final case class InvalidOsmRequest(reqRunId: UUID, actualRunId: UUID)
      extends Response
  final case class OsmReadFailed(reason: Throwable)
      extends Response
      with InputDataEvent
  final case class RepAssetTypes(osmModel: OsmoGridModel) extends Response

  // internal api
  sealed trait InputDataEvent

  private final case class OsmDataReadSuccessful(
      runId: UUID,
      osmModel: OsmoGridModel
  ) extends InputDataEvent

  // actor data
  private final case class ProviderData(
      runId: UUID,
      ctx: ActorContext[InputDataEvent],
      buffer: StashBuffer[InputDataEvent],
      osmSource: OsmSource
  )

  def apply(runId: UUID, osmSource: OsmSource): Behavior[InputDataEvent] = {
    Behaviors.withStash[InputDataEvent](100) { buffer =>
      Behaviors.setup[InputDataEvent] { ctx =>
        idle(ProviderData(runId, ctx, buffer, osmSource))
      }
    }
  }

  private def idle(providerData: ProviderData): Behavior[InputDataEvent] =
    Behaviors.receive[InputDataEvent] { case (ctx, msg) =>
      msg match {
        case ReqOsm(runId, replyTo, filter) =>
          if (runId != providerData.runId) {
            replyTo ! InvalidOsmRequest(runId, providerData.runId)
            Behaviors.same
          } else {
            ctx.pipeToSelf(
              providerData.osmSource
                .read(filter)
            ) {
              case Success(osmoGridModel: OsmoGridModel) =>
                OsmDataReadSuccessful(runId, osmoGridModel)
              case Failure(exception) =>
                ctx.log.error(
                  s"Error while reading osm data: $exception"
                )
                OsmReadFailed(exception)
            }
            readOsmData(providerData, replyTo)
          }
        case ReqAssetTypes(_, _) =>
          ctx.log.info("Got request to provide asset types. But do nothing.")
          Behaviors.same
        case Terminate =>
          ctx.log.info("Stopping input data provider ...")
          cleanUp(providerData)
          Behaviors.stopped
        case invalid: (OsmReadFailed | OsmDataReadSuccessful) =>
          ctx.log.error(
            s"Received unexpected message '$invalid' in state Idle! Ignoring!"
          )
          Behaviors.same
      }
    }

  private def readOsmData(
      providerData: ProviderData,
      replyTo: ActorRef[InputDataProvider.Response]
  ): Behaviors.Receive[InputDataEvent] =
    Behaviors.receiveMessage {
      case OsmDataReadSuccessful(runId, osmoGriModel) =>
        replyTo ! RepOsm(runId, osmoGriModel)
        providerData.buffer.unstashAll(idle(providerData))
      case OsmReadFailed(exception: Exception) =>
        replyTo ! OsmReadFailed(exception)
        providerData.buffer.unstashAll(idle(providerData))
      case other =>
        providerData.buffer.stash(other)
        Behaviors.same
    }

  private def cleanUp(providerData: ProviderData): Unit = {
    providerData.osmSource.close()
  }
}
