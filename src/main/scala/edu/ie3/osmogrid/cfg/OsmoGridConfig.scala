/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.cfg

final case class OsmoGridConfig(
    generation: OsmoGridConfig.Generation,
    input: OsmoGridConfig.Input,
    output: OsmoGridConfig.Output
)
object OsmoGridConfig {
  final case class Generation(
      lv: scala.Option[OsmoGridConfig.Generation.Lv]
  )
  object Generation {
    final case class Lv(
        boundaryAdminLevel: OsmoGridConfig.Generation.Lv.BoundaryAdminLevel,
        distinctHouseConnections: scala.Boolean,
        osm: OsmoGridConfig.Generation.Lv.Osm
    )
    object Lv {
      final case class BoundaryAdminLevel(
          lowest: scala.Int,
          starting: scala.Int
      )
      object BoundaryAdminLevel {
        def apply(
            c: com.typesafe.config.Config,
            parentPath: java.lang.String,
            $tsCfgValidator: $TsCfgValidator
        ): OsmoGridConfig.Generation.Lv.BoundaryAdminLevel = {
          OsmoGridConfig.Generation.Lv.BoundaryAdminLevel(
            lowest = if (c.hasPathOrNull("lowest")) c.getInt("lowest") else 8,
            starting =
              if (c.hasPathOrNull("starting")) c.getInt("starting") else 2
          )
        }
      }

      final case class Osm(
          filter: scala.Option[OsmoGridConfig.Generation.Lv.Osm.Filter]
      )
      object Osm {
        final case class Filter(
            building: scala.List[java.lang.String],
            highway: scala.List[java.lang.String],
            landuse: scala.List[java.lang.String]
        )
        object Filter {
          def apply(
              c: com.typesafe.config.Config,
              parentPath: java.lang.String,
              $tsCfgValidator: $TsCfgValidator
          ): OsmoGridConfig.Generation.Lv.Osm.Filter = {
            OsmoGridConfig.Generation.Lv.Osm.Filter(
              building =
                $_L$_str(c.getList("building"), parentPath, $tsCfgValidator),
              highway =
                $_L$_str(c.getList("highway"), parentPath, $tsCfgValidator),
              landuse =
                $_L$_str(c.getList("landuse"), parentPath, $tsCfgValidator)
            )
          }
        }

        def apply(
            c: com.typesafe.config.Config,
            parentPath: java.lang.String,
            $tsCfgValidator: $TsCfgValidator
        ): OsmoGridConfig.Generation.Lv.Osm = {
          OsmoGridConfig.Generation.Lv.Osm(
            filter =
              if (c.hasPathOrNull("filter"))
                scala.Some(
                  OsmoGridConfig.Generation.Lv.Osm.Filter(
                    c.getConfig("filter"),
                    parentPath + "filter.",
                    $tsCfgValidator
                  )
                )
              else None
          )
        }
      }

      def apply(
          c: com.typesafe.config.Config,
          parentPath: java.lang.String,
          $tsCfgValidator: $TsCfgValidator
      ): OsmoGridConfig.Generation.Lv = {
        OsmoGridConfig.Generation.Lv(
          boundaryAdminLevel = OsmoGridConfig.Generation.Lv.BoundaryAdminLevel(
            if (c.hasPathOrNull("boundaryAdminLevel"))
              c.getConfig("boundaryAdminLevel")
            else
              com.typesafe.config.ConfigFactory
                .parseString("boundaryAdminLevel{}"),
            parentPath + "boundaryAdminLevel.",
            $tsCfgValidator
          ),
          distinctHouseConnections = c.hasPathOrNull(
            "distinctHouseConnections"
          ) && c.getBoolean("distinctHouseConnections"),
          osm = OsmoGridConfig.Generation.Lv.Osm(
            if (c.hasPathOrNull("osm")) c.getConfig("osm")
            else com.typesafe.config.ConfigFactory.parseString("osm{}"),
            parentPath + "osm.",
            $tsCfgValidator
          )
        )
      }
    }

    def apply(
        c: com.typesafe.config.Config,
        parentPath: java.lang.String,
        $tsCfgValidator: $TsCfgValidator
    ): OsmoGridConfig.Generation = {
      OsmoGridConfig.Generation(
        lv =
          if (c.hasPathOrNull("lv"))
            scala.Some(
              OsmoGridConfig.Generation
                .Lv(c.getConfig("lv"), parentPath + "lv.", $tsCfgValidator)
            )
          else None
      )
    }
  }

