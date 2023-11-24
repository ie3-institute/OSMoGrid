/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.io.input.pbf

import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors, Routers}
import org.apache.pekko.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import com.acervera.osm4scala.BlobTupleIterator
import edu.ie3.osmogrid.ActorStopSupport
import edu.ie3.osmogrid.exception.PbfReadFailedException
import edu.ie3.osmogrid.model.OsmoGridModel.LvOsmoGridModel
import edu.ie3.osmogrid.model.SourceFilter
import edu.ie3.util.osm.model.OsmContainer
import edu.ie3.util.osm.model.OsmContainer.ParOsmContainer

import java.io.{File, FileInputStream}
import java.util.UUID
import scala.util.{Failure, Success, Try}

private[input] object PbfGuardian extends ActorStopSupport[StateData] {

  def apply(
      pbfFile: File,
      filter: SourceFilter,
      noOfActors: Int = Runtime.getRuntime.availableProcessors()
  ): Behavior[PbfGuardianRequest] = Behaviors.setup[PbfGuardianRequest] { ctx =>
    ctx.log.info(s"Start reading pbf file using $noOfActors actors ...")
    val pool = Routers.pool(poolSize = noOfActors) {
      Behaviors
        .supervise(PbfWorker())
        .onFailure[Exception](SupervisorStrategy.restart)
    }
    val router = ctx.spawn(pool, s"pbf-worker-pool-${UUID.randomUUID()}")
    val pbfIS = new FileInputStream(pbfFile)

    val workerResponseMapper: ActorRef[PbfWorkerResponse] =
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

  private def idle(stateData: StateData): Behavior[PbfGuardianRequest] =
    Behaviors
      .receive[PbfGuardianRequest] { case (ctx, msg) =>
        msg match {
          case PbfRun(sender) =>
            // start the reading process
            val start = System.currentTimeMillis()
            Try {
              readPbf(
                stateData.blobIterator,
                stateData.workerPool,
                stateData.workerResponseMapper
              )
            } match {
              case Success(noOfBlobs) if noOfBlobs > 0 =>
                ctx.log.debug(s"Reading $noOfBlobs blobs ...")
                idle(
                  stateData.copy(
                    sender = Some(sender),
                    noOfBlobs = noOfBlobs,
                    startTime = start
                  )
                )
              case Success(_) =>
                sender ! PbfReadFailed(
                  PbfReadFailedException(
                    "Input file is empty, stopping."
                  )
                )
                terminate(ctx.log, stateData)
              case Failure(exception) =>
                sender ! PbfReadFailed(
                  PbfReadFailedException(
                    "Reading input failed.",
                    exception
                  )
                )
                terminate(ctx.log, stateData)
            }

          case WrappedPbfWorkerResponse(response) =>
            response match {
              case ReadSuccessful(osmContainer) =>
                addOsmoGridModel(osmContainer, stateData, ctx)
              case ReadFailed(blobHeader, blob, filter, exception) =>
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
                terminate(ctx.log, stateData)
            }
        }
      }

  override protected def cleanUp(stateData: StateData): Unit =
    stateData.pbfIS.close()

  private def addOsmoGridModel(
      osmContainer: OsmContainer,
      stateData: StateData,
      ctx: ActorContext[PbfGuardianRequest]
  ) = {

    def status(stateData: StateData): Unit = {
      val currentProgress =
        ((stateData.noOfResponses.toDouble / stateData.noOfBlobs) * 100d).toInt
      val fivePercent = Math.round((stateData.noOfBlobs / 100d) * 5).toInt
      if (
        currentProgress != 0d && stateData.noOfResponses.toDouble % fivePercent == 0
      ) {
        ctx.log.debug(
          s"Finished reading ${stateData.noOfResponses} blobs ($currentProgress% of all blobs). Duration: ${(System
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
      terminate(ctx.log, stateData)
    }
    // if not not done, just stay in idle with updated data
    idle(updatedStateData)
  }

  private def readPbf(
      blobIterator: BlobTupleIterator,
      workerPool: ActorRef[PbfWorkerRequest],
      workerResponseMapper: ActorRef[PbfWorkerResponse]
  ): Int =
    blobIterator.foldLeft(0) { case (counter, (blobHeader, blob)) =>
      workerPool ! ReadBlobMsg(blobHeader, blob, workerResponseMapper)
      counter + 1
    }
}
