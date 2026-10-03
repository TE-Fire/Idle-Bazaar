package com.campus.core.dto.resp;

import lombok.Data;

/**
 * 面交点（对齐前端 `DictMeetPointItem`）
 *
 * <p>额外带 `campusName`：列表页要显示"中心校区"，让前端拿 id 再查一次校区字典
 * 会把一次请求拆成 N+1，这种冗余字段是划算的。
 */
@Data
public class DictMeetPointVO {

    private Long id;

    private String name;

    private Long campusId;

    /** 冗余校区名，省掉前端二次查询 */
    private String campusName;

    private String openHours;

    private Boolean enabled;
}
