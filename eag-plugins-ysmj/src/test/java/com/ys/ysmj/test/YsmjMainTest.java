package com.ys.ysmj.test;

import com.ys.eag.api.DeviceLoader;
import com.ys.eag.api.dau.IDevice;
import com.ys.eag.api.dau.entity.device.DeviceProperties;
import com.ys.eag.api.dau.entity.point.PointProperties;
import com.ys.eag.api.dau.entity.point.PointValue;
import com.ys.eag.api.dsu.IDsuService;
import com.ys.eag.plugin.ysmj.YsmjDeviceLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * @author wupeng
 * Created by wupeng on 2021/7/27.
 */
public class YsmjMainTest {

    public static void main(String[] args) {
        DeviceLoader loader = new YsmjDeviceLoader();
        String privateProverty = "{\"remoteIp\":\"192.168.2.13\",\"remotePort\":8220}";
        DeviceProperties properties = new DeviceProperties("123", "test",
                "2~s", 0, 3000, privateProverty);
        List<PointProperties> pointProperties = new ArrayList<>();
        IDevice ysmj = loader.getInstance(properties, pointProperties, new IDsuService() {
            @Override
            public void setValue(List<PointValue> values) {

            }
        });

        ysmj.start();

    }

}
