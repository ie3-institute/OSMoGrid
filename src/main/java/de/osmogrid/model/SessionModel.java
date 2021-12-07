/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package de.osmogrid.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Model class for the session.
 *
 * @author Mahr
 * @version 0.1
 * @since 17.12.2018
 */
public class SessionModel {

  // TODO: implement

  private static final Logger logger = LoggerFactory.getLogger(SessionModel.class);

  private String sessionName;

  public SessionModel(String sessionName) {
    super();
    this.sessionName = sessionName;
  }

  public String getSessionName() {
    return sessionName;
  }

  public void setSessionName(String sessionName) {
    this.sessionName = sessionName;
  }
}
