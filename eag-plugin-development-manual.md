#EAG插件开发规范帮助手册
本文档介绍了EAG插件开发的基本规则和一些约束，在开发EAG设备插件时，需严格遵守本手册。手册版本与eag-api开发包版本保持一致，确保所开发的插件包完美兼容EAG设备。
###修改作者与版本记录
作者|版本|修改日期
:---:|:---:|:---:
伍鹏|V0.2.1|2018-11-19
伍鹏|V0.2.1|2018-11-30
伍鹏|V0.3.0|2018-12-19
伍鹏|V1.5|2019-03-04

###项目结构
- eag-plugins
是整个工程的根节点，存放了maven根配置文件以及本手册。所有的子模块都需要继承该模块。该层一般不存放代码。
- eag-plugins-*
这是第一层模块，一般按照通讯设备的通用协议来分类。比如modbus协议、OPC协议、串口协议可分别命名为eag-plugins-modbus、eag-plugins-opc、eag-plugins-serial。该层模块maven配置文件一般引用本协议底层开发包、必要的工具包等。除此之外，该协议通用型代码也放置于此。如果该种协议没有子分类，本层模块的打包类型直接设置为jar。
- eag-plugins-*-\*
这是第二层模块，本项目最多设置两层子模块。该层模块主要描述了同一基本协议下分属不同的设备类型。比如：eag-plugins-modbus下的modbus-ethernet即属于以太网类modbus协议设备。
###快速上手
####依赖环境
- 本工程开发要求使用jdk 1.8版本，适配Ubuntu 16.04LTS，IDE工具推荐使用idea。
- 当你要开发一个新插件时，确保引入了最新的eag-api。该开发包源代码位于eag工程下，用idea打开该工程并更新代码，用maven的install插件安装到自己本机maven仓库中。
- 更新eag-plugins工程代码，并修改根maven配置文件，确保eag-api版本号一致。注意：若api有改动且工程原有代码不兼容最新版本，请即时修改自己负责的模块，即时更新代码。
####开发要求
- 代码要求符合阿里巴巴JAVA编程规范。
- 要求开发模块时以尽量复用的规则来组织。
- 每个插件（协议、设备）要求经过严格的单元测试，并提供最直接的测试工具（包），供测试人员、配置人员、开发人员使用。
####接口说明

每一个EAG插件都是eag-api的实现，因此需要详细了解eag-api的结构。eag-api分两个包，com.ys.eag.api.dsu为数据处理单元的统一接口，com.ys.eag.api.dau则为数据采集单元的接口。在开发插件的过程中，我们需要对dau接口进行实现，而dsu的接口只需调用即可。

下面将对每一个接口和类进行说明。

