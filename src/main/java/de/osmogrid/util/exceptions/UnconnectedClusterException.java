/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package de.osmogrid.util.exceptions;

/**
 * Thrown to indicate that a cluster is not fully connected.
 *
 * @author Mahr
 * @since 17.12.2018
 */
public class UnconnectedClusterException extends Exception {

  public UnconnectedClusterException(String message) {
    super(message);
  }
}
