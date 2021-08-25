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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Throwables.throwIfUnchecked;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Throwables;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.primitives.Primitives;
import com.google.common.reflect.TypeToken;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.j2objc.annotations.Weak;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Registry of subscribers to a single event bus.
 *
 * 单个事件总线的订阅者注册表。
 *
 * @author Colin Decker
 */
final class SubscriberRegistry {

  /**
   * All registered subscribers, indexed by event type.
   *
   * 存储所有注册订阅者，按事件类型索引。
   *
   * <p>The {@link CopyOnWriteArraySet} values make it easy and relatively lightweight to get an
   * immutable snapshot of all current subscribers to an event without any locking.
   *
   * ConcurrentMap<注册事件Class, 订阅该事件的订阅者集合>
   *
   * 例：新用户注册后自动创建账户 ConcurrentMap<UserRegisterEvent, Set(AccountService.createAccount())>
   */
  private final ConcurrentMap<Class<?>, CopyOnWriteArraySet<Subscriber>> subscribers =
      Maps.newConcurrentMap();

  /** The event bus this registry belongs to. */
  /** 该注册表所属的事件总线。 */
  @Weak private final EventBus bus;

  SubscriberRegistry(EventBus bus) {
    this.bus = checkNotNull(bus);
  }

  /**
   * Registers all subscriber methods on the given listener object.
   * 注册给定的监听器对象上的所有订阅者方法。（该对象所有声明 @Subscribe 注解的方法）
   */
  void register(Object listener) {
    // 获取给定监听器的所有订阅者，并按事件分类
    // Multimap<事件, 订阅该事件的订阅者集合>
    Multimap<Class<?>, Subscriber> listenerMethods = findAllSubscribers(listener);

    for (Entry<Class<?>, Collection<Subscriber>> entry : listenerMethods.asMap().entrySet()) {
      // 获取事件 Class 对象
      Class<?> eventType = entry.getKey();
      // 获取订阅该事件的订阅者集合
      Collection<Subscriber> eventMethodsInListener = entry.getValue();

      // 获取该事件已在注册表中的订阅者集合
      CopyOnWriteArraySet<Subscriber> eventSubscribers = subscribers.get(eventType);

      // 如果注册表中没有该事件及其订阅者，就创建一个空的订阅者集合
      if (eventSubscribers == null) {
        CopyOnWriteArraySet<Subscriber> newSet = new CopyOnWriteArraySet<>();
        // MoreObjects.firstNonNull: 返回两个给定参数中不是 null 的第一个参数，如果都是 null，抛出异常
        // subscribers.putIfAbsent：将 event 和 空集合 放入map，这里起到关联两者的作用，还可以防止并发时该事件其他订阅者进来
        eventSubscribers =
            MoreObjects.firstNonNull(subscribers.putIfAbsent(eventType, newSet), newSet);
      }
      // 将新的订阅者集合追加到注册表中（eventSubscribers 是一个Set，会去重的）
      eventSubscribers.addAll(eventMethodsInListener);
    }
  }

  /**
   * Unregisters all subscribers on the given listener object.
   *
   * 取消注册给定监听器对象上的所有订阅者。
   */
  void unregister(Object listener) {
    // 获取给定监听器的所有订阅者，并按事件分类
    // Multimap<事件, 订阅该事件的订阅者集合>
    Multimap<Class<?>, Subscriber> listenerMethods = findAllSubscribers(listener);

    for (Entry<Class<?>, Collection<Subscriber>> entry : listenerMethods.asMap().entrySet()) {
      Class<?> eventType = entry.getKey();
      Collection<Subscriber> listenerMethodsForType = entry.getValue();

      CopyOnWriteArraySet<Subscriber> currentSubscribers = subscribers.get(eventType);
      // 如果订阅者注册表查询不到该事件，就抛异常
      // 如果订阅者注册表该事件的订阅者不为空，就尝试删除该监听器中的该事件的所有订阅者
      // 如果删除 removeAll 失败，返回 false, 也抛异常。
      // 什么时候回返回false呢？一个都没有删除成功，即两者没有交集元素，哪怕只删除一个，都会在删除后返回true
      if (currentSubscribers == null || !currentSubscribers.removeAll(listenerMethodsForType)) {
        // if removeAll returns true, all we really know is that at least one subscriber was
        // removed... however, barring something very strange we can assume that if at least one
        // subscriber was removed, all subscribers on listener for that event type were... after
        // all, the definition of subscribers on a particular class is totally static

        // 如果 removeAll 返回 true，我们真正知道的是至少有一个订阅者被删除了……
        // 但是，除非发生一些非常奇怪的事情，我们可以假设如果至少删除了一个订阅者，则该事件类型的侦听器上的所有订阅者都是……
        // 毕竟，特定类上订阅者的定义是完全静态的
        throw new IllegalArgumentException(
            "missing event subscriber for an annotated method. Is " + listener + " registered?");
      }

      // don't try to remove the set if it's empty; that can't be done safely without a lock
      // 如果集合为空，请不要尝试删除它； 没有锁就无法安全完成
      // anyway, if the set is empty it'll just be wrapping an array of length 0
      // 无论如何，如果集合为空，它只会包装一个长度为 0 的数组
    }
  }

  @VisibleForTesting
  Set<Subscriber> getSubscribersForTesting(Class<?> eventType) {
    return MoreObjects.firstNonNull(subscribers.get(eventType), ImmutableSet.<Subscriber>of());
  }

  /**
   * Gets an iterator representing an immutable snapshot of all subscribers to the given event at
   * the time this method is called.
   *
   * 获取一个迭代器，调用此方法时给定事件的所有订阅者的不可变快照。
   */
  Iterator<Subscriber> getSubscribers(Object event) {

    // 获取指定事件的所有超类（可传递的）和由这些超类实现的所有接口
    ImmutableSet<Class<?>> eventTypes = flattenHierarchy(event.getClass());

    List<Iterator<Subscriber>> subscriberIterators =
        Lists.newArrayListWithCapacity(eventTypes.size());

    for (Class<?> eventType : eventTypes) {
      CopyOnWriteArraySet<Subscriber> eventSubscribers = subscribers.get(eventType);
      if (eventSubscribers != null) {
        // eager no-copy snapshot
        subscriberIterators.add(eventSubscribers.iterator());
      }
    }
    // flat
    return Iterators.concat(subscriberIterators.iterator());
  }

  /**
   * A thread-safe cache that contains the mapping from each class to all methods in that class and
   * all super-classes, that are annotated with {@code @Subscribe}. The cache is shared across all
   * instances of this class; this greatly improves performance if multiple EventBus instances are
   * created and objects of the same class are registered on all of them.
   */
  private static final LoadingCache<Class<?>, ImmutableList<Method>> subscriberMethodsCache =
      CacheBuilder.newBuilder()
          .weakKeys()
          .build(
              new CacheLoader<Class<?>, ImmutableList<Method>>() {
                @Override
                public ImmutableList<Method> load(Class<?> concreteClass) throws Exception {
                  return getAnnotatedMethodsNotCached(concreteClass);
                }
              });

  /**
   * Returns all subscribers for the given listener grouped by the type of event they subscribe to.
   *
   * 返回给定监听器的所有订阅者，按订阅者订阅的事件类型分组。
   */
  private Multimap<Class<?>, Subscriber> findAllSubscribers(Object listener) {
    // 创建一个HashMultimap， 等价于 HashMap<Class<?>, HashSet<Subscriber>>
    Multimap<Class<?>, Subscriber> methodsInListener = HashMultimap.create();
    // 获取 Class 对象
    Class<?> clazz = listener.getClass();
    // 获取监听器所有带 @Subscribe 注解的方法
    for (Method method : getAnnotatedMethods(clazz)) {
      // 获取方法参数列表
      Class<?>[] parameterTypes = method.getParameterTypes();
      // 取第一个参数为事件
      Class<?> eventType = parameterTypes[0];
      // 创建订阅者对象并放进 Map 中
      methodsInListener.put(eventType, Subscriber.create(bus, listener, method));
    }
    return methodsInListener;
  }

