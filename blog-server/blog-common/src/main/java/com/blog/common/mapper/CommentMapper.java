package com.blog.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.common.entity.Comment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CommentMapper extends BaseMapper<Comment> {
    @Select("SELECT id, user_id, status FROM comment WHERE id=#{id} FOR UPDATE")
    Comment lockForReport(@Param("id") Long id);
}
