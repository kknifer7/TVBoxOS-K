package io.knifer.freebox.websocket.service;

import android.util.Base64;

import androidx.annotation.Nullable;

import com.github.catvod.crawler.Spider;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.AbsJson;
import com.github.tvbox.osc.bean.AbsSortJson;
import com.github.tvbox.osc.bean.AbsSortXml;
import com.github.tvbox.osc.bean.AbsXml;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.MovieSort;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.cache.CacheManager;
import com.github.tvbox.osc.cache.RoomDataManger;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.FileUtils;
import com.github.tvbox.osc.util.MD5;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.lzy.okgo.OkGo;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;

import org.apache.commons.lang3.StringUtils;
import org.java_websocket.WebSocket;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import io.knifer.freebox.constant.MessageCodes;
import io.knifer.freebox.model.c2s.RegisterInfo;
import io.knifer.freebox.model.common.Message;
import io.knifer.freebox.model.s2c.DeleteMovieCollectionDTO;
import io.knifer.freebox.model.s2c.DeletePlayHistoryDTO;
import io.knifer.freebox.model.s2c.GetCategoryContentDTO;
import io.knifer.freebox.model.s2c.GetDetailContentDTO;
import io.knifer.freebox.model.s2c.GetMovieCollectedStatusDTO;
import io.knifer.freebox.model.s2c.GetOnePlayHistoryDTO;
import io.knifer.freebox.model.s2c.GetPlayHistoryDTO;
import io.knifer.freebox.model.s2c.GetPlayerContentDTO;
import io.knifer.freebox.model.s2c.GetSearchContentDTO;
import io.knifer.freebox.model.s2c.SaveMovieCollectionDTO;
import io.knifer.freebox.model.s2c.SavePlayHistoryDTO;
import io.knifer.freebox.util.GsonUtil;
import io.knifer.freebox.util.HttpUtil;

/**
 * WebSocket服务
 * @author knifer
 */
public class WSService {

    private final WebSocket connection;

    private final String clientId;

    public WSService(WebSocket connection, String clientId) {
        this.connection = connection;
        this.clientId = clientId;
    }

    public void register() {
        send(Message.oneWay(
                MessageCodes.REGISTER,
                RegisterInfo.of(clientId, "tvbox-k-default")
        ));
    }

    public void sendSourceBeanList(String topicId) {
        send(Message.oneWay(
                MessageCodes.GET_SOURCE_BEAN_LIST_RESULT,
                ApiConfig.get().getSourceBeanList(),
                topicId
        ));
    }

    public void sendHomeContent(String topicId, SourceBean source) {
        send(Message.oneWay(
                MessageCodes.GET_HOME_CONTENT_RESULT,
                getHomeContent(source.getKey()),
                topicId
        ));
    }

    /**
     * 改写自SourceViewModel中的getSort方法
     * @see com.github.tvbox.osc.viewmodel.SourceViewModel
     * @param sourceKey sourceKey
     * @return homeContent信息
     */
    private AbsSortXml getHomeContent(String sourceKey) {
        if (sourceKey == null) {
            return null;
        }
        SourceBean sourceBean = ApiConfig.get().getSource(sourceKey);
        int type = sourceBean.getType();
        AbsSortXml sortXml = null;
        String content;
        switch (type) {
            case 3:
                try {
                    Spider sp = ApiConfig.get().getCSP(sourceBean);
                    String sortJson = sp.homeContent(true);
                    sortXml = sortJson(sortJson);
                } catch (Exception ignored) {}
                break;
            case 0:
            case 1:
                content = HttpUtil.getStringBody(OkGo.<String>get(sourceBean.getApi())
                        .tag(sourceBean.getKey() + "_sort")
                );
                if (type == 0) {
                    sortXml = sortXml(content);
                } else {
                    sortXml = sortJson(content);
                }
                // 到此为止即可（只取对应源站主页推荐数据，不考虑将豆瓣推荐结果发送给FreeBox）
                break;
            case 4:
                String extend = getFixUrl(sourceBean.getExt());

                if (URLEncoder.encode(extend).length() < 1000) {
                    content = HttpUtil.getStringBody(OkGo.<String>get(sourceBean.getApi())
                            .tag(sourceBean.getKey() + "_sort")
                            .params("filter", "true")
                            .params("extend", extend)
                    );
                    sortXml = sortJson(content);
                }
                // 到此为止即可（同上）
                break;
        }

        return sortXml;
    }

