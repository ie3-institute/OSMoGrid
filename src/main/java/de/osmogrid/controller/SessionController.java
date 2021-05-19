/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
*/
package de.osmogrid.controller;

import de.osmogrid.config.OsmogridConfig;
import de.osmogrid.controller.graph.GraphController;
import de.osmogrid.controller.grid.GridController;
import de.osmogrid.model.graph.DistanceWeightedOsmEdge;
import de.osmogrid.model.graph.OsmGridNode;
import de.osmogrid.util.enums.TargetFormat;
import edu.ie3.datamodel.models.input.container.JointGridContainer;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jgrapht.graph.AsSubgraph;

/**
 * Controls all operations on the session.
 *
 * @author Mahr
 * @since 17.12.2018
 */
public class SessionController {

  private static final Logger logger = LogManager.getLogger(SessionController.class);

  public void initialize(OsmogridConfig osmogridConfig) {

    long tStart = System.currentTimeMillis();
    logger.debug("Starting session for \"{}\", begin initialization", osmogridConfig.runtime.name);

    GraphController graphController = new GraphController();
    graphController.initialize(osmogridConfig);
    logger.info("Start building the graph ...");
    List<AsSubgraph<OsmGridNode, DistanceWeightedOsmEdge>> graphModel =
        graphController.generateGraph();

    GridController gridController = new GridController();
    gridController.initialize(osmogridConfig);
    logger.info("Start building the grid ...");
    JointGridContainer gridModel = gridController.generateGrid(graphModel);

    if (osmogridConfig.io.write) {
      writeResult(gridModel, TargetFormat.valueOf(osmogridConfig.io.typeSourceFormat));
    }

    // consider using System.nanoTime() to get duration of JVM attended time (depends on what is
    // desired here)
    long tEnd = System.currentTimeMillis();
    logger.info("Finished. Took me {} seconds.", (tEnd - tStart) / 1000d);
  }

  private void writeResult(JointGridContainer jointGridContainer, TargetFormat targetFormat) {
    if (targetFormat.equals(TargetFormat.CSV)) {
      logger.info("CSV writing not yet implemented.");
    } else {
      logger.error("Unknown target format.");
    }
  }
}
