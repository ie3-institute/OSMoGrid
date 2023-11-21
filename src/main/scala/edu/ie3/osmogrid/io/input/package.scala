/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.io

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.scaladsl.{ActorContext, StashBuffer}
import edu.ie3.datamodel.models.input.connector.`type`.{
  LineTypeInput,
  Transformer2WTypeInput
}
import edu.ie3.osmogrid.model.{OsmoGridModel, SourceFilter}

package object input {
  // actor data
  final case class ProviderData(
      ctx: ActorContext[InputDataEvent],
      buffer: StashBuffer[InputDataEvent],
      osmSource: OsmSource,
      assetSource: AssetSource
  )

  // external requests
  sealed trait Request

  // internal api
  sealed trait InputDataEvent

  final case class ReqOsm(
      replyTo: ActorRef[input.Response],
      filter: SourceFilter
  ) extends Request
      with InputDataEvent

  final case class ReqAssetTypes(
      replyTo: ActorRef[input.Response]
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
}
