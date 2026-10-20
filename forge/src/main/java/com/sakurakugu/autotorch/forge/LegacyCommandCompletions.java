package com.sakurakugu.autotorch.forge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.sakurakugu.autotorch.config.ConfigDefinitions.BooleanValue;
import com.sakurakugu.autotorch.config.ConfigDefinitions.IntValue;
import com.sakurakugu.autotorch.config.ConfigDefinitions.ServerCategory;
import com.sakurakugu.autotorch.config.ConfigDefinitions.Value;
import com.sakurakugu.autotorch.server.ServerConfig;

/**
 * 服务端配置命令的补全候选。
 * 客户端命令与服务端命令在 1.7.10 中各自实现补全，这里集中维护键名、动作与范围的候选列表。
 */
final class LegacyCommandCompletions {
    private static final List<String> SERVER_CONFIG_ACTIONS =
            Arrays.asList("gui", "get", "set", "defaults", "reset");

    private LegacyCommandCompletions() {
    }

    /** 按当前输入过滤候选，比较时忽略大小写。 */
    static List<String> matching(String input, String... candidates) {
        return matching(input, Arrays.asList(candidates));
    }

    static List<String> matching(String input, Collection<String> candidates) {
        String prefix = input == null ? "" : input.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(prefix) && !matches.contains(candidate)) {
                matches.add(candidate);
            }
        }
        return matches;
    }

    /** 服务端配置命令的补全：/autotorch serverconfig <动作> [键] [值]。 */
    static List<String> serverConfig(String[] arguments) {
        if (arguments.length < 2) return matching("", SERVER_CONFIG_ACTIONS);
        String action = arguments[1];
        if (arguments.length == 2) return matching(action, SERVER_CONFIG_ACTIONS);
        if (arguments.length == 3) {
            if ("get".equals(action) || "set".equals(action)) return matching(arguments[2], keys());
            if ("reset".equals(action)) return matching(arguments[2], resetScopes());
            return Collections.emptyList();
        }
        if (arguments.length == 4 && "set".equals(action)) {
            Value definition = ServerConfig.definition(arguments[2]);
            if (definition instanceof BooleanValue) {
                return matching(arguments[3], "true", "false");
            }
            if (definition instanceof IntValue) {
                IntValue intValue = (IntValue) definition;
                return matching(arguments[3], String.valueOf(intValue.minValue()),
                        String.valueOf(intValue.maxValue()));
            }
        }
        return Collections.emptyList();
    }

    /** 全部服务端配置键。 */
    static List<String> keys() {
        List<String> keys = new ArrayList<>();
        for (Value definition : ServerConfig.definitions()) keys.add(definition.key());
        return keys;
    }

    /** reset 可用的范围：分组枚举名、配置键与 TOML 配置节前缀。 */
    static List<String> resetScopes() {
        Set<String> scopes = new LinkedHashSet<>();
        for (ServerCategory category : ServerCategory.values()) scopes.add(category.name());
        for (Value definition : ServerConfig.definitions()) {
            scopes.add(definition.key());
            scopes.add(definition.key().split("\\.", 2)[0]);
        }
        return new ArrayList<>(scopes);
    }
}
