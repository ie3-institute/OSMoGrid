/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package de.osmogrid.config;

public class OsmogridConfig {
  public final OsmogridConfig.Grid grid;
  public final OsmogridConfig.Io io;
  public final OsmogridConfig.Runtime runtime;

  public static class Grid {
    public final double averagePowerDensity;
    public final boolean considerHouseConnectionPoints;
    public final double ignoreClustersSmallerThan;
    public final java.lang.String lineType;
    public final double loadSubstation;
    public final double nominalPower;
    public final double ratedVoltage;
    public final double ratedVoltageHigherNet;
    public final boolean separateClustersByLandUses;
    public final java.lang.String voltageLevel;

    public Grid(
        com.typesafe.config.Config c,
        java.lang.String parentPath,
        $TsCfgValidator $tsCfgValidator) {
      this.averagePowerDensity = $_reqDbl(parentPath, c, "averagePowerDensity", $tsCfgValidator);
      this.considerHouseConnectionPoints =
          $_reqBln(parentPath, c, "considerHouseConnectionPoints", $tsCfgValidator);
      this.ignoreClustersSmallerThan =
          $_reqDbl(parentPath, c, "ignoreClustersSmallerThan", $tsCfgValidator);
      this.lineType = $_reqStr(parentPath, c, "lineType", $tsCfgValidator);
      this.loadSubstation = $_reqDbl(parentPath, c, "loadSubstation", $tsCfgValidator);
      this.nominalPower = $_reqDbl(parentPath, c, "nominalPower", $tsCfgValidator);
      this.ratedVoltage = $_reqDbl(parentPath, c, "ratedVoltage", $tsCfgValidator);
      this.ratedVoltageHigherNet =
          $_reqDbl(parentPath, c, "ratedVoltageHigherNet", $tsCfgValidator);
      this.separateClustersByLandUses =
          $_reqBln(parentPath, c, "separateClustersByLandUses", $tsCfgValidator);
      this.voltageLevel = $_reqStr(parentPath, c, "voltageLevel", $tsCfgValidator);
    }

    private static boolean $_reqBln(
        java.lang.String parentPath,
        com.typesafe.config.Config c,
        java.lang.String path,
        $TsCfgValidator $tsCfgValidator) {
      if (c == null) return false;
      try {
        return c.getBoolean(path);
      } catch (com.typesafe.config.ConfigException e) {
        $tsCfgValidator.addBadPath(parentPath + path, e);
        return false;
      }
    }

    private static double $_reqDbl(
        java.lang.String parentPath,
        com.typesafe.config.Config c,
        java.lang.String path,
        $TsCfgValidator $tsCfgValidator) {
      if (c == null) return 0;
      try {
        return c.getDouble(path);
      } catch (com.typesafe.config.ConfigException e) {
        $tsCfgValidator.addBadPath(parentPath + path, e);
        return 0;
      }
    }

    private static java.lang.String $_reqStr(
        java.lang.String parentPath,
        com.typesafe.config.Config c,
        java.lang.String path,
        $TsCfgValidator $tsCfgValidator) {
      if (c == null) return null;
      try {
        return c.getString(path);
      } catch (com.typesafe.config.ConfigException e) {
        $tsCfgValidator.addBadPath(parentPath + path, e);
        return null;
      }
    }
  }

  public static class Io {
    public final java.lang.String pbfFilePath;
    public final boolean readTypes;
    public final java.lang.String targetFormat;
    public final java.lang.String typeSourceFormat;
    public final boolean write;

    public Io(
        com.typesafe.config.Config c,
        java.lang.String parentPath,
        $TsCfgValidator $tsCfgValidator) {
      this.pbfFilePath = $_reqStr(parentPath, c, "pbfFilePath", $tsCfgValidator);
      this.readTypes = $_reqBln(parentPath, c, "readTypes", $tsCfgValidator);
      this.targetFormat = $_reqStr(parentPath, c, "targetFormat", $tsCfgValidator);
      this.typeSourceFormat = $_reqStr(parentPath, c, "typeSourceFormat", $tsCfgValidator);
      this.write = $_reqBln(parentPath, c, "write", $tsCfgValidator);
    }