#####包：com.ys.eag.api.dau.entity
1. com.ys.eag.api.dau.entity.data.DataType
enum类型。该类提供了EAG采集时所支持的所有数据类型。每一个类型存储着显示名称、数据库存储值、类型长度（1长度对应2字节）以及对应的modbus4j DataType值。在点位实体PointProperties中对应的dataType所存储的信息为数据库存储值，因此DataType提供了`getDataTypeByIndex(String index)`方法进行转换。
2. com.ys.eag.api.dau.entity.data.PluginPrivateProperties
class entity类型。该类主要是每个插件的属性描述文件的数据实体(Entity)，主要用于加载属性描述文件（一般为json文件）。该类包含4个私有字段version、deviceFields、pointFields、registerTypes分别为插件版本号、设备私有属性、点位私有属性以及插件寄存器类型列表。
3. com.ys.eag.api.dau.entity.data.PrivateField
class entity类型。该类主要是每个插件的私有字段数据实体，主要也用于加载属性描述文件。
4. com.ys.eag.api.dau.entity.data.ReadWriteType
enum类型。该类是点位读写类型的集合，包含只读、只写、读写。该类仅做一般了解，在开发插件的过程中一般不用考虑。
5. com.ys.eag.api.dau.entity.data.RegisterType
class entity类型。该类也用于加载属性描述文件的寄存器类型字段。
6. com.ys.eag.api.dau.entity.data.TypeValue
class entity类型。该类不用了解。
7. com.ys.eag.api.dau.entity.device.DeviceProperties
class entity类型。该类极为重要，是设备插件的基本信息，在设备建立连接时极为重要。类中的devicePrivateProperties字段为该设备插件的私有属性值，可通过`getDevicePropertyMap()`获取对应的key:value信息。具体字段及其意义请阅读eag-api源码。
8. com.ys.eag.api.dau.entity.point.PointProperties
class entity类型。该类极为重要，是采集点位的基本信息，在点位读写时提供所有必要信息。在开发插件时，需着重关注该类的dataType、initValue、registerType、pointProperty、writeValue等字段。具体字段及其意义请阅读eag-api源码。
9. com.ys.eag.api.dau.entity.point.PointValue
class entity类型。该类极为重要，用于存储采集得到的点位值。字段包含pointId（点位ID）、value（点位值）、timestamps（生成值的时间戳）。
10. com.ys.eag.api.dau.entity.data.TypeValueData
class entity类型。该类极为重要，用来组织私有字段的Data值。
11. com.ys.eag.api.dau.entity.data.ValidEntity
class entity类型。该类极为重要，用来组织联动校验。
#####包：com.ys.eag.api.dau
1. com.ys.eag.api.dau.IDevice
interface类型。该接口是所有插件中的设备类的抽象。
2. com.ys.eag.api.dau.AbstractDevice
abstract类型。**该类极为重要，在开发插件时，必须有一个类需继承该类并实现其中的抽象方法。** 我们可以通过`getDeviceProperties()`获取设备属性信息。该类的抽象方法有`boolean createConnection()`用以创建与下位机设备的连接，`void destroyConnection()`用来关闭或销毁与下位机设备的连接。`List<PointValue> readData()`用来读取下位机的点位值，在调用此方法之前，可以调用`this.getReadPoints()`用来获取得到应读取的所有点位信息，并做相应的一些处理。最后的一个抽象方法`boolean writeData(List<PointProperties> points)`用以向下位机设备写值。
#####包：com.ys.eag.api
1. IDeviceLoader
interface类型。每个插件包必须实现该接口，且作为该插件包对外提供的最重要的信息之一（对应eag上传插件时需提供ClassLoaderName字段）。该接口代码如下：
```java
package com.ys.eag.api;

import com.ys.eag.api.dau.IDevice;
import com.ys.eag.api.dau.entity.data.PrivateField;
import com.ys.eag.api.dau.entity.data.RegisterType;
import com.ys.eag.api.dau.entity.device.DeviceProperties;
import com.ys.eag.api.dau.entity.point.PointProperties;

import java.util.List;

/**
 * 插件开发者必须实现的一个接口
 * 是插件的加载类
 *
 * @author wupeng
 * @version 1.0
 */
public interface IDeviceLoader {

    /**
     * 获取一个设备的实例
     *
     * @param properties 设备实际属性
     * @param points     设备将采集的点位数据
     * @return 设备实例
     */
    IDevice getInstance(DeviceProperties properties, List<PointProperties> points);

    /**
     * 获取一个设备的独有字段信息
     *
     * @return 一组字段信息
     */
    List<PrivateField> getDevicePrivateFields();

    /**
     * 获取一个设备下点位的独有字段信息
     *
     * @return 一组字段信息
     */
    List<PrivateField> getPointPrivateFields();

    /**
     * 获取该设备类型的所有寄存器类型
     *
     * @return 寄存器类型列表
     */
    List<RegisterType> getRegisterTypeList();

    /**
     * 获取插件的版本号
     *
     * @return 版本号
     */
    String getVersion();

}
```
####开发过程
1. 根据将要开发的新设备协议，编写一个规范的、健壮且可独立运行的测试工具（包含使用文档），并需经严格测试通过。
2. 在eag-plugins工程下对应的模块中，新建模块（maven项目）并继承上层模块，主包的命名符合com.ys.eag.plugin.*，*为你新建模块名称。
3. 在resources文件夹下新建设备属性描述文件（一般为json文件，但不强制）。文件编写要求附在本节最后。
4. 新建类`*Device`并继承AbstractDevice抽象类，并实现其中的抽象方法。
5. 新建类`*DeviceLoader`并实现`IDeviceLoader`接口，读取设备属性描述文件到`PluginPrivateProperties`中，并在实现方法中调用对应的方法。 
6. 在实现的`*DeviceLoader`中，对设备和点位字段的验证必须有详细的日志操作，验证失败需提供ERROR级别的日志，并给出详细信息。
7. 所有的类都需加注解`@Slf4j`并加以详细的日志，日志分级完整，日志系统详尽但又不冗余。日志在记录INFO、DEBUG和非提示性的WARN级别时，提供英文信息即可，若有提示性的WARN级别、ERROR级别，则需提供清晰明了且详细的中文信息。
8. 插件开发完成后，需经单元测试通过，最后maven打包时需完整依赖，以免插件运行时找不到对应工具类。注意插件包的命名版本需和插件本身版本一致。

