/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.io.input

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.{ActorContext, StashBuffer}
import edu.ie3.datamodel.models.input.connector.`type`.{
  LineTypeInput,
  Transformer2WTypeInput
}
import edu.ie3.osmogrid.model.{OsmoGridModel, SourceFilter}

// actor data
final case class ProviderData(
    ctx: ActorContext[InputDataEvent],
    buffer: StashBuffer[InputDataEvent],
    osmSource: OsmSource,
    assetSource: AssetSource
)

// external requests
sealed trait InputRequest

// internal api
sealed trait InputDataEvent

final case class ReqOsm(
    replyTo: ActorRef[InputResponse],
    filter: SourceFilter
) extends InputRequest
    with InputDataEvent

final case class ReqAssetTypes(
    replyTo: ActorRef[InputResponse]
) extends InputRequest
    with InputDataEvent

case object InputTerminate extends InputRequest with InputDataEvent

// external responses
sealed trait InputResponse

final case class RepOsm(osmModel: OsmoGridModel)
    extends InputResponse
    with InputDataEvent

final case class OsmReadFailed(reason: Throwable)
    extends InputResponse
    with InputDataEvent

final case class RepAssetTypes(assetInformation: AssetInformation)
    extends InputResponse
    with InputDataEvent

final case class AssetReadFailed(reason: Throwable)
    extends InputResponse
    with InputDataEvent

final case class AssetInformation(
    lineTypes: Seq[LineTypeInput],
    transformerTypes: Seq[Transformer2WTypeInput]
)
