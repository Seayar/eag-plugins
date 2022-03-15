import com.alibaba.fastjson.JSONObject;
import com.ys.eag.api.IDeviceLoader;
import com.ys.eag.api.dau.IDevice;
import com.ys.eag.api.dau.entity.device.DeviceProperties;
import com.ys.eag.api.dau.entity.point.PointProperties;
import com.ys.eag.api.dsu.IDsuService;
import com.ys.eag.plugin.swzj.SwzjDeviceLoader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author wupeng
 * Created by wupeng on 2021/10/22.
 */
public class TestSwDevice {

    public static void main(String[] args) {
        IDeviceLoader deviceLoader = new SwzjDeviceLoader();
        IDsuService service = values -> System.out.println(values.toString());


        HashMap<String, Object> map0 = new HashMap<>();
        map0.put("ip", "192.168.102.255");
        map0.put("port", 5006);
        String devicePrivateProperties = JSONObject.toJSONString(map0);
        DeviceProperties deviceProperties = new DeviceProperties("abcd1234", "testDevice",
                "1~s", 3, 300, devicePrivateProperties);


        List<PointProperties> pointPropertiesList = new ArrayList<>();
        PointProperties properties = new PointProperties();
        Map map = new HashMap();
        map.put("defense_no", 1);
        properties.setPointProperty(JSONObject.toJSONString(map));


        IDevice device = deviceLoader.getInstance(deviceProperties, pointPropertiesList, service);
        device.start();

    }

}
