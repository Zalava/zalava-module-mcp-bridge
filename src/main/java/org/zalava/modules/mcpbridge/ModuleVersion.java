package org.zalava.modules.mcpbridge;

import java.io.IOException;
import java.util.Properties;

final class ModuleVersion {
  private ModuleVersion() {}

  static String value() {
    Properties properties = new Properties();
    try (var stream =
        ModuleVersion.class.getClassLoader().getResourceAsStream("module.properties")) {
      if (stream == null) throw new IllegalStateException("module.properties is missing");
      properties.load(stream);
      return properties.getProperty("version");
    } catch (IOException exception) {
      throw new IllegalStateException("Could not read module.properties", exception);
    }
  }
}
