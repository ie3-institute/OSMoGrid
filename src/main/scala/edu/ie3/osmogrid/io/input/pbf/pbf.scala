/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.io.input.pbf

import akka.actor.typed.ActorRef
import com.acervera.osm4scala.BlobTupleIterator
import edu.ie3.osmogrid.model.{OsmoGridModel, SourceFilter}
import edu.ie3.util.osm.model.OsmContainer
import edu.ie3.util.osm.model.OsmContainer.ParOsmContainer
import org.openstreetmap.osmosis.osmbinary.fileformat.{Blob, BlobHeader}

import java.io.InputStream
import scala.collection.parallel.immutable.{ParSeq, ParVector}

// external request protocol
sealed trait PbfGuardianRequest
sealed trait PbfWorkerRequest

final case class PbfRun(replyTo: ActorRef[PbfGuardianResponse])
    extends PbfGuardianRequest

private[pbf] final case class ReadBlobMsg(
    blobHeader: BlobHeader,
    blob: Blob,
    replyTo: ActorRef[PbfWorkerResponse]
) extends PbfWorkerRequest

// external reply protocol
sealed trait PbfGuardianResponse
sealed trait PbfWorkerResponse

final case class PbfReadSuccessful(osmoGridModel: OsmoGridModel)
    extends PbfGuardianResponse

final case class PbfReadFailed(exception: Throwable) extends PbfGuardianResponse

private[pbf] final case class ReadSuccessful(osmContainer: OsmContainer)
    extends PbfWorkerResponse

private[pbf] final case class ReadFailed(
    blobHeader: BlobHeader,
    blob: Blob,
    filter: SourceFilter,
    exception: Throwable
) extends PbfWorkerResponse

// internal private protocol
private final case class WrappedPbfWorkerResponse(
    response: PbfWorkerResponse
) extends PbfGuardianRequest

// internal state data
protected[pbf] final case class StateData(
    pbfIS: InputStream,
    blobIterator: BlobTupleIterator,
    workerPool: ActorRef[PbfWorkerRequest],
    workerResponseMapper: ActorRef[PbfWorkerResponse],
    filter: SourceFilter,
    sender: Option[ActorRef[PbfGuardianResponse]] = None,
    noOfBlobs: Int = -1,
    noOfResponses: Int = 0,
    receivedModels: ParSeq[ParOsmContainer] = ParVector.empty,
    startTime: Long = -1
) {
  def allBlobsRead(): Boolean = noOfResponses == noOfBlobs
}
