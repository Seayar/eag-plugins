import com.alibaba.fastjson.JSONObject;
import com.ys.eag.api.IDeviceLoader;
import com.ys.eag.api.dau.IDevice;
import com.ys.eag.api.dau.entity.data.DataType;
import com.ys.eag.api.dau.entity.data.ReadWriteType;
import com.ys.eag.api.dau.entity.device.DeviceProperties;
import com.ys.eag.api.dau.entity.point.PointProperties;
import com.ys.eag.api.dau.entity.point.PointValue;
import com.ys.eag.api.dsu.IDsuService;
import com.ys.eag.plugin.tcp.TcpClientDeviceLoader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * @author liuhualong
 * @date 2020/11/27
 */
public class Test {

    public static void main(String[] args) throws InterruptedException {

        IDeviceLoader deviceLoader = new TcpClientDeviceLoader();
        IDsuService service = new IDsuService() {
            @Override
            public void setValue(List<PointValue> values) {
                System.out.println(values.toString());
            }
        };


        HashMap<String, Object> map0 = new HashMap<>();
        map0.put("ip", "192.168.2.86");
        map0.put("port", 6667);
        map0.put("hexStr", "str");
//        map0.put("hexStr", "hex");
        map0.put("maxBytes", 350);


//        map0.put("keys", "2");
        map0.put("keys", "地址:#*1*#,报警状态:#*2*#,");
//        map0.put("keys", "{\"temp\":#*1*#,\"hum\":#*2*#}");
        String devicePrivateProperties = JSONObject.toJSONString(map0);
        DeviceProperties deviceProperties = new DeviceProperties("abcd1234", "testDevice",
                "2~s", 0, 5000, devicePrivateProperties);

        PointProperties point = new PointProperties();
        point.setId("id123");
        point.setRegisterType("register");
        point.setDataType(DataType.STRING.getIndex());
        point.setRwType(ReadWriteType.READ_WRITE.getIndex());
        point.setPointName("testPoint");

        HashMap<String, Object> map = new HashMap<>();
        // 十六进制读
//        map.put("key", "4,#3");
//        map.put("mode", "01053000FF00833A 01053010GG082FF");
        // 字符串读
        map.put("key", "2");

        // 定时写
//        map.put("mode","80#?#7F08");
        map.put("time", 0);
        point.setPointProperty(JSONObject.toJSONString(map));

        List<PointProperties> pointPropertiesList = new ArrayList<>();
        pointPropertiesList.add(point);

        IDevice device = deviceLoader.getInstance(deviceProperties, pointPropertiesList, service);
        device.start();

//        Thread.sleep(1000);
//        final ArrayList<PointValue> pointValues = new ArrayList<>();
//        pointValues.add(new PointValue(point.getId(), "10"));
//        device.writePoints(pointValues);
//
//        Thread.sleep(15000);
//        pointValues.clear();
//        pointValues.add(new PointValue(point.getId(),"12"));
//        device.writePoints(pointValues);

    }
}
