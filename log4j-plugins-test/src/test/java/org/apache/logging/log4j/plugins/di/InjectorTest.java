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

package org.apache.logging.log4j.plugins.di;

import org.apache.logging.log4j.plugins.Factory;
import org.apache.logging.log4j.plugins.Inject;
import org.apache.logging.log4j.plugins.Named;
import org.apache.logging.log4j.plugins.Node;
import org.apache.logging.log4j.plugins.Plugin;
import org.apache.logging.log4j.plugins.PluginAttribute;
import org.apache.logging.log4j.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.plugins.PluginElement;
import org.apache.logging.log4j.plugins.PluginValue;
import org.apache.logging.log4j.plugins.QualifierType;
import org.apache.logging.log4j.plugins.ScopeType;
import org.apache.logging.log4j.plugins.Singleton;
import org.apache.logging.log4j.plugins.processor.PluginEntry;
import org.apache.logging.log4j.plugins.test.validation.ValidatingPluginWithGenericBuilder;
import org.apache.logging.log4j.plugins.util.PluginType;
import org.apache.logging.log4j.plugins.util.TypeUtil;
import org.apache.logging.log4j.plugins.validation.constraints.Required;
import org.apache.logging.log4j.plugins.validation.constraints.RequiredProperty;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class InjectorTest {
    @Singleton
    static class BeanA {
    }

    @Singleton
    static class BeanB {
    }

    static class BeanC {
    }

    static class ExplicitConstructorInjection {
        final BeanA a;
        final BeanB b;
        final BeanC c;

        @Inject
        ExplicitConstructorInjection(final BeanA a, final BeanB b, final BeanC c) {
            this.a = a;
            this.b = b;
            this.c = c;
        }
    }

    @Test
    void canLoadDefaultConstructorInstanceOnDemand() {
        final BeanA bean = DI.createInjector().getInstance(BeanA.class);
        assertThat(bean).isNotNull();
    }

    @Test
    void singletonInstancesAreSameInstance() {
        final var factory = DI.createInjector().getFactory(BeanA.class);
        assertThat(factory.get()).isSameAs(factory.get());
    }

    @Test
    void unscopedInstancesFreshlyCreatedEachTime() {
        final var factory = DI.createInjector().getFactory(BeanC.class);
        assertThat(factory.get()).isNotNull().isNotSameAs(factory.get());
    }

    @Test
    void injectConstructorParametersInjection() {
        final ExplicitConstructorInjection bean = DI.createInjector().getInstance(ExplicitConstructorInjection.class);
        assertThat(bean).isNotNull();
        assertThat(bean.a).isNotNull();
        assertThat(bean.b).isNotNull();
        assertThat(bean.c).isNotNull();
    }

    static class NamedInjection {
        final BeanA defaultBean;
        final BeanA namedBean;

        @Inject
        NamedInjection(final BeanA defaultBean, @Named("a") final BeanA namedBean) {
            this.defaultBean = defaultBean;
            this.namedBean = namedBean;
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @QualifierType
    @interface SomeQualifier {
    }

    static class QualifiedInjection {
        final BeanB defaultBean;
        final BeanB qualifiedBean;

        @Inject
        QualifiedInjection(final BeanB defaultBean, @SomeQualifier final BeanB qualifiedBean) {
            this.defaultBean = defaultBean;
            this.qualifiedBean = qualifiedBean;
        }
    }

    static class QualifiedBeanModule {
        @Factory
        @Named("a")
        BeanA beanA() {
            return new BeanA();
        }

        @Factory
        @SomeQualifier
        BeanB beanB() {
            return new BeanB();
        }
    }

    @Test
    void implicitInjectionViaNamedConstructorParameter() {
        final Injector injector = DI.createInjector(new QualifiedBeanModule());
        final BeanA defaultBean = injector.getInstance(BeanA.class);
        final BeanA namedBean = injector.getInstance(new @Named("a") Key<>() {});
        final NamedInjection namedInjection = injector.getInstance(NamedInjection.class);
        assertThat(namedInjection.defaultBean).isSameAs(defaultBean).isNotSameAs(namedInjection.namedBean);
        assertThat(namedInjection.namedBean).isSameAs(namedBean);
    }

    @Test
    void implicitInjectionViaQualifierMetaAnnotatedParameter() {
        final QualifiedInjection qualifiedInjection = DI.createInjector(new QualifiedBeanModule())
                .getInstance(QualifiedInjection.class);
        assertThat(qualifiedInjection.defaultBean).isNotNull().isNotSameAs(qualifiedInjection.qualifiedBean);
        assertThat(qualifiedInjection.qualifiedBean).isNotNull();
    }

    static class MethodInjection {
        BeanA a;
        boolean initCalled;

        @Inject
        void setA(BeanA a) {
            this.a = a;
        }

        @Inject
        void init() {
            initCalled = true;
        }
    }

    @Test
    void methodInjection() {
        final MethodInjection methodInjection = DI.createInjector().getInstance(MethodInjection.class);
        assertThat(methodInjection.a).isNotNull();
        assertThat(methodInjection.initCalled).isTrue();
    }

    static class FieldInjection {
        @Inject
        BeanA bean;

        @Named
        BeanA a;
    }

    @Test
    void fieldInjection() {
        final FieldInjection fieldInjection =
                DI.createInjector(new QualifiedBeanModule()).getInstance(FieldInjection.class);
        assertThat(fieldInjection.a).isNotNull().isNotSameAs(fieldInjection.bean);
        assertThat(fieldInjection.bean).isNotNull();
    }

    @Test
    void ambiguousModuleError() {
        assertThatThrownBy(() -> DI.createInjector(new Object() {
            @Factory
            String alpha() {
                return "alpha";
            }

            @Factory
            String beta() {
                return "beta";
            }
        })).hasMessageContaining("Duplicate @Factory method");
    }

    static class CircularBeanA {
        final CircularBeanB bean;

        @Inject
        CircularBeanA(final CircularBeanB bean) {
            this.bean = bean;
        }
    }

    static class CircularBeanB {
        final CircularBeanA bean;

        @Inject
        CircularBeanB(final CircularBeanA bean) {
            this.bean = bean;
        }
    }

    @Test
    void circularDependenciesError() {
        assertThatThrownBy(() -> DI.createInjector().getInstance(CircularBeanA.class))
                .hasMessageContaining("Circular dependency encountered");
    }

    static class CircularBean {
        final CircularSupplierBean bean;

        @Inject
        CircularBean(final CircularSupplierBean bean) {
            this.bean = bean;
        }
    }

    static class CircularSupplierBean {
        final Supplier<CircularBean> beanSupplier;

        @Inject
        CircularSupplierBean(final Supplier<CircularBean> beanSupplier) {
            this.beanSupplier = beanSupplier;
        }
    }

    @Test
    void circularDependenciesWithSupplier() {
        final CircularSupplierBean bean = DI.createInjector().getInstance(CircularSupplierBean.class);
        assertThat(bean.beanSupplier.get()).isNotNull();
    }

    static class UnknownInstance {
        UnknownInstance(@Named String ignored) {
        }
    }

    @Test
    void unknownInstanceError() {
        final Key<UnknownInstance> key = new Key<>() {};
        assertThatThrownBy(() -> DI.createInjector().getInstance(key))
                .hasMessage("No @Inject constructors or no-arg constructor found for " + key);
    }

    @Singleton
    static class DeferredSingleton {
        private final int id;

        @Inject
        public DeferredSingleton(final int id) {
            this.id = id;
        }
    }

    static class DeferredDependent {
        private final int id;

        @Inject
        public DeferredDependent(final int id) {
            this.id = id;
        }
    }

    static class DeferredSupplierBean {
        @Inject
        Supplier<DeferredSingleton> singletonSupplier;

        @Inject
        Supplier<DeferredDependent> dependentSupplier;

        Supplier<DeferredDependent> methodInjectedSupplier;

        @Inject
        void setMethodInjectedSupplier(final Supplier<DeferredDependent> supplier) {
            methodInjectedSupplier = supplier;
        }
    }

    @Test
    void testDeferredSupplierNotInvokedUntilInitiallyProvided() {
        final AtomicInteger counter = new AtomicInteger();
        final DeferredSupplierBean bean = DI.createInjector(new Object() {
            @Factory
            int nextId() {
                return counter.incrementAndGet();
            }
        }).getInstance(DeferredSupplierBean.class);
        assertThat(counter.get()).isEqualTo(0);
        assertThat(bean.singletonSupplier.get().id).isEqualTo(1);
        assertThat(bean.singletonSupplier.get().id).isEqualTo(1);
        assertThat(counter.get()).isEqualTo(1);
        assertThat(bean.dependentSupplier.get().id).isEqualTo(2);
        assertThat(bean.singletonSupplier.get().id).isEqualTo(1);
        assertThat(counter.get()).isEqualTo(2);
        assertThat(bean.dependentSupplier.get().id).isEqualTo(3);
        assertThat(bean.singletonSupplier.get().id).isEqualTo(1);
        assertThat(counter.get()).isEqualTo(3);
        assertThat(bean.dependentSupplier.get().id).isEqualTo(4);
        assertThat(bean.singletonSupplier.get().id).isEqualTo(1);
        assertThat(counter.get()).isEqualTo(4);
        assertThat(bean.methodInjectedSupplier).isNotNull();
        assertThat(bean.methodInjectedSupplier.get().id).isEqualTo(5);
        assertThat(counter.get()).isEqualTo(5);
    }

    static class Primary {
    }

    static class Secondary {
        final Primary primary;

        @Inject
        Secondary(final Primary primary) {
            this.primary = primary;
        }
    }

    static class Tertiary {
        final Secondary secondary;

        @Inject
        Tertiary(final Secondary secondary) {
            this.secondary = secondary;
        }
    }

    @Test
    void chainedDependencies() {
        final Tertiary tertiary = DI.createInjector().getInstance(Tertiary.class);
        assertThat(tertiary.secondary.primary).isNotNull();
    }

    static class AliasModule {
        @Factory
        @Named({ "foo", "bar" })
        String foo() {
            return "bar";
        }
    }

    static class Aliases {
        @Named String foo;

        @Named String bar;

        @Named({ "invalid", "foo" }) String baz;

        final String constructed;

        String methodInjected;

        @Inject
        Aliases(@Named({ "", "foo" }) final String constructed) {
            this.constructed = constructed;
        }

        @Inject
        void setMethodInjected(@Named({ "baz", "bar" }) final String methodInjected) {
            this.methodInjected = methodInjected;
        }
    }

    @Test
    void supplierAliases() {
        final Aliases aliases = DI.createInjector(new AliasModule()).getInstance(Aliases.class);
        assertThat(List.of(aliases.foo, aliases.bar, aliases.bar, aliases.constructed, aliases.methodInjected))
                .allMatch("bar"::equals);
    }

    static class CustomSingletonScope implements Scope {
        private final Map<Key<?>, Object> bindings = new ConcurrentHashMap<>();

        @Override
        public <T> Supplier<T> get(final Key<T> key, final Supplier<T> unscoped) {
            return () -> TypeUtil.cast(bindings.computeIfAbsent(key, ignored -> unscoped.get()));
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @ScopeType
    @interface CustomSingleton {
    }

    @CustomSingleton
    static class CustomInstance {
    }

    @Test
    void registerCustomScope() {
        final Injector injector = DI.createInjector();
        injector.registerScope(CustomSingleton.class, new CustomSingletonScope());
        final var factory = injector.getFactory(CustomInstance.class);
        assertThat(factory.get()).isSameAs(factory.get()).isSameAs(injector.getInstance(CustomInstance.class));
    }

    static class ClassModule {
        @Factory
        static String string() {
            return "hello";
        }
    }

    @Test
    void staticProvidesMethodInClassModule() {
        final String string = DI.createInjector(ClassModule.class).getInstance(String.class);
        assertThat(string).isEqualTo("hello");
    }

    static class UppercaseModule {
        @Factory
        @Named(Keys.SUBSTITUTOR_NAME)
        Function<String, String> uppercase() {
            return s -> s != null ? s.toUpperCase(Locale.ROOT) : null;
        }
    }

    @Test
    void functionGenericsInjection() {
        final Injector injector = DI.createInjector(new UppercaseModule());
        final Function<String, String> function = injector.getInstance(Keys.SUBSTITUTOR_KEY);
        assertThat(function.apply("foo")).isEqualTo("FOO");
        assertThatThrownBy(() -> injector.getInstance(Function.class)).hasMessageContaining("No @Inject constructors");
    }

    @Plugin(category = "Test", name = "ConfigurableObject", elementType = "inner")
    static class ConfigurableObject {
        final String greeting;
        final Tertiary tertiary;

        ConfigurableObject(final String greeting, final Tertiary tertiary) {
            this.greeting = greeting;
            this.tertiary = tertiary;
        }

        @Factory
        static ConfigurableObject newInstance(@PluginAttribute final String greeting, final Tertiary tertiary) {
            return new ConfigurableObject(greeting, tertiary);
        }
    }

    @Plugin(category = "Test", name = "FactoryBuilt", elementType = "config")
    static class ConfigurableFactoryObject {
        final int id;
        final String name;
        final ConfigurableObject inner;

        ConfigurableFactoryObject(
                final int id, final String name, ConfigurableObject inner) {
            this.id = id;
            this.name = name;
            this.inner = inner;
        }

        @Factory
        static Builder newBuilder() {
            return new Builder();
        }

        static class Builder implements org.apache.logging.log4j.plugins.util.Builder<ConfigurableFactoryObject> {
            int id;
            String name;
            ConfigurableObject inner;

            @Inject
            public Builder setId(@PluginAttribute final int id) {
                this.id = id;
                return this;
            }

            @Inject
            public Builder setName(@PluginAttribute final String name) {
                this.name = name;
                return this;
            }

            @Inject
            public Builder setInner(@PluginElement final ConfigurableObject inner) {
                this.inner = inner;
                return this;
            }

            @Override
            public ConfigurableFactoryObject build() {
                return new ConfigurableFactoryObject(id, name, inner);
            }
        }
    }

    @Plugin(name = "HasDefaults", category = "Test", elementType = "defs")
    static class DefaultValueTest {
        final String name;
        final long millis;
        final boolean enabled;
        final String fieldBasedDefault;

        DefaultValueTest(final String name, final long millis, final boolean enabled, final String fieldBasedDefault) {
            this.name = name;
            this.millis = millis;
            this.enabled = enabled;
            this.fieldBasedDefault = fieldBasedDefault;
        }

        @Factory
        static Builder newBuilder() {
            return new Builder();
        }

        static class Builder implements Supplier<DefaultValueTest> {
            String name;
            @PluginAttribute(defaultLong = 1000L)
            long millis;
            @PluginAttribute(defaultBoolean = true)
            boolean enabled;
            @PluginBuilderAttribute
            String fieldBasedDefault = "rabbit";

            public Builder setName(@PluginAttribute(defaultString = "dog") final String name) {
                this.name = name;
                return this;
            }

            public Builder setMillis(final long millis) {
                this.millis = millis;
                return this;
            }

            public Builder setEnabled(final boolean enabled) {
                this.enabled = enabled;
                return this;
            }

            public Builder setFieldBasedDefault(final String fieldBasedDefault) {
                this.fieldBasedDefault = fieldBasedDefault;
                return this;
            }

            @Override
            public DefaultValueTest get() {
                return new DefaultValueTest(name, millis, enabled, fieldBasedDefault);
            }
        }
    }

    static class NoOpStringSubstitution {
        @Factory
        @Named(Keys.SUBSTITUTOR_NAME)
        static Function<String, String> substitutor() {
            return Function.identity();
        }
    }

    static <T> PluginType<T> fromClass(final Class<T> clazz) {
        final Plugin plugin = clazz.getAnnotation(Plugin.class);
        final PluginEntry entry = new PluginEntry(plugin.name().toLowerCase(Locale.ROOT), clazz.getName(), plugin.name(),
                plugin.printObject(), plugin.deferChildren(), plugin.category());
        return new PluginType<>(entry, clazz, plugin.elementType());
    }

    @Test
    void pluginNodeConfiguration() {
        final var factoryBuiltObject = fromClass(ConfigurableFactoryObject.class);
        final var rootNode = new Node(null, "config", factoryBuiltObject);
        rootNode.getAttributes().put("id", "42");
        rootNode.getAttributes().put("name", "adi");
        final var configurableObject = fromClass(ConfigurableObject.class);
        final Node config = new Node(rootNode, "inner", configurableObject);
        config.getAttributes().put("greeting", "hello");
        rootNode.getChildren().add(config);
        final ConfigurableFactoryObject object = DI.createInjector(NoOpStringSubstitution.class).configure(rootNode);
        assertThat(object).isNotNull();
        assertThat(object.id).isEqualTo(42);
        assertThat(object.name).isEqualTo("adi");
        assertThat(object.inner.greeting).isEqualTo("hello");
        assertThat(object.inner.tertiary).isNotNull();
        assertThat(object.inner.tertiary.secondary).isNotNull();
        assertThat(object.inner.tertiary.secondary.primary).isNotNull();
    }

    @Test
    void pluginDefaultAttributeValues() {
        final var type = fromClass(DefaultValueTest.class);
        final var node = new Node(null, "root", type);
        final DefaultValueTest test = DI.createInjector(NoOpStringSubstitution.class).configure(node);
        assertThat(test.name).isEqualTo("dog");
        assertThat(test.millis).isEqualTo(1000L);
        assertThat(test.enabled).isTrue();
        assertThat(test.fieldBasedDefault).isEqualTo("rabbit");
    }

    @RequiredProperty(name = "enableLoggins")
    @Plugin(name = "Dangerous", category = "Test", elementType = "DangerZone")
    static class DangerousPlugin {
    }

    @Test
    @ResourceLock(Resources.SYSTEM_PROPERTIES)
    void classConstraintValidation() {
        final var type = fromClass(DangerousPlugin.class);
        final var node = new Node(null, "zone", type);
        final Injector injector = DI.createInjector();
        final var instance = injector.configure(node);
        assertThat(instance).isNull();
        System.setProperty("enableLoggins", "true");
        assertDoesNotThrow(() -> injector.configure(node));
        System.clearProperty("enableLoggins");
    }

    @Test
    void validatingPluginWithGenericBuilder() {
        final var type = fromClass(ValidatingPluginWithGenericBuilder.class);
        final var node = new Node(null, "test", type);
        final Injector injector = DI.createInjector(NoOpStringSubstitution.class);
        final Object instance = injector.configure(node);
        assertThat(instance).isNull();
        node.getAttributes().put("name", "valid");
        assertDoesNotThrow(() -> injector.configure(node));
    }

    @Plugin(name = "ValidatedParameters", category = "Test")
    static class ValidatedParameters {
        final String name;
        final String value;

        @Inject
        ValidatedParameters(@Required @PluginAttribute final String name, @PluginValue final String value) {
            this.name = name;
            this.value = value;
        }
    }

    @Test
    void parameterValidation() {
        final var type = fromClass(ValidatedParameters.class);
        final var node = new Node(null, "config", type);
        final Injector injector = DI.createInjector(NoOpStringSubstitution.class);
        final Object instance = injector.configure(node);
        assertThat(instance).isNull();
        node.getAttributes().put("name", "hank");
        node.setValue("propane");
        final ValidatedParameters parameters = injector.configure(node);
        assertThat(parameters.name).isEqualTo("hank");
        assertThat(parameters.value).isEqualTo("propane");
    }

    static class ValidatedInjectionPoints {
        @Required
        @Named
        String foo;

        final String bar;

        @Inject
        ValidatedInjectionPoints(@Required @Named final String bar) {
            this.bar = bar;
        }
    }

    @Test
    void injectionPointValidation() {
        final var injector = DI.createInjector();
        assertThatThrownBy(() -> injector.getInstance(ValidatedInjectionPoints.class))
                .hasMessageStartingWith("Validation failed");
        injector.registerBinding(new @Named("foo") Key<>() {}, () -> "hello");
        assertThatThrownBy(() -> injector.getInstance(ValidatedInjectionPoints.class))
                .hasMessageStartingWith("Validation failed");
        injector.registerBinding(new @Named("bar") Key<>() {}, () -> "world");
        final ValidatedInjectionPoints instance = injector.getInstance(ValidatedInjectionPoints.class);
        assertThat(instance.foo).isEqualTo("hello");
        assertThat(instance.bar).isEqualTo("world");
    }

    enum Level {
        ERROR, WARN
    }

    @Plugin(name = "LevelInjection", category = "Test")
    static class LevelInject {
        final Level first;
        final Level second;

        @Inject
        LevelInject(@PluginAttribute final Level first, @PluginAttribute(defaultString = "ERROR") final Level second) {
            this.first = first;
            this.second = second;
        }
    }

    @Test
    void enumInjection() {
        final var type = fromClass(LevelInject.class);
        final var node = new Node(null, "levels", type);
        final LevelInject levelInject = DI.createInjector(NoOpStringSubstitution.class).configure(node);
        assertThat(levelInject.first).isNull();
        assertThat(levelInject.second).isEqualTo(Level.ERROR);
    }

    @Plugin(name = "MultipleElements", category = "Test")
    static class MultipleElements {
        final ConfigurableObject[] objects;

        @Inject
        MultipleElements(@PluginElement final ConfigurableObject... objects) {
            this.objects = objects;
        }
    }

    @Test
    void multipleElementInjection() {
        final var innerType = fromClass(ConfigurableObject.class);
        final var outerType = fromClass(MultipleElements.class);
        final var root = new Node(null, "root", outerType);
        final var child1 = new Node(root, "first", innerType);
        child1.getAttributes().put("greeting", "g'day");
        root.getChildren().add(child1);
        final var child2 = new Node(root, "second", innerType);
        child2.getAttributes().put("greeting", "alright");
        root.getChildren().add(child2);
        final MultipleElements instance = DI.createInjector(NoOpStringSubstitution.class).configure(root);
        assertThat(instance.objects).hasSize(2);
    }
}
