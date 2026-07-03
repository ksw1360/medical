package com.dicom.medical.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "patient")
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String patientId;   // (0010,0020)

    private String patientName; // (0010,0010)
    private LocalDate birthDate; // (0010,0030)
    private String sex;          // (0010,0040) M/F/O

    // 추가
    private String age;          // (0010,1010) 예: 034Y
}
