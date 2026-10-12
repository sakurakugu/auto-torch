#!/bin/sh

# 将允许的中英文提交类型统一为用于提交标题和更新日志的中文类型。
normalize_commit_type() {
    case "$1" in
        feat|新增) printf '%s\n' "新增" ;;
        fix|修复) printf '%s\n' "修复" ;;
        docs|文档) printf '%s\n' "文档" ;;
        refactor|重构) printf '%s\n' "重构" ;;
        chore|杂项) printf '%s\n' "杂项" ;;
        style|格式|样式) printf '%s\n' "样式" ;;
        perf|性能) printf '%s\n' "性能" ;;
        test|测试) printf '%s\n' "测试" ;;
        ci|集成) printf '%s\n' "集成" ;;
        build|构建) printf '%s\n' "构建" ;;
        revert|回退) printf '%s\n' "回退" ;;
        merge|合并) printf '%s\n' "合并" ;;
        update|更新) printf '%s\n' "更新" ;;
        optimize|优化) printf '%s\n' "优化" ;;
        delete|移除|删除) printf '%s\n' "删除" ;;
        release|发布) printf '%s\n' "发布" ;;
        adapt|适配) printf '%s\n' "适配" ;;
        *) return 1 ;;
    esac
}

# 判断标准类型是否属于用户可见的更新日志内容。
is_release_note_commit_type() {
    case "$1" in
        新增|修复|文档|重构|杂项|样式|性能|测试|集成|构建|回退|合并|更新|优化|删除|适配)
            return 0
            ;;
        *)
            return 1
            ;;
    esac
}

# 输出提交钩子拒绝非法类型时使用的帮助文本。
print_allowed_commit_types() {
    printf '%s\n' \
        "  feat / 新增" \
        "  fix / 修复" \
        "  docs / 文档" \
        "  refactor / 重构" \
        "  chore / 杂项" \
        "  style / 样式" \
        "  perf / 性能" \
        "  test / 测试" \
        "  ci / 集成" \
        "  build / 构建" \
        "  revert / 回退" \
        "  merge / 合并" \
        "  update / 更新" \
        "  optimize / 优化" \
        "  delete / 删除" \
        "  release / 发布" \
        "  adapt / 适配"
}
