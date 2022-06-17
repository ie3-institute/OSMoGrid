/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.cfg

import com.typesafe.config.ConfigFactory

import java.io.File
import scala.util.Try

object OsmoGridConfigFactory {
  lazy val defaultTestConfig: OsmoGridConfig =
    OsmoGridConfig(
      ConfigFactory.parseFile(new File("src/test/resources/testConfig.conf"))
    )

  def parse(config: String): Try[OsmoGridConfig] = Try {
    OsmoGridConfig(
      ConfigFactory
        .parseString(config)
        .withFallback(
          ConfigFactory.parseFile(
            new File("src/test/resources/testConfig.conf")
          )
        )
    )
  }
  def parseWithoutFallback(config: String): Try[OsmoGridConfig] = Try {
    OsmoGridConfig(
      ConfigFactory
        .parseString(config)
    )
  }
}
