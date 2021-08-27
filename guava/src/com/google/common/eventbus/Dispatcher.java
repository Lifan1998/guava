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

import com.google.common.collect.Queues;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Handler for dispatching events to subscribers, providing different event ordering guarantees that
 * make sense for different situations.
 *
 * 用于将事件分派给订阅者的处理程序，提供对不同情况有意义的不同事件排序保证。
 *
 *
 * <p><b>Note:</b> The dispatcher is orthogonal to the subscriber's {@code Executor}. The dispatcher
 * controls the order in which events are dispatched, while the executor controls how (i.e. on which
 * thread) the subscriber is actually called when an event is dispatched to it.
 *
 * 调度程序控制调度事件的顺序，而执行程序控制在将事件调度给订阅者时实际调用订阅者的方式（即在哪个线程上）。
 *
 * 一个 EventBus 只有一个 调度器 和 一个订阅者注册表，订阅者注册表记录事件和订阅者的关系
 * 而调度器才是负责将事件分配给对应订阅者的执行者，并处理所有的事件分发
 * 不管在单线程（订阅者发布事件）还是多线程情况下，都会出现多次触发调度器调度方法，由此产生了不同的调度器
 *
 * 1. perThreadDispatchQueue：单个线程内事件分发顺序是有序的
 * 2. LegacyAsyncDispatcher：全局事件队列，没什么用，看下面的注释
 * 3. ImmediateDispatcher：即时调度器，来一个分发一个
 *
 * @author Colin Decker
 */
abstract class Dispatcher {

  /**
   * Returns a dispatcher that queues events that are posted reentrantly on a thread that is already
   * dispatching an event, guaranteeing that all events posted on a single thread are dispatched to
   * all subscribers in the order they are posted.
   *
   * 返回一个调度程序，该调度程序将可重入发布的事件排在已经在调度事件的线程上，保证发布在单个线程上的所有事件都按照发布的顺序调度给所有订阅者。
   *
   * <p>When all subscribers are dispatched to using a <i>direct</i> executor (which dispatches on
   * the same thread that posts the event), this yields a breadth-first dispatch order on each
   * thread. That is, all subscribers to a single event A will be called before any subscribers to
   * any events B and C that are posted to the event bus by the subscribers to A.
   *
   * 当所有订阅者都被调度为使用直接执行器（在发布事件的同一线程上调度）时，这会在每个线程上产生广度优先调度顺序。
   * 也就是说，单个事件 A 的所有订阅者将在任何事件 B 和 C 的订阅者之前被调用，这些事件 B 和 C 由订阅者 A 发布到事件总线。
   */
  static Dispatcher perThreadDispatchQueue() {
    return new PerThreadQueuedDispatcher();
  }

  /**
   * Returns a dispatcher that queues events that are posted in a single global queue. This behavior
   * matches the original behavior of AsyncEventBus exactly, but is otherwise not especially useful.
   * For async dispatch, an {@linkplain #immediate() immediate} dispatcher should generally be
   * preferable.
   *
   * 返回一个调度程序，它对在单个全局队列中发布的事件进行排队。
   * 此行为与 AsyncEventBus 的原始行为完全匹配，但在其他方面并不是特别有用。
   * 对于异步调度，通常最好使用 {@linkplain #immediate() immediate} 即时调度器。
   */
  static Dispatcher legacyAsync() {
    return new LegacyAsyncDispatcher();
  }

  /**
   * Returns a dispatcher that dispatches events to subscribers immediately as they're posted
   * without using an intermediate queue to change the dispatch order. This is effectively a
   * depth-first dispatch order, vs. breadth-first when using a queue.
   *
   * 返回一个调度程序，该调度程序在事件发布时立即将事件调度给订阅者，而无需使用中间队列来更改调度顺序。
   * 这实际上是深度优先的调度顺序，而不是使用队列时的广度优先。
   */
  static Dispatcher immediate() {
    return ImmediateDispatcher.INSTANCE;
  }


  /**
   *
   * Dispatches the given {@code event} to the given {@code subscribers}.
   *
   * 将给定的 {@code 事件} 分派给给定的 {@code 订阅者}。
   *
   * @param event
   * @param subscribers
   */
  abstract void dispatch(Object event, Iterator<Subscriber> subscribers);

  /** Implementation of a {@link #perThreadDispatchQueue()} dispatcher. */
  private static final class PerThreadQueuedDispatcher extends Dispatcher {

    // This dispatcher matches the original dispatch behavior of EventBus.
    // 此调度器匹配 EventBus 的原始调度行为。
    // EventBus 默认调度器

    /** Per-thread queue of events to dispatch. */
    /**
     * 线程事件队列
     */
    private final ThreadLocal<Queue<Event>> queue =
        new ThreadLocal<Queue<Event>>() {
          @Override
          protected Queue<Event> initialValue() {
            return Queues.newArrayDeque();
          }
        };

    /**
     * Per-thread dispatch state, used to avoid reentrant event dispatching.
     *
     * 线程调度状态，用于避免重入事件调度。
     */
    private final ThreadLocal<Boolean> dispatching =
        new ThreadLocal<Boolean>() {
          @Override
          protected Boolean initialValue() {
            return false;
          }
        };

    @Override
    void dispatch(Object event, Iterator<Subscriber> subscribers) {
      checkNotNull(event);
      checkNotNull(subscribers);
      Queue<Event> queueForThread = queue.get();
      // 如果可以在不违反容量限制的情况下立即将指定元素插入此队列，插入失败返回false
      queueForThread.offer(new Event(event, subscribers));
      // 检查线程调度状态
      if (!dispatching.get()) {
        // 设置线程调度状态为已调度
        dispatching.set(true);
        try {
          Event nextEvent;
          // poll: 从队列中获取并删除第一个元素
          while ((nextEvent = queueForThread.poll()) != null) {
            while (nextEvent.subscribers.hasNext()) {
              nextEvent.subscribers.next().dispatchEvent(nextEvent.event);
            }
          }
        } finally {
          // 删除此线程局部变量的当前线程值
          dispatching.remove();
          queue.remove();
        }
      }
    }

    private static final class Event {
      private final Object event;
      private final Iterator<Subscriber> subscribers;

      private Event(Object event, Iterator<Subscriber> subscribers) {
        this.event = event;
        this.subscribers = subscribers;
      }
    }
  }

  /** Implementation of a {@link #legacyAsync()} dispatcher. */
  private static final class LegacyAsyncDispatcher extends Dispatcher {

    // This dispatcher matches the original dispatch behavior of AsyncEventBus.
    // 此调度程序与 AsyncEventBus 的原始调度行为相匹配。
    // AsyncEventBus 默认调度器，在我看来和 ImmediateDispatcher 没什么不同，作者也说了（最后的注释），实现异步的是执行器而非调度器
    //
    // We can't really make any guarantees about the overall dispatch order for this dispatcher in
    // a multithreaded environment for a couple reasons:
    //
    // 由于以下几个原因，我们无法真正保证多线程环境中此调度程序的整体调度顺序：
    //
    // 1. 不同线程上发布的事件的订阅者可以相互交错。
    //  （一个线程上的事件，另一个线程上的 B 事件可以产生 [a1, a2, a3, b1, b2], [a1, b2, a2, a3, b2], [a1, b2, b3, a2, a3]，等等。）
    //
    // 2. 订阅者实际上可能以与添加到队列中的顺序不同的顺序被分派到。
    // 一个线程可以很容易地获取队列的头部，紧接着另一个线程获取队列中的下一个元素。 然后，第二个线程可以在第一个线程执行之前将其分派给订阅者。
    //
    // 1. Subscribers to events posted on different threads can be interleaved with each other
    //    freely. (A event on one thread, B event on another could yield any of
    //    [a1, a2, a3, b1, b2], [a1, b2, a2, a3, b2], [a1, b2, b3, a2, a3], etc.)
    // 2. It's possible for subscribers to actually be dispatched to in a different order than they
    //    were added to the queue. It's easily possible for one thread to take the head of the
    //    queue, immediately followed by another thread taking the next element in the queue. That
    //    second thread can then dispatch to the subscriber it took before the first thread does.
    //
    // All this makes me really wonder if there's any value in queueing here at all. A dispatcher
    // that simply loops through the subscribers and dispatches the event to each would actually
    // probably provide a stronger order guarantee, though that order would obviously be different
    // in some cases.
    //
    // 这一切让我真的想知道在这里排队是否有任何价值。
    // 一个简单地遍历订阅者并将事件分派给每个订阅者的调度程序实际上可能会提供更强的顺序保证，尽管在某些情况下该顺序显然会有所不同。

    /**
     * Global event queue.
     *
     * 全局事件队列
     */
    private final ConcurrentLinkedQueue<EventWithSubscriber> queue =
        Queues.newConcurrentLinkedQueue();

    @Override
    void dispatch(Object event, Iterator<Subscriber> subscribers) {
      checkNotNull(event);
      while (subscribers.hasNext()) {
        queue.add(new EventWithSubscriber(event, subscribers.next()));
      }

      EventWithSubscriber e;
      while ((e = queue.poll()) != null) {
        e.subscriber.dispatchEvent(e.event);
      }
    }

    private static final class EventWithSubscriber {
      private final Object event;
      private final Subscriber subscriber;

      private EventWithSubscriber(Object event, Subscriber subscriber) {
        this.event = event;
        this.subscriber = subscriber;
      }
    }
  }

  /**
   * Implementation of {@link #immediate()}.
   *
   * 即时调度器实现
   */
  private static final class ImmediateDispatcher extends Dispatcher {
    private static final ImmediateDispatcher INSTANCE = new ImmediateDispatcher();

    @Override
    void dispatch(Object event, Iterator<Subscriber> subscribers) {
      checkNotNull(event);
      // 来一个分发一个
      while (subscribers.hasNext()) {
        subscribers.next().dispatchEvent(event);
      }
    }
  }
}
