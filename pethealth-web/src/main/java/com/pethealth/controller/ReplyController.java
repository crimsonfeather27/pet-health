package com.pethealth.controller;

import com.pethealth.dto.ApiResponse;
import com.pethealth.entity.Reply;
import com.pethealth.service.ReplyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/replies")
@RequiredArgsConstructor
public class ReplyController {

    private final ReplyService replyService;

    /**
     * POST /api/replies — 回复帖子
     */
    @PostMapping
    public ApiResponse<Reply> create(@RequestBody Reply reply) {
        return ApiResponse.success(replyService.create(reply));
    }

    /**
     * GET /api/replies/post/{postId} — 某帖子的回复
     */
    @GetMapping("/post/{postId}")
    public ApiResponse<List<Reply>> byPost(@PathVariable String postId) {
        return ApiResponse.success(replyService.findByPostId(postId));
    }

    /**
     * DELETE /api/replies/{id} — 删除回复
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        replyService.delete(id);
        return ApiResponse.success("删除成功", null);
    }

    /**
     * POST /api/replies/{id}/accept — 标记为最佳答案
     */
    @PostMapping("/{id}/accept")
    public ApiResponse<Reply> accept(@PathVariable String id) {
        return ApiResponse.success(replyService.acceptBest(id));
    }
}
