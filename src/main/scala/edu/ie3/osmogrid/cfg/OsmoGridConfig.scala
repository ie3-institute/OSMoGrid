/*
 * © 2021. TU Dortmund University,
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
        amountOfGridGenerators: scala.Int,
        amountOfRegionCoordinators: scala.Int,
        distinctHouseConnections: scala.Boolean,
        loadDensity: scala.Double
    )
    object Lv {
      def apply(
          c: com.typesafe.config.Config,
          parentPath: java.lang.String,
          $tsCfgValidator: $TsCfgValidator
      ): OsmoGridConfig.Generation.Lv = {
        OsmoGridConfig.Generation.Lv(
          amountOfGridGenerators =
            if (c.hasPathOrNull("amountOfGridGenerators"))
              c.getInt("amountOfGridGenerators")
            else 10,
          amountOfRegionCoordinators =
            if (c.hasPathOrNull("amountOfRegionCoordinators"))
              c.getInt("amountOfRegionCoordinators")
            else 5,
          distinctHouseConnections = c.hasPathOrNull(
            "distinctHouseConnections"
          ) && c.getBoolean("distinctHouseConnections"),
          loadDensity = $_reqDbl(parentPath, c, "loadDensity", $tsCfgValidator)
        )
      }
      private def $_reqDbl(
          parentPath: java.lang.String,
          c: com.typesafe.config.Config,
          path: java.lang.String,
          $tsCfgValidator: $TsCfgValidator
      ): scala.Double = {
        if (c == null) 0
        else
          try c.getDouble(path)
          catch {
            case e: com.typesafe.config.ConfigException =>
              $tsCfgValidator.addBadPath(parentPath + path, e)
              0
          }
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
      file: scala.Option[OsmoGridConfig.Output.File]
  )
  object Output {
    final case class File(
        directory: java.lang.String,
        hierarchic: scala.Boolean
    )
    object File {
      def apply(
          c: com.typesafe.config.Config,
          parentPath: java.lang.String,
          $tsCfgValidator: $TsCfgValidator
      ): OsmoGridConfig.Output.File = {
        OsmoGridConfig.Output.File(
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
    ): OsmoGridConfig.Output = {
      OsmoGridConfig.Output(
        file =
          if (c.hasPathOrNull("file"))
            scala.Some(
              OsmoGridConfig.Output.File(
                c.getConfig("file"),
                parentPath + "file.",
                $tsCfgValidator
              )
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
