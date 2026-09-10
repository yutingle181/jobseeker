package com.jobseeker.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PositionRequest {

    private String category;

    @NotBlank(message = "不能为空")
    private String title;

    private String description;

    private String companyName;

    private String companyIntro;

    private String city;
}
