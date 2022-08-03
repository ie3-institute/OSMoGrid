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
import scala.jdk.CollectionConverters.IterableHasAsScala

trait AssetSource {

  def read(): AssetInformation

}

object AssetSource {

  def apply(assetCfg: Asset): AssetSource = {
    assetCfg match {
      case Asset(Some(File(directory, hierarchic, separator))) =>
        val namingStrategy = if (hierarchic) {
          new FileNamingStrategy(
            new EntityPersistenceNamingStrategy(),
            new DefaultDirectoryHierarchy(directory, "osm")
          )
        } else
          new FileNamingStrategy()
        AssetFileSource(separator, directory, namingStrategy)
      case Asset(None) =>
        throw IllegalConfigException(
          "You have to provide at least one input data type for asset type information!"
        )
    }
  }

  final case class AssetFileSource(
      assetInformation: AssetInformation
  ) extends AssetSource {

    override def read(): AssetInformation = {
      assetInformation
    }
  }
  object AssetFileSource {

    def apply(
        csvSep: String,
        folderPath: String,
        namingStrategy: FileNamingStrategy
    ): AssetFileSource = {
      val typeSource: CsvTypeSource =
        new CsvTypeSource(csvSep, folderPath, namingStrategy)
      val transformerTypes = typeSource.getTransformer2WTypes.asScala.toSeq
      val lineTypes = typeSource.getLineTypes.asScala.toSeq
      (transformerTypes, lineTypes) match {
        case (transformerTypes, _) if transformerTypes.isEmpty =>
          throw InputDataException(
            s"There are no transformer types at: $folderPath"
          )
        case (_, lineTypes) if lineTypes.isEmpty =>
          throw InputDataException(s"There are no line types at: $folderPath")
        case (_, _) =>
          AssetFileSource(AssetInformation(lineTypes, transformerTypes))
      }
    }
  }
}
