package com.jewelcart.vendor.entity;

import com.jewelcart.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor          // JPA needs empty constructor to load entities
@AllArgsConstructor         // for manual object creation
@Builder                    // enables Vendor.builder().name("x").build()
@EqualsAndHashCode(callSuper = true) // include BaseEntity fields in equals/hashCode
@Entity                     // marks this as a JPA managed table
@Table(name = "vendors")    // maps to vendors table in DB
public class Vendor extends BaseEntity {  // inherits createdAt, updatedAt, createdBy, updatedBy

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // use PostgreSQL BIGSERIAL, not Hibernate sequence
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "brand_name", nullable = false)
    private String brandName;

    @Column(name = "gstin")
    private String gstIn;

    @Column(name = "email", unique = true)
    private String email;

    @Column(name = "phone")
    private String phone;

    @Column(name = "address")
    private String address;

    @Builder.Default                          // without this, builder ignores = true and sets null
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;          // soft delete flag — never hard delete vendors
}