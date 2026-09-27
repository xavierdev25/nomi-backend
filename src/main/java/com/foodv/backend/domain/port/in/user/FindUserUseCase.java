package com.foodv.backend.domain.port.in.user;

import com.foodv.backend.domain.common.PageQuery;
import com.foodv.backend.domain.common.PagedResult;
import com.foodv.backend.domain.model.user.User;

import java.util.List;

/**
 * Consulta de usuarios.
 */
public interface FindUserUseCase {

    User findById(Long id);

    User findByEmail(String email);

    List<User> findAll();

    PagedResult<User> findAllPaginated(PageQuery query);
}