    private String getFixUrl(String content){
        if (content.startsWith("http://127.0.0.1")) {
            String path = content.replaceAll("^http.+/file/", FileUtils.getRootPath()+"/");
            path = path.replaceAll("localhost/", "/");
            content = FileUtils.readFileToString(path,"UTF-8");
        }

        return content;
    }

    public void sendCategoryContent(String topicId, GetCategoryContentDTO dto) {
        send(Message.oneWay(
                MessageCodes.GET_CATEGORY_CONTENT_RESULT,
                getCategoryContent(dto),
                topicId
        ));
    }

    private AbsXml getCategoryContent(GetCategoryContentDTO dto) {
        SourceBean source = dto.getSource();
        MovieSort.SortData sortData = dto.getSortData();
        Integer page = dto.getPage();
        int type = source.getType();
        String api = source.getApi();
        String key = source.getKey();
        String jsonData;
        AbsXml data = null;

        switch (type) {
            case 3:
                Spider spider = ApiConfig.get().getCSP(source);
                jsonData = spider.categoryContent(
                        sortData.id, String.valueOf(page), true, sortData.filterSelect
                );
                data = GsonUtil.fromJson(jsonData, AbsJson.class).toAbsXml();
                absXml(data, key);
                break;
            case 0:
            case 1:
                jsonData = HttpUtil.getStringBody(
                        OkGo.<String>get(api)
                                .tag(api)
                                .params("ac", type == 0 ? "videolist" : "detail")
                                .params("t", sortData.id)
                                .params("pg", page)
                                .params(sortData.filterSelect)
                                .params(
                                        "f",
                                        (
                                                sortData.filterSelect == null ||
                                                sortData.filterSelect.size() <= 0
                                        ) ?
                                        "" : new JSONObject(sortData.filterSelect).toString()
                                )
                );
                if (jsonData == null) {
                    break;
                }
                if (type == 0) {
                    data = xml(jsonData, key);
                } else {
                    data = GsonUtil.fromJson(jsonData, AbsJson.class).toAbsXml();
                    absXml(data, key);
                }
                break;
            case 4:
                String ext = StringUtils.EMPTY;
                String selectExt;

                if (sortData.filterSelect != null && sortData.filterSelect.size() > 0) {
                    try {
                        selectExt = new JSONObject(sortData.filterSelect).toString();
                        ext = Base64.encodeToString(
                                selectExt.getBytes("UTF-8"),
                                Base64.DEFAULT |  Base64.NO_WRAP
                        );
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                } else {
                    ext = Base64.encodeToString("{}".getBytes(), Base64.DEFAULT |  Base64.NO_WRAP);
                }
                jsonData = HttpUtil.getStringBody(
                        OkGo.<String>get(api)
                                .tag(api)
                                .params("ac", "detail")
                                .params("filter", "true")
                                .params("t", sortData.id)
                                .params("pg", page)
                                .params("ext", ext)
                );
                if (jsonData == null) {
                    break;
                }
                data = GsonUtil.fromJson(jsonData, AbsJson.class).toAbsXml();
                absXml(data, key);
                break;
        }

        return data;
    }

    private AbsXml xml(String xml, String sourceKey) {
        XStream xstream = new XStream(new DomDriver());
        xstream.autodetectAnnotations(true);
        xstream.processAnnotations(AbsXml.class);
        xstream.ignoreUnknownElements();
        if (xml.contains("<year></year>")) {
            xml = xml.replace("<year></year>", "<year>0</year>");
        }
        if (xml.contains("<state></state>")) {
            xml = xml.replace("<state></state>", "<state>0</state>");
        }
        AbsXml data = (AbsXml) xstream.fromXML(xml);
        absXml(data, sourceKey);

        return data;
    }

    private AbsSortXml sortXml(String xml) {
        try {
            XStream xstream = new XStream(new DomDriver());
            xstream.autodetectAnnotations(true);
            xstream.processAnnotations(AbsSortXml.class);
            xstream.ignoreUnknownElements();
            AbsSortXml data = (AbsSortXml) xstream.fromXML(xml);
            for (MovieSort.SortData sort : data.classes.sortList) {
                if (sort.filters == null) {
                    sort.filters = new ArrayList<>();
                }
            }
            return data;
        } catch (Exception e) {
            return null;
        }
    }

    private AbsSortXml sortJson(String json) {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            AbsSortJson sortJson = new Gson().fromJson(obj, new TypeToken<AbsSortJson>() {
            }.getType());
            AbsSortXml data = sortJson.toAbsSortXml();
            try {
                if (obj.has("filters")) {
                    LinkedHashMap<String, ArrayList<MovieSort.SortFilter>> sortFilters = new LinkedHashMap<>();
                    JsonObject filters = obj.getAsJsonObject("filters");
                    for (String key : filters.keySet()) {
                        ArrayList<MovieSort.SortFilter> sortFilter = new ArrayList<>();
                        JsonElement one = filters.get(key);
                        if (one.isJsonObject()) {
                            sortFilter.add(getSortFilter(one.getAsJsonObject()));
                        } else {
                            for (JsonElement ele : one.getAsJsonArray()) {
                                sortFilter.add(getSortFilter(ele.getAsJsonObject()));
                            }
                        }
                        sortFilters.put(key, sortFilter);
                    }
                    for (MovieSort.SortData sort : data.classes.sortList) {
                        if (sortFilters.containsKey(sort.id) && sortFilters.get(sort.id) != null) {
                            sort.filters = sortFilters.get(sort.id);
                        }
                    }
                }
            } catch (Throwable ignored) {}

            return data;
        } catch (Exception e) {
            return null;
        }
    }

