# EEC介绍

[![Build Status][travis-image]][travis] [![Release][release-image]][releases] [![License][license-image]][license]

EEC（Excel Export Core）是一个Excel读取和写入工具，目前支持xlsx格式的读取、写入以及xls格式的读取(xls支持版本BIFF8也就是excel 97~2003格式)。
EEC的设计初衷是为了解决Apache POI速度慢，高内存且API臃肿的诟病，EEC的底层并没有使用Apache POI包，所有的底层读写代码均自己实现，事实上EEC仅依懒`dom4j`和`logback`，前者用于小文件xml读取，后者统一日志接口。

EEC最大特点是`高速`和`低内存`，如果在项目中做数据导入导出功能，选用EEC将为你带来极大的便利，同时它的`可扩展`能力也不弱。

使用`inlineStr`模式的情况下EEC的读写内存可以控制在*10MB*以下，`SharedString`模式也可以控制在*16MB*以下。[这里](https://www.ttzero.org/excel/2020/03/05/eec-vs-easyexcel-2.html) 有关于EEC的压力测试，最低可以在*6MB*的情况下完成1,000,000行x29列数据的读写。

EEC采用单线程、高IO设计，所以多核心、高内存并不能显著提高速度，高主频和一块好SSD能显著提升速度。

EEC在JVM参数`-Xmx6m -Xms1m`下读写`1,000,000行x29列`内存使用截图

写文件

![eec write 100w](./images/eec_write_100w.png)

读文件

![eec read 100w](./images/eec_read_100w.png)

## 现状

目前已实现worksheet类型有

- [ListSheet](./src/main/java/org/ttzero/excel/entity/ListSheet.java) // 对象数组
- [ListMapSheet](./src/main/java/org/ttzero/excel/entity/ListMapSheet.java) // Map数组
- [StatementSheet](./src/main/java/org/ttzero/excel/entity/StatementSheet.java) // PreparedStatement
- [ResultSetSheet](./src/main/java/org/ttzero/excel/entity/ResultSetSheet.java) // ResultSet支持(多用于存储过程)
- [EmptySheet](./src/main/java/org/ttzero/excel/entity/EmptySheet.java) // 空worksheet
- [CSVSheet](./src/main/java/org/ttzero/excel/entity/CSVSheet.java) // 支持csv与xlsx互转

也可以继承已知[Worksheet](./src/main/java/org/ttzero/excel/entity/Sheet.java)来实现自定义数据源，比如微服务，mybatis或者其它RPC

EEC并不是一个功能全面的Excel操作工具类，它功能有限并不能用它来完全替代Apache POI，它最擅长的操作是表格处理。比如将数据库表导出为Excel或者读取Excel表格内容到Stream或数据库。

## WIKI

阅读[WIKI](https://github.com/wangguanquan/eec/wiki) 了解更多用法（编写中）


## 主要功能

1. 支持大数据量导出，行数无上限。如果数据量超过单个sheet上限会自动分页。（xlsx单sheet最大1,048,576行）
2. **超低内存**，无论是xlsx还是xls格式，大部分情况下可以在10MB以内完成十万级甚至百万级行数据读写。
3. 支持 对象数组 和 Map数组 导出。
4. 可以为某列设置阀值高亮显示。如导出学生成绩时低于60分的单元格背景标黄显示。
5. 导出excel默认隔行变色(俗称斑马线)，利于阅读
6. 设置列宽自动调节（功能未完善）
7. 设置水印（文字，本地＆网络图片）
8. 提供Watch窗口查看操作细节也可以做进度条。
9. ExcelReader采用stream方式读取文件，只有当你操作某行数据的时候才会执行读文件，而不会将整个文件读入到内存。
10. Reader支持iterator或者stream+lambda操作sheet或行数据，你可以像操作集合类一样读取并操作excel

## 使用方法

pom.xml添加

```
<dependency>
    <groupId>org.ttzero</groupId>
    <artifactId>eec</artifactId>
    <version>${eec.version}</version>
</dependency>
```

## 示例

### 导出示例，更多使用方法请参考test/各测试类

所有测试生成的excel文件均放在target/excel目录下，可以使用`mvn clean`清空。测试命令可以使用`mvn clean test`
清空先前文件避免找不到测试结果文件

#### 1. 简单导出
对象数组导出时可以在对象上使用注解`@ExcelColumn("column name")`来设置excel头部信息，未添加ExcelColumn注解标记的属性将不会被导出，也可以通过调用`forceExport`方法来强制导出。

```
    private int id; // not export

    @ExcelColumn("渠道ID")
    private int channelId;

    @ExcelColumn
    private String account;

    @ExcelColumn("注册时间")
    private Timestamp registered;
```

默认情况下导出的列顺序与字段在对象中的定义顺序一致，也可以设置`colIndex`或者在`addSheet`时重置列头顺序。

```
public void testWrite(List<Student> students) throws IOException {
    // 创建一个名为"test object"的excel文件，指定作者，不指定时默认取系统登陆名
    new Workbook("test object", "guanquan.wang")
    
        // 添加一个worksheet，可以通过addSheet添加多个worksheet
        .addSheet(new ListSheet<>("学生信息", students))
        
        // 指定输出位置，如果做文件导出可以直接输出到`respone.getOutputStream()`
        .writeTo(Paths.get("f:/excel"));
}
```

#### 2. 高亮和数据转换

高亮和数据转换是通过`@FunctionalInterface`实现，下面展示如何将低下60分的成绩输出为"不合格"并将单元格标红

```
public void testStyleConversion(List<Student> students) throws IOException {
    new Workbook("2021小五班期未考试成绩")
        .addSheet(new ListSheet<>("期末成绩", students
            , new Column("学号", "id", int.class)
            , new Column("姓名", "name", String.class)
            , new Column("成绩", "score", int.class, n -> (int) n < 60 ? "不合格" : n)
                .setStyleProcessor((o, style, sst) -> {
                   if ((int)o < 60) {
                       style = Styles.clearFill(style)
                           | sst.addFill(new Fill(PatternType.solid, Color.orange));
                   }
                   return style;
               })
            )
        )
        .writeTo(Paths.get("f:/excel"));
}
```

内容如下图

![期未成绩](./images/30dbd0b2-528b-4e14-b450-106c09d0f3b2.png)

### 读取示例

EEC使用`ExcelReader#read`静态方法读文件，其内部采用流式操作，当使用某一行数据时才会真正读入内存，所以即使是GB级别的Excel文件也只占用少量内存。

默认的ExcelReader仅读取单元格的值而忽略单元格的公式，可以使用`ExcelReader#parseFormula`方法使Reader解析单元格的公式。

下面展示一些常规的读取方法

#### 1. 使用stream操作

```
public void streamRead() {
    try (ExcelReader reader = ExcelReader.read(defaultPath.resolve("用户注册.xlsx"))) {
        reader.sheets().flatMap(Sheet::rows).forEach(System.out::println);
    } catch (IOException e) {
        e.printStackTrace();
    }
}
```

#### 2. 将excel读入到数组或List中

```
/**
 * read excel to object array
 */
public void readToList() {
    try (ExcelReader reader = ExcelReader.read(defaultPath.resolve("用户注册.xlsx"))) {
        // 读取所有worksheet
        Regist[] array = reader.sheets()

            // 读取数据行
            .flatMap(Sheet::dataRows)

            // 将每行数据转换为Regist对象
            .map(row -> row.to(Regist.class))

            // 转数组或者List
            .toArray(Regist[]::new);

        // TODO 其它逻辑

    } catch (IOException e) {
        e.printStackTrace();
    }
}
```

#### 3. 当然既然是Stream那么就可以使用流的全部功能，比如加一些过滤和聚合等。

```
reader.sheets()
    .flatMap(Sheet::dataRows)
    .map(row -> row.to(Regist.class))
    .filter(e -> "iOS".equals(e.platform()))
    .collect(Collectors.toList());
```

以上代码相当于`select * from 用户注册 where platform = 'iOS'`

### xls格式支持

读取xls格式的方法与读取xlsx格式完全一样，读取文件时不需要判断是xls格式还是xlsx格式，因为EEC为其提供了完全一样的接口，内部会根据文件头去判断具体类型， 这种方式比判断文件后缀准确得多。

pom.xml添加

```
<dependency>
    <groupId>org.ttzero</groupId>
    <artifactId>eec-e3-support</artifactId>
    <version>0.5.0</version>
</dependency>
```

你可以在 [search.maven.org](https://search.maven.org/artifact/org.ttzero/eec-e3-support) 查询eec-e3-support版本，两个工具的兼容性 [参考此表](https://github.com/wangguanquan/eec/wiki/EEC%E4%B8%8EE3-support%E5%85%BC%E5%AE%B9%E6%80%A7%E5%AF%B9%E7%85%A7%E8%A1%A8)

### CSV与Excel格式互转

- CSV => Excel 向Workbook中添加一个`CSVSheet`即可
- Excel => CSV 读Excel后通过Worksheet调用`saveAsCSV`

代码示例

```
// CSV转Excel
new Workbook("csv path test", author)
    .addSheet(new CSVSheet(csvPath)) // 添加CSVSheet并指定csv路径
    .writeTo(getOutputTestPath());
    
// Excel转CSV
try (ExcelReader reader = ExcelReader.read(testResourceRoot().resolve("1.xlsx"))) {
    // 读取Excel并保存为CSV格式
    reader.sheet(0).saveAsCSV(getOutputTestPath());
} catch (IOException e) {
    e.printStackTrace();
}
```

## CHANGELOG
Version 0.5.0 (2022-05-22)
-------------
- 增加StyleDesign用于样式处理（单元格或者整行样式处理）
- 增加FreezePanes用于冻结网格
- 修改部分BUG(#227,#232,#238,#243)
- 读取文件支持自定义注解转对象(#237)

Version 0.4.14 (2021-12-19)
-------------
- 提高对Numbers转xlsx的兼容性
- 值转换从原来的int类型扩大为Object
- 增加@RowNum注解，用于注入行号
- 修改ListSheet.EntryColumn的访问权限，方便实现更多高级特性
- 支持单列数字无表头导出，现在可以简单的导出`List<String>`数据
- 修复已知BUG(#197,#202，#205,#219)
- 将com.google.common包重命名为org.ttzero.excel.common解决内嵌引起的包冲突(#200)

Version 0.4.13 (2021-08-09)
-------------
- 支持xls获取图片
- `@ExcelColumn`注解增加`colIndex`属性，用于指定列顺序(#188)
- 读取文件时`Worksheet#getIndex()`方法返回Sheet在文件中的下标而非id，并取消按id排序(#193)
- 修复部分BUG(#182,#190)

Version 0.4.12.1 (2021-05-20)
-------------
- Hotfix：HeaderStyle注解设置某列cell颜色会影响所有表头样式


[更多...](./CHANGELOG)

[travis]: https://travis-ci.org/wangguanquan/eec
[travis-image]: https://travis-ci.org/wangguanquan/eec.png?branch=master

[releases]: https://github.com/wangguanquan/eec/releases
[release-image]: http://img.shields.io/badge/release-0.5.0-blue.svg?style=flat

[license]: http://www.apache.org/licenses/LICENSE-2.0
[license-image]: http://img.shields.io/badge/license-Apache--2-blue.svg?style=flat
