package com.nomi.backend.infrastructure.persistence.adapter;

import com.nomi.backend.domain.model.product.DietaryTag;
import com.nomi.backend.domain.model.product.Product;
import com.nomi.backend.infrastructure.persistence.entity.ProductEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/**
 * Mapeo MapStruct entre {@code Product} y {@code ProductEntity}. {@code deletedAt} no se copia
 * desde el dominio: solo lo gestiona el borrado lógico. Las etiquetas dietéticas se guardan como
 * {@code text[]}.
 */
@Mapper(componentModel = "spring")
public interface ProductEntityMapper {

    @Mapping(target = "deletedAt", ignore = true)
    ProductEntity toEntity(Product product);

    Product toDomain(ProductEntity entity);

    default String[] toTagArray(Set<DietaryTag> tags) {
        if (tags == null) {
            return new String[0];
        }
        return tags.stream().map(Enum::name).sorted().toArray(String[]::new);
    }

    /** Ignora valores desconocidos: el {@code CHECK} de la columna ya los impide. */
    default Set<DietaryTag> toTagSet(String[] tags) {
        Set<DietaryTag> result = EnumSet.noneOf(DietaryTag.class);
        if (tags == null) {
            return result;
        }
        Arrays.stream(tags)
                .filter(tag -> Arrays.stream(DietaryTag.values()).anyMatch(known -> known.name().equals(tag)))
                .map(DietaryTag::valueOf)
                .forEach(result::add);
        return result;
    }
}
