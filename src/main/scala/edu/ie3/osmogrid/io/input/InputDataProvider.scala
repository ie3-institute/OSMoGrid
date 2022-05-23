/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.io.input

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer}
import akka.actor.typed.{ActorRef, Behavior, PostStop}
import com.acervera.osm4scala.EntityIterator.fromPbf
import com.acervera.osm4scala.model.{NodeEntity, RelationEntity, WayEntity}
import edu.ie3.datamodel.models.input.connector.`type`.{
  LineTypeInput,
  Transformer2WTypeInput
}
import edu.ie3.osmogrid.ActorStopSupport
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.io.input.InputDataProvider.{
  InputDataEvent,
  ProviderData
}
import edu.ie3.osmogrid.model.{OsmoGridModel, SourceFilter}
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
import java.util.UUID

object InputDataProvider extends ActorStopSupport[ProviderData] {

  // external requests
  sealed trait Request

  final case class ReqOsm(
      replyTo: ActorRef[InputDataProvider.Response],
      filter: SourceFilter
  ) extends Request
      with InputDataEvent

  final case class ReqAssetTypes(
      replyTo: ActorRef[InputDataProvider.Response]
  ) extends Request
      with InputDataEvent

  case object Terminate extends Request with InputDataEvent

  // external responses
  sealed trait Response
  final case class RepOsm(osmModel: OsmoGridModel)
      extends Response
      with InputDataEvent
  final case class OsmReadFailed(reason: Throwable)
      extends Response
      with InputDataEvent
  final case class RepAssetTypes(assetInformation: AssetInformation)
      extends Response

  final case class AssetInformation(
      lineTypes: Seq[LineTypeInput],
      transformerTypes: Seq[Transformer2WTypeInput]
  )

  // internal api
  sealed trait InputDataEvent

  // actor data
  protected final case class ProviderData(
      ctx: ActorContext[InputDataEvent],
      buffer: StashBuffer[InputDataEvent],
      osmSource: OsmSource
  )

  def apply(
      runId: UUID,
      osmConfig: OsmoGridConfig.Input
  ): Behavior[InputDataEvent] = {
    Behaviors.withStash[InputDataEvent](100) { buffer =>
      Behaviors.setup[InputDataEvent] { ctx =>
        idle(
          ProviderData(ctx, buffer, OsmSource(osmConfig.osm, ctx))
        )
      }
    }
  }

  private def idle(providerData: ProviderData): Behavior[InputDataEvent] =
    Behaviors
      .receive[InputDataEvent] { case (ctx, msg) =>
        msg match {
          case ReqOsm(replyTo, filter) =>
            ctx.pipeToSelf(
              providerData.osmSource.read(filter)
            ) {
              case Success(osmoGridModel: OsmoGridModel) =>
                RepOsm(osmoGridModel)
              case Failure(exception) =>
                ctx.log.error(
                  s"Error while reading osm data: $exception"
                )
                OsmReadFailed(exception)
            }
            readOsmData(providerData, replyTo)
          case ReqAssetTypes(_) =>
            ctx.log.info("Got request to provide asset types. But do nothing.")
            Behaviors.same
          case Terminate =>
            terminate(ctx.log, providerData)
          case invalid: (OsmReadFailed | RepOsm) =>
            ctx.log.error(
              s"Received unexpected message '$invalid' in state Idle! Ignoring!"
            )
            Behaviors.same
        }
      }
      .receiveSignal { case (ctx, PostStop) =>
        postStopCleanUp(ctx.log, providerData)
      }

  private def readOsmData(
      providerData: ProviderData,
      replyTo: ActorRef[InputDataProvider.Response]
  ): Behaviors.Receive[InputDataEvent] =
    Behaviors.receiveMessage {
      case osmResponse: RepOsm =>
        replyTo ! osmResponse
        providerData.buffer.unstashAll(idle(providerData))
      case readFailed: OsmReadFailed =>
        replyTo ! readFailed
        providerData.buffer.unstashAll(idle(providerData))
      case other =>
        providerData.buffer.stash(other)
        Behaviors.same
    }

  // TODO this doesn't seem to make too much sense here
  /* Nothing to do here. At least until now. */
  override protected def cleanUp(providerData: ProviderData): Unit = {
    providerData.osmSource.close()
  }
}
