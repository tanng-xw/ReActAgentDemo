package com.react.agentdemo.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.react.agentdemo.model.RelationType;
import com.react.agentdemo.model.enums.AssetType;
import com.react.agentdemo.service.MockDataService;
import com.react.agentdemo.tool.ToolContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 智能助手工具集合
 * 使用 @Tool 注解定义工具方法
 * 
 * @author Kimi
 */
@Component
public class AgentTools {

    private static final Logger logger = LoggerFactory.getLogger(AgentTools.class);

    private final LocationTool locationTool;
    private final WeatherTool weatherTool;
    private final MockDataService mockDataService;

    public AgentTools(LocationTool locationTool, WeatherTool weatherTool, MockDataService mockDataService) {
        this.locationTool = locationTool;
        this.weatherTool = weatherTool;
        this.mockDataService = mockDataService;
    }

    // ==================== 原有工具 ====================

    /**
     * 获取用户当前所在城市
     */
    @Tool(description = "查询用户当前所在的城市位置。支持北京、上海、杭州")
    public String getUserLocation() {
        String sessionId = ToolContextHolder.getSessionId();
        if (sessionId == null) {
            sessionId = "default";
            logger.warn("未找到 SessionId，使用默认值");
        }
        
        logger.info("获取用户位置，SessionId: {}", sessionId);
        
        Map<String, Object> params = Map.of("sessionId", sessionId);
        Object result = locationTool.execute(params, sessionId);
        return result != null ? result.toString() : "未知";
    }

    /**
     * 查询指定城市的天气
     */
    @Tool(description = "查询指定城市的天气信息。支持北京、上海、杭州的天气查询，返回天气状况、温度、湿度和风力")
    public String getWeather(
            @ToolParam(description = "城市名称，如：北京、上海、杭州") String city) {
        Map<String, Object> params = Map.of("city", city != null ? city : "北京");
        Object result = weatherTool.execute(params, "default");
        return result != null ? result.toString() : "查询失败";
    }

    // ==================== 新工具 ====================

    /**
     * 联网知识搜索工具
     */
    @Tool(description = "联网搜索相关知识。支持搜索歌手/乐队/歌曲背景信息和资料。关键词支持最多5个，可增加限定词如'XXX 百科'、'XXX 歌曲'")
    public String webSearch(
            @ToolParam(description = "搜索关键词列表，最多5个。可从多个角度搜索，可增加限定词如'周杰伦 百科'") List<String> keywords) {
        
        logger.info("WebSearch: keywords={}", keywords);
        
        if (keywords == null || keywords.isEmpty()) {
            return "请提供搜索关键词";
        }
        
        // 限制最多5个关键词
        List<String> limitedKeywords = keywords.size() > 5 ? keywords.subList(0, 5) : keywords;
        
        List<JsonNode> results = mockDataService.getWebSearchResults(limitedKeywords);
        
        StringBuilder sb = new StringBuilder();
        sb.append("搜索结果（").append(results.size()).append("条）：\n\n");
        
        for (int i = 0; i < results.size(); i++) {
            JsonNode result = results.get(i);
            sb.append(i + 1).append(". ").append(result.get("title").asText()).append("\n");
            sb.append("   ").append(result.get("content").asText()).append("\n\n");
        }
        
        return sb.toString().trim();
    }

    /**
     * 曲库歌曲搜索工具
     */
    @Tool(description = "通过关键词搜索曲库歌曲。关键词可以是歌名、歌手名、专辑名，或通过空格两两组合。支持最多5个关键词")
    public String songSearch(
            @ToolParam(description = "搜索关键词列表，最多5个。例如：['晴天']、['周杰伦']、['流行 抒情']") List<String> keywords) {
        
        logger.info("SongSearch: keywords={}", keywords);
        
        if (keywords == null || keywords.isEmpty()) {
            return "请提供搜索关键词";
        }
        
        List<String> limitedKeywords = keywords.size() > 5 ? keywords.subList(0, 5) : keywords;
        List<JsonNode> songs = mockDataService.searchSongs(limitedKeywords);
        
        return formatSongList(songs, "曲库搜索结果");
    }

