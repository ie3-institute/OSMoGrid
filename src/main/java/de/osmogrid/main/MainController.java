/*
 * © 2021. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package de.osmogrid.main;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import de.osmogrid.config.OsmogridConfig;
import de.osmogrid.controller.SessionController;
import de.osmogrid.interfaces.OsmogridMainControl;
import java.io.File;

/**
 * Controls the main functionality operations.
 *
 * @author Mahr
 * @since 17.12.2018
 */
public class MainController implements OsmogridMainControl {

  private OsmogridConfig osmogridConfig;

  @Override
  public void setup(File configFile) {
    // Build the config class
    logger.info("Building the config class ...");
    Config tsConfig = ConfigFactory.parseFile(configFile);
    osmogridConfig = new OsmogridConfig(tsConfig);
  }

  @Override
  public void start() {
    // start new session
    logger.info("Start new session \"{}\"", osmogridConfig.runtime.name);
    SessionController sessionController = new SessionController();
    sessionController.initialize(osmogridConfig);
  }

  @Override
  public void shutdown() {
    // TODO: implement
  }

  @Override
  public void getSessionStatus() {
    // TODO: implement
  }

  /** Prints an opening message. */
  private static void printOpener() {
    System.out.print("                       .-:+s::.`                                 \n");
    System.out.print("                    ./+/-./s`.:++-                               \n");
    System.out.print("                   /o.    :s    `:o.                             \n");
    System.out.print("                  ++      :s      .s.                            \n");
    System.out.print("                 -s///////+s///////o+                            \n");
    System.out.print("                `:o````````````````:s``                          \n");
    System.out.print("               ./+s////+s////+o////os//                          \n");
    System.out.print("                 :o    `++--:o-    :o`                           \n");
    System.out.print("               .ooo      `--.`     -s+/                          \n");
    System.out.print("               :o/o   +o:    `oo-  -s:s                          \n");
    System.out.print("                :+o   :/.     :/`  -s/.                          \n");
    System.out.print("                 .s`               /+                            \n");
    System.out.print("                  ++`             -o.                            \n");
    System.out.print("                  `/+-          `/o.                             \n");
    System.out.print("                  `/o+o/-.....:+o+o-                  .-:`       \n");
    System.out.print("                `:o/``:oo::::/s+. .o+-               :sss/       \n");
    System.out.print("              `:+s/    `/+-`/+-:   .so+.            .+++/`       \n");
    System.out.print("            `:oo.s.      .+o- .s.:  +/.+/.        .+/.           \n");
    System.out.print("          `:o//s`s`       :s  .o+s  +/  -+/.  ``./+.             \n");
    System.out.print("        `:o+//:.`s`       :s    .o  +/    -o++/+o.               \n");
    System.out.print("       -+:-+/. `:s-....-+/+o///..-..+o.  .+/-+/+/                \n");
    System.out.print("       s-   -+++/s/:::+:s:--`+o:so::o++++/.   o/                 \n");
    System.out.print("       /+.``:+:``s:---/:s::-`+o:oo:-o/ .+/.``:o.                 \n");
    System.out.print("        .:::-`  `s-....-+/+o/+/.....+/   .:::-`                  \n");
    System.out.print("                `s:-------/s--------+/                           \n");
    System.out.print("                 ..:s-..../s.....++..`                           \n");
    System.out.print("                   .s`    :s     //                              \n");
    System.out.print("                   .s`    :s     //                              \n");
    System.out.print("                   .s`    :s     //                              \n");
    System.out.print("                   .s`    :s     //                              \n");
    System.out.print("                   .s`...`:s .-.`//                              \n");
    System.out.print("                   .so/--/sso:-:+s/                              \n");
    System.out.print("                   .s+////os+////s/                              \n");
  }
}
