/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.io.input

import edu.ie3.datamodel.models.input.connector.`type`.{
  LineTypeInput,
  Transformer2WTypeInput,
  Transformer3WTypeInput,
}
import edu.ie3.osmogrid.cfg.OsmoGridConfig
import edu.ie3.osmogrid.model.{OsmoGridModel, SourceFilter}
import edu.ie3.util.osm.model.OsmContainer.ParOsmContainer
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.scaladsl.{ActorContext, StashBuffer}

// actor data
final case class ProviderData(
    ctx: ActorContext[InputDataEvent],
    buffer: StashBuffer[InputDataEvent],
    osmCfg: OsmoGridConfig.Input.Osm,
    osmSource: OsmSource,
    assetSource: AssetSource,
    osmContainer: Option[ParOsmContainer] = None,
)

// external requests
sealed trait InputRequest

// internal api
sealed trait InputDataEvent

enum FilterType {
  case LV, POI
}

final case class ReqOsm(
    replyTo: ActorRef[InputResponse],
    filterType: FilterType,
) extends InputRequest
    with InputDataEvent

final case class ReqAssetTypes(
    replyTo: ActorRef[InputResponse]
) extends InputRequest
    with InputDataEvent

case object InputTerminate extends InputRequest with InputDataEvent

// external responses
sealed trait InputResponse

final case class RepOsmContainer(osmContainer: ParOsmContainer)
    extends InputResponse
    with InputDataEvent

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
    transformerTypes: Seq[Transformer2WTypeInput],
    transformer3WTypes: Seq[Transformer3WTypeInput],
)
