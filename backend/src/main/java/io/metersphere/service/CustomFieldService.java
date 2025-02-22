package io.metersphere.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import io.metersphere.base.domain.*;
import io.metersphere.base.domain.ext.CustomFieldResource;
import io.metersphere.base.mapper.CustomFieldIssuesMapper;
import io.metersphere.base.mapper.CustomFieldMapper;
import io.metersphere.base.mapper.CustomFieldTemplateMapper;
import io.metersphere.base.mapper.ProjectMapper;
import io.metersphere.base.mapper.ext.ExtCustomFieldMapper;
import io.metersphere.commons.constants.CustomFieldType;
import io.metersphere.commons.constants.TemplateConstants;
import io.metersphere.commons.exception.MSException;
import io.metersphere.commons.utils.*;
import io.metersphere.controller.request.QueryCustomFieldRequest;
import io.metersphere.dto.CustomFieldDao;
import io.metersphere.dto.CustomFieldItemDTO;
import io.metersphere.i18n.Translator;
import io.metersphere.log.utils.ReflexObjectUtil;
import io.metersphere.log.vo.DetailColumn;
import io.metersphere.log.vo.OperatingLogDetails;
import io.metersphere.log.vo.system.SystemReference;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = Exception.class)
public class CustomFieldService {

    @Resource
    ExtCustomFieldMapper extCustomFieldMapper;

    @Resource
    CustomFieldMapper customFieldMapper;

    @Lazy
    @Resource
    TestCaseTemplateService testCaseTemplateService;

    @Lazy
    @Resource
    IssueTemplateService issueTemplateService;

    @Lazy
    @Resource
    CustomFieldTemplateService customFieldTemplateService;
    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private CustomFieldIssuesMapper customFieldIssuesMapper;

    public String add(CustomField customField) {
        checkExist(customField);
        customField.setId(UUID.randomUUID().toString());
        customField.setCreateTime(System.currentTimeMillis());
        customField.setUpdateTime(System.currentTimeMillis());
        customField.setGlobal(false);
        customField.setThirdPart(false);
        customField.setCreateUser(SessionUtils.getUserId());
        customFieldMapper.insert(customField);
        return customField.getId();
    }

    /**
     * 系统字段默认是查询的 workspace_id 为 null 的系统字段
     * 如果创建了对应工作空间的系统字段，则过滤掉重复的 workspace_id 为 null 的系统字段
     *
     * @param request
     * @return
     */
    public List<CustomField> list(QueryCustomFieldRequest request) {
        request.setOrders(ServiceUtils.getDefaultOrder(request.getOrders()));
        return extCustomFieldMapper.list(request);
    }

    public Pager<List<CustomField>> listRelate(int goPage, int pageSize, QueryCustomFieldRequest request) {
        List<String> templateContainIds = request.getTemplateContainIds();
        if (CollectionUtils.isEmpty(templateContainIds)) {
            templateContainIds = new ArrayList<>();
        }
        request.setTemplateContainIds(templateContainIds);
        Page<List<CustomField>> page = PageHelper.startPage(goPage, pageSize, true);
        return PageUtils.setPageInfo(page, extCustomFieldMapper.listRelate(request));
    }

    public void delete(String id) {
        customFieldMapper.deleteByPrimaryKey(id);
        customFieldTemplateService.deleteByFieldId(id);
    }

    public CustomField get(String id) {
        return customFieldMapper.selectByPrimaryKey(id);
    }

    public void update(CustomField customField) {
        if (customField.getGlobal() != null && customField.getGlobal()) {
            CustomFieldDao customFieldDao = new CustomFieldDao();
            BeanUtils.copyBean(customFieldDao, customField);
            customFieldDao.setOriginGlobalId(customField.getId());
            // 如果是全局字段，则创建对应项目字段
            add(customFieldDao);
            if (StringUtils.equals(customField.getScene(), TemplateConstants.FieldTemplateScene.TEST_CASE.name())) {
                testCaseTemplateService.handleSystemFieldCreate(customFieldDao);
            } else {
                issueTemplateService.handleSystemFieldCreate(customFieldDao);
            }
        } else {
            checkExist(customField);
            customField.setUpdateTime(System.currentTimeMillis());
            customFieldMapper.updateByPrimaryKeySelective(customField);
        }
    }

