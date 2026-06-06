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
package org.apache.logging.log4j.layout.template.structured;

import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;
import org.apache.logging.log4j.util.Strings;

@Plugin(name = "EventTemplateAdditionalField", category = Node.CATEGORY, printObject = true)
public class EventTemplateAdditionalField {

    public enum Format {
        STRING,
        JSON
    }

    private final String key;
    private final String value;
    private final Format format;

    private EventTemplateAdditionalField(final Builder builder) {
        this.key = builder.key;
        this.value = builder.value;
        this.format = builder.format;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public Format getFormat() {
        return format;
    }

    @Override
    public int hashCode() {
        int result = key != null ? key.hashCode() : 0;
        result = 31 * result + (value != null ? value.hashCode() : 0);
        result = 31 * result + (format != null ? format.hashCode() : 0);
        return result;
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        final EventTemplateAdditionalField that = (EventTemplateAdditionalField) object;
        if (key != null ? !key.equals(that.key) : that.key != null) {
            return false;
        }
        if (value != null ? !value.equals(that.value) : that.value != null) {
            return false;
        }
        return format == that.format;
    }

    @Override
    public String toString() {
        final String formattedValue = Format.STRING.equals(format) ? String.format("\"%s\"", value) : value;
        return String.format("%s=%s", key, formattedValue);
    }

    @PluginBuilderFactory
    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder implements org.apache.logging.log4j.core.util.Builder<EventTemplateAdditionalField> {

        @PluginBuilderAttribute
        private String key;

        @PluginBuilderAttribute
        private String value;

        @PluginBuilderAttribute
        private Format format = Format.STRING;

        public Builder setKey(final String key) {
            this.key = key;
            return this;
        }

        public Builder setValue(final String value) {
            this.value = value;
            return this;
        }

        public Builder setFormat(final Format format) {
            this.format = format;
            return this;
        }

        @Override
        public EventTemplateAdditionalField build() {
            if (Strings.isBlank(key)) {
                throw new IllegalArgumentException("blank key");
            }
            if (Strings.isBlank(value)) {
                throw new IllegalArgumentException("blank value");
            }
            if (format == null) {
                throw new IllegalArgumentException("null format");
            }
            return new EventTemplateAdditionalField(this);
        }

    }

}
