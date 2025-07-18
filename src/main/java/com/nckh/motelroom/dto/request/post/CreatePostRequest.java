package com.nckh.motelroom.dto.request.post;

import com.nckh.motelroom.dto.entity.AccomodationDto;
import com.nckh.motelroom.dto.request.accommodation.CreateAccommodationRequest;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CreatePostRequest {
    //Basic information of Post
    @NotNull
    @Size(min = 10, max = 100, message = "Tiêu đề phải từ 10 đến 100 ký tự")
    private String title;
    @NotNull
    @Size(min = 50, max = 500, message = "Nội dung mô tả phải từ 50 đến 500 ký tự")
    private String content;
    private CreateAccommodationRequest accomodation;
}
