/*
 * © 2019. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package de.osmogrid.controller.grid;

import de.osmogrid.config.OsmogridConfig;
import de.osmogrid.model.graph.OsmogridNode;
import de.osmogrid.util.GridUtils;
import de.osmogrid.util.enums.TypeSourceFormat;
import edu.ie3.datamodel.exceptions.VoltageLevelException;
import edu.ie3.datamodel.graph.DistanceWeightedEdge;
import edu.ie3.datamodel.io.FileNamingStrategy;
import edu.ie3.datamodel.io.source.TypeSource;
import edu.ie3.datamodel.io.source.csv.CsvTypeSource;
import edu.ie3.datamodel.models.BdewLoadProfile;
import edu.ie3.datamodel.models.input.NodeInput;
import edu.ie3.datamodel.models.input.connector.LineInput;
import edu.ie3.datamodel.models.input.connector.type.LineTypeInput;
import edu.ie3.datamodel.models.input.container.GraphicElements;
import edu.ie3.datamodel.models.input.container.JointGridContainer;
import edu.ie3.datamodel.models.input.container.RawGridElements;
import edu.ie3.datamodel.models.input.container.SubGridContainer;
import edu.ie3.datamodel.models.input.container.SystemParticipants;
import edu.ie3.datamodel.models.input.system.LoadInput;
import edu.ie3.datamodel.models.voltagelevels.CommonVoltageLevel;
import edu.ie3.datamodel.models.voltagelevels.GermanVoltageLevelUtils;
import edu.ie3.util.OneToOneMap;
import edu.ie3.util.quantities.PowerSystemUnits;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.measure.quantity.Dimensionless;
import javax.measure.quantity.ElectricPotential;
import javax.measure.quantity.Energy;
import javax.measure.quantity.Length;
import javax.measure.quantity.Power;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.linear.FieldMatrix;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jgrapht.graph.AsSubgraph;
import tec.uom.se.ComparableQuantity;
import tec.uom.se.quantity.Quantities;
import tec.uom.se.unit.Units;

/**
 * Controls all operations on the grid.
 *
 * @author Mahr
 * @since 17.12.2018
 */
public class GridController {

  public static final Logger logger = LogManager.getLogger(GridController.class);

  private static final Map<Integer, FieldMatrix<Complex>> admittanceMatrices = new HashMap<>();
  private static final Map<Integer, OneToOneMap<String, Integer>> nodeCodeMaps = new HashMap<>();
  private OsmogridConfig osmogridConfig;
  private LineTypeInput lineType;

  /** Initializes the GridController. */
  public void initialize(OsmogridConfig osmogridConfig) {
    this.osmogridConfig = osmogridConfig;

    loadTypes();
  }

  private JointGridContainer buildGridContainer(
      Set<NodeInput> nodes, Set<LineInput> lines, Set<LoadInput> loads) {
    RawGridElements rawGridElements =
        new RawGridElements(
            nodes, lines, new HashSet<>(), new HashSet<>(), new HashSet<>(), new HashSet<>());

    SystemParticipants systemParticipants =
        new SystemParticipants(
            new HashSet<>(),
            new HashSet<>(),
            new HashSet<>(),
            new HashSet<>(),
            new HashSet<>(),
            new HashSet<>(),
            loads,
            new HashSet<>(),
            new HashSet<>(),
            new HashSet<>());

    GraphicElements graphicElements = new GraphicElements(new HashSet<>(), new HashSet<>());

    return new JointGridContainer(
        osmogridConfig.runtime.name, rawGridElements, systemParticipants, graphicElements);
  }

  /**
   * Calls the methods that are necessary for building the complete electrical grid out of a
   * GraphModel.
   *
   * @param graphModel GraphModel that was generated in the GraphController.
   * @return Returns the complete electrical grid as a List of GridInputModels (one for each
   *     subnet).
   */
  public JointGridContainer generateGrid(
      List<AsSubgraph<OsmogridNode, DistanceWeightedEdge>> graphModel) {

    JointGridContainer gridModel = buildGrid(graphModel);

    // build node code maps and admittance matrices for each sub net
    for (SubGridContainer subGrid : gridModel.getSubGridTopologyGraph().vertexSet()) {
      OneToOneMap<String, Integer> nodeCodeMap =
          GridUtils.buildNodeCodeMap(subGrid.getRawGrid().getNodes());
      nodeCodeMaps.put(subGrid.getSubnet(), nodeCodeMap);

      FieldMatrix<Complex> admittanceMatrix =
          GridUtils.calcAdmittanceMatrix(
              nodeCodeMap,
              subGrid.getRawGrid().getLines(),
              Quantities.getQuantity(
                  osmogridConfig.grid.nominalPower, PowerSystemUnits.KILOVOLTAMPERE));
      admittanceMatrices.put(subGrid.getSubnet(), admittanceMatrix);
    }
    return gridModel;
  }

