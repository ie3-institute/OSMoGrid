/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.graph

import java.util.Objects
import javax.measure.Quantity
import javax.measure.quantity.Power
import net.morbz.osmonaut.osm.LatLon
import net.morbz.osmonaut.osm.Node
import net.morbz.osmonaut.osm.Tags
import net.morbz.osmonaut.util.StringUtil

/** Implementation of {@link Node} that extends {@link Node} with information
  * about load, house connection point, substation and cluster belonging.
  *
  * @author
  *   Mahr
  * @since 17.12.2018
  */
@SerialVersionUID(-7214964092396827554L)
class OsmGridNode() extends Node {
  private var load = null
  private var houseConnectionPoint = null
  private var subStation = false
  private var cluster = -1
//  def this(id: Long, tags: Tags, latLon: LatLon) = { this()
//    super(id, tags, latLon)
//  }
//  def this(n: Node) = { this()
//    super(n.getId, n.getTags, n.getLatlon)
//  }
  def getLoad: Quantity[Power] = load
//  def setLoad(load: Quantity[Power]): Unit =  { this.load = load
//  }
  def getHouseConnectionPoint: LatLon = houseConnectionPoint
//  def setHouseConnectionPoint(houseConnectionPoint: LatLon): Unit =  { this.houseConnectionPoint = houseConnectionPoint
//  }
  def isSubStation: Boolean = subStation
//  def setSubStation(subStation: Boolean): Unit =  { this.subStation = subStation
//  }
  def getCluster: Int = cluster
//  def setCluster(cluster: Int): Unit =  { this.cluster = cluster
//  }
  override def toString: String = {
    var str = ""
    str += "{" + "\t" + "NODE" + "\n"
    str += "\t" + "id: " + id + "\n"
    str += "\t" + "latlon: " + getLatlon + "\n"
    str += "\t" + "tags: " + StringUtil.indent(getTags.toString)
    if (getLoad != null)
      str += "\t" + "load: " + StringUtil.indent(getLoad.toString)
    else str += "\t" + "load: null"
    if (getHouseConnectionPoint != null)
      str += "\t" + "houseConnectionPoint: " + StringUtil.indent(
        houseConnectionPoint.toString
      )
    else str += "\t" + "houseConnectionPoint: null"
    str += "\t" + "sub station: " + StringUtil.indent(
      String.valueOf(isSubStation)
    )
    str += "\t" + "cluster: " + StringUtil.indent(String.valueOf(getCluster))
    str += "}"
    str
  }
//  override def equals(obj: Any): Boolean = super.equals(obj)
//  override def hashCode: Int = Objects.hash(id)
}
