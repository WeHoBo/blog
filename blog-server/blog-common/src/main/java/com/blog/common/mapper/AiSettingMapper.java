package com.blog.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.common.entity.AiSetting;
import org.apache.ibatis.annotations.Mapper;

/** 全局设置（固定单行 id=1）。 */
@Mapper
public interface AiSettingMapper extends BaseMapper<AiSetting> {
}
