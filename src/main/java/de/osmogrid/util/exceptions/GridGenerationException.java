/*
 * © 2020. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
*/
package de.osmogrid.util.exceptions;

/**
 * Thrown to indicate an error during the grid generation.
 *
 * @author Kittl
 * @since 26.04.2018
 */
public class GridGenerationException extends Throwable {
  private static final long serialVersionUID = 1269694751531161934L;

  public GridGenerationException(String message) {
    super(message);
  }
}
