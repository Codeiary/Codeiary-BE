package com.codeiary.domain.blog.fixture;

import com.codeiary.domain.blog.dto.request.BlogPostRequest;
import com.codeiary.domain.blog.entity.BlogPost;
import com.codeiary.domain.user.entity.User;
import org.springframework.test.util.ReflectionTestUtils;

public final class BlogPostFixture {

    public static final long ID = 10L;
    public static final String CONTENT = "# 첫 기록\n\n**Markdown** 본문입니다.";
    public static final String REQUEST_JSON = """
            {"title":"첫 기록","content":"# 본문","category":"Java","publicPost":true}
            """;

    private BlogPostFixture() {
    }

    public static BlogPost create(User author, boolean publicPost) {
        return BlogPost.create(author, "첫 기록", CONTENT, "Java", null, publicPost);
    }

    public static BlogPost createWithId(User author, boolean publicPost) {
        BlogPost post = create(author, publicPost);
        ReflectionTestUtils.setField(post, "id", ID);
        return post;
    }

    public static BlogPostRequest request() {
        return new BlogPostRequest("  첫 기록  ", CONTENT, "  Java  ", "", true);
    }
}
