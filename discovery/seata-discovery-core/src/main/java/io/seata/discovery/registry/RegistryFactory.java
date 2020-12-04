/*
 *  Copyright 1999-2019 Seata.io Group.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package io.seata.discovery.registry;

import java.util.Objects;

import io.seata.common.exception.NotSupportYetException;
import io.seata.common.loader.EnhancedServiceLoader;
import io.seata.config.ConfigurationFactory;
import io.seata.config.ConfigurationKeys;

/**
 * The type Registry factory.
 *
 * @author slievrly
 */
public class RegistryFactory {

    private static volatile RegistryService instance = null;

    /**
     * Gets instance.
     *
     * @return the instance
     */
    public static RegistryService getInstance() {
        if (instance == null) {
            synchronized (RegistryFactory.class) {
                if (instance == null) {
                    instance = buildRegistryService();
                }
            }
        }
        return instance;
    }

    private static RegistryService buildRegistryService() {
        RegistryType registryType;
        // 从总配置源获取 registry.type 值
        String registryTypeName = ConfigurationFactory.CURRENT_FILE_INSTANCE.getConfig(
            ConfigurationKeys.FILE_ROOT_REGISTRY + ConfigurationKeys.FILE_CONFIG_SPLIT_CHAR
                + ConfigurationKeys.FILE_ROOT_TYPE);
        try {
            // 支持类型枚举 RegistryType
            registryType = RegistryType.getType(registryTypeName);
        } catch (Exception exx) {
            throw new NotSupportYetException("not support registry type: " + registryTypeName);
        }
        if (RegistryType.File == registryType) {
            return FileRegistryServiceImpl.getInstance();
        } else {
            // SPI 获取具体实现类
            return EnhancedServiceLoader.load(RegistryProvider.class, Objects.requireNonNull(registryType).name()).provide();
        }
    }
}
