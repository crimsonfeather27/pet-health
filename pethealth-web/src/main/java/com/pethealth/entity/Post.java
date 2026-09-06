package com.pethealth.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "posts")
public class Post {

    @Id
    private String id;

    @NotBlank(message = "标题不能为空")
    @Size(min = 2, max = 100, message = "标题长度需在 2-100 字之间")
    private String title;

    @NotBlank(message = "内容不能为空")
    @Size(min = 5, max = 5000, message = "内容长度需在 5-5000 字之间")
    private String content;

    @Indexed
    @NotBlank(message = "作者ID不能为空")
    private String authorId;

    @NotBlank(message = "作者名不能为空")
    private String authorName;

    private String category;

    private List<String> tags;

    private String petSpecies;

    private Integer viewCount;

    private Integer likeCount;

    private Integer replyCount;

    @Indexed
    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

