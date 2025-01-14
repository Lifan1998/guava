/*
 * Copyright (C) 2007 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.common.eventbus;

import com.google.common.annotations.Beta;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;

/**
 * Marks an event subscriber method as being thread-safe. This annotation indicates that EventBus
 * may invoke the event subscriber simultaneously from multiple threads.
 *
 * 将事件订阅者方法标记为线程安全的。 这个注解表明 EventBus 可以从多个线程同时调用事件订阅者。
 *
 * <p>This does not mark the method, and so should be used in combination with {@link Subscribe}.
 *
 * 他不单独标记方法，应与 {@link Subscribe} 一起使用
 *
 * 他的实现在这 {@link Subscriber#create(EventBus, Object, Method)}
 *
 * @author Cliff Biffle
 * @since 10.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Beta
public @interface AllowConcurrentEvents {}
