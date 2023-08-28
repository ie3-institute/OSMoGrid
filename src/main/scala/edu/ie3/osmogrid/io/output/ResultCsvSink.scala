/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.io.output

import edu.ie3.datamodel.io.naming.{
  DefaultDirectoryHierarchy,
  EntityPersistenceNamingStrategy,
  FileNamingStrategy,
  FlatDirectoryHierarchy
}
import edu.ie3.datamodel.io.processor.ProcessorProvider
import edu.ie3.datamodel.io.sink.CsvFileSink

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

final case class ResultCsvSink(
    runId: UUID,
    saveFolderPath: String,
    csvSeparator: String,
    hierarchic: Boolean
) extends ResultSink {

  private val csvFileSink = new CsvFileSink(
    saveFolderPath,
    new ProcessorProvider(),
    new FileNamingStrategy(
      new EntityPersistenceNamingStrategy(),
      if (hierarchic)
        new DefaultDirectoryHierarchy(saveFolderPath, "grid")
      else
        new FlatDirectoryHierarchy()
    ),
    false,
    csvSeparator
  )

  def handleResult(
      gridResult: ResultListenerProtocol.GridResult
  ): Future[Unit] =
    Future(csvFileSink.persistJointGrid(gridResult.grid))

  def close(): Unit = csvFileSink.shutdown()
}