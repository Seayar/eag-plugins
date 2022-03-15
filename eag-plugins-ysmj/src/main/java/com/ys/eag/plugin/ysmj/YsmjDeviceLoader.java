package com.ys.eag.plugin.ysmj;

import com.ys.eag.api.DeviceLoader;
import com.ys.eag.api.dau.IDevice;
import lombok.extern.slf4j.Slf4j;

/**
 * @author ws
 */
@Slf4j
public class YsmjDeviceLoader extends DeviceLoader {

    @Override
    protected IDevice getNewDevice() {
        return new YsmjDevice();
    }
}
