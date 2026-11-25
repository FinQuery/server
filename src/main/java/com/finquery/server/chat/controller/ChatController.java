package com.finquery.server.chat.controller;

import com.finquery.server.chat.dto.ChatRequest;
import com.finquery.server.chat.dto.ChatResponse;
import com.finquery.server.chat.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 실시간 금융 질의응답 채팅 API 엔드포인트(POST /api/chat)를 제공하는 컨트롤러이다.
 * HTTP 요청 파싱·유효성 검증·응답 반환만 담당하며 비즈니스 로직은 ChatService에 위임한다.
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

	private final ChatService chatService;

	@PostMapping
	public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
		return ResponseEntity.ok(chatService.chat(request));
	}
}
