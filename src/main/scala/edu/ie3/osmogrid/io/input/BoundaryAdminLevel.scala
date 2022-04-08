/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.io.input

/** Represents the levels of
  * [[https://wiki.openstreetmap.org/wiki/Tag:boundary%3Dadministrative administrative boundaries]]
  * in OSM
  */
enum BoundaryAdminLevel(val osmLevel: Int) extends Ordered[BoundaryAdminLevel] {

  /** National border (NUTS 0)
    */
  case NationLevel extends BoundaryAdminLevel(2)

  /** Federal states border (Bundesland) (NUTS 1)
    */
  case FederalStateLevel extends BoundaryAdminLevel(4)

  /** State-district border (Regierungsbezirk) (NUTS 2)
    */
  case StateDistrictLevel extends BoundaryAdminLevel(5)

  /** County borders (Landkreis / Kreis / kreisfreie Stadt / Stadtkreis) (NUTS
    * 3)
    */
  case CountyLevel extends BoundaryAdminLevel(6)

  /** Amt (Amtsgemeinde, Verwaltungsgemeinschaft) (LAU 1/NUTS 4)
    */
  case AmtLevel extends BoundaryAdminLevel(7)

  /** Towns, Municipalities / City-districts (Stadt, Gemeinde) (LAU 2/NUTS 5)
    */
  case MunicipalityLevel extends BoundaryAdminLevel(8)

  /** Parts of a municipality with parish councils / self government
    * (Stadtbezirk/Gemeindeteil mit Selbstverwaltung)
    */
  case Suburb1Level extends BoundaryAdminLevel(9)

  /** Parts of a municipality without parish councils / self government
    * (Stadtteil/Gemeindeteil ohne Selbstverwaltung)
    */
  case Suburb2Level extends BoundaryAdminLevel(10)

  /** Neighbourhoods, statistical or historical (Stadtviertel etc.)
    */
  case Suburb3Level extends BoundaryAdminLevel(11)

  def compare(other: BoundaryAdminLevel): Int =
    osmLevel compareTo other.osmLevel
}

object BoundaryAdminLevel {
  def apply(osmLevel: Int): Option[BoundaryAdminLevel] =
    BoundaryAdminLevel.values.find(_.osmLevel == osmLevel)

  def nextLowerLevel(
      boundaryAdminLevel: BoundaryAdminLevel
  ): Option[BoundaryAdminLevel] =
    BoundaryAdminLevel.values.filter(_ > boundaryAdminLevel).minOption
}
