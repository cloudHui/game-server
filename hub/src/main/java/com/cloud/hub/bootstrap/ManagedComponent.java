package com.cloud.hub.bootstrap;

public interface ManagedComponent {
    HubComponent component();
    void start() throws Exception;
    void stop() throws Exception;
}
