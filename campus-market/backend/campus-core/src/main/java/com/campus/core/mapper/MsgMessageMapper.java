package com.campus.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.core.entity.MsgMessage;
import lombok.Data;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MsgMessageMapper extends BaseMapper<MsgMessage> {

    /**
     * 公告分批插入：一条公告的收件行一次拼多值 `VALUES`，500 行一批（notes/22 S12 规则 6）。
     *
     * <p>不用 `BaseMapper#insert` 逐行发——全校公告几千行就是几千次往返。
     */
    @Insert("<script>"
            + "INSERT INTO msg_message (id, zone, sender_id, receiver_id, type, content, extra, batch_id, read_flag, create_time, deleted) VALUES "
            + "<foreach collection='list' item='m' separator=','>"
            + "(#{m.id}, 2, 0, #{m.receiverId}, 6, #{m.content}, #{m.extra}, #{m.batchId}, 0, NOW(), 0)"
            + "</foreach>"
            + "</script>")
    int insertAnnouncements(@Param("list") List<MsgMessage> list);

    /** 公告批次总数（一个批次 = 一条公告） */
    @Select("SELECT COUNT(DISTINCT batch_id) FROM msg_message "
            + "WHERE zone = 2 AND type = 6 AND batch_id IS NOT NULL")
    long countNoticeBatch();

    /**
     * 公告批次分页：按批次聚合，取每批最小 id 作样本行（标题与正文随后一次性捞出）。
     *
     * <p>聚合**不过滤** `deleted`——撤回是软删收件行，管理端仍要看到「已撤回」那一条，
     * 靠 `aliveCount=0` 判断，而不是让它从列表里消失（notes/22 S12 规则 7）。
     */
    @Select("SELECT batch_id AS batchId, MIN(id) AS sampleId, COUNT(*) AS receiverCount, "
            + "SUM(CASE WHEN deleted = 0 THEN 1 ELSE 0 END) AS aliveCount, "
            + "SUM(CASE WHEN deleted = 0 AND read_flag = 1 THEN 1 ELSE 0 END) AS readCount, "
            + "MIN(create_time) AS createTime "
            + "FROM msg_message WHERE zone = 2 AND type = 6 AND batch_id IS NOT NULL "
            + "GROUP BY batch_id ORDER BY MIN(id) DESC LIMIT #{offset}, #{size}")
    List<NoticeBatchRow> selectNoticeBatchPage(@Param("offset") long offset, @Param("size") long size);

    /**
     * 首页播报条：最近 N 条未撤回的公告，按批次去重。
     *
     * <p>必须 `GROUP BY batch_id`——一条公告在每个收件人名下各一行，不去重会把同一条
     * 公告按人数重复播 N 次。取 `MIN()` 而不是直接选列，是为了过 `ONLY_FULL_GROUP_BY`。
     */
    @Select("SELECT MIN(id) AS id, MIN(content) AS content, MIN(extra) AS extra, "
            + "MIN(create_time) AS createTime FROM msg_message "
            + "WHERE zone = 2 AND type = 6 AND deleted = 0 AND batch_id IS NOT NULL "
            + "GROUP BY batch_id ORDER BY MIN(id) DESC LIMIT #{limit}")
    List<BulletinRow> selectBulletins(@Param("limit") int limit);

    /** 按批次撤回：只软删仍活着的收件行，返回影响行数 */
    @Update("UPDATE msg_message SET deleted = #{deleted} "
            + "WHERE batch_id = #{batchId} AND deleted = 0")
    int revokeNoticeBatch(@Param("batchId") Long batchId, @Param("deleted") long deleted);

    /** 批次聚合的中间投影，不直接出参 */
    @Data
    class NoticeBatchRow {
        private Long batchId;
        private Long sampleId;
        private Integer receiverCount;
        private Integer aliveCount;
        private Integer readCount;
        private LocalDateTime createTime;
    }

    /** 播报条投影：一行 = 一条公告 */
    @Data
    class BulletinRow {
        private Long id;
        private String content;
        private String extra;
        private LocalDateTime createTime;
    }
}
