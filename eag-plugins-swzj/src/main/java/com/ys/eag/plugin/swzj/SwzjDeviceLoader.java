package com.ys.eag.plugin.swzj;

import com.ys.eag.api.DeviceLoader;
import com.ys.eag.api.dau.IDevice;

/**
 * @Author ws
 * @Date 2019/08/08
 */
public class SwzjDeviceLoader extends DeviceLoader {

    @Override
    protected IDevice getNewDevice() {
        return new SwzjDevice();
    }
}
