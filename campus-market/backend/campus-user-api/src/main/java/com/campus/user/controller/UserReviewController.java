package com.campus.user.controller;

import com.campus.common.result.PageResult;
import com.campus.common.result.Result;
import com.campus.core.context.UserContext;
import com.campus.core.dto.req.UserReviewCreateReq;
import com.campus.core.dto.resp.OrderReviewVO;
import com.campus.core.dto.resp.ReviewSummaryVO;
import com.campus.core.dto.resp.UserReviewVO;
import com.campus.core.service.UserReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 交易互评（notes/13 O18）。
 *
 * <p>两个路径段合在一个控制器里：写评价挂在订单下（评价属于这笔交易），
 * 读评价挂在用户下（列表属于这个人）。拆成两个控制器反而要多维护一份服务依赖。
 *
 * <p>评价列表与汇总是**免登录**的：个人主页本身对游客可见，评价是它的一部分；
 * 提交与查订单评价态必须登录，且必须是该单当事人（服务层校验）。
 */
@Tag(name = "学生端 · 交易互评")
@RestController
@RequiredArgsConstructor
public class UserReviewController {

    private final UserReviewService userReviewService;

    @Operation(summary = "提交评价", description = "仅订单已完成(4)且面交后 30 天内；重复提交返回既有评价")
    @PostMapping("/api/order/{id}/review")
    public Result<UserReviewVO> submit(@PathVariable("id") Long orderId,
                                       @Valid @RequestBody UserReviewCreateReq req) {
        return Result.ok(userReviewService.submit(orderId, UserContext.getUserId(), req));
    }

    @Operation(summary = "该单的评价状态", description = "我评没评、对方评没评、现在能不能评 + 标签表")
    @GetMapping("/api/order/{id}/review")
    public Result<OrderReviewVO> ofOrder(@PathVariable("id") Long orderId) {
        return Result.ok(userReviewService.ofOrder(orderId, UserContext.getUserId()));
    }

    @Operation(summary = "某人收到的评价", description = "免登录；时间倒序")
    @GetMapping("/api/user/{id}/reviews")
    public Result<PageResult<UserReviewVO>> list(@PathVariable("id") Long userId,
                                                 @RequestParam(defaultValue = "1") int pageNum,
                                                 @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(userReviewService.listOf(userId, pageNum, pageSize));
    }

    @Operation(summary = "评价汇总", description = "免登录；平均分 + 五档条数 + 最近 3 条")
    @GetMapping("/api/user/{id}/review-summary")
    public Result<ReviewSummaryVO> summary(@PathVariable("id") Long userId) {
        return Result.ok(userReviewService.summary(userId));
    }
}
