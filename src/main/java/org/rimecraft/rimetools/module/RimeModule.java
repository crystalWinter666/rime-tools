package org.rimecraft.rimetools.module;

public interface RimeModule {
    String id();

    void initialize(RimeModuleContext context);
}
