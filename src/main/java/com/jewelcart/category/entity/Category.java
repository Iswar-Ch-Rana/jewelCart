package com.jewelcart.category.entity;

import com.jewelcart.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "categories")
public class Category extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    // many categories → one parent category
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")     // maps to parent_id column
    private Category parent;            // same type — Category!

    // one category → many child categories
    @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY)     // mapped by the parent field above
    @Builder.Default                          // ← add this
    private List<Category> children = new ArrayList<>();

    @Column(name = "description")
    private String description;

    @Column(name = "image_url")
    private String imageUrl;

    @Builder.Default
    @Column(name = "display_order")
    private Integer displayOrder = 0;

    @Builder.Default
    @Column(name = "is_active")
    private Boolean isActive = true;
}
