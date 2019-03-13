/*
 * Copyright (c) 2008-2016 Haulmont.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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

package com.haulmont.cuba.core.sys;

import ch.qos.logback.classic.LoggerContext;
import com.google.common.base.Splitter;
import com.haulmont.cuba.core.global.Events;
import com.haulmont.cuba.core.sys.servlet.events.ServletContextDestroyedEvent;
import com.haulmont.cuba.core.sys.servlet.events.ServletContextInitializedEvent;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringSubstitutor;
import org.apache.commons.text.StringTokenizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.ResourceUtils;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Base class for {@link AppContext} loaders of web applications.
 */
public abstract class AbstractWebAppContextLoader extends AbstractAppContextLoader implements ServletContextListener {

    public static final Pattern SEPARATOR_PATTERN = Pattern.compile("\\s");

    public static final String APP_COMPONENTS_PARAM = "appComponents";

    public static final String APP_PROPS_CONFIG_PARAM = "appPropertiesConfig";

    public static final String APP_PROPS_PARAM = "appProperties";

    private final Logger log = LoggerFactory.getLogger(AbstractWebAppContextLoader.class);

    @Override
    public void contextInitialized(ServletContextEvent servletContextEvent) {
        try {
            ServletContext sc = servletContextEvent.getServletContext();
            ServletContextHolder.setServletContext(sc);

            initAppComponents(sc);
            initAppProperties(sc);
            afterInitAppProperties();

            beforeInitAppContext();
            initAppContext();
            afterInitAppContext();

            ApplicationContext applicationContext = AppContext.getApplicationContext();

            applicationContext.getBean(Events.class)
                    .publish(new ServletContextInitializedEvent(sc, applicationContext));

            AppContext.Internals.startContext();
            log.info("AppContext started");
        } catch (RuntimeException e) {
            log.error("Error initializing application", e);
            try {
                ApplicationContext springContext = AppContext.getApplicationContext();
                if (springContext != null) {
                    ((ConfigurableApplicationContext) springContext).close();
                }
            } catch (Exception e1) {
                log.debug("Error closing application context: {}", e1.toString());
            }
            throw e;
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent servletContextEvent) {
        ApplicationContext applicationContext = AppContext.getApplicationContext();

        applicationContext.getBean(Events.class)
                .publish(new ServletContextDestroyedEvent(
                                servletContextEvent.getServletContext(),
                                applicationContext
                        ));

        AppContext.Internals.stopContext();
        AppContext.Internals.setApplicationContext(null);

        if (LoggerFactory.getILoggerFactory() instanceof LoggerContext) {
            ((LoggerContext) LoggerFactory.getILoggerFactory()).stop();
        }
    }

    protected void initAppComponents(ServletContext sc) {
        String block = getBlock();

        String appComponentsParam = sc.getInitParameter(APP_COMPONENTS_PARAM);
        AppComponents appComponents;
        if (StringUtils.isEmpty(appComponentsParam)) {
            appComponents = new AppComponents(block);
        } else {
            List<String> compNames = Splitter.on(SEPARATOR_PATTERN).omitEmptyStrings().splitToList(appComponentsParam);
            appComponents = new AppComponents(compNames, block);
        }
        AppContext.Internals.setAppComponents(appComponents);
    }

    protected void initAppProperties(ServletContext sc) {
        // get properties from web.xml
        String appProperties = sc.getInitParameter(APP_PROPS_PARAM);
        if (appProperties != null) {
            StringTokenizer tokenizer = new StringTokenizer(appProperties);
            for (String str : tokenizer.getTokenArray()) {
                int i = str.indexOf("=");
                if (i < 0)
                    continue;
                String name = StringUtils.substring(str, 0, i);
                String value = StringUtils.substring(str, i+1);
                if (!StringUtils.isBlank(name)) {
                    AppContext.setProperty(name, value);
                }
            }
        }

        // get properties from a set of app.properties files defined in web.xml
        String propsConfigName = getAppPropertiesConfig(sc);
        if (propsConfigName == null)
            throw new IllegalStateException(APP_PROPS_CONFIG_PARAM + " servlet context parameter not defined");

        final Properties properties = new Properties();

        DefaultResourceLoader resourceLoader = new DefaultResourceLoader();
        StringTokenizer tokenizer = new StringTokenizer(propsConfigName);
        tokenizer.setQuoteChar('"');
        for (String str : tokenizer.getTokenArray()) {
            log.trace("Processing properties location: {}", str);
            str = StringSubstitutor.replaceSystemProperties(str);
            InputStream stream = null;
            try {
                if (ResourceUtils.isUrl(str) || str.startsWith(ResourceLoader.CLASSPATH_URL_PREFIX)) {
                    Resource resource = resourceLoader.getResource(str);
                    if (resource.exists())
                        stream = resource.getInputStream();
                } else {
                   stream = sc.getResourceAsStream(str);
                }

                if (stream != null) {
                    log.info("Loading app properties from {}", str);
                    BOMInputStream bomInputStream = new BOMInputStream(stream);
                    try (Reader reader = new InputStreamReader(bomInputStream, StandardCharsets.UTF_8)) {
                        properties.load(reader);
                    }
                } else {
                    log.trace("Resource {} not found, ignore it", str);
                }
            } catch (IOException e) {
                throw new RuntimeException("Unable to read properties from stream", e);
            } finally {
                IOUtils.closeQuietly(stream);
            }
        }

        for (Object key : properties.keySet()) {
            AppContext.setProperty((String) key, properties.getProperty((String) key).trim());
        }

        if (log.isTraceEnabled()) {
            String props = Arrays.asList(AppContext.getPropertyNames()).stream()
                    .map(key -> key + "=" + AppContext.getProperty(key))
                    .sorted()
                    .collect(Collectors.joining("\n"));
            log.trace("AppProperties of the '{}' block:\n{}", getBlock(), props);
        }
    }

    protected String getAppPropertiesConfig(ServletContext sc) {
        return sc.getInitParameter(APP_PROPS_CONFIG_PARAM);
    }

    @Override
    protected void afterInitAppProperties() {
        super.afterInitAppProperties();

        String property = AppContext.getProperty("cuba.confDir");
        if (property == null)
            throw new RuntimeException("App property cuba.confDir not defined");

        File file = new File(property);
        if (!file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.mkdirs();
        }

        property = AppContext.getProperty("cuba.tempDir");
        if (property == null)
            throw new RuntimeException("App property cuba.tempDir not defined");

        file = new File(property);
        if (!file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.mkdirs();
        }
    }

    public static class CubaServletConfig implements ServletConfig {
        protected String name;
        protected ServletContext servletContext;

        public CubaServletConfig(String name, ServletContext servletContext) {
            this.name = name;
            this.servletContext = servletContext;
        }

        @Override
        public String getServletName() {
            return name;
        }

        @Override
        public ServletContext getServletContext() {
            return servletContext;
        }

        @Override
        public String getInitParameter(String name) {
            return servletContext.getInitParameter(name);
        }

        @Override
        public Enumeration<String> getInitParameterNames() {
            return servletContext.getInitParameterNames();
        }
    }
}