/*
 * Copyright (C) 2011 The Guava Authors
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

package com.google.common.cache;

import com.google.common.annotations.GwtCompatible;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ExecutionError;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.errorprone.annotations.CompatibleWith;
import com.google.errorprone.annotations.DoNotMock;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A semi-persistent mapping from keys to values. Cache entries are manually added using {@link
 * #get(Object, Callable)} or {@link #put(Object, Object)}, and are stored in the cache until either
 * evicted or manually invalidated. The common way to build instances is using {@link CacheBuilder}.
 *
 * <p>Implementations of this interface are expected to be thread-safe, and can be safely accessed
 * by multiple concurrent threads.
 *
 * @author Charles Fry
 * @since 10.0
 */
@DoNotMock("Use CacheBuilder.newBuilder().build()")
@GwtCompatible
public interface Cache<K, V> {

  /**
   * Returns the value associated with {@code key} in this cache, or {@code null} if there is no
   * cached value for {@code key}.
   *
   * 返回与此缓存中的 {@code key} 关联的值，如果 {@code key} 没有缓存值，则返回 {@code null}。
   *
   * @since 11.0
   */
  @Nullable
  V getIfPresent(@CompatibleWith("K") Object key);

  /**
   * Returns the value associated with {@code key} in this cache, obtaining that value from {@code
   * loader} if necessary. The method improves upon the conventional "if cached, return; otherwise
   * create, cache and return" pattern. For further improvements, use {@link LoadingCache} and its
   * {@link LoadingCache#get(Object) get(K)} method instead of this one.
   *
   *
   * 返回与此缓存中的 {@code key} 关联的值，如有必要，从 {@code loader} 获取该值。
   * 该方法改进了传统的“如果缓存，则返回；否则创建，缓存并返回”模式。
   * 为了进一步改进，请使用 {@link LoadingCache} 及其 {@link LoadingCache#get(Object) get(K)} 方法而不是这个方法。
   *
   * <p>Among the improvements that this method and {@code LoadingCache.get(K)} both provide are:
   *
   * <ul>
   *   <li>{@linkplain LoadingCache#get(Object) awaiting the result of a pending load} rather than
   *       starting a redundant one
   *   <li>eliminating the error-prone caching boilerplate
   *   <li>tracking load {@linkplain #stats statistics}
   * </ul>
   *
   * <p>Among the further improvements that {@code LoadingCache} can provide but this method cannot:
   *
   * <ul>
   *   <li>consolidation of the loader logic to {@linkplain CacheBuilder#build(CacheLoader) a single
   *       authoritative location}
   *   <li>{@linkplain LoadingCache#refresh refreshing of entries}, including {@linkplain
   *       CacheBuilder#refreshAfterWrite automated refreshing}
   *   <li>{@linkplain LoadingCache#getAll bulk loading requests}, including {@linkplain
   *       CacheLoader#loadAll bulk loading implementations}
   * </ul>
   *
   * <p><b>Warning:</b> For any given key, every {@code loader} used with it should compute the same
   * value. Otherwise, a call that passes one {@code loader} may return the result of another call
   * with a differently behaving {@code loader}. For example, a call that requests a short timeout
   * for an RPC may wait for a similar call that requests a long timeout, or a call by an
   * unprivileged user may return a resource accessible only to a privileged user making a similar
   * call. To prevent this problem, create a key object that includes all values that affect the
   * result of the query. Or use {@code LoadingCache.get(K)}, which lacks the ability to refer to
   * state other than that in the key.
   *
   * <p><b>Warning:</b> as with {@link CacheLoader#load}, {@code loader} <b>must not</b> return
   * {@code null}; it may either return a non-null value or throw an exception.
   *
   * 与 CacheLoader.load(K) 一样，valueLoader 不能返回 null；它可能返回非空值或抛出异常。
   *
   * <p>No observable state associated with this cache is modified until loading completes.
   *
   * @throws ExecutionException if a checked exception was thrown while loading the value
   * @throws UncheckedExecutionException if an unchecked exception was thrown while loading the
   *     value
   * @throws ExecutionError if an error was thrown while loading the value
   * @since 11.0
   */
  V get(K key, Callable<? extends V> loader) throws ExecutionException;

