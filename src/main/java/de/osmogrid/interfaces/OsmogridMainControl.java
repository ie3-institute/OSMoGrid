/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package de.osmogrid.interfaces;

import java.io.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main interface to control an OSMoGrid instance.
 *
 * @author Mahr
 * @version 0.1
 * @since 17.12.2018
 */
public interface OsmogridMainControl {

  Logger logger = LoggerFactory.getLogger(OsmogridMainControl.class);

  /**
   * Builds the configuration class.
   *
   * @param configFile Indicates which config file to use.
   */
  void setup(File configFile);

  /** Starts generating the grid. */
  void start();

  /** Shuts down OSMoGrid properly. */
  void shutdown();

  /** Get the current status of the session. */
  void getSessionStatus();
}
