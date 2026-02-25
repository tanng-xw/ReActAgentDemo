package com.react.agentdemo.config;

import com.react.agentdemo.service.MockDataService;
import com.react.agentdemo.tools.LocationTool;
import com.react.agentdemo.tools.WeatherTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * 工具回调工厂
 * 将现有工具转换为 Spring AI Alibaba 的 ToolCallback 格式
 * 
 * @author Kimi
 */
@Configuration
public class ToolCallbackFactory {

    private static final Logger logger = LoggerFactory.getLogger(ToolCallbackFactory.class);

    private final LocationTool locationTool;
    private final WeatherTool weatherTool;
    private final MockDataService mockDataService;
    private final ToolDescriptionProperties toolProperties;

    public ToolCallbackFactory(LocationTool locationTool, WeatherTool weatherTool, 
                               MockDataService mockDataService,
                               ToolDescriptionProperties toolProperties) {
        this.locationTool = locationTool;
        this.weatherTool = weatherTool;
        this.mockDataService = mockDataService;
        this.toolProperties = toolProperties;
    }

    /**
     * 创建所有工具回调
     * 
     * @return 工具回调列表
     */
    @Bean
    public List<ToolCallback> toolCallbacks() {
        logger.info("正在创建工具回调...");
        
        return List.of(
                createGetUserLocationTool(),
                createGetWeatherTool(),
                createWebSearchTool(),
                createSongSearchTool(),
                createSongListSearchTool(),
                createSongSlotMatchTool(),
                createPersonalizedRecommendTool(),
                createTopMusicChartTool(),
                createPlaybackControlTool(),
                createUserAssetTool()
        );
    }

    /**
     * 获取用户位置工具
     */
    private ToolCallback createGetUserLocationTool() {
        ToolDescriptionProperties.ToolDescription desc = toolProperties.getTools().get("getUserLocation");
        String description = desc != null ? desc.getDescription() : "查询用户当前所在的城市位置";
        
        return FunctionToolCallback.builder("getUserLocation", 
                        (BiFunction<GetUserLocationRequest, ToolContext, String>) (request, context) -> {
                    String sessionId = context != null && context.getContext() != null 
                            ? (String) context.getContext().get("thread_id") 
                            : "default";
                    
                    logger.info("获取用户位置，SessionId: {}", sessionId);
                    
                    Map<String, Object> params = Map.of("sessionId", sessionId);
                    Object result = locationTool.execute(params, sessionId);
                    return result != null ? result.toString() : "未知";
                })
                .description(description)
                .inputType(GetUserLocationRequest.class)
                .build();
    }

    public record GetUserLocationRequest() {}

    /**
     * 获取天气工具
     */
    private ToolCallback createGetWeatherTool() {
        ToolDescriptionProperties.ToolDescription desc = toolProperties.getTools().get("getWeather");
        String description = desc != null ? desc.getDescription() : "查询指定城市的天气信息";
        
        return FunctionToolCallback.builder("getWeather",
                        (Function<GetWeatherRequest, String>) request -> {
                    String city = request.city() != null ? request.city() : "北京";
                    Map<String, Object> params = Map.of("city", city);
                    Object result = weatherTool.execute(params, "default");
                    return result != null ? result.toString() : "查询失败";
                })
                .description(description)
                .inputType(GetWeatherRequest.class)
                .build();
    }

    public record GetWeatherRequest(
            @ToolParam(description = "城市名称，如：北京、上海、杭州") String city
    ) {}

    /**
     * 联网搜索工具
     */
    private ToolCallback createWebSearchTool() {
        ToolDescriptionProperties.ToolDescription desc = toolProperties.getTools().get("webSearch");
        String description = desc != null ? desc.getDescription() : "联网搜索相关知识";
        
        return FunctionToolCallback.builder("webSearch",
                        (Function<WebSearchRequest, String>) request -> {
                    logger.info("WebSearch: keywords={}", request.keywords());
                    
                    if (request.keywords() == null || request.keywords().isEmpty()) {
                        return "请提供搜索关键词";
                    }
                    
                    List<String> limitedKeywords = request.keywords().size() > 5 
                            ? request.keywords().subList(0, 5) 
                            : request.keywords();
                    
                    var results = mockDataService.getWebSearchResults(limitedKeywords);
                    
                    StringBuilder sb = new StringBuilder();
                    sb.append("搜索结果（").append(results.size()).append("条）：\n\n");
                    
                    for (int i = 0; i < results.size(); i++) {
                        var result = results.get(i);
                        sb.append(i + 1).append(". ").append(result.get("title").asText()).append("\n");
                        sb.append("   ").append(result.get("content").asText()).append("\n\n");
                    }
                    
                    return sb.toString().trim();
                })
                .description(description)
                .inputType(WebSearchRequest.class)
                .build();
    }

    public record WebSearchRequest(
            @ToolParam(description = "搜索关键词列表，最多5个") List<String> keywords
    ) {}

