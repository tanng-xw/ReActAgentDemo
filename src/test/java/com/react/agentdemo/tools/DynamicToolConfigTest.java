package com.react.agentdemo.tools;

import com.react.agentdemo.config.ToolDescriptionProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 动态工具配置测试
 * 验证工具描述可以从配置文件加载
 */
@SpringBootTest
@ActiveProfiles("test")
public class DynamicToolConfigTest {

    @Autowired
    private ToolDescriptionProperties toolProperties;

    @Test
    void shouldLoadToolDescriptionFromConfig() {
        // 验证配置已加载
        assertNotNull(toolProperties.getTools(), "工具配置应已加载");
        assertFalse(toolProperties.getTools().isEmpty(), "工具配置不应为空");
        
        // 验证用户资产工具配置
        ToolDescriptionProperties.ToolDescription userAssetDesc = 
            toolProperties.getToolDescription("userAsset");
        assertNotNull(userAssetDesc, "userAsset 工具描述应存在");
        assertNotNull(userAssetDesc.getDescription(), "工具描述不应为空");
        assertTrue(userAssetDesc.getDescription().contains("资产"), "描述应包含'资产'关键字");
        
        // 验证参数配置
        assertNotNull(userAssetDesc.getParameters(), "参数配置不应为空");
        assertTrue(userAssetDesc.getParameters().containsKey("assetType"), 
            "应包含 assetType 参数配置");
    }

    @Test
    void shouldLoadAllToolsFromConfig() {
        // 验证所有工具都有配置
        String[] expectedTools = {
            "getUserLocation", "getWeather", "webSearch", "songSearch",
            "songListSearch", "songSlotMatch", "personalizedRecommend",
            "topMusicChart", "playbackControl", "userAsset"
        };
        
        for (String toolName : expectedTools) {
            ToolDescriptionProperties.ToolDescription desc = 
                toolProperties.getToolDescription(toolName);
            assertNotNull(desc, toolName + " 应有配置");
            assertNotNull(desc.getDescription(), toolName + " 应有描述");
        }
    }

    @Test
    void shouldLoadParameterEnumsFromConfig() {
        // 验证参数枚举值可以从配置加载
        ToolDescriptionProperties.ToolDescription playbackDesc = 
            toolProperties.getToolDescription("playbackControl");
        assertNotNull(playbackDesc, "playbackControl 应有配置");
        
        // 验证参数中存在枚举定义（通过 schema 或 enumValues）
        assertTrue(playbackDesc.getParameters().containsKey("playbackType"),
            "应有 playbackType 参数");
    }

    @Test
    void shouldLoadArrayItemEnumsFromConfig() {
        // 验证数组项的枚举值可以从配置加载（如 language 的 items.enum）
        ToolDescriptionProperties.ToolDescription slotMatchDesc = 
            toolProperties.getToolDescription("songSlotMatch");
        assertNotNull(slotMatchDesc, "songSlotMatch 应有配置");
        
        // 验证 language 参数
        ToolDescriptionProperties.ParameterDefinition languageParam = 
            slotMatchDesc.getParameters().get("language");
        assertNotNull(languageParam, "应有 language 参数");
        assertEquals("array", languageParam.getType(), "language 应为数组类型");
        
        // 验证 items 中的 enum
        ToolDescriptionProperties.ItemDefinition items = languageParam.getItems();
        assertNotNull(items, "应有 items 定义");
        assertNotNull(items.getEnum(), "items 应有 enum 定义");
        assertTrue(items.getEnum().contains("中文"), "enum 应包含中文");
        assertTrue(items.getEnum().contains("英文"), "enum 应包含英文");
        assertTrue(items.getEnum().contains("粤语"), "enum 应包含粤语");
    }
}
