/*
 * © 2026. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.model

import edu.ie3.datamodel.models.input.connector.LineInput
import edu.ie3.datamodel.models.input.system.LoadInput
import edu.ie3.datamodel.models.input.{AssetInput, NodeInput}

object GridData {

  final case class LvGridData(
      substation: NodeInput,
      assets: Seq[? <: AssetInput],
  )

  final case class MvGridData(
      mvToLv: Seq[(NodeInput, NodeInput)],
      hvToMv: Seq[(NodeInput, NodeInput)],
      assets: Seq[? <: AssetInput],
  )

  final case class HvGridData(substation: NodeInput)
}
