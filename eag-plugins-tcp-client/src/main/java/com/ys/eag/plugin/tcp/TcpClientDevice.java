package com.ys.eag.plugin.tcp;

import com.ys.eag.api.dau.AbstractDevice;
import com.ys.eag.api.dau.entity.point.PointProperties;
import com.ys.eag.api.dau.entity.point.PointValue;
import com.ys.eag.api.exception.ConnectionInterruptException;
import com.ys.eag.plugin.tcp.util.TcpClientImpl;
import com.ys.eag.plugin.tcp.util.TcpClientService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;

import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author liuhualong
 * @date 2020/11/27
 */
@Slf4j
public class TcpClientDevice extends AbstractDevice {
    private TcpClientService service;
    private Boolean hexStr;
    private Boolean heartState = true;

    private int checkByteNum = 0;
    private int checkByte = (byte) 0x00;
    private String heartCheck = "";
    private AtomicLong lastUpdate;

    private HashMap<String, byte[]> recvBytes = new HashMap<>();
    private HashMap<String, String> recvString = new HashMap<>();

    private ScheduledThreadPoolExecutor executor;
    private AtomicReference<HashMap<String, byte[]>> atomicWriter = new AtomicReference<>();

    @Override
    protected boolean init() {
        executor = new ScheduledThreadPoolExecutor(5,
                new BasicThreadFactory.Builder().namingPattern("Eag-schedule-thread-%d").daemon(true).build());
        service = new TcpClientImpl();
        return true;
    }