    /**
     * 歌曲搜索工具
     */
    private ToolCallback createSongSearchTool() {
        ToolDescriptionProperties.ToolDescription desc = toolProperties.getTools().get("songSearch");
        String description = desc != null ? desc.getDescription() : "搜索曲库歌曲";
        
        return FunctionToolCallback.builder("songSearch",
                        (Function<SongSearchRequest, String>) request -> {
                    logger.info("SongSearch: keywords={}", request.keywords());
                    
                    if (request.keywords() == null || request.keywords().isEmpty()) {
                        return "请提供搜索关键词";
                    }
                    
                    var matchedSongs = mockDataService.searchSongs(request.keywords());
                    return formatSongList(matchedSongs, "曲库歌曲搜索结果");
                })
                .description(description)
                .inputType(SongSearchRequest.class)
                .build();
    }

    public record SongSearchRequest(
            @ToolParam(description = "搜索关键词列表") List<String> keywords
    ) {}

    /**
     * 歌单搜索工具
     */
    private ToolCallback createSongListSearchTool() {
        ToolDescriptionProperties.ToolDescription desc = toolProperties.getTools().get("songListSearch");
        String description = desc != null ? desc.getDescription() : "搜索曲库歌单";
        
        return FunctionToolCallback.builder("songListSearch",
                        (Function<SongListSearchRequest, String>) request -> {
                    logger.info("SongListSearch: keywords={}", request.keywords());
                    
                    if (request.keywords() == null || request.keywords().isEmpty()) {
                        return "请提供搜索关键词";
                    }
                    
                    var matchedLists = mockDataService.searchSonglists(request.keywords());
                    return formatSongListWithDetails(matchedLists, "曲库歌单搜索结果");
                })
                .description(description)
                .inputType(SongListSearchRequest.class)
                .build();
    }

    public record SongListSearchRequest(
            @ToolParam(description = "搜索关键词列表") List<String> keywords
    ) {}

    /**
     * 歌曲槽位匹配工具
     */
    private ToolCallback createSongSlotMatchTool() {
        ToolDescriptionProperties.ToolDescription desc = toolProperties.getTools().get("songSlotMatch");
        String description = desc != null ? desc.getDescription() : "通过指定元数据字段匹配曲库歌曲";
        
        return FunctionToolCallback.builder("songSlotMatch",
                        (Function<SongSlotMatchRequest, String>) request -> {
                    logger.info("SongSlotMatch: songName={}, artist={}, style={}",
                            request.songName(), request.artist(), request.style());
                    
                    var matchedSongs = mockDataService.slotMatchSongs(
                            request.songName(), request.songNameBool(),
                            request.artist(), request.artistBool(), request.artistSex(),
                            request.album(), request.albumBool(),
                            request.style(), request.styleBool(),
                            request.language(), request.languageBool(),
                            request.properties(), request.years()
                    );
                    
                    return formatSongList(matchedSongs, "槽位匹配结果");
                })
                .description(description)
                .inputType(SongSlotMatchRequest.class)
                .build();
    }

    public record SongSlotMatchRequest(
            @ToolParam(description = "歌名列表") List<String> songName,
            @ToolParam(description = "歌名关系：or/and/not") String songNameBool,
            @ToolParam(description = "艺术家列表") List<String> artist,
            @ToolParam(description = "艺术家关系：or/and/not") String artistBool,
            @ToolParam(description = "艺术家性别：male/female/group") String artistSex,
            @ToolParam(description = "专辑列表") List<String> album,
            @ToolParam(description = "专辑关系：or/not") String albumBool,
            @ToolParam(description = "曲风列表") List<String> style,
            @ToolParam(description = "曲风关系：or/not") String styleBool,
            @ToolParam(description = "语种列表") List<String> language,
            @ToolParam(description = "语种关系：or/and/not") String languageBool,
            @ToolParam(description = "音乐属性列表") List<String> properties,
            @ToolParam(description = "年代列表") List<String> years
    ) {}

    /**
     * 个性化推荐工具
     */
    private ToolCallback createPersonalizedRecommendTool() {
        ToolDescriptionProperties.ToolDescription desc = toolProperties.getTools().get("personalizedRecommend");
        String description = desc != null ? desc.getDescription() : "根据用户的个性化喜好推荐歌曲";
        
        return FunctionToolCallback.builder("personalizedRecommend",
                        (Function<Void, String>) unused -> {
                    logger.info("PersonalizedRecommend");
                    
                    var allSongs = mockDataService.getAllSongs();
                    var recommendations = allSongs.size() > 5 
                            ? allSongs.subList(0, 5) 
                            : allSongs;
                    
                    return formatSongList(recommendations, "为您推荐");
                })
                .description(description)
                .inputType(Void.class)
                .build();
    }

