package com.react.agentdemo.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.react.agentdemo.model.RelationType;
import com.react.agentdemo.service.MockDataService;
import com.react.agentdemo.tool.ToolContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 智能助手工具函数集合
 * 基于 Function 接口实现，支持动态工具配置
 * 
 * @author Kimi
 */
@Component
public class AgentToolFunctions {

    private static final Logger logger = LoggerFactory.getLogger(AgentToolFunctions.class);

    private final LocationTool locationTool;
    private final WeatherTool weatherTool;
    private final MockDataService mockDataService;

    public AgentToolFunctions(LocationTool locationTool, WeatherTool weatherTool, MockDataService mockDataService) {
        this.locationTool = locationTool;
        this.weatherTool = weatherTool;
        this.mockDataService = mockDataService;
    }

    // ==================== 原有工具 ====================

    /**
     * 获取用户当前所在城市
     */
    public Function<Void, String> getUserLocation() {
        return (unused) -> {
            String sessionId = ToolContextHolder.getSessionId();
            if (sessionId == null) {
                sessionId = "default";
                logger.warn("未找到 SessionId，使用默认值");
            }
            
            logger.info("获取用户位置，SessionId: {}", sessionId);
            
            Map<String, Object> params = Map.of("sessionId", sessionId);
            Object result = locationTool.execute(params, sessionId);
            return result != null ? result.toString() : "未知";
        };
    }

    /**
     * 查询指定城市的天气
     */
    public Function<GetWeatherRequest, String> getWeather() {
        return (request) -> {
            String city = request.city() != null ? request.city() : "北京";
            Map<String, Object> params = Map.of("city", city);
            Object result = weatherTool.execute(params, "default");
            return result != null ? result.toString() : "查询失败";
        };
    }

    public record GetWeatherRequest(String city) {}

    // ==================== 新工具 ====================

    /**
     * 联网知识搜索工具
     */
    public Function<WebSearchRequest, String> webSearch() {
        return (request) -> {
            logger.info("WebSearch: keywords={}", request.keywords());
            
            if (request.keywords() == null || request.keywords().isEmpty()) {
                return "请提供搜索关键词";
            }
            
            List<String> limitedKeywords = request.keywords().size() > 5 
                ? request.keywords().subList(0, 5) 
                : request.keywords();
            
            List<JsonNode> results = mockDataService.getWebSearchResults(limitedKeywords);
            
            StringBuilder sb = new StringBuilder();
            sb.append("搜索结果（").append(results.size()).append("条）：\n\n");
            
            for (int i = 0; i < results.size(); i++) {
                JsonNode result = results.get(i);
                sb.append(i + 1).append(". ").append(result.get("title").asText()).append("\n");
                sb.append("   ").append(result.get("content").asText()).append("\n\n");
            }
            
            return sb.toString().trim();
        };
    }

    public record WebSearchRequest(List<String> keywords) {}

    /**
     * 曲库歌曲搜索工具
     */
    public Function<SongSearchRequest, String> songSearch() {
        return (request) -> {
            logger.info("SongSearch: keywords={}", request.keywords());
            
            if (request.keywords() == null || request.keywords().isEmpty()) {
                return "请提供搜索关键词";
            }
            
            List<JsonNode> matchedSongs = new ArrayList<>();
            for (JsonNode song : mockDataService.getAllSongs()) {
                String songName = song.get("name").asText("").toLowerCase();
                for (String keyword : request.keywords()) {
                    if (songName.contains(keyword.toLowerCase())) {
                        matchedSongs.add(song);
                        break;
                    }
                }
            }
            
            return formatSongList(matchedSongs, "曲库歌曲搜索结果");
        };
    }

    public record SongSearchRequest(List<String> keywords) {}

    /**
     * 曲库歌单搜索工具
     */
    public Function<SongListSearchRequest, String> songListSearch() {
        return (request) -> {
            logger.info("SongListSearch: keywords={}", request.keywords());
            
            if (request.keywords() == null || request.keywords().isEmpty()) {
                return "请提供搜索关键词";
            }
            
            List<JsonNode> matchedLists = mockDataService.searchSonglists(request.keywords());
            
            return formatSongListWithDetails(matchedLists, "曲库歌单搜索结果");
        };
    }