  /** Reads line types from csv files. Transformer types. */
  private void loadTypes() {
    TypeSource typeSource;
    TypeSourceFormat sourceFormat = TypeSourceFormat.valueOf(osmogridConfig.io.typeSourceFormat);

    if (osmogridConfig.io.readTypes) {
      if (sourceFormat == TypeSourceFormat.CSV) {
        typeSource = new CsvTypeSource(";", "", new FileNamingStrategy());

        lineType =
            typeSource.getLineTypes().stream()
                .filter(lineTypeInput -> lineTypeInput.getId().equals(osmogridConfig.grid.lineType))
                .findAny()
                .orElse(builtDefaultLineType());
      } else {
        logger.error("Invalid TypeSource in config file. Use standard LineTypeInput");
        lineType = builtDefaultLineType();
      }
    } else {
      logger.info("Use default line type.");
      lineType = builtDefaultLineType();
    }
  }

  private static LineTypeInput builtDefaultLineType() {
    return new LineTypeInput(
        UUID.randomUUID(),
        "Default generated line type",
        Quantities.getQuantity(0.0, PowerSystemUnits.SIEMENS_PER_KILOMETRE),
        Quantities.getQuantity(0.07, PowerSystemUnits.SIEMENS_PER_KILOMETRE),
        Quantities.getQuantity(0.32, PowerSystemUnits.OHM_PER_KILOMETRE),
        Quantities.getQuantity(0.07, PowerSystemUnits.OHM_PER_KILOMETRE),
        Quantities.getQuantity(235.0, Units.AMPERE),
        Quantities.getQuantity(0.4, PowerSystemUnits.KILOVOLT));
  }

