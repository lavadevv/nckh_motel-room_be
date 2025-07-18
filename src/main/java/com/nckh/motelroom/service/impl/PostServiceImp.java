package com.nckh.motelroom.service.impl;

import com.nckh.motelroom.dto.entity.AccomodationDto;
import com.nckh.motelroom.dto.entity.CommentDto;
import com.nckh.motelroom.dto.entity.PostDto;
import com.nckh.motelroom.dto.entity.SearchDto;
import com.nckh.motelroom.dto.request.post.CreatePostRequest;
import com.nckh.motelroom.dto.request.post.UpdatePostRequest;
import com.nckh.motelroom.dto.response.post.*;
import com.nckh.motelroom.exception.DataNotFoundException;
import com.nckh.motelroom.mapper.AccommodationMapper;
import com.nckh.motelroom.mapper.CommentMapper;
import com.nckh.motelroom.mapper.PostMapper;
import com.nckh.motelroom.mapper.UserMapper;
import com.nckh.motelroom.model.*;
import com.nckh.motelroom.model.enums.ActionName;
import com.nckh.motelroom.repository.*;
import com.nckh.motelroom.repository.custom.CustomPostQuery;
import com.nckh.motelroom.service.PostService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostServiceImp implements PostService {
    //Inject Service
    private final ApplicationEventPublisher applicationEventPublisher;
    //Inject Repository into class
    private final PostRepository postRepository;

    private final UserRepository userRepository;

    private final DistrictRepository districtRepository;

    private final AccomodationRepository accomodationRepository;

    private final CommentRepository commentRepository;

    private final ImageServiceImp imageServiceImp;

    private final ActionServiceImp actionService;

 ;   //Some Mapper in this
    private final PostMapper postMapper;

    private final AccommodationMapper accommodationMapper;

    private final UserMapper userMapper;

    private final CommentMapper commentMapper;

    //hold
    @Override
        public Page<Post> getAllPost(CustomPostQuery.PostFilterParam param, PageRequest pageRequest) {
        try {
            Specification<Post> specification = CustomPostQuery.getFilterPost(param);
            return postRepository.findAll(specification, pageRequest);
        }catch (Exception e){
            throw new DataNotFoundException("Không có bài viết nào được tìm thấy! " + e.getMessage());
        }
    }

    //hold
    @Override
    public PostDto getPostById(Long id) {
        // Tìm bài viết
        Optional<Post> post = postRepository.findPostById(id);

        // Kiểm tra xem bài viết có tồn tại không
        if(post.isPresent()) {
            PostDto postDto = postMapper.toPostDto(post.get());
            //Lay cho o ra
            AccomodationDto accomodationDto = accommodationMapper.toAccomodationDto(post.get().getAccomodation());
            // Lấy các bình luận của bài đăng
            List<CommentDto> commentDtos = new ArrayList<>();
            List<Comment> comments = commentRepository.findCommentsByPostId(id);
            for (Comment comment : comments) {
                commentDtos.add(commentMapper.toCommentDTO(comment));
            }
            // Lấy hình ảnh của bài đăng
            List<String> images = imageServiceImp.getImageByIdPost(id);
            // Thiết lập dữ liệu cho DTO
            postDto.setAccomodationDTO(accomodationDto);
            postDto.setImageStrings(images);
            postDto.setCommentDTOS(commentDtos);
            postDto.setUserDTO(userMapper.toUserDto(post.get().getUser()));

            // Trả về thông tin bài viết
            return postDto;
        } else {
            // Nếu không tìm thấy bài viết
            throw new DataNotFoundException("Không tìm thấy bài viết theo id đã cho");
        }
    }

    @Override
    @Transactional
    public CreatePostResponse createPost(CreatePostRequest createPostRequest, String email) {
        try {
            Optional<User> userOptional = userRepository.findByEmail(email);
            if (userOptional.isEmpty()) {
                throw new DataNotFoundException("User not found with email: " + email);
            }
            User user = userOptional.get();

            // Kiểm tra số dư và trừ đi 2000 nếu đủ
            if (user.getBalance() < 2000) {
                throw new DataNotFoundException("Số dư không đủ để đăng bài. Yêu cầu tối thiểu là 2000.");
            }
            user.setBalance(user.getBalance() - 2000);
            userRepository.save(user);

            // Tạo bài đăng mới
            Post post = postMapper.createRequestDtoToPost(createPostRequest);
            post.setCreateAt(LocalDateTime.now());
            post.setLastUpdate(LocalDateTime.now());
            post.setUser(user);
            post.setDel(false);
            post.setApproved(true);
            post.setNotApproved(true);

            // Xử lý đối tượng Accomodation liên quan đến bài đăng
            Accomodation accomodation = accommodationMapper.toAccomodation(createPostRequest.getAccomodation());
            accomodation.setId(null);
            accomodation.setPost(post);
            Accomodation accomodationSaved = accomodationRepository.save(accomodation);
            post.setAccomodation(accomodationSaved);

            // Lưu bài đăng vào database
            Post postSaved = postRepository.save(post);

            // Tạo action cho bài đăng
            actionService.createAction(post, user, ActionName.CREATE);

            return postMapper.toCreatePostResponse(postSaved);
        } catch (DataNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new RuntimeException(e);
        }
    }




    @Override
    @Transactional
    public UpdatePostResponse updatePost(Long id, UpdatePostRequest updatePostRequest, String userId) {
        try {
            // Kiểm tra xem bài đăng có tồn tại không
            Post post = postRepository.findPostById(id)
                    .orElseThrow(() -> new DataNotFoundException("Không tìm thấy bài đăng với ID: " + id));

            // Tìm hoặc tạo District
            District district = districtRepository.findDistrictById(updatePostRequest.getAccomodation().getDistrict().getId())
                    .orElseGet(() -> {
                        District newDistrict = new District();
                        newDistrict.setName("Default District");

                        return districtRepository.save(newDistrict);
                    });

            // Cập nhật thông tin Accomodation
            Accomodation accomodation = accommodationMapper.toAccomodation(updatePostRequest.getAccomodation());
            accomodation.setDistrict(district);


            // Cập nhật thông tin Post
            post.setTitle(updatePostRequest.getTitle());
            post.setContent(updatePostRequest.getContent());
            post.setLastUpdate(LocalDateTime.now());
            post.setAccomodation(accomodation);
            post.setApproved(true);
            post.setNotApproved(true);

            // Gán Accomodation vào Post (quan hệ 1-1)
            accomodation.setPost(post);

            // Lưu vào database
            accomodationRepository.save(accomodation);
            postRepository.save(post);

            return postMapper.toUpdatePostResponse(post);
        } catch (Exception e) {
            log.error("Lỗi khi cập nhật bài đăng: {}", e.getMessage());
            throw new RuntimeException("Lỗi trong quá trình cập nhật bài đăng: " + e.getMessage());
        }
    }

    @Override
    public HiddenPostResponse hidePost(Long id) {
        try {
            // Tìm bài đăng, nếu không có thì ném DataNotFoundException
            Post post = postRepository.findById(id)
                    .orElseThrow(() -> new DataNotFoundException("Không tìm thấy bài đăng với ID " + id));

            // Chuyển đổi trạng thái của thuộc tính del (nếu false -> true, nếu true -> false)
            post.setDel(!post.getDel());
            postRepository.save(post);

            // Tạo thông báo phù hợp dựa trên trạng thái mới của del
            String statusMessage = post.getDel() ? "Bài đăng đã được ẩn thành công." : "Bài đăng đã được hiển thị thành công.";
            return new HiddenPostResponse(post.getId(), statusMessage, post.getDel());
        } catch (DataNotFoundException e) {
            log.warn("Không tìm thấy bài đăng với ID: {}", id);
            throw e; // Ném lỗi tiếp để controller xử lý
        } catch (Exception e) {
            log.error("Lỗi khi ẩn/bật bài đăng ID {}: {}", id, e.getMessage(), e);
            throw new RuntimeException("Đã xảy ra lỗi khi ẩn/bật bài đăng.");
        }
    }


    @Override
    public DeletePostResponse deletePostByAdmin(Long id) {
        try {
            // Tìm bài đăng, nếu không có thì ném DataNotFoundException
            Post post = postRepository.findById(id)
                    .orElseThrow(() -> new DataNotFoundException("Không tìm thấy bài đăng với ID " + id));

            // Xóa bài đăng
            postRepository.delete(post);

            // Trả về response
            return new DeletePostResponse(id, "Bài đăng đã bị xóa bởi Admin.", true);
        } catch (DataNotFoundException e) {
            log.warn("Không tìm thấy bài đăng với ID: {}", id);
            throw e; // Ném lỗi để controller xử lý
        } catch (Exception e) {
            log.error("Lỗi khi Admin xóa bài đăng ID {}: {}", id, e.getMessage(), e);
            throw new RuntimeException("Đã xảy ra lỗi khi xóa bài đăng.");
        }
    }

    @Override
    public ApprovePostResponse ApprovePost(Long idPost, String usernameApprove, boolean isApprove) {
        try {
            Optional<Post> postOpt = postRepository.findById(idPost);
            if (postOpt.isEmpty()) {
                return new ApprovePostResponse(idPost, "Không tìm thấy bài đăng", false);
            }

            Optional<User> userOpt = userRepository.findByEmail(usernameApprove);
            if (userOpt.isEmpty()) {
                return new ApprovePostResponse(idPost, "Không tìm thấy người dùng có username: " + usernameApprove, false);
            }

            Post post = postOpt.get();
            User user = userOpt.get();
            User postOwner = post.getUser(); // Chủ bài viết

            if (isApprove) {
                // Duyệt bài viết
                post.setApproved(true);
                post.setNotApproved(false);
                actionService.createAction(post, user, ActionName.APPROVE);
            } else {
                // Khóa bài viết
                // Kiểm tra trạng thái hiện tại của bài viết
                boolean wasWaitingApproval = post.getApproved() && post.getNotApproved(); // Chờ duyệt
                boolean wasApproved = post.getApproved() && !post.getNotApproved(); // Đã duyệt

                // Cập nhật trạng thái khóa bài
                post.setApproved(false);
                post.setNotApproved(true);

                // Hoàn tiền nếu bài viết đang ở trạng thái "Chờ duyệt"
                if (wasWaitingApproval) {
                    postOwner.setBalance(postOwner.getBalance() + 2000);
                    userRepository.save(postOwner);
                    log.info("Hoàn tiền 2000 cho user ID: {} khi khóa bài viết ID: {} từ trạng thái chờ duyệt",
                            postOwner.getId(), idPost);
                }

                actionService.createAction(post, user, ActionName.BLOCK);
            }

            postRepository.save(post);

            String message = "Bài đăng đã được " + (isApprove ? "duyệt" : "khóa") + " thành công";
            if (!isApprove && post.getApproved() && post.getNotApproved()) {
                message += " và đã hoàn tiền 2000 cho chủ bài viết";
            }

            return new ApprovePostResponse(idPost, message, isApprove);

        } catch (Exception e) {
            log.error("Lỗi khi duyệt bài đăng: {}", e.getMessage());
            return new ApprovePostResponse(idPost, "Đã xảy ra lỗi trong quá trình xử lý", false);
        }
    }


    @Override
    public Page<PostDto> searchPostByMaps(SearchDto searchForm, int page, int sort) {
        return null;
    }

    @Override
    public Page<PostDto> getPostWaitingApprove(int page) {
        // Lấy danh sách bài đăng chờ duyệt từ repository với phân trang
        Page<Post> posts = postRepository.findByApprovedFalseAndNotApprovedFalse(
                PageRequest.of(page, 12, Sort.by("createAt").descending()));

        // Chuyển đổi từ Page<Post> thành Page<PostDto>
        return posts.map(postMapper::toPostDto);
    }
}
