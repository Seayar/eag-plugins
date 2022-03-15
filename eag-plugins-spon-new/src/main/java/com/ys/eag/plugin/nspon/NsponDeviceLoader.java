package com.ys.eag.plugin.nspon;

import com.ys.eag.api.DeviceLoader;
import com.ys.eag.api.dau.IDevice;

/**
 * @Author ws
 * @Date 2019/12/03
 */
public class NsponDeviceLoader extends DeviceLoader {
    @Override
    protected IDevice getNewDevice() {
        return new NsponDevice();
    }
}
