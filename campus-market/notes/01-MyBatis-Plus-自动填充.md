# 笔记 · MyBatis-Plus 的自动填充字段

这份笔记对应项目中的 `campus-core/src/main/java/com/campus/core/handler/AuditMetaObjectHandler.java`。起因是搭建后端骨架时，每次插入数据都要手写 `create_time`、`update_time`、`deleted` 三个字段，我想知道能否交给框架统一处理，由此引出 MyBatis-Plus 的自动填充机制。下面记录它的工作方式、相关 API 的用法，以及「填充 SPI」这个说法的确切含义。

## 一、SPI 是什么

SPI 是 Service Provider Interface 的缩写，与 API 是一对相对的概念。API 的方向是「我调用你」：调用方知道接口，也知道实现由谁提供。SPI 的方向相反，是「框架调用你」：框架只定义接口，实现由使用方提供，框架在运行期找到它并回调。

Java 标准库里最典型的例子是 JDBC。`java.sql.Driver` 是 JDK 定义的接口，MySQL、Oracle 各自提供实现，并在自己的 jar 中放一个文件 `META-INF/services/java.sql.Driver`，文件内容是实现类的全限定名。`DriverManager` 初始化时用 `ServiceLoader` 扫描 classpath 下这类文件，把实现类装载进来。于是编写 `DriverManager.getConnection(...)` 的人不必知道底层是哪家厂商的驱动，换数据库只需要换 jar。

这套机制解决的是一个具体问题：扩展代码的位置如果必须在编译期确定，框架就无法调用它。SPI 把「发现实现」推迟到运行期，并约定好声明的位置与格式，使框架与实现之间只剩接口这一层依赖。

这里有一处措辞需要澄清。MyBatis-Plus 的 `MetaObjectHandler` 常被称作填充 SPI，但它的发现方式并不是 JDK 的 `ServiceLoader`，而是 Spring 容器：实现类标注 `@Component` 之后，MyBatis-Plus 的自动配置在启动阶段从容器中取出 `MetaObjectHandler` 类型的 Bean，注册进全局配置。准确的说法是——它承担的是 SPI 的职责（框架定义接口、业务提供实现、框架回调执行），但发现机制用的是 Spring Bean 装配，而不是 `META-INF/services`。区分这两层是有实际意义的：它必须是一个受 Spring 管理的 Bean，手动 new 出来的对象框架看不到。

## 二、填充的机制与 API

填充发生在 SQL 执行之前。业务调用 `mapper.insert(entity)` 或 `mapper.updateById(entity)` 后，MyBatis-Plus 的参数处理器在生成最终 SQL 前回调 `MetaObjectHandler` 的两个方法：写入走 `insertFill`，更新走 `updateFill`，填充完成后才把 SQL 发给数据库。业务代码因此不需要接触时间字段。

回调方法的参数是 `MetaObject`，它是 MyBatis 的反射包装器，通过 `getValue` / `setValue` 读写实体属性。handler 拿到的是这个包装器而不是具体实体，所以一个 handler 能处理项目中所有类型的实体。

写入字段用的是 `strictInsertFill` 与 `strictUpdateFill`：

```java
@Override
public void insertFill(MetaObject metaObject) {
    LocalDateTime now = LocalDateTime.now();
    strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
    strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
    strictInsertFill(metaObject, "deleted",    Long.class,          0L);
}

@Override
public void updateFill(MetaObject metaObject) {
    strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
}
```

四个参数依次是：`MetaObject`、实体属性名、属性类型、要写入的值。第二个参数是 **Java 属性名**（`createTime`），不是表列名（`create_time`），写错列名不会报错，只会静默不填充。

`strict` 前缀包含两层含义。第一，只处理声明过的字段：属性必须标注 `@TableField(fill = FieldFill.INSERT)` 或 `FieldFill.INSERT_UPDATE`，框架会在表信息中查找该属性，未声明填充策略的直接跳过。第二，只在属性值为 `null` 时写入，已有值不被覆盖。与之相对的是非严格方法 `fillStrategy(metaObject, property, value)`，它不校验注解，只按值是否为 `null` 判断。

`FieldFill` 是一个枚举，取值为 `DEFAULT`、`INSERT`、`UPDATE`、`INSERT_UPDATE`，分别表示不参与填充、插入时填充、更新时填充、插入与更新都填充。

## 三、本项目中的用法与三个需要注意的点

项目里 `AdminUser`、`AdminRole` 三个审计字段的声明方式如下，审计三件套因此不再出现在任何 Service 或初始化代码中：

```java
@TableField(fill = FieldFill.INSERT)
private LocalDateTime createTime;

@TableField(fill = FieldFill.INSERT_UPDATE)
private LocalDateTime updateTime;

@TableField(fill = FieldFill.INSERT)
private Long deleted;
```

`deleted` 填充的是 `0L` 而不是布尔值。本项目把 `deleted` 定义为删除时间戳而非 0/1 标记（`db/01` 的全局约定），唯一索引带上 `deleted` 之后，「取消收藏再收藏」不会撞唯一索引。也正因为不是标准布尔，项目没有启用 MyBatis-Plus 的逻辑删除配置，查询一律显式带 `deleted = 0`。

第一个需要注意的是注解缺失导致的静默失效。三个实体的时间字段最初没有标注 `@TableField(fill = ...)`，`strictInsertFill` 会跳过未声明的字段且不抛异常，于是初始化器一直在手动 set 时间——表面上能正常运行，换个 Service 忘记 set 就会产生脏数据。这类问题不会有任何日志提示，只能通过检查实体注解发现。

第二个是严格模式不覆盖已有值。将来实现软删除时要把 `deleted` 回填为删除时间戳，`strictUpdateFill` 只在值为 `null` 时生效，这种情况下需要直接调用 `metaObject.setValue("deleted", System.currentTimeMillis())`。

第三个是不要把填充机制与 `updateById` 的空值策略混为一谈。填充器解决的是「字段有没有值」，`@TableField(strategy)` 解决的是「`null` 要不要写进 SET 子句」，后者默认是 `NOT_NULL`。因此 `AdminAuthService` 中的 `update.setLockUntil(null)` 并不会真正把数据库中的值清空，将来确实需要置空时要用 `LambdaUpdateWrapper` 显式 set。这两者分属不同环节，容易一起踩。

还有一处需要在实际使用时验证：填充由 MyBatis-Plus 的参数处理器触发，对于手写 XML 的写入语句是否同样生效，不要想当然，写一条实际跑一下再下结论。

最后记下它的扩展点。将来要增加 `create_by`、`update_by` 这类操作人字段时，不需要改动任何 Service，只在这个 handler 里从 Sa-Token 取当前登录 ID 填入即可，一处修改、全局生效。这也是把它写成独立 handler 而不是静态工具方法的原因。