    public record SongListSearchRequest(List<String> keywords) {}

    /**
     * 曲库歌曲槽位匹配工具
     */
    public Function<SongSlotMatchRequest, String> songSlotMatch() {
        return (request) -> {
            logger.info("SongSlotMatch: songName={}, artist={}, album={}, style={}, language={}, years={}",
                    request.songName(), request.artist(), request.album(), 
                    request.style(), request.language(), request.years());
            
            List<JsonNode> allSongs = mockDataService.getAllSongs();
            List<JsonNode> matchedSongs = new ArrayList<>();
            
            for (JsonNode song : allSongs) {
                boolean matches = true;
                
                if (matches && request.songName() != null && !request.songName().isEmpty()) {
                    matches = matchesField(song, "name", request.songName(), parseRelationType(request.songNameBool()));
                }
                
                if (matches && request.artist() != null && !request.artist().isEmpty()) {
                    matches = matchesArtist(song, request.artist(), parseRelationType(request.artistBool()));
                }
                
                if (matches && request.album() != null && !request.album().isEmpty()) {
                    matches = matchesField(song, "album", request.album(), parseRelationType(request.albumBool()));
                }
                
                if (matches && request.style() != null && !request.style().isEmpty()) {
                    matches = matchesArrayField(song, "style", request.style(), parseRelationType(request.styleBool()));
                }
                
                if (matches && request.language() != null && !request.language().isEmpty()) {
                    matches = matchesArrayField(song, "language", request.language(), parseRelationType(request.languageBool()));
                }
                
                if (matches && request.years() != null && !request.years().isEmpty()) {
                    matches = matchesYears(song, request.years());
                }
                
                if (matches) {
                    matchedSongs.add(song);
                }
            }
            
            return formatSongList(matchedSongs, "槽位匹配结果");
        };
    }

    public record SongSlotMatchRequest(
        List<String> songName,
        String songNameBool,
        List<String> artist,
        String artistBool,
        List<String> album,
        String albumBool,
        List<String> style,
        String styleBool,
        List<String> language,
        String languageBool,
        List<String> years
    ) {}

    /**
     * 个性化推荐工具
     */
    public Function<Void, String> personalizedRecommend() {
        return (unused) -> {
            logger.info("PersonalizedRecommend");
            
            List<JsonNode> allSongs = mockDataService.getAllSongs();
            List<JsonNode> recommendations = allSongs.size() > 5 
                ? allSongs.subList(0, 5) 
                : allSongs;
            
            return formatSongList(recommendations, "为您推荐");
        };
    }

    /**
     * 榜单搜索工具
     */
    public Function<TopMusicChartRequest, String> topMusicChart() {
        return (request) -> {
            logger.info("TopMusicChart: chartType={}, date={}", request.chartType(), request.date());
            
            if (request.chartType() == null || request.chartType().isEmpty()) {
                return "请指定榜单类型";
            }
            
            String dateStr = request.date() != null && !request.date().isEmpty() 
                ? request.date() 
                : "202502";
            
            List<JsonNode> chartSongs = mockDataService.getChartSongs(request.chartType(), dateStr);
            
            if (chartSongs.isEmpty()) {
                return "未找到榜单: " + request.chartType();
            }
            
            return formatSongList(chartSongs, request.chartType() + "（" + dateStr + "）");
        };
    }

    public record TopMusicChartRequest(String chartType, String date) {}

    /**
     * 播放控制工具
     */
    public Function<PlaybackControlRequest, String> playbackControl() {
        return (request) -> {
            logger.info("PlaybackControl: type={}, contentId={}, time={}", 
                    request.playbackType(), request.contentId(), request.time());
            
            if (request.playbackType() == null) {
                return "{\"result\":\"失败\", \"message\":\"请指定播控类型：play 或 pause\"}";
            }
            
            int delay = request.time() != null ? request.time() : 0;
            boolean isPlay = "play".equalsIgnoreCase(request.playbackType());
            
            if (isPlay) {
                if (request.contentId() == null || request.contentId().isEmpty()) {
                    return "{\"result\":\"失败\", \"message\":\"播放歌曲时必须提供 contentId\"}";
                }
                
                if (delay > 0) {
                    return "{\"result\":\"成功\", \"message\":\"" + delay + "秒后开始播放歌曲 " + request.contentId() + "\"}";
                } else {
                    return "{\"result\":\"成功\", \"message\":\"开始播放歌曲 " + request.contentId() + "\"}";
                }
            } else {
                if (delay > 0) {
                    return "{\"result\":\"成功\", \"message\":\"" + delay + "秒后暂停播放\"}";
                } else {
                    return "{\"result\":\"成功\", \"message\":\"已暂停播放\"}";
                }
            }
        };
    }