    @Override
    protected boolean createConnection() {
        final Map propertyMap = this.getDeviceProperties().getDevicePropertyMap();
        final java.lang.String ip = propertyMap.get("ip").toString();
        final int port = Integer.parseInt(propertyMap.get("port").toString());
        hexStr = "hex".equals(propertyMap.get("hexStr").toString());
        final boolean connect = service.connect(ip, port, this.getDeviceProperties().getConnectTimeout());
        if (!connect) {
            log.error("??????TCP?????????[{}:{}]??????", ip, port);
            return false;
        }
        if (contains(propertyMap, "heartSend")) {
            // ????????????????????????
            final String heartSend = propertyMap.get("heartSend").toString();
            final int heartInterval = Integer.parseInt(propertyMap.get("heartInterval").toString());
            executor.scheduleAtFixedRate(() -> {
                if (hexStr) {
                    if (heartSend.contains(" ")) {
                        for (String s : heartSend.split(" ")) {
                            service.writeBytes(hex2bytes(s));
                        }
                    } else {
                        service.writeBytes(hex2bytes(heartSend));
                    }
                } else {
                    service.writeBytes(heartSend.getBytes());
                }
            }, 0L, heartInterval, TimeUnit.MILLISECONDS);
        }
        final int maxBytes = Integer.parseInt(propertyMap.get("maxBytes").toString());
        if (maxBytes == 0) {
            // ??????0??????????????????????????????????????????????????????
            return true;
        }
        if (contains(propertyMap, "heartCheck")) {
            // ??????????????????????????????????????????
            heartCheck = propertyMap.get("heartCheck").toString();
            int heartDelay = Integer.parseInt(propertyMap.get("heartDelay").toString());
            if (hexStr) {
                // ??????????????????heartCheck??????????????? 7#6A
                // ??????????????????????????????????????????7??????1?????????????????????0x6A?????????
                final String[] split = heartCheck.split("#");
                checkByteNum = Integer.parseInt(split[0]);
                checkByte = Integer.parseInt(split[1], 16);
            }
            // ?????????????????????heartCheck???????????????
            lastUpdate = new AtomicLong(System.currentTimeMillis());
            heartState = true;
            this.getThreadPool().execute(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    if (System.currentTimeMillis() - lastUpdate.get() > heartDelay) {
                        // ????????????????????????
                        heartState = false;
                    }
                }
            });
        }
        service.addBytesListener(maxBytes, this.getThreadPool(), (bytes, len) -> {
            try {
                if (hexStr) {
                    // ????????????
                    if (contains(propertyMap, "heartCheck") && bytes[checkByteNum - 1] == checkByte) {
                        lastUpdate.set(System.currentTimeMillis());
                    } else {
                        StringBuilder pointKey = new StringBuilder();
                        String[] keys = propertyMap.get("keys").toString().split("#");
                        for (String key : keys) {
                            int anInt = bytes[Integer.parseInt(key) - 1] & 0xff;
                            pointKey.append(anInt).append(",");
                        }
                        recvBytes.put(pointKey.toString(), bytes.clone());
                    }
                } else {
                    // ?????????
                    String s = new String(bytes, "GBK");
                    log.info("????????????????????????{}", s);
//                    String s = new String(bytes, 0, len);
                    if (contains(propertyMap, "heartCheck") && s.contains(heartCheck)) {
                        lastUpdate.set(System.currentTimeMillis());
                    } else {
                        // s:      {"temp":18.2,"hum":30}
                        // keys:   {"temp":#*1*#,"hum":#*2*#}
                        String keys = propertyMap.get("keys").toString();
                        final int index0 = keys.indexOf("#*");
                        final String pre = keys.substring(0, index0);
                        // pre = {"temp":
                        s = s.substring(s.indexOf(pre) + pre.length());
                        // s = 18.2,"hum":30}
                        keys = keys.substring(keys.indexOf(pre) + pre.length());
                        // keys = #*1*#,"hum":#*2*#}

                        while (keys.contains("#*") && keys.contains("*#")) {
                            keys = keys.substring(2);
                            // keys = 1*#,"hum":#*2*#}
                            final int index2 = keys.indexOf("*#");
                            String key = keys.substring(0, index2);
                            // key = 1
                            //????????????????????????
                            int nextIndex = keys.indexOf("#*");
                            // nextIndex = 10
                            if (nextIndex == -1) {
                                String checkStr = keys.substring(index2 + 2);
                                recvString.put(key, s.substring(0, s.indexOf(checkStr)));
                                break;
                            } else {
                                String checkStr = keys.substring(index2 + 2, nextIndex);
                                // checkStr = ,"hum":
                                keys = keys.substring(nextIndex);
                                // keys = #*2*#}
                                String sCheck = s.substring(0, s.indexOf(checkStr));
                                // sCheck = 18.2
                                final String replaceStr = sCheck + checkStr;
                                // replaceStr = 18.2,"hum":
                                s = s.substring(s.indexOf(replaceStr) + replaceStr.length());
                                // s =  30}
                                recvString.put(key, sCheck);
                            }
                        }
                    }
                }
            } catch (IndexOutOfBoundsException | UnsupportedEncodingException e) {
                log.warn("??????????????????????????????????????????????????????", e);
            }
        });
        return true;
    }

    @Override
    protected void destroyConnection() {
        executor.shutdown();
        recvBytes.clear();
        recvString.clear();
        service.disconnect();
    }

    @Override
    protected List<PointValue> readData() throws ConnectionInterruptException {
        if (!heartState) {
            log.warn("???????????????????????????????????????????????????");
            throw new ConnectionInterruptException();
        }

        List<PointValue> pvs = new ArrayList<>();
        try {
            for (PointProperties point : this.getReadPoints()) {
                Map map = point.getPointPropertyMap();
                if (hexStr) {
                    // ????????????????????????key??????????????? ??????key??????#?????????#???????????????1??????
                    final String[] keys = map.get("key").toString().split("#");
                    if (recvBytes.containsKey(keys[0])) {
                        final byte[] bytes = recvBytes.get(keys[0]);
                        final int bt = Integer.parseInt(keys[1]);
                        if (keys.length == 2) {
                            if (contains(map, "adder")) {
                                String[] split = map.get("adder").toString().split("#");
                                int hi = Integer.parseInt(split[0]);
                                int lo = Integer.parseInt(split[1]);
                                int ad = Integer.parseInt(split[2]);
                                int r = (bytes[hi] & 0xff) * ad + (bytes[lo] & 0xff);
                                pvs.add(new PointValue(point.getId(), String.valueOf(r)));
                            } else {
                                pvs.add(new PointValue(point.getId(), String.valueOf(bytes[bt - 1] & 0xff)));
                            }
                        } else {
                            final int bi = Integer.parseInt(keys[2]);
                            StringBuilder s = new StringBuilder(Integer.toBinaryString(bytes[bt - 1]));
                            if (s.length() >= 8) {
                                s = new StringBuilder(s.substring(s.length() - 8));
                            } else {
                                while (s.length() < 8) {
                                    s.insert(0, "0");
                                }
                            }
                            if (bi > 0 && bi <= 8) {
                                pvs.add(new PointValue(point.getId(), String.valueOf(s.charAt(bi - 1))));
                            }
                        }
                    }
                } else {
                    // ?????????????????????key??? ??????key????????????
                    String key = map.get("key").toString();
                    if (recvString.containsKey(key)) {
                        pvs.add(new PointValue(point.getId(), recvString.get(key)));
                    }
                }
            }
        } catch (IndexOutOfBoundsException e) {
            log.warn("??????????????????????????????????????????????????????");
            throw new ConnectionInterruptException();
        }
        return pvs;
    }

    @Override
    protected boolean writeData(List<PointProperties> points) {
        for (PointProperties point : points) {
            final Map propertyMap = point.getPointPropertyMap();
            String mode = propertyMap.get("mode").toString();

            int time = Integer.parseInt(propertyMap.get("time").toString());
            byte[] writeBytes;
            if (hexStr) {
                if (mode.contains("#?#")) {
                    final int parseInt = Integer.parseInt(point.getWriteValue());
                    String rep = Integer.toHexString(parseInt).toUpperCase();
                    if (parseInt < 16) {
                        rep = "0" + rep;
                    }
                    mode = mode.replace("#?#", rep);
                }
                if (mode.contains(" ")) {
                    // ???????????????????????????????????????
                    String[] split = mode.split(" ");
                    for (int i = 0; i < split.length - 1; i++) {
                        if (!service.writeBytes(hex2bytes(split[i]))) {
                            log.error("???????????????{}", mode);
                            return false;
                        }
                    }
                    mode = split[split.length - 1];
                }
                writeBytes = hex2bytes(mode);

            } else {
                mode = mode.replace("#?#", point.getWriteValue());
                writeBytes = mode.getBytes();
            }

            HashMap<String, byte[]> map = atomicWriter.get();
            if (map == null) {
                map = new HashMap<>(this.getPointCount());
            }
            if (time != 0) {
                if (!map.containsKey(point.getId())) {
                    map.put(point.getId(), writeBytes);
                    atomicWriter.set(map);
                    executor.scheduleAtFixedRate(new Runnable() {
                        private final String pointId = point.getId();

                        @Override
                        public void run() {
                            if (!service.writeBytes(atomicWriter.get().get(pointId))) {
                                log.warn("??????[{}]????????????", point.getPointName());
                            }
                        }
                    }, 0L, time, TimeUnit.MILLISECONDS);
                } else if (!Arrays.equals(map.get(point.getId()), writeBytes)) {
                    map.put(point.getId(), writeBytes);
                    atomicWriter.set(map);
                }
            } else {
                if (!service.writeBytes(writeBytes)) {
                    log.warn("??????[{}]????????????????????????{}", point.getPointName(), mode);
                } else {
                    log.info("??????[{}]????????????????????????{}", point.getPointName(), mode);
                }
            }
        }
        return true;
    }

    private byte[] hex2bytes(String hex) {
        if (hex.length() % 2 != 0) {
            log.error("???????????????????????????[{}]???????????????????????????????????????", hex);
        }
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < hex.length(); i = i + 2) {
            final byte parseByte = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
            bytes[i / 2] = parseByte;
        }
        return bytes;
    }

    private boolean contains(Map propertyMap, String key) {
        return propertyMap.containsKey(key)
                && propertyMap.get(key) != null
                && propertyMap.get(key).toString().length() != 0;
    }

}
