package com.ys.eag.plugin.ysmj;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.ys.eag.api.dau.AbstractDevice;
import com.ys.eag.api.dau.entity.data.TypeValue;
import com.ys.eag.api.dau.entity.data.TypeValueData;
import com.ys.eag.api.dau.entity.point.PointProperties;
import com.ys.eag.api.dau.entity.point.PointValue;
import com.ys.eag.api.exception.ConnectionInterruptException;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author ws
 */
@Slf4j
public class YsmjDevice extends AbstractDevice {
    private YsMj ysmj;
    private Map<String, Integer> pointKey;

    @Override
    protected boolean valid() {
        String ip = this.getDeviceProperties().getDevicePropertyMap().get("remoteIp").toString();
        String[] split = ip.replace(".", ",").split(",");
        if (split.length != 4) {
            log.info("Wrong format of ip address on device: {} ", this.getDeviceProperties().getDeviceName());
            return false;
        }
        return true;
    }

    @Override
    public List<PointProperties> getBatchInsertData(PointProperties batchPoint) {
        List<PointProperties> points = new ArrayList<>();
        String pts = batchPoint.getPointPropertyMap().get("point").toString();
        JSONArray jsonArray = JSONArray.parseArray(pts);
        if (jsonArray.size() <= 0) {
            log.info("从批量新增点位中获取点位的json字符串大小为0，批量点位名:{}", batchPoint.getPointName());
        } else {
            for (Object o : jsonArray) {
                String item = o.toString();
                if (item.length() == 0) {
                    continue;
                }
                PointProperties point = new PointProperties();
                point.setRegisterType(batchPoint.getRegisterType());
                point.setDataType(batchPoint.getDataType());
                point.setRwType(batchPoint.getRwType());
                point.setPointName(batchPoint.getPointName() + "_" + item);
                HashMap<String, Object> map = new HashMap<>();
                map.put("points", item);
                point.setPointProperty(JSONObject.toJSONString(map));

                points.add(point);
            }
        }

        if (points.size() == 0) {
            log.info("没有添加任何点位");
        } else {
            log.info("此次批量新增添加了{}个点位，批量点位名:{} ",
                    points.size(), batchPoint.getPointName());
        }
        return points;
    }

    @Override
    protected boolean init() {
        return true;
    }

    @Override
    public boolean createConnection() {
        Map deviceProperty = this.getDeviceProperties().getDevicePropertyMap();
        String remoteIp = deviceProperty.get("remoteIp").toString();
        int remotePort = Integer.parseInt(deviceProperty.get("remotePort").toString());
        ysmj = new YsMj(remoteIp, remotePort, this.getDeviceProperties().getConnectTimeout());
        if (ysmj.enumVars() != 0) {
            log.error("设备[name:{}]枚举点位失败", this.getDeviceProperties().getDeviceName());
            return false;
        }
        final List<PointProperties> readPoints = this.getReadPoints();
        pointKey = new HashMap<>(readPoints.size());
        for (PointProperties point : readPoints) {
            final String points = point.getPointPropertyMap().get("points").toString();
            pointKey.put(point.getId(), Integer.valueOf(points));
        }
        return true;
    }

    @Override
    public void destroyConnection() {
        ysmj.releaseMemory();
        pointKey.clear();
        ysmj = null;
    }

    @Override
    public List<PointValue> readData() throws ConnectionInterruptException {
        if (!ysmj.readValue(new ArrayList<>(pointKey.values()))) {
            return null;
        }
        List<PointValue> pointValueList = new ArrayList<>();
        Map<Integer, String> map = ysmj.getMapValues();
        for (Map.Entry<String, Integer> entry : pointKey.entrySet()) {
            pointValueList.add(new PointValue(entry.getKey(), map.get(entry.getValue())));
        }
        return pointValueList;
    }

    @Override
    public boolean writeData(List<PointProperties> points) {
        Map<Integer, String> map = new HashMap<>(points.size());
        for (PointProperties point : points) {
            String p = point.getPointPropertyMap().get("point").toString();
            map.put(Integer.valueOf(p), point.getWriteValue());
        }
        return ysmj.writeValues(map);
    }

    @Override
    public TypeValue getFieldDynamicData(String name) {
        TypeValue typeValue = new TypeValue();
        if ("point".equals(name)) {
            List<TypeValueData> dataList = new ArrayList<>();
            for (MjModel next : ysmj.getVars()) {
                TypeValueData data = new TypeValueData();
                data.setKey(next.getAddr());
                data.setValue(next.getKey());
                dataList.add(data);
            }
            typeValue.setData(dataList);
        } else {
            log.info("Found no dynamic data called {}", name);
        }
        return typeValue;
    }
}
