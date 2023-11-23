/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.io.output

import edu.ie3.datamodel.io.source.csv.CsvJointGridContainerSource
import edu.ie3.test.common.{IOTestCommons, ThreeWindingTestData, UnitSpec}
import edu.ie3.util.io.FileIOUtils

import java.nio.file.Paths
import java.util.UUID
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class ResultCsvSinkIT
    extends UnitSpec
    with IOTestCommons
    with ThreeWindingTestData {

  "A ResultCsvSink" should {
    "handle a given valid GridResult correctly" in {
      createDir(testTmpDir)
      val runId = UUID.randomUUID()
      val csvSeparator = ";"
      val hierarchic = false

      val resultSink = ResultCsvSink(
        runId,
        testTmpDir,
        csvSeparator,
        hierarchic
      )

      val jointGrid = threeWindingTestGrid

      val writingFuture = resultSink.handleResult(GridResult(jointGrid))
      Await.ready(writingFuture, 5.seconds)

      resultSink.close()

      val gridData = CsvJointGridContainerSource.read(
        jointGrid.getGridName,
        csvSeparator,
        Paths.get(testTmpDir),
        hierarchic
      )

      gridData shouldBe jointGrid

      FileIOUtils.deleteRecursively(testTmpDir)
    }
  }
}
