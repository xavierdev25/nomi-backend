package com.foodv.backend.application.user;

import com.foodv.backend.domain.common.PageQuery;
import com.foodv.backend.domain.common.PagedResult;
import com.foodv.backend.domain.model.user.User;
import com.foodv.backend.domain.port.in.user.FindUserUseCase;
import com.foodv.backend.domain.port.out.UserRepositoryPort;
import com.foodv.backend.domain.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Consulta de usuarios.
 */
@Service
@Transactional(readOnly = true)
public class FindUserHandler implements FindUserUseCase {

    private final UserRepositoryPort userRepositoryPort;

    public FindUserHandler(UserRepositoryPort userRepositoryPort) {
        this.userRepositoryPort = userRepositoryPort;
    }

    @Override
    public User findById(Long id) {
        return userRepositoryPort.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }

    @Override
    public User findByEmail(String email) {
        return userRepositoryPort.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }

    @Override
    public List<User> findAll() {
        return userRepositoryPort.findAll();
    }

    @Override
    public PagedResult<User> findAllPaginated(PageQuery query) {
        return userRepositoryPort.findAllPaginated(query);
    }
}
