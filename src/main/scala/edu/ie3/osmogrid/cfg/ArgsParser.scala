/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.cfg

import java.io.File
import java.nio.file.Paths

import com.typesafe.scalalogging.LazyLogging
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import scopt.OptionParser
import scala.jdk.CollectionConverters._

object ArgsParser extends LazyLogging {
  def prepare(args: Array[String]): OsmoGridConfig = {
    /* Build the parser */
    val parser = new OptionParser[CliArguments]("osmogrid") {
      opt[String]("config")
        .action((value, args) => args.copy(configLocation = Some(value)))
        .validate(value =>
          if (value.trim.isEmpty) failure("config location cannot be empty")
          else success
        )
        .validate(value =>
          if (value.contains("\\"))
            failure("wrong config path, expected: /, found: \\")
          else success
        )
        .text("Location of the config file")
        .minOccurs(1)
    }

    /* Actually parse the config */
    parser.parse(args, init = CliArguments(args)) match {
      case Some(CliArguments(_, Some(configLocation))) =>
        /* Parse the config file at the given location */
        val typeSafeConfig = parseTypesafeConfig(configLocation)
        OsmoGridConfig(typeSafeConfig)
      case Some(CliArguments(_, None)) =>
        throw new RuntimeException(
          s"No config location provided. Please provide a valid config file via --config <path-to-config-file>."
        )
      case None =>
        throw new RuntimeException(s"Unable to parse cli arguments '$args'.")
    }
  }

  private def parseTypesafeConfig(fileName: String): Config = {
    val file = Paths.get(fileName).toFile
    if (!file.exists())
      throw new Exception(s"Missing config file on path $fileName")
    parseTypesafeConfig(file)
  }

  private def parseTypesafeConfig(file: File): Config = {
    ConfigFactory
      .parseFile(file)
      .withFallback(
        ConfigFactory.parseMap(
          Map.empty[String, String].asJava
        )
      )
  }

  final case class CliArguments(
      rawArgs: Array[String],
      configLocation: Option[String] = None,
  )
}
