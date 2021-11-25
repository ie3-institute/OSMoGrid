/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package de.osmogrid.model.graph;

import java.util.Objects;
import javax.measure.Quantity;
import javax.measure.quantity.Power;
import net.morbz.osmonaut.osm.LatLon;
import net.morbz.osmonaut.osm.Node;
import net.morbz.osmonaut.osm.Tags;
import net.morbz.osmonaut.util.StringUtil;

/**
 * Implementation of {@link Node} that extends {@link Node} with information about load, house
 * connection point, substation and cluster belonging.
 *
 * @author Mahr
 * @since 17.12.2018
 */
public class OsmGridNode extends Node {

  private static final long serialVersionUID = -7214964092396827554L;

  private Quantity<Power> load;
  private LatLon houseConnectionPoint;
  private boolean subStation;
  private int cluster = -1;

  public OsmGridNode() {
    super();
  }

  public OsmGridNode(long id, Tags tags, LatLon latLon) {
    super(id, tags, latLon);
  }

  public OsmGridNode(Node n) {
    super(n.getId(), n.getTags(), n.getLatlon());
  }

  public Quantity<Power> getLoad() {
    return load;
  }

  public void setLoad(Quantity<Power> load) {
    this.load = load;
  }

  public LatLon getHouseConnectionPoint() {
    return houseConnectionPoint;
  }

  public void setHouseConnectionPoint(LatLon houseConnectionPoint) {
    this.houseConnectionPoint = houseConnectionPoint;
  }

  public boolean isSubStation() {
    return subStation;
  }

  public void setSubStation(boolean subStation) {
    this.subStation = subStation;
  }

  public int getCluster() {
    return cluster;
  }

  public void setCluster(int cluster) {
    this.cluster = cluster;
  }

  @Override
  public String toString() {
    String str = "";
    str += "{" + "\t" + "NODE" + "\n";
    str += "\t" + "id: " + id + "\n";
    str += "\t" + "latlon: " + getLatlon() + "\n";
    str += "\t" + "tags: " + StringUtil.indent(getTags().toString());
    if (getLoad() != null) {
      str += "\t" + "load: " + StringUtil.indent(getLoad().toString());
    } else {
      str += "\t" + "load: null";
    }
    if (getHouseConnectionPoint() != null) {
      str += "\t" + "houseConnectionPoint: " + StringUtil.indent(houseConnectionPoint.toString());
    } else {
      str += "\t" + "houseConnectionPoint: null";
    }
    str += "\t" + "sub station: " + StringUtil.indent(String.valueOf(isSubStation()));
    str += "\t" + "cluster: " + StringUtil.indent(String.valueOf(getCluster()));
    str += "}";
    return str;
  }

  @Override
  public boolean equals(Object obj) {
    return super.equals(obj);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