  final case class Input(
      asset: OsmoGridConfig.Input.Asset,
      osm: OsmoGridConfig.Input.Osm
  )
  object Input {
    final case class Asset(
        file: scala.Option[OsmoGridConfig.Input.Asset.File]
    )
    object Asset {
      final case class File(
          directory: java.lang.String,
          hierarchic: scala.Boolean
      )
      object File {
        def apply(
            c: com.typesafe.config.Config,
            parentPath: java.lang.String,
            $tsCfgValidator: $TsCfgValidator
        ): OsmoGridConfig.Input.Asset.File = {
          OsmoGridConfig.Input.Asset.File(
            directory = $_reqStr(parentPath, c, "directory", $tsCfgValidator),
            hierarchic =
              c.hasPathOrNull("hierarchic") && c.getBoolean("hierarchic")
          )
        }
        private def $_reqStr(
            parentPath: java.lang.String,
            c: com.typesafe.config.Config,
            path: java.lang.String,
            $tsCfgValidator: $TsCfgValidator
        ): java.lang.String = {
          if (c == null) null
          else
            try c.getString(path)
            catch {
              case e: com.typesafe.config.ConfigException =>
                $tsCfgValidator.addBadPath(parentPath + path, e)
                null
            }
        }

      }

      def apply(
          c: com.typesafe.config.Config,
          parentPath: java.lang.String,
          $tsCfgValidator: $TsCfgValidator
      ): OsmoGridConfig.Input.Asset = {
        OsmoGridConfig.Input.Asset(
          file =
            if (c.hasPathOrNull("file"))
              scala.Some(
                OsmoGridConfig.Input.Asset.File(
                  c.getConfig("file"),
                  parentPath + "file.",
                  $tsCfgValidator
                )
              )
            else None
        )
      }
    }

    final case class Osm(
        pbf: scala.Option[OsmoGridConfig.Input.Osm.Pbf]
    )
    object Osm {
      final case class Pbf(
          file: java.lang.String
      )
      object Pbf {
        def apply(
            c: com.typesafe.config.Config,
            parentPath: java.lang.String,
            $tsCfgValidator: $TsCfgValidator
        ): OsmoGridConfig.Input.Osm.Pbf = {
          OsmoGridConfig.Input.Osm.Pbf(
            file = $_reqStr(parentPath, c, "file", $tsCfgValidator)
          )
        }
        private def $_reqStr(
            parentPath: java.lang.String,
            c: com.typesafe.config.Config,
            path: java.lang.String,
            $tsCfgValidator: $TsCfgValidator
        ): java.lang.String = {
          if (c == null) null
          else
            try c.getString(path)
            catch {
              case e: com.typesafe.config.ConfigException =>
                $tsCfgValidator.addBadPath(parentPath + path, e)
                null
            }
        }

      }

      def apply(
          c: com.typesafe.config.Config,
          parentPath: java.lang.String,
          $tsCfgValidator: $TsCfgValidator
      ): OsmoGridConfig.Input.Osm = {
        OsmoGridConfig.Input.Osm(
          pbf =
            if (c.hasPathOrNull("pbf"))
              scala.Some(
                OsmoGridConfig.Input.Osm
                  .Pbf(c.getConfig("pbf"), parentPath + "pbf.", $tsCfgValidator)
              )
            else None
        )
      }
    }

    def apply(
        c: com.typesafe.config.Config,
        parentPath: java.lang.String,
        $tsCfgValidator: $TsCfgValidator
    ): OsmoGridConfig.Input = {
      OsmoGridConfig.Input(
        asset = OsmoGridConfig.Input.Asset(
          if (c.hasPathOrNull("asset")) c.getConfig("asset")
          else com.typesafe.config.ConfigFactory.parseString("asset{}"),
          parentPath + "asset.",
          $tsCfgValidator
        ),
        osm = OsmoGridConfig.Input.Osm(
          if (c.hasPathOrNull("osm")) c.getConfig("osm")
          else com.typesafe.config.ConfigFactory.parseString("osm{}"),
          parentPath + "osm.",
          $tsCfgValidator
        )
      )
    }
  }

