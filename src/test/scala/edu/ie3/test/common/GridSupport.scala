/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.test.common

import edu.ie3.datamodel.models.{OperationTime, StandardUnits}
import edu.ie3.datamodel.models.input.{
  MeasurementUnitInput,
  NodeInput,
  OperatorInput
}
import edu.ie3.datamodel.models.input.connector.{
  LineInput,
  SwitchInput,
  Transformer2WInput,
  Transformer3WInput
}
import edu.ie3.datamodel.models.input.container.{
  GraphicElements,
  RawGridElements,
  SubGridContainer,
  SystemParticipants
}
import edu.ie3.datamodel.models.input.graphics.{
  LineGraphicInput,
  NodeGraphicInput
}
import edu.ie3.datamodel.models.input.system.{
  BmInput,
  ChpInput,
  EvInput,
  EvcsInput,
  FixedFeedInInput,
  HpInput,
  LoadInput,
  PvInput,
  StorageInput,
  WecInput
}
import edu.ie3.datamodel.models.voltagelevels.GermanVoltageLevelUtils
import org.locationtech.jts.geom.Point
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar.mock
import tech.units.indriya.quantity.Quantities

import java.util.UUID
import scala.jdk.CollectionConverters._

trait GridSupport {
  protected def mockSubGrid(subnetNo: Int): SubGridContainer = {
    val mockedRawGrid = mock[RawGridElements]

    // include at least a single node for voltage level determination
    val dummyNodeInput = new NodeInput(
      UUID.randomUUID(),
      "Dummy node",
      mock[OperatorInput],
      mock[OperationTime],
      Quantities.getQuantity(1.0d, StandardUnits.TARGET_VOLTAGE_MAGNITUDE),
      false,
      mock[Point],
      GermanVoltageLevelUtils.LV,
      subnetNo
    )
    when(mockedRawGrid.getNodes).thenReturn(Set(dummyNodeInput).asJava)

    when(mockedRawGrid.getLines).thenReturn(Set.empty[LineInput].asJava)
    when(mockedRawGrid.getTransformer2Ws).thenReturn(
      Set.empty[Transformer2WInput].asJava
    )
    when(mockedRawGrid.getTransformer3Ws).thenReturn(
      Set.empty[Transformer3WInput].asJava
    )
    when(mockedRawGrid.getSwitches).thenReturn(Set.empty[SwitchInput].asJava)
    when(mockedRawGrid.getMeasurementUnits).thenReturn(
      Set.empty[MeasurementUnitInput].asJava
    )

    val mockedParticipants = new SystemParticipants(
      Set.empty[BmInput].asJava,
      Set.empty[ChpInput].asJava,
      Set.empty[EvcsInput].asJava,
      Set.empty[EvInput].asJava,
      Set.empty[FixedFeedInInput].asJava,
      Set.empty[HpInput].asJava,
      Set.empty[LoadInput].asJava,
      Set.empty[PvInput].asJava,
      Set.empty[StorageInput].asJava,
      Set.empty[WecInput].asJava
    )

    val mockedGraphics = new GraphicElements(
      Set.empty[NodeGraphicInput].asJava,
      Set.empty[LineGraphicInput].asJava
    )

    val mockedSubGrid = mock[SubGridContainer]
    when(mockedSubGrid.getGridName).thenReturn(s"DummyGrid")
    when(mockedSubGrid.getSubnet).thenReturn(subnetNo)
    when(mockedSubGrid.getRawGrid).thenReturn(mockedRawGrid)
    when(mockedSubGrid.getSystemParticipants).thenReturn(mockedParticipants)
    when(mockedSubGrid.getGraphics).thenReturn(mockedGraphics)

    mockedSubGrid
  }
}
