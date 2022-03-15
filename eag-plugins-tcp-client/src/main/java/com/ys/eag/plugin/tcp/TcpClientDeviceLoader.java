package com.ys.eag.plugin.tcp;

import com.ys.eag.api.DeviceLoader;
import com.ys.eag.api.dau.IDevice;

/**
 * @author liuhualong
 * @date 2020/11/27
 */
public class TcpClientDeviceLoader extends DeviceLoader {
    @Override
    protected IDevice getNewDevice() {
        return new TcpClientDevice();
    }
}
