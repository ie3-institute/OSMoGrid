/*
 * © 2023. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package utils

import edu.ie3.osmogrid.graph.OsmGraph
import edu.ie3.util.osm.model.OsmEntity.Node
import org.locationtech.jts.geom.LineSegment

import java.awt.geom.{Line2D, Rectangle2D}
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Path
import javax.imageio.ImageIO
import scala.jdk.CollectionConverters._

object GraphUtils {
  val FOLDER: Path = new File(".").toPath.resolve("OutputData")

  /** Method for drawing an [[OsmGraph]].
    *
    * @param osmGraph
    *   to be drawn
    * @param name
    *   of the image file
    * @param width
    *   of the image file
    * @param height
    *   of the image file
    */
  def draw(osmGraph: OsmGraph, name: String, width: Int, height: Int): Unit = {
    // creating image
    val image: BufferedImage =
      new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val graphics = image.createGraphics()
    graphics.setBackground(Color.WHITE)
    graphics.fillRect(0, 0, width, height)

    // adding graph components
    val vertexes = osmGraph.vertexSet().asScala
    val edges = osmGraph.edgeSet().asScala

    val lat = vertexes.map(v => v.latitude)
    val lon = vertexes.map(v => v.longitude)
    val (minLat, maxLat, minLon, maxLon) =
      (lat.min - 1, lat.max + 1, lon.min - 1, lon.max + 1)

    val xFac: Double = image.getWidth / (maxLon - minLon)
    val yFac: Double = image.getHeight / (maxLat - minLat)

    // start drawing of edges
    graphics.setColor(Color.BLACK)
    edges.foreach { e =>
      val source = osmGraph.getEdgeSource(e)
      val target = osmGraph.getEdgeTarget(e)

      val startX = (source.longitude - minLon) * xFac
      val startY = image.getHeight - (source.latitude - minLat) * yFac
      val endX = (target.longitude - minLon) * xFac
      val endY = image.getHeight - (target.latitude - minLat) * yFac

      val line = new Line2D.Double(startX, startY, endX, endY)
      graphics.draw(line)
    }

    // start drawing of vertexes
    graphics.setColor(Color.BLUE)
    vertexes.foreach { v =>
      val x = (v.longitude - minLon) * xFac - 5
      val y = image.getHeight - (v.latitude - minLat) * yFac - 5

      graphics.setColor(Color.BLUE)
      val rect = new Rectangle2D.Double(x, y, 10, 10)
      graphics.draw(rect)
      graphics.fill(rect)

      // adding vertex coordinates
      graphics.setColor(Color.RED)
      val str: String = s"(${v.latitude}, ${v.longitude})"

      graphics.drawString(str, x.toFloat, y.toFloat)
    }

    val imgFile: File = FOLDER.resolve(name).toFile
    ImageIO.write(image, "PNG", imgFile)
  }

  /** Builds a [[LineSegment]] from two given [[Node]]'s.
    *
    * @param nodeA
    *   start point of the line
    * @param nodeB
    *   end point of the line
    * @return
    *   a new [[LineSegment]]
    */
  def getLineSegmentBetweenNodes(nodeA: Node, nodeB: Node): LineSegment = {
    new LineSegment(
      nodeA.longitude,
      nodeA.latitude,
      nodeB.longitude,
      nodeB.latitude,
    )
  }

  /** Checks if two [[LineSegment]] intersects each other.
    *
    * @param lineA
    *   first line
    * @param lineB
    *   second line
    * @return
    *   true if both lines intersects
    */
  def hasIntersection(lineA: LineSegment, lineB: LineSegment): Boolean = {
    val intersectionPoint = lineA.intersection(lineB)

    if (intersectionPoint == null) {
      false
    } else if (
      intersectionPoint == lineA.p0 || intersectionPoint == lineA.p1 || intersectionPoint == lineB.p0 || intersectionPoint == lineB.p1
    ) {
      // if the found intersection point is one of the start or end points of the two line segments,
      // we need to check if the two line segments only intersect at the found point
      val dist1: Double = lineA.distance(lineB.p0)
      val dist2: Double = lineA.distance(lineB.p1)

      val dist3: Double = lineB.distance(lineA.p0)
      val dist4: Double = lineB.distance(lineA.p1)

      // comparing and filtering the distances
      val distances: List[Double] = List(dist1, dist2, dist3, dist4).filter {
        d => d.compare(0d) == 0
      }

      // if more than two points have a distance of zero, the line segments are on top of each other
      // therefore we will count them as intersected
      if (distances.size > 2) {
        true
      } else {
        false
      }
    } else {
      // if another intersection point is found, we have two intersecting line segments
      true
    }
  }
}