    public List<CustomField> getGlobalField(String scene) {
        CustomFieldExample example = new CustomFieldExample();
        example.createCriteria()
                .andGlobalEqualTo(true)
                .andSceneEqualTo(scene);
        return customFieldMapper.selectByExampleWithBLOBs(example);
    }

    public List<CustomFieldDao> getCustomFieldByTemplateId(String templateId) {
        List<CustomFieldTemplate> customFields = customFieldTemplateService.getCustomFields(templateId);
        List<String> fieldIds = customFields.stream()
                .map(CustomFieldTemplate::getFieldId)
                .collect(Collectors.toList());

        List<CustomField> fields = getFieldByIds(fieldIds);
        Map<String, CustomField> fieldMap = fields.stream()
                .collect(Collectors.toMap(CustomField::getId, item -> item));

        List<CustomFieldDao> result = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(customFields)) {
            customFields.forEach((item) -> {
                CustomFieldDao customFieldDao = new CustomFieldDao();
                CustomField customField = fieldMap.get(item.getFieldId());
                BeanUtils.copyBean(customFieldDao, customField);
                BeanUtils.copyBean(customFieldDao, item);
                customFieldDao.setId(item.getFieldId());
                result.add(customFieldDao);
            });
        }
        return result.stream().distinct().collect(Collectors.toList());
    }

    public List<CustomField> getFieldByIds(List<String> ids) {
        if (CollectionUtils.isNotEmpty(ids)) {
            CustomFieldExample example = new CustomFieldExample();
            example.createCriteria()
                    .andIdIn(ids);
            return customFieldMapper.selectByExampleWithBLOBs(example);
        }
        return new ArrayList<>();
    }

    public List<CustomField> getDefaultField(QueryCustomFieldRequest request) {
        CustomFieldExample example = new CustomFieldExample();
        example.createCriteria()
                .andSystemEqualTo(true)
                .andSceneEqualTo(request.getScene())
                .andProjectIdEqualTo(request.getProjectId());
        List<CustomField> workspaceSystemFields = customFieldMapper.selectByExampleWithBLOBs(example);
        Set<String> workspaceSystemFieldNames = workspaceSystemFields.stream()
                .map(CustomField::getName)
                .collect(Collectors.toSet());
        List<CustomField> globalFields = getGlobalField(request.getScene());
        // 工作空间的系统字段加上全局的字段
        globalFields.forEach(item -> {
            if (!workspaceSystemFieldNames.contains(item.getName())) {
                workspaceSystemFields.add(item);
            }
        });
        return workspaceSystemFields;
    }

    public CustomField getGlobalFieldByName(String name) {
        CustomFieldExample example = new CustomFieldExample();
        example.createCriteria()
                .andGlobalEqualTo(true)
                .andNameEqualTo(name);
        List<CustomField> customFields = customFieldMapper.selectByExampleWithBLOBs(example);
        if (CollectionUtils.isNotEmpty(customFields)) {
            return customFields.get(0);
        }
        return null;
    }

    public List<String> listIds(QueryCustomFieldRequest request) {
        return extCustomFieldMapper.listIds(request);
    }

    private void checkExist(CustomField customField) {
        if (customField.getName() != null) {
            CustomFieldExample example = new CustomFieldExample();
            CustomFieldExample.Criteria criteria = example.createCriteria();
            criteria.andNameEqualTo(customField.getName());
            criteria.andProjectIdEqualTo(customField.getProjectId());
            criteria.andSceneEqualTo(customField.getScene());
            if (StringUtils.isNotBlank(customField.getId())) {
                criteria.andIdNotEqualTo(customField.getId());
            }
            if (customFieldMapper.selectByExample(example).size() > 0) {
                MSException.throwException(Translator.get("custom_field_already") + customField.getName());
            }
        }
    }

    public String getLogDetails(String id) {
        CustomField customField = customFieldMapper.selectByPrimaryKey(id);
        if (customField != null) {
            List<DetailColumn> columns = ReflexObjectUtil.getColumns(customField, SystemReference.fieldColumns);
            OperatingLogDetails details = new OperatingLogDetails(JSON.toJSONString(customField.getId()), null, customField.getName(), customField.getCreateUser(), columns);
            return JSON.toJSONString(details);
        }
        return null;
    }

    public static List<CustomFieldItemDTO> getCustomFields(String customFieldsStr) {
        if (StringUtils.isNotBlank(customFieldsStr)) {
            if (JSONObject.parse(customFieldsStr) instanceof JSONArray) {
                return JSONArray.parseArray(customFieldsStr, CustomFieldItemDTO.class);
            }
        }
        return new ArrayList<>();
    }

    public List<CustomFieldResource> getCustomFieldResource(String customFieldsStr) {
        List<CustomFieldResource> list = new ArrayList<>();
        if (StringUtils.isNotBlank(customFieldsStr)) {
            if (JSONObject.parse(customFieldsStr) instanceof JSONArray) {
                List<CustomFieldItemDTO> items = JSONArray.parseArray(customFieldsStr, CustomFieldItemDTO.class);
                for (CustomFieldItemDTO item : items) {
                    list.add(constructorCustomFieldResource(item));
                }
                return list;
            }
        }
        return new ArrayList<>();
    }

    private CustomFieldResource constructorCustomFieldResource(CustomFieldItemDTO dto) {
        CustomFieldResource resource = new CustomFieldResource();
        resource.setFieldId(dto.getId());
        if (StringUtils.isNotBlank(dto.getType()) &&
                StringUtils.equalsAny(dto.getType(), CustomFieldType.RICH_TEXT.getValue(), CustomFieldType.TEXTAREA.getValue())) {
            resource.setTextValue(dto.getValue() == null ? "" : dto.getValue().toString());
        } else {
            resource.setValue(JSONObject.toJSONString(dto.getValue()));
        }
        return resource;
    }

    public List<CustomField> getByProjectId(String projectId) {
        CustomFieldExample example = new CustomFieldExample();
        example.createCriteria().andProjectIdEqualTo(projectId);
        return customFieldMapper.selectByExample(example);
    }

    public Map<String, CustomField> getNameMapByProjectId(String projectId) {
        return this.getByProjectId(projectId)
                .stream()
                .collect(Collectors.toMap(i -> i.getName() + i.getScene(), i -> i, (val1, val2) -> val1));
    }

    public Map<String, CustomField> getGlobalNameMapByProjectId() {
        CustomFieldExample example = new CustomFieldExample();
        example.createCriteria().andGlobalEqualTo(true);
        return customFieldMapper.selectByExample(example)
                .stream()
                .collect(Collectors.toMap(i -> i.getName() + i.getScene(), i -> i));
    }

    public Map<String, String> getIssueSystemCustomFieldByName(String fieldName, String projectId, List<String> resourceIds) {
        if (CollectionUtils.isEmpty(resourceIds)) {
            return null;
        }
        Project project = projectMapper.selectByPrimaryKey(projectId);
        if (project == null) {
            return null;
        }
        String templateId = project.getIssueTemplateId();
        if (StringUtils.isBlank(templateId)) {
            return null;
        }
        // 模版对于同一个系统字段应该只关联一次
        List<String> fieldIds = customFieldTemplateService.getSystemCustomField(templateId, fieldName);
        if (CollectionUtils.isEmpty(fieldIds)) {
            return null;
        }
        // 该系统字段的自定义ID
        String customFieldId = fieldIds.get(0);
        CustomFieldIssuesExample example = new CustomFieldIssuesExample();
        example.createCriteria().andFieldIdEqualTo(customFieldId).andResourceIdIn(resourceIds);
        List<CustomFieldIssues> customFieldIssues = customFieldIssuesMapper.selectByExample(example);
        return customFieldIssues.stream().collect(Collectors.toMap(CustomFieldIssues::getResourceId, CustomFieldIssues::getValue));
    }
}
