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
package org.apache.logging.log4j.core.time;

import org.apache.logging.log4j.core.time.internal.CachedClock;
import org.apache.logging.log4j.core.time.internal.CoarseCachedClock;
import org.apache.logging.log4j.core.time.internal.SystemClock;
import org.apache.logging.log4j.plugins.di.DI;
import org.apache.logging.log4j.plugins.di.Injector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClockFactoryTest {

    private final Injector injector = DI.createInjector();

    @BeforeEach
    public void setUp() throws Exception {
        System.clearProperty(ClockFactory.PROPERTY_NAME);
    }

    @Test
    public void testDefaultIsSystemClock() {
        System.clearProperty(ClockFactory.PROPERTY_NAME);
        injector.init();
        assertThat(injector.getInstance(Clock.class)).isInstanceOf(SystemClock.class);
    }

    @Test
    public void testSpecifySystemClockShort() {
        System.setProperty(ClockFactory.PROPERTY_NAME, "SystemClock");
        injector.init();
        assertThat(injector.getInstance(Clock.class)).isInstanceOf(SystemClock.class);
    }

    @Test
    public void testSpecifySystemClockLong() {
        System.setProperty(ClockFactory.PROPERTY_NAME, SystemClock.class.getName());
        injector.init();
        assertThat(injector.getInstance(Clock.class)).isInstanceOf(SystemClock.class);
    }

    @Test
    public void testSpecifyCachedClockShort() {
        System.setProperty(ClockFactory.PROPERTY_NAME, "CachedClock");
        injector.init();
        assertThat(injector.getInstance(Clock.class)).isInstanceOf(CachedClock.class);
    }

    @Test
    public void testSpecifyCachedClockLong() {
        System.setProperty(ClockFactory.PROPERTY_NAME, CachedClock.class.getName());
        injector.init();
        assertThat(injector.getInstance(Clock.class)).isInstanceOf(CachedClock.class);
    }

    @Test
    public void testSpecifyCoarseCachedClockShort() {
        System.setProperty(ClockFactory.PROPERTY_NAME, "CoarseCachedClock");
        injector.init();
        assertThat(injector.getInstance(Clock.class)).isInstanceOf(CoarseCachedClock.class);
    }

    @Test
    public void testSpecifyCoarseCachedClockLong() {
        System.setProperty(ClockFactory.PROPERTY_NAME, CoarseCachedClock.class.getName());
        injector.init();
        assertThat(injector.getInstance(Clock.class)).isInstanceOf(CoarseCachedClock.class);
    }

    public static class MyClock implements Clock {
        @Override
        public long currentTimeMillis() {
            return 42;
        }
    }

    @Test
    public void testCustomClock() {
        System.setProperty(ClockFactory.PROPERTY_NAME, MyClock.class.getName());
        injector.init();
        assertThat(injector.getInstance(Clock.class)).isInstanceOf(MyClock.class);
    }

}
