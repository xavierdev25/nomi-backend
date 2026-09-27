package com.nomi.backend.domain.port.in.user;

import com.nomi.backend.domain.common.PageQuery;
import com.nomi.backend.domain.common.PagedResult;
import com.nomi.backend.domain.model.user.User;

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
