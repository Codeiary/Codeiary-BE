package com.codeiary.domain.blog.fixture;

import com.codeiary.domain.blog.dto.request.BlogPostRequest;
import com.codeiary.domain.blog.entity.BlogPost;
import com.codeiary.domain.blog.entity.Category;
import com.codeiary.domain.user.entity.User;
import java.util.List;
import org.springframework.test.util.ReflectionTestUtils;

public final class BlogPostFixture {

  public static final long ID = 10L;
  public static final String CONTENT = "# 첫 기록\n\n**Markdown** 본문입니다.";
  public static final String REQUEST_JSON =
      """
      {"title":"첫 기록","content":"# 본문","category":"Java","tags":["spring","java"],"publicPost":true}
      """;

  private BlogPostFixture() {}

  public static BlogPost create(User user, boolean publicPost) {
    return BlogPost.create(user, "첫 기록", CONTENT, Category.create(user, "Java"), null, publicPost);
  }

  public static BlogPost createWithId(User user, boolean publicPost) {
    BlogPost blogPost = create(user, publicPost);
    ReflectionTestUtils.setField(blogPost, "id", ID);
    return blogPost;
  }

  public static BlogPostRequest request() {
    return new BlogPostRequest(
        "  첫 기록  ", CONTENT, "  Java  ", List.of(" Spring ", "JAVA", "java"), "", true);
  }
}