    /**
     * 获取监听器所有带 @Subscribe 注解的方法
     * @param clazz 监听器对象
     * @return
     */
  private static ImmutableList<Method> getAnnotatedMethods(Class<?> clazz) {
    try {
      return subscriberMethodsCache.getUnchecked(clazz);
    } catch (UncheckedExecutionException e) {
      throwIfUnchecked(e.getCause());
      throw e;
    }
  }

  private static ImmutableList<Method> getAnnotatedMethodsNotCached(Class<?> clazz) {
    Set<? extends Class<?>> supertypes = TypeToken.of(clazz).getTypes().rawTypes();
    Map<MethodIdentifier, Method> identifiers = Maps.newHashMap();
    for (Class<?> supertype : supertypes) {
      for (Method method : supertype.getDeclaredMethods()) {
        if (method.isAnnotationPresent(Subscribe.class) && !method.isSynthetic()) {
          // TODO(cgdecker): Should check for a generic parameter type and error out
          Class<?>[] parameterTypes = method.getParameterTypes();
          checkArgument(
              parameterTypes.length == 1,
              "Method %s has @Subscribe annotation but has %s parameters. "
                  + "Subscriber methods must have exactly 1 parameter.",
              method,
              parameterTypes.length);

          checkArgument(
              !parameterTypes[0].isPrimitive(),
              "@Subscribe method %s's parameter is %s. "
                  + "Subscriber methods cannot accept primitives. "
                  + "Consider changing the parameter to %s.",
              method,
              parameterTypes[0].getName(),
              Primitives.wrap(parameterTypes[0]).getSimpleName());

          MethodIdentifier ident = new MethodIdentifier(method);
          if (!identifiers.containsKey(ident)) {
            identifiers.put(ident, method);
          }
        }
      }
    }
    return ImmutableList.copyOf(identifiers.values());
  }

  /**
   * Global cache of classes to their flattened hierarchy of supertypes.
   * 缓存类以及它的类型层次结构
   */
  private static final LoadingCache<Class<?>, ImmutableSet<Class<?>>> flattenHierarchyCache =
      CacheBuilder.newBuilder()
          .weakKeys()
          .build(
              new CacheLoader<Class<?>, ImmutableSet<Class<?>>>() {
                // <Class<?>> is actually needed to compile

                /**
                 *
                 * @param concreteClass concreteClass
                 * @return
                 */
                @SuppressWarnings("RedundantTypeArguments")
                @Override
                public ImmutableSet<Class<?>> load(Class<?> concreteClass) {
                  return ImmutableSet.<Class<?>>copyOf(
                      TypeToken.of(concreteClass).getTypes().rawTypes());
                }
              });

  /**
   * Flattens a class's type hierarchy into a set of {@code Class} objects including all
   * superclasses (transitively) and all interfaces implemented by these superclasses.
   *
   * 将类的类型层次结构扁平化为一组 {@code Class} 对象，包括所有超类（可传递的）和由这些超类实现的所有接口。
   */
  @VisibleForTesting
  static ImmutableSet<Class<?>> flattenHierarchy(Class<?> concreteClass) {
    try {
      return flattenHierarchyCache.getUnchecked(concreteClass);
    } catch (UncheckedExecutionException e) {
      throw Throwables.propagate(e.getCause());
    }
  }

  /**
   * 方法标识符
   */
  private static final class MethodIdentifier {

    private final String name;
    private final List<Class<?>> parameterTypes;

    MethodIdentifier(Method method) {
      this.name = method.getName();
      this.parameterTypes = Arrays.asList(method.getParameterTypes());
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(name, parameterTypes);
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (o instanceof MethodIdentifier) {
        MethodIdentifier ident = (MethodIdentifier) o;
        return name.equals(ident.name) && parameterTypes.equals(ident.parameterTypes);
      }
      return false;
    }
  }
}
