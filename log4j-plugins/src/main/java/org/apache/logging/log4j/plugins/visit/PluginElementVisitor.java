/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache license, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the license for the specific language governing permissions and
 * limitations under the license.
 */

package org.apache.logging.log4j.plugins.visit;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.plugins.Node;
import org.apache.logging.log4j.plugins.name.AnnotatedElementAliasesProvider;
import org.apache.logging.log4j.plugins.name.AnnotatedElementNameProvider;
import org.apache.logging.log4j.plugins.util.PluginType;
import org.apache.logging.log4j.plugins.util.TypeUtil;
import org.apache.logging.log4j.status.StatusLogger;
import org.apache.logging.log4j.util.StringBuilders;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class PluginElementVisitor implements NodeVisitor {
    private static final Logger LOGGER = StatusLogger.getLogger();

    @Override
    public Object visitField(final Field field, final Node node, final StringBuilder debugLog) {
        final String name = AnnotatedElementNameProvider.getName(field);
        final Collection<String> aliases = AnnotatedElementAliasesProvider.getAliases(field);
        final Type targetType = field.getGenericType();
        final Class<?> componentType = getComponentType(targetType);
        return TypeUtil.cast(componentType != null ? parseArrayElement(node, name, aliases, componentType, debugLog) :
                parseChildElement(node, name, aliases, targetType, debugLog));
    }

    @Override
    public Object visitParameter(final Parameter parameter, final Node node, final StringBuilder debugLog) {
        final String name = AnnotatedElementNameProvider.getName(parameter);
        final Collection<String> aliases = AnnotatedElementAliasesProvider.getAliases(parameter);
        final Type targetType = parameter.getParameterizedType();
        final Class<?> componentType = getComponentType(targetType);
        return TypeUtil.cast(componentType != null ? parseArrayElement(node, name, aliases, componentType, debugLog) :
                parseChildElement(node, name, aliases, targetType, debugLog));
    }

    private static Object[] parseArrayElement(
            final Node node, final String name, final Collection<String> aliases, final Class<?> componentType,
            final StringBuilder debugLog) {
        final Iterator<Node> iterator = node.getChildren().iterator();
        final List<Object> values = new ArrayList<>();
        if (debugLog.length() > 0) {
            debugLog.append(", ");
        }
        debugLog.append(name).append("={");
        boolean first = true;
        while (iterator.hasNext()) {
            final Node child = iterator.next();
            final PluginType<?> pluginType = child.getType();
            final String elementName = pluginType.getElementName();
            if (name.equalsIgnoreCase(elementName) || aliases.stream().anyMatch(elementName::equalsIgnoreCase) ||
                    componentType.isAssignableFrom(pluginType.getPluginClass())) {
                if (!first) {
                    debugLog.append(", ");
                }
                first = false;
                final Object childObject = child.getObject();
                if (childObject == null) {
                    LOGGER.error("Skipping null child object with name {} in element {}", child.getName(), node.getName());
                    continue;
                }
                iterator.remove();
                if (childObject.getClass().isArray()) {
                    final Object[] array = (Object[]) childObject;
                    debugLog.append(Arrays.toString(array)).append('}');
                    return array;
                } else {
                    debugLog.append(childObject);
                    values.add(childObject);
                }
            }
        }
        debugLog.append('}');
        final Object[] objects = (Object[]) Array.newInstance(componentType, values.size());
        for (int i = 0; i < objects.length; i++) {
            objects[i] = values.get(i);
        }
        return objects;
    }

    private static Object parseChildElement(
            final Node node, final String name, final Collection<String> aliases, final Type targetType,
            final StringBuilder debugLog) {
        final Iterator<Node> iterator = node.getChildren().iterator();
        while (iterator.hasNext()) {
            final Node child = iterator.next();
            final PluginType<?> pluginType = child.getType();
            final String elementName = pluginType.getElementName();
            if (name.equalsIgnoreCase(elementName) || aliases.stream().anyMatch(elementName::equalsIgnoreCase) ||
                    TypeUtil.isAssignable(targetType, pluginType.getPluginClass())) {
                iterator.remove();
                final Object value = child.getObject();
                StringBuilders.appendKeyDqValueWithJoiner(debugLog, name, value, ", ");
                return value;
            }
        }
        return null;
    }

    private static Class<?> getComponentType(final Type type) {
        if (type instanceof Class<?>) {
            return ((Class<?>) type).getComponentType();
        }
        return null;
    }
}
