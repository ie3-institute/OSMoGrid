/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.io.input.pbf

import akka.actor.typed.{ActorRef, Behavior, PostStop, SupervisorStrategy}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, Routers}
import com.acervera.osm4scala.BlobTupleIterator
import com.acervera.osm4scala.model.{NodeEntity, RelationEntity, WayEntity}
import edu.ie3.osmogrid.exception.PbfReadFailedException
import edu.ie3.osmogrid.io.input.pbf.PbfWorker.{Request, ReadBlobMsg}
import edu.ie3.osmogrid.model.OsmoGridModel.LvOsmoGridModel
import edu.ie3.osmogrid.model.{OsmoGridModel, SourceFilter}
import edu.ie3.util.osm.model.OsmContainer
import edu.ie3.util.osm.model.OsmContainer.ParOsmContainer
import edu.ie3.util.osm.model.OsmEntity.{Node, Relation, Way}

import java.io.{File, FileInputStream, InputStream}
import java.util.UUID
import scala.collection.parallel.immutable.{ParSeq, ParVector}

private[input] object PbfGuardian {

  sealed trait PbfGuardianEvent

  // external request protocol
  sealed trait Request

  final case class Run(replyTo: ActorRef[PbfGuardian.Response])
      extends Request
      with PbfGuardianEvent

  // external reply protocol
  sealed trait Response

  final case class PbfReadSuccessful(osmoGridModel: OsmoGridModel)
      extends Response

  final case class PbfReadFailed(exception: Throwable) extends Response

  // internal private protocol
  private final case class WrappedPbfWorkerResponse(
      response: PbfWorker.Response
  ) extends PbfGuardianEvent

  // internal state data
  private final case class StateData(
      pbfIS: InputStream,
      blobIterator: BlobTupleIterator,
      workerPool: ActorRef[PbfWorker.Request],
      workerResponseMapper: ActorRef[PbfWorker.Response],
      filter: SourceFilter,
      sender: Option[ActorRef[PbfGuardian.Response]] = None,
      noOfBlobs: Int = -1,
      noOfResponses: Int = 0,
      receivedModels: ParSeq[ParOsmContainer] = ParVector.empty,
      startTime: Long = -1
  ) {
    def allBlobsRead(): Boolean = noOfResponses == noOfBlobs
  }

  def apply(
      pbfFile: File,
      filter: SourceFilter,
      noOfActors: Int = Runtime.getRuntime.availableProcessors()
  ): Behavior[PbfGuardianEvent] = Behaviors.setup[PbfGuardianEvent] { ctx =>
    ctx.log.info(s"Start reading pbf file using $noOfActors actors ...")
    val pool = Routers.pool(poolSize = noOfActors) {
      Behaviors
        .supervise(PbfWorker())
        .onFailure[Exception](SupervisorStrategy.restart)
    }
    val router = ctx.spawn(pool, s"pbf-worker-pool-${UUID.randomUUID()}")
    val pbfIS = new FileInputStream(pbfFile)

    val workerResponseMapper: ActorRef[PbfWorker.Response] =
      ctx.messageAdapter(rsp => WrappedPbfWorkerResponse(rsp))

    idle(
      StateData(
        pbfIS,
        BlobTupleIterator.fromPbf(pbfIS),
        router,
        workerResponseMapper,
        filter
      )
    )
  }

  private def idle(stateData: StateData): Behavior[PbfGuardianEvent] = Behaviors
    .receive[PbfGuardianEvent] { case (ctx, msg) =>
      msg match {
        case Run(sender) =>
          // start the reading process
          val start = System.currentTimeMillis()
          val noOfBlobs = readPbf(
            stateData.blobIterator,
            stateData.workerPool,
            stateData.workerResponseMapper
          )
          ctx.log.debug(s"Reading $noOfBlobs blobs ...")
          idle(
            stateData.copy(
              sender = Some(sender),
              noOfBlobs = noOfBlobs,
              startTime = start
            )
          )
        case WrappedPbfWorkerResponse(response) =>
          response match {
            case PbfWorker.ReadSuccessful(osmContainer) =>
              addOsmoGridModel(osmContainer, stateData, ctx)
            case PbfWorker.ReadFailed(blobHeader, blob, filter, exception) =>
              ctx.log.error("Error reading blob data.", exception)
              stateData.sender.foreach(
                _ !
                  PbfReadFailed(
                    PbfReadFailedException(
                      "Error while reading pbf file from blob!"
                    )
                      .initCause(exception)
                  )
              )
              stopAndCleanup(stateData, ctx)
          }
      }
    }

  private def stopAndCleanup(
      stateData: StateData,
      ctx: ActorContext[PbfGuardianEvent]
  ): Behavior[PbfGuardianEvent] = {
    ctx.log.info("Stopping .pbf file reading!")
    stateData.pbfIS.close()
    Behaviors.stopped // stops this actor and all its children
  }

  private def addOsmoGridModel(
      osmContainer: OsmContainer,
      stateData: StateData,
      ctx: ActorContext[PbfGuardianEvent]
  ) = {

    def status(stateData: StateData): Unit = {
      val currentProgress =
        (stateData.noOfResponses.toDouble / stateData.noOfBlobs) * 100d
      val fivePercent = Math.round((stateData.noOfBlobs / 100d) * 5)
      if (
        currentProgress != 0d && stateData.noOfResponses.toDouble % fivePercent == 0
      ) {
        ctx.log.debug(
          s"Finished reading ${stateData.noOfResponses} blobs (${currentProgress.toInt}% of all blobs). Duration: ${(System
            .currentTimeMillis() - stateData.startTime) / 1000}s"
        )
      }
    }

    val updatedStateData =
      stateData.copy(
        receivedModels = stateData.receivedModels :+ osmContainer.par(),
        noOfResponses = stateData.noOfResponses + 1
      )

    if (ctx.log.isDebugEnabled)
      status(updatedStateData)

    // if we are done, terminate
    if (updatedStateData.allBlobsRead()) {
      ctx.log.debug("Finished reading all blobs. Returning data!")
      ctx.log.debug(
        s"Reading duration: ${(System.currentTimeMillis() - stateData.startTime) / 1000}s for ${updatedStateData.noOfBlobs} blobs"
      )
      val startProcessing = System.currentTimeMillis()
      ctx.log.debug("Start model processing ...")

      val mergedOsmContainer = ParOsmContainer(
        updatedStateData.receivedModels.flatMap(_.nodes).toMap,
        updatedStateData.receivedModels.flatMap(_.ways).toMap,
        updatedStateData.receivedModels.flatMap(_.relations).toMap
      )

      val osmoGridModel = updatedStateData.filter match {
        case lvFilter: SourceFilter.LvFilter =>
          LvOsmoGridModel(mergedOsmContainer, lvFilter, filterNodes = false)
      }

      ctx.log.debug(
        s"Processing done. Took ${(System.currentTimeMillis() - startProcessing) / 1000}s"
      )

      stateData.sender
        .foreach(_ ! PbfReadSuccessful(osmoGridModel))
      stopAndCleanup(stateData, ctx)
    }
    // if not not done, just stay in idle with updated data
    idle(updatedStateData)
  }

  private def readPbf(
      blobIterator: BlobTupleIterator,
      workerPool: ActorRef[PbfWorker.Request],
      workerResponseMapper: ActorRef[PbfWorker.Response]
  ): Int =
    blobIterator.foldLeft(0) { case (counter, (blobHeader, blob)) =>
      workerPool ! ReadBlobMsg(blobHeader, blob, workerResponseMapper)
      counter + 1
    }
}
