package com.ys.eag.plugin.swzj;


import com.alibaba.fastjson.JSONObject;
import com.ys.eag.api.dau.AbstractDevice;
import com.ys.eag.api.dau.entity.data.DataType;
import com.ys.eag.api.dau.entity.data.ReadWriteType;
import com.ys.eag.api.dau.entity.device.DeviceProperties;
import com.ys.eag.api.dau.entity.point.PointProperties;
import com.ys.eag.api.dau.entity.point.PointValue;
import com.ys.eag.api.exception.ConnectionInterruptException;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * @author wupeng
 * @date 2021/10/13
 */
@Slf4j
public class SwzjDevice extends AbstractDevice {

    private DatagramSocket socket;
    private final Map<Integer, Integer> recieveDataMap = new HashMap<>();
    private InetAddress castAddress;
    private int castPort;

    @Override
    public List<PointProperties> getBatchInsertData(PointProperties batchPoint) {
        Map batchPointMap = batchPoint.getPointPropertyMap();
        int startDefenseNo = Integer.parseInt(batchPointMap.get("defense_start_no").toString());
        int defenseNum = Integer.parseInt(batchPointMap.get("defense_num").toString());
        List<PointProperties> pointProperties = new ArrayList<>();
        for (int i = 0; i < defenseNum; i++, startDefenseNo++) {
            PointProperties properties = new PointProperties();
            properties.setDataType(DataType.TWO_BYTES_INT_SIGNED.getIndex());
            properties.setPointName("防区" + startDefenseNo);
            properties.setRegisterType("register");
            properties.setRwType(ReadWriteType.READ_ONLY.getIndex());
            Map<String, Integer> map = new HashMap<>();
            map.put("defense_no", startDefenseNo);
            properties.setPointProperty(JSONObject.toJSONString(map));
            pointProperties.add(properties);
        }
        return pointProperties;
    }

    @Override
    protected boolean init() {
        return true;
    }

    @Override
    protected boolean createConnection() {
        try {
            DeviceProperties properties = getDeviceProperties();
            Map pm = properties.getDevicePropertyMap();
            socket = new DatagramSocket(Integer.parseInt(pm.get("localPort").toString()));
            castAddress = InetAddress.getByName(pm.get("ip").toString());
            castPort = Integer.parseInt(pm.get("port").toString());
            return true;
        } catch (Exception e) {
            log.error("创建UDP连接失败，错误信息：{}", e.getMessage());
            return false;
        }
    }

    @Override
    protected void destroyConnection() {
        socket.close();
        castAddress = null;
        socket = null;
    }

    @Override
    protected List<PointValue> readData() throws ConnectionInterruptException {
        //发送采集指令
        byte[] message = new byte[]{(byte) 0xfb, 3, 0x0b, 0x0e};
        try {
            socket.send(new DatagramPacket(message, message.length, castAddress, castPort));
            processReceiveData();
        } catch (IOException e) {
            log.error("发送采集指令失败，详细信息：{}", e.getMessage());
            throw new ConnectionInterruptException();
        }
        List<PointValue> res = new ArrayList<>();
        for (PointProperties properties : getPoints()) {
            try {
                int defenseNo = Integer.parseInt(properties.getPointPropertyMap().get("defense_no").toString());
                //发送防区状态的采集指令
                byte[] defenseReqByte = createDefenseReqBytes((byte) defenseNo);
                socket.send(new DatagramPacket(defenseReqByte, defenseReqByte.length, castAddress, castPort));
                //将状态采集读取线程得到后进行填充
                res.add(new PointValue(properties.getId(), recieveDataMap.get(defenseNo).toString()));
            } catch (Exception ignored) {

            }
        }
        return res;
    }

    @Override
    protected boolean writeData(List<PointProperties> points) {
        return false;
    }

    private byte[] createDefenseReqBytes(byte defenseNo) {
        byte[] defenseReqByte = new byte[]{(byte) 0xf5, 4, defenseNo, (byte) 0x16, 0};
        byte checkSum = 0;
        for (int i = 0; i < 4; i++) {
            checkSum += defenseReqByte[i];
        }
        defenseReqByte[4] = checkSum;
        return defenseReqByte;
    }

    /**
     * 解析报文，放入Map缓存中
     */
    private void processReceiveData() throws IOException {
        byte[] buf = new byte[1024];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        socket.receive(packet);
        List<byte[]> data = parseReceiveData(packet.getData(), packet.getLength());
        for (byte[] res : data) {
            if (res.length == 6) {
                byte sumCheck = 0;
                for (int i = 1; i < 5; i++) {
                    sumCheck += res[i];
                }
                if (sumCheck != res[5]) {
                    return;
                }
                int defenseNo = res[3] * 100 + res[4];
                int type = res[2];
                recieveDataMap.put(defenseNo, type);
            } else if (res.length == 4) {
                byte sumCheck = 0;
                for (int i = 1; i < 3; i++) {
                    sumCheck += res[i];
                }
                if (sumCheck != res[3]) {
                    return;
                }
                int defenseNo = res[3] * 100 + res[4];
                int type = res[2];
                recieveDataMap.put(defenseNo, type);
            } else {
                log.info("不识别的报文，报文长度为：{}", res.length);
            }
        }
    }

    private List<byte[]> parseReceiveData(byte[] ori, int len) {
        byte[] tmp = new byte[6];
        List<byte[]> res = new ArrayList<>();
        int tmpLength = 0;
        for (int i = 0; i < len; i++) {
            if (ori[i] == (byte) 0xfa) {
                if (tmpLength != 0) {
                    byte[] copyData = new byte[tmpLength];
                    System.arraycopy(tmp, 0, copyData, 0, tmpLength);
                    res.add(copyData);
                    tmpLength = 0;
                }
            }
            tmp[tmpLength++] = ori[i];
        }
        if (tmpLength != 0) {
            byte[] copyData = new byte[tmpLength];
            System.arraycopy(tmp, 0, copyData, 0, tmpLength);
            res.add(copyData);
        }
        return res;
    }

}