/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.apm.plugin.spring.mvc.commons.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.skywalking.apm.agent.core.boot.AgentPackagePath;
import org.apache.skywalking.apm.agent.core.conf.ConfigNotFoundException;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.util.ConfigInitializer;
import org.apache.skywalking.apm.util.PropertyPlaceholderHelper;
import org.apache.skywalking.apm.util.StringUtil;

public class IgnoreExceptionConfig {

    private static final ILog LOGGER = LogManager.getLogger(IgnoreExceptionConfig.class);

    /**
     * config example:
     * trace.ignore_exception_names=com.xxx.XxxException,java.lang.IllegalArgumentException
     */
    private static String CONFIG_FILE_NAME = "/config/apm-trace-ignore-exception.config";

    private static final String EXCEPTION_SPLIT_SYMBOL = ",";

    private static final String ENV_KEY_PREFIX = "skywalking.";

    public static class Trace {

        public static String IGNORE_EXCEPTION_NAMES = "";

        public static Set IGNORE_EXCEPTION_NAMES_SET = new HashSet();
    }

    public static void initialize() {
        LOGGER.info("IgnoreExceptionConfig initialize");

        try {
            loadConfigFromSystemProp();
            if (!StringUtil.isEmpty(IgnoreExceptionConfig.Trace.IGNORE_EXCEPTION_NAMES)) {
                for (String exception : IgnoreExceptionConfig.Trace.IGNORE_EXCEPTION_NAMES
                    .split(EXCEPTION_SPLIT_SYMBOL)) {
                    Trace.IGNORE_EXCEPTION_NAMES_SET.add(exception);
                }
                return;
            }
        } catch (Exception e) {
            LOGGER.error(e, "Failed to load the system env.");
        }

        try {
            loadConfigFromAgentFolder();
            if (!StringUtil.isEmpty(IgnoreExceptionConfig.Trace.IGNORE_EXCEPTION_NAMES)) {
                for (String exception : IgnoreExceptionConfig.Trace.IGNORE_EXCEPTION_NAMES
                    .split(EXCEPTION_SPLIT_SYMBOL)) {
                    Trace.IGNORE_EXCEPTION_NAMES_SET.add(exception);
                }
            }
        } catch (Exception e) {
            LOGGER.warn(e,
                "Failed to load the " + CONFIG_FILE_NAME + " file, skywalking is going to run in default config.");
        }

    }

    /**
     * Load the config file
     *
     * @throws Exception when load file error
     */
    private static void loadConfigFromAgentFolder() throws Exception {
        File configFile = new File(AgentPackagePath.getPath(), CONFIG_FILE_NAME);
        if (configFile.exists() && configFile.isFile()) {
            try {
                LOGGER.warn("Ignore exception config file found in {}.", configFile);
                InputStream configFileStream = new FileInputStream(configFile);
                Properties properties = new Properties();
                properties.load(configFileStream);
                for (String key : properties.stringPropertyNames()) {
                    String value = (String) properties.get(key);
                    properties.put(key, PropertyPlaceholderHelper.INSTANCE.replacePlaceholders(value, properties));
                }
                ConfigInitializer.initialize(properties, IgnoreExceptionConfig.class);
            } catch (FileNotFoundException e) {
                throw new ConfigNotFoundException("Fail to load " + CONFIG_FILE_NAME, e);
            }
        } else {
            throw new ConfigNotFoundException("Fail to load ignore exception config file.");
        }
    }

    /**
     * load the system env
     * 
     * @throws IllegalAccessException when properties illegal
     */
    private static void loadConfigFromSystemProp() throws IllegalAccessException {
        Properties properties = new Properties();
        Properties systemProperties = System.getProperties();
        for (final Map.Entry<Object, Object> prop : systemProperties.entrySet()) {
            if (prop.getKey().toString().startsWith(ENV_KEY_PREFIX)) {
                String realKey = prop.getKey().toString().substring(ENV_KEY_PREFIX.length());
                properties.put(realKey, prop.getValue());
            }
        }
        if (!properties.isEmpty()) {
            ConfigInitializer.initialize(properties, IgnoreExceptionConfig.class);
        }
    }

}