  /**
   * Returns a map of the values associated with {@code keys} in this cache. The returned map will
   * only contain entries which are already present in the cache.
   *
   * 返回与此缓存中的 {@code keys} 关联的值的映射。 返回的映射将只包含缓存中已经存在的条目。
   *
   * @since 11.0
   */
  ImmutableMap<K, V> getAllPresent(Iterable<?> keys);

  /**
   * Associates {@code value} with {@code key} in this cache. If the cache previously contained a
   * value associated with {@code key}, the old value is replaced by {@code value}.
   *
   * 将 {@code value} 与此缓存中的 {@code key} 相关联。 如果缓存先前包含与 {@code key} 关联的值，则旧值将替换为 {@code value}。
   *
   * <p>Prefer {@link #get(Object, Callable)} when using the conventional "if cached, return;
   * otherwise create, cache and return" pattern.
   *
   * 使用传统的“如果缓存，返回；否则创建，缓存并返回”模式时，更喜欢 {@link #get(Object, Callable)}。
   *
   * @since 11.0
   */
  void put(K key, V value);

  /**
   * Copies all of the mappings from the specified map to the cache. The effect of this call is
   * equivalent to that of calling {@code put(k, v)} on this map once for each mapping from key
   * {@code k} to value {@code v} in the specified map. The behavior of this operation is undefined
   * if the specified map is modified while the operation is in progress.
   *
   * 将所有映射从指定映射复制到缓存。 此调用的效果等同于对指定映射中从键 {@code k} 到值 {@code v} 的每个映射在此映射上调用一次 {@code put(k, v)}。
   * 如果在操作进行时修改了指定的映射，则此操作的行为未定义。
   *
   * @since 12.0
   */
  void putAll(Map<? extends K, ? extends V> m);

  /**
   * Discards any cached value for key {@code key}.
   *
   * 丢弃键 {@code key} 的任何缓存值。
   */
  void invalidate(@CompatibleWith("K") Object key);

  /**
   * Discards any cached values for keys {@code keys}.
   *
   * 丢弃键 {@code keys} 的任何缓存值。
   *
   * @since 11.0
   */
  void invalidateAll(Iterable<?> keys);

  /**
   * Discards all entries in the cache.
   *
   * 丢弃缓存中的所有条目
   */
  void invalidateAll();

  /**
   * Returns the approximate number of entries in this cache.
   *
   * 返回此缓存中条目的大致数量。
   */
  long size();

  /**
   * Returns a current snapshot of this cache's cumulative statistics, or a set of default values if
   * the cache is not recording statistics. All statistics begin at zero and never decrease over the
   * lifetime of the cache.
   *
   * 返回此缓存累积统计信息的当前快照，如果缓存未记录统计信息，则返回一组默认值。
   * 所有统计信息都从零开始，并且在缓存的生命周期内永远不会减少。
   *
   * <p><b>Warning:</b> this cache may not be recording statistical data. For example, a cache
   * created using {@link CacheBuilder} only does so if the {@link CacheBuilder#recordStats} method
   * was called. If statistics are not being recorded, a {@code CacheStats} instance with zero for
   * all values is returned.
   *
   */
  CacheStats stats();

  /**
   * Returns a view of the entries stored in this cache as a thread-safe map. Modifications made to
   * the map directly affect the cache.
   *
   * 返回存储在此缓存中的条目的视图作为线程安全映射。
   * 对 Map 所做的修改直接影响缓存。
   *
   * <p>Iterators from the returned map are at least <i>weakly consistent</i>: they are safe for
   * concurrent use, but if the cache is modified (including by eviction) after the iterator is
   * created, it is undefined which of the changes (if any) will be reflected in that iterator.
   *
   *
   * 来自返回的 map 的迭代器至少是弱一致的：它们对于并发使用是安全的，但是如果在创建迭代器后缓存被修改（包括通过驱逐），则不确定哪些更改（如果有）将反映在 那个迭代器。
   */
  ConcurrentMap<K, V> asMap();

  /**
   * Performs any pending maintenance operations needed by the cache. Exactly which activities are
   * performed -- if any -- is implementation-dependent.
   *
   * 执行缓存所需的任何挂起的维护操作。
   * 究竟执行哪些活动——如果有的话——取决于实现。
   */
  void cleanUp();
}
