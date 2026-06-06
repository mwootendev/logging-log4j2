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

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.StringLayout;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;
import org.apache.logging.log4j.core.config.plugins.PluginConfiguration;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.impl.LocationAware;
import org.apache.logging.log4j.core.layout.ByteBufferDestination;
import org.apache.logging.log4j.core.layout.Encoder;
import org.apache.logging.log4j.core.layout.TextEncoderHelper;
import org.apache.logging.log4j.core.util.Constants;
import org.apache.logging.log4j.core.util.StringEncoder;
import org.apache.logging.log4j.layout.template.structured.resolver.EventResolverContext;
import org.apache.logging.log4j.layout.template.structured.resolver.EventResolverFactories;
import org.apache.logging.log4j.layout.template.structured.resolver.EventResolverFactory;
import org.apache.logging.log4j.layout.template.structured.resolver.EventResolverInterceptor;
import org.apache.logging.log4j.layout.template.structured.resolver.EventResolverInterceptors;
import org.apache.logging.log4j.layout.template.structured.resolver.EventResolverStringSubstitutor;
import org.apache.logging.log4j.layout.template.structured.resolver.TemplateResolver;
import org.apache.logging.log4j.layout.template.structured.resolver.TemplateResolvers;
import org.apache.logging.log4j.layout.template.structured.util.StructuredWriter;
import org.apache.logging.log4j.layout.template.structured.EventTemplateAdditionalField;
import org.apache.logging.log4j.layout.template.yaml.util.YamlWriter;
import org.apache.logging.log4j.layout.template.structured.util.Recycler;
import org.apache.logging.log4j.layout.template.structured.util.RecyclerFactory;
import org.apache.logging.log4j.layout.template.structured.util.Uris;
import org.apache.logging.log4j.status.StatusLogger;
import org.apache.logging.log4j.util.Strings;

@Plugin(name = "YamlTemplateLayout", category = Node.CATEGORY, elementType = Layout.ELEMENT_TYPE)
public class YamlTemplateLayout implements StringLayout, LocationAware {

    private static final Map<String, String> CONTENT_FORMAT = Collections.singletonMap("version", "1");

    private final Charset charset;

    private final String contentType;

    private final boolean locationInfoEnabled;

    private final TemplateResolver<LogEvent> eventResolver;

    private final String eventDelimiter;

    private final Recycler<Context> contextRecycler;

    private static final class Context implements AutoCloseable {

        final YamlWriter yamlWriter;

        final Encoder<StringBuilder> encoder;

        private Context(final YamlWriter yamlWriter, final Encoder<StringBuilder> encoder) {
            this.yamlWriter = yamlWriter;
            this.encoder = encoder;
        }

        @Override
        public void close() {
            yamlWriter.close();
        }
    }

    private YamlTemplateLayout(final Builder builder) {
        this.charset = builder.charset;
        this.contentType = "application/yaml; charset=" + charset;
        this.locationInfoEnabled = builder.locationInfoEnabled;
        final String eventDelimiterSuffix = builder.isNullEventDelimiterEnabled() ? "\0" : "";
        this.eventDelimiter = builder.eventDelimiter + eventDelimiterSuffix;
        final Configuration configuration = builder.configuration;
        final YamlWriter yamlWriter = YamlWriter.newBuilder()
                .setMaxStringLength(builder.maxStringLength)
                .setTruncatedStringSuffix(builder.truncatedStringSuffix)
                .build();
        this.eventResolver = createEventResolver(builder, configuration, charset, yamlWriter);
        this.contextRecycler = createContextRecycler(builder, yamlWriter);
    }

