package com.pethealth.service;

import com.pethealth.config.GlobalExceptionHandler.ResourceNotFoundException;
import com.pethealth.entity.User;
import com.pethealth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 注册新用户（BCrypt 加密密码，检查用户名/邮箱唯一性）
     */
    public User register(String username, String password, String email) {
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("用户名已存在: " + username);
        }
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("邮箱已被注册: " + email);
        }

        LocalDateTime now = LocalDateTime.now();
        User user = User.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .email(email)
                .avatar("https://api.dicebear.com/7.x/bottts/svg?seed=" + username)
                .createdAt(now)
                .updatedAt(now)
                .build();

        User saved = userRepository.save(user);
        log.info("新用户注册: {}", saved.getUsername());
        return saved;
    }

    /**
     * 登录验证（用户名 + BCrypt 密码匹配）
     */
    public Optional<User> login(String username, String rawPassword) {
        Optional<User> opt = userRepository.findByUsername(username);
        if (opt.isEmpty()) return Optional.empty();

        User user = opt.get();
        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            return Optional.empty();
        }
        return Optional.of(user);
    }

    public Optional<User> findById(String id) {
        return userRepository.findById(id);
    }

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public User update(String id, User updates) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在: " + id));

        if (updates.getEmail() != null) user.setEmail(updates.getEmail());
        if (updates.getAvatar() != null) user.setAvatar(updates.getAvatar());
        if (updates.getPhone() != null) user.setPhone(updates.getPhone());
        if (updates.getPreferences() != null) user.setPreferences(updates.getPreferences());
        user.setUpdatedAt(LocalDateTime.now());

        return userRepository.save(user);
    }
}
