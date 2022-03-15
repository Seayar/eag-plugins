package com.ys.eag.plugin.ysmj;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author ws
 */
@Slf4j
public class YsMj {
    private static final Pattern ERR_CODE_PATTERN = Pattern.compile("\"errCode\":(\\w+)}");
    protected final ReadWriteLock lockVarValue = new ReentrantReadWriteLock();
    private final String PostType = "POST";

    private final String enumVarsUrl;
    private final String readEnumVarsUrl;
    private final String writeEnumVarsUrl;
    private List<MjModel> vars;
    private final Map<Integer, String> mapValues = new HashMap<>();
    private final int timeout;

    public List<MjModel> getVars() {
        return vars;
    }

    public Map<Integer, String> getMapValues() {
        lockVarValue.readLock().lock();
        Map<Integer, String> map = new HashMap<>(mapValues);
        lockVarValue.readLock().unlock();
        return map;
    }

    public void releaseMemory() {
        mapValues.clear();
    }

    public YsMj(String ip, int port, int timeout) {
        String common = "http://" + ip + ":" + port + "/mj/services/resteasy/gate_service";
        this.enumVarsUrl = common + "/enum_vars";
        this.readEnumVarsUrl = common + "/read_vars";
        this.writeEnumVarsUrl = common + "/write_vars";
        this.timeout = timeout;
    }

    public int enumVars() {
        int result = -2;
        String json = requestByGet(enumVarsUrl);
        if (json != null) {
            vars = getEnumVarsFromJsonArrStr(json);
            if (vars.size() > 0) {
                result = 0;
            } else {
                result = -1;
            }
        }
        return result;
    }

    public boolean readValue(List<Integer> keyList) {
        if (vars.size() > 0) {
            final List<MjModel> collect = vars.stream()
                    .filter(model -> keyList.contains(model.getKey()))
                    .collect(Collectors.toList());
            return readValueByModel(collect);
        }
        return false;
    }

    private boolean readValueByModel(List<MjModel> modelList) {
        final ArrayList<Integer> list = new ArrayList<>();
        modelList.forEach(model -> list.add(model.getKey()));
        final HashMap<String, ArrayList<Integer>> map = new HashMap<>(1);
        map.put("vars", list);
        final String jsonString = JSONObject.toJSONString(map);
        byte[] bytes = jsonString.getBytes(StandardCharsets.UTF_8);
        String json = requestByPost(readEnumVarsUrl, bytes);
        if (json != null) {
            getVarsFromJsonArrStr(json);
            return true;
        } else {
            return false;
        }

    }

    public Boolean writeValues(Map<Integer, String> map) {
        final ArrayList<Map<String, Integer>> list = new ArrayList<>();
        for (Map.Entry<Integer, String> entry : map.entrySet()) {
            final HashMap<String, Integer> hashMap = new HashMap<>(2);
            hashMap.put("k", entry.getKey());
            hashMap.put("v", Integer.valueOf(entry.getValue()));
            list.add(hashMap);
        }
        final HashMap<String, ArrayList<Map<String, Integer>>> wvs = new HashMap<>(1);
        wvs.put("vars", list);
        final String jsonString = JSONObject.toJSONString(wvs);
        byte[] bytes = jsonString.getBytes(StandardCharsets.UTF_8);
        String writeResult = requestByPost(writeEnumVarsUrl, bytes);
        // 分析结果
        Matcher matcher = ERR_CODE_PATTERN.matcher(writeResult);
        String temp = "";
        boolean find = false;
        while (matcher.find()) {
            temp = matcher.group(1);
            find = true;
        }
        if (find) {
            return Integer.parseInt(temp) == 0;
        }
        return true;
    }

    private List<MjModel> getEnumVarsFromJsonArrStr(String json) {
        List<MjModel> list = new ArrayList<>();
        JSONObject object = JSONObject.parseObject(json);
        JSONArray jsonArray = object.getJSONArray("vars");
        for (int i = 0; i < jsonArray.size(); i++) {
            try {
                list.add(jsonArray.getObject(i, MjModel.class));
            } catch (Exception ignored) {
            }
        }
        return list;
    }

    private void getVarsFromJsonArrStr(String json) {
        JSONArray array = JSONObject.parseObject(json).getJSONArray("vars");
        lockVarValue.writeLock().lock();
        for (int i = 0; i < array.size(); i++) {
            JSONObject jsonObject = array.getJSONObject(i);
            mapValues.put(Integer.valueOf(jsonObject.getString("k")), jsonObject.getString("v"));
        }
        lockVarValue.writeLock().unlock();
    }

    private String requestByGet(String url) {
        String getType = "GET";
        return request(url, getType, null);
    }

    private String requestByPost(String url, byte[] post) {
        return request(url, PostType, post);
    }

    private String request(String uri, String type, byte[] data) {
        StringBuilder stringBuilder = null;
        DataOutputStream out = null;
        InputStream in = null;
        HttpURLConnection urlConnection = null;
        try {
            URL url = new URL(uri);
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setDoInput(true);
            urlConnection.setDoOutput(true);
            urlConnection.setRequestMethod(type);
            urlConnection.setRequestProperty("Content-type", "application/json");
            urlConnection.setRequestProperty("Charset", "UTF-8");
            urlConnection.setConnectTimeout(timeout);
            urlConnection.connect();

            if (type.equals(PostType)) {
                out = new DataOutputStream(urlConnection.getOutputStream());
                out.write(data);
                out.flush();
            }
            //int rspCode = httpURLConnection.getResponseCode()
            in = urlConnection.getInputStream();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            stringBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line).append("\n");
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
                if (out != null) {
                    out.close();
                }
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
            } catch (Exception ignored) {
            }
        }
        return stringBuilder == null ? null : stringBuilder.toString();
    }
}
