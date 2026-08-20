package com.football.backend.repository.specification;

import com.football.backend.dto.MatchFilterRequest;
import com.football.backend.entity.MatchEntity;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class MatchSpecifications {
    public static Specification<MatchEntity> withFilters(MatchFilterRequest filter){
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates=new ArrayList<>();

            if(filter.status()!=null){
                predicates.add(criteriaBuilder.equal(root.get("status"),filter.status()));
            }

            if(filter.format()!=null && !filter.format().isBlank()){
                predicates.add(criteriaBuilder.equal(root.get("format"),filter.format()));
            }

            if(filter.location()!=null && !filter.location().isBlank()){
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("location")),
                        "%"+filter.location().toLowerCase()+"%"));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