    private static boolean $_reqBln(
        java.lang.String parentPath,
        com.typesafe.config.Config c,
        java.lang.String path,
        $TsCfgValidator $tsCfgValidator) {
      if (c == null) return false;
      try {
        return c.getBoolean(path);
      } catch (com.typesafe.config.ConfigException e) {
        $tsCfgValidator.addBadPath(parentPath + path, e);
        return false;
      }
    }

    private static java.lang.String $_reqStr(
        java.lang.String parentPath,
        com.typesafe.config.Config c,
        java.lang.String path,
        $TsCfgValidator $tsCfgValidator) {
      if (c == null) return null;
      try {
        return c.getString(path);
      } catch (com.typesafe.config.ConfigException e) {
        $tsCfgValidator.addBadPath(parentPath + path, e);
        return null;
      }
    }
  }

  public static class Runtime {
    public final boolean cutArea;
    public final boolean gui;
    public final java.lang.String name;
    public final boolean plot;

    public Runtime(
        com.typesafe.config.Config c,
        java.lang.String parentPath,
        $TsCfgValidator $tsCfgValidator) {
      this.cutArea = $_reqBln(parentPath, c, "cutArea", $tsCfgValidator);
      this.gui = $_reqBln(parentPath, c, "gui", $tsCfgValidator);
      this.name = $_reqStr(parentPath, c, "name", $tsCfgValidator);
      this.plot = $_reqBln(parentPath, c, "plot", $tsCfgValidator);
    }

    private static boolean $_reqBln(
        java.lang.String parentPath,
        com.typesafe.config.Config c,
        java.lang.String path,
        $TsCfgValidator $tsCfgValidator) {
      if (c == null) return false;
      try {
        return c.getBoolean(path);
      } catch (com.typesafe.config.ConfigException e) {
        $tsCfgValidator.addBadPath(parentPath + path, e);
        return false;
      }
    }

    private static java.lang.String $_reqStr(
        java.lang.String parentPath,
        com.typesafe.config.Config c,
        java.lang.String path,
        $TsCfgValidator $tsCfgValidator) {
      if (c == null) return null;
      try {
        return c.getString(path);
      } catch (com.typesafe.config.ConfigException e) {
        $tsCfgValidator.addBadPath(parentPath + path, e);
        return null;
      }
    }
  }

  public OsmogridConfig(com.typesafe.config.Config c) {
    final $TsCfgValidator $tsCfgValidator = new $TsCfgValidator();
    final java.lang.String parentPath = "";
    this.grid =
        c.hasPathOrNull("grid")
            ? new OsmogridConfig.Grid(c.getConfig("grid"), parentPath + "grid.", $tsCfgValidator)
            : new OsmogridConfig.Grid(
                com.typesafe.config.ConfigFactory.parseString("grid{}"),
                parentPath + "grid.",
                $tsCfgValidator);
    this.io =
        c.hasPathOrNull("io")
            ? new OsmogridConfig.Io(c.getConfig("io"), parentPath + "io.", $tsCfgValidator)
            : new OsmogridConfig.Io(
                com.typesafe.config.ConfigFactory.parseString("io{}"),
                parentPath + "io.",
                $tsCfgValidator);
    this.runtime =
        c.hasPathOrNull("runtime")
            ? new OsmogridConfig.Runtime(
                c.getConfig("runtime"), parentPath + "runtime.", $tsCfgValidator)
            : new OsmogridConfig.Runtime(
                com.typesafe.config.ConfigFactory.parseString("runtime{}"),
                parentPath + "runtime.",
                $tsCfgValidator);
    $tsCfgValidator.validate();
  }

  private static final class $TsCfgValidator {
    private final java.util.List<java.lang.String> badPaths = new java.util.ArrayList<>();

    void addBadPath(java.lang.String path, com.typesafe.config.ConfigException e) {
      badPaths.add("'" + path + "': " + e.getClass().getName() + "(" + e.getMessage() + ")");
    }

    void validate() {
      if (!badPaths.isEmpty()) {
        java.lang.StringBuilder sb = new java.lang.StringBuilder("Invalid configuration:");
        for (java.lang.String path : badPaths) {
          sb.append("\n    ").append(path);
        }
        throw new com.typesafe.config.ConfigException(sb.toString()) {};
      }
    }
  }
}
