package com.nomi.backend.application.ai;

import com.nomi.backend.domain.model.ai.AiFeedback;
import com.nomi.backend.domain.port.in.ai.SubmitFeedbackUseCase;
import com.nomi.backend.domain.port.out.AiFeedbackRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Guarda o actualiza la valoración del usuario sobre un producto recomendado.
 */
@Service
@RequiredArgsConstructor
public class SubmitFeedbackHandler implements SubmitFeedbackUseCase {

    private final AiFeedbackRepositoryPort feedbackRepositoryPort;

    @Override
    public AiFeedback execute(SubmitFeedbackCommand command) {
        Optional<AiFeedback> existing = feedbackRepositoryPort
                .findByUserIdAndProductId(command.userId(), command.productId());

        AiFeedback feedback = AiFeedback.builder()
                .id(existing.map(AiFeedback::getId).orElse(null))
                .userId(command.userId())
                .productId(command.productId())
                .liked(command.liked())
                .context("RECOMMENDATION")
                .creadoEn(existing.map(AiFeedback::getCreadoEn).orElse(LocalDateTime.now()))
                .build();

        return feedbackRepositoryPort.save(feedback);
    }
}
