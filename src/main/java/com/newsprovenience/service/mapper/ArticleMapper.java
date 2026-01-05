package com.newsprovenience.service.mapper;
import com.newsprovenience.domain.Article;
import com.newsprovenience.service.dto.ArticleDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ArticleMapper {

    ArticleDTO toDTO(Article entity);

    Article toEntity(ArticleDTO dto);
}
