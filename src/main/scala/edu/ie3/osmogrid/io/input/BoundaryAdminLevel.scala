/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.io.input

import scala.language.implicitConversions

object BoundaryAdminLevel extends Enumeration {

  case class BoundaryAdminLevelValue(osmLevel: Int) extends super.Val {

    /** Returns the next boundary level below the current one if one exists
      * which corresponds to the boundary admin level with the next higher osm
      * level.
      *
      * @return
      *   an optional boundary admin level value
      */
    def nextLowerLevel(): Option[BoundaryAdminLevelValue] =
      BoundaryAdminLevel.values.filter(_.osmLevel > this.osmLevel).minOption
  }

  implicit def valueToBoundaryAdminLevelValue(
      x: Value
  ): BoundaryAdminLevelValue = x.asInstanceOf[BoundaryAdminLevelValue]

  implicit def optionalValueToOptionalBoundaryAdminLevelValue(
      x: Option[Value]
  ): Option[BoundaryAdminLevelValue] = x match {
    case Some(value) => Some(value.asInstanceOf[BoundaryAdminLevelValue])
    case None        => None
  }

  /** National border (NUTS 0)
    */
  val NATION_LEVEL: BoundaryAdminLevelValue = BoundaryAdminLevelValue(2)

  /** Federal states border (Bundesland) (NUTS 1)
    */
  val FEDERAL_STATE_LEVEL: BoundaryAdminLevelValue = BoundaryAdminLevelValue(4)

  /** State-district border (Regierungsbezirk) (NUTS 2)
    */
  val STATE_DISTRICT_LEVEL: BoundaryAdminLevelValue = BoundaryAdminLevelValue(5)

  /** County borders (Landkreis / Kreis / kreisfreie Stadt / Stadtkreis) (NUTS
    * 3)
    */
  val COUNTY_LEVEL: BoundaryAdminLevelValue = BoundaryAdminLevelValue(6)

  /** Amt (Amtsgemeinde, Verwaltungsgemeinschaft) (LAU 1/NUTS 4)
    */
  val AMT_LEVEL: BoundaryAdminLevelValue = BoundaryAdminLevelValue(7)

  /** Towns, Municipalities / City-districts (Stadt, Gemeinde) (LAU 2/NUTS 5)
    */
  val MUNICIPALITY_LEVEL: BoundaryAdminLevelValue = BoundaryAdminLevelValue(8)

  /** Parts of a municipality with parish councils / self government
    * (Stadtbezirk/Gemeindeteil mit Selbstverwaltung)
    */
  val SUBURB_1_LEVEL: BoundaryAdminLevelValue = BoundaryAdminLevelValue(9)

  /** Parts of a municipality without parish councils / self government
    * (Stadtteil/Gemeindeteil ohne Selbstverwaltung)
    */
  val SUBURB_2_LEVEL: BoundaryAdminLevelValue = BoundaryAdminLevelValue(10)

  /** Neighbourhoods, statistical or historical (Stadtviertel etc.)
    */
  val SUBURB_3_LEVEL: BoundaryAdminLevelValue = BoundaryAdminLevelValue(11)

  /** Statistical level (census)
    */
  val SUBURB_4_LEVEL: BoundaryAdminLevelValue = BoundaryAdminLevelValue(12)

  def get(osmLevel: Int): Option[BoundaryAdminLevelValue] = {
    BoundaryAdminLevel.values.find(_.osmLevel == osmLevel)
  }
}
