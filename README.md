# 前言

本项目是从`v30.0 Tag`切出来的分支，记录阅读源码时的注释

## 已注释模块

### EventBus(100%)

类名|注释完成度| 模块说明
---|---|---
EventBus| 100%| 事件总线，经典发布订阅模式
AsyncEventBus| 100%| 支持异步事件总线，可以使用指定的Executor
Subscriber| 100%| 订阅者，包装订阅者方法
SubscriberRegistry | 100% | 订阅者注册表
Dispatcher|100%| 调度器，负责分发事件给订阅者，保证事件分发的顺序
Subscribe| 100% | 注解，声明订阅者方法
AllowConcurrentEvents| 100% | 注解，声明订阅者方法是线程安全的
DeadEvent| 100% | 包装已发布但没有订阅者因此无法传递的事件
SubscriberExceptionHandler|100%|订阅者抛出的异常处理程序
SubscriberExceptionContext|100%|订阅者抛出的异常的上下文

> AsyncEventBus 的异步执行是通过自己指定Executor来实现的，你可以传入一个线程池，你也可以传入一个DirectExecutor把他变成同步的

> EventBus 和 AsyncEventBus 的异同请仔细比较双方的构造函数

> Dispatcher 没有暴露接口给开发者，开发者无法指定Dispatcher。（除非修改包名）

### Cache

类名|注释完成度| 模块说明
---|---|---
Cache| 100%| Cache 接口
