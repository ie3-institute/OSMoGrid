/*
 * © 2019. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package de.osmogrid.util.exceptions;

/**
 * Thrown during clustering algorithm to indicate that a cluster does not contain any nodes anymore.
 *
 * @author Mahr
 * @since 17.12.2018
 */
public class EmptyClusterException extends Exception {

  public EmptyClusterException(String message) {
    super(message);
  }
}
