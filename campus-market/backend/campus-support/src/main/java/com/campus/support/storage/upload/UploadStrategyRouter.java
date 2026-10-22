package com.campus.support.storage.upload;

import com.campus.common.exception.BizException;
import com.campus.common.exception.CommonErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 上传策略路由器：按 {@code campus.upload.mode} 选默认策略，请求可显式覆盖 mode
 * （小程序端固定传 PROXY——uni.uploadFile 发不了 PUT 直传，这是平台限制不是偏好）。
 *
 * <p>策略 Map 由 Spring 注入全部实现装配：加一种上传方式只需新增一个
 * {@link UploadStrategy} Bean，本类与其他调用方零改动。
 */
@Slf4j
@Service
public class UploadStrategyRouter {

    private final Map<UploadMode, UploadStrategy> strategies;

    private final UploadMode defaultMode;

    public UploadStrategyRouter(List<UploadStrategy> strategyList,
                                @Value("${campus.upload.mode:proxy}") String modeConfig) {
        this.strategies = strategyList.stream()
                .collect(Collectors.toMap(UploadStrategy::mode, Function.identity(), (a, b) -> a));
        this.defaultMode = UploadMode.fromConfig(modeConfig);
        log.info("[upload] 策略就绪：available={} default={}", strategies.keySet(), defaultMode);
    }

    /** requested 传 null 走配置默认；显式传值按请求来（策略未装配时抛 1000，不静默降级） */
    public UploadTicket issue(UploadRequest req, UploadMode requested) {
        UploadMode mode = requested == null ? defaultMode : requested;
        UploadStrategy strategy = strategies.get(mode);
        if (strategy == null) {
            throw new BizException(CommonErrorCode.PARAM_ERROR,
                    "上传方式不可用：" + mode.name().toLowerCase(Locale.ROOT));
        }
        return strategy.issue(req);
    }
}
