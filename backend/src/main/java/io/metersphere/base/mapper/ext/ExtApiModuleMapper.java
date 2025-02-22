package io.metersphere.base.mapper.ext;

import io.metersphere.api.dto.definition.ApiModuleDTO;
import io.metersphere.base.domain.ApiModule;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

public interface ExtApiModuleMapper {
    int insertBatch(@Param("records") List<ApiModule> records);

    List<ApiModuleDTO> getNodeTreeByProjectId(@Param("projectId") String projectId, @Param("protocol") String protocol);

    List<ApiModuleDTO> selectByIds(@Param("ids") Collection<String> ids);

    void updatePos(String id, Double pos);

    String getNameById(String moduleId);
}