/*
 * © 2020. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
*/
package de.osmogrid.main;

import de.osmogrid.interfaces.MissionControlOsmogrid;
import java.io.File;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * MissionControl implementation including OSMoGrid and picocli to serve as a standalone
 * implementation of OSMoGrid with a CLI.
 *
 * @author mahr
 * @version 0.1
 * @since 17.12.2018
 */
@Command(
    name = "osmogrid",
    description = "Starts OSMoGrid",
    mixinStandardHelpOptions = true,
    header = {
      "\n"
          + "\n"
          + " ██████╗ ███████╗███╗   ███╗ ██████╗  ██████╗ ██████╗ ██╗██████╗ \n"
          + "██╔═══██╗██╔════╝████╗ ████║██╔═══██╗██╔════╝ ██╔══██╗██║██╔══██╗\n"
          + "██║   ██║███████╗██╔████╔██║██║   ██║██║  ███╗██████╔╝██║██║  ██║\n"
          + "██║   ██║╚════██║██║╚██╔╝██║██║   ██║██║   ██║██╔══██╗██║██║  ██║\n"
          + "╚██████╔╝███████║██║ ╚═╝ ██║╚██████╔╝╚██████╔╝██║  ██║██║██████╔╝\n"
          + " ╚═════╝ ╚══════╝╚═╝     ╚═╝ ╚═════╝  ╚═════╝ ╚═╝  ╚═╝╚═╝╚═════╝ \n"
          + "\n"
    },
    footer = {
      "\n\n© 2019 Technische Universität Dortmund",
      "Institut für Energiesysteme, Energieeffizienz und Energiewirtschaft",
      "Forschungsgruppe Verteilnetzplanung und -betrieb"
    })
public class MissionControlOsmogridStandalone implements MissionControlOsmogrid {

  public static final Logger logger = LogManager.getLogger(MissionControlOsmogridStandalone.class);

  /**
   * Starts the grid generation.
   *
   * @param configFilePath The path to the config file.
   */
  @Command(name = "--config", description = "Starts grid generation using the given config file.")
  @Override
  public void startGridGeneration(@Parameters String configFilePath) {
    File configFile = new File(configFilePath);
    if (configFile.exists()) {
      logger.debug(
          "CLI input started grid generation method with configFilePath = {}", configFilePath);
      // create, initialize and start a new MainController object
      logger.info("Starting OSMoGrid ...");
      MainController mainController = new MainController();
      mainController.setup(configFile);
      mainController.start();
    } else {
      logger.error("Invalid path to config file");
      System.exit(1);
    }
  }
}
