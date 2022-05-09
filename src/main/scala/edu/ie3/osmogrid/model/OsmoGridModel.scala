/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.model

import edu.ie3.osmogrid.model.OsmoGridModel.EnhancedOsmEntity
import edu.ie3.osmogrid.model.SourceFilter.{Filter, LvFilter}
import edu.ie3.util.osm.model.OsmEntity.{Node, Relation, Way}
import edu.ie3.util.osm.model.{OsmContainer, OsmEntity}
import edu.ie3.util.osm.model.OsmContainer.ParOsmContainer
import edu.ie3.util.osm.model.OsmEntity.Relation.RelationMemberType

import scala.collection.parallel.ParSeq
import scala.collection.parallel.immutable.ParVector

sealed trait OsmoGridModel {
  protected val filter: SourceFilter

  def +(additional: OsmoGridModel): Option[OsmoGridModel]

//  def filterForWays(entities: Seq[EnhancedOsmEntity]) = {
//    entities.foldLeft(Seq.empty[Way], Map.empty[Long, OsmEntity]) { case ((ways, subentities), curEntity: EnhancedOsmEntity) =>
//      curEntity.entity match
//        case way: Way => (ways ++ , curEntity.subentities)
//  }
}

object OsmoGridModel {

  final case class LvOsmoGridModel(
      buildings: ParSeq[EnhancedOsmEntity],
      highways: ParSeq[EnhancedOsmEntity],
      landuses: ParSeq[EnhancedOsmEntity],
      boundaries: ParSeq[EnhancedOsmEntity],
      existingSubstations: ParSeq[EnhancedOsmEntity],
      filter: LvFilter
  ) extends OsmoGridModel {

    /** Merges two lv grids, if their filter match. To recombine two lv grids
      * with different filters, they need to be recreated to ensure filter
      * consistency
      *
      * @param additional
      *   second OsmoGridModel
      * @return
      *   combined model
      */
    override def +(additional: OsmoGridModel): Option[OsmoGridModel] = {
      additional match {
        case LvOsmoGridModel(
              buildings,
              highways,
              landuses,
              boundaries,
              existingSubstations,
              filter
            ) if this.filter.equals(additional.filter) =>
          Some(
            LvOsmoGridModel(
              this.buildings ++ buildings,
              this.highways ++ highways,
              this.landuses ++ landuses,
              this.boundaries ++ boundaries,
              this.existingSubstations ++ existingSubstations,
              this.filter // filter are the same for both models
            )
          )
        case _ =>
          None
      }
    }

  }

  object LvOsmoGridModel {

    def apply(
        osmContainer: ParOsmContainer,
        lvFilter: LvFilter,
        filterNodes: Boolean = true
    ): LvOsmoGridModel =
      val buildings = filter(osmContainer, lvFilter.buildingFilter)
      val highways = filter(osmContainer, lvFilter.highwayFilter)
      val landuses = filter(osmContainer, lvFilter.landuseFilter)
      val boundaries = filter(osmContainer, lvFilter.boundaryFilter)
      val substations =
        filterOr(osmContainer, lvFilter.existingSubstationFilter)

      new LvOsmoGridModel(
        buildings,
        highways,
        landuses,
        boundaries,
        substations,
        lvFilter
      )

    def mergeAll(
        models: ParSeq[LvOsmoGridModel],
        filterNodes: Boolean = true
    ): Option[OsmoGridModel] = {
      models.headOption.flatMap { case lvHeadModel: LvOsmoGridModel =>
        if (models.forall(_.filter == lvHeadModel.filter)) {
          Some(
            LvOsmoGridModel(
              models.flatMap(_.buildings),
              models.flatMap(_.highways),
              models.flatMap(_.landuses),
              models.flatMap(_.boundaries),
              models.flatMap(_.existingSubstations),
              lvHeadModel.filter
            )
          )
        } else {
          None
        }
      }

    }

  }

  case class EnhancedOsmEntity(
      entity: OsmEntity,
      subEntities: Map[Long, OsmEntity]
  ) {
    def allSubEntities: Iterable[OsmEntity] = subEntities.values

    def node(id: Long): Option[Node] =
      subEntities.get(id) match {
        case Some(n: Node) => Some(n)
        case _             => None
      }

    def nodes(ids: Seq[Long]): Seq[Option[Node]] =
      ids.map(subEntities.get).map {
        case Some(n: Node) => Some(n)
        case _             => None
      }

    def way(id: Long): Option[Way] =
      subEntities.get(id) match {
        case Some(w: Way) => Some(w)
        case _            => None
      }

    def relation(id: Long): Option[Relation] =
      subEntities.get(id) match {
        case Some(r: Relation) => Some(r)
        case _                 => None
      }
  }

  object EnhancedOsmEntity {
    def apply(
        entity: OsmEntity,
        subEntities: Iterable[OsmEntity]
    ): EnhancedOsmEntity =
      EnhancedOsmEntity(
        entity,
        subEntities.map(ent => ent.id -> ent).toMap
      )
  }

  def filter(
      osmContainer: ParOsmContainer,
      filter: Filter
  ): ParSeq[EnhancedOsmEntity] =
    filterOr(osmContainer, Set(filter))

  def filterOr(
      osmContainer: ParOsmContainer,
      filters: Set[Filter]
  ): ParSeq[EnhancedOsmEntity] = {
    val mappedFilter =
      filters.map(filter => (filter.key, filter.tagValues)).toMap
    (osmContainer.nodes.values ++ osmContainer.ways.values ++ osmContainer.relations.values)
      .foldLeft(ParSeq.empty) {
        case (resEntities, curEntity)
            if curEntity.hasKeysValuesPairOr(mappedFilter) =>
          val subEntities = curEntity match {
            case node: Node =>
              Iterable.empty
            case way: Way =>
              getSubEntities(osmContainer, way)
            case relation: Relation =>
              getSubEntities(osmContainer, relation)
          }
          resEntities :+ EnhancedOsmEntity(curEntity, subEntities)
        case (resEntities, _) =>
          resEntities
      }
  }

  private def getSubEntities(
      osmContainer: OsmContainer,
      way: Way
  ): Iterable[OsmEntity] = {
    osmContainer.nodes(way.nodes).flatten
  }

  private def getSubEntities(
      osmContainer: OsmContainer,
      relation: Relation
  ): Iterable[OsmEntity] = {
    relation.members.flatMap { member =>
      member.relationType match {
        case RelationMemberType.Node =>
          osmContainer
            .node(member.id)
            .map(node => Iterable.single(node))
            .getOrElse(Iterable.empty)
        case RelationMemberType.Way =>
          osmContainer
            .way(member.id)
            .map { way =>
              Iterable.single(way) ++ getSubEntities(osmContainer, way)
            }
            .getOrElse(Iterable.empty)
        case RelationMemberType.Relation =>
          osmContainer
            .relation(member.id)
            .map { relation =>
              Iterable
                .single(relation) ++ getSubEntities(osmContainer, relation)
            }
            .getOrElse(Iterable.empty)
        case RelationMemberType.Unrecognized =>
          Iterable.empty
      }
    }
  }

}
