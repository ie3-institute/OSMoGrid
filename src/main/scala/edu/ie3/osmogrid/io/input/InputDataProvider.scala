/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.io.input

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer}
import akka.actor.typed.{ActorRef, Behavior, PostStop}
import edu.ie3.datamodel.models.input.connector.`type`.{
  LineTypeInput,
  Transformer2WTypeInput
}
import edu.ie3.osmogrid.ActorStopSupport
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.io.input.InputDataProvider.InputDataEvent
import edu.ie3.osmogrid.model.{OsmoGridModel, SourceFilter}

import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success}

// actor data
protected final case class ProviderData(
    ctx: ActorContext[InputDataEvent],
    buffer: StashBuffer[InputDataEvent],
    osmSource: OsmSource,
    assetSource: AssetSource
)

object InputDataProvider extends ActorStopSupport[ProviderData] {

  // external requests
  sealed trait Request

  // internal api
  sealed trait InputDataEvent

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
      with InputDataEvent
  final case class AssetReadFailed(reason: Throwable)
      extends Response
      with InputDataEvent

  final case class AssetInformation(
      lineTypes: Seq[LineTypeInput],
      transformerTypes: Seq[Transformer2WTypeInput]
  )

  def apply(
      osmConfig: OsmoGridConfig.Input
  ): Behavior[InputDataEvent] = {
    Behaviors.withStash[InputDataEvent](100) { buffer =>
      Behaviors.setup[InputDataEvent] { ctx =>
        implicit val ec: ExecutionContextExecutor = ctx.system.executionContext
        idle(
          ProviderData(
            ctx,
            buffer,
            OsmSource(osmConfig.osm, ctx),
            AssetSource(ec, osmConfig.asset)
          )
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
          case ReqAssetTypes(replyTo) =>
            ctx.pipeToSelf(
              providerData.assetSource.read()
            ) {
              case Success(assetInformation) => RepAssetTypes(assetInformation)
              case Failure(exception) =>
                ctx.log.error(s"Error while reading asset data: $exception")
                AssetReadFailed(exception)
            }
            readAssetData(providerData, replyTo)
          case Terminate =>
            terminate(ctx.log, providerData)
          case invalid =>
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

  private def readAssetData(
      providerData: ProviderData,
      replyTo: ActorRef[InputDataProvider.Response]
  ): Behaviors.Receive[InputDataEvent] = {
    Behaviors.receiveMessage {
      case repAssetTypes: RepAssetTypes =>
        replyTo ! repAssetTypes
        providerData.buffer.unstashAll(idle(providerData))
      case readFailed: AssetReadFailed =>
        replyTo ! readFailed
        providerData.buffer.unstashAll(idle(providerData))
      case other =>
        providerData.buffer.stash(other)
        Behaviors.same
    }
  }

  override protected def cleanUp(providerData: ProviderData): Unit = {
    providerData.osmSource.close()
  }
}
