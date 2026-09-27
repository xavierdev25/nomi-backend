package com.nomi.backend.domain.port.out;

import com.nomi.backend.domain.common.PageQuery;
import com.nomi.backend.domain.common.PagedResult;
import com.nomi.backend.domain.model.user.User;

import java.util.List;
import java.util.Optional;

/**
 * Persistencia de usuarios. Las lecturas excluyen usuarios borrados lógicamente y el email se
 * compara normalizado.
 */
public interface UserRepositoryPort {

    User save(User user);

    Optional<User> findById(Long id);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    List<User> findAll();

    PagedResult<User> findAllPaginated(PageQuery query);

    void deleteById(Long id);

    Optional<User> findByIdWithPreferences(Long id);
}