    /**
     * 歌单搜索工具
     */
    @Tool(description = "搜索曲库歌单。关键词应接近歌单名，支持最多3个关键词")
    public String songlistSearch(
            @ToolParam(description = "搜索关键词列表，最多3个。例如：['经典']、['深夜 治愈']") List<String> keywords) {
        
        logger.info("SongListSearch: keywords={}", keywords);
        
        if (keywords == null || keywords.isEmpty()) {
            return "请提供搜索关键词";
        }
        
        List<String> limitedKeywords = keywords.size() > 3 ? keywords.subList(0, 3) : keywords;
        List<JsonNode> songlists = mockDataService.searchSonglists(limitedKeywords);
        
        if (songlists.isEmpty()) {
            return "未找到相关歌单";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("找到 ").append(songlists.size()).append(" 个歌单：\n\n");
        
        for (int i = 0; i < songlists.size(); i++) {
            JsonNode songlist = songlists.get(i);
            sb.append(i + 1).append(". ").append(songlist.get("title").asText()).append("\n");
            sb.append("   ").append(songlist.get("description").asText()).append("\n");
            
            JsonNode songs = songlist.get("songs");
            if (songs != null && songs.size() > 0) {
                sb.append("   包含歌曲：");
                List<String> songNames = new ArrayList<>();
                for (JsonNode song : songs) {
                    songNames.add(song.get("name").asText());
                }
                sb.append(String.join("、", songNames)).append("\n");
            }
            sb.append("\n");
        }
        
        return sb.toString().trim();
    }

    /**
     * 曲库歌曲槽位匹配工具
     */
    @Tool(description = "通过指定元数据字段匹配曲库歌曲。可用于按歌名、艺术家、专辑、曲风、语种、年代等条件精确查找歌曲")
    public String songSlotMatch(
            @ToolParam(description = "歌名列表") List<String> songName,
            @ToolParam(description = "歌名关系，取值：or(或，默认值)/and(与)/not(非)") RelationType songNameBool,
            @ToolParam(description = "艺术家列表") List<String> artist,
            @ToolParam(description = "艺术家关系，取值：or(或，默认值)/and(与，合唱场景)/not(非)") RelationType artistBool,
            @ToolParam(description = "专辑列表") List<String> album,
            @ToolParam(description = "专辑关系，取值：or(或，默认值)/not(非)") RelationType albumBool,
            @ToolParam(description = "曲风列表") List<String> style,
            @ToolParam(description = "曲风关系，取值：or(或，默认值)/not(非)") RelationType styleBool,
            @ToolParam(description = "语种列表") List<String> language,
            @ToolParam(description = "语种关系，取值：or(或，默认值)/not(非)") RelationType languageBool,
            @ToolParam(description = "年代列表，如：['2003', '1990s']") List<String> years) {
        
        logger.info("SongSlotMatch: songName={}, artist={}, album={}, style={}, language={}, years={}",
                songName, artist, album, style, language, years);
        
        List<JsonNode> allSongs = mockDataService.getAllSongs();
        List<JsonNode> matchedSongs = new ArrayList<>();
        
        for (JsonNode song : allSongs) {
            boolean matches = true;
            
            // 匹配歌名
            if (matches && songName != null && !songName.isEmpty()) {
                matches = matchesField(song, "name", songName, songNameBool);
            }
            
            // 匹配艺术家
            if (matches && artist != null && !artist.isEmpty()) {
                matches = matchesArtist(song, artist, artistBool);
            }
            
            // 匹配专辑
            if (matches && album != null && !album.isEmpty()) {
                matches = matchesField(song, "album", album, albumBool);
            }
            
            // 匹配曲风
            if (matches && style != null && !style.isEmpty()) {
                matches = matchesArrayField(song, "style", style, styleBool);
            }
            
            // 匹配语种
            if (matches && language != null && !language.isEmpty()) {
                matches = matchesArrayField(song, "language", language, languageBool);
            }
            
            // 匹配年代
            if (matches && years != null && !years.isEmpty()) {
                matches = matchesYears(song, years);
            }
            
            if (matches) {
                matchedSongs.add(song);
            }
        }
        
        return formatSongList(matchedSongs, "槽位匹配结果");
    }

    /**
     * 个性化推荐工具
     */
    @Tool(description = "根据用户的个性化喜好推荐歌曲。适用于用户没有明确需求时，或要求根据个人喜好推荐时")
    public String personalizedRecommend() {
        logger.info("PersonalizedRecommend");
        
        // 从最近播放中推荐相似歌曲，或返回热门歌曲
        List<JsonNode> allSongs = mockDataService.getAllSongs();
        
        // 简单实现：返回前5首作为推荐
        List<JsonNode> recommendations = allSongs.size() > 5 ? allSongs.subList(0, 5) : allSongs;
        
        return formatSongList(recommendations, "为您推荐");
    }

    /**
     * 榜单搜索工具
     */
    @Tool(description = "搜索曲库榜单。支持新歌榜、热歌榜、美国流行榜、英国流行榜、日本流行榜、韩国流行榜等")
    public String topMusicChart(
            @ToolParam(description = "榜单类型，如：新歌榜、热歌榜、美国流行榜、韩国流行榜") String chartType,
            @ToolParam(description = "榜单月份，格式：202512（代表2025年12月榜单）") String date) {
        
        logger.info("TopMusicChart: chartType={}, date={}", chartType, date);
        
        if (chartType == null || chartType.isEmpty()) {
            return "请指定榜单类型";
        }
        
        if (date == null || date.isEmpty()) {
            date = "202502"; // 默认使用2025年2月
        }
        
        List<JsonNode> songs = mockDataService.getChartSongs(chartType, date);
        
        if (songs.isEmpty()) {
            return "未找到 " + chartType + " " + date + " 的榜单数据";
        }
        
        return formatSongList(songs, chartType + " " + date);
    }

    /**
     * 播放控制工具
     */
    @Tool(description = "控制音乐播放（播放、暂停）。支持立即执行或延迟执行")
    public String playbackControl(
            @ToolParam(description = "播控类型，取值：play(播放)/pause(暂停)") PlaybackType playbackType,
            @ToolParam(description = "歌曲曲库ID，playbackType为play时必传") String contentId,
            @ToolParam(description = "延迟执行时间（秒），0表示立即执行") Integer time) {
        
        logger.info("PlaybackControl: playbackType={}, contentId={}, time={}", playbackType, contentId, time);
        
        if (playbackType == null) {
            return "{\"result\":\"失败\", \"message\":\"请指定播控类型：play 或 pause\"}";
        }
        
        int delay = time != null ? time : 0;
        
        if (playbackType == PlaybackType.play) {
            if (contentId == null || contentId.isEmpty()) {
                return "{\"result\":\"失败\", \"message\":\"播放歌曲时必须提供 contentId\"}";
            }
            
            if (delay > 0) {
                return "{\"result\":\"成功\", \"message\":\"" + delay + "秒后开始播放歌曲 " + contentId + "\"}";
            } else {
                return "{\"result\":\"成功\", \"message\":\"开始播放歌曲 " + contentId + "\"}";
            }
        } else {
            // pause
            if (delay > 0) {
                return "{\"result\":\"成功\", \"message\":\"" + delay + "秒后暂停播放\"}";
            } else {
                return "{\"result\":\"成功\", \"message\":\"已暂停播放\"}";
            }
        }
    }

    /**
     * 用户资产获取工具
     */
    @Tool(description = "获取用户的音乐资产信息，包括最近播放历史和收藏歌曲")
    public String userAsset(
            @ToolParam(description = "资产类型") AssetType assetType) {
        
        logger.info("UserAsset: assetType={}", assetType);
        
        if (assetType == null) {
            return "请指定资产类型：play(最近播放) 或 favorites(收藏歌曲)";
        }
        
        switch (assetType) {
            case play:
                List<JsonNode> recentPlays = mockDataService.getRecentPlays();
                return formatUserAsset(recentPlays, "最近播放");
            case favorites:
                List<JsonNode> favorites = mockDataService.getFavorites();
                return formatUserAsset(favorites, "收藏歌曲");
            default:
                return "请指定资产类型：play(最近播放) 或 favorites(收藏歌曲)";
        }
    }

    // ==================== 辅助方法 ====================

    private String formatSongList(List<JsonNode> songs, String title) {
        if (songs == null || songs.isEmpty()) {
            return "未找到相关歌曲";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append(title).append("（").append(songs.size()).append("首）：\n\n");
        
        for (int i = 0; i < songs.size(); i++) {
            JsonNode song = songs.get(i);
            sb.append(i + 1).append(". ").append(song.get("name").asText()).append("\n");
            
            JsonNode artists = song.get("artist");
            if (artists != null && artists.size() > 0) {
                List<String> artistNames = new ArrayList<>();
                for (JsonNode artist : artists) {
                    artistNames.add(artist.asText());
                }
                sb.append("   歌手：").append(String.join("、", artistNames)).append("\n");
            }
            
            sb.append("   专辑：").append(song.get("album").asText()).append("\n");
            
            JsonNode styles = song.get("style");
            if (styles != null && styles.size() > 0) {
                List<String> styleNames = new ArrayList<>();
                for (JsonNode style : styles) {
                    styleNames.add(style.asText());
                }
                sb.append("   曲风：").append(String.join("、", styleNames)).append("\n");
            }
            
            JsonNode years = song.get("years");
            if (years != null && years.size() > 0) {
                sb.append("   年代：").append(years.get(0).asText()).append("\n");
            }
            
            sb.append("   ID：").append(song.get("contentId").asText()).append("\n\n");
        }
        
        return sb.toString().trim();
    }

    private String formatUserAsset(List<JsonNode> assets, String title) {
        if (assets == null || assets.isEmpty()) {
            return "暂无" + title + "记录";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append(title).append("（").append(assets.size()).append("首）：\n\n");
        
        for (int i = 0; i < assets.size(); i++) {
            JsonNode asset = assets.get(i);
            sb.append(i + 1).append(". ").append(asset.get("name").asText()).append("\n");
            
            JsonNode artists = asset.get("artist");
            if (artists != null && artists.size() > 0) {
                List<String> artistNames = new ArrayList<>();
                for (JsonNode artist : artists) {
                    artistNames.add(artist.asText());
                }
                sb.append("   歌手：").append(String.join("、", artistNames)).append("\n");
            }
            
            JsonNode date = asset.get("date");
            if (date != null) {
                sb.append("   时间：").append(date.asText()).append("\n");
            }
            
            sb.append("   ID：").append(asset.get("contentId").asText()).append("\n\n");
        }
        
        return sb.toString().trim();
    }

    // 槽位匹配辅助方法
    private boolean matchesField(JsonNode song, String field, List<String> values, RelationType relation) {
        String fieldValue = song.get(field).asText("").toLowerCase();
        
        // 默认为 or 关系
        if (relation == null) {
            relation = RelationType.or;
        }
        
        for (String value : values) {
            boolean match = fieldValue.contains(value.toLowerCase());
            switch (relation) {
                case not:
                    if (match) return false; // not: 任何一个匹配就返回false
                    break;
                case or:
                    if (match) return true;  // or: 任何一个匹配就返回true
                    break;
                case and:
                    // and 对于单字段就是必须都包含，这里简化为全部匹配
                    continue;
            }
        }
        
        return relation == RelationType.not; // not时全部不匹配返回true，or时全部不匹配返回false
    }

    private boolean matchesArtist(JsonNode song, List<String> artists, RelationType relation) {
        JsonNode songArtists = song.get("artist");
        if (songArtists == null || songArtists.size() == 0) return false;
        
        // 默认为 or 关系
        if (relation == null) {
            relation = RelationType.or;
        }
        
        switch (relation) {
            case and:
                // and: 所有查询的艺术家都要在歌曲艺术家中
                for (String artist : artists) {
                    boolean found = false;
                    for (JsonNode songArtist : songArtists) {
                        if (songArtist.asText("").toLowerCase().contains(artist.toLowerCase())) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) return false;
                }
                return true;
            case not:
                // not: 所有查询的艺术家都不应在歌曲艺术家中
                for (String artist : artists) {
                    for (JsonNode songArtist : songArtists) {
                        if (songArtist.asText("").toLowerCase().contains(artist.toLowerCase())) {
                            return false;
                        }
                    }
                }
                return true;
            case or:
            default:
                // or: 任何一个查询的艺术家匹配即可
                for (String artist : artists) {
                    for (JsonNode songArtist : songArtists) {
                        if (songArtist.asText("").toLowerCase().contains(artist.toLowerCase())) {
                            return true;
                        }
                    }
                }
                return false;
        }
    }

    private boolean matchesArrayField(JsonNode song, String field, List<String> values, RelationType relation) {
        JsonNode fieldArray = song.get(field);
        if (fieldArray == null || fieldArray.size() == 0) return false;
        
        // 默认为 or 关系
        if (relation == null) {
            relation = RelationType.or;
        }
        
        for (String value : values) {
            String lowerValue = value.toLowerCase();
            for (JsonNode item : fieldArray) {
                boolean match = item.asText("").toLowerCase().contains(lowerValue);
                switch (relation) {
                    case not:
                        if (match) return false;
                        break;
                    case or:
                        if (match) return true;
                        break;
                    case and:
                        continue;
                }
            }
        }
        
        return relation == RelationType.not;
    }

    private boolean matchesYears(JsonNode song, List<String> years) {
        JsonNode songYears = song.get("years");
        if (songYears == null || songYears.size() == 0) return false;
        
        for (String year : years) {
            for (JsonNode songYear : songYears) {
                String songYearStr = songYear.asText("");
                // 支持 2003 或 1990s 格式
                if (year.endsWith("s")) {
                    // 年代匹配，如 1990s 匹配 1990-1999
                    int decade = Integer.parseInt(year.substring(0, year.length() - 1));
                    try {
                        int songYearInt = Integer.parseInt(songYearStr);
                        if (songYearInt >= decade && songYearInt < decade + 10) {
                            return true;
                        }
                    } catch (NumberFormatException e) {
                        // 忽略非数字
                    }
                } else {
                    // 精确匹配
                    if (songYearStr.equals(year)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