    private TemplateResolver<LogEvent> createEventResolver(
            final Builder builder,
            final Configuration configuration,
            final Charset charset,
            final YamlWriter yamlWriter) {

        // Inject resolver factory and interceptor plugins.
        final List<String> pluginPackages = configuration.getPluginPackages();
        final Map<String, EventResolverFactory> resolverFactoryByName =
                EventResolverFactories.populateResolverFactoryByName(pluginPackages);
        final List<EventResolverInterceptor> resolverInterceptors =
                EventResolverInterceptors.populateInterceptors(pluginPackages);
        final EventResolverStringSubstitutor substitutor =
                new EventResolverStringSubstitutor(configuration.getStrSubstitutor());

        // Read event and stack trace element templates.
        final String eventTemplate = readEventTemplate(builder);
        final String stackTraceElementTemplate = readStackTraceElementTemplate(builder);

        // Determine the max. string byte count.
        final float maxByteCountPerChar = builder.charset.newEncoder().maxBytesPerChar();
        final int maxStringByteCount =
                Math.toIntExact(Math.round(Math.ceil(maxByteCountPerChar * builder.maxStringLength)));

        // Replace null event template additional fields with an empty array.
        final EventTemplateAdditionalField[] eventTemplateAdditionalFields =
                builder.eventTemplateAdditionalFields != null
                        ? builder.eventTemplateAdditionalFields
                        : new EventTemplateAdditionalField[0];

        // Create the resolver context.
        final EventResolverContext resolverContext = EventResolverContext.newBuilder()
                .setConfiguration(configuration)
                .setResolverFactoryByName(resolverFactoryByName)
                .setResolverInterceptors(resolverInterceptors)
                .setSubstitutor(substitutor)
                .setCharset(charset)
                .setStructuredWriter(yamlWriter)
                .setRecyclerFactory(builder.recyclerFactory)
                .setMaxStringByteCount(maxStringByteCount)
                .setTruncatedStringSuffix(builder.truncatedStringSuffix)
                .setLocationInfoEnabled(builder.locationInfoEnabled)
                .setStackTraceEnabled(builder.stackTraceEnabled)
                .setStackTraceElementTemplate(stackTraceElementTemplate)
                .setEventTemplateRootObjectKey(builder.eventTemplateRootObjectKey)
                .setEventTemplateAdditionalFields(eventTemplateAdditionalFields)
                .build();

        // Compile the resolver template.
        return TemplateResolvers.ofTemplate(resolverContext, eventTemplate);
    }

    private static String readEventTemplate(final Builder builder) {
        return readTemplate(builder.eventTemplate, builder.eventTemplateUri, builder.charset);
    }

    private static String readStackTraceElementTemplate(final Builder builder) {
        return readTemplate(builder.stackTraceElementTemplate, builder.stackTraceElementTemplateUri, builder.charset);
    }

    private static String readTemplate(final String template, final String templateUri, final Charset charset) {
        return Strings.isBlank(template) ? Uris.readUri(templateUri, charset) : template;
    }

    private static Recycler<Context> createContextRecycler(final Builder builder, final YamlWriter yamlWriter) {
        final Supplier<Context> supplier = createContextSupplier(builder.charset, yamlWriter);
        return builder.recyclerFactory.create(supplier, Context::close);
    }

    private static Supplier<Context> createContextSupplier(final Charset charset, final YamlWriter yamlWriter) {
        return () -> {
            final YamlWriter clonedStructuredWriter = yamlWriter.clone();
            final Encoder<StringBuilder> encoder = new StringBuilderEncoder(charset);
            return new Context(clonedStructuredWriter, encoder);
        };
    }

    /**
     * {@link org.apache.logging.log4j.core.layout.StringBuilderEncoder} clone replacing thread-local allocations with instance fields.
     */
    private static final class StringBuilderEncoder implements Encoder<StringBuilder> {

        private final Charset charset;

        private final CharsetEncoder charsetEncoder;

        private final CharBuffer charBuffer;

        private final ByteBuffer byteBuffer;

        private StringBuilderEncoder(final Charset charset) {
            this.charset = charset;
            this.charsetEncoder = charset.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPLACE)
                    .onUnmappableCharacter(CodingErrorAction.REPLACE);
            this.charBuffer = CharBuffer.allocate(Constants.ENCODER_CHAR_BUFFER_SIZE);
            this.byteBuffer = ByteBuffer.allocate(Constants.ENCODER_BYTE_BUFFER_SIZE);
        }

        @Override
        public void encode(final StringBuilder source, final ByteBufferDestination destination) {
            try {
                TextEncoderHelper.encodeText(charsetEncoder, charBuffer, byteBuffer, source, destination);
            } catch (final Exception error) {
                fallbackEncode(charset, source, destination, error);
            }
        }

