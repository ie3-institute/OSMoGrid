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

import java.nio.file.Paths
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
    Paths.get(saveFolderPath),
    new FileNamingStrategy(
      new EntityPersistenceNamingStrategy(),
      if (hierarchic)
        new DefaultDirectoryHierarchy(Paths.get(saveFolderPath), "grid")
      else
        new FlatDirectoryHierarchy()
    ),
    csvSeparator
  )

  def handleResult(
      gridResult: GridResult
  ): Future[Unit] =
    Future(csvFileSink.persistJointGrid(gridResult.grid))

  def close(): Unit = csvFileSink.shutdown()
}
