package com.campus.core.oplog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 操作日志的变更内容脱敏（tech/01 §5.4 第 4 条）。
 *
 * <p><b>为什么一定要有这一步</b>：`after_value` 存的是接口入参的快照。一期挂 `@OpLog` 的
 * 都是字典 / 商品 / 审核类接口，入参里暂时没有手机号学号；但这层防线不能靠「记得」——
 * 哪天给一个带 `phone` 字段的接口挂上注解，日志表就成了个人信息泄露面。
 *
 * <p><b>两道口径一起做</b>：
 * <ol>
 *   <li><b>按键名</b>：`password` / `captcha` / `mailCode` / `phone` / `email` / `idCard` /
 *       `studentNo` / `token` 这类字段名，整值替成 `***`（连长度都不留）；</li>
 *   <li><b>按值形</b>：键名猜不出来时兜一层——字符串里的手机号与邮箱按位替换，
 *       如 `13800138000` → `138****8000`。因为「校外引流 13800138000」这种
 *       自由文本里的号码，键名（`reason`）看不出来。</li>
 * </ol>
 *
 * <p><b>为什么要走 JsonNode 树而不是正则改 JSON 文本</b>：树能分清「键名」与「值」，
 * 正则改文本会连键名一起改（把 `phone` 的键改成 `***` 就让日志失去可读性）；
 * 也能逐层下钻，`{"items":[{"phone":"..."}]}` 这种嵌套结构同样覆盖。
 *
 * <p>序列化复用容器里的 `ObjectMapper`（`JacksonConfig` 定制过的那个），
 * 于是日志里的时间、Long 与接口响应口径一致，排查时不用二次换算。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpLogMasker {

    /** 替值标记：用不可逆的固定串，不做部分保留——键名已经说明了这里是什么 */
    private static final String MASK = "***";

    /** 与 `sys_operation_log.before_value / after_value` 的 varchar(2000) 对齐，留 3 字符给省略号 */
    private static final int MAX_VALUE_LENGTH = 2000;

    private static final String ELLIPSIS = "...";

    /**
     * 命中的键名整值清空。都是**小写子串**匹配，所以 `passwordHash` / `newPassword` /
     * `captchaCode` / `mailCode` / `studentNo` / `bankCardNo` 都在范围内。
     *
     * <p>刻意**没有**把 `name` / `code` / `username` 收进来：字典的 `name`、成色的 `code`
     * 是日志的主要可读内容，一刀切会把日志变成 `{"name":"***"}` 这种废数据；
     * 而它们本身不构成个人信息。
     */
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "passwd", "pwd", "secret", "token",
            "captcha", "mailcode", "smscode",
            "phone", "mobile", "telephone",
            "email", "mail",
            "idcard", "idno", "studentno", "studentid", "bankcard");

    /** 中国大陆手机号：1 开头 + 第 2 位 3-9 + 9 位数字，两侧不能还有数字 */
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");

    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.]+");

    private final ObjectMapper objectMapper;

    /**
     * 把任意对象转成脱敏后的 JSON 字符串。任何一步失败都返回 null —— 记不到内容比记错内容好，
     * 而抛异常会把主流程的日志写入整体带崩。
     */
    public String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            JsonNode masked = mask(objectMapper.valueToTree(value));
            return truncate(objectMapper.writeValueAsString(masked));
        } catch (Exception e) {
            log.warn("[oplog] 变更内容序列化失败，本条不记录内容", e);
            return null;
        }
    }

    /**
     * 逐层脱敏。对象 / 数组就地改（Jackson 的 `ObjectNode`/`ArrayNode` 可变），
     * 文本节点不可变所以返回新节点，由调用方接住。
     */
    private JsonNode mask(JsonNode node) {
        if (node == null) {
            return null;
        }
        if (node instanceof ObjectNode obj) {
            // 先收集键名再改：直接在 fieldNames() 的迭代里 put 会动到正在遍历的 map
            List<String> names = new ArrayList<>();
            obj.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                if (isSensitiveKey(name)) {
                    obj.put(name, MASK);
                } else {
                    obj.set(name, mask(obj.get(name)));
                }
            }
            return obj;
        }
        if (node instanceof ArrayNode arr) {
            for (int i = 0; i < arr.size(); i++) {
                arr.set(i, mask(arr.get(i)));
            }
            return arr;
        }
        if (node instanceof TextNode) {
            String raw = node.asText();
            String masked = maskText(raw);
            return masked.equals(raw) ? node : TextNode.valueOf(masked);
        }
        return node;
    }

    private boolean isSensitiveKey(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (String key : SENSITIVE_KEYS) {
            if (lower.contains(key)) {
                return true;
            }
        }
        return false;
    }

    /** 手机号保头 3 尾 4，邮箱保首字母与域名——保留到「能认出是同一个人」但不泄露全量 */
    private String maskText(String text) {
        return maskEmails(maskPhones(text));
    }

    /** 手写循环而不用 `replaceAll`：替换文本里有 `$` 与 `\` 时 `replaceAll` 会当成组引用 */
    private String maskPhones(String text) {
        Matcher m = PHONE.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String phone = m.group();
            m.appendReplacement(sb, phone.substring(0, 3) + "****" + phone.substring(7));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String maskEmails(String text) {
        Matcher m = EMAIL.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String mail = m.group();
            m.appendReplacement(sb, Matcher.quoteReplacement(mail.charAt(0) + "***" + mail.substring(mail.indexOf('@'))));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String truncate(String json) {
        if (json.length() <= MAX_VALUE_LENGTH) {
            return json;
        }
        return json.substring(0, MAX_VALUE_LENGTH - ELLIPSIS.length()) + ELLIPSIS;
    }
}
