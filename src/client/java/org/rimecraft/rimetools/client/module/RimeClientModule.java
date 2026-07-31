package org.rimecraft.rimetools.client.module;

public interface RimeClientModule {
    String id();

    void initializeClient(ClientModuleContext context);
}
