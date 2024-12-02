/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.main

import edu.ie3.datamodel.io.source.csv.CsvJointGridContainerSource
import edu.ie3.osmogrid.cfg.OsmoGridConfig.Output
import edu.ie3.osmogrid.cfg.{ArgsParser, OsmoGridConfig}
import edu.ie3.osmogrid.exception.{GridException, IllegalConfigException}
import edu.ie3.osmogrid.guardian.{OsmoGridGuardian, Run}
import org.apache.pekko.actor.typed.ActorSystem

import java.nio.file.Path
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.jdk.CollectionConverters.CollectionHasAsScala

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
      case Output(_, Some(csv), gridName) =>
        val newestOutput =
          Path.of(csv.directory).toFile.listFiles().toSeq.lastOption

        newestOutput match {
          case Some(directory) =>
            CsvJointGridContainerSource.read(
              gridName,
              csv.separator,
              directory.toPath,
              csv.hierarchic,
            )
          case None =>
            throw IllegalConfigException("No output given.")
        }
      case Output(_, None, _) =>
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

        i -> s"In subgrid $i: ${voltLvl.toSeq(0)} with ${subgridNodes.size} node(s)"
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

  }
}