附：

######属性描述文件的说明

名称|类型|描述|备注
:---:|:---:|:---:|---
name|string|英文名称|无
cName|string|中文名称|无
type|enum|类型|目前包含四项：STRING,NUM,ENUM,TREE  
typeValue|string|值|如果为null，则为空文本框。initValue代表默认值。若type为enum,tree，则data用[{key:string,value:string,children:[]}] 表示
typeUnit|string|单位|如果为null，则不显示该项

######typeValue结构如下：

```
typeValue: {
	initValue：默认值,
	data: [
		{
			key: string,
			value: string,
			children: []
		}
		....
	]
}
```
###更新说明
####关于字段联动校验的说明
在设备以及点位的一些字段设置中，可能某些字段带有联动检验。
如：当modbus设备的寄存器类型为CoilStatus时，点位可读写，且数据类型必须为BOOLEAN，因此需要字段的联动校验。
联动校验的规则如下：每一个由插件提供的**需要联动校验**的字段的**值**，都**有一个属性valid**，该属性是一个数组，数组包含了联动校验对象，结构如下：
```
	"valid": [
        {
          "target": "",
          "value": [""]
        },
        {
          "target": "",
          "value": [""]
        }
      ]
```
target代表目标字段的name，value代表该字段应该填写的值，是一个列表。**若value为null，则可以是任意值**（若target代表的是下拉列表，则为下拉列表任意枚举值）。
##### 寄存器类型字段示例：
```
  "registerTypes": [
    {
      "name": "CoilStatus",
      "value": "CoilStatus",
      "valid": [
        {
          "target": "dataType",
          "value": ["BOOLEAN"]
        }
      ]
    }
  ]
```
上述代码表示：
当选中寄存器类型字段下的CoilStatus，那么对应的数据类型字段应该选中BOOLEAN，读写类型可任意，如果不符合该验证规范，则提示报错。
##### 私有字段示例：
```
"deviceFields": [
    {
      "name": "communicationMode",
      "cName": "通信模式",
      "type": "ENUM",
      "typeValue": {
        "initValue": "TCP",
        "data": [
          {
            "key": "TCP",
            "value": "TCP"
          },
          {
            "key": "UDP",
            "value": "UDP"
			"valid": [
			  "target":"dataProtocol",
			  "value":"TCP"
			]
          }
        ]
      }
    },
    {
      "name": "dataProtocol",
      "cName": "数据协议",
      "type": "ENUM",
      "typeValue": {
        "initValue": "TCP",
        "data": [
          {
            "key": "TCP",
            "value": "TCP"
          },
          {
            "key": "RTU",
            "value": "RTU"
          }
        ]
      }
    }
]
```
上述例子表明，当通信模式选择TCP时并无联动校验，但当通信模式选择UDP时，数据协议只能选择TCP。
####私有字段描述文件增加动态获取数据字段和是否必填字段
在原有的描述文件中，除了原有字段外，增加了两项**dynamicData、required**，具体描述如下：

名称|类型|描述|备注
:---:|:---:|:---:|---
dynamicData|bool|是否为动态数据|如果是动态数据，则通过api请求，并且要求设备处于运行状态下，值为true，否则为false或null
required|bool|是否为必填项目|如果必填，则为true或null,非必填则为false

由于增加了**dynamicData**字段，因此插件在继承0.3.0及后续的API时，都应实现```Map getDynamicData(String name)```方法，其中参数部分为要获取动态数据的字段名称。所有数据以符合该字段的```type```形式返回，如列表或树等。
####新增读取数据抛异常
readData接口新增抛出异常，异常类在API中。接口原型：```List<PointValue> readData() throws ConnectionInterruptException```。
该异常表示在设备采集数据时与设备断开连接或任何连接异常，则抛出该异常。我们必须有捕捉和处理设备运行状态中与下位机断开连接或通信异常的能力。