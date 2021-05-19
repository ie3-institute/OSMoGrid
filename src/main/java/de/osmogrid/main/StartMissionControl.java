/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
*/
package de.osmogrid.main;

import de.osmogrid.interfaces.MissionControlOsmogrid;
import picocli.CommandLine;

/**
 * Main class to start a MissionControl by its wanted implementation
 *
 * @author hiry
 * @version 0.1
 * @since 24.07.2018
 */
public class StartMissionControl {

  /**
   * @param args Pass "generate [name]" as parameters to start the application properly (i.e.
   *     "generate fuerweiler").
   */
  public static void main(String[] args) {
    MissionControlOsmogrid mc;
    mc = new MissionControlOsmogridStandalone();
    new CommandLine(mc).execute(args);
  }
}