    /**
     * 榜单查询工具
     */
    private ToolCallback createTopMusicChartTool() {
        ToolDescriptionProperties.ToolDescription desc = toolProperties.getTools().get("topMusicChart");
        String description = desc != null ? desc.getDescription() : "搜索曲库榜单";
        
        return FunctionToolCallback.builder("topMusicChart",
                        (Function<TopMusicChartRequest, String>) request -> {
                    logger.info("TopMusicChart: chartType={}, date={}", request.chartType(), request.date());
                    
                    if (request.chartType() == null || request.chartType().isEmpty()) {
                        return "请指定榜单类型";
                    }
                    
                    String dateStr = request.date() != null && !request.date().isEmpty() 
                            ? request.date() 
                            : "202502";
                    
                    var chartSongs = mockDataService.getChartSongs(request.chartType(), dateStr);
                    
                    if (chartSongs.isEmpty()) {
                        return "未找到榜单: " + request.chartType();
                    }
                    
                    return formatSongList(chartSongs, request.chartType() + "（" + dateStr + "）");
                })
                .description(description)
                .inputType(TopMusicChartRequest.class)
                .build();
    }

    public record TopMusicChartRequest(
            @ToolParam(description = "榜单类型") String chartType,
            @ToolParam(description = "榜单月份，格式：202512") String date
    ) {}

    /**
     * 播放控制工具
     */
    private ToolCallback createPlaybackControlTool() {
        ToolDescriptionProperties.ToolDescription desc = toolProperties.getTools().get("playbackControl");
        String description = desc != null ? desc.getDescription() : "控制歌曲播放状态";
        
        return FunctionToolCallback.builder("playbackControl",
                        (Function<PlaybackControlRequest, String>) request -> {
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
                })
                .description(description)
                .inputType(PlaybackControlRequest.class)
                .build();
    }

    public record PlaybackControlRequest(
            @ToolParam(description = "播控类型：play/pause") String playbackType,
            @ToolParam(description = "歌曲ID，播放时必须提供") String contentId,
            @ToolParam(description = "延迟时间（秒）") Integer time
    ) {}

    /**
     * 用户资产工具
     */
    private ToolCallback createUserAssetTool() {
        ToolDescriptionProperties.ToolDescription desc = toolProperties.getTools().get("userAsset");
        String description = desc != null ? desc.getDescription() : "获取用户的音乐资产信息";
        
        return FunctionToolCallback.builder("userAsset",
                        (Function<UserAssetRequest, String>) request -> {
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
                })
                .description(description)
                .inputType(UserAssetRequest.class)
                .build();
    }

    public record UserAssetRequest(
            @ToolParam(description = "资产类型：play/favorites") String assetType
    ) {}

    // ==================== 辅助方法 ====================

    private String formatSongList(List<com.fasterxml.jackson.databind.JsonNode> songs, String title) {
        if (songs == null || songs.isEmpty()) {
            return "未找到相关歌曲";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append(title).append("（").append(songs.size()).append("首）：\n\n");
        
        for (int i = 0; i < songs.size(); i++) {
            var song = songs.get(i);
            sb.append(i + 1).append(". ").append(song.get("name").asText()).append("\n");
            
            var artists = song.get("artist");
            if (artists != null && artists.size() > 0) {
                StringBuilder artistStr = new StringBuilder();
                for (var artist : artists) {
                    if (artistStr.length() > 0) artistStr.append(", ");
                    artistStr.append(artist.asText());
                }
                sb.append("   艺术家：").append(artistStr).append("\n");
            }
            
            sb.append("   ID：").append(song.get("contentId").asText()).append("\n\n");
        }
        
        return sb.toString().trim();
    }

    private String formatSongListWithDetails(List<com.fasterxml.jackson.databind.JsonNode> songs, String title) {
        if (songs == null || songs.isEmpty()) {
            return "未找到相关歌单";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append(title).append("（").append(songs.size()).append("个）：\n\n");
        
        for (int i = 0; i < songs.size(); i++) {
            var songList = songs.get(i);
            sb.append(i + 1).append(". ").append(songList.get("name").asText()).append("\n");
            sb.append("   描述：").append(songList.get("description").asText()).append("\n");
            sb.append("   歌曲数：").append(songList.get("songCount").asText()).append("\n");
            sb.append("   ID：").append(songList.get("id").asText()).append("\n\n");
        }
        
        return sb.toString().trim();
    }

    private String formatUserAsset(List<com.fasterxml.jackson.databind.JsonNode> assets, String title) {
        if (assets == null || assets.isEmpty()) {
            return "暂无" + title;
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append(title).append("（").append(assets.size()).append("首）：\n\n");
        
        for (int i = 0; i < assets.size(); i++) {
            var asset = assets.get(i);
            sb.append(i + 1).append(". ").append(asset.get("name").asText()).append("\n");
            
            var artists = asset.get("artist");
            if (artists != null && artists.size() > 0) {
                StringBuilder artistStr = new StringBuilder();
                for (var artist : artists) {
                    if (artistStr.length() > 0) artistStr.append(", ");
                    artistStr.append(artist.asText());
                }
                sb.append("   艺术家：").append(artistStr).append("\n");
            }
            
            var date = asset.get("date");
            if (date != null) {
                sb.append("   时间：").append(date.asText()).append("\n");
            }
            
            sb.append("   ID：").append(asset.get("contentId").asText()).append("\n\n");
        }
        
        return sb.toString().trim();
    }
}
