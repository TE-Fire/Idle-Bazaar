package com.campus.core.dto.resp;

import com.campus.core.enums.ItemStatusEnum;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 学生端商品视图（列表与详情共用）。
 *
 * <p>不复用管理端 ItemListVO：reportCount / riskScore 是处置侧治理数据，不下发 C 端。
 * `images` 仅详情有（列表用 mainImage）。价格字段按 item 原样命名 originPrice，
 * 不继承管理端 VO 的 originalPrice。
 */
@Data
public class ApiItemVO {

    private Long id;

    private String title;

    private String mainImage;

    private Long categoryId;

    private String categoryName;

    private BigDecimal price;

    /** 卖家填的划线价，可空 */
    private BigDecimal originPrice;

    /** 成色 A/B/C/D */
    private String grade;

    private Long campusId;

    private String campusName;

    private Long meetPointId;

    private String meetPointName;

    private Long sellerId;

    private String sellerNick;

    private Integer sellerCredit;

    /** 卖家认证态（app_user.auth_status：0未认证 1审核中 2已认证 3未通过），首页「仅看已认证」筛选用 */
    private Integer sellerAuthStatus;

    /** 卖家头像 URL（来自 app_user.avatar_url，经 UserPubCache 读穿）；空时前端回退占位色块 */
    private String sellerAvatar;

    /** 1 可议价；议价在聊天里进行，不进订单 */
    private Integer negotiable;

    /** 收货方式：1面交 2自提 3送货上门 */
    private Integer deliveryType;

    /** 自提地址 id（deliveryType=2 时有效，引用 user_address.kind=PICKUP） */
    private Long pickupAddressId;

    /** 自提点完整地址文本（省市区+详细，仅详情回填），前端直接展示，不在客户端拼串 */
    private String pickupAddress;

    private String description;

    /** 对外只会出现 ON_SALE：搜索列表与个人列表过滤条件不同，但都不会漏出待审核商品 */
    private ItemStatusEnum status;

    private LocalDateTime publishAt;

    private Integer viewCount;

    /** 详情专用，列表为 null */
    private List<ItemImageVO> images;

    /** 当前登录人是否已收藏；未登录为 false。仅详情页填充 */
    private Boolean favorited;

    /** 收藏时间；仅「我的收藏」列表填充 */
    private LocalDateTime favoritedAt;
}
