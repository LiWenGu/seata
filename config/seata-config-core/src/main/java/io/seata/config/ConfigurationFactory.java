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
package io.seata.config;

import io.seata.common.exception.NotSupportYetException;
import io.seata.common.loader.EnhancedServiceLoader;
import io.seata.common.loader.EnhancedServiceNotFoundException;
import io.seata.common.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * The type Configuration factory.
 *
 * @author slievrly
 * @author Geng Zhang
 */
public final class ConfigurationFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigurationFactory.class);

    private static final String REGISTRY_CONF_DEFAULT = "registry";
    private static final String ENV_SYSTEM_KEY = "SEATA_ENV";
    public static final String ENV_PROPERTY_KEY = "seataEnv";

    private static final String SYSTEM_PROPERTY_SEATA_CONFIG_NAME = "seata.config.name";

    private static final String ENV_SEATA_CONFIG_NAME = "SEATA_CONFIG_NAME";

    public static Configuration CURRENT_FILE_INSTANCE;

    static {
        load();
    }

    private static void load() {
        // 优先虚拟机变量 seata.config.name 配置属性
        String seataConfigName = System.getProperty(SYSTEM_PROPERTY_SEATA_CONFIG_NAME);
        if (seataConfigName == null) {
            // 次优先系统变量 SEATA_CONFIG_NAME 配置属性
            seataConfigName = System.getenv(ENV_SEATA_CONFIG_NAME);
        }
        if (seataConfigName == null) {
            // 默认为 registry
            seataConfigName = REGISTRY_CONF_DEFAULT;
        }
        // 用于多环境切换配置，优先虚拟机变量 seataEnv
        String envValue = System.getProperty(ENV_PROPERTY_KEY);
        if (envValue == null) {
            // 次优先级系统变量 SEATA_ENV
            envValue = System.getenv(ENV_SYSTEM_KEY);
        }
        // 对多环境配置文件拼接，默认没有后缀
        Configuration configuration = (envValue == null) ? new FileConfiguration(seataConfigName,
                false) : new FileConfiguration(seataConfigName + "-" + envValue, false);
        Configuration extConfiguration = null;
        try {
            // 目前源码未使用的接口 ExtConfigurationProvider，但是使用方可以实现该接口，实现所有的配置都存储在第三方配置中心上
            // 个人理解类似 dubbo-admin 元数据的配置中心，适合重度依赖配置中心并方便统一管理和授权配置的客户端应用，一般本地文件配置就够了
            extConfiguration = EnhancedServiceLoader.load(ExtConfigurationProvider.class).provide(configuration);
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("load Configuration:{}", extConfiguration == null ? configuration.getClass().getSimpleName()
                        : extConfiguration.getClass().getSimpleName());
            }
        } catch (EnhancedServiceNotFoundException ignore) {

        } catch (Exception e) {
            LOGGER.error("failed to load extConfiguration:{}", e.getMessage(), e);
        }
        // 就这样，我们得到了总配置的源
        CURRENT_FILE_INSTANCE = extConfiguration == null ? configuration : extConfiguration;
    }

    private static final String NAME_KEY = "name";
    private static final String FILE_TYPE = "file";

    private static volatile Configuration instance = null;

    /**
     * Gets instance.
     *
     * @return the instance
     */
    public static Configuration getInstance() {
        if (instance == null) {
            synchronized (Configuration.class) {
                if (instance == null) {
                    instance = buildConfiguration();
                }
            }
        }
        return instance;
    }

    private static Configuration buildConfiguration() {
        ConfigType configType;
        String configTypeName;
        try {
            // 从总配置源中获取配置中心的类型，具体支持种类参见 ConfigType 枚举，key 为 config.type
            configTypeName = CURRENT_FILE_INSTANCE.getConfig(
                    ConfigurationKeys.FILE_ROOT_CONFIG + ConfigurationKeys.FILE_CONFIG_SPLIT_CHAR
                            + ConfigurationKeys.FILE_ROOT_TYPE);

            if (StringUtils.isBlank(configTypeName)) {
                throw new NotSupportYetException("config type can not be null");
            }
            configType = ConfigType.getType(configTypeName);
        } catch (Exception e) {
            throw e;
        }
        Configuration extConfiguration = null;
        Configuration configuration;
        // 默认为文件配置中心
        if (ConfigType.File == configType) {
            // 拿到文件配置中心的文件，配置 key 为：config.file.name
            String pathDataId = String.join(ConfigurationKeys.FILE_CONFIG_SPLIT_CHAR,
                    ConfigurationKeys.FILE_ROOT_CONFIG, FILE_TYPE, NAME_KEY);
            String name = CURRENT_FILE_INSTANCE.getConfig(pathDataId);
            configuration = new FileConfiguration(name);
            try {
                // 接下来又判断是否有 ExtConfigurationProvider，通过前面 spi 的分析，如果有多个实现，默认取最后一个实现（Order最大的）
                extConfiguration = EnhancedServiceLoader.load(ExtConfigurationProvider.class).provide(configuration);
                if (LOGGER.isInfoEnabled()) {
                    LOGGER.info("load Configuration:{}", extConfiguration == null
                            ? configuration.getClass().getSimpleName() : extConfiguration.getClass().getSimpleName());
                }
            } catch (EnhancedServiceNotFoundException ignore) {

            } catch (Exception e) {
                LOGGER.error("failed to load extConfiguration:{}", e.getMessage(), e);
            }
        } else {
            // 这里就是具体的 SPI 调用，根据 configType 调用不同的 provider
            configuration = EnhancedServiceLoader
                    .load(ConfigurationProvider.class, Objects.requireNonNull(configType).name()).provide();
        }
        try {
            Configuration configurationCache;
            // 对 extConfiguration 优先级最高
            // 同时对配置中心加入了代理，这个动态代理用于代理所有除 getLatestConfig 方法的所有get方法，做缓存处理
            // 所以当系统启动那一瞬间，第n+1次，就直接从缓存取配置值了
            if (null != extConfiguration) {
                configurationCache = ConfigurationCache.getInstance().proxy(extConfiguration);
            } else {
                configurationCache = ConfigurationCache.getInstance().proxy(configuration);
            }
            if (null != configurationCache) {
                extConfiguration = configurationCache;
            }
        } catch (EnhancedServiceNotFoundException ignore) {

        } catch (Exception e) {
            LOGGER.error("failed to load configurationCacheProvider:{}", e.getMessage(), e);
        }
        return null == extConfiguration ? configuration : extConfiguration;
    }

    protected static void reload() {
        ConfigurationCache.getInstance().clear();
        load();
        instance = null;
        getInstance();
    }
}
