package com.pethealth.service;

import com.pethealth.config.GlobalExceptionHandler.ResourceNotFoundException;
import com.pethealth.entity.PetProfile;
import com.pethealth.exception.AccessDeniedException;
import com.pethealth.interceptor.AuthContext;
import com.pethealth.repository.PetProfileRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 资源属主校验：防止水平越权（IDOR）
 * <p>
 * 当前登录用户与资源属主不一致时抛 403。
 */
@Component
@RequiredArgsConstructor
public class OwnershipGuard {

    private final PetProfileRepository petProfileRepository;

    /** 校验当前登录用户是否为资源属主 */
    public void check(HttpServletRequest request, String resourceOwnerId) {
        String userId = AuthContext.requireUserId(request);
        if (!userId.equals(resourceOwnerId)) {
            throw new AccessDeniedException("无权操作他人资源");
        }
    }

    /**
     * 校验当前登录用户拥有指定宠物，通过后返回宠物档案。
     * 用于一切以 petId 为入口的私有数据访问（健康记录、趋势、营养报告、AI 报告、按宠物查提醒等）。
     *
     * @throws ResourceNotFoundException 宠物不存在
     * @throws AccessDeniedException     已登录但不是该宠物的属主
     */
    public PetProfile requireOwnedPet(HttpServletRequest request, String petId) {
        String userId = AuthContext.requireUserId(request);
        PetProfile pet = petProfileRepository.findById(petId).orElse(null);
        if (pet == null) {
            throw new ResourceNotFoundException("宠物档案不存在: " + petId);
        }
        if (!userId.equals(pet.getOwnerId())) {
            throw new AccessDeniedException("无权访问他人宠物数据");
        }
        return pet;
    }
}
