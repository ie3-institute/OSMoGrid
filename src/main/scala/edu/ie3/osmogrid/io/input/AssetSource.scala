/*
 * © 2022. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.osmogrid.io.input

import edu.ie3.datamodel.io.naming.{
  DefaultDirectoryHierarchy,
  EntityPersistenceNamingStrategy,
  FileNamingStrategy
}
import edu.ie3.datamodel.io.source.csv.CsvTypeSource
import edu.ie3.osmogrid.cfg.OsmoGridConfig.Input.Asset
import edu.ie3.osmogrid.cfg.OsmoGridConfig.Input.Asset.File
import edu.ie3.osmogrid.exception.{IllegalConfigException, InputDataException}
import edu.ie3.osmogrid.io.input.InputDataProvider.AssetInformation

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.jdk.CollectionConverters.IterableHasAsScala

trait AssetSource {

  def read(): Future[AssetInformation]
}

object AssetSource {

  def apply(ec: ExecutionContextExecutor, assetCfg: Asset): AssetSource = {
    assetCfg match {
      case Asset(Some(File(directory, hierarchic, separator))) =>
        val namingStrategy = if (hierarchic) {
          new FileNamingStrategy(
            new EntityPersistenceNamingStrategy(),
            new DefaultDirectoryHierarchy(directory, "osm")
          )
        } else
          new FileNamingStrategy()
        AssetFileSource(ec, separator, directory, namingStrategy)
      case Asset(None) =>
        throw IllegalConfigException(
          "You have to provide at least one input data type for asset type information!"
        )
    }
  }

  final case class AssetFileSource(
      executionContextExecutor: ExecutionContextExecutor,
      csvSep: String,
      directoryPath: String,
      namingStrategy: FileNamingStrategy
  ) extends AssetSource {

    override def read(): Future[AssetInformation] = {
      implicit val implicitEc: ExecutionContextExecutor =
        executionContextExecutor
      Future {
        val typeSource: CsvTypeSource =
          new CsvTypeSource(csvSep, directoryPath, namingStrategy)
        val transformerTypes = typeSource.getTransformer2WTypes.asScala.toSeq
        val lineTypes = typeSource.getLineTypes.asScala.toSeq
        (transformerTypes, lineTypes) match {
          case (Nil, _) =>
            throw InputDataException(
              s"There are no or corrupt transformer types at: $directoryPath"
            )
          case (_, Nil) =>
            throw InputDataException(
              s"There are no or corrupt line types at: $directoryPath"
            )
          case (_, _) =>
            AssetInformation(lineTypes, transformerTypes)
        }
      }
    }
  }
}
