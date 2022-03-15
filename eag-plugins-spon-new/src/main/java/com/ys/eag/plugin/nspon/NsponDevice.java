package com.ys.eag.plugin.nspon;

import com.ys.eag.api.dau.AbstractDevice;
import com.ys.eag.api.dau.entity.point.PointProperties;
import com.ys.eag.api.dau.entity.point.PointValue;
import com.ys.eag.api.exception.ConnectionInterruptException;
import com.ys.eag.plugin.nspon.enums.TerminalState;
import com.ys.eag.plugin.nspon.enums.TerminalStatus;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @Author ws
 * @Date 2019/12/03
 */
@Slf4j
public class NsponDevice extends AbstractDevice {
    private PostServer server;
    private List<String> list;
    private Map<String, String> pointIdTerminal;
    private Map<String, String> tasks;

    @Override
    protected boolean init() {
        Map map = this.getDeviceProperties().getDevicePropertyMap();
        String ip = map.get("ip").toString();
        String user = map.get("user").toString();
        server = new PostServer(ip, user,
                this.getDeviceProperties().getConnectTimeout());

        pointIdTerminal = new HashMap<>();
        this.getPoints().forEach(point -> {
            Object source = point.getPointPropertyMap().get("source");
            if (source != null) {
                pointIdTerminal.put(point.getId(), source.toString());
            }
        });
        List<String> l = new ArrayList<>();
        this.getReadPoints().stream()
                .filter(point -> point.getPointPropertyMap().get("function").toString().contains("status"))
                .distinct()
                .forEach(point -> l.add(point.getPointPropertyMap().get("source").toString()));
        list = l.stream().distinct().collect(Collectors.toList());
        return true;
    }

    @Override
    protected boolean createConnection() {
        Map map = this.getDeviceProperties().getDevicePropertyMap();
        String ip = map.get("ip").toString();
        boolean ping = ping(ip);
        if (ping) {
            tasks = server.getTasks(0);
            log.info("登录设备[{}]成功，查询到计划任务数量为：{}",
                    this.getDeviceProperties().getDeviceName(), tasks.size());
        }
        return ping;
    }

    @Override
    protected void destroyConnection() {
    }

    @Override
    protected List<PointValue> readData() throws ConnectionInterruptException {
        Map<String, TerminalStatus> terminalStatus = server.getTerminalStatus(list);

        List<PointValue> pvs = new ArrayList<>();
        for (PointProperties point : this.getReadPoints()) {
            String id = point.getPointPropertyMap().get("source").toString();
            String function = point.getPointPropertyMap().get("function").toString();
            if ("status2".equals(function)) {
                // 目标终端
                if (!terminalStatus.containsKey(id) || terminalStatus.get(id).target == null) {
                    pvs.add(new PointValue(point.getId(), ""));
                } else {
                    pvs.add(new PointValue(point.getId(), terminalStatus.get(id).target));
                }
            } else if ("status3".equals(function)) {
                // 发起方/接收方
                if (!terminalStatus.containsKey(id) || terminalStatus.get(id).identity == null) {
                    pvs.add(new PointValue(point.getId(), ""));
                } else {
                    pvs.add(new PointValue(point.getId(), String.valueOf(terminalStatus.get(id).identity.id)));
                }
            } else {
                // 终端状态
                if (!terminalStatus.containsKey(id)) {
                    pvs.add(new PointValue(point.getId(), TerminalState.OFFLINE.index));
                } else {
                    pvs.add(new PointValue(point.getId(), terminalStatus.get(id).state.index));
                }
            }
        }

        return pvs;
    }

    @Override
    protected boolean writeData(List<PointProperties> points) {
        for (PointProperties point : points) {
            if ("status".equals(point.getRegisterType())) {
                log.info("点位[{}]寄存器类型为状态采集，不可进行控制写值", point.getPointName());
                continue;
            }

            String function = point.getPointPropertyMap().get("function").toString();
            if ("control".equals(function)) {
                log.info("错误点位:{}", point.getPointName());
            } else if (function.contains("status")) {
                log.info("点位[{}]的功能不是一个可写功能，写值失败", point.getPointName());
                return false;
            } else if ("task".equals(function)) {
                String wv = point.getWriteValue();
                String act = point.getPointPropertyMap().get("act").toString();
                boolean ret = server.doTask(act, tasks.get(wv));
                if (!ret) {
                    log.info("点位[{}]写值失败", point.getPointName());
                    return false;
                }
            } else {
                int cmdType = Integer.parseInt(function.substring(function.length() - 1));
                String source = point.getPointPropertyMap().get("source").toString();
                int act = Integer.parseInt(point.getPointPropertyMap().get("act").toString());

                // 写值为目标终端，多个终端用'-'隔开
                String wv = point.getWriteValue();
                if (wv.length() > 6) {
                    wv = pointIdTerminal.get(wv);
                }
                boolean ret = server.doTerminalCtrl(source, wv, cmdType, act);
                if (!ret) {
                    log.info("点位[{}]写值失败", point.getPointName());
                    return false;
                }
            }
        }

        return true;
    }

    private boolean ping(String ipAddress) {
        try {
            return InetAddress.getByName(ipAddress).isReachable(this.getDeviceProperties().getConnectTimeout());
        } catch (IOException e) {
            log.error("目标主机不可达，设备启动失败");
            return false;
        }
    }
}
