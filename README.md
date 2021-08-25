# 前言

本项目是从`v30.0 Tag`切出来的分支，记录阅读源码时的注释

## 已注释模块

### EventBus

类名|注释完成度| 模块说明
---|---|---
EventBus| 30%| 事件总线，经典发布订阅模式
SubscriberRegistry | 100% | 订阅者注册表
Subscribe| 100% | 注解，声明订阅者方法
AllowConcurrentEvents| 100% | 注解，声明订阅者方法是线程安全的
DeadEvent| 100% | 包装已发布但没有订阅者因此无法传递的事件
SubscriberExceptionHandler|100%|订阅者抛出的异常处理程序
SubscriberExceptionContext|100%|订阅者抛出的异常的上下文


### Cache


