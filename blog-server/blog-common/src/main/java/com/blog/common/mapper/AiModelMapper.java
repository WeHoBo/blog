package com.blog.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.common.entity.AiModel;
import org.apache.ibatis.annotations.Mapper;

/** 模型注册表。常规 CRUD 全部由 BaseMapper 提供，无需 XML。 */
@Mapper
public interface AiModelMapper extends BaseMapper<AiModel> {
}
