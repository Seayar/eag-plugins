package com.ys.eag.plugin.nspon;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.base.Charsets;
import com.ys.eag.plugin.nspon.enums.TerminalIdentity;
import com.ys.eag.plugin.nspon.enums.TerminalState;
import com.ys.eag.plugin.nspon.enums.TerminalStatus;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author ws
 * @Date 2019/12/03
 */
@Slf4j
public class PostServer {
    private final String ip;
    private final String user;
    private final int timeout;

    public PostServer(String ip, String user, int timeout) {
        this.ip = ip;
        this.user = user;
        this.timeout = timeout;
    }

    public Map<String, String> getTasks(int pageIndex) {
        String url = "http://" + ip + "/php/gettaskdata.php";
        HashMap<String, String> map = new HashMap<>(3);
        map.put("pageIndex", String.valueOf(pageIndex));
        map.put("pageCount", "50");
        map.put("user", user);
        String content = map2Str(map);

        Map<String, String> tasks = new HashMap<>();
        try {
            String ret = doPost(url, content, timeout);
            JSONArray rows = JSON.parseObject(ret).getJSONArray("rows");
            for (Object r : rows) {
                JSONObject row = JSON.parseObject(r.toString());
                tasks.put(row.getString("name"), row.getString("id"));
            }
            if (rows.size() >= 50) {
                tasks.putAll(getTasks(pageIndex + 1));
            }
        } catch (IOException e) {
            log.info("发送POST请求时发生了错误，错误信息：", e);
        }
        return tasks;
    }

    public Map<String, TerminalStatus> getTerminalStatus(List<String> terminalIds) {
        String url = "http://" + ip + "/php/getsingleterminaldata.php";

        Map<String, TerminalStatus> terminalStatus = new HashMap<>(terminalIds.size());
        for (int t = 0; t < terminalIds.size() / 100 + 1; t++) {
            // 分页查询终端信息，每页最多100个
            StringBuilder searchText = new StringBuilder();
            for (int i = 0; i < 100 && i < terminalIds.size() - t * 100; i++) {
                String id = terminalIds.get(t * 100 + i);
                searchText.append(id);
                searchText.append(",");
            }
            // 去掉末尾的逗号
            String st = searchText.substring(0, searchText.length() - 1);
            int finalT = t;
            HashMap<String, String> map = new HashMap<String, String>(4) {
                {
                    put("pageIndex", String.valueOf(finalT));
                    put("pageCount", "100");
                    put("user", user);
                    put("searchTxt", st);
                }
            };
            String content = map2Str(map);

            try {
                String ret = doPost(url, content, timeout);
                JSONObject retObject = JSON.parseObject(ret);
                String res = retObject.get("res").toString();
                if ("1".equals(res)) {
                    for (Object row : retObject.getJSONArray("rows")) {
                        JSONObject terminalObj = JSON.parseObject(row.toString());
                        String id = terminalObj.get("id").toString();
                        String state = terminalObj.get("state").toString();
                        if ("-1".equals(state)) {
                            // 离线、脱机
                            terminalStatus.put(id, new TerminalStatus(id, TerminalState.OFFLINE));
                        } else {
                            String task = terminalObj.get("task").toString();
                            if ("0".equals(task)) {
                                // 空闲
                                terminalStatus.put(id, new TerminalStatus(id, TerminalState.ONLINE));
                            } else {
                                String[] split = task.replace('|', ',').split(",");
                                TerminalState ts = TerminalState.get(Integer.parseInt(split[0]) + 1);
                                TerminalIdentity ti = TerminalIdentity.get(Integer.parseInt(split[2]));
                                String target = split[1];
                                terminalStatus.put(id, new TerminalStatus(id, ti, ts, target));
                            }
                        }
                    }
                }
            } catch (IOException e) {
                log.info("发送POST请求时发生了错误，错误信息：", e);
            }

        }
        return terminalStatus;
    }