    public record PlaybackControlRequest(
        String playbackType,
        String contentId,
        Integer time
    ) {}

    /**
     * 用户资产获取工具
     */
    public Function<UserAssetRequest, String> userAsset() {
        return (request) -> {
            logger.info("UserAsset: assetType={}", request.assetType());
            
            if (request.assetType() == null) {
                return "请指定资产类型：play(最近播放) 或 favorites(收藏歌曲)";
            }
            
            if ("play".equalsIgnoreCase(request.assetType())) {
                return formatUserAsset(mockDataService.getRecentPlays(), "最近播放");
            } else if ("favorites".equalsIgnoreCase(request.assetType())) {
                return formatUserAsset(mockDataService.getFavorites(), "收藏歌曲");
            } else {
                return "请指定资产类型：play(最近播放) 或 favorites(收藏歌曲)";
            }
        };
    }

    public record UserAssetRequest(String assetType) {}

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
                StringBuilder artistStr = new StringBuilder();
                for (JsonNode artist : artists) {
                    if (artistStr.length() > 0) artistStr.append(", ");
                    artistStr.append(artist.asText());
                }
                sb.append("   艺术家：").append(artistStr).append("\n");
            }
            
            sb.append("   ID：").append(song.get("contentId").asText()).append("\n\n");
        }
        
        return sb.toString().trim();
    }

    private String formatSongListWithDetails(List<JsonNode> songs, String title) {
        if (songs == null || songs.isEmpty()) {
            return "未找到相关歌单";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append(title).append("（").append(songs.size()).append("个）：\n\n");
        
        for (int i = 0; i < songs.size(); i++) {
            JsonNode songList = songs.get(i);
            sb.append(i + 1).append(". ").append(songList.get("name").asText()).append("\n");
            sb.append("   描述：").append(songList.get("description").asText()).append("\n");
            sb.append("   歌曲数：").append(songList.get("songCount").asText()).append("\n");
            sb.append("   ID：").append(songList.get("id").asText()).append("\n\n");
        }
        
        return sb.toString().trim();
    }

    private String formatUserAsset(List<JsonNode> assets, String title) {
        if (assets == null || assets.isEmpty()) {
            return "暂无" + title;
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append(title).append("（").append(assets.size()).append("首）：\n\n");
        
        for (int i = 0; i < assets.size(); i++) {
            JsonNode asset = assets.get(i);
            sb.append(i + 1).append(". ").append(asset.get("name").asText()).append("\n");
            
            JsonNode artists = asset.get("artist");
            if (artists != null && artists.size() > 0) {
                StringBuilder artistStr = new StringBuilder();
                for (JsonNode artist : artists) {
                    if (artistStr.length() > 0) artistStr.append(", ");
                    artistStr.append(artist.asText());
                }
                sb.append("   艺术家：").append(artistStr).append("\n");
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
        
        if (relation == null) {
            relation = RelationType.or;
        }
        
        for (String value : values) {
            boolean match = fieldValue.contains(value.toLowerCase());
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
        
        return relation == RelationType.not;
    }

    private boolean matchesArtist(JsonNode song, List<String> artists, RelationType relation) {
        JsonNode songArtists = song.get("artist");
        if (songArtists == null || songArtists.size() == 0) return false;
        
        if (relation == null) {
            relation = RelationType.or;
        }
        
        switch (relation) {
            case and:
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
        String songYear = song.get("year").asText("");
        for (String year : years) {
            if (songYear.equals(year) || songYear.startsWith(year.replace("s", ""))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 将字符串解析为 RelationType 枚举
     */
    private RelationType parseRelationType(String value) {
        if (value == null) {
            return RelationType.or;
        }
        try {
            return RelationType.valueOf(value.toLowerCase());
        } catch (IllegalArgumentException e) {
            return RelationType.or; // 默认值
        }
    }
}