    private MovieSort.SortFilter getSortFilter(JsonObject obj) {
        String key = obj.get("key").getAsString();
        String name = obj.get("name").getAsString();
        JsonArray kv = obj.getAsJsonArray("value");
        LinkedHashMap<String, String> values = new LinkedHashMap<>();

        for (JsonElement ele : kv) {
            JsonObject ele_obj = ele.getAsJsonObject();
            String values_key=ele_obj.has("n")?ele_obj.get("n").getAsString():"";
            String values_value=ele_obj.has("v")?ele_obj.get("v").getAsString():"";
            values.put(values_key, values_value);
        }
        MovieSort.SortFilter filter = new MovieSort.SortFilter();
        filter.key = key;
        filter.name = name;
        filter.values = values;

        return filter;
    }

    private void absXml(AbsXml data, String sourceKey) {
        if (data.movie != null && data.movie.videoList != null) {
            for (Movie.Video video : data.movie.videoList) {
                if (video.urlBean != null && video.urlBean.infoList != null) {
                    for (Movie.Video.UrlBean.UrlInfo urlInfo : video.urlBean.infoList) {
                        String[] str = null;
                        if (urlInfo.urls.contains("#")) {
                            str = urlInfo.urls.split("#");
                        } else {
                            str = new String[]{urlInfo.urls};
                        }
                        List<Movie.Video.UrlBean.UrlInfo.InfoBean> infoBeanList = new ArrayList<>();
                        for (String s : str) {
                            String[] ss = s.split("\\$");
                            if (ss.length > 0) {
                                if (ss.length >= 2) {
                                    infoBeanList.add(new Movie.Video.UrlBean.UrlInfo.InfoBean(ss[0], ss[1]));
                                } else {
                                    infoBeanList.add(new Movie.Video.UrlBean.UrlInfo.InfoBean(String.valueOf(infoBeanList.size() + 1), ss[0]));
                                }
                            }
                        }
                        urlInfo.beanList = infoBeanList;
                    }
                }
                video.sourceKey = sourceKey;
            }
        }
    }

