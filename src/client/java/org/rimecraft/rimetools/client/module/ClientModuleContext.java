package org.rimecraft.rimetools.client.module;

import org.slf4j.Logger;

import java.nio.file.Path;

public record ClientModuleContext(Path configDirectory, Logger logger, ClientModuleRegistry registry) {
}