    /**
     * 终端控制: 1-对讲 2-监听 3-广播
     */
    public boolean doTerminalCtrl(String source, String target, int cmdType, int act) {
        String url = "http://" + ip + "/php/exesdkcommand.php";
        HashMap<String, String> map = new HashMap<String, String>(5) {
            {
                put("source", source);
                put("target", target);
                put("commandtype", String.valueOf(cmdType));
                put("isstop", String.valueOf(act));
                put("user", user);
            }
        };

        String content = map2Str(map);

        String ret;
        try {
            ret = doPost(url, content, timeout);
        } catch (IOException e) {
            log.info("发送终端控制时发生了错误，错误信息：", e);
            return false;
        }
        // {“res”:”1”}
        // {“res”:”0”,"taskId":taskId}
        JSONObject retObject = JSON.parseObject(ret);
        String res = retObject.get("res").toString();
        return "1".equals(res);
    }

    /**
     * 执行计划任务
     */
    public boolean doTask(String act, String taskId) {
        String url = "http://" + ip + "/php/exetaskcmd.php";

        // act当且仅当为0才会发起任务
        HashMap<String, String> map = new HashMap<String, String>(2) {
            {
                put("taskCommand", "0".equals(act) ? "runtaskinfo" : "stoptaskinfo");
                put("taskId", taskId);
            }
        };
        String content = map2Str(map);

        String ret;
        try {
            ret = doPost(url, content, timeout);
        } catch (IOException e) {
            log.info("发送任务请求时发生了错误，错误信息：", e);
            return false;
        }
        // {“res”:”1”}
        // {“res”:”0”,"taskId":taskId}
        JSONObject retObject = JSON.parseObject(ret);
        String res = retObject.get("res").toString();
        return "1".equals(res);
    }

    private static String doPost(String strUrl, String content, int timeout) throws IOException {
        URL url = new URL(strUrl);
        //通过调用url.openConnection()来获得一个新的URLConnection对象，并且将其结果强制转换为HttpURLConnection.
        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        urlConnection.setRequestMethod("POST");
        //设置连接的超时值为30000毫秒，超时将抛出SocketTimeoutException异常
        urlConnection.setConnectTimeout(timeout);
        //设置读取的超时值为30000毫秒，超时将抛出SocketTimeoutException异常
        urlConnection.setReadTimeout(timeout);
        //将url连接用于输出，这样才能使用getOutputStream()。getOutputStream()返回的输出流用于传输数据
        urlConnection.setDoOutput(true);
        //设置通用请求属性为默认浏览器编码类型
        urlConnection.setRequestProperty("content-type", "application/x-www-form-urlencoded");
        //getOutputStream()返回的输出流，用于写入参数数据。
        OutputStream outputStream = urlConnection.getOutputStream();
        outputStream.write(content.getBytes());
        outputStream.flush();
        outputStream.close();
        //此时将调用接口方法。getInputStream()返回的输入流可以读取返回的数据。
        InputStream inputStream = urlConnection.getInputStream();
        int length = urlConnection.getContentLength();
        if (length != -1) {
            byte[] data = new byte[length];
            byte[] temp = new byte[1024];
            int readLen = 0;
            int destPos = 0;
            while ((readLen = inputStream.read(temp)) > 0) {
                System.arraycopy(temp, 0, data, destPos, readLen);
                destPos += readLen;
            }
            inputStream.close();
            return new String(data, Charsets.UTF_8);
        }
        inputStream.close();

        return "";
    }

    private String map2Str(HashMap<String, String> m) {
        String str1 = "jsondata%5B";
        String str2 = "%5D=";

        StringBuilder ret = new StringBuilder();
        for (String key : m.keySet()) {
            ret.append(str1).append(key).append(str2).append(m.get(key)).append("&");
        }
        return ret.toString().substring(0, ret.lastIndexOf("&"));
    }
}
