package com.hrishabh.algocracksubmissionservice.progress.model;

import com.hrishabh.algocracksubmissionservice.models.BaseModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "user_topic_progress", uniqueConstraints = @UniqueConstraint(
        name = "uk_user_topic_version", columnNames = {"user_id", "tag_id", "rank_algorithm_version"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserTopicProgress extends BaseModel {

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "tag_id", nullable = false)
    private long tagId;

    @Column(name = "tag_name_snapshot")
    private String tagNameSnapshot;

    @Column(name = "normalized_credit", nullable = false, precision = 10, scale = 4)
    private BigDecimal normalizedCredit;

    @Column(name = "contributing_solves", nullable = false)
    private int contributingSolves;

    @Column(name = "topic_level", nullable = false, length = 32)
    private String topicLevel;

    @Column(name = "rank_algorithm_version", nullable = false, length = 32)
    private String rankAlgorithmVersion;
}
