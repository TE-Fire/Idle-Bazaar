package com.campus.support.storage.upload;

import com.campus.common.exception.BizException;
import com.campus.common.exception.CommonErrorCode;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 上传公共校验与 objectKey 规则。策略与 Controller 两侧都用它——前端可绕过 ticket 直接
 * 调 multipart 端点，服务端必须自己再查一遍。
 */
public final class UploadChecks {

    /** 图片白名单：商品图只收这三种，HEIC/ GIF 等一律拒（前端预览兼容与存储成本） */
    private static final Set<String> IMAGE_EXTS = Set.of("jpg", "jpeg", "png", "webp");

    /** 单图上限 5MB：校园网慢，更大的图应引导压缩而不是硬传 */
    private static final long MAX_SIZE = 5L * 1024 * 1024;

    private static final String OBJECT_KEY_PREFIX = "item/";

    private UploadChecks() {
    }

    /** 取扩展名（小写、不含点）；不在白名单抛 1000。文件名为空或没有扩展名同样拒 */
    public static String requireImageExt(String filename) {
        int dot = filename == null ? -1 : filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            throw new BizException(CommonErrorCode.PARAM_ERROR, "文件缺少扩展名");
        }
        String ext = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!IMAGE_EXTS.contains(ext)) {
            throw new BizException(CommonErrorCode.PARAM_ERROR, "只支持 jpg / jpeg / png / webp 图片");
        }
        return ext;
    }

    public static void requireSizeOk(long size) {
        if (size <= 0 || size > MAX_SIZE) {
            throw new BizException(CommonErrorCode.PARAM_ERROR, "图片大小需在 5MB 以内");
        }
    }

    /** 生成对象名：item/{userId}/{uuid}.{ext}——带用户前缀，删除与越权校验都靠它 */
    public static String buildObjectKey(Long userId, String filename) {
        return OBJECT_KEY_PREFIX + userId + "/" + UUID.randomUUID() + "." + requireImageExt(filename);
    }

    /**
     * multipart 端点的越权校验：objectKey 前缀必须是 item/{当前用户}/。
     * 否则拿到别人的 key 就能覆盖他人商品图（桶同 key 覆盖即替换）。
     */
    public static void requireOwnObjectKey(Long userId, String objectKey) {
        if (objectKey == null || !objectKey.startsWith(OBJECT_KEY_PREFIX + userId + "/")) {
            throw new BizException(CommonErrorCode.PARAM_ERROR, "非法的图片标识");
        }
    }
}
