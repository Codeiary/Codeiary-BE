package com.codeiary.domain.github.service;

import com.codeiary.domain.blog.entity.BlogPost;

public final class GitHubBlogPath {
  private GitHubBlogPath() {}

  public static String of(BlogPost blogPost) {
    return segment(blogPost.getCategory().getName())
        + "/"
        + segment(blogPost.getTitle())
        + "-"
        + blogPost.getId()
        + ".md";
  }

  public static String legacy(Long postId) {
    return "posts/" + postId + ".md";
  }

  private static String segment(String value) {
    String result =
        value.strip().replace('/', '-').replace('\\', '-').replaceAll("\\p{Cntrl}", "-");
    return result.equals(".") || result.equals("..") ? "_" + result : result;
  }
}
