/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.main

import edu.ie3.datamodel.io.source.csv.CsvJointGridContainerSource
import edu.ie3.datamodel.utils.validation.ValidationUtils
import edu.ie3.osmogrid.cfg.OsmoGridConfig.Output
import edu.ie3.osmogrid.cfg.{ArgsParser, OsmoGridConfig}
import edu.ie3.osmogrid.exception.{GridException, IllegalConfigException}
import edu.ie3.osmogrid.guardian.{OsmoGridGuardian, Run}
import org.apache.pekko.actor.typed.ActorSystem

import java.nio.file.{Files, Path}
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.jdk.CollectionConverters.IterableHasAsScala
import scala.util.Using

object RunOsmoGridStandalone {

  def main(args: Array[String]): Unit = {
    val cfg: OsmoGridConfig = ArgsParser.prepare(args)

    val actorSystem = ActorSystem(OsmoGridGuardian(), "OSMoGridGuardian")
    actorSystem ! Run(cfg)

    Await.result(actorSystem.whenTerminated, Duration.Inf)
    summarizeGrid(cfg)
  }

  private def summarizeGrid(cfg: OsmoGridConfig): Unit = {
    println(" ")

    val grid = cfg.output match {
      case Output(_, Some(csv), gridName, grids) =>
        // only read and validate a grid if a grid output was actually requested
        val gridRequested = grids.lv || grids.mv || grids.hv
        if (!gridRequested) {
          println("No grid output was requested. Skipping grid summarization.")
          return
        }

        // Locate the actual grid output directory. Accept two cases:
        // 1. csv.directory points directly to the run output (contains node_input.csv)
        // 2. csv.directory is a parent that contains exactly one run subdirectory
        //    which contains node_input.csv. If multiple candidate subdirectories
        //    are present, fail with a helpful error to avoid ambiguity.
        val configured = Path.of(csv.directory)
        locateGridOutput(configured) match {
          case Some(outputDir) =>
            CsvJointGridContainerSource.read(
              gridName,
              csv.separator,
              outputDir,
              csv.hierarchic,
            )
          case None =>
            throw IllegalConfigException(s"No grid output found at ${configured.toAbsolutePath}")
        }
      case Output(_, None, _, _) =>
        throw IllegalConfigException("No output given.")
    }

    println(" ")
    println(s"Summarization of grid \"${cfg.output.gridName}\":")

    // get nodes
    val nodes = grid.getRawGrid.getNodes.asScala

    // check sub grids
    val subnets = nodes.groupBy(_.getSubnet)
    subnets
      .map { case (i, subgridNodes) =>
        val voltLvl = subgridNodes.map(_.getVoltLvl.getNominalVoltage).toSet

        if (voltLvl.size != 1) {
          throw GridException(s"In subgrid $i: $voltLvl")
        }

        i -> s"In subgrid $i: ${voltLvl.head} with ${subgridNodes.size} node(s)"
      }
      .toList
      .sortBy(_._1)
      .map(_._2)
      .foreach(println)

    println(s"Number of slack nodes: ${nodes.toSeq.count(_.isSlack)}")
    println(s"Number of lv nodes: ${nodes.toSeq
        .count(_.getVoltLvl.getNominalVoltage.getValue.doubleValue() < 10)}")
    println(s"Number of mv nodes: ${nodes.toSeq
        .count(_.getVoltLvl.getNominalVoltage.getValue.doubleValue() == 10)}")
    println(s"Number of hv nodes: ${nodes.toSeq
        .count(_.getVoltLvl.getNominalVoltage.getValue.doubleValue() == 110)}")

    ValidationUtils.check(grid)
  }

  // Helper: determine the actual grid output directory given the configured path
  private def locateGridOutput(dir: Path): Option[Path] = {
    if (!Files.isDirectory(dir)) return None

    // If the configured dir itself contains the expected file, use it
    if (Files.exists(dir.resolve("node_input.csv"))) return Some(dir)

    // Otherwise, look for subdirectories that contain node_input.csv
    val candidates = Using.resource(Files.newDirectoryStream(dir)) { stream =>
      stream.asScala
        .filter(p => Files.isDirectory(p) && Files.exists(p.resolve("node_input.csv")))
        .toList
    }

    candidates match {
      case Nil           => None
      case single :: Nil => Some(single)
      case many =>
        // Multiple candidates found — pick the most recently modified one to
        // match typical expectation that the latest run is intended.
        Some(many.maxBy(p => Files.getLastModifiedTime(p).toMillis))
    }
  }
}
