package com.react.agentdemo.tools;

import com.react.agentdemo.config.ToolDescriptionProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 工具参数一致性测试
 * 验证 tool-descriptions.json 中的参数与代码中 AgentToolFunctions 的参数一一对应
 */
@SpringBootTest
@ActiveProfiles("test")
public class ToolParameterConsistencyTest {

    @Autowired
    private ToolDescriptionProperties toolProperties;

    @Autowired
    private AgentToolFunctions agentToolFunctions;

    /**
     * 检查所有工具的参数一致性
     */
    @Test
    void shouldHaveConsistentParametersBetweenConfigAndCode() throws Exception {
        Map<String, ToolDescriptionProperties.ToolDescription> configTools = toolProperties.getTools();
        
        for (Map.Entry<String, ToolDescriptionProperties.ToolDescription> entry : configTools.entrySet()) {
            String toolName = entry.getKey();
            ToolDescriptionProperties.ToolDescription toolDesc = entry.getValue();
            
            // 获取配置中的参数
            Set<String> configParams = toolDesc.getParameters().keySet();
            
            // 获取代码中的参数
            Set<String> codeParams = getCodeParameters(toolName);
            
            // 比较参数
            assertEquals(configParams, codeParams, 
                "工具 '" + toolName + "' 的参数不一致！\n" +
                "配置中的参数: " + configParams + "\n" +
                "代码中的参数: " + codeParams);
        }
    }

    /**
     * 通过反射获取代码中工具的参数列表
     */
    private Set<String> getCodeParameters(String toolName) throws Exception {
        // 找到对应的方法
        Method method = findToolMethod(toolName);
        if (method == null) {
            fail("未找到工具 '" + toolName + "' 对应的方法");
            return Collections.emptySet();
        }

        // 获取返回类型 (Function<Request, String>)
        Type returnType = method.getGenericReturnType();
        if (!(returnType instanceof ParameterizedType)) {
            fail("工具 '" + toolName + "' 的返回类型不是 ParameterizedType");
            return Collections.emptySet();
        }

        ParameterizedType paramType = (ParameterizedType) returnType;
        Type[] actualTypeArgs = paramType.getActualTypeArguments();
        
        if (actualTypeArgs.length < 1) {
            fail("工具 '" + toolName + "' 的 Function 类型参数不足");
            return Collections.emptySet();
        }

        // 获取 Request 类型
        Type requestType = actualTypeArgs[0];
        
        // Void 类型表示无参数
        if (requestType == Void.class || requestType.getTypeName().equals("java.lang.Void")) {
            return Collections.emptySet();
        }

        // 获取 Request 类的 record 组件（字段）
        Class<?> requestClass;
        if (requestType instanceof Class) {
            requestClass = (Class<?>) requestType;
        } else if (requestType instanceof ParameterizedType) {
            requestClass = (Class<?>) ((ParameterizedType) requestType).getRawType();
        } else {
            fail("工具 '" + toolName + "' 的 Request 类型无法解析: " + requestType);
            return Collections.emptySet();
        }

        // 获取 record 的组件名
        RecordComponent[] components = requestClass.getRecordComponents();
        if (components == null) {
            // 不是 record 类型，可能是普通类，尝试获取字段
            return getFieldNames(requestClass);
        }

        Set<String> paramNames = new TreeSet<>();
        for (RecordComponent component : components) {
            paramNames.add(component.getName());
        }
        return paramNames;
    }

    /**
     * 找到 AgentToolFunctions 中对应工具的方法
     */
    private Method findToolMethod(String toolName) {
        // 方法名映射（如果工具名和方法名不一致）
        String methodName = toolName;
        
        for (Method method : AgentToolFunctions.class.getMethods()) {
            if (method.getName().equals(methodName) && 
                method.getReturnType() == Function.class) {
                return method;
            }
        }
        return null;
    }

    /**
     * 获取类的字段名（用于非 record 类型）
     */
    private Set<String> getFieldNames(Class<?> clazz) {
        Set<String> names = new TreeSet<>();
        java.lang.reflect.Field[] fields = clazz.getDeclaredFields();
        for (java.lang.reflect.Field field : fields) {
            names.add(field.getName());
        }
        return names;
    }

    /**
     * 打印所有工具的参数对比（用于调试）
     */
    @Test
    void printAllToolParameters() throws Exception {
        System.out.println("\n========== 工具参数一致性检查 ==========\n");
        
        Map<String, ToolDescriptionProperties.ToolDescription> configTools = toolProperties.getTools();
        
        for (String toolName : new TreeSet<>(configTools.keySet())) {
            ToolDescriptionProperties.ToolDescription toolDesc = configTools.get(toolName);
            Set<String> configParams = new TreeSet<>(toolDesc.getParameters().keySet());
            Set<String> codeParams = getCodeParameters(toolName);
            
            boolean match = configParams.equals(codeParams);
            String status = match ? "✓ 一致" : "✗ 不一致";
            
            System.out.println("工具: " + toolName + " " + status);
            System.out.println("  配置参数: " + configParams);
            System.out.println("  代码参数: " + codeParams);
            
            if (!match) {
                Set<String> onlyInConfig = new TreeSet<>(configParams);
                onlyInConfig.removeAll(codeParams);
                Set<String> onlyInCode = new TreeSet<>(codeParams);
                onlyInCode.removeAll(configParams);
                
                if (!onlyInConfig.isEmpty()) {
                    System.out.println("  ⚠ 仅在配置中: " + onlyInConfig);
                }
                if (!onlyInCode.isEmpty()) {
                    System.out.println("  ⚠ 仅在代码中: " + onlyInCode);
                }
            }
            System.out.println();
        }
        
        // 这个测试只用于打印信息，总是通过
        assertTrue(true);
    }
}
