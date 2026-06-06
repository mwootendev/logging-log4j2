/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.logging.log4j.layout.template.yaml;

import static org.apache.logging.log4j.layout.template.yaml.TestHelpers.serializeUsingLayout;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.DefaultConfiguration;
import org.apache.logging.log4j.core.layout.YamlLayout;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class YamlLayoutTest {

    private static final Configuration CONFIGURATION = new DefaultConfiguration();

    private static final YamlTemplateLayout JSON_TEMPLATE_LAYOUT = YamlTemplateLayout.newBuilder()
            .setConfiguration(CONFIGURATION)
            .setEventTemplateUri("classpath:YamlLayout.yaml")
            .build();

    private static final YamlLayout JSON_LAYOUT = YamlLayout.newBuilder()
            .setConfiguration(CONFIGURATION)
            .setProperties(true)
            .build();

    @Test
    void test_lite_log_events() {
        final List<LogEvent> logEvents = LogEventFixture.createLiteLogEvents(1_000);
        test(logEvents);
    }

    @Test
    void test_full_log_events() {
        final List<LogEvent> logEvents = LogEventFixture.createFullLogEvents(1_000);
        test(logEvents);
    }

    private static void test(final Collection<LogEvent> logEvents) {
        for (final LogEvent logEvent : logEvents) {
            test(logEvent);
        }
    }

    private static void test(final LogEvent logEvent) {
        final Map<String, Object> yamlTemplateLayoutMap = renderUsingYamlTemplateLayout(logEvent);
        final Map<String, Object> yamlLayoutMap = renderUsingYamlLayout(logEvent);
        // `YamlLayout` blindly serializes the `Throwable` as a POJO, this is, to say the least, quite wrong, and I
        // ain't going to try to emulate this behaviour in `YamlTemplateLayout`.
        // Hence, discarding the "thrown" field.
        yamlTemplateLayoutMap.remove("thrown");
        yamlLayoutMap.remove("thrown");
        // When the log event doesn't have any MDC, `YamlLayout` still emits an empty `contextMap` field, whereas
        // `YamlTemplateLayout` totally skips it.
        // Removing `contextMap` field to avoid discrepancies when there is no MDC to render.
        if (logEvent.getContextData().isEmpty()) {
            yamlLayoutMap.remove("contextMap");
        }
        Assertions.assertThat(yamlTemplateLayoutMap).isEqualTo(yamlLayoutMap);
    }

    private static Map<String, Object> renderUsingYamlTemplateLayout(final LogEvent logEvent) {
        return serializeUsingLayout(logEvent, JSON_TEMPLATE_LAYOUT);
    }

    private static Map<String, Object> renderUsingYamlLayout(final LogEvent logEvent) {
        return serializeUsingLayout(logEvent, JSON_LAYOUT);
    }
}
