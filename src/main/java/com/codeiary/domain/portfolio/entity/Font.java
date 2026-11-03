package com.codeiary.domain.portfolio.entity;

import com.codeiary.global.entity.TimeBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@org.hibernate.annotations.BatchSize(size = 100)
@Table(name = "font", uniqueConstraints = @UniqueConstraint(name = "uk_font_code", columnNames = "code"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Font extends TimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String displayName;

    @Column(nullable = false, length = 500)
    private String cssFamily;

    @Column(length = 2048)
    private String stylesheetUrl;

    @Column(nullable = false)
    private boolean enabled = true;

    @Builder(access = AccessLevel.PRIVATE)
    private Font(String code, String displayName, String cssFamily, String stylesheetUrl) {
        this.code = code;
        this.displayName = displayName;
        this.cssFamily = cssFamily;
        this.stylesheetUrl = stylesheetUrl;
    }

    public static Font create(String code, String displayName, String cssFamily, String stylesheetUrl) {
        return Font.builder()
                .code(code)
                .displayName(displayName)
                .cssFamily(cssFamily)
                .stylesheetUrl(stylesheetUrl)
                .build();
    }
}
