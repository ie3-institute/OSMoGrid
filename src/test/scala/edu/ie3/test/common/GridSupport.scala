/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.test.common

import edu.ie3.datamodel.models.BdewLoadProfile
import edu.ie3.datamodel.models.input.connector.`type`.{
  LineTypeInput,
  Transformer3WTypeInput
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
import edu.ie3.datamodel.models.input.system.characteristic.{
  OlmCharacteristicInput,
  ReactivePowerCharacteristic
}
import edu.ie3.datamodel.models.input.system._
import edu.ie3.datamodel.models.input.{MeasurementUnitInput, NodeInput}
import edu.ie3.datamodel.models.voltagelevels.GermanVoltageLevelUtils
import edu.ie3.datamodel.models.StandardUnits
import edu.ie3.datamodel.utils.GridAndGeoUtils
import edu.ie3.util.geo.GeoUtils
import edu.ie3.util.quantities.PowerSystemUnits._
import org.locationtech.jts.geom.Point
import org.scalatestplus.mockito.MockitoSugar.mock
import tech.units.indriya.quantity.Quantities
import tech.units.indriya.unit.Units.PERCENT

import java.util.UUID
import scala.jdk.CollectionConverters._

trait GridSupport {

  /** Return a mocked test grid with given sub grid number
    * @param subgridNo
    *   the subgrid number
    * @return
    *   a test grid with given sub grid number
    */
  protected def mockSubGrid(subgridNo: Int): SubGridContainer = {
    // include at least a single node for voltage level determination
    val dummyNodeInput = new NodeInput(
      UUID.randomUUID(),
      "Dummy node",
      Quantities.getQuantity(1.0d, StandardUnits.TARGET_VOLTAGE_MAGNITUDE),
      false,
      mock[Point],
      GermanVoltageLevelUtils.LV,
      subgridNo
    )

    val rawGrid = new RawGridElements(
      Set(dummyNodeInput).asJava,
      Set.empty[LineInput].asJava,
      Set.empty[Transformer2WInput].asJava,
      Set.empty[Transformer3WInput].asJava,
      Set.empty[SwitchInput].asJava,
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

    new SubGridContainer(
      "DummyGrid",
      subgridNo,
      rawGrid,
      mockedParticipants,
      mockedGraphics
    )
  }

  /** Return a simple test grid with given sub grid number
    * @param subgridNo
    *   the subgrid number
    * @return
    *   a test grid with given sub grid number
    */
  protected def simpleSubGrid(subgridNo: Int): SubGridContainer = {

    // include at least a single node for voltage level determination
    val nodeA = new NodeInput(
      UUID.randomUUID(),
      "Node A",
      Quantities.getQuantity(1.0d, StandardUnits.TARGET_VOLTAGE_MAGNITUDE),
      false,
      GeoUtils.buildPoint(51.49249, 7.41105),
      GermanVoltageLevelUtils.LV,
      subgridNo
    )
    val nodeB = new NodeInput(
      UUID.randomUUID(),
      "Node B",
      Quantities.getQuantity(1.0d, StandardUnits.TARGET_VOLTAGE_MAGNITUDE),
      false,
      GeoUtils.buildPoint(51.49276, 7.41657),
      GermanVoltageLevelUtils.LV,
      subgridNo
    )
    val topNode1 = new NodeInput(
      UUID.randomUUID(),
      "Top node 1",
      Quantities.getQuantity(1.0d, StandardUnits.TARGET_VOLTAGE_MAGNITUDE),
      false,
      mock[Point],
      GermanVoltageLevelUtils.MV_10KV,
      subgridNo
    )
    val topNode2 = new NodeInput(
      UUID.randomUUID(),
      "Top node 2",
      Quantities.getQuantity(1.0d, StandardUnits.TARGET_VOLTAGE_MAGNITUDE),
      false,
      mock[Point],
      GermanVoltageLevelUtils.MV_20KV,
      subgridNo
    )

    val lineInput = new LineInput(
      UUID.randomUUID(),
      "Line 1",
      nodeA,
      nodeB,
      1,
      mock[LineTypeInput],
      Quantities.getQuantity(0.01d, KILOMETRE),
      GridAndGeoUtils.buildSafeLineStringBetweenNodes(nodeA, nodeB),
      OlmCharacteristicInput.CONSTANT_CHARACTERISTIC
    )

    lineInput.copy().nodeA(topNode1).nodeB(topNode2).build()

    val transformer3W = new Transformer3WInput(
      UUID.randomUUID(),
      "Transformer (3W)",
      nodeA,
      topNode1,
      topNode2,
      1,
      mock[Transformer3WTypeInput],
      0,
      false
    )

    val rawGrid = new RawGridElements(
      Set(nodeA, nodeB, topNode1, topNode2).asJava,
      Set(lineInput).asJava,
      Set.empty[Transformer2WInput].asJava,
      Set(transformer3W).asJava,
      Set.empty[SwitchInput].asJava,
      Set.empty[MeasurementUnitInput].asJava
    )

    val loadInput = new LoadInput(
      UUID.randomUUID(),
      "Load",
      nodeB,
      mock[ReactivePowerCharacteristic],
      BdewLoadProfile.H0,
      false,
      Quantities.getQuantity(3000d, KILOWATTHOUR),
      Quantities.getQuantity(10d, KILOWATT),
      0.95d
    )

    val pvInput = new PvInput(
      UUID.randomUUID(),
      "PV",
      nodeA,
      mock[ReactivePowerCharacteristic],
      0d,
      Quantities.getQuantity(0d, DEGREE_GEOM),
      Quantities.getQuantity(100d, PERCENT),
      Quantities.getQuantity(0d, DEGREE_GEOM),
      1d,
      1d,
      false,
      Quantities.getQuantity(1, KILOWATT),
      0.9d
    )

    val participants = new SystemParticipants(
      Set.empty[BmInput].asJava,
      Set.empty[ChpInput].asJava,
      Set.empty[EvcsInput].asJava,
      Set.empty[EvInput].asJava,
      Set.empty[FixedFeedInInput].asJava,
      Set.empty[HpInput].asJava,
      Set(loadInput).asJava,
      Set(pvInput).asJava,
      Set.empty[StorageInput].asJava,
      Set.empty[WecInput].asJava
    )

    val mockedGraphics = new GraphicElements(
      Set.empty[NodeGraphicInput].asJava,
      Set.empty[LineGraphicInput].asJava
    )

    new SubGridContainer(
      "SimpleGrid",
      subgridNo,
      rawGrid,
      participants,
      mockedGraphics
    )
  }
}
