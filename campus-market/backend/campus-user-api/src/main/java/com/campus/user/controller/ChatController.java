package com.campus.user.controller;

import com.campus.common.result.Result;
import com.campus.core.context.UserContext;
import com.campus.core.dto.req.ChatSendReq;
import com.campus.core.dto.resp.ChatConversationVO;
import com.campus.core.dto.resp.ChatMessageVO;
import com.campus.core.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 学生端询单留言（/api/chat）。同步模式：无推送，前端进会话轮询 + 拉取即已读。
 *
 * <p>所有接口从令牌取当前用户（UserContext），不接受客户端传 senderId——
 * 会话与未读都以「我是谁」为前提，传 id 就能看别人的会话。
 */
@Tag(name = "学生端 · 询单留言")
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @Operation(summary = "发送留言", description = "会话按商品+双方定位；买家发起时对方必须是卖家。返回消息 id")
    @PostMapping("/send")
    public Result<Long> send(@Valid @RequestBody ChatSendReq req) {
        return Result.ok(chatService.send(UserContext.getUserId(), req));
    }

    @Operation(summary = "会话列表", description = "我参与的会话按最近消息倒序，含商品摘要、对方昵称与未读数")
    @GetMapping("/conversations")
    public Result<List<ChatConversationVO>> conversations() {
        return Result.ok(chatService.conversations(UserContext.getUserId()));
    }

    @Operation(summary = "会话消息", description = "倒序分页（beforeId 游标向上翻）；无会话返回空列表")
    @GetMapping("/messages")
    public Result<List<ChatMessageVO>> messages(@RequestParam Long itemId,
                                                @RequestParam Long withUserId,
                                                @RequestParam(required = false) Long beforeId,
                                                @RequestParam(required = false) Integer size) {
        return Result.ok(chatService.messages(UserContext.getUserId(), itemId, withUserId, beforeId, size));
    }

    @Operation(summary = "标记已读", description = "对方发来的未读消息标已读；进会话时调")
    @PostMapping("/read")
    public Result<Void> read(@RequestParam Long itemId, @RequestParam Long withUserId) {
        chatService.markRead(UserContext.getUserId(), itemId, withUserId);
        return Result.ok();
    }
}