  /**
   * Generates a GridInputModel for each sub graph in graphModel
   *
   * @param graphModel GraphModel from which the GridInputModels shall be generated
   */
  private JointGridContainer buildGrid(
      @NotNull List<AsSubgraph<OsmogridNode, DistanceWeightedEdge>> graphModel) {

    ComparableQuantity<ElectricPotential> vRated =
        Quantities.getQuantity(osmogridConfig.grid.ratedVoltage, PowerSystemUnits.KILOVOLT);
    ComparableQuantity<Dimensionless> vTarget = Quantities.getQuantity(1d, PowerSystemUnits.PU);

    CommonVoltageLevel voltLvl;
    try {
      voltLvl = GermanVoltageLevelUtils.parse(osmogridConfig.grid.voltageLevel, vRated);
    } catch (VoltageLevelException e) {
      voltLvl = GermanVoltageLevelUtils.LV;
      logger.error("Could not set voltage level from config file. Continue with {}", voltLvl, e);
    }

    // set id counters
    int nodeIdCounter = 1;
    int lineIdCounter = 1;
    int loadIdCounter = 1;
    int subNetCounter = 1;

    Set<NodeInput> nodeInputs = new HashSet<>();
    Set<LoadInput> loadInputs = new HashSet<>();
    Set<LineInput> lineInputs = new HashSet<>();

    for (AsSubgraph<OsmogridNode, DistanceWeightedEdge> subgraph : graphModel) {
      Map<OsmogridNode, NodeInput> geoGridNodesMap = new HashMap<>();

      for (OsmogridNode osmogridNode : subgraph.vertexSet()) {
        if (osmogridNode.getLoad() != null) {
          NodeInput nodeInput =
              new NodeInput(
                  UUID.randomUUID(),
                  "Node " + nodeIdCounter++,
                  vTarget,
                  osmogridNode.isSubStation(),
                  GridUtils.latLonToPointNew(osmogridNode.getLatlon()),
                  voltLvl,
                  subNetCounter);

          geoGridNodesMap.put(osmogridNode, nodeInput);
          nodeInputs.add(nodeInput);

          if (osmogridConfig.grid.considerHouseConnectionPoints) {
            // If parameter considerHouseConnectionPoints is set to true, create another NodeInput
            // at the nodes house connection point.
            NodeInput houseConnectionPoint =
                new NodeInput(
                    UUID.randomUUID(),
                    "Node " + nodeIdCounter++,
                    vTarget,
                    false,
                    GridUtils.latLonToPointNew(osmogridNode.getHouseConnectionPoint()),
                    voltLvl,
                    subNetCounter);

            // The load will then be set to the node that represents the house connection point.
            ComparableQuantity<Energy> eConsAnnual =
                Quantities.getQuantity(
                    osmogridNode.getLoad().toSystemUnit().getValue().doubleValue(),
                    PowerSystemUnits.KILOWATTHOUR);

            LoadInput loadInput =
                new LoadInput(
                    UUID.randomUUID(),
                    "Load " + loadIdCounter,
                    houseConnectionPoint,
                    null,
                    BdewLoadProfile.H0,
                    false,
                    eConsAnnual,
                    (ComparableQuantity<Power>)
                        osmogridNode.getLoad().to(PowerSystemUnits.KILOVOLTAMPERE),
                    1d);

            // Create a LineInput between the NodeInputs from the OsmogridNode and the house
            // connection point
            ComparableQuantity<Length> length =
                GridUtils.haversine(
                    nodeInput.getGeoPosition().getY(),
                    nodeInput.getGeoPosition().getX(),
                    houseConnectionPoint.getGeoPosition().getY(),
                    houseConnectionPoint.getGeoPosition().getX());

            LineInput lineInput =
                new LineInput(
                    UUID.randomUUID(),
                    "Line " + lineIdCounter++,
                    nodeInput,
                    houseConnectionPoint,
                    1,
                    lineType,
                    length,
                    GridUtils.lineStringFromNodeInputs(nodeInput, houseConnectionPoint),
                    null);

            lineInputs.add(lineInput);
            nodeInputs.add(houseConnectionPoint);
            loadInputs.add(loadInput);

          } else {
            // If parameter considerHouseConnectionPoints is set to false, solely create the load
            // and set it to the NodeInput.
            ComparableQuantity<Energy> eConsAnnual =
                Quantities.getQuantity(
                    osmogridNode.getLoad().toSystemUnit().getValue().doubleValue(),
                    PowerSystemUnits.KILOWATTHOUR);

            LoadInput loadInput =
                new LoadInput(
                    UUID.randomUUID(),
                    "Load " + loadIdCounter++,
                    nodeInput,
                    null,
                    BdewLoadProfile.H0,
                    false,
                    eConsAnnual,
                    (ComparableQuantity<Power>)
                        osmogridNode.getLoad().to(PowerSystemUnits.KILOVOLTAMPERE),
                    1d);
            loadInputs.add(loadInput);
          }
        } else if (subgraph.degreeOf(osmogridNode) > 2) {
          // If the node is an intersection, create a NodeInput
          NodeInput nodeInput =
              new NodeInput(
                  UUID.randomUUID(),
                  "Node " + nodeIdCounter++,
                  vTarget,
                  osmogridNode.isSubStation(),
                  GridUtils.latLonToPointNew(osmogridNode.getLatlon()),
                  voltLvl,
                  subNetCounter);

          geoGridNodesMap.put(osmogridNode, nodeInput);
          nodeInputs.add(nodeInput);
        }
      }

      // Depth-first-search for adding LineInputModels:
      LineBuilder lineBuilder = new LineBuilder(lineType);
      lineBuilder.initialize(subgraph, geoGridNodesMap, lineIdCounter);
      lineBuilder.start();

      // add all lines from Gridbuilder to list
      lineInputs.addAll(lineBuilder.getLineInputModels());

      // refresh lineIdCounter
      lineIdCounter = lineBuilder.getLineIdCounter();

      subNetCounter++;
    }

    // Build and return a JointGridContainer
    return buildGridContainer(nodeInputs, lineInputs, loadInputs);
  }
}
