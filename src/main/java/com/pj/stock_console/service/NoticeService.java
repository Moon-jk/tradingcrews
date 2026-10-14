package com.pj.stock_console.service;

import com.pj.stock_console.entity.Comment;
import com.pj.stock_console.entity.Notice;
import com.pj.stock_console.repository.NoticeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class NoticeService {

    private final NoticeRepository noticeRepository;

    // Create: 공지사항 저장
    @Transactional
    public void saveNotice(String title, String content, String author, boolean pinned) {
        Notice notice = new Notice();
        notice.setTitle(title);
        notice.setContent(content);
        notice.setAuthor(author);
        notice.setPinned(pinned);
        noticeRepository.save(notice);
    }

    // Read: 공지사항 상세 조회 (조회수 1 증가)
    @Transactional
    public Notice getNoticeDetail(Long id) {
        Notice notice = noticeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다."));

        notice.setViewCount(notice.getViewCount() + 1); // 조회수 증가

        // [핵심 추가 코드] 화면(Thymeleaf)에서 사용해야 할 지연 로딩 데이터들을
        // DB 연결이 끊어지기 전(트랜잭션 안)에 미리 .size()를 호출하여 강제로 당겨옵니다.
        notice.getLikedUsers().size();
        notice.getDislikedUsers().size();
        notice.getComments().size();

        return notice;
    }

    // Read: 수정 폼을 위한 단순 조회 (조회수 증가 X)
    @Transactional(readOnly = true)
    public Notice getNoticeForEdit(Long id) {
        return noticeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다."));
    }

    // Update: 공지사항 수정
    @Transactional
    public void updateNotice(Long id, String title, String content, boolean pinned) {
        Notice notice = noticeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다."));
        notice.setTitle(title);
        notice.setContent(content);
        notice.setPinned(pinned);
        notice.setUpdatedAt(LocalDateTime.now());
    }

    // Delete: 공지사항 삭제
    @Transactional
    public void deleteNotice(Long id) {
        noticeRepository.deleteById(id);
    }

    // --- [좋아요 / 싫어요 로직] ---
    // --- [스마트 토글 로직: 취소 및 갈아타기 가능] ---
    @Transactional
    public void toggleLike(Long id, String nickname) {
        Notice notice = noticeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다."));

        if (notice.getLikedUsers().contains(nickname)) {
            // 1. 이미 '좋아요'를 눌렀던 상태에서 또 누르면 -> 좋아요 취소!
            notice.getLikedUsers().remove(nickname);
        } else {
            // 2. 안 눌렀던 상태면 -> 좋아요 추가 (동시에 '싫어요'가 눌려있었다면 해제)
            notice.getLikedUsers().add(nickname);
            notice.getDislikedUsers().remove(nickname);
        }
    }

    @Transactional
    public void toggleDislike(Long id, String nickname) {
        Notice notice = noticeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다."));

        if (notice.getDislikedUsers().contains(nickname)) {
            // 1. 이미 '싫어요'를 눌렀던 상태에서 또 누르면 -> 싫어요 취소!
            notice.getDislikedUsers().remove(nickname);
        } else {
            // 2. 안 눌렀던 상태면 -> 싫어요 추가 (동시에 '좋아요'가 눌려있었다면 해제)
            notice.getDislikedUsers().add(nickname);
            notice.getLikedUsers().remove(nickname);
        }
    }

    // --- [댓글 로직] ---
    @Transactional
    public void addComment(Long noticeId, String author, String content) {
        Notice notice = noticeRepository.findById(noticeId).orElseThrow();
        Comment comment = new Comment();
        comment.setAuthor(author);
        comment.setContent(content);
        comment.setNotice(notice);
        notice.getComments().add(comment);
    }

    @Transactional
    public void deleteComment(Long noticeId, Long commentId, String nickname, boolean isAdmin) {
        Notice notice = noticeRepository.findById(noticeId).orElseThrow();
        notice.getComments()
                .removeIf(comment -> comment.getId().equals(commentId) && (isAdmin || comment.getAuthor().equals(nickname)));
    }
}