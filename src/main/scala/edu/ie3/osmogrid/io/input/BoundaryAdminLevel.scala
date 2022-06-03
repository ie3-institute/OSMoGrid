/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.io.input

import edu.ie3
import edu.ie3.osmogrid
import edu.ie3.osmogrid.io
import edu.ie3.osmogrid.io.input

import scala.util.Try

/** Represents the levels of
  * [[https://wiki.openstreetmap.org/wiki/Tag:boundary%3Dadministrative administrative boundaries]]
  * in OSM
  */
sealed trait BoundaryAdminLevel {
  def osmLevel: Int
}

object BoundaryAdminLevel extends Enumeration {

  /** National border (NUTS 0)
    */
  val NATION_LEVEL: BoundaryAdminLevel.Value = Value(2)

  /** Federal states border (Bundesland) (NUTS 1)
    */
  val FEDERAL_STATE_LEVEL: BoundaryAdminLevel.Value = Value(4)

  /** State-district border (Regierungsbezirk) (NUTS 2)
    */
  val STATE_DISTRICT_LEVEL: BoundaryAdminLevel.Value = Value(5)

  /** County borders (Landkreis / Kreis / kreisfreie Stadt / Stadtkreis) (NUTS
    * 3)
    */
  val COUNTY_LEVEL: BoundaryAdminLevel.Value = Value(6)

  /** Amt (Amtsgemeinde, Verwaltungsgemeinschaft) (LAU 1/NUTS 4)
    */
  val AMT_LEVEL: io.input.BoundaryAdminLevel.Value = Value(7)

  /** Towns, Municipalities / City-districts (Stadt, Gemeinde) (LAU 2/NUTS 5)
    */
  val MUNICIPALITY_LEVEL: osmogrid.io.input.BoundaryAdminLevel.Value = Value(8)

  /** Parts of a municipality with parish councils / self government
    * (Stadtbezirk/Gemeindeteil mit Selbstverwaltung)
    */
  val SUBURB_1_LEVEL: ie3.osmogrid.io.input.BoundaryAdminLevel.Value = Value(9)

  /** Parts of a municipality without parish councils / self government
    * (Stadtteil/Gemeindeteil ohne Selbstverwaltung)
    */
  val SUBURB_2_LEVEL
      : _root_.edu.ie3.osmogrid.io.input.BoundaryAdminLevel.Value = Value(10)

  /** Neighbourhoods, statistical or historical (Stadtviertel etc.)
    */
  val SUBURB_3_LEVEL
      : _root_.edu.ie3.osmogrid.io.input.BoundaryAdminLevel.Value = Value(11)

  def get(osmLevel: Int): Option[BoundaryAdminLevel.Value] = {
    BoundaryAdminLevel.values.find(_.id == osmLevel)
  }

  def nextLowerLevel(
      boundaryAdminLevel: BoundaryAdminLevel.Value
  ): Option[BoundaryAdminLevel.Value] =
    BoundaryAdminLevel.values.filter(_.id > boundaryAdminLevel.id).minOption
}
