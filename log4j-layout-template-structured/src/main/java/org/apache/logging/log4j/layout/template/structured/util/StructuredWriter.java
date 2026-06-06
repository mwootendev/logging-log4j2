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
package org.apache.logging.log4j.layout.template.structured.util;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import org.apache.logging.log4j.util.IndexedReadOnlyStringMap;
import org.apache.logging.log4j.util.StringBuilderFormattable;
import org.apache.logging.log4j.util.StringMap;

public interface StructuredWriter extends AutoCloseable {

    void writeValue(Object value);

    void writeNull();

    void writeObjectStart();

    void writeObjectEnd();

    void writeObjectKey(String key);

    void writeObjectKey(CharSequence key);

    void writeArrayStart();

    void writeArrayEnd();

    void writeString(String string);

    void writeString(CharSequence seq);

    void writeString(char[] buffer, int offset, int length);

    void writeString(StringBuilderFormattable formattable);

    void writeNumber(Number number);

    void writeNumber(BigDecimal number);

    void writeNumber(BigInteger number);

    void writeNumber(float number);

    void writeNumber(double number);

    void writeNumber(short number);

    void writeNumber(int number);

    void writeNumber(long number);

    void writeBoolean(boolean value);

    <V> void writeArray(Collection<V> collection);

    <V> void writeArray(List<V> list);

    <V> void writeArray(V[] items);

    <V> void writeArray(V[] items, BiConsumer<V, StructuredWriter> itemWriter);

    void writeArray(short[] items);

    void writeArray(int[] items);

    void writeArray(long[] items);

    void writeArray(float[] items);

    void writeArray(double[] items);

    void writeArray(boolean[] items);

    void writeArray(char[] items);

    <V> void writeObject(Map<String, V> map);

    void writeObject(IndexedReadOnlyStringMap map);

    void writeObject(StringMap map);

    void writeRawString(CharSequence seq);

    void writeRawString(char[] buffer, int offset, int length);

    StringBuilder getStringBuilder();

    @Override
    void close();

    char[] getBuffer();

    int getBufferCapacity();

    boolean isObjectStart();
    boolean isArrayStart();


    void writeSeparator();

    String use(Runnable runnable);

    <V> void writeString(java.util.function.BiConsumer<StringBuilder, V> formattable, V state);

    int getMaxStringLength();
}