    public void sendDetailContent(String topicId, GetDetailContentDTO dto) {
        send(Message.oneWay(
                MessageCodes.GET_DETAIL_CONTENT_RESULT,
                getDetailContent(dto),
                topicId
        ));
    }

    private AbsXml getDetailContent(GetDetailContentDTO dto) {
        String sourceKey = dto.getSourceKey();
        String urlid = dto.getVideoId();
        String pushUrl;
        String id;
        SourceBean source;
        int type;
        Spider sp;
        String jsonData;
        AbsXml data = null;

        if (urlid.startsWith("push://") && ApiConfig.get().getSource("push_agent") != null) {
            pushUrl = urlid.substring(7);
            if (pushUrl.startsWith("b64:")) {
                try {
                    pushUrl = new String(Base64.decode(pushUrl.substring(4), Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP), "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            } else {
                pushUrl = URLDecoder.decode(pushUrl);
            }
            sourceKey = "push_agent";
            urlid = pushUrl;
        }
        id = urlid;
        source = ApiConfig.get().getSource(sourceKey);
        type = source.getType();
        switch (type) {
            case 3:
                sp = ApiConfig.get().getCSP(source);
                jsonData = sp.detailContent(ImmutableList.of(id));
                data = GsonUtil.fromJson(jsonData, AbsJson.class).toAbsXml();
                absXml(data, sourceKey);
                break;
            case 0:
            case 1:
            case 4:
                jsonData = HttpUtil.getStringBody(
                        OkGo.<String>get(source.getApi())
                                .tag("detail")
                                .params("ac", type == 0 ? "videolist" : "detail")
                                .params("ids", id)
                );
                if (jsonData == null) {
                    break;
                }
                if (type == 0) {
                    data = xml(jsonData, sourceKey);
                } else {
                    data = GsonUtil.fromJson(jsonData, AbsJson.class).toAbsXml();
                    absXml(data, sourceKey);
                }
                break;
        }

        return data;
    }

    public void sendPlayerContent(String topicId, GetPlayerContentDTO dto) {
        send(Message.oneWay(
                MessageCodes.GET_PLAYER_CONTENT_RESULT,
                getPlayerContent(dto),
                topicId
        ));
    }

    @Nullable
    private JSONObject getPlayerContent(GetPlayerContentDTO dto) {
        SourceBean sourceBean = ApiConfig.get().getSource(dto.getSourceKey());
        int type = sourceBean.getType();
        String id = dto.getId();
        String playFlag = dto.getPlayFlag();
        String progressKey = dto.getProcessKey();
        Spider sp;
        String json;
        JSONObject result;

        switch (type) {
            case 3:
                if (StringUtils.isBlank(id)) {
                    return null;
                }
                sp = ApiConfig.get().getCSP(sourceBean);
                json = sp.playerContent(playFlag, id, ApiConfig.get().getVipParseFlags());
                try {
                    result = new JSONObject(json);
                    result.put("key", id);
                    result.put("proKey", progressKey);
                    if (!result.has("flag")) {
                        result.put("flag", playFlag);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();

                    return null;
                }
                break;
            case 0:
            case 1:
                String playUrl;

                result = new JSONObject();
                try {
                    result.put("key", id);
                    playUrl = sourceBean.getPlayerUrl().trim();
                    if (DefaultConfig.isVideoFormat(id) && playUrl.isEmpty()) {
                        result.put("parse", 0);
                        result.put("url", id);
                    } else {
                        result.put("parse", 1);
                        result.put("url", id);
                    }
                    result.put("proKey", progressKey);
                    result.put("playUrl", playUrl);
                    result.put("flag", playFlag);
                } catch (JSONException e) {
                    e.printStackTrace();

                    return null;
                }
                break;
            case 4:
                String extend = getFixUrl(sourceBean.getExt());

                if(URLEncoder.encode(extend).length()>1000) {
                    extend = "";
                }
                json = HttpUtil.getStringBody(
                        OkGo.<String>get(sourceBean.getApi())
                                .params("play", id)
                                .params("flag" ,playFlag)
                                .params("extend", extend)
                                .tag("play")
                );
                if (json == null) {
                    return null;
                }
                try {
                    result = new JSONObject(json);
                    result.put("key", id);
                    result.put("proKey", progressKey);
                    if (!result.has("flag")) {
                        result.put("flag", playFlag);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();

                    return null;
                }
                break;
            default:
                result = null;
        }

        return result;
    }

    public void sendPlayHistory(String topicId, GetPlayHistoryDTO dto) {
        send(Message.oneWay(
                MessageCodes.GET_PLAY_HISTORY_RESULT,
                getPlayHistory(dto),
                topicId
        ));
    }

    public void sendOnePlayHistory(String topicId, GetOnePlayHistoryDTO dto) {
        send(Message.oneWay(
                MessageCodes.GET_ONE_PLAY_HISTORY_RESULT,
                getOnePlayHistory(dto),
                topicId
        ));
    }

    private VodInfo getOnePlayHistory(GetOnePlayHistoryDTO dto) {
        String sourceKey = dto.getSourceKey();
        String vodId = dto.getVodId();
        VodInfo vodInfo;
        Object progressObj;

        if (StringUtils.isBlank(sourceKey) || StringUtils.isBlank(vodId)) {
            return null;
        }
        vodInfo = RoomDataManger.getVodInfo(sourceKey, vodId);
        if (vodInfo == null) {
            return null;
        }
        progressObj = CacheManager.getCache(getProgressKey(vodInfo));
        if (progressObj != null) {
            vodInfo.setProgress((long) progressObj);
        }

        return vodInfo;
    }

    private List<VodInfo> getPlayHistory(GetPlayHistoryDTO dto) {
        Integer limit = dto.getLimit();
        List<VodInfo> resultList;
        Object progressObj;

        if (limit == null || limit < 1) {
            limit = 100;
        }
        resultList = RoomDataManger.getAllVodRecord(Math.min(limit, 100));
        for (VodInfo vodInfo : resultList) {
            // 在缓存中查询播放进度
            progressObj = CacheManager.getCache(getProgressKey(vodInfo));
            if (progressObj == null) {
                continue;
            }
            vodInfo.setProgress(((long) progressObj));
        }

        return resultList;
    }

    public void searchContent(String topicId, GetSearchContentDTO dto) {
        AbsXml result = null;

        try {
            result = getSearchContent(dto);
        } catch (Exception ignored) {}
        send(Message.oneWay(
                MessageCodes.GET_SEARCH_CONTENT_RESULT,
                result,
                topicId
        ));
    }

    private AbsXml getSearchContent(GetSearchContentDTO dto) {
        String sourceKey = dto.getSourceKey();
        SourceBean source = ApiConfig.get().getSource(sourceKey);
        String keyword = dto.getKeyword();
        int type;
        Spider spider;
        String jsonData;
        AbsXml data = null;

        if (source == null) {
            return null;
        }
        type = source.getType();
        switch (type) {
            case 3:
                spider = ApiConfig.get().getCSP(source);
                jsonData = spider.searchContent(keyword, false);
                data = GsonUtil.fromJson(jsonData, AbsJson.class).toAbsXml();
                absXml(data, sourceKey);
                break;
            case 0:
            case 1:
                jsonData = HttpUtil.getStringBody(
                        OkGo.<String>get(source.getApi())
                                .params("wd", keyword)
                                .params(type == 1 ? "ac" : null, type == 1 ? "detail" : null)
                                .tag("search")
                );
                data = GsonUtil.fromJson(jsonData, AbsJson.class).toAbsXml();
                absXml(data, sourceKey);
                break;
            case 4:
                jsonData = HttpUtil.getStringBody(
                        OkGo.<String>get(source.getApi())
                                .params("wd", keyword)
                                .params("ac" ,"detail")
                                .params("quick" ,"false")
                                .tag("search")
                );
                data = GsonUtil.fromJson(jsonData, AbsJson.class).toAbsXml();
                absXml(data, sourceKey);
                break;
        }

        return data;
    }

    public void deletePlayHistory(String topicId, DeletePlayHistoryDTO dto) {
        VodInfo vodInfo;

        if (dto == null) {
            return;
        }
        vodInfo = dto.getVodInfo();
        if (isVodInfoInvalid(vodInfo)) {
            return;
        }
        RoomDataManger.deleteVodRecord(vodInfo.sourceKey, vodInfo);
        CacheManager.delete(getProgressKey(vodInfo), 0);
        send(Message.oneWay(
                MessageCodes.DELETE_PLAY_HISTORY_RESULT,
                null,
                topicId
        ));
    }

    public void sendMovieCollection(String topicId) {
        send(Message.oneWay(
                MessageCodes.GET_MOVIE_COLLECTION_RESULT,
                RoomDataManger.getAllVodCollect(),
                topicId
        ));
    }

    public void saveMovieCollection(String topicId, SaveMovieCollectionDTO dto) {
        VodInfo vodInfo = dto.getVodInfo();

        if (isVodInfoInvalid(vodInfo)) {
            return;
        }
        RoomDataManger.insertVodCollect(vodInfo.sourceKey, vodInfo);
        send(Message.oneWay(
                MessageCodes.SAVE_MOVIE_COLLECTION_RESULT,
                null,
                topicId
        ));
    }

    public void deleteMovieCollection(String topicId, DeleteMovieCollectionDTO dto) {
        VodInfo vodInfo = dto.getVodInfo();

        if (isVodInfoInvalid(vodInfo)) {
            return;
        }
        RoomDataManger.deleteVodCollect(vodInfo.sourceKey, vodInfo);
        send(Message.oneWay(
                MessageCodes.DELETE_MOVIE_COLLECTION_RESULT,
                null,
                topicId
        ));
    }

    public void getMovieCollectedStatus(String topicId, GetMovieCollectedStatusDTO dto) {
        String sourceKey = dto.getSourceKey();
        String vodId = dto.getVodId();

        if (StringUtils.isBlank(sourceKey) || StringUtils.isBlank(vodId)) {
            return;
        }
        send(Message.oneWay(
                MessageCodes.GET_MOVIE_COLLECTION_RESULT,
                RoomDataManger.isVodCollect(sourceKey, vodId),
                topicId
        ));
    }

    public void sendLives(String topicId) {
        send(Message.oneWay(
                MessageCodes.GET_LIVES_RESULT,
                ApiConfig.get().getLives(),
                topicId
        ));
    }

    private void send(Object obj) {
        connection.send(GsonUtil.toJson(obj));
    }

    public void savePlayHistory(SavePlayHistoryDTO dto) {
        VodInfo vodInfo = dto.getVodInfo();
        Long progress;
        String progressKey;

        if (isVodInfoInvalid(vodInfo)) {
            return;
        }
        // 保存历史记录：影片信息
        RoomDataManger.insertVodRecord(vodInfo.sourceKey, vodInfo);
        // 保存历史记录：播放进度
        progressKey = getProgressKey(vodInfo);
        progress = vodInfo.getProgress();
        if (progress != null && progress > 0) {
            CacheManager.save(progressKey, progress);
        }
    }

    private boolean isVodInfoInvalid(@Nullable VodInfo vodInfo) {
        return vodInfo == null || StringUtils.isBlank(vodInfo.sourceKey);
    }

    private String getProgressKey(VodInfo vodInfo) {
        return MD5.string2MD5(
                vodInfo.sourceKey +
                vodInfo.id +
                vodInfo.playFlag +
                vodInfo.playIndex +
                vodInfo.playNote
        );
    }
}
