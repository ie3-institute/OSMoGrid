/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.io.output

import edu.ie3.datamodel.io.csv.BufferedCsvWriter
import edu.ie3.datamodel.io.naming.{
  DefaultDirectoryHierarchy,
  EntityPersistenceNamingStrategy,
  FileNamingStrategy,
  FlatDirectoryHierarchy,
}
import edu.ie3.datamodel.io.sink.CsvFileSink
import edu.ie3.osmogrid.poi.PoiElement

import java.nio.file.{Files, Path}
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

final case class ResultCsvSink(
    runId: UUID,
    saveFolderPath: Path,
    csvSeparator: String,
    hierarchic: Boolean,
) extends ResultSink {

  private val csvFileSink = new CsvFileSink(
    saveFolderPath,
    new FileNamingStrategy(
      new EntityPersistenceNamingStrategy(),
      if (hierarchic)
        new DefaultDirectoryHierarchy(saveFolderPath, "grid")
      else
        new FlatDirectoryHierarchy(),
    ),
    csvSeparator,
  )

  private val poiSink = {
    val path = saveFolderPath.resolve("poi")
    Files.createDirectories(path)
    val writer = new BufferedCsvWriter(
      path.resolve("poi.csv"),
      Array("id", "size", "lat", "lon"),
      csvSeparator,
      false,
    )
    writer.writeFileHeader()
    writer
  }

  private given Conversion[PoiElement, java.util.Map[String, String]] = poi => {
    val map = new java.util.HashMap[String, String](4)
    map.put("id", s"${poi.poiType}" + s"_${poi.id}")
    map.put("size", poi.size.toString)
    map.put("lat", poi.lat.toString)
    map.put("lon", poi.lon.toString)
    map
  }

  override def handlePOIs(pois: Iterable[PoiElement]): Future[Unit] = Future(
    pois.foreach(poiSink.write(_))
  )

  def handleResult(
      gridResult: GridResult
  ): Future[Unit] =
    Future(csvFileSink.persistJointGrid(gridResult.grid))

  def close(): Unit = csvFileSink.shutdown()
}
