package com.campus.core.service;

import com.campus.common.asserts.BizAssert;
import com.campus.common.exception.CommonErrorCode;
import com.campus.core.dto.resp.CaptchaVO;
import com.campus.core.errcode.UserErrorCode;
import com.campus.core.redis.CaptchaRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

/**
 * 图形验证码（business/03 F-A1 补两条：登录 / 注册 / 找回密码三处共用）。
 *
 * <p>自己画而不用 Hutool 的 {@code hutool-captcha}：项目只引了 `hutool-core`，
 * 为一个 4 位验证码多拉一个带 AWT 依赖的包不划算；且自画能直接控制
 * 「去掉易混字符」这件事——`0/O`、`1/I/l` 混排会让用户反复输错。
 *
 * <p>验证码存 Redis 而不是 session：管理端是多实例无状态部署的前提，
 * 且 Sa-Token 的会话与验证码生命周期本就不该绑在一起（登录前还没有会话）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaptchaService {

    /** 去掉 0/O/1/I/l 这些容易看错的字符 */
    private static final String CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int CODE_LEN = 4;
    private static final int WIDTH = 120;
    private static final int HEIGHT = 40;

    private static final Duration TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate stringRedisTemplate;
    private final SecureRandom random = new SecureRandom();

    /** 总开关：本地联调可关（application-dev.yml campus.captcha.enabled） */
    @Value("${campus.captcha.enabled:true}")
    private boolean captchaEnabled;

    /**
     * 下发：返回 key + 图片 base64，前端直接用 data:image/png;base64 渲染。
     *
     * <p>开关关闭时不下发代码、不画图，只回一个 disabled 标记让前端隐藏输入框——
     * 若照常生成再让调用方打补丁，等于白写一次 Redis 加一次 AWT 画图。
     */
    public CaptchaVO generate() {
        if (!captchaEnabled) {
            CaptchaVO disabled = new CaptchaVO();
            disabled.setDisabled(true);
            return disabled;
        }
        String code = randomCode();
        String key = UUID.randomUUID().toString().replace("-", "");
        stringRedisTemplate.opsForValue().set(CaptchaRedisKeys.buildCodeKey(key), code, TTL);
        return new CaptchaVO(key, toBase64Png(code));
    }

    /**
     * 校验：一次性，通过即删。
     *
     * <p>开关关闭时直接放行——本地联调每次都要先调下发接口太烦，
     * 但**生产必须开**，否则注册接口就是裸的刷子入口。
     */
    public void verify(String key, String code) {
        if (!captchaEnabled) {
            return;
        }
        BizAssert.isTrue(key != null && !key.isBlank() && code != null && !code.isBlank(),
                CommonErrorCode.PARAM_ERROR);
        String cacheKey = CaptchaRedisKeys.buildCodeKey(key);
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        BizAssert.notNull(cached, UserErrorCode.CAPTCHA_INVALID);
        // ★ 先比对、后删除：先删的话输错一次图片就失效了，用户被迫刷新重填
        BizAssert.isTrue(cached.equalsIgnoreCase(code.trim()), UserErrorCode.CAPTCHA_INVALID);
        stringRedisTemplate.delete(cacheKey);
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LEN);
        for (int i = 0; i < CODE_LEN; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    /** 4 位字符 + 3 条干扰线 + 噪点；不扭曲（扭曲会降低可读性，毕设场景没必要） */
    private String toBase64Png(String code) {
        BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, WIDTH, HEIGHT);

            // 干扰线：画在字符**下面**，避免盖住字符影响识别
            for (int i = 0; i < 3; i++) {
                g.setColor(randColor(180, 230));
                g.setStroke(new BasicStroke(1.2f));
                g.drawLine(random.nextInt(WIDTH), random.nextInt(HEIGHT),
                        random.nextInt(WIDTH), random.nextInt(HEIGHT));
            }

            g.setFont(new Font("Arial", Font.BOLD, 26));
            int slot = WIDTH / (CODE_LEN + 1);
            for (int i = 0; i < code.length(); i++) {
                g.setColor(randColor(40, 120));
                // 每个字符随机上下偏移 ±5px，稍微抗 OCR
                g.drawString(String.valueOf(code.charAt(i)),
                        slot * i + random.nextInt(6), 28 + random.nextInt(11) - 5);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            // 画不出来不能让登录页卡死：降级成无图，前端提示「点击刷新」
            log.error("[captcha] 生成图片失败", e);
            return "";
        } finally {
            if (g != null) {
                g.dispose();
            }
        }
    }

    private Color randColor(int min, int max) {
        int r = min + random.nextInt(max - min);
        int gg = min + random.nextInt(max - min);
        int b = min + random.nextInt(max - min);
        return new Color(r, gg, b);
    }
}
