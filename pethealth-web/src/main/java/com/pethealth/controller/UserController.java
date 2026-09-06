package com.pethealth.controller;

import com.pethealth.dto.ApiResponse;
import com.pethealth.entity.User;
import com.pethealth.interceptor.AuthInterceptor;
import com.pethealth.service.AuthService;
import com.pethealth.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AuthService authService;

    /**
     * POST /api/users/register — 用户注册
     */
    @PostMapping("/register")
    public ApiResponse<User> register(@Valid @RequestBody RegisterRequest req) {
        User user = userService.register(req.getUsername(), req.getPassword(), req.getEmail());
        user.setPassword(null);
        return ApiResponse.success("注册成功", user);
    }

    /**
     * POST /api/users/login — 用户登录，生成 Redis Token
     * 返回 {token, user}
     */
    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody LoginRequest req) {
        return userService.login(req.getUsername(), req.getPassword())
                .map(user -> {
                    String token = authService.createToken(user);
                    user.setPassword(null);
                    Map<String, Object> data = new HashMap<>();
                    data.put("token", token);
                    data.put("user", user);
                    log.info("用户 {} 登录成功，颁发 Token", user.getUsername());
                    return ApiResponse.success("登录成功", data);
                })
                .orElse(ApiResponse.error(401, "用户名或密码错误"));
    }

    /**
     * POST /api/users/logout — 登出，删除 Redis Token
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            boolean invalidated = authService.invalidate(token);
            log.info("登出，Token 已删除: {}", invalidated ? "成功" : "已过期或不存在");
        }
        return ApiResponse.success("已登出", null);
    }

    /**
     * GET /api/users/me — 当前登录用户（基于 AuthInterceptor 注入的 request attribute）
     */
    @GetMapping("/me")
    public ApiResponse<User> me(HttpServletRequest request) {
        String userId = (String) request.getAttribute(AuthInterceptor.CURRENT_USER_ID);
        if (userId == null) {
            return ApiResponse.error(401, "未登录或 Token 已失效");
        }
        return userService.findById(userId)
                .map(user -> { user.setPassword(null); return ApiResponse.success(user); })
                .orElse(ApiResponse.error(404, "用户不存在"));
    }

    /**
     * GET /api/users/{id} — 查询用户信息
     */
    @GetMapping("/{id}")
    public ApiResponse<User> get(@PathVariable String id) {
        return userService.findById(id)
                .map(user -> { user.setPassword(null); return ApiResponse.success(user); })
                .orElse(ApiResponse.error(404, "用户不存在"));
    }

    /**
     * PUT /api/users/{id} — 更新用户资料
     */
    @PutMapping("/{id}")
    public ApiResponse<User> update(@PathVariable String id, @RequestBody User updates) {
        User user = userService.update(id, updates);
        user.setPassword(null);
        return ApiResponse.success(user);
    }

    /**
     * POST /api/users/avatar — 上传头像（multipart/form-data，字段名 file）
     * 保存到 uploads/avatars/，更新当前用户 avatar 字段并返回最新用户
     */
    @PostMapping("/avatar")
    public ApiResponse<User> uploadAvatar(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        String userId = (String) request.getAttribute(AuthInterceptor.CURRENT_USER_ID);
        if (userId == null) {
            return ApiResponse.error(401, "未登录或 Token 已失效");
        }
        if (file.isEmpty()) {
            return ApiResponse.error(400, "请选择要上传的头像文件");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ApiResponse.error(400, "仅支持上传图片文件");
        }

        try {
            // 通过扩展名推断图片类型（png/jpg/jpeg/gif/webp），否则使用默认 png
            String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
            String ext = original.contains(".")
                    ? original.substring(original.lastIndexOf('.') + 1).toLowerCase()
                    : "png";
            if (!ext.matches("png|jpg|jpeg|gif|webp")) {
                ext = "png";
            }
            String filename = userId + "_" + UUID.randomUUID().toString().replace("-", "") + "." + ext;

            // 使用基于 JVM 工作目录的绝对路径（transferTo 对相对路径会解析到 Tomcat 临时目录，必须用绝对路径）
            Path dir = Paths.get(System.getProperty("user.dir"), "uploads", "avatars");
            Files.createDirectories(dir);
            file.transferTo(dir.resolve(filename).toFile());

            String url = "/uploads/avatars/" + filename;

            // 更新当前用户头像
            User updates = new User();
            updates.setAvatar(url);
            User user = userService.update(userId, updates);
            user.setPassword(null);
            log.info("用户 {} 头像已更新: {}", user.getUsername(), url);
            return ApiResponse.success("头像上传成功", user);
        } catch (IOException e) {
            log.error("头像文件保存失败", e);
            return ApiResponse.error(500, "头像保存失败: " + e.getMessage());
        }
    }

    @Data
    public static class RegisterRequest {
        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 20, message = "用户名长度需在 3-20 字之间")
        private String username;

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 32, message = "密码长度需在 6-32 字之间")
        private String password;

        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        private String email;
    }

    @Data
    public static class LoginRequest {
        @NotBlank(message = "用户名不能为空")
        private String username;

        @NotBlank(message = "密码不能为空")
        private String password;
    }
}
