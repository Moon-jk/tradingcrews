package com.pj.stock_console.controller;

import com.pj.stock_console.entity.Todo;
import com.pj.stock_console.repository.TodoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller // 화면을 띄워야 하므로 @Controller로 시작
@RequiredArgsConstructor
public class TodoController {

    private final TodoRepository todoRepository;

    // 1. [화면] 투두리스트 페이지 열기
    @GetMapping("/todolist")
    public String todoPage() {
        return "todolist"; // templates/todolist.html 실행
    }

    // 2. [API] 전체 목록 조회
    @GetMapping("/api/todos")
    @ResponseBody // JSON 데이터를 반환하기 위해 사용
    public List<Todo> getTodos() {
        return todoRepository.findAll();
    }

    // 3. [API] 할 일 추가
    @PostMapping("/api/todos")
    @ResponseBody
    public Todo createTodo(@RequestBody Todo todo) {
        return todoRepository.save(todo);
    }

    // 4. [API] 상태 수정 (완료 여부)
    @PutMapping("/api/todos/{id}")
    @ResponseBody
    public Todo updateTodo(@PathVariable Long id, @RequestBody Todo todoDetails) {
        Todo todo = todoRepository.findById(id).orElseThrow();
        todo.setDone(todoDetails.isDone());
        return todoRepository.save(todo);
    }

    // 5. [API] 개별 삭제
    @DeleteMapping("/api/todos/{id}")
    @ResponseBody
    public void deleteTodo(@PathVariable Long id) {
        todoRepository.deleteById(id);
    }

    // 6. [API] 완료 항목 일괄 삭제
    @DeleteMapping("/api/todos/done")
    @ResponseBody
    public void clearDone() {
        List<Todo> doneList = todoRepository.findAll().stream().filter(Todo::isDone).toList();
        todoRepository.deleteAll(doneList);
    }
}