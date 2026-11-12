package com.codeiary.domain.github.service;

import com.codeiary.domain.blog.entity.BlogPost;

public final class GitHubBlogPath {
    private GitHubBlogPath() {
    }

    public static String of(BlogPost post) {
        return segment(post.getCategory().getName()) + "/" + segment(post.getTitle()) + "-" + post.getId() + ".md";
    }

    public static String legacy(Long postId) {
        return "posts/" + postId + ".md";
    }

    private static String segment(String value) {
        String result = value.strip().replace('/', '-').replace('\\', '-').replaceAll("\\p{Cntrl}", "-");
        return result.equals(".") || result.equals("..") ? "_" + result : result;
    }
}
