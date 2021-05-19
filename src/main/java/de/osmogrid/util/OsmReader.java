/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
*/
package de.osmogrid.util;

import java.util.ArrayList;
import java.util.List;
import net.morbz.osmonaut.IOsmonautReceiver;
import net.morbz.osmonaut.osm.*;

/**
 * Reads all nodes and ways from a .pbf file.
 *
 * @author Mahr
 * @since 17.12.2018
 */
public class OsmReader implements IOsmonautReceiver {

  private List<Node> nodes = new ArrayList<>();
  private List<Way> ways = new ArrayList<>();

  public List<Node> getNodes() {
    return this.nodes;
  }

  public List<Way> getWays() {
    return this.ways;
  }

  @Override
  public void foundEntity(Entity entity) {
    if (entity.getEntityType() == EntityType.NODE) {
      nodes.add((Node) entity);
    } else if (entity.getEntityType() == EntityType.WAY) {
      ways.add((Way) entity);
    }
  }

  @Override
  public boolean needsEntity(EntityType entityType, Tags tags) {
    return true;
  }
}
