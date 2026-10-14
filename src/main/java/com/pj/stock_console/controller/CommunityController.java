package com.pj.stock_console.controller;

import com.pj.stock_console.entity.CommunityBoard;
import com.pj.stock_console.entity.CommunityComment;
import com.pj.stock_console.entity.CommunityPost;
import com.pj.stock_console.entity.Member;
import com.pj.stock_console.repository.CommunityBoardRepository;
import com.pj.stock_console.repository.CommunityCommentRepository;
import com.pj.stock_console.repository.CommunityPostRepository;
import com.pj.stock_console.repository.MemberRepository;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/community")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommunityController {

    private static final String LOGIN_MEMBER = "loginMember";
    private static final String REDIRECT_COMMUNITY = "redirect:/community/";
    private static final String REDIRECT_LOGIN = "redirect:/login";
    private static final String SUCCESS_MESSAGE = "successMessage";
    private static final String ERROR_MESSAGE = "errorMessage";
    private static final String ROLE_ADMIN = "ADMIN";

    private final CommunityBoardRepository boardRepository;
    private final CommunityPostRepository postRepository;
    private final CommunityCommentRepository commentRepository;
    private final MemberRepository memberRepository;

    @GetMapping
    public String communityHome() {
        List<CommunityBoard> boards = boardRepository.findAll();
        if (boards.isEmpty()) {
            return "community/empty";
        }
        return REDIRECT_COMMUNITY + boards.get(0).getId();
    }

    @GetMapping("/{boardId}")
    public String boardFeed(@PathVariable("boardId") Long boardId, Model model, HttpSession session) {
        Member loginMember = (Member) session.getAttribute(LOGIN_MEMBER);

        CommunityBoard currentBoard = boardRepository.findById(boardId).orElseThrow();
        Hibernate.initialize(currentBoard.getSubscribers()); // 🚩 Fix LazyInitializationException for subscribers count
        List<CommunityBoard> allBoards = boardRepository.findAll();
        List<CommunityPost> posts = postRepository.findByBoardIdOrderByCreatedAtDesc(boardId);
        
        // 🚩 Fix LazyInitializationException for crawlers/non-logged-in users
        posts.forEach(p -> {
            Hibernate.initialize(p.getAuthor());
            Hibernate.initialize(p.getUpvotedMembers());
            Hibernate.initialize(p.getComments());
            p.getComments().forEach(c -> Hibernate.initialize(c.getAuthor()));
        });

        boolean isSubscribed = false;
        java.util.Set<Long> upvotedPostIds = new java.util.HashSet<>();

        if (loginMember != null) {
            isSubscribed = currentBoard.getSubscribers().stream()
                    .anyMatch(m -> m.getId().equals(loginMember.getId()));

            upvotedPostIds = posts.stream()
                    .filter(p -> p.getUpvotedMembers().stream().anyMatch(m -> m.getId().equals(loginMember.getId())))
                    .map(CommunityPost::getId)
                    .collect(java.util.stream.Collectors.toSet());
        }

        model.addAttribute("currentBoard", currentBoard);
        model.addAttribute("allBoards", allBoards);
        model.addAttribute("posts", posts);
        model.addAttribute("member", loginMember);
        model.addAttribute("isSubscribed", isSubscribed);
        model.addAttribute("upvotedPostIds", upvotedPostIds);

        return "community/feed"; 
    }

    @Transactional
    @PostMapping("/{boardId}/subscribe")
    public String toggleSubscription(@PathVariable("boardId") Long boardId, HttpSession session, RedirectAttributes rttr) {
        Member loginMember = (Member) session.getAttribute(LOGIN_MEMBER);
        if (loginMember == null) return REDIRECT_LOGIN;

        CommunityBoard board = boardRepository.findById(boardId).orElseThrow();
        boolean isSubscribed = board.getSubscribers().stream()
                .anyMatch(m -> m.getId().equals(loginMember.getId()));

        if (isSubscribed) {
            board.getSubscribers().removeIf(m -> m.getId().equals(loginMember.getId()));
            rttr.addFlashAttribute(SUCCESS_MESSAGE, "✅ 게시판을 탈퇴했습니다.");
        } else {
            Member managedMember = memberRepository.findById(loginMember.getId()).orElseThrow();
            board.getSubscribers().add(managedMember);
            rttr.addFlashAttribute(SUCCESS_MESSAGE, "✅ 게시판에 가입했습니다.");
        }
        boardRepository.save(board);

        return REDIRECT_COMMUNITY + boardId;
    }

    @GetMapping("/{boardId}/posts/new")
    public String createPostForm(@PathVariable("boardId") Long boardId, Model model, HttpSession session, RedirectAttributes rttr) {
        Member loginMember = (Member) session.getAttribute(LOGIN_MEMBER);
        if (loginMember == null) return REDIRECT_LOGIN;

        CommunityBoard currentBoard = boardRepository.findById(boardId).orElseThrow();
        boolean isSubscribed = currentBoard.getSubscribers().stream()
                .anyMatch(m -> m.getId().equals(loginMember.getId()));
        
        if (!isSubscribed && !loginMember.getRole().name().equals(ROLE_ADMIN)) {
            rttr.addFlashAttribute(ERROR_MESSAGE, "❌ 게시판에 가입해야 글을 작성할 수 있습니다.");
            return REDIRECT_COMMUNITY + boardId;
        }

        List<CommunityBoard> allBoards = boardRepository.findAll();
        
        model.addAttribute("currentBoard", currentBoard);
        model.addAttribute("allBoards", allBoards);
        model.addAttribute("member", loginMember);

        return "community/create_post";
    }

    @Transactional
    @PostMapping("/{boardId}/posts")
    public String createPost(@PathVariable("boardId") Long boardId, @RequestParam("content") String content, HttpSession session, RedirectAttributes rttr) {
        Member loginMember = (Member) session.getAttribute(LOGIN_MEMBER);
        if (loginMember == null) return REDIRECT_LOGIN;

        CommunityBoard board = boardRepository.findById(boardId).orElseThrow();
        
        boolean isSubscribed = board.getSubscribers().stream()
                .anyMatch(m -> m.getId().equals(loginMember.getId()));
        if (!isSubscribed && !loginMember.getRole().name().equals(ROLE_ADMIN)) {
            rttr.addFlashAttribute(ERROR_MESSAGE, "❌ 게시판에 가입해야 글을 작성할 수 있습니다.");
            return REDIRECT_COMMUNITY + boardId;
        }
        
        Member managedMember = memberRepository.findById(loginMember.getId()).orElseThrow();
        CommunityPost post = new CommunityPost();
        post.setBoard(board);
        post.setAuthor(managedMember);
        post.setContent(content);
        postRepository.save(post);

        rttr.addFlashAttribute(SUCCESS_MESSAGE, "✅ 글이 등록되었습니다.");
        return REDIRECT_COMMUNITY + boardId;
    }

    @Transactional
    @PostMapping("/posts/{postId}/comments")
    public String createComment(@PathVariable("postId") Long postId, @RequestParam("content") String content, HttpSession session, RedirectAttributes rttr) {
        Member loginMember = (Member) session.getAttribute(LOGIN_MEMBER);
        if (loginMember == null) return REDIRECT_LOGIN;

        CommunityPost post = postRepository.findById(postId).orElseThrow();
        Member managedMember = memberRepository.findById(loginMember.getId()).orElseThrow();
        
        CommunityComment comment = new CommunityComment();
        comment.setPost(post);
        comment.setAuthor(managedMember);
        comment.setContent(content);
        commentRepository.save(comment);

        return REDIRECT_COMMUNITY + post.getBoard().getId();
    }

    @Transactional
    @PostMapping("/posts/{postId}/upvote")
    @ResponseBody
    public String upvotePost(@PathVariable("postId") Long postId, HttpSession session) {
        Member loginMember = (Member) session.getAttribute(LOGIN_MEMBER);
        if (loginMember == null) return "auth_fail";

        CommunityPost post = postRepository.findById(postId).orElseThrow();
        Member managedMember = memberRepository.findById(loginMember.getId()).orElseThrow();
        
        boolean isActive = post.getUpvotedMembers().stream().anyMatch(m -> m.getId().equals(managedMember.getId()));
        if (isActive) {
            post.getUpvotedMembers().removeIf(m -> m.getId().equals(managedMember.getId()));
            isActive = false;
        } else {
            post.getUpvotedMembers().add(managedMember);
            isActive = true;
        }
        
        postRepository.save(post);
        return post.getUpvotedMembers().size() + ":" + isActive;
    }

    @Transactional
    @PostMapping("/posts/{postId}/delete")
    public String deletePost(@PathVariable("postId") Long postId, HttpSession session, RedirectAttributes rttr) {
        Member loginMember = (Member) session.getAttribute(LOGIN_MEMBER);
        if (loginMember == null) return REDIRECT_LOGIN;

        CommunityPost post = postRepository.findById(postId).orElseThrow();
        if (post.getAuthor().getId().equals(loginMember.getId()) || loginMember.getRole().name().equals(ROLE_ADMIN)) {
            postRepository.delete(post);
            rttr.addFlashAttribute(SUCCESS_MESSAGE, "✅ 글이 삭제되었습니다.");
        } else {
            rttr.addFlashAttribute(ERROR_MESSAGE, "❌ 삭제 권한이 없습니다.");
        }
        
        return REDIRECT_COMMUNITY + post.getBoard().getId();
    }
}
