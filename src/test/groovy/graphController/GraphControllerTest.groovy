/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */
package graphController

import com.typesafe.config.ConfigFactory
import de.osmogrid.config.OsmogridConfig
import de.osmogrid.controller.graph.GraphController
import de.osmogrid.model.graph.OsmGridNode
import edu.ie3.datamodel.graph.DistanceWeightedGraph
import edu.ie3.util.quantities.PowerSystemUnits
import org.apache.commons.lang3.tuple.Pair
import spock.lang.Shared
import spock.lang.Specification
import tec.uom.se.quantity.Quantities
import utils.TestObjectFactory

import java.util.stream.Collectors

class GraphControllerTest extends Specification {

	@Shared
	def config = ConfigFactory.parseFile(new File("configs" + File.separator +"fuerweiler.conf"))

	@Shared
	def graphController = new GraphController()

	@Shared
	def powerDensity

	def setupSpec() {
		OsmogridConfig osmogridConfig = new OsmogridConfig(config)
		graphController.initialize(osmogridConfig)
		powerDensity = Quantities.getQuantity(osmogridConfig.grid.averagePowerDensity, PowerSystemUnits.WATT_PER_SQUAREMETRE)
	}

	def "calculated perpendicular distance matrix test"() {
		given:
		// Call method under test
		graphController.invokeMethod("calcPerpendicularDistanceMatrix", powerDensity)

		/* Get node id to lat lon mapping. The first element of the lat lon tuple is the actual nodes position and the
		 * second one is the position of the house connection point */
		def expected = TestObjectFactory.getNodeToLatLon()

		when:
		/* Get the actual node to lat lon mapping */
		def actual = ((DistanceWeightedGraph<OsmGridNode>) graphController["fullGraph"]).
				vertexSet().
				parallelStream().
				filter({node -> Objects.nonNull(node.getHouseConnectionPoint())}).
				collect(
				Collectors.toMap(
				{node -> ((OsmGridNode) node).id},
				{node -> Pair.of(((OsmGridNode) node).latlon, ((OsmGridNode) node).houseConnectionPoint)})
				)

		then:
		actual.size() == expected.size()
		actual.forEach { nodeId, latLonPair ->
			/* Try to get the expected values for this node */
			def expectedLatLon = actual.get(nodeId)
			assert Objects.nonNull(expectedLatLon)
			assert latLonPair.getKey() == expectedLatLon.getKey()
			assert latLonPair.getValue() == expectedLatLon.getValue()
		}
	}
}
