/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.io.output

import edu.ie3.datamodel.io.source.csv.CsvJointGridContainerSource
import edu.ie3.test.common.{ThreeWindingTestData, UnitSpec}
import edu.ie3.util.io.FileIOUtils

import java.nio.file.{Files, Path}
import java.util.UUID
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class ResultCsvSinkIT extends UnitSpec with ThreeWindingTestData {

  val tmpDirectory: Path = Files.createTempDirectory("tmpDirectory")

  override protected def afterAll(): Unit = {
    super.afterAll()
    FileIOUtils.deleteRecursively(tmpDirectory)
  }

  "A ResultCsvSink" should {
    "handle a given valid GridResult correctly" in {
      val runId = UUID.randomUUID()
      val csvSeparator = ";"
      val hierarchic = false

      val resultSink = ResultCsvSink(
        runId,
        tmpDirectory.toFile.toPath,
        csvSeparator,
        hierarchic,
      )

      val jointGrid = threeWindingTestGrid

      val writingFuture = resultSink.handleResult(GridResult(jointGrid))
      Await.ready(writingFuture, 5.seconds)

      resultSink.close()

      val gridData = CsvJointGridContainerSource.read(
        jointGrid.getGridName,
        csvSeparator,
        tmpDirectory,
        hierarchic,
      )

      gridData shouldBe jointGrid
    }
  }
}
