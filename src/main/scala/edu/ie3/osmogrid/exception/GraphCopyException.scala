/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.exception

final case class GraphCopyException(
    msg: String = "Error during copying of graph.",
    cause: Throwable = None.orNull,
) extends Exception(msg, cause)
