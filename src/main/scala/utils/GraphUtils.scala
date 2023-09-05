/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package utils

import edu.ie3.osmogrid.graph.OsmGraph

import java.awt.geom.{Line2D, Rectangle2D}
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import scala.jdk.CollectionConverters._

object GraphUtils {
  def draw(osmGraph: OsmGraph, name: String): Unit = {
    // creating image
    val image: BufferedImage =
      new BufferedImage(600, 600, BufferedImage.TYPE_INT_RGB)
    val graphics = image.createGraphics()
    graphics.setBackground(Color.WHITE)
    graphics.fillRect(0, 0, 600, 600)

    // adding graph components
    val vertexes = osmGraph.vertexSet().asScala
    val edges = osmGraph.edgeSet().asScala

    val lat = vertexes.map(v => v.latitude)
    val lon = vertexes.map(v => v.longitude)
    val (minLat, maxLat, minLon, maxLon) =
      (lat.min - 1, lat.max + 1, lon.min - 1, lon.max + 1)

    val xFac: Double = image.getWidth / (maxLon - minLon)
    val yFac: Double = image.getHeight / (maxLat - minLat)

    graphics.setColor(Color.BLUE)
    vertexes.foreach { v =>
      val x = (v.longitude - minLon) * xFac
      val y = (v.latitude - minLat) * yFac

      val rect = new Rectangle2D.Double(x, y, 10, 10)
      graphics.draw(rect)
      graphics.fill(rect)

    // adding coordinates

    }

    graphics.setColor(Color.BLACK)
    edges.foreach { e =>
      val source = osmGraph.getEdgeSource(e)
      val target = osmGraph.getEdgeTarget(e)

      val startX = (source.longitude - minLon) * xFac
      val startY = (source.latitude - minLat) * yFac
      val endX = (target.longitude - minLon) * xFac
      val endY = (target.latitude - minLat) * yFac

      val line = new Line2D.Double(startX, startY, endX, endY)
      graphics.draw(line)
    }

    val imgFile: File =
      new File(".").toPath.resolve("OutputData").resolve(name).toFile
    ImageIO.write(image, "PNG", imgFile)
  }

}
