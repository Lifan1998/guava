/*
 * Copyright (C) 2014 The Guava Authors
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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.j2objc.annotations.Weak;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A subscriber method on a specific object, plus the executor that should be used for dispatching
 * events to it.
 *
 * 特定对象上的订阅者方法，以及应该用于向其分派事件的执行程序。
 *
 * <p>Two subscribers are equivalent when they refer to the same method on the same object (not
 * class). This property is used to ensure that no subscriber method is registered more than once.
 *
 * @author Colin Decker
 */
class Subscriber {

  /** Creates a {@code Subscriber} for {@code method} on {@code listener}. */

  /**
   *  在 listener 上为 method 创建一个 Subscriber。
   *  并这在判断是否添加了 {@link AllowConcurrentEvents} 注解，从而创建不同的订阅者
   */
  static Subscriber create(EventBus bus, Object listener, Method method) {
    return isDeclaredThreadSafe(method)
        ? new Subscriber(bus, listener, method)
        : new SynchronizedSubscriber(bus, listener, method);
  }

  /** The event bus this subscriber belongs to. */

  /** 此订阅者所属的事件总线。 */
  @Weak private EventBus bus;

  /** The object with the subscriber method. */

  /** 带有订阅者方法的对象。 */
  @VisibleForTesting final Object target;

  /** Subscriber method. */

  /** 订阅者方法。 */
  private final Method method;

  /** Executor to use for dispatching events to this subscriber. */

  /** 用于将事件分派给该订阅者的执行器。 */
  private final Executor executor;

  private Subscriber(EventBus bus, Object target, Method method) {
    this.bus = bus;
    this.target = checkNotNull(target);
    this.method = method;
    method.setAccessible(true);

    this.executor = bus.executor();
  }

  /** Dispatches {@code event} to this subscriber using the proper executor. */

  /** 使用 Bus 指定的执行器将 event 分派给该订阅者。 */
  final void dispatchEvent(final Object event) {
    executor.execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              invokeSubscriberMethod(event);
            } catch (InvocationTargetException e) {
              // 使用 SubscriberExceptionHandler 来处理订阅者方法调用异常
              bus.handleSubscriberException(e.getCause(), context(event));
            }
          }
        });
  }

  /**
   * Invokes the subscriber method. This method can be overridden to make the invocation
   * synchronized.
   *
   * 调用订阅者方法。可以重写此方法同步调用
   */
  @VisibleForTesting
  void invokeSubscriberMethod(Object event) throws InvocationTargetException {
    try {
      method.invoke(target, checkNotNull(event));
    } catch (IllegalArgumentException e) {
      throw new Error("Method rejected target/argument: " + event, e);
    } catch (IllegalAccessException e) {
      throw new Error("Method became inaccessible: " + event, e);
    } catch (InvocationTargetException e) {
      if (e.getCause() instanceof Error) {
        throw (Error) e.getCause();
      }
      throw e;
    }
  }

  /** Gets the context for the given event. */

  /** 获取给定事件的上下文。 */
  private SubscriberExceptionContext context(Object event) {
    return new SubscriberExceptionContext(bus, event, target, method);
  }

  @Override
  public final int hashCode() {
    return (31 + method.hashCode()) * 31 + System.identityHashCode(target);
  }

  @Override
  public final boolean equals(@Nullable Object obj) {
    if (obj instanceof Subscriber) {
      Subscriber that = (Subscriber) obj;
      // Use == so that different equal instances will still receive events.
      // We only guard against the case that the same object is registered
      // multiple times
      return target == that.target && method.equals(that.method);
    }
    return false;
  }

  /**
   * Checks whether {@code method} is thread-safe, as indicated by the presence of the {@link
   * AllowConcurrentEvents} annotation.
   *
   * 检查 method 是否是线程安全的，如  AllowConcurrentEvents 注释的存在所示。
   *
   * 如果方法上添加了 AllowConcurrentEvents 注解，会
   */
  private static boolean isDeclaredThreadSafe(Method method) {
    return method.getAnnotation(AllowConcurrentEvents.class) != null;
  }

  /**
   * Subscriber that synchronizes invocations of a method to ensure that only one thread may enter
   * the method at a time.
   *
   * 同步方法调用以确保一次只能有一个线程进入该方法的订阅者。
   *
   * 注意这里不是说该事件的订阅者集合执行是串行的，而是包装这个方法的订阅者执行是串行的
   * 比如事件A有两个订阅者，一个是 SynchronizedSubscriber (MethodA) 一个不是 (MethodB)
   * 那么如果同时发布了多个事件A（比如多线程环境下），MethodA 和 MethodB 都会被执行，不同的是 MethodA 是串行的，因为事件即使有多个，但 MethodA 订阅者只有一个
   */
  @VisibleForTesting
  static final class SynchronizedSubscriber extends Subscriber {

    private SynchronizedSubscriber(EventBus bus, Object target, Method method) {
      super(bus, target, method);
    }

    @Override
    void invokeSubscriberMethod(Object event) throws InvocationTargetException {
      synchronized (this) {
        super.invokeSubscriberMethod(event);
      }
    }
  }
}
