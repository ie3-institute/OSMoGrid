/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.test.common

import edu.ie3.datamodel.models.StandardUnits
import edu.ie3.datamodel.models.input.connector.`type`.{
  LineTypeInput,
  Transformer2WTypeInput,
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
import edu.ie3.datamodel.models.input.system._
import edu.ie3.datamodel.models.input.system.characteristic.{
  OlmCharacteristicInput,
  ReactivePowerCharacteristic
}
import edu.ie3.datamodel.models.input.{MeasurementUnitInput, NodeInput}
import edu.ie3.datamodel.models.profile.BdewStandardLoadProfile
import edu.ie3.datamodel.models.voltagelevels.{
  CommonVoltageLevel,
  GermanVoltageLevelUtils
}
import edu.ie3.datamodel.utils.GridAndGeoUtils
import edu.ie3.osmogrid.io.input.AssetInformation
import edu.ie3.util.geo.GeoUtils._
import edu.ie3.util.quantities.PowerSystemUnits._
import edu.ie3.util.quantities.QuantityUtils.RichQuantityDouble
import org.locationtech.jts.geom.Point
import org.scalatestplus.mockito.MockitoSugar.mock
import tech.units.indriya.ComparableQuantity
import tech.units.indriya.quantity.Quantities
import tech.units.indriya.unit.Units.PERCENT

import java.util.UUID
import javax.measure.quantity._
import scala.jdk.CollectionConverters._

trait GridSupport {
  val defaultLineTypeMv = new LineTypeInput(
    UUID.fromString("6b223bc3-69e2-4eb8-a2c0-76be1cd2c998"),
    "NA2XS2Y 1x400 RM/25 6/10 kV",
    169.646.asSiemensPerKilometre,
    0.0.asSiemensPerKilometre,
    0.078.asOhmPerKilometre,
    0.0942.asOhmPerKilometre,
    535.0.asAmpere,
    10.0.asKiloVolt
  )

  val trafo_10kV_to_lv = new Transformer2WTypeInput(
    UUID.fromString("a0cbd90a-4e9f-47db-8dca-041d3a288f77"),
    "0.63 MVA 10/0.4 kV Dyn5 ASEA",
    1.7384731670445954.asOhm,
    9.36379511166658.asOhm,
    630.asKiloVoltAmpere,
    10.0.asKiloVolt,
    0.4.asKiloVolt,
    16500.0.asNanoSiemens,
    145.8952227629774.asNanoSiemens,
    2.5.asPercent,
    0.0.asDegreeGeom,
    false,
    0,
    -2,
    2
  )

  val trafo_20kV_to_lv = new Transformer2WTypeInput(
    UUID.fromString("0843b836-cee4-4a8c-81a4-098400fe91cf"),
    "0.4 MVA 20/0.4 kV Dyn5 ASEA",
    11.999999999999998.asOhm,
    58.787753826796276.asOhm,
    400.asKiloVoltAmpere,
    20.0.asKiloVolt,
    0.4.asKiloVolt,
    2999.9999999999995.asNanoSiemens,
    24.495101551166183.asNanoSiemens,
    2.5.asPercent,
    0.0.asDegreeGeom,
    false,
    0,
    -2,
    2
  )

  val dummyTransformer3WType: Transformer3WTypeInput =
    new Transformer3WTypeInput(
      UUID.randomUUID(),
      "dummy transformer3W type",
      mock[ComparableQuantity[Power]],
      mock[ComparableQuantity[Power]],
      mock[ComparableQuantity[Power]],
      20.asKiloVolt,
      20.asKiloVolt,
      0.4.asKiloVolt,
      mock[ComparableQuantity[ElectricResistance]],
      mock[ComparableQuantity[ElectricResistance]],
      mock[ComparableQuantity[ElectricResistance]],
      mock[ComparableQuantity[ElectricResistance]],
      mock[ComparableQuantity[ElectricResistance]],
      mock[ComparableQuantity[ElectricResistance]],
      mock[ComparableQuantity[ElectricConductance]],
      mock[ComparableQuantity[ElectricConductance]],
      mock[ComparableQuantity[Dimensionless]],
      mock[ComparableQuantity[Angle]],
      0,
      -2,
      2
    )

  val assetInformation: AssetInformation =
    AssetInformation(
      Seq(defaultLineTypeMv),
      Seq(trafo_10kV_to_lv, trafo_20kV_to_lv)
    )

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
      s"Dummy node in $subgridNo",
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
      Set.empty[WecInput].asJava,
      Set.empty[EmInput].asJava
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

  /** Return a mocked test grid with given sub grid number.
    * @param subgridNo
    *   the subgrid number
    * @param voltLvlA
    *   voltage level of the higher node
    * @param voltLvlB
    *   voltage level of the lower node
    * @return
    *   a mocked sub grid
    */
  protected def mockSubGrid(
      subgridNo: Int,
      voltLvlA: CommonVoltageLevel,
      voltLvlB: CommonVoltageLevel
  ): SubGridContainer = {
    // include at least a single node for voltage level determination
    val dummyNodeA = new NodeInput(
      UUID.randomUUID(),
      s"Dummy node in $subgridNo",
      Quantities.getQuantity(1.0d, StandardUnits.TARGET_VOLTAGE_MAGNITUDE),
      false,
      mock[Point],
      voltLvlA,
      subgridNo
    )

    val dummyNodeB = new NodeInput(
      UUID.randomUUID(),
      s"Dummy node in $subgridNo",
      Quantities.getQuantity(1.0d, StandardUnits.TARGET_VOLTAGE_MAGNITUDE),
      false,
      mock[Point],
      voltLvlB,
      subgridNo
    )

    val dummyTrafo = new Transformer2WInput(
      UUID.randomUUID(),
      s"Dummy transformer",
      dummyNodeA,
      dummyNodeB,
      1,
      mock[Transformer2WTypeInput],
      0,
      false
    )

    val rawGrid = new RawGridElements(
      Set(dummyNodeA, dummyNodeB).asJava,
      Set.empty[LineInput].asJava,
      Set(dummyTrafo).asJava,
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
      Set.empty[WecInput].asJava,
      Set.empty[EmInput].asJava
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

    // GRID //
    // include at least a single node for voltage level determination
    val nodeA = new NodeInput(
      UUID.randomUUID(),
      s"Node A in $subgridNo",
      Quantities.getQuantity(1.0d, StandardUnits.TARGET_VOLTAGE_MAGNITUDE),
      false,
      buildPoint(51.49249, 7.41105),
      GermanVoltageLevelUtils.LV,
      subgridNo
    )
    val nodeB = new NodeInput(
      UUID.randomUUID(),
      s"Node B in $subgridNo",
      Quantities.getQuantity(1.0d, StandardUnits.TARGET_VOLTAGE_MAGNITUDE),
      false,
      buildPoint(51.49276, 7.41657),
      GermanVoltageLevelUtils.LV,
      subgridNo
    )
    val nodeC = new NodeInput(
      UUID.randomUUID(),
      s"Node C in $subgridNo",
      Quantities.getQuantity(1.0d, StandardUnits.TARGET_VOLTAGE_MAGNITUDE),
      false,
      buildPoint(51.49350, 7.41605),
      GermanVoltageLevelUtils.LV,
      subgridNo
    )

    val topNode1 = new NodeInput(
      UUID.randomUUID(),
      s"Top node 1 in $subgridNo",
      Quantities.getQuantity(1.0d, StandardUnits.TARGET_VOLTAGE_MAGNITUDE),
      false,
      mock[Point],
      GermanVoltageLevelUtils.MV_10KV,
      subgridNo
    )
    val topNode2 = new NodeInput(
      UUID.randomUUID(),
      s"Top node 2 in $subgridNo",
      Quantities.getQuantity(1.0d, StandardUnits.TARGET_VOLTAGE_MAGNITUDE),
      false,
      mock[Point],
      GermanVoltageLevelUtils.MV_20KV,
      subgridNo
    )

    val lineInput = new LineInput(
      UUID.randomUUID(),
      s"Line 1 in $subgridNo",
      nodeA,
      nodeB,
      1,
      mock[LineTypeInput],
      Quantities.getQuantity(0.01d, KILOMETRE),
      GridAndGeoUtils.buildSafeLineStringBetweenNodes(nodeA, nodeB),
      OlmCharacteristicInput.CONSTANT_CHARACTERISTIC
    )

    val transformer2W = new Transformer2WInput(
      UUID.randomUUID(),
      s"Transformer (2W) in $subgridNo",
      topNode2,
      nodeC,
      1,
      mock[Transformer2WTypeInput],
      0,
      false
    )

    val transformer3W = new Transformer3WInput(
      UUID.randomUUID(),
      s"Transformer (3W) in $subgridNo",
      topNode2,
      topNode1,
      nodeA,
      1,
      mock[Transformer3WTypeInput],
      0,
      false
    )

    val switchInput = new SwitchInput(
      UUID.randomUUID(),
      s"Switch in $subgridNo",
      nodeB,
      nodeC,
      false
    )

    val measurementUnitInput = new MeasurementUnitInput(
      UUID.randomUUID(),
      s"Measurement unit in $subgridNo",
      nodeB,
      true,
      true,
      false,
      false
    )

    val rawGrid = new RawGridElements(
      Set(nodeA, nodeB, nodeC, topNode1, topNode2).asJava,
      Set(lineInput).asJava,
      Set(transformer2W).asJava,
      Set(transformer3W).asJava,
      Set(switchInput).asJava,
      Set(measurementUnitInput).asJava
    )

    // PARTICIPANTS //
    val loadInput = new LoadInput(
      UUID.randomUUID(),
      s"Load in $subgridNo",
      nodeB,
      mock[ReactivePowerCharacteristic],
      BdewStandardLoadProfile.H0,
      false,
      Quantities.getQuantity(3000d, KILOWATTHOUR),
      Quantities.getQuantity(10d, KILOWATT),
      0.95d
    )

    val pvInput = new PvInput(
      UUID.randomUUID(),
      s"PV in $subgridNo",
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
      Set.empty[WecInput].asJava,
      Set.empty[EmInput].asJava
    )

    // GRAPHICS (just mocked) //
    val mockedGraphics = new GraphicElements(
      Set.empty[NodeGraphicInput].asJava,
      Set.empty[LineGraphicInput].asJava
    )

    new SubGridContainer(
      "SubGrid",
      subgridNo,
      rawGrid,
      participants,
      mockedGraphics
    )
  }
}
