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

/**
 * Marks a method as an event subscriber.
 *
 * 将方法标记为事件订阅者。
 *
 * <p>The type of event will be indicated by the method's first (and only) parameter, which cannot
 * be primitive. If this annotation is applied to methods with zero parameters, or more than one
 * parameter, the object containing the method will not be able to register for event delivery from
 * the {@link EventBus}.
 *
 * 事件的类型将由方法的第一个（也是唯一的）参数指示，该参数不能是原始参数。
 * 如果将此注释应用于具有零个参数或多个参数的方法，则包含该方法的对象将无法从 {@link EventBus} 注册事件传递。
 *
 * <p>Unless also annotated with @{@link AllowConcurrentEvents}, event subscriber methods will be
 * invoked serially by each event bus that they are registered with.
 *
 * 用 @{@link AllowConcurrentEvents} 注释，事件订阅者方法将被它们注册的每个事件总线串行调用。
 *
 * @author Cliff Biffle
 * @since 10.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Beta
public @interface Subscribe {}
