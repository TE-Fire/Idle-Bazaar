package com.campus.core.dto.req;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 拒绝议价。理由可空——卖家只想快速划走一条不感兴趣的单子，
 * 强制填写会让「拒绝」比「同意」还费事。
 */
@Data
public class OfferRejectReq {

    @Size(max = 200)
    private String reason;
}
