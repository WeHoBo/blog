package com.blog.common.vo;

import com.blog.common.entity.Category;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CategoryTreeVO {
    private Long id;
    private String name;
    private String slug;
    private Long parentId;
    private Integer sort;
    private Integer articleCount;
    private List<CategoryTreeVO> children = new ArrayList<>();

    public static List<CategoryTreeVO> buildTree(List<Category> list) {
        List<CategoryTreeVO> roots = new ArrayList<>();
        for (Category cat : list) {
            if (cat.getParentId() == null || cat.getParentId() == 0) {
                CategoryTreeVO vo = toVO(cat);
                buildChildren(vo, list);
                roots.add(vo);
            }
        }
        return roots;
    }

    private static void buildChildren(CategoryTreeVO parent, List<Category> list) {
        for (Category cat : list) {
            if (parent.getId().equals(cat.getParentId())) {
                CategoryTreeVO vo = toVO(cat);
                buildChildren(vo, list);
                parent.getChildren().add(vo);
            }
        }
    }

    private static CategoryTreeVO toVO(Category cat) {
        CategoryTreeVO vo = new CategoryTreeVO();
        vo.setId(cat.getId());
        vo.setName(cat.getName());
        vo.setSlug(cat.getSlug());
        vo.setParentId(cat.getParentId());
        vo.setSort(cat.getSort());
        vo.setArticleCount(cat.getArticleCount());
        return vo;
    }
}
