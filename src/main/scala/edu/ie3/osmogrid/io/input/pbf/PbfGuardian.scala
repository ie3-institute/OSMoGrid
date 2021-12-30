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
import edu.ie3.osmogrid.io.input.pbf.PbfWorker.{PbfWorkerMsg, ReadBlobMsg}
import edu.ie3.osmogrid.model.OsmoGridModel
import edu.ie3.util.osm.OsmEntities.{Node, Relation, Way}

import java.io.{File, FileInputStream, InputStream}

private[input] object PbfGuardian {

  // external request protocol
  sealed trait PbfReaderMsg
  final case class Run(replyTo: ActorRef[PbfGuardian.Reply])
      extends PbfReaderMsg

  // external reply protocol
  sealed trait Reply
  final case class PbfReadSuccessful(osmoGridModel: OsmoGridModel) extends Reply
  final case class PbfReadFailed(exception: Throwable) extends Reply

  // internal private protocol
  private final case class WrappedPbfWorkerResponse(
      response: PbfWorker.Response
  ) extends PbfReaderMsg

  // internal state data
  private final case class OsmAccumulator(
      nodes: List[Node] = List.empty,
      ways: List[Way] = List.empty,
      relations: List[Relation] = List.empty,
      highways: List[Way] = List.empty,
      buildings: List[Way] = List.empty,
      landuses: List[Way] = List.empty
  ) {
    def update(
        nodes: List[Node],
        ways: List[Way],
        relations: List[Relation],
        highways: List[Way],
        buildings: List[Way],
        landuses: List[Way]
    ): OsmAccumulator =
      this.copy(
        nodes = this.nodes ++ nodes,
        ways = this.ways ++ ways,
        relations = this.relations ++ relations,
        highways = this.highways ++ highways,
        buildings = this.buildings ++ buildings,
        landuses = this.landuses ++ landuses
      )

    def osmoGridModel(): OsmoGridModel = OsmoGridModel(
      nodes,
      ways,
      Some(relations),
      null, // todo fix
      null, // todo fix
      null, // todo fix
      null // todo fix
    )
  }

  private final case class StateData(
      pbfIS: InputStream,
      blobIterator: BlobTupleIterator,
      workerPool: ActorRef[PbfWorkerMsg],
      workerResponseMapper: ActorRef[PbfWorker.Response],
      sender: Option[ActorRef[PbfGuardian.Reply]] = None,
      noOfBlobs: Int = -1,
      noOfResponses: Int = 0,
      osmAccumulator: OsmAccumulator = OsmAccumulator()
  ) {
    def updateOsmAccumulator(
        nodes: List[Node],
        ways: List[Way],
        relations: List[Relation],
        highways: List[Way],
        buildings: List[Way],
        landuses: List[Way]
    ): StateData =
      this.copy(
        osmAccumulator = osmAccumulator
          .update(nodes, ways, relations, highways, buildings, landuses),
        noOfResponses = this.noOfResponses + 1
      )

    def allBlobsRead(): Boolean = noOfResponses == noOfBlobs

    def osmoGridModel(): OsmoGridModel =
      osmAccumulator.osmoGridModel()

  }

  def apply(
      pbfFile: File,
      highwayTags: Option[Set[String]] = None,
      buildingTags: Option[Set[String]] = None,
      landuseTags: Option[Set[String]] = None,
      noOfActors: Int = Runtime.getRuntime.availableProcessors()
  ): Behavior[PbfReaderMsg] = Behaviors.setup[PbfReaderMsg] { ctx =>
    ctx.log.info(s"Start reading pbf file using $noOfActors actors ...")
    val pool = Routers.pool(poolSize = noOfActors) {
      Behaviors
        .supervise(PbfWorker(highwayTags, buildingTags, landuseTags))
        .onFailure[Exception](SupervisorStrategy.restart)
    }
    val router = ctx.spawn(pool, "pbf-worker-pool")
    val pbfIS = new FileInputStream(pbfFile)

    val workerResponseMapper: ActorRef[PbfWorker.Response] =
      ctx.messageAdapter(rsp => WrappedPbfWorkerResponse(rsp))

    idle(
      StateData(
        pbfIS,
        BlobTupleIterator.fromPbf(pbfIS),
        router,
        workerResponseMapper
      )
    )
  }

  private def idle(stateData: StateData): Behavior[PbfReaderMsg] = Behaviors
    .receive[PbfReaderMsg] { case (ctx, msg) =>
      msg match {
        case Run(sender) =>
          // start the reading process
          val noOfBlobs = readPbf(
            stateData.blobIterator,
            stateData.workerPool,
            stateData.workerResponseMapper
          )
          ctx.log.debug(s"Reading $noOfBlobs blobs ...")
          idle(stateData.copy(sender = Some(sender), noOfBlobs = noOfBlobs))
        case WrappedPbfWorkerResponse(response) =>
          response match {
            case PbfWorker.ReadSuccessful(
                  nodes,
                  ways,
                  relations,
                  highways,
                  buildings,
                  landuses
                ) =>
              val updatedStateData =
                stateData.updateOsmAccumulator(
                  nodes,
                  ways,
                  relations,
                  highways,
                  buildings,
                  landuses
                )

              // if we are done, terminate
              if (updatedStateData.allBlobsRead()) {
                ctx.log.info("Finished reading all blobs. Returning data!")

                stateData.sender.foreach(
                  _ ! PbfReadSuccessful(updatedStateData.osmoGridModel())
                )
                stopAndCleanup(stateData, ctx)
              }

              idle(updatedStateData)
            case PbfWorker.ReadFailed(blobHeader, blob, exception) =>
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
    .receiveSignal { case (ctx, PostStop) =>
      stopAndCleanup(stateData, ctx)
    }

  private def stopAndCleanup(
      stateData: StateData,
      ctx: ActorContext[PbfReaderMsg]
  ): Behavior[PbfReaderMsg] = {
    ctx.log.info("Stopping .pbf file reading!")
    stateData.pbfIS.close()
    Behaviors.stopped // stops this actor and all its children
  }

  private def readPbf(
      blobIterator: BlobTupleIterator,
      workerPool: ActorRef[PbfWorkerMsg],
      workerResponseMapper: ActorRef[PbfWorker.Response]
  ): Int =
    blobIterator.foldLeft(0) { case (counter, (blobHeader, blob)) =>
      workerPool ! ReadBlobMsg(blobHeader, blob, workerResponseMapper)
      counter + 1
    }
}