  final case class Output(
      csv: scala.Option[OsmoGridConfig.Output.Csv]
  )
  object Output {
    final case class Csv(
        directory: java.lang.String,
        hierarchic: scala.Boolean,
        separator: java.lang.String
    )
    object Csv {
      def apply(
          c: com.typesafe.config.Config,
          parentPath: java.lang.String,
          $tsCfgValidator: $TsCfgValidator
      ): OsmoGridConfig.Output.Csv = {
        OsmoGridConfig.Output.Csv(
          directory = $_reqStr(parentPath, c, "directory", $tsCfgValidator),
          hierarchic =
            c.hasPathOrNull("hierarchic") && c.getBoolean("hierarchic"),
          separator =
            if (c.hasPathOrNull("separator")) c.getString("separator") else ";"
        )
      }
      private def $_reqStr(
          parentPath: java.lang.String,
          c: com.typesafe.config.Config,
          path: java.lang.String,
          $tsCfgValidator: $TsCfgValidator
      ): java.lang.String = {
        if (c == null) null
        else
          try c.getString(path)
          catch {
            case e: com.typesafe.config.ConfigException =>
              $tsCfgValidator.addBadPath(parentPath + path, e)
              null
          }
      }

    }

    def apply(
        c: com.typesafe.config.Config,
        parentPath: java.lang.String,
        $tsCfgValidator: $TsCfgValidator
    ): OsmoGridConfig.Output = {
      OsmoGridConfig.Output(
        csv =
          if (c.hasPathOrNull("csv"))
            scala.Some(
              OsmoGridConfig.Output
                .Csv(c.getConfig("csv"), parentPath + "csv.", $tsCfgValidator)
            )
          else None
      )
    }
  }

  def apply(c: com.typesafe.config.Config): OsmoGridConfig = {
    val $tsCfgValidator: $TsCfgValidator = new $TsCfgValidator()
    val parentPath: java.lang.String = ""
    val $result = OsmoGridConfig(
      generation = OsmoGridConfig.Generation(
        if (c.hasPathOrNull("generation")) c.getConfig("generation")
        else com.typesafe.config.ConfigFactory.parseString("generation{}"),
        parentPath + "generation.",
        $tsCfgValidator
      ),
      input = OsmoGridConfig.Input(
        if (c.hasPathOrNull("input")) c.getConfig("input")
        else com.typesafe.config.ConfigFactory.parseString("input{}"),
        parentPath + "input.",
        $tsCfgValidator
      ),
      output = OsmoGridConfig.Output(
        if (c.hasPathOrNull("output")) c.getConfig("output")
        else com.typesafe.config.ConfigFactory.parseString("output{}"),
        parentPath + "output.",
        $tsCfgValidator
      )
    )
    $tsCfgValidator.validate()
    $result
  }

  private def $_L$_str(
      cl: com.typesafe.config.ConfigList,
      parentPath: java.lang.String,
      $tsCfgValidator: $TsCfgValidator
  ): scala.List[java.lang.String] = {
    import scala.jdk.CollectionConverters._
    cl.asScala.map(cv => $_str(cv)).toList
  }
  private def $_expE(
      cv: com.typesafe.config.ConfigValue,
      exp: java.lang.String
  ) = {
    val u: Any = cv.unwrapped
    new java.lang.RuntimeException(
      s"${cv.origin.lineNumber}: " +
        "expecting: " + exp + " got: " +
        (if (u.isInstanceOf[java.lang.String]) "\"" + u + "\"" else u)
    )
  }

  private def $_str(cv: com.typesafe.config.ConfigValue): java.lang.String = {
    java.lang.String.valueOf(cv.unwrapped())
  }

  final class $TsCfgValidator {
    private val badPaths =
      scala.collection.mutable.ArrayBuffer[java.lang.String]()

    def addBadPath(
        path: java.lang.String,
        e: com.typesafe.config.ConfigException
    ): Unit = {
      badPaths += s"'$path': ${e.getClass.getName}(${e.getMessage})"
    }

    def addInvalidEnumValue(
        path: java.lang.String,
        value: java.lang.String,
        enumName: java.lang.String
    ): Unit = {
      badPaths += s"'$path': invalid value $value for enumeration $enumName"
    }

    def validate(): Unit = {
      if (badPaths.nonEmpty) {
        throw new com.typesafe.config.ConfigException(
          badPaths.mkString("Invalid configuration:\n    ", "\n    ", "")
        ) {}
      }
    }
  }
}
