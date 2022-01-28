/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.io.output

import edu.ie3.datamodel.models.result.ResultEntity
import edu.ie3.osmogrid.exception.ProcessResultException
import edu.ie3.osmogrid.io.output.ResultListenerProtocol.GridResult

import scala.concurrent.Future

/** Trait that should be mixed into each sink in [[edu.ie3.simona.io.result]] to
  * allow an easy calling of different sinks e.g. csv-file sink or database sink
  */
trait ResultSink {

  @throws(classOf[ProcessResultException])
  def handleResult(gridResult: GridResult): Future[Unit]

  /** Contains all cleanup operations before closing this sink. Should be
    * blocking to ensure that everything inside a buffer or similar is written
    * out.
    */
  def close(): Unit

}
