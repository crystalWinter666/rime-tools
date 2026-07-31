package org.rimecraft.rimetools.module;

import org.slf4j.Logger;

import java.nio.file.Path;

public record RimeModuleContext(Path configDirectory, Logger logger, ModuleRegistry registry) {
    public Path moduleDirectory(String moduleId) {
        return configDirectory.resolve(moduleId);
    }

    /**
     * 返回模块的统一配置文件路径 {@code config/rime-tools/<moduleId>.yml}。
     * 旧版配置文件（<moduleId>/config.yml、<moduleId>.properties）的迁移由各模块
     * 在加载时自行处理（teleport 为同格式复制，title 需要 properties → YAML 转换）。
     */
    public Path configFile(String moduleId) {
        return configDirectory.resolve(moduleId + ".yml");
    }
}
