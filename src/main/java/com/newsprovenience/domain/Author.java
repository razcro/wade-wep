package com.newsprovenience.domain;

import jakarta.persistence.*;
import lombok.Data;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "authors")
@Data
public class Author {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String uri;

    @Column(nullable = false)
    private String name;

    private String email;

    private String nationality;

    @Column(length = 2000)
    private String bio;

    @Column(length = 500)
    private String affiliation; // Publisher/Organization

    @Column(length = 500)
    private String dbpediaUri;

    @Column(length = 500)
    private String wikidataUri;

    @OneToMany(mappedBy = "author")
    private Set<Article> articles = new HashSet<>();
}
