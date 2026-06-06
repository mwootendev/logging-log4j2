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
package org.apache.logging.log4j.layout.template.yaml.util;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import org.apache.logging.log4j.layout.template.structured.util.StructuredWriter;
import org.apache.logging.log4j.util.IndexedReadOnlyStringMap;
import org.apache.logging.log4j.util.StringBuilderFormattable;
import org.apache.logging.log4j.util.StringMap;

public final class YamlWriter implements StructuredWriter, Cloneable {

    private final StringBuilder stringBuilder;
    private final int maxStringLength;
    private final CharSequence truncatedStringSuffix;

    private int depth = 0;
    private boolean isFirstInContainer = true;
    private final boolean[] inArrayStack = new boolean[128]; // Max depth 128

    private YamlWriter(final Builder builder) {
        this.stringBuilder = new StringBuilder(builder.maxStringLength);
        this.maxStringLength = builder.maxStringLength;
        this.truncatedStringSuffix = builder.truncatedStringSuffix;
    }


    public YamlWriter clone() {
        return new Builder()
                .setMaxStringLength(maxStringLength)
                .setTruncatedStringSuffix(truncatedStringSuffix)
                .build();
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static final class Builder {

        private int maxStringLength;
        private CharSequence truncatedStringSuffix;

        private Builder() {}

        public Builder setMaxStringLength(final int maxStringLength) {
            this.maxStringLength = maxStringLength;
            return this;
        }

        public Builder setTruncatedStringSuffix(final CharSequence truncatedStringSuffix) {
            this.truncatedStringSuffix = truncatedStringSuffix;
            return this;
        }

        public YamlWriter build() {
            return new YamlWriter(this);
        }
    }

    private void writeIndent() {
        for (int i = 0; i < depth; i++) {
            stringBuilder.append("  ");
        }
    }

    private void writeNewlineAndIndent() {
        stringBuilder.append('\n');
        writeIndent();
    }


    public void writeValue(final Object value) {
        if (value == null) {
            writeNull();
        } else if (value instanceof CharSequence) {
            writeString((CharSequence) value);
        } else if (value instanceof Number) {
            writeNumber((Number) value);
        } else if (value instanceof Boolean) {
            writeBoolean((Boolean) value);
        } else if (value instanceof Map) {
            writeObject((Map<String, ?>) value);
        } else if (value instanceof Collection) {
            writeArray((Collection<?>) value);
        } else if (value.getClass().isArray()) {
            if (value instanceof byte[]) writeArray((byte[]) value);
            else if (value instanceof short[]) writeArray((short[]) value);
            else if (value instanceof int[]) writeArray((int[]) value);
            else if (value instanceof long[]) writeArray((long[]) value);
            else if (value instanceof float[]) writeArray((float[]) value);
            else if (value instanceof double[]) writeArray((double[]) value);
            else if (value instanceof boolean[]) writeArray((boolean[]) value);
            else if (value instanceof char[]) writeArray((char[]) value);
            else writeArray((Object[]) value);
        } else {
            writeString(String.valueOf(value));
        }
    }


    public void writeNull() {
        stringBuilder.append("null");
    }


    public void writeObjectStart() {
        if (depth == 0) {
            stringBuilder.append("---");
        }
        depth++;
        isFirstInContainer = true;
        inArrayStack[depth] = false;
    }


    public void writeObjectEnd() {
        depth--;
        isFirstInContainer = false; // Inherit state from previous
    }


    public void writeObjectKey(final String key) {
        if (!isFirstInContainer || depth > 1) {
            writeNewlineAndIndent();
        }
        writeString(key);
        stringBuilder.append(':').append(' ');
        isFirstInContainer = false;
    }


    public void writeObjectKey(final CharSequence key) {
        writeObjectKey(key.toString());
    }


    public void writeArrayStart() {
        depth++;
        isFirstInContainer = true;
        inArrayStack[depth] = true;
    }


    public void writeArrayEnd() {
        depth--;
        isFirstInContainer = false;
    }


    public void writeSeparator() {
        if (depth > 0 && inArrayStack[depth]) {
            writeNewlineAndIndent();
            stringBuilder.append("- ");
        }
    }


    public void writeString(final String string) {
        quoteString(string);
    }


    public void writeString(final CharSequence seq) {
        quoteString(seq.toString());
    }


    public void writeString(final char[] buffer, final int offset, final int length) {
        quoteString(new String(buffer, offset, length));
    }


    public void writeString(final StringBuilderFormattable formattable) {
        final StringBuilder temp = new StringBuilder();
        formattable.formatTo(temp);
        quoteString(temp.toString());
    }


    public <V> void writeString(final BiConsumer<StringBuilder, V> formattable, final V state) {
        final StringBuilder temp = new StringBuilder();
        formattable.accept(temp, state);
        quoteString(temp.toString());
    }

    private void quoteString(String string) {
        if (string.length() > maxStringLength) {
            string = string.substring(0, maxStringLength) + truncatedStringSuffix;
        }
        stringBuilder.append('"');
        for (int i = 0; i < string.length(); i++) {
            char c = string.charAt(i);
            if (c == '"') {
                stringBuilder.append("\\\"");
            } else if (c == '\\') {
                stringBuilder.append("\\\\");
            } else if (c == '\n') {
                stringBuilder.append("\\n");
            } else if (c == '\r') {
                stringBuilder.append("\\r");
            } else if (c == '\t') {
                stringBuilder.append("\\t");
            } else if (c < 0x20) {
                stringBuilder.append(String.format("\\u%04x", (int) c));
            } else {
                stringBuilder.append(c);
            }
        }
        stringBuilder.append('"');
    }


    public void writeNumber(final Number number) {
        stringBuilder.append(number);
    }


    public void writeNumber(final BigDecimal number) {
        stringBuilder.append(number);
    }


    public void writeNumber(final BigInteger number) {
        stringBuilder.append(number);
    }


    public void writeNumber(final float number) {
        stringBuilder.append(number);
    }


    public void writeNumber(final double number) {
        stringBuilder.append(number);
    }


    public void writeNumber(final short number) {
        stringBuilder.append(number);
    }


    public void writeNumber(final int number) {
        stringBuilder.append(number);
    }


    public void writeNumber(final long number) {
        stringBuilder.append(number);
    }


    public void writeBoolean(final boolean value) {
        stringBuilder.append(value);
    }


    public <V> void writeArray(final Collection<V> collection) {
        if (collection == null) {
            writeNull();
            return;
        }
        writeArrayStart();
        for (V item : collection) {
            writeSeparator();
            writeValue(item);
        }
        writeArrayEnd();
    }


    public <V> void writeArray(final List<V> list) {
        writeArray((Collection<V>) list);
    }


    public <V> void writeArray(final V[] items) {
        if (items == null) {
            writeNull();
            return;
        }
        writeArrayStart();
        for (V item : items) {
            writeSeparator();
            writeValue(item);
        }
        writeArrayEnd();
    }


    public <V> void writeArray(final V[] items, BiConsumer<V, StructuredWriter> itemWriter) {
        if (items == null) {
            writeNull();
            return;
        }
        writeArrayStart();
        for (V item : items) {
            writeSeparator();
            itemWriter.accept(item, this);
        }
        writeArrayEnd();
    }


    public void writeArray(final byte[] items) {
        if (items == null) {
            writeNull();
            return;
        }
        writeArrayStart();
        for (byte item : items) {
            writeSeparator();
            writeNumber(item);
        }
        writeArrayEnd();
    }

    public void writeArray(final short[] items) {
        if (items == null) {
            writeNull();
            return;
        }
        writeArrayStart();
        for (short item : items) {
            writeSeparator();
            writeNumber(item);
        }
        writeArrayEnd();
    }


    public void writeArray(final int[] items) {
        if (items == null) {
            writeNull();
            return;
        }
        writeArrayStart();
        for (int item : items) {
            writeSeparator();
            writeNumber(item);
        }
        writeArrayEnd();
    }


    public void writeArray(final long[] items) {
        if (items == null) {
            writeNull();
            return;
        }
        writeArrayStart();
        for (long item : items) {
            writeSeparator();
            writeNumber(item);
        }
        writeArrayEnd();
    }


    public void writeArray(final float[] items) {
        if (items == null) {
            writeNull();
            return;
        }
        writeArrayStart();
        for (float item : items) {
            writeSeparator();
            writeNumber(item);
        }
        writeArrayEnd();
    }


    public void writeArray(final double[] items) {
        if (items == null) {
            writeNull();
            return;
        }
        writeArrayStart();
        for (double item : items) {
            writeSeparator();
            writeNumber(item);
        }
        writeArrayEnd();
    }


    public void writeArray(final boolean[] items) {
        if (items == null) {
            writeNull();
            return;
        }
        writeArrayStart();
        for (boolean item : items) {
            writeSeparator();
            writeBoolean(item);
        }
        writeArrayEnd();
    }


    public void writeArray(final char[] items) {
        if (items == null) {
            writeNull();
            return;
        }
        writeArrayStart();
        for (char item : items) {
            writeSeparator();
            writeString(String.valueOf(item));
        }
        writeArrayEnd();
    }


    public <V> void writeObject(final Map<String, V> map) {
        if (map == null) {
            writeNull();
            return;
        }
        writeObjectStart();
        for (Map.Entry<String, V> entry : map.entrySet()) {
            writeObjectKey(entry.getKey());
            writeValue(entry.getValue());
        }
        writeObjectEnd();
    }


    public void writeObject(final IndexedReadOnlyStringMap map) {
        if (map == null) {
            writeNull();
            return;
        }
        writeObjectStart();
        for (int i = 0; i < map.size(); i++) {
            writeObjectKey(map.getKeyAt(i));
            writeValue(map.getValueAt(i));
        }
        writeObjectEnd();
    }


    public void writeObject(final StringMap map) {
        if (map == null) {
            writeNull();
            return;
        }
        writeObjectStart();
        map.forEach((key, value) -> {
            writeObjectKey(key);
            writeValue(value);
        });
        writeObjectEnd();
    }


    public void writeRawString(final CharSequence seq) {
        stringBuilder.append(seq);
    }


    public void writeRawString(final char[] buffer, final int offset, final int length) {
        stringBuilder.append(buffer, offset, length);
    }


    public StringBuilder getStringBuilder() {
        return stringBuilder;
    }


    public void close() {
        stringBuilder.setLength(0);
        depth = 0;
        isFirstInContainer = true;
    }


    public char[] getBuffer() {
        // This relies on accessing StringBuilder's internal buffer, which is not public.
        // As a simple workaround for the interface, we can just return a char array.
        // The original JsonWriter used its own internal character array or reflection.
        // For YamlWriter, we just provide a copy if needed, or an empty one since log4j doesn't heavily rely on it if not present.
        char[] result = new char[stringBuilder.length()];
        stringBuilder.getChars(0, stringBuilder.length(), result, 0);
        return result;
    }


    public int getBufferCapacity() {
        return stringBuilder.capacity();
    }


    public boolean isObjectStart() {
        return depth > 0 && !inArrayStack[depth];
    }


    public boolean isArrayStart() {
        return depth > 0 && inArrayStack[depth];
    }


    public String use(final Runnable runnable) {
        final int startIndex = stringBuilder.length();
        runnable.run();
        final String result = stringBuilder.substring(startIndex);
        stringBuilder.setLength(startIndex);
        return result;
    }


    public int getMaxStringLength() {
        return maxStringLength;
    }
}
