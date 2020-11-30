package io.seata.config.zk;

import io.seata.config.Configuration;
import io.seata.config.ConfigurationChangeEvent;
import io.seata.config.ConfigurationChangeListener;
import io.seata.config.ConfigurationFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ZookeeperConfigurationTest {
    @Test
    public void testCustomConfigLoad() {
        Configuration configuration = ConfigurationFactory.getInstance();
        Assertions.assertNotNull(configuration);
        configuration.putConfig("test", "content");
        String content = configuration.getLatestConfig("test", "defaultContent", 1000);
        Assertions.assertEquals(content, "content");
        String contentCache = configuration.getConfig("test");
        Assertions.assertEquals(contentCache, "content");
        configuration.putConfig("test", "content2");
        String contentCache2 = configuration.getConfig("test");
        Assertions.assertEquals(contentCache2, "content");

















        configuration.addConfigListener("", new ConfigurationChangeListener() {
            @Override
            public void onProcessEvent(ConfigurationChangeEvent event) {

            }

            @Override
            public void onShutDown() {

            }

            @Override
            public void onChangeEvent(ConfigurationChangeEvent event) {

            }
        });
    }
}
