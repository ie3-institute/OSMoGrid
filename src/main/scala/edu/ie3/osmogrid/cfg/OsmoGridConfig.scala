/*
 * © 2024. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.cfg

import com.typesafe.config.Config
import edu.ie3.osmogrid.cfg.OsmoGridConfig.Input.Osm.{Filter, POI}
import edu.ie3.osmogrid.cfg.OsmoGridConfig.{Grids, Voltage}
import edu.ie3.osmogrid.exception.IllegalConfigException
import pureconfig.error.*
import pureconfig.generic.*
import pureconfig.generic.semiauto.deriveConvert
import pureconfig.*

import scala.deriving.Mirror

/** Configuration for OSMoGrid.
  * @param generation
  *   Subconfig for grid generation.
  * @param input
  *   Subconfig for input parameters.
  * @param output
  *   Subconfig for output parameters.
  * @param voltage
  *   Subconfig for voltage parameters.
  */
final case class OsmoGridConfig(
    generation: OsmoGridConfig.Generation = OsmoGridConfig.Generation(),
    input: OsmoGridConfig.Input = OsmoGridConfig.Input(),
    output: OsmoGridConfig.Output = OsmoGridConfig.Output(),
    voltage: OsmoGridConfig.Voltage = Voltage(),
) derives ConfigConvert
object OsmoGridConfig {
  // pure config start
  implicit def productHint[T]: ProductHint[T] =
    ProductHint[T](ConfigFieldMapping(CamelCase, CamelCase))

  extension (c: ConfigConvert.type)
    inline def derived[A](using m: Mirror.Of[A]): ConfigConvert[A] =
      deriveConvert[A]

  def apply(typeSafeConfig: Config): OsmoGridConfig =
    apply(ConfigSource.fromConfig(typeSafeConfig))

  def apply(confSrc: ConfigObjectSource): OsmoGridConfig =
    confSrc.load[OsmoGridConfig] match {
      case Left(readerFailures) =>
        val detailedErrors = readerFailures.toList
          .map {
            case CannotParse(msg, origin) =>
              f"CannotParse => $msg, Origin: $origin \n"
            case _: CannotRead =>
              f"CannotRead => Can not read config source} \n"
            case ConvertFailure(reason, _, path) =>
              f"ConvertFailure => Path: $path, Description: ${reason.description} \n"
            case ThrowableFailure(throwable, origin) =>
              f"ThrowableFailure => ${throwable.getMessage}, Origin: $origin \n"
            case failure =>
              f"Unknown failure type => ${failure.toString} \n"
          }
          .mkString("\n")
        throw IllegalConfigException(
          s"Unable to load config due to following failures:\n$detailedErrors"
        )
      case Right(conf) => conf
    }

  // pure config end

  /** Parameters for csv files.
    * @param directory
    *   Folder of the files.
    * @param hierarchic
    *   If the files are using hierarchical structure.
    * @param separator
    *   The separator to use.
    */
  final case class Csv(
      directory: String,
      hierarchic: Boolean = false,
      separator: String = ",",
  ) derives ConfigConvert

  /** Definition of a voltage level.
    * @param default
    *   The default rated voltage.
    * @param id
    *   The id of this level.
    * @param vNom
    *   Option for a list with more rated voltages (default: None).
    */
  case class VoltageLevel(
      default: Double,
      id: String,
      vNom: Option[List[Double]] = None,
  )

  /** Generation config parameters.
    * @param lv
    *   Option for parameters for low voltage generation (default: None).
    * @param mv
    *   Option for parameters for medium voltage generation (default: None).
    */
  final case class Generation(
      lv: Option[OsmoGridConfig.Generation.Lv] = None,
      mv: Option[OsmoGridConfig.Generation.Mv] = None,
  ) derives ConfigConvert

  object Generation {

    /** Parameters for low voltage generation.
      * @param averagePowerDensity
      *   The average power density to use.
      * @param boundaryAdminLevel
      *   Boundary admin configuration.
      * @param considerHouseConnectionPoints
      *   Should house connection points be considered (default: false).
      * @param loadSimultaneousFactor
      *   The load simultaneous factor to use (default: 0.2)
      * @param minDistance
      *   Minimal distance.
      */
    final case class Lv(
        averagePowerDensity: Double,
        boundaryAdminLevel: OsmoGridConfig.Generation.Lv.BoundaryAdminLevel =
          Lv.BoundaryAdminLevel(),
        considerHouseConnectionPoints: Boolean = false,
        loadSimultaneousFactor: Double = 0.2,
        minDistance: Double,
    ) derives ConfigConvert

    object Lv {

      final case class BoundaryAdminLevel(
          lowest: Int = 8,
          starting: Int = 2,
      ) derives ConfigConvert

    }

    final case class Mv(
        spawnMissingHvNodes: Boolean = true
    ) derives ConfigConvert

  }

  final case class Input(
      asset: OsmoGridConfig.Input.Asset = Input.Asset(),
      osm: OsmoGridConfig.Input.Osm = Input.Osm(),
  ) derives ConfigConvert

  object Input {

    final case class Asset(
        file: Option[OsmoGridConfig.Csv] = None
    ) derives ConfigConvert

    /** @param pbf
      *   The config for pbf files (default: None).
      * @param filter
      *   The open street map filters to use for grid generation (default:
      *   None).
      * @param poi
      *   The filters for points of interest (default: None).
      */
    final case class Osm(
        pbf: Option[OsmoGridConfig.Input.Osm.Pbf] = None,
        filter: Option[Filter] = None,
        poi: Option[POI] = None,
    ) derives ConfigConvert

    object Osm {

      final case class Pbf(
          file: String
      ) derives ConfigConvert

      final case class Filter(
          building: List[String] = Nil,
          highway: List[String] = Nil,
          landuse: List[String] = Nil,
      ) derives ConfigConvert

      final case class POI(
          home: Map[String, List[String]] = Map.empty,
          supermarket: Map[String, List[String]] = Map.empty,
          bbpq: Map[String, List[String]] = Map.empty,
          services: Map[String, List[String]] = Map.empty,
          culture: Map[String, List[String]] = Map.empty,
          medicinal: Map[String, List[String]] = Map.empty,
          religious: Map[String, List[String]] = Map.empty,
          restaurant: Map[String, List[String]] = Map.empty,
          sports: Map[String, List[String]] = Map.empty,
          otherShops: Map[String, List[String]] = Map.empty,
      ) derives ConfigConvert
    }
  }

  final case class Output(
      addTimestampToOutputDir: Boolean = true,
      csv: Option[OsmoGridConfig.Csv] = None,
      gridName: String = "",
      grids: Grids = Grids(),
  ) derives ConfigConvert

  /** Parameters for grid outputs.
    *
    * @param hv
    *   If high voltage grids should be written (default: false).
    * @param lv
    *   If low voltage grids should be written (default: false).
    * @param mv
    *   If medium voltage grids should be written (default: false).
    */
  final case class Grids(
      hv: Boolean = false,
      lv: Boolean = false,
      mv: Boolean = false,
  ) derives ConfigConvert

  /** Voltage level configuration.
    * @param hv
    *   Parameters for high voltage level.
    * @param mv
    *   Parameters for medium voltage level.
    * @param lv
    *   Parameters for low voltage level.
    */
  final case class Voltage(
      hv: VoltageLevel = VoltageLevel(110.0, "hv"),
      mv: VoltageLevel = VoltageLevel(10.0, "mv"),
      lv: VoltageLevel = VoltageLevel(0.4, "lv"),
  ) derives ConfigConvert

}
