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

import static org.apache.logging.log4j.util.Strings.LINE_SEPARATOR;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.TimeZone;
import org.apache.logging.log4j.core.config.plugins.convert.TypeConverter;
import org.apache.logging.log4j.core.config.plugins.convert.TypeConverterRegistry;
import org.apache.logging.log4j.layout.template.structured.util.RecyclerFactory;
import org.apache.logging.log4j.util.PropertiesUtil;

public final class YamlTemplateLayoutDefaults {

    private YamlTemplateLayoutDefaults() {}

    private static final PropertiesUtil PROPERTIES = PropertiesUtil.getProperties();

    public static Charset getCharset() {
        final String charsetName = PROPERTIES.getStringProperty("log4j.layout.template.charset");
        return charsetName != null ? Charset.forName(charsetName) : StandardCharsets.UTF_8;
    }

    public static boolean isLocationInfoEnabled() {
        return PROPERTIES.getBooleanProperty("log4j.layout.template.locationInfoEnabled", false);
    }

    public static boolean isStackTraceEnabled() {
        return PROPERTIES.getBooleanProperty("log4j.layout.template.stackTraceEnabled", true);
    }

    public static String getTimestampFormatPattern() {
        return PROPERTIES.getStringProperty(
                "log4j.layout.template.timestampFormatPattern", "yyyy-MM-dd'T'HH:mm:ss.SSSZZZ");
    }

    public static TimeZone getTimeZone() {
        final String timeZoneId = PROPERTIES.getStringProperty("log4j.layout.template.timeZone");
        return timeZoneId != null ? TimeZone.getTimeZone(timeZoneId) : TimeZone.getDefault();
    }

    public static Locale getLocale() {
        final String locale = PROPERTIES.getStringProperty("log4j.layout.template.locale");
        if (locale == null) {
            return Locale.getDefault();
        }
        final String[] localeFields = locale.split("_", 3);
        switch (localeFields.length) {
            case 1:
                return new Locale(localeFields[0]);
            case 2:
                return new Locale(localeFields[0], localeFields[1]);
            case 3:
                return new Locale(localeFields[0], localeFields[1], localeFields[2]);
            default:
                throw new IllegalArgumentException("invalid locale: " + locale);
        }
    }

    public static String getEventTemplate() {
        return PROPERTIES.getStringProperty("log4j.layout.template.eventTemplate");
    }

    public static String getEventTemplateUri() {
        return PROPERTIES.getStringProperty("log4j.layout.template.eventTemplateUri", "classpath:EcsLayout.yaml");
    }

    public static String getEventTemplateRootObjectKey() {
        return PROPERTIES.getStringProperty("log4j.layout.template.eventTemplateRootObjectKey");
    }

    public static String getStackTraceElementTemplate() {
        return PROPERTIES.getStringProperty("log4j.layout.template.stackTraceElementTemplate");
    }

    public static String getStackTraceElementTemplateUri() {
        return PROPERTIES.getStringProperty(
                "log4j.layout.template.stackTraceElementTemplateUri", "classpath:StackTraceElementLayout.yaml");
    }

    public static String getEventDelimiter() {
        return PROPERTIES.getStringProperty("log4j.layout.template.eventDelimiter", LINE_SEPARATOR);
    }

    public static boolean isNullEventDelimiterEnabled() {
        return PROPERTIES.getBooleanProperty("log4j.layout.template.nullEventDelimiterEnabled", false);
    }

    public static int getMaxStringLength() {
        final int maxStringLength =
                PROPERTIES.getIntegerProperty("log4j.layout.template.maxStringLength", 16 * 1_024);
        if (maxStringLength <= 0) {
            throw new IllegalArgumentException("was expecting a non-zero positive maxStringLength: " + maxStringLength);
        }
        return maxStringLength;
    }

    public static String getTruncatedStringSuffix() {
        return PROPERTIES.getStringProperty("log4j.layout.template.truncatedStringSuffix", "…");
    }

    public static RecyclerFactory getRecyclerFactory() {

        // Get the recycler factory specification
        final String propertyName = "log4j.layout.template.recyclerFactory";
        final String recyclerFactorySpec = PROPERTIES.getStringProperty(propertyName);

        // Read the specification
        @SuppressWarnings("unchecked")
        final TypeConverter<RecyclerFactory> typeConverter = (TypeConverter<RecyclerFactory>)
                TypeConverterRegistry.getInstance().findCompatibleConverter(RecyclerFactory.class);
        final RecyclerFactory recyclerFactory;
        try {
            // Using `TypeConverter#convert()` instead of `TypeConverters.convert`.
            // The latter doesn't pass the converter a null value, which is a valid input.
            recyclerFactory = typeConverter.convert(recyclerFactorySpec);
        } catch (final Exception error) {
            final String message = String.format(
                    "failed converting the recycler factory specified by the `%s` property: %s",
                    propertyName, recyclerFactorySpec == null ? null : '`' + recyclerFactorySpec + '`');
            throw new RuntimeException(message, error);
        }

        // Verify and return loaded recycler factory
        if (recyclerFactory == null) {
            final String message = String.format(
                    "could not determine the recycler factory specified by the `%s` property: %s",
                    propertyName, recyclerFactorySpec == null ? null : '`' + recyclerFactorySpec + '`');
            throw new IllegalArgumentException(message);
        }
        return recyclerFactory;
    }
}
