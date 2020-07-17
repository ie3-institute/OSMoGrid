/*
 * © 2020. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
*/
package utils;

import de.osmogrid.model.graph.OsmGraph;
import de.osmogrid.model.graph.OsmGridNode;
import edu.ie3.util.quantities.PowerSystemUnits;
import java.util.HashMap;
import java.util.Map;
import javax.measure.Quantity;
import javax.measure.quantity.Power;
import net.morbz.osmonaut.osm.LatLon;
import net.morbz.osmonaut.osm.Tags;
import org.apache.commons.lang3.tuple.Pair;
import tec.uom.se.quantity.Quantities;

public class TestObjectFactory {

  @Deprecated
  public static OsmGraph calcPerpGraphFuerweiler() {

    OsmGraph graph = new OsmGraph();

    graph.addVertex(
        createOsmogridNode(
            271045355L, new Tags(), new LatLon(49.3784618, 6.5919926), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388307406L, new Tags(), new LatLon(49.3777125, 6.5922686), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388305991L, new Tags(), new LatLon(49.3758757, 6.5958673), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388305660L, new Tags(), new LatLon(49.3758914, 6.5959366), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388305663L, new Tags(), new LatLon(49.3759923, 6.5962463), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388305665L, new Tags(), new LatLon(49.3761821, 6.5964674), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            1324280445L, new Tags(), new LatLon(49.3764586, 6.5966353), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388305671L, new Tags(), new LatLon(49.3767235, 6.5970328), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388307503L, new Tags(), new LatLon(49.3771619, 6.5978165), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            1324280399L, new Tags(), new LatLon(49.3772829, 6.5977057), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388307507L, new Tags(), new LatLon(49.3776450, 6.5972922), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388307698L, new Tags(), new LatLon(49.3779964, 6.5968005), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388307699L, new Tags(), new LatLon(49.3779080, 6.5966495), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            970729089L, new Tags(), new LatLon(49.3776295, 6.5961583), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388307701L, new Tags(), new LatLon(49.3772902, 6.5956041), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388307702L, new Tags(), new LatLon(49.3772416, 6.5952255), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388307703L, new Tags(), new LatLon(49.3772312, 6.5951187), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            970729126L, new Tags(), new LatLon(49.3772111, 6.5949578), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            271045356L, new Tags(), new LatLon(49.3769247, 6.5945180), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388303824L, new Tags(), new LatLon(49.3753664, 6.5961804), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            2092382189L, new Tags(), new LatLon(49.3753865, 6.5959867), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            1324280451L, new Tags(), new LatLon(49.3754546, 6.5958271), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388308826L, new Tags(), new LatLon(49.3754829, 6.5957025), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388308827L, new Tags(), new LatLon(49.3755108, 6.5954879), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388308828L, new Tags(), new LatLon(49.3755052, 6.5952647), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388308521L, new Tags(), new LatLon(49.3754526, 6.5950676), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388308522L, new Tags(), new LatLon(49.3753499, 6.5947287), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388308523L, new Tags(), new LatLon(49.3752204, 6.5942703), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            3388089388L, new Tags(), new LatLon(49.3751522, 6.5940221), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388308524L, new Tags(), new LatLon(49.3751218, 6.5937997), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388308525L, new Tags(), new LatLon(49.3750917, 6.5932906), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            2092382194L, new Tags(), new LatLon(49.3750896, 6.5931434), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388308526L, new Tags(), new LatLon(49.3750861, 6.5928958), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388308829L, new Tags(), new LatLon(49.3750917, 6.5926984), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388308830L, new Tags(), new LatLon(49.3751476, 6.5924323), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388308831L, new Tags(), new LatLon(49.3751979, 6.5921748), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388308832L, new Tags(), new LatLon(49.3751979, 6.5920718), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388308833L, new Tags(), new LatLon(49.3752817, 6.5920718), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388308834L, new Tags(), new LatLon(49.3753935, 6.5920547), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388308835L, new Tags(), new LatLon(49.3754941, 6.5921662), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            2092382186L, new Tags(), new LatLon(49.3756531, 6.5924326), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388308836L, new Tags(), new LatLon(49.3756785, 6.5924752), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388308837L, new Tags(), new LatLon(49.3758517, 6.5926898), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            2714782555L, new Tags(), new LatLon(49.3759237, 6.5927174), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388308838L, new Tags(), new LatLon(49.3760753, 6.5927756), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388308839L, new Tags(), new LatLon(49.3763204, 6.5927755), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388308840L, new Tags(), new LatLon(49.3766014, 6.5927643), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            3388072359L, new Tags(), new LatLon(49.3766653, 6.5927805), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388308841L, new Tags(), new LatLon(49.3767275, 6.5928123), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388308842L, new Tags(), new LatLon(49.3768409, 6.5928529), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388308842L, new Tags(), new LatLon(49.3768409, 6.5928529), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388308843L, new Tags(), new LatLon(49.3770812, 6.5925782), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388308844L, new Tags(), new LatLon(49.3772991, 6.5924237), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388308845L, new Tags(), new LatLon(49.3775450, 6.5923207), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388307406L, new Tags(), new LatLon(49.3777125, 6.5922686), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388307406L, new Tags(), new LatLon(49.3777125, 6.5922686), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388307407L, new Tags(), new LatLon(49.3776678, 6.5926186), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388307408L, new Tags(), new LatLon(49.3775948, 6.5929292), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388307409L, new Tags(), new LatLon(49.3772151, 6.5932970), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            1324280356L, new Tags(), new LatLon(49.3771362, 6.5933673), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388307410L, new Tags(), new LatLon(49.3770768, 6.5935926), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388307411L, new Tags(), new LatLon(49.3770291, 6.5942958), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            271045356L, new Tags(), new LatLon(49.3769247, 6.5945180), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            970729119L, new Tags(), new LatLon(49.3766553, 6.5949310), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            2092382190L, new Tags(), new LatLon(49.3763556, 6.5954315), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            271045357L, new Tags(), new LatLon(49.3760784, 6.5957810), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388305991L, new Tags(), new LatLon(49.3758757, 6.5958673), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            1324280451L, new Tags(), new LatLon(49.3754546, 6.5958271), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            2092382191L, new Tags(), new LatLon(49.3752749, 6.5958166), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            271118834L, new Tags(), new LatLon(49.3750945, 6.5958590), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388308832L, new Tags(), new LatLon(49.3751979, 6.5920718), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            672818010L, new Tags(), new LatLon(49.3750914, 6.5915919), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            672818011L, new Tags(), new LatLon(49.3744810, 6.5905760), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            970729126L, new Tags(), new LatLon(49.3772111, 6.5949578), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            970729051L, new Tags(), new LatLon(49.3773897, 6.5947067), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            970729131L, new Tags(), new LatLon(49.3777182, 6.5950096), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            970729144L, new Tags(), new LatLon(49.3778180, 6.5951504), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            970729071L, new Tags(), new LatLon(49.3780603, 6.5955896), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            2092382188L, new Tags(), new LatLon(49.3783780, 6.5961745), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            970729047L, new Tags(), new LatLon(49.3787741, 6.5969679), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            970729089L, new Tags(), new LatLon(49.3776295, 6.5961583), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            970729082L, new Tags(), new LatLon(49.3773274, 6.5965460), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            970729071L, new Tags(), new LatLon(49.3780603, 6.5955896), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            970729091L, new Tags(), new LatLon(49.3783944, 6.5950592), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            970729087L, new Tags(), new LatLon(49.3780864, 6.5945649), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            970729131L, new Tags(), new LatLon(49.3777182, 6.5950096), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388307701L, new Tags(), new LatLon(49.3772902, 6.5956041), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            970729044L, new Tags(), new LatLon(49.3769490, 6.5959648), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            970729110L, new Tags(), new LatLon(49.3760731, 6.5939994), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            970729083L, new Tags(), new LatLon(49.3762777, 6.5943388), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            970729119L, new Tags(), new LatLon(49.3766553, 6.5949310), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388308842L, new Tags(), new LatLon(49.3768409, 6.5928529), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            1324280356L, new Tags(), new LatLon(49.3771362, 6.5933673), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            2092382194L, new Tags(), new LatLon(49.3750896, 6.5931434), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            2092382187L, new Tags(), new LatLon(49.3756790, 6.5929183), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            2714782553L, new Tags(), new LatLon(49.3757858, 6.5928672), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            2714782555L, new Tags(), new LatLon(49.3759237, 6.5927174), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            2714782558L, new Tags(), new LatLon(49.3760156, 6.5935158), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            2714782553L, new Tags(), new LatLon(49.3757858, 6.5928672), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            1556736951L, new Tags(), new LatLon(49.3862298, 6.5958277), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            1556736947L, new Tags(), new LatLon(49.3861093, 6.5956742), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            1567348065L, new Tags(), new LatLon(49.3856043, 6.5927280), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            1556736945L, new Tags(), new LatLon(49.3855010, 6.5925646), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            1556736944L, new Tags(), new LatLon(49.3853717, 6.5925232), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            1556736933L, new Tags(), new LatLon(49.3845245, 6.5923524), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            1567348061L, new Tags(), new LatLon(49.3839139, 6.5923724), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            1237112153L, new Tags(), new LatLon(49.3834113, 6.5924950), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            1237112183L, new Tags(), new LatLon(49.3831622, 6.5925110), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            1237112192L, new Tags(), new LatLon(49.3830208, 6.5925200), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            392078670L, new Tags(), new LatLon(49.3811037, 6.5919599), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            271045353L, new Tags(), new LatLon(49.3800476, 6.5919522), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            672818021L, new Tags(), new LatLon(49.3797622, 6.5919564), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            3951344064L, new Tags(), new LatLon(49.3797264, 6.5919515), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            271045354L, new Tags(), new LatLon(49.3790634, 6.5918610), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            271045355L, new Tags(), new LatLon(49.3784618, 6.5919926), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            672818011L, new Tags(), new LatLon(49.3744810, 6.5905760), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            2092365253L, new Tags(), new LatLon(49.3741226, 6.5899254), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            2092365275L, new Tags(), new LatLon(49.3738443, 6.5895513), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            672818012L, new Tags(), new LatLon(49.3735728, 6.5892015), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            672818013L, new Tags(), new LatLon(49.3733775, 6.5889447), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            672818014L, new Tags(), new LatLon(49.3732278, 6.5884787), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            672818015L, new Tags(), new LatLon(49.3731736, 6.5880480), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388303910L, new Tags(), new LatLon(49.3809246, 6.6148849), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388303911L, new Tags(), new LatLon(49.3806932, 6.6146638), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388303912L, new Tags(), new LatLon(49.3803732, 6.6139758), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388303841L, new Tags(), new LatLon(49.3800149, 6.6130255), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388303842L, new Tags(), new LatLon(49.3792574, 6.6111276), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388303843L, new Tags(), new LatLon(49.3784674, 6.6088386), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388303844L, new Tags(), new LatLon(49.3781969, 6.6078053), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388303845L, new Tags(), new LatLon(49.3780870, 6.6068690), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388303846L, new Tags(), new LatLon(49.3780423, 6.6060107), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388303814L, new Tags(), new LatLon(49.3780088, 6.6053670), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            1324280287L, new Tags(), new LatLon(49.3779423, 6.6050133), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388303815L, new Tags(), new LatLon(49.3777104, 6.6042125), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388303816L, new Tags(), new LatLon(49.3773128, 6.6028979), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388303817L, new Tags(), new LatLon(49.3769526, 6.6018136), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388303818L, new Tags(), new LatLon(49.3762709, 6.6003202), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            392076818L, new Tags(), new LatLon(49.3760187, 6.5998606), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            271118834L, new Tags(), new LatLon(49.3750945, 6.5958590), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388301760L, new Tags(), new LatLon(49.3749047, 6.5958880), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            2092382198L, new Tags(), new LatLon(49.3746104, 6.5960731), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            271045360L, new Tags(), new LatLon(49.3733537, 6.5971273), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            2154980835L, new Tags(), new LatLon(49.3730296, 6.5973264), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            271045362L, new Tags(), new LatLon(49.3712764, 6.5984034), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            380424744L, new Tags(), new LatLon(49.3709709, 6.5986187), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388309403L, new Tags(), new LatLon(49.3703021, 6.5989383), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388309682L, new Tags(), new LatLon(49.3694079, 6.5997708), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388309683L, new Tags(), new LatLon(49.3683346, 6.6015731), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388309684L, new Tags(), new LatLon(49.3676361, 6.6026033), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            271045365L, new Tags(), new LatLon(49.3670325, 6.6036332), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388309685L, new Tags(), new LatLon(49.3665574, 6.6047919), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388309686L, new Tags(), new LatLon(49.3660879, 6.6058648), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388309687L, new Tags(), new LatLon(49.3656183, 6.6065515), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            3912465423L, new Tags(), new LatLon(49.3653542, 6.6069526), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            958457205L, new Tags(), new LatLon(49.3651512, 6.6072611), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388309688L, new Tags(), new LatLon(49.3650985, 6.6073411), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            4368122618L, new Tags(), new LatLon(49.3647967, 6.6077955), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388309689L, new Tags(), new LatLon(49.3645483, 6.6081260), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            1564404072L, new Tags(), new LatLon(49.3643098, 6.6083248), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            4368122607L, new Tags(), new LatLon(49.3640613, 6.6084527), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            1564404069L, new Tags(), new LatLon(49.3638175, 6.6085545), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            470047441L, new Tags(), new LatLon(49.3621192, 6.6090206), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            1564404066L, new Tags(), new LatLon(49.3618296, 6.6090916), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            4368122455L, new Tags(), new LatLon(49.3615109, 6.6092439), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            4368122439L, new Tags(), new LatLon(49.3611790, 6.6094934), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            1564404064L, new Tags(), new LatLon(49.3608395, 6.6097823), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            4368122349L, new Tags(), new LatLon(49.3595072, 6.6111027), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            1564404063L, new Tags(), new LatLon(49.3592752, 6.6112920), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            1564404060L, new Tags(), new LatLon(49.3590821, 6.6114055), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            380433106L, new Tags(), new LatLon(49.3588970, 6.6114677), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            1564404058L, new Tags(), new LatLon(49.3587354, 6.6114889), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            1564404055L, new Tags(), new LatLon(49.3586500, 6.6114734), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            4368122268L, new Tags(), new LatLon(49.3585569, 6.6114085), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            271045371L, new Tags(), new LatLon(49.3584817, 6.6113262), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            1564404053L, new Tags(), new LatLon(49.3584142, 6.6112330), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            380433105L, new Tags(), new LatLon(49.3583332, 6.6110082), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            1564404051L, new Tags(), new LatLon(49.3581672, 6.6104693), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            271045372L, new Tags(), new LatLon(49.3580296, 6.6100494), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            4368122218L, new Tags(), new LatLon(49.3579367, 6.6098528), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            380433109L, new Tags(), new LatLon(49.3574687, 6.6091462), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            958456409L, new Tags(), new LatLon(49.3572107, 6.6086725), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            392076818L, new Tags(), new LatLon(49.3760187, 6.5998606), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388303819L, new Tags(), new LatLon(49.3759279, 6.5995430), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388303820L, new Tags(), new LatLon(49.3758908, 6.5989640), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388303821L, new Tags(), new LatLon(49.3758629, 6.5984061), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388303822L, new Tags(), new LatLon(49.3757288, 6.5977195), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388303823L, new Tags(), new LatLon(49.3754717, 6.5966123), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388303824L, new Tags(), new LatLon(49.3753664, 6.5961804), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388303824L, new Tags(), new LatLon(49.3753664, 6.5961804), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            271118834L, new Tags(), new LatLon(49.3750945, 6.5958590), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            1868997254L, new Tags(), new LatLon(49.3757563, 6.5875836), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            1868997252L, new Tags(), new LatLon(49.3755775, 6.5880009), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            970729117L, new Tags(), new LatLon(49.3756761, 6.5877642), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            970729095L, new Tags(), new LatLon(49.3754756, 6.5882469), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            1868997249L, new Tags(), new LatLon(49.3754001, 6.5884214), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            1868997247L, new Tags(), new LatLon(49.3753354, 6.5886083), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            1868997245L, new Tags(), new LatLon(49.3752491, 6.5888072), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            1868997243L, new Tags(), new LatLon(49.3752136, 6.5889225), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            970729062L, new Tags(), new LatLon(49.3751882, 6.5890498), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            1868997241L, new Tags(), new LatLon(49.3751453, 6.5894322), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            970729042L, new Tags(), new LatLon(49.3751041, 6.5898210), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            1868997239L, new Tags(), new LatLon(49.3750402, 6.5903048), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            1868997237L, new Tags(), new LatLon(49.3750119, 6.5905780), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            1868997237L, new Tags(), new LatLon(49.3750119, 6.5905780), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            970729085L, new Tags(), new LatLon(49.3749936, 6.5908040), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            672818010L, new Tags(), new LatLon(49.3750914, 6.5915919), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            388307503L, new Tags(), new LatLon(49.3771619, 6.5978165), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            2092382193L, new Tags(), new LatLon(49.3770412, 6.5980252), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            2092382187L, new Tags(), new LatLon(49.3756790, 6.5929183), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            2092382197L, new Tags(), new LatLon(49.3756267, 6.5925052), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            2092382186L, new Tags(), new LatLon(49.3756531, 6.5924326), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            392064950L, new Tags(), new LatLon(49.3609021, 6.6187958), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            392076670L, new Tags(), new LatLon(49.3608505, 6.6185057), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            392076671L, new Tags(), new LatLon(49.3607276, 6.6183168), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            392076672L, new Tags(), new LatLon(49.3604648, 6.6179907), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            392076673L, new Tags(), new LatLon(49.3601685, 6.6175100), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            392076674L, new Tags(), new LatLon(49.3600288, 6.6170809), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            392076675L, new Tags(), new LatLon(49.3598108, 6.6163942), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            392076676L, new Tags(), new LatLon(49.3596654, 6.6158278), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            392076677L, new Tags(), new LatLon(49.3596654, 6.6156389), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            392076678L, new Tags(), new LatLon(49.3597213, 6.6154501), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            392076679L, new Tags(), new LatLon(49.3598667, 6.6152870), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            392076680L, new Tags(), new LatLon(49.3599785, 6.6152184), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            392076681L, new Tags(), new LatLon(49.3601853, 6.6152784), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            392076682L, new Tags(), new LatLon(49.3603139, 6.6153643), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            392076683L, new Tags(), new LatLon(49.3610741, 6.6161625), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            392076684L, new Tags(), new LatLon(49.3617058, 6.6167032), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            392076685L, new Tags(), new LatLon(49.3620524, 6.6169607), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            392076686L, new Tags(), new LatLon(49.3623598, 6.6172783), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            392076687L, new Tags(), new LatLon(49.3627567, 6.6174929), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            392076688L, new Tags(), new LatLon(49.3628294, 6.6174929), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            392076689L, new Tags(), new LatLon(49.3633101, 6.6173985), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            392076690L, new Tags(), new LatLon(49.3642995, 6.6174843), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            392076691L, new Tags(), new LatLon(49.3649423, 6.6175701), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            392076692L, new Tags(), new LatLon(49.3666639, 6.6171410), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            392076693L, new Tags(), new LatLon(49.3672899, 6.6171495), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            392076695L, new Tags(), new LatLon(49.3679439, 6.6173813), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            1237112228L, new Tags(), new LatLon(49.3683376, 6.6174307), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            392076697L, new Tags(), new LatLon(49.3684916, 6.6174500), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            1237112226L, new Tags(), new LatLon(49.3689501, 6.6174867), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            392076699L, new Tags(), new LatLon(49.3690282, 6.6174929), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            392076700L, new Tags(), new LatLon(49.3697324, 6.6173813), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            392076701L, new Tags(), new LatLon(49.3700677, 6.6174328), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            392076702L, new Tags(), new LatLon(49.3705205, 6.6174585), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            392076703L, new Tags(), new LatLon(49.3708558, 6.6174070), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            958456238L, new Tags(), new LatLon(49.3721483, 6.6170581), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            392076704L, new Tags(), new LatLon(49.3727951, 6.6168835), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            392076705L, new Tags(), new LatLon(49.3739296, 6.6166346), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            392076706L, new Tags(), new LatLon(49.3743935, 6.6165487), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            392076707L, new Tags(), new LatLon(49.3747008, 6.6165916), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            392076708L, new Tags(), new LatLon(49.3751535, 6.6167032), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            392076709L, new Tags(), new LatLon(49.3753323, 6.6153213), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            392076710L, new Tags(), new LatLon(49.3754888, 6.6139652), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            392076711L, new Tags(), new LatLon(49.3756397, 6.6128494), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            392076712L, new Tags(), new LatLon(49.3755447, 6.6118195), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            392076713L, new Tags(), new LatLon(49.3752653, 6.6106007), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            392076714L, new Tags(), new LatLon(49.3752094, 6.6096394), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            392076715L, new Tags(), new LatLon(49.3752653, 6.6085579), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            392076716L, new Tags(), new LatLon(49.3751759, 6.6073391), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            392076717L, new Tags(), new LatLon(49.3752206, 6.6069529), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            392076718L, new Tags(), new LatLon(49.3754162, 6.6059229), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            392076719L, new Tags(), new LatLon(49.3754553, 6.6055796), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            392076720L, new Tags(), new LatLon(49.3754162, 6.6051762), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            392076721L, new Tags(), new LatLon(49.3752653, 6.6044037), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            392076722L, new Tags(), new LatLon(49.3752653, 6.6040947), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            392076723L, new Tags(), new LatLon(49.3753044, 6.6038029), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            392076724L, new Tags(), new LatLon(49.3758912, 6.6007730), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            392076818L, new Tags(), new LatLon(49.3760187, 6.5998606), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            970729056L, new Tags(), new LatLon(49.3734240, 6.5856651), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            970729139L, new Tags(), new LatLon(49.3746245, 6.5856651), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            970729128L, new Tags(), new LatLon(49.3749440, 6.5877078), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            970729062L, new Tags(), new LatLon(49.3751882, 6.5890498), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            2092365275L, new Tags(), new LatLon(49.3738443, 6.5895513), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            2154980840L, new Tags(), new LatLon(49.3731316, 6.5896725), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            2154980839L, new Tags(), new LatLon(49.3731707, 6.5904975), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            2154980828L, new Tags(), new LatLon(49.3721465, 6.5909278), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            2154980829L, new Tags(), new LatLon(49.3719342, 6.5910447), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            2154980832L, new Tags(), new LatLon(49.3718671, 6.5912175), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            2154980836L, new Tags(), new LatLon(49.3718811, 6.5916037), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            2154980833L, new Tags(), new LatLon(49.3720487, 6.5942108), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            2154980835L, new Tags(), new LatLon(49.3730296, 6.5973264), null, null, false, -1));
    graph.addVertex(
        createOsmogridNode(
            7584032811027286594L,
            new Tags(),
            new LatLon(49.3774666, 6.5923535),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3773781, 6.5921422),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            4998727616509923180L,
            new Tags(),
            new LatLon(49.3755017, 6.5921789),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3756091, 6.5921148),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            3043955112838185573L,
            new Tags(),
            new LatLon(49.3777523, 6.5963748),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3778958, 6.5962935),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            388307698L,
            new Tags(),
            new LatLon(49.3779964, 6.5968005),
            Quantities.getQuantity(4.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3782094, 6.5966890),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            6021715049468872232L,
            new Tags(),
            new LatLon(49.3757122, 6.5925169),
            Quantities.getQuantity(4.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3759261, 6.5923443),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            8084083437137248903L,
            new Tags(),
            new LatLon(49.3775678, 6.5948710),
            Quantities.getQuantity(4.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3776956, 6.5947325),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            3827915409570024960L,
            new Tags(),
            new LatLon(49.3771119, 6.5925564),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3769467, 6.5923234),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            4437497689979176156L,
            new Tags(),
            new LatLon(49.3751149, 6.5925881),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3749720, 6.5925581),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            7801906562737718204L,
            new Tags(),
            new LatLon(49.3761569, 6.5956820),
            Quantities.getQuantity(4.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3759019, 6.5954797),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            388308840L,
            new Tags(),
            new LatLon(49.3766014, 6.5927643),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3765879, 6.5923831),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            3693633237338964728L,
            new Tags(),
            new LatLon(49.3784649, 6.5963485),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3785985, 6.5962818),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            5496130406750373111L,
            new Tags(),
            new LatLon(49.3776449, 6.5961854),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3778028, 6.5960959),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            2581337076587448376L,
            new Tags(),
            new LatLon(49.3762236, 6.5927755),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3762237, 6.5930036),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            8378328344009394040L,
            new Tags(),
            new LatLon(49.3777569, 6.5963830),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3776220, 6.5964595),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            933295862165426117L,
            new Tags(),
            new LatLon(49.3750863, 6.5929134),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3751936, 6.5929119),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            5765036547391112411L,
            new Tags(),
            new LatLon(49.3750281, 6.5914866),
            Quantities.getQuantity(8.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3747792, 6.5916362),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            88054582532394834L,
            new Tags(),
            new LatLon(49.3750905, 6.5932062),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3749307, 6.5932085),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            8350808787971557777L,
            new Tags(),
            new LatLon(49.3773219, 6.5931936),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3775404, 6.5934192),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            1439824552153533531L,
            new Tags(),
            new LatLon(49.3762686, 6.5927755),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3762685, 6.5925327),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            3993789031083688389L,
            new Tags(),
            new LatLon(49.3751511, 6.5924143),
            Quantities.getQuantity(4.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3752487, 6.5924334),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            6270815926028619033L,
            new Tags(),
            new LatLon(49.3770497, 6.5939927),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3771914, 6.5940023),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            7840955676788212143L,
            new Tags(),
            new LatLon(49.3754175, 6.5949519),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3752215, 6.5950114),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            6407100744050753997L,
            new Tags(),
            new LatLon(49.3770025, 6.5931344),
            Quantities.getQuantity(4.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3771651, 6.5930412),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            1032021398069726705L,
            new Tags(),
            new LatLon(49.3773711, 6.5976050),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3772330, 6.5974841),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            2429362061069861692L,
            new Tags(),
            new LatLon(49.3776930, 6.5972251),
            Quantities.getQuantity(4.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3775210, 6.5971022),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            2718064068870032588L,
            new Tags(),
            new LatLon(49.3764877, 6.5952109),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3763090, 6.5951038),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            2714782558L,
            new Tags(),
            new LatLon(49.3760156, 6.5935158),
            Quantities.getQuantity(4.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3760991, 6.5935838),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            1298484650948840734L,
            new Tags(),
            new LatLon(49.3769394, 6.5944867),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3767965, 6.5944196),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            3132526446030638532L,
            new Tags(),
            new LatLon(49.3768196, 6.5972046),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3769611, 6.5971255),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            7138083082522935359L,
            new Tags(),
            new LatLon(49.3769237, 6.5973907),
            Quantities.getQuantity(4.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3770920, 6.5972966),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            3694468447708201780L,
            new Tags(),
            new LatLon(49.3782595, 6.5959564),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3783870, 6.5958872),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            6355715002474382105L,
            new Tags(),
            new LatLon(49.3759512, 6.5933340),
            Quantities.getQuantity(4.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3760705, 6.5932918),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            459102476665766029L,
            new Tags(),
            new LatLon(49.3765342, 6.5967488),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3764020, 6.5968369),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            5285354126639187738L,
            new Tags(),
            new LatLon(49.3770152, 6.5958948),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3770951, 6.5959704),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            3646157420429592233L,
            new Tags(),
            new LatLon(49.3764897, 6.5927688),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3764760, 6.5924255),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            9201669643006003591L,
            new Tags(),
            new LatLon(49.3746346, 6.5908317),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3744686, 6.5909315),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            8052286791415320450L,
            new Tags(),
            new LatLon(49.3757294, 6.5977225),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3758974, 6.5976897),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            1060887076877125589L,
            new Tags(),
            new LatLon(49.3767170, 6.5970230),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3768460, 6.5969371),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            6336769097111062092L,
            new Tags(),
            new LatLon(49.3776886, 6.5949823),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3775960, 6.5950828),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            970729082L,
            new Tags(),
            new LatLon(49.3773274, 6.5965460),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3773121, 6.5967856),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            1275397096939406253L,
            new Tags(),
            new LatLon(49.3779071, 6.5953119),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3780605, 6.5952273),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            5790691016049312648L,
            new Tags(),
            new LatLon(49.3782455, 6.5920723),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3781303, 6.5917593),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            1998696969552415861L,
            new Tags(),
            new LatLon(49.3745322, 6.5906612),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3746445, 6.5905937),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            2092382193L,
            new Tags(),
            new LatLon(49.3770412, 6.5980252),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3771179, 6.5981702),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            7130420768753732398L,
            new Tags(),
            new LatLon(49.3775486, 6.5948532),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3774545, 6.5949553),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            5243348599735340493L,
            new Tags(),
            new LatLon(49.3773340, 6.5965375),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3774668, 6.5966410),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            970729051L,
            new Tags(),
            new LatLon(49.3773897, 6.5947067),
            Quantities.getQuantity(1.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3775882, 6.5944277),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            2803027553190235188L,
            new Tags(),
            new LatLon(49.3757572, 6.5978651),
            Quantities.getQuantity(1.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3760343, 6.5978110),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            1997433474448051218L,
            new Tags(),
            new LatLon(49.3771160, 6.5978959),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3772625, 6.5979807),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            7311476090275187063L,
            new Tags(),
            new LatLon(49.3772928, 6.5976944),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3774261, 6.5978111),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            1812292526948040847L,
            new Tags(),
            new LatLon(49.3758334, 6.5982550),
            Quantities.getQuantity(1.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3761483, 6.5981935),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            970729044L,
            new Tags(),
            new LatLon(49.3769490, 6.5959648),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3769984, 6.5962008),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            9146167944477951202L,
            new Tags(),
            new LatLon(49.3775467, 6.5962646),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3774432, 6.5961840),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            2459300685067669267L,
            new Tags(),
            new LatLon(49.3753349, 6.5930497),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3753803, 6.5931685),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            4933810452401768723L,
            new Tags(),
            new LatLon(49.3779563, 6.5968566),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3780852, 6.5969487),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            7024592874196386841L,
            new Tags(),
            new LatLon(49.3779161, 6.5966633),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3778090, 6.5967261),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            2734646973726673811L,
            new Tags(),
            new LatLon(49.3775826, 6.5929410),
            Quantities.getQuantity(5.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3777520, 6.5931158),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            970729110L,
            new Tags(),
            new LatLon(49.3760731, 6.5939994),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3759475, 6.5940124),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            8766091262754244376L,
            new Tags(),
            new LatLon(49.3774158, 6.5964325),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3772992, 6.5963416),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            7914733434204210917L,
            new Tags(),
            new LatLon(49.3751238, 6.5925454),
            Quantities.getQuantity(1.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3742592, 6.5923638),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            2024279690927686243L,
            new Tags(),
            new LatLon(49.3779051, 6.5953083),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3778134, 6.5953589),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            9030512673428226653L,
            new Tags(),
            new LatLon(49.3745627, 6.5907119),
            Quantities.getQuantity(1.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3743170, 6.5908596),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            8914517618344873751L,
            new Tags(),
            new LatLon(49.3778280, 6.5970361),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3776787, 6.5969294),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            1519599971976891479L,
            new Tags(),
            new LatLon(49.3751751, 6.5919689),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3750147, 6.5920045),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            8571089117169403965L,
            new Tags(),
            new LatLon(49.3753324, 6.5946669),
            Quantities.getQuantity(4.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3751175, 6.5947277),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            4654899987586174208L,
            new Tags(),
            new LatLon(49.3778167, 6.5970519),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3779487, 6.5971463),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            3210866191244610803L,
            new Tags(),
            new LatLon(49.3758244, 6.5982089),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3759836, 6.5981779),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            2411194461765912863L,
            new Tags(),
            new LatLon(49.3753484, 6.5947234),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3756065, 6.5946506),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            7973951764520779810L,
            new Tags(),
            new LatLon(49.3765789, 6.5948111),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3764821, 6.5948729),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            2589635373340705695L,
            new Tags(),
            new LatLon(49.3757754, 6.5979582),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3759368, 6.5979267),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            7424833642358263333L,
            new Tags(),
            new LatLon(49.3759292, 6.5932720),
            Quantities.getQuantity(4.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3757829, 6.5933239),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            970729082L,
            new Tags(),
            new LatLon(49.3773274, 6.5965460),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3771629, 6.5965472),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            8666940762377373251L,
            new Tags(),
            new LatLon(49.3771002, 6.5977061),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3769731, 6.5977773),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            304882005755120410L,
            new Tags(),
            new LatLon(49.3764582, 6.5946218),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3763556, 6.5946873),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            4280465131080797064L,
            new Tags(),
            new LatLon(49.3763359, 6.5965608),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3762227, 6.5967472),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            8475986428622163431L,
            new Tags(),
            new LatLon(49.3761393, 6.5964176),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3763262, 6.5962572),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            4513119705808323274L,
            new Tags(),
            new LatLon(49.3757138, 6.5976548),
            Quantities.getQuantity(1.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3759834, 6.5975922),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            892349675621468443L,
            new Tags(),
            new LatLon(49.3765306, 6.5951392),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3766502, 6.5952108),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            680671198064297448L,
            new Tags(),
            new LatLon(49.3767951, 6.5947168),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3766608, 6.5946292),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            3976457997697240688L,
            new Tags(),
            new LatLon(49.3760353, 6.5957994),
            Quantities.getQuantity(4.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3760890, 6.5959255),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            4286756911375468464L,
            new Tags(),
            new LatLon(49.3763176, 6.5944013),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3762228, 6.5944618),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            7529881389863751140L,
            new Tags(),
            new LatLon(49.3763708, 6.5954062),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3761794, 6.5952916),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            1145959346490526892L,
            new Tags(),
            new LatLon(49.3764707, 6.5966535),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3766122, 6.5965592),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            2317961701649173903L,
            new Tags(),
            new LatLon(49.3762969, 6.5943690),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3764132, 6.5942949),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            5923904934485574477L,
            new Tags(),
            new LatLon(49.3765942, 6.5968387),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3767268, 6.5967504),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            1688380869059233279L,
            new Tags(),
            new LatLon(49.3779559, 6.5947226),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3780643, 6.5948124),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            4238963248129851794L,
            new Tags(),
            new LatLon(49.3761850, 6.5941851),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3760932, 6.5942404),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            87417343118559515L,
            new Tags(),
            new LatLon(49.3755010, 6.5955631),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3753207, 6.5955397),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            7753119416939135292L,
            new Tags(),
            new LatLon(49.3780131, 6.5921579),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3779309, 6.5919347),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            2667341346163280785L,
            new Tags(),
            new LatLon(49.3766586, 6.5969354),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3765179, 6.5970292),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            5732106181316660937L,
            new Tags(),
            new LatLon(49.3750136, 6.5909648),
            Quantities.getQuantity(5.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3749280, 6.5909755),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            3349247679943167851L,
            new Tags(),
            new LatLon(49.3770798, 6.5935814),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3769867, 6.5935569),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            6392877151096292852L,
            new Tags(),
            new LatLon(49.3755089, 6.5954118),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3756269, 6.5954088),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            2196588529475928917L,
            new Tags(),
            new LatLon(49.3771004, 6.5947878),
            Quantities.getQuantity(7.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3769436, 6.5948900),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            2626056003933654963L,
            new Tags(),
            new LatLon(49.3777014, 6.5972133),
            Quantities.getQuantity(4.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3778258, 6.5973022),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            8535974006583345975L,
            new Tags(),
            new LatLon(49.3782454, 6.5952957),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3783821, 6.5953819),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            6683501840636068357L,
            new Tags(),
            new LatLon(49.3768940, 6.5973376),
            Quantities.getQuantity(4.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3767322, 6.5974282),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            3892150939695599412L,
            new Tags(),
            new LatLon(49.3750004, 6.5908585),
            Quantities.getQuantity(12.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3753491, 6.5908153),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            1661380344260215149L,
            new Tags(),
            new LatLon(49.3758299, 6.5929917),
            Quantities.getQuantity(5.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3759576, 6.5929465),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            3142294360397729573L,
            new Tags(),
            new LatLon(49.3761285, 6.5964050),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3760249, 6.5964940),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            134890294000108424L,
            new Tags(),
            new LatLon(49.3764158, 6.5945554),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3765469, 6.5944718),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            686464964686857733L,
            new Tags(),
            new LatLon(49.3770343, 6.5942191),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3769106, 6.5942107),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            3001070114674265383L,
            new Tags(),
            new LatLon(49.3763708, 6.5965820),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3764876, 6.5963897),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            8903718498648147400L,
            new Tags(),
            new LatLon(49.3748275, 6.5911528),
            Quantities.getQuantity(4.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3746623, 6.5912521),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            9080254421840773650L,
            new Tags(),
            new LatLon(49.3775605, 6.5973887),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3776753, 6.5974892),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            7489504300060853487L,
            new Tags(),
            new LatLon(49.3773651, 6.5957264),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3775392, 6.5956198),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            6391815535295352315L,
            new Tags(),
            new LatLon(49.3770523, 6.5939537),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3769308, 6.5939455),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            1842047865910282586L,
            new Tags(),
            new LatLon(49.3767851, 6.5971428),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3766243, 6.5972328),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            64291990424077358L,
            new Tags(),
            new LatLon(49.3783183, 6.5960646),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3786116, 6.5959053),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            388308841L,
            new Tags(),
            new LatLon(49.3767275, 6.5928123),
            Quantities.getQuantity(8.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3766517, 6.5929971),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            7609839868254768021L,
            new Tags(),
            new LatLon(49.3763732, 6.5954021),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3764900, 6.5954720),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            2714356250432982263L,
            new Tags(),
            new LatLon(49.3781187, 6.5921190),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3782073, 6.5923596),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            8060314717796977964L,
            new Tags(),
            new LatLon(49.3778738, 6.5965891),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3780375, 6.5964963),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            9001575307390307353L,
            new Tags(),
            new LatLon(49.3778098, 6.5948989),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3779414, 6.5950079),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            6526675762620156274L,
            new Tags(),
            new LatLon(49.3779423, 6.5921839),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3777739, 6.5917267),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            7001984334661044938L,
            new Tags(),
            new LatLon(49.3771317, 6.5957716),
            Quantities.getQuantity(4.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3769600, 6.5956092),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            8692206324610779672L,
            new Tags(),
            new LatLon(49.3781222, 6.5954913),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3782570, 6.5955762),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            4968962965725594200L,
            new Tags(),
            new LatLon(49.3773975, 6.5957794),
            Quantities.getQuantity(4.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3772719, 6.5958563),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            6233703070244750130L,
            new Tags(),
            new LatLon(49.3775062, 6.5959568),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3776545, 6.5958661),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            5141469655394372348L,
            new Tags(),
            new LatLon(49.3769957, 6.5975194),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3768700, 6.5975897),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            6169233413584274315L,
            new Tags(),
            new LatLon(49.3750921, 6.5932976),
            Quantities.getQuantity(4.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3752222, 6.5932899),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            7130026094235306005L,
            new Tags(),
            new LatLon(49.3776414, 6.5927310),
            Quantities.getQuantity(10.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3779696, 6.5928081),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            296232887093380762L,
            new Tags(),
            new LatLon(49.3751427, 6.5918230),
            Quantities.getQuantity(8.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3752931, 6.5917897),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            830011807837013527L,
            new Tags(),
            new LatLon(49.3752074, 6.5942230),
            Quantities.getQuantity(7.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3749559, 6.5942922),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            6358237269264515869L,
            new Tags(),
            new LatLon(49.3778041, 6.5922349),
            Quantities.getQuantity(4.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3777269, 6.5920252),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            1043383221157251323L,
            new Tags(),
            new LatLon(49.3761507, 6.5941281),
            Quantities.getQuantity(4.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3762881, 6.5940454),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            8606320085903429403L,
            new Tags(),
            new LatLon(49.3773870, 6.5931304),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3776634, 6.5934157),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            7106417206342730336L,
            new Tags(),
            new LatLon(49.3770958, 6.5935205),
            Quantities.getQuantity(6.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3772910, 6.5935719),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            7001787228082294321L,
            new Tags(),
            new LatLon(49.3751421, 6.5924584),
            Quantities.getQuantity(1.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3744090, 6.5923044),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            8422283110615632732L,
            new Tags(),
            new LatLon(49.3776171, 6.5928343),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3774418, 6.5927932),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            3896569637154277718L,
            new Tags(),
            new LatLon(49.3755015, 6.5955595),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3756956, 6.5955848),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            1638784503241328128L,
            new Tags(),
            new LatLon(49.3747755, 6.5910661),
            Quantities.getQuantity(1.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3744335, 6.5912716),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            7196320059751154154L,
            new Tags(),
            new LatLon(49.3754434, 6.5921100),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3755732, 6.5919930),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            3188821732849568178L,
            new Tags(),
            new LatLon(49.3750863, 6.5929073),
            Quantities.getQuantity(1.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3742828, 6.5929187),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            6925886492835529034L,
            new Tags(),
            new LatLon(49.3750873, 6.5929811),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3749156, 6.5929835),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            388308834L,
            new Tags(),
            new LatLon(49.3753935, 6.5920547),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3755323, 6.5918544),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            8119972202863806476L,
            new Tags(),
            new LatLon(49.3758914, 6.5931653),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3761512, 6.5930733),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            4852974727684441800L,
            new Tags(),
            new LatLon(49.3769390, 6.5927408),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3770364, 6.5928260),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            4268303527865371422L,
            new Tags(),
            new LatLon(49.3763229, 6.5927754),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3763292, 6.5929337),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            388308526L,
            new Tags(),
            new LatLon(49.3750861, 6.5928958),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3743929, 6.5928951),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            7454249236436438068L,
            new Tags(),
            new LatLon(49.3748995, 6.5912725),
            Quantities.getQuantity(1.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3745069, 6.5915084),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            5787363696224848186L,
            new Tags(),
            new LatLon(49.3770297, 6.5942869),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3771951, 6.5942982),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            2900530286981171935L,
            new Tags(),
            new LatLon(49.3770595, 6.5938477),
            Quantities.getQuantity(1.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3776125, 6.5938852),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            8047696828710144620L,
            new Tags(),
            new LatLon(49.3772449, 6.5932681),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3774413, 6.5934709),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            7368707314296308751L,
            new Tags(),
            new LatLon(49.3772717, 6.5954596),
            Quantities.getQuantity(1.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3775658, 6.5954219),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            6340600088584605243L,
            new Tags(),
            new LatLon(49.3753096, 6.5920675),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3753273, 6.5921831),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            7690246658304986048L,
            new Tags(),
            new LatLon(49.3779786, 6.5954415),
            Quantities.getQuantity(1.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3777553, 6.5955648),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            2147773720947672586L,
            new Tags(),
            new LatLon(49.3776372, 6.5973011),
            Quantities.getQuantity(1.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3781424, 6.5977435),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            5443737985321419651L,
            new Tags(),
            new LatLon(49.3770433, 6.5940868),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3772838, 6.5941031),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            513618694971805307L,
            new Tags(),
            new LatLon(49.3772262, 6.5950784),
            Quantities.getQuantity(1.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3773080, 6.5950682),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            544133599336091077L,
            new Tags(),
            new LatLon(49.3766135, 6.5927674),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3766562, 6.5925990),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            388307507L,
            new Tags(),
            new LatLon(49.3776450, 6.5972922),
            Quantities.getQuantity(1.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3780520, 6.5976274),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            2763521509518099421L,
            new Tags(),
            new LatLon(49.3751172, 6.5937212),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3752423, 6.5937139),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            2494294375757988114L,
            new Tags(),
            new LatLon(49.3787402, 6.5968999),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3786738, 6.5969331),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            3296906060377901311L,
            new Tags(),
            new LatLon(49.3785990, 6.5966171),
            Quantities.getQuantity(1.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3786890, 6.5965722),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            5592705687116006062L,
            new Tags(),
            new LatLon(49.3763045, 6.5954959),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3764127, 6.5955818),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            970729051L,
            new Tags(),
            new LatLon(49.3773897, 6.5947067),
            Quantities.getQuantity(1.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3774248, 6.5946322),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            5105125748883407655L,
            new Tags(),
            new LatLon(49.3751828, 6.5941334),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3753325, 6.5940923),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            2638979301239113785L,
            new Tags(),
            new LatLon(49.3780496, 6.5955701),
            Quantities.getQuantity(1.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3779229, 6.5956401),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            2492480840685932176L,
            new Tags(),
            new LatLon(49.3751208, 6.5917243),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3750093, 6.5917491),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            8703044937646623709L,
            new Tags(),
            new LatLon(49.3750817, 6.5915136),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3752299, 6.5914953),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            4393061227427020840L,
            new Tags(),
            new LatLon(49.3756303, 6.5925339),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3754654, 6.5925548),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            964650462412032466L,
            new Tags(),
            new LatLon(49.3772525, 6.5924567),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3771184, 6.5922676),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            6649840792907566349L,
            new Tags(),
            new LatLon(49.3752925, 6.5945256),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3754294, 6.5944870),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            3776874449008937337L,
            new Tags(),
            new LatLon(49.3752303, 6.5943054),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3754084, 6.5942552),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            7954896405919779106L,
            new Tags(),
            new LatLon(49.3763734, 6.5927734),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3763616, 6.5924773),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            1753028448782208694L,
            new Tags(),
            new LatLon(49.3751570, 6.5923840),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3750079, 6.5923549),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            6661912841003157672L,
            new Tags(),
            new LatLon(49.3776352, 6.5927574),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3775144, 6.5927290),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            3722501285985469782L,
            new Tags(),
            new LatLon(49.3776709, 6.5925945),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3774988, 6.5925726),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            790919506390304281L,
            new Tags(),
            new LatLon(49.3755867, 6.5923213),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3754463, 6.5924051),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            1360843264331631155L,
            new Tags(),
            new LatLon(49.3756553, 6.5927308),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3755087, 6.5927494),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            6834598874174670950L,
            new Tags(),
            new LatLon(49.3751392, 6.5939268),
            Quantities.getQuantity(4.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3752774, 6.5939079),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            388308840L,
            new Tags(),
            new LatLon(49.3766014, 6.5927643),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3766330, 6.5924691),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            5823242403824617170L,
            new Tags(),
            new LatLon(49.3750427, 6.5911994),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3751332, 6.5911882),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            8878887242267838478L,
            new Tags(),
            new LatLon(49.3771905, 6.5925007),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3770099, 6.5922459),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            3981575480206445807L,
            new Tags(),
            new LatLon(49.3750987, 6.5934095),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3749465, 6.5934186),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            1090085942029468665L,
            new Tags(),
            new LatLon(49.3755275, 6.5922221),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3754024, 6.5922968),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            6955418595997927893L,
            new Tags(),
            new LatLon(49.3753940, 6.5930271),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3753372, 6.5928785),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            1158815579581465300L,
            new Tags(),
            new LatLon(49.3750893, 6.5927839),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3749334, 6.5927795),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            8470901461510799545L,
            new Tags(),
            new LatLon(49.3774048, 6.5923794),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3774519, 6.5924918),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            1361479772216381079L,
            new Tags(),
            new LatLon(49.3759310, 6.5927202),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3760145, 6.5925027),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            63032227231976117L,
            new Tags(),
            new LatLon(49.3775103, 6.5930111),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3773476, 6.5928431),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            5124099288808835058L,
            new Tags(),
            new LatLon(49.3751087, 6.5935774),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3749590, 6.5935863),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            748426143412273979L,
            new Tags(),
            new LatLon(49.3755785, 6.5923075),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3757301, 6.5922171),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            6694291219951734364L,
            new Tags(),
            new LatLon(49.3772672, 6.5954246),
            Quantities.getQuantity(4.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3771138, 6.5954443),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            1620564222610262814L,
            new Tags(),
            new LatLon(49.3751233, 6.5938110),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3749830, 6.5938302),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            298569470487711238L,
            new Tags(),
            new LatLon(49.3774220, 6.5930966),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3772703, 6.5929401),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            7080936905058110772L,
            new Tags(),
            new LatLon(49.3755067, 6.5953246),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3752453, 6.5953312),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            631267178578002493L,
            new Tags(),
            new LatLon(49.3769604, 6.5927163),
            Quantities.getQuantity(4.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3768143, 6.5925885),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            3543561509980622302L,
            new Tags(),
            new LatLon(49.3751584, 6.5918937),
            Quantities.getQuantity(2.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3747874, 6.5919760),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            1561872608925860803L,
            new Tags(),
            new LatLon(49.3761554, 6.5927756),
            Quantities.getQuantity(3.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3761553, 6.5924506),
            false,
            -1));
    graph.addVertex(
        createOsmogridNode(
            395484186890030123L,
            new Tags(),
            new LatLon(49.3754671, 6.5951219),
            Quantities.getQuantity(1.0, PowerSystemUnits.KILOWATT),
            new LatLon(49.3755351, 6.5951038),
            true,
            -1));

    return graph;
  }

  /**
   * Builds a map from node id to a pair of actual node lat lon (first element) and house connection
   * point lat lon (second element).
   *
   * @return The said map
   */
  public static Map<Long, Pair<LatLon, LatLon>> getNodeToLatLon() {
    Map<Long, Pair<LatLon, LatLon>> map = new HashMap<>();

    map.put(
        388307507L, Pair.of(new LatLon(49.377645, 6.5972922), new LatLon(49.378052, 6.59762735)));
    map.put(
        388307698L,
        Pair.of(
            new LatLon(49.3779964, 6.5968005000000005),
            new LatLon(49.378209350000006, 6.5966890000000005)));
    map.put(
        388308526L,
        Pair.of(
            new LatLon(49.375086100000004, 6.5928958), new LatLon(49.374392900000004, 6.5928951)));
    map.put(
        388308834L,
        Pair.of(new LatLon(49.3753935, 6.5920547), new LatLon(49.3755323, 6.591854400000001)));
    map.put(
        388308840L,
        Pair.of(
            new LatLon(49.376601400000006, 6.592764300000001), new LatLon(49.376633, 6.59246905)));
    map.put(
        388308841L,
        Pair.of(new LatLon(49.3767275, 6.5928123), new LatLon(49.37665165, 6.592997050000001)));
    map.put(
        970729051L,
        Pair.of(new LatLon(49.3773897, 6.594706700000001), new LatLon(49.3774248, 6.5946322)));
    map.put(
        970729082L,
        Pair.of(
            new LatLon(49.377327400000006, 6.596546), new LatLon(49.377162850000005, 6.5965472)));
    map.put(
        970729044L,
        Pair.of(
            new LatLon(49.376949, 6.595964800000001), new LatLon(49.376998400000005, 6.59620075)));
    map.put(
        970729110L,
        Pair.of(new LatLon(49.376073100000006, 6.5939994), new LatLon(49.3759475, 6.59401235)));
    map.put(
        2714782558L,
        Pair.of(
            new LatLon(49.3760156, 6.5935158000000005),
            new LatLon(49.37609905000001, 6.593583800000001)));
    map.put(
        2092382193L,
        Pair.of(
            new LatLon(49.3770412, 6.5980252), new LatLon(49.377117850000005, 6.5981701500000005)));
    map.put(
        7631346241323879694L,
        Pair.of(
            new LatLon(49.37746659536283, 6.592353541307965),
            new LatLon(49.377378050000004, 6.592142150000001)));
    map.put(
        344948223476912078L,
        Pair.of(
            new LatLon(49.37550168581469, 6.592178909817812),
            new LatLon(49.375609100000005, 6.592114800000001)));
    map.put(
        333321857425034124L,
        Pair.of(
            new LatLon(49.37775226777416, 6.596374829733096),
            new LatLon(49.377895800000005, 6.596293450000001)));
    map.put(
        2011397671468421085L,
        Pair.of(
            new LatLon(49.375712165656914, 6.592516912759656), new LatLon(49.3759261, 6.59234425)));
    map.put(
        567098690544158673L,
        Pair.of(
            new LatLon(49.377567838021285, 6.594870955727992),
            new LatLon(49.37769555, 6.59473245)));
    map.put(
        5287362806949367737L,
        Pair.of(
            new LatLon(49.37711194323732, 6.592556401789057),
            new LatLon(49.376946700000005, 6.59232335)));
    map.put(
        5149871561494908912L,
        Pair.of(
            new LatLon(49.375114878302625, 6.59258806464531),
            new LatLon(49.374972, 6.592558050000001)));
    map.put(
        8679228825976389708L,
        Pair.of(
            new LatLon(49.376156942283885, 6.595681972120432),
            new LatLon(49.375901850000005, 6.59547965)));
    map.put(
        5553239865115297219L,
        Pair.of(
            new LatLon(49.37846485432139, 6.59634847177124),
            new LatLon(49.37859850000001, 6.596281750000001)));
    map.put(
        1973680976754658160L,
        Pair.of(
            new LatLon(49.37764488474663, 6.5961854346051805),
            new LatLon(49.377802800000005, 6.5960959)));
    map.put(
        3600500065724154156L,
        Pair.of(
            new LatLon(49.37622355695206, 6.592775539511647), new LatLon(49.37622365, 6.5930036)));
    map.put(
        3002476766672011622L,
        Pair.of(
            new LatLon(49.37775690635323, 6.596383010954046), new LatLon(49.377622, 6.5964595)));
    map.put(
        5339510679671816785L,
        Pair.of(
            new LatLon(49.375086349015504, 6.592913416068036),
            new LatLon(49.3751936, 6.592911900000001)));
    map.put(
        4961249724263057372L,
        Pair.of(
            new LatLon(49.375028122363034, 6.591486585859443),
            new LatLon(49.374779200000006, 6.59163615)));
    map.put(
        7054331223659793440L,
        Pair.of(
            new LatLon(49.37509049620333, 6.5932062195854115), new LatLon(49.37493065, 6.5932085)));
    map.put(
        6161861297170304057L,
        Pair.of(
            new LatLon(49.37732188332642, 6.593193563319845),
            new LatLon(49.3775404, 6.593419150000001)));
    map.put(
        6167847070128359680L,
        Pair.of(
            new LatLon(49.37626859907024, 6.592775521134611),
            new LatLon(49.37626850000001, 6.5925327000000005)));
    map.put(
        8116661199788592091L,
        Pair.of(
            new LatLon(49.3751511183776, 6.59241428842483), new LatLon(49.3752487, 6.59243335)));
    map.put(
        6434898823099664218L,
        Pair.of(
            new LatLon(49.377049661097324, 6.593992685458392), new LatLon(49.3771914, 6.5940023)));
    map.put(
        8140509388135876669L,
        Pair.of(
            new LatLon(49.37541754600085, 6.594951925216033),
            new LatLon(49.375221450000005, 6.59501135)));
    map.put(
        6127614691009839111L,
        Pair.of(
            new LatLon(49.377002527748246, 6.593134448641027),
            new LatLon(49.37716505, 6.59304115)));
    map.put(
        7630059381296416433L,
        Pair.of(
            new LatLon(49.37737109827245, 6.597604982005926),
            new LatLon(49.377233000000004, 6.59748405)));
    map.put(
        589338092379849714L,
        Pair.of(
            new LatLon(49.37769298269006, 6.597225059736194), new LatLon(49.377521, 6.59710215)));
    map.put(
        4904010700539971368L,
        Pair.of(
            new LatLon(49.37648772505502, 6.595210850717259),
            new LatLon(49.37630895000001, 6.5951038)));
    map.put(
        5357617564998322606L,
        Pair.of(
            new LatLon(49.376939389255746, 6.594486736085956),
            new LatLon(49.376796500000005, 6.5944196)));
    map.put(
        3973018340605968481L,
        Pair.of(
            new LatLon(49.376819626789654, 6.597204639792543),
            new LatLon(49.3769611, 6.597125500000001)));
    map.put(
        8878862787543911444L,
        Pair.of(
            new LatLon(49.37692369308414, 6.597390672536579),
            new LatLon(49.37709195000001, 6.59729655)));
    map.put(
        6240738577815847697L,
        Pair.of(
            new LatLon(49.37825952851246, 6.595956388658906),
            new LatLon(49.378387000000004, 6.595887150000001)));
    map.put(
        5364032021844281281L,
        Pair.of(
            new LatLon(49.375951205200266, 6.593334048620073),
            new LatLon(49.37607045, 6.593291800000001)));
    map.put(
        7989908304054603415L,
        Pair.of(
            new LatLon(49.37653421098079, 6.596748759286008),
            new LatLon(49.37640195, 6.5968369000000004)));
    map.put(
        6587195426626227159L,
        Pair.of(
            new LatLon(49.37701519988717, 6.595894816707795), new LatLon(49.37709505, 6.59597035)));
    map.put(
        8053994087879557220L,
        Pair.of(
            new LatLon(49.376489683241985, 6.59276875276758),
            new LatLon(49.376476000000004, 6.59242545)));
    map.put(
        1289276254510730129L,
        Pair.of(
            new LatLon(49.37463461770889, 6.590831668791708), new LatLon(49.37446855, 6.59093145)));
    map.put(
        3043460009495380945L,
        Pair.of(
            new LatLon(49.37572937932071, 6.597722466156558), new LatLon(49.3758974, 6.59768965)));
    map.put(
        3846658138575752645L,
        Pair.of(
            new LatLon(49.37671699441945, 6.597023037945378), new LatLon(49.37684595, 6.5969371)));
    map.put(
        8953711385724145390L,
        Pair.of(
            new LatLon(49.37768860850159, 6.594982314566613),
            new LatLon(49.377596000000004, 6.59508275)));
    map.put(
        3313550393374359750L,
        Pair.of(
            new LatLon(49.377907088673, 6.595311884709776),
            new LatLon(49.378060500000004, 6.595227250000001)));
    map.put(
        4058317892605068070L,
        Pair.of(
            new LatLon(49.378245527800395, 6.592072262521143), new LatLon(49.37813025, 6.5917593)));
    map.put(
        5143871937691995681L,
        Pair.of(
            new LatLon(49.3745321699439, 6.590661163083226),
            new LatLon(49.374644450000005, 6.5905937)));
    map.put(
        9141592033056804035L,
        Pair.of(
            new LatLon(49.37754859007608, 6.594853207774867), new LatLon(49.3774545, 6.59495525)));
    map.put(
        4271968580922003004L,
        Pair.of(
            new LatLon(49.37733400975252, 6.59653751737487), new LatLon(49.37746675, 6.59664095)));
    map.put(
        1560736786190518586L,
        Pair.of(
            new LatLon(49.375757230246045, 6.59786506455579), new LatLon(49.3760343, 6.59781095)));
    map.put(
        2392936360984987335L,
        Pair.of(
            new LatLon(49.37711597482429, 6.597895908319558), new LatLon(49.3772625, 6.59798065)));
    map.put(
        2138503529693269421L,
        Pair.of(new LatLon(49.3772928123307, 6.59769438061656), new LatLon(49.3774261, 6.5978111)));
    map.put(
        3175547154312282379L,
        Pair.of(
            new LatLon(49.37583338023647, 6.598254957049648), new LatLon(49.3761483, 6.59819345)));
    map.put(
        1328926728937422959L,
        Pair.of(
            new LatLon(49.37754666320076, 6.596264608596718),
            new LatLon(49.377443150000005, 6.59618395)));
    map.put(
        9111622286273957503L,
        Pair.of(
            new LatLon(49.37533493050727, 6.593049704891098),
            new LatLon(49.3753803, 6.593168500000001)));
    map.put(
        4188089099766613220L,
        Pair.of(
            new LatLon(49.37795632115064, 6.596856580734865),
            new LatLon(49.378085150000004, 6.596948650000001)));
    map.put(
        4785370527103733235L,
        Pair.of(
            new LatLon(49.37791610628909, 6.596663346715527),
            new LatLon(49.377809, 6.596726050000001)));
    map.put(
        7017196318441424972L,
        Pair.of(
            new LatLon(49.3775826234316, 6.592940994948275),
            new LatLon(49.377751950000004, 6.5931158)));
    map.put(
        1114444174245512148L,
        Pair.of(
            new LatLon(49.37741584649518, 6.596432492200657),
            new LatLon(49.3772992, 6.596341600000001)));
    map.put(
        1430863780617670396L,
        Pair.of(
            new LatLon(49.375123831501696, 6.5925454448551095),
            new LatLon(49.37425915, 6.592363800000001)));
    map.put(
        7493769494378463739L,
        Pair.of(
            new LatLon(49.377905112905594, 6.595308303376541),
            new LatLon(49.37781340000001, 6.595358900000001)));
    map.put(
        6679416765401416961L,
        Pair.of(
            new LatLon(49.374562676683965, 6.59071193601448),
            new LatLon(49.374317000000005, 6.59085955)));
    map.put(
        8001444449239715342L,
        Pair.of(
            new LatLon(49.377828023851656, 6.597036102026581),
            new LatLon(49.37767865000001, 6.59692935)));
    map.put(
        7197491607117254566L,
        Pair.of(
            new LatLon(49.37517506444456, 6.591968900628577),
            new LatLon(49.37501465, 6.592004500000001)));
    map.put(
        5178131990727184131L,
        Pair.of(
            new LatLon(49.375332448598925, 6.594666926082983), new LatLon(49.3751175, 6.59472765)));
    map.put(
        3307426255641919623L,
        Pair.of(
            new LatLon(49.37781671542671, 6.597051925454432),
            new LatLon(49.377948700000005, 6.59714625)));
    map.put(
        5534743008150769526L,
        Pair.of(
            new LatLon(49.37582439170688, 6.598208935241928),
            new LatLon(49.37598355, 6.598177850000001)));
    map.put(
        9132962692176167059L,
        Pair.of(
            new LatLon(49.37534841567219, 6.594723445823412),
            new LatLon(49.37560645000001, 6.594650550000001)));
    map.put(
        4660407718591547215L,
        Pair.of(
            new LatLon(49.37657887655267, 6.594811143099818),
            new LatLon(49.376482100000004, 6.59487285)));
    map.put(
        2200519884393957653L,
        Pair.of(
            new LatLon(49.3757754241357, 6.5979582183562515),
            new LatLon(49.375936800000005, 6.5979267)));
    map.put(
        8898119821070518780L,
        Pair.of(
            new LatLon(49.375929218262996, 6.593271991494243),
            new LatLon(49.37578285000001, 6.593323850000001)));
    map.put(
        8933322122885877444L,
        Pair.of(
            new LatLon(49.37710016581778, 6.597706141700252),
            new LatLon(49.376973050000004, 6.59777725)));
    map.put(
        2607099156386826468L,
        Pair.of(
            new LatLon(49.37645816154314, 6.594621822579044),
            new LatLon(49.37635555, 6.594687250000001)));
    map.put(
        5628984247574809927L,
        Pair.of(
            new LatLon(49.37633589477624, 6.596560789305353),
            new LatLon(49.3762227, 6.596747200000001)));
    map.put(
        40147112826326180L,
        Pair.of(
            new LatLon(49.376139349267994, 6.596417599226308),
            new LatLon(49.37632620000001, 6.5962572)));
    map.put(
        4309462566727994407L,
        Pair.of(
            new LatLon(49.37571377808622, 6.597654808195478), new LatLon(49.3759834, 6.5975922)));
    map.put(
        8882163573716502357L,
        Pair.of(
            new LatLon(49.37653063794924, 6.595139186040732),
            new LatLon(49.37665015, 6.595210750000001)));
    map.put(
        6735214389577140504L,
        Pair.of(
            new LatLon(49.37679505194193, 6.594716755189244), new LatLon(49.37666075, 6.59462915)));
    map.put(
        1517763587944105946L,
        Pair.of(
            new LatLon(49.37603526833733, 6.595799363406455),
            new LatLon(49.37608895, 6.595925450000001)));
    map.put(
        8307424651960863606L,
        Pair.of(
            new LatLon(49.37631757520105, 6.594401337325372),
            new LatLon(49.376222750000004, 6.5944618)));
    map.put(
        766964281344216564L,
        Pair.of(
            new LatLon(49.376370767145595, 6.595406170816254),
            new LatLon(49.37617935, 6.595291550000001)));
    map.put(
        6226469018692700093L,
        Pair.of(
            new LatLon(49.37647069579332, 6.596653450539238), new LatLon(49.3766122, 6.59655915)));
    map.put(
        6835702154760505127L,
        Pair.of(
            new LatLon(49.37629694244143, 6.594368978426407),
            new LatLon(49.3764132, 6.594294850000001)));
    map.put(
        2050786792270876391L,
        Pair.of(
            new LatLon(49.37659415686953, 6.596838712063554),
            new LatLon(49.37672675, 6.596750350000001)));
    map.put(
        120265451016374069L,
        Pair.of(
            new LatLon(49.37795585875382, 6.594722563476851),
            new LatLon(49.378064300000005, 6.59481235)));
    map.put(
        3997526544572239246L,
        Pair.of(
            new LatLon(49.37618501565879, 6.5941850509022135), new LatLon(49.3760932, 6.5942404)));
    map.put(
        8739546914634091489L,
        Pair.of(
            new LatLon(49.37550101771098, 6.595563142982927), new LatLon(49.3753207, 6.5955397)));
    map.put(
        1849382510445284941L,
        Pair.of(
            new LatLon(49.37801307698097, 6.592157884336385),
            new LatLon(49.37793085, 6.591934650000001)));
    map.put(
        3665854743362357064L,
        Pair.of(
            new LatLon(49.37665861021973, 6.596935428585651),
            new LatLon(49.3765179, 6.597029200000001)));
    map.put(
        472778290337565728L,
        Pair.of(
            new LatLon(49.375013563320444, 6.590964829245159),
            new LatLon(49.374928000000004, 6.59097545)));
    map.put(
        5380668971616325405L,
        Pair.of(
            new LatLon(49.37707975699323, 6.593581384333767),
            new LatLon(49.3769867, 6.593556850000001)));
    map.put(
        6741535477342159688L,
        Pair.of(
            new LatLon(49.375508889698295, 6.59541176083194), new LatLon(49.3756269, 6.5954088)));
    map.put(
        5275059393739638624L,
        Pair.of(
            new LatLon(49.37710041535075, 6.594787831044898),
            new LatLon(49.376943600000004, 6.594889950000001)));
    map.put(
        8378773655438772722L,
        Pair.of(
            new LatLon(49.37770137640671, 6.5972133147433745), new LatLon(49.37782575, 6.5973022)));
    map.put(
        1112868100730146398L,
        Pair.of(
            new LatLon(49.378245401057576, 6.59529574306813),
            new LatLon(49.3783821, 6.595381850000001)));
    map.put(
        3299890516739638536L,
        Pair.of(
            new LatLon(49.376894029566145, 6.597337644938374), new LatLon(49.37673215, 6.5974282)));
    map.put(
        6140390782177002760L,
        Pair.of(
            new LatLon(49.37500036953936, 6.590858537014914), new LatLon(49.3753491, 6.59081525)));
    map.put(
        8396190869128366807L,
        Pair.of(
            new LatLon(49.37582990200228, 6.592991675886334), new LatLon(49.37595755, 6.59294645)));
    map.put(
        7056580209371136601L,
        Pair.of(
            new LatLon(49.37612852734238, 6.596404992652269),
            new LatLon(49.376024900000004, 6.596493950000001)));
    map.put(
        8055223059040718701L,
        Pair.of(
            new LatLon(49.376415780254234, 6.594555354890246),
            new LatLon(49.37654690000001, 6.59447175)));
    map.put(
        5255703721542503791L,
        Pair.of(
            new LatLon(49.37703430337391, 6.594219091141832),
            new LatLon(49.3769106, 6.5942107000000005)));
    map.put(
        2691229492112277775L,
        Pair.of(
            new LatLon(49.3763707691962, 6.59658196621353),
            new LatLon(49.37648755, 6.596389650000001)));
    map.put(
        6728396875173808848L,
        Pair.of(
            new LatLon(49.37482754582376, 6.591152762618539), new LatLon(49.3746623, 6.59125205)));
    map.put(
        7415395745233323893L,
        Pair.of(
            new LatLon(49.37756049047736, 6.59738870562721), new LatLon(49.37767525, 6.5974892)));
    map.put(
        507333351811204462L,
        Pair.of(
            new LatLon(49.377365078196995, 6.595726403261927), new LatLon(49.3775392, 6.5956198)));
    map.put(
        3199474451133517429L,
        Pair.of(
            new LatLon(49.377052302748766, 6.593953741867344),
            new LatLon(49.376930800000004, 6.5939455)));
    map.put(
        2427867643087373733L,
        Pair.of(
            new LatLon(49.37678505843465, 6.597142844126901),
            new LatLon(49.376624250000006, 6.5972328000000005)));
    map.put(
        1315041610333506617L,
        Pair.of(
            new LatLon(49.37831828829027, 6.596064568054683), new LatLon(49.3786116, 6.59590525)));
    map.put(
        4260123898067242386L,
        Pair.of(
            new LatLon(49.37637322112183, 6.595402072667755),
            new LatLon(49.376490000000004, 6.595472000000001)));
    map.put(
        7031801934305512980L,
        Pair.of(
            new LatLon(49.378118688619914, 6.59211898294529), new LatLon(49.3782073, 6.59235955)));
    map.put(
        5971136679098207650L,
        Pair.of(
            new LatLon(49.37787377174547, 6.596589130453762),
            new LatLon(49.378037500000005, 6.5964963)));
    map.put(
        2825101261551584820L,
        Pair.of(
            new LatLon(49.3778098188943, 6.594898945675468), new LatLon(49.37794135, 6.59500785)));
    map.put(
        6253185864117931469L,
        Pair.of(
            new LatLon(49.37794232367783, 6.592183945876046),
            new LatLon(49.37777390000001, 6.591726700000001)));
    map.put(
        1230597754413205622L,
        Pair.of(new LatLon(49.37713172038737, 6.595771636917579), new LatLon(49.37696, 6.5956092)));
    map.put(
        5476213106879842748L,
        Pair.of(
            new LatLon(49.37812222707096, 6.595491287762831),
            new LatLon(49.37825695000001, 6.59557615)));
    map.put(
        7574395280086550649L,
        Pair.of(
            new LatLon(49.37739751941547, 6.5957793915415595),
            new LatLon(49.377271900000004, 6.5958563)));
    map.put(
        6435827370559489229L,
        Pair.of(
            new LatLon(49.377506158720244, 6.595956838941221),
            new LatLon(49.37765445000001, 6.595866050000001)));
    map.put(
        2622466790800900424L,
        Pair.of(
            new LatLon(49.376995683375604, 6.597519365035262), new LatLon(49.37686995, 6.5975897)));
    map.put(
        3236137755584449681L,
        Pair.of(
            new LatLon(49.37509211317527, 6.59329758828997), new LatLon(49.37522215, 6.5932899)));
    map.put(
        4727673052506636540L,
        Pair.of(
            new LatLon(49.3776413891608, 6.592730972693941),
            new LatLon(49.37796955, 6.592808100000001)));
    map.put(
        6535731494310594790L,
        Pair.of(
            new LatLon(49.375142690008275, 6.591823018074847),
            new LatLon(49.37529305, 6.591789650000001)));
    map.put(
        3489175019655744565L,
        Pair.of(
            new LatLon(49.37520741381724, 6.594223039434587),
            new LatLon(49.3749559, 6.594292150000001)));
    map.put(
        1188142245785387947L,
        Pair.of(
            new LatLon(49.37780409583378, 6.592234861243663),
            new LatLon(49.37772685, 6.5920251500000004)));
    map.put(
        8546721877886257645L,
        Pair.of(
            new LatLon(49.37615070973024, 6.59412814263168), new LatLon(49.37628805, 6.59404535)));
    map.put(
        224536378665815520L,
        Pair.of(
            new LatLon(49.37738703928745, 6.593130449381296),
            new LatLon(49.377663350000006, 6.5934157)));
    map.put(
        2975718777976997428L,
        Pair.of(
            new LatLon(49.37709582103796, 6.593520454547959),
            new LatLon(49.37729095, 6.593571900000001)));
    map.put(
        5497112814334723565L,
        Pair.of(
            new LatLon(49.375142115739244, 6.59245840665097), new LatLon(49.374409, 6.5923044)));
    map.put(
        2767677098516106098L,
        Pair.of(
            new LatLon(49.37761709276201, 6.592834348878386), new LatLon(49.3774418, 6.59279315)));
    map.put(
        3543564196220783677L,
        Pair.of(
            new LatLon(49.37550148870355, 6.595559520222876), new LatLon(49.37569555, 6.59558475)));
    map.put(
        3137488759929585867L,
        Pair.of(
            new LatLon(49.37477546410462, 6.591066082050928), new LatLon(49.3744335, 6.59127155)));
    map.put(
        5504626007861513033L,
        Pair.of(
            new LatLon(49.375443404605235, 6.592110011764249),
            new LatLon(49.37557315, 6.59199295)));
    map.put(
        8721061978923748691L,
        Pair.of(
            new LatLon(49.37508626244449, 6.592907291786938),
            new LatLon(49.374282750000006, 6.5929186500000005)));
    map.put(
        6867389787020214772L,
        Pair.of(
            new LatLon(49.37508730539129, 6.592981072823631),
            new LatLon(49.3749156, 6.592983500000001)));
    map.put(
        351950318751796227L,
        Pair.of(
            new LatLon(49.37589142476066, 6.59316532106075),
            new LatLon(49.376151150000005, 6.5930733)));
    map.put(
        1202014498190609592L,
        Pair.of(
            new LatLon(49.37693897987726, 6.592740779557715),
            new LatLon(49.3770364, 6.5928260000000005)));
    map.put(
        2345752751261827556L,
        Pair.of(
            new LatLon(49.37632289056298, 6.59277540073201),
            new LatLon(49.3763292, 6.592933700000001)));
    map.put(
        1008752758135079010L,
        Pair.of(
            new LatLon(49.37489946041009, 6.591272451393526), new LatLon(49.37450685, 6.59150835)));
    map.put(
        3815516148111985001L,
        Pair.of(
            new LatLon(49.37702970163783, 6.594286930571849), new LatLon(49.3771951, 6.59429815)));
    map.put(
        1681314204934095999L,
        Pair.of(
            new LatLon(49.377059496429496, 6.593847691630668),
            new LatLon(49.37761245, 6.593885200000001)));
    map.put(
        4034718267022262322L,
        Pair.of(
            new LatLon(49.3772448933843, 6.5932681403562166),
            new LatLon(49.377441250000004, 6.593470850000001)));
    map.put(
        5101479749284940692L,
        Pair.of(
            new LatLon(49.37727165203832, 6.595459609088583), new LatLon(49.3775658, 6.59542185)));
    map.put(
        8774326937300323333L,
        Pair.of(
            new LatLon(49.37530962323352, 6.592067529093979),
            new LatLon(49.3753273, 6.592183100000001)));
    map.put(
        781463898184304594L,
        Pair.of(
            new LatLon(49.37797860971476, 6.5954415258222125),
            new LatLon(49.37775525000001, 6.59556475)));
    map.put(
        8160042435997286765L,
        Pair.of(
            new LatLon(49.377637182494816, 6.597301127197998),
            new LatLon(49.378142350000005, 6.5977435)));
    map.put(
        7918101558928689313L,
        Pair.of(
            new LatLon(49.377043278083356, 6.594086784733468), new LatLon(49.3772838, 6.5941031)));
    map.put(
        6940060437586857053L,
        Pair.of(
            new LatLon(49.37722616845234, 6.595078422586128), new LatLon(49.377308, 6.5950682)));
    map.put(
        3217569014930162316L,
        Pair.of(
            new LatLon(49.37661351433364, 6.592767371239515),
            new LatLon(49.3766562, 6.592599000000001)));
    map.put(
        2908947785021080300L,
        Pair.of(
            new LatLon(49.375117161473945, 6.5937212457270356),
            new LatLon(49.37524225, 6.59371385)));
    map.put(
        1813458275980231229L,
        Pair.of(
            new LatLon(49.37874016120153, 6.596899919584166),
            new LatLon(49.3786738, 6.5969330500000005)));
    map.put(
        5102064421030481154L,
        Pair.of(
            new LatLon(49.3785989750021, 6.596617119456351),
            new LatLon(49.37868895, 6.596572200000001)));
    map.put(
        692094074657131876L,
        Pair.of(
            new LatLon(49.37630449757834, 6.595495931083592),
            new LatLon(49.3764127, 6.595581750000001)));
    map.put(
        592589964765841419L,
        Pair.of(
            new LatLon(49.37518277652699, 6.594133377038112),
            new LatLon(49.37533245, 6.594092250000001)));
    map.put(
        4481932239342878960L,
        Pair.of(
            new LatLon(49.37804956577073, 6.59557014282503),
            new LatLon(49.377922850000004, 6.59564005)));
    map.put(
        3680931594325609358L,
        Pair.of(
            new LatLon(49.375120792650165, 6.59172434631748),
            new LatLon(49.375009250000005, 6.5917491)));
    map.put(
        9184206835593266573L,
        Pair.of(
            new LatLon(49.37508168593943, 6.5915136412236635),
            new LatLon(49.375229850000004, 6.59149525)));
    map.put(
        289383352590159594L,
        Pair.of(
            new LatLon(49.37563033508089, 6.592533912273709), new LatLon(49.37546535, 6.5925548)));
    map.put(
        1849854587008208836L,
        Pair.of(
            new LatLon(49.37725249624785, 6.5924567439637825),
            new LatLon(49.37711835, 6.592267550000001)));
    map.put(
        1769261792814844344L,
        Pair.of(
            new LatLon(49.37529252813771, 6.594525616898269), new LatLon(49.3754294, 6.59448695)));
    map.put(
        2987410959058357654L,
        Pair.of(
            new LatLon(49.375230327783285, 6.594305442052935),
            new LatLon(49.37540835, 6.59425515)));
    map.put(
        3599243498251701243L,
        Pair.of(
            new LatLon(49.37637340334166, 6.5927733874112935), new LatLon(49.3763616, 6.59247725)));
    map.put(
        8455030416760193143L,
        Pair.of(
            new LatLon(49.37515703646459, 6.592383992055025), new LatLon(49.37500785, 6.59235485)));
    map.put(
        7911710475915904882L,
        Pair.of(new LatLon(49.37763518102399, 6.59275738704041), new LatLon(49.3775144, 6.592729)));
    map.put(
        7019380523646144150L,
        Pair.of(
            new LatLon(49.37767087374303, 6.592594532660894),
            new LatLon(49.37749875, 6.592572550000001)));
    map.put(
        1702680790988770733L,
        Pair.of(
            new LatLon(49.37558665776273, 6.592321277911891),
            new LatLon(49.37544630000001, 6.59240505)));
    map.put(
        4813015879782451382L,
        Pair.of(
            new LatLon(49.375655261144296, 6.592730794812765),
            new LatLon(49.375508700000005, 6.59274935)));
    map.put(
        1761295086579109610L,
        Pair.of(
            new LatLon(49.375139172614574, 6.593926794390814),
            new LatLon(49.3752774, 6.5939079000000005)));
    map.put(
        8873183049756329843L,
        Pair.of(
            new LatLon(49.37504268438759, 6.591199435470103), new LatLon(49.3751332, 6.5911882)));
    map.put(
        7716977113203558005L,
        Pair.of(
            new LatLon(49.37719050992106, 6.5925006948012745), new LatLon(49.37700985, 6.5922459)));
    map.put(
        513018656666305011L,
        Pair.of(
            new LatLon(49.375098732757635, 6.593409549398932),
            new LatLon(49.3749465, 6.593418550000001)));
    map.put(
        7266103699742083835L,
        Pair.of(
            new LatLon(49.375527488428006, 6.592222141366167),
            new LatLon(49.3754024, 6.592296800000001)));
    map.put(
        106453647462911557L,
        Pair.of(
            new LatLon(49.3753939902611, 6.593027149155457),
            new LatLon(49.375337200000004, 6.592878450000001)));
    map.put(
        8071507677105441422L,
        Pair.of(
            new LatLon(49.3750892752224, 6.592783873410566),
            new LatLon(49.374933350000006, 6.59277945)));
    map.put(
        8219860850566842170L,
        Pair.of(
            new LatLon(49.377404800592686, 6.592379425249914),
            new LatLon(49.37745185, 6.592491750000001)));
    map.put(
        14157153893958044L,
        Pair.of(
            new LatLon(49.37593095615879, 6.592720185675736),
            new LatLon(49.37601445, 6.592502700000001)));
    map.put(
        3823022170995769949L,
        Pair.of(
            new LatLon(49.377510293015334, 6.593011058490814),
            new LatLon(49.37734755, 6.592843050000001)));
    map.put(
        475636704219251768L,
        Pair.of(
            new LatLon(49.37510865667336, 6.593577398750997),
            new LatLon(49.37495895000001, 6.59358625)));
    map.put(
        7689595906653638151L,
        Pair.of(
            new LatLon(49.37557845261594, 6.5923075304206655),
            new LatLon(49.37573005, 6.59221705)));
    map.put(
        6319451363763241931L,
        Pair.of(
            new LatLon(49.37726715974472, 6.595424613566844),
            new LatLon(49.377113800000004, 6.5954443000000005)));
    map.put(
        7761354599345768117L,
        Pair.of(
            new LatLon(49.37512334006961, 6.593810966825018),
            new LatLon(49.374983, 6.5938301500000005)));
    map.put(
        3020848512162746267L,
        Pair.of(
            new LatLon(49.37742196310485, 6.593096620094912),
            new LatLon(49.377270300000006, 6.59294005)));
    map.put(
        5426936285372042017L,
        Pair.of(
            new LatLon(49.37550670265439, 6.595324591510471),
            new LatLon(49.3752453, 6.595331150000001)));
    map.put(
        4165793331186913278L,
        Pair.of(
            new LatLon(49.376960397548245, 6.592716295853091),
            new LatLon(49.37681425, 6.592588450000001)));
    map.put(
        3691478899120686758L,
        Pair.of(
            new LatLon(49.37515837003022, 6.591893673873268),
            new LatLon(49.3747874, 6.591976000000001)));
    map.put(
        1564112217868488100L,
        Pair.of(
            new LatLon(49.3761554325856, 6.592775567306167),
            new LatLon(49.3761553, 6.592450600000001)));
    map.put(
        2528465424325296814L,
        Pair.of(
            new LatLon(49.37546710335904, 6.59512194623701), new LatLon(49.3755351, 6.5951038)));

    return map;
  }

  @Deprecated
  public static OsmGridNode createOsmogridNode(
      long id,
      Tags tags,
      LatLon latLon,
      Quantity<Power> load,
      LatLon houseConnectionPoint,
      boolean subStation,
      int cluster) {
    OsmGridNode osmGridNode = new OsmGridNode(id, tags, latLon);
    osmGridNode.setLoad(load);
    osmGridNode.setHouseConnectionPoint(houseConnectionPoint);
    osmGridNode.setSubStation(subStation);
    osmGridNode.setCluster(cluster);

    return osmGridNode;
  }
}