        private /* for JIT-ergonomics: */ static void fallbackEncode(
                final Charset charset,
                final StringBuilder source,
                final ByteBufferDestination destination,
                final Exception error) {
            StatusLogger.getLogger().error("TextEncoderHelper.encodeText() failure", error);
            final byte[] bytes = source.toString().getBytes(charset);
            destination.writeBytes(bytes, 0, bytes.length);
        }
    }

    @Override
    public byte[] toByteArray(final LogEvent event) {
        final String eventYaml = toSerializable(event);
        return StringEncoder.toBytes(eventYaml, charset);
    }

    @Override
    public String toSerializable(final LogEvent event) {

        // Acquire a context.
        final Recycler<Context> contextRecycler = this.contextRecycler;
        final Context context = contextRecycler.acquire();
        final YamlWriter yamlWriter = context.yamlWriter;
        final StringBuilder stringBuilder = yamlWriter.getStringBuilder();

        // Render the JSON.
        try {
            eventResolver.resolve(event, yamlWriter);
            stringBuilder.append(eventDelimiter);
            return stringBuilder.toString();
        }

        // Release the context.
        finally {
            contextRecycler.release(context);
        }
    }

    @Override
    public void encode(final LogEvent event, final ByteBufferDestination destination) {

        // Acquire a context.
        final Recycler<Context> contextRecycler = this.contextRecycler;
        final Context context = contextRecycler.acquire();
        final YamlWriter yamlWriter = context.yamlWriter;
        final StringBuilder stringBuilder = yamlWriter.getStringBuilder();
        final Encoder<StringBuilder> encoder = context.encoder;

        // Render & write the JSON.
        try {
            eventResolver.resolve(event, yamlWriter);
            stringBuilder.append(eventDelimiter);
            encoder.encode(stringBuilder, destination);
        }

        // Release the context.
        finally {
            contextRecycler.release(context);
        }
    }

    @Override
    public byte[] getFooter() {
        return null;
    }

    @Override
    public byte[] getHeader() {
        return null;
    }

    @Override
    public Charset getCharset() {
        return charset;
    }

    @Override
    public boolean requiresLocation() {
        return locationInfoEnabled;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public Map<String, String> getContentFormat() {
        return CONTENT_FORMAT;
    }

    @PluginBuilderFactory
    @SuppressWarnings("WeakerAccess")
    public static Builder newBuilder() {
        return new Builder();
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public static final class Builder implements org.apache.logging.log4j.core.util.Builder<YamlTemplateLayout> {

        @PluginConfiguration
        private Configuration configuration;

        @PluginBuilderAttribute
        private Charset charset = YamlTemplateLayoutDefaults.getCharset();

        @PluginBuilderAttribute
        private boolean locationInfoEnabled = YamlTemplateLayoutDefaults.isLocationInfoEnabled();

        @PluginBuilderAttribute
        private boolean stackTraceEnabled = YamlTemplateLayoutDefaults.isStackTraceEnabled();

        @PluginBuilderAttribute
        private String eventTemplate = YamlTemplateLayoutDefaults.getEventTemplate();

        @PluginBuilderAttribute
        private String eventTemplateUri = YamlTemplateLayoutDefaults.getEventTemplateUri();

        @PluginBuilderAttribute
        private String eventTemplateRootObjectKey = YamlTemplateLayoutDefaults.getEventTemplateRootObjectKey();

        @PluginElement("EventTemplateAdditionalField")
        private EventTemplateAdditionalField[] eventTemplateAdditionalFields;

        @PluginBuilderAttribute
        private String stackTraceElementTemplate = YamlTemplateLayoutDefaults.getStackTraceElementTemplate();

        @PluginBuilderAttribute
        private String stackTraceElementTemplateUri = YamlTemplateLayoutDefaults.getStackTraceElementTemplateUri();

        @PluginBuilderAttribute
        private String eventDelimiter = YamlTemplateLayoutDefaults.getEventDelimiter();

        @PluginBuilderAttribute
        private boolean nullEventDelimiterEnabled = YamlTemplateLayoutDefaults.isNullEventDelimiterEnabled();

        @PluginBuilderAttribute
        private int maxStringLength = YamlTemplateLayoutDefaults.getMaxStringLength();

        @PluginBuilderAttribute
        private String truncatedStringSuffix = YamlTemplateLayoutDefaults.getTruncatedStringSuffix();

        @PluginBuilderAttribute
        private RecyclerFactory recyclerFactory = YamlTemplateLayoutDefaults.getRecyclerFactory();

        private Builder() {
            // Do nothing.
        }

        public Configuration getConfiguration() {
            return configuration;
        }

        public Builder setConfiguration(final Configuration configuration) {
            this.configuration = configuration;
            return this;
        }

        public Charset getCharset() {
            return charset;
        }

        public Builder setCharset(final Charset charset) {
            this.charset = charset;
            return this;
        }

        public boolean isLocationInfoEnabled() {
            return locationInfoEnabled;
        }

        public Builder setLocationInfoEnabled(final boolean locationInfoEnabled) {
            this.locationInfoEnabled = locationInfoEnabled;
            return this;
        }

        public boolean isStackTraceEnabled() {
            return stackTraceEnabled;
        }

        public Builder setStackTraceEnabled(final boolean stackTraceEnabled) {
            this.stackTraceEnabled = stackTraceEnabled;
            return this;
        }

        public String getEventTemplate() {
            return eventTemplate;
        }

        public Builder setEventTemplate(final String eventTemplate) {
            this.eventTemplate = eventTemplate;
            return this;
        }

        public String getEventTemplateUri() {
            return eventTemplateUri;
        }

        public Builder setEventTemplateUri(final String eventTemplateUri) {
            this.eventTemplateUri = eventTemplateUri;
            return this;
        }

        public String getEventTemplateRootObjectKey() {
            return eventTemplateRootObjectKey;
        }

        public Builder setEventTemplateRootObjectKey(final String eventTemplateRootObjectKey) {
            this.eventTemplateRootObjectKey = eventTemplateRootObjectKey;
            return this;
        }

        public EventTemplateAdditionalField[] getEventTemplateAdditionalFields() {
            return eventTemplateAdditionalFields;
        }

        public Builder setEventTemplateAdditionalFields(
                final EventTemplateAdditionalField[] eventTemplateAdditionalFields) {
            this.eventTemplateAdditionalFields = eventTemplateAdditionalFields;
            return this;
        }

        public String getStackTraceElementTemplate() {
            return stackTraceElementTemplate;
        }

        public Builder setStackTraceElementTemplate(final String stackTraceElementTemplate) {
            this.stackTraceElementTemplate = stackTraceElementTemplate;
            return this;
        }

        public String getStackTraceElementTemplateUri() {
            return stackTraceElementTemplateUri;
        }

        public Builder setStackTraceElementTemplateUri(final String stackTraceElementTemplateUri) {
            this.stackTraceElementTemplateUri = stackTraceElementTemplateUri;
            return this;
        }

        public String getEventDelimiter() {
            return eventDelimiter;
        }

        public Builder setEventDelimiter(final String eventDelimiter) {
            this.eventDelimiter = eventDelimiter;
            return this;
        }

        public boolean isNullEventDelimiterEnabled() {
            return nullEventDelimiterEnabled;
        }

        public Builder setNullEventDelimiterEnabled(final boolean nullEventDelimiterEnabled) {
            this.nullEventDelimiterEnabled = nullEventDelimiterEnabled;
            return this;
        }

        public int getMaxStringLength() {
            return maxStringLength;
        }

        public Builder setMaxStringLength(final int maxStringLength) {
            this.maxStringLength = maxStringLength;
            return this;
        }

        public String getTruncatedStringSuffix() {
            return truncatedStringSuffix;
        }

        public Builder setTruncatedStringSuffix(final String truncatedStringSuffix) {
            this.truncatedStringSuffix = truncatedStringSuffix;
            return this;
        }

        public RecyclerFactory getRecyclerFactory() {
            return recyclerFactory;
        }

        public Builder setRecyclerFactory(final RecyclerFactory recyclerFactory) {
            this.recyclerFactory = recyclerFactory;
            return this;
        }

        @Override
        public YamlTemplateLayout build() {
            validate();
            return new YamlTemplateLayout(this);
        }

        private void validate() {
            Objects.requireNonNull(configuration, "configuration");
            if (Strings.isBlank(eventTemplate) && Strings.isBlank(eventTemplateUri)) {
                throw new IllegalArgumentException("both eventTemplate and eventTemplateUri are blank");
            }
            if (stackTraceEnabled
                    && Strings.isBlank(stackTraceElementTemplate)
                    && Strings.isBlank(stackTraceElementTemplateUri)) {
                throw new IllegalArgumentException(
                        "both stackTraceElementTemplate and stackTraceElementTemplateUri are blank");
            }
            if (maxStringLength <= 0) {
                throw new IllegalArgumentException(
                        "was expecting a non-zero positive maxStringLength: " + maxStringLength);
            }
            Objects.requireNonNull(truncatedStringSuffix, "truncatedStringSuffix");
            Objects.requireNonNull(recyclerFactory, "recyclerFactory");
        }
    }

}
