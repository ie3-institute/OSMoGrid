/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package de.osmogrid.interfaces;

/**
 * Interface of the MissionControl.
 *
 * @author hiry
 * @version 0.1
 * @since 26.07.2018
 */
public interface MissionControlOsmogrid {
  void startGridGeneration(String configFilePath);
}
