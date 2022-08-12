<template>
  <div>
    <ms-module-minder
      v-loading="result.loading"
      minder-key="PLAN_CASE"
      :tree-nodes="treeNodes"
      :data-map="dataMap"
      :tags="tags"
      :disabled="disableMinder"
      :tag-edit-check="tagPlanEditCheck()"
      :select-node="selectNode"
      :distinct-tags="[...tags, this.$t('test_track.plan.plan_status_prepare')]"
      :ignore-num="true"
      :module-disable="true"
      @afterMount="handleAfterMount"
      @save="save"
      ref="minder"
    />

    <IssueRelateList
      :plan-case-id="getCurId()"
      :case-id="getCurCaseId()"
      @refresh="refreshRelateIssue"
      ref="issueRelate"/>

    <test-plan-issue-edit
      :is-minder="true"
      :plan-id="planId"
      :plan-case-id="getCurId()"
      :case-id="getCurCaseId()"
      @refresh="refreshIssue"
      ref="issueEdit"/>

<!--    <is-change-confirm
      @confirm="changeConfirm"
      ref="isChangeConfirm"/> -->

  </div>
</template>

<script>
import {setPriorityView} from "vue-minder-editor-plus/src/script/tool/utils";

const {getCurrentWorkspaceId} = require("@/common/js/utils");
const {getIssuesListById} = require("@/network/Issue");
import MsModuleMinder from "@/business/components/common/components/MsModuleMinder";
import {getNodePath, getUUID, hasPermission} from "@/common/js/utils";
import {
  getChildNodeId,handleAfterSave,isCaseNodeData,isModuleNodeData,
  isModuleNode,handTestCaeEdit,tagPlanEditCheck,
  handleExpandToLevel, listenBeforeExecCommand, listenNodeSelected, loadSelectNodes,
  tagPlanBatch, getSelectedNodeData, handleIssueAdd, handleIssueBatch, listenDblclick, handleMinderIssueDelete
} from "@/business/components/track/common/minder/minderUtils";
import {getPlanCasesForMinder} from "@/network/testCase";
import IssueRelateList from "@/business/components/track/case/components/IssueRelateList";
import TestPlanIssueEdit from "@/business/components/track/case/components/TestPlanIssueEdit";
// import IsChangeConfirm from "@/business/components/common/components/IsChangeConfirm";

import {addIssueHotBox} from "./minderUtils";
export default {
name: "TestPlanMinder",
  components: {MsModuleMinder, TestPlanIssueEdit, IssueRelateList},
  data() {
    return{
      dataMap: new Map(),
      result: {loading: false},
      tags: [this.$t('test_track.plan_view.pass'), this.$t('test_track.plan_view.failure'), this.$t('test_track.plan_view.blocking'), this.$t('test_track.plan_view.skip'),this.$t('api_test.definition.request.case'), this.$t('test_track.case.prerequisite'), this.$t('commons.remark'), this.$t('test_track.module.module')],
      needRefresh: false,
      noRefresh: false,
      noRefreshMinder: false,
      saveCases: [],
      saveModules: [],
      saveModuleNodeMap: new Map(),
      deleteNodes: [], // 包含测试模块、用例和临时节点
      saveExtraNode: {},
      extraNodeChanged: [] // 记录转成用例或模块的临时节点
    }
  },
  props: {
    treeNodes: {
      type: Array,
      default() {
        return []
      }
    },
    selectNodeIds: {
      type: Array
    },
    planId: {
      type: String
    },
    planStatus: {
      type: String
    },
    projectId: String,
    condition: Object,
    activeName: String
  },
  computed: {
    selectNode() {
      return this.$store.state.testPlanViewSelectNode;
    },
    moduleOptions() {
      return this.$store.state.testCaseModuleOptions;
    },
    testCaseDefaultValue() {
      return this.$store.state.testCaseDefaultValue;
    },

    workspaceId(){
      return getCurrentWorkspaceId();
    },
    disableMinder(){
      if (this.planStatus === 'Archived') {
       return  true
      } else {
        return false
      }
    }
  },
  mounted() {
    this.setIsChange(false);
    if (this.selectNode && this.selectNode.data) {
      if (this.$refs.minder) {
        let importJson = this.$refs.minder.getImportJsonBySelectNode(this.selectNode.data);
        this.$refs.minder.setJsonImport(importJson);
      }
    }
  },
  watch: {
    selectNode() {
      if (this.$refs.minder) {
        this.$refs.minder.handleNodeSelect(this.selectNode);
      }
    },
    treeNodes() {
      this.$refs.minder.initData();
    }
    // treeNodes(newVal, oldVal) {
    //   if (newVal !== oldVal && this.activeName === 'default')  {
    //     // 模块刷新并且当前 tab 是用例列表时，提示是否刷新脑图
    //     this.handleNodeUpdateForMinder();
    //   }
    // }

  },
  methods: {
    handleAfterMount: function () {
      listenNodeSelected(() => {
        loadSelectNodes(this.getParam(), getPlanCasesForMinder, this.setParamCallback);
      });
      listenBeforeExecCommand((even) => {
        if (even.commandName === 'expandtolevel') {
          let level = Number.parseInt(even.commandArgs);
          handleExpandToLevel(level, even.minder.getRoot(), this.getParam(), getPlanCasesForMinder, this.setParamCallback);
        }

        if (handleMinderIssueDelete(even.commandName, true))  return; // 删除缺陷不算有编辑脑图信息

        if (even.commandName.toLocaleLowerCase() === 'resource') {
          // 设置完标签后，优先级显示有问题，重新设置下
          setTimeout(() => setPriorityView(true, 'P'), 100);

          this.setIsChange(true);
        }
      });

      listenDblclick(() => {
        let data = getSelectedNodeData();
        if (data.type === 'issue') {
          getIssuesListById(data.id, this.projectId,this.workspaceId,(data) => {
            data.customFields = JSON.parse(data.customFields);
            this.$refs.issueEdit.open(data);
          });
        }
      });

      // listenDblclick(() => {
      //   let minder = window.minder;
      //   let selectNodes = minder.getSelectedNodes();
      //   let isNotDisableNode = false;
      //   selectNodes.forEach(node => {
      //     // 如果鼠标双击了非模块的节点，表示已经编辑
      //     if (!node.data.disable) {
      //       isNotDisableNode = true;
      //     }
      //     if (node.data.type === 'issue') {
      //       getIssuesListById(node.data.id, this.projectId,this.workspaceId,(data) => {
      //         data.customFields = JSON.parse(data.customFields);
      //         this.$refs.issueEdit.open(data);
      //       });
      //     }
      //   });
      //   if (isNotDisableNode) {
      //     this.setIsChange(true);
      //   }
      // });

      let statusTags = [this.$t('test_track.plan_view.pass'), this.$t('test_track.plan_view.failure'), this.$t('test_track.plan_view.blocking'), this.$t('test_track.plan_view.skip'),this.$t('test_track.plan_view.not_pass')];

      // tagBatch([...this.tags, this.$t('test_track.plan.plan_status_prepare')]);
      tagPlanBatch([...statusTags, this.$t('test_track.plan.plan_status_prepare')]);
      // addIssueHotBox(this);

    },
    getParam() {
      return {
        request: {
          planId: this.planId,
          orders: this.condition.orders
        },
        result: this.result,
        // isDisable: true
        isDisable: false
      }
    },
    setParamCallback(data, item) {
      if (item.status === 'Pass') {
        data.resource.push(this.$t('test_track.plan_view.pass'));
      } else if (item.status === 'Failure') {
        data.resource.push(this.$t('test_track.plan_view.failure'));
      } else if (item.status === 'Blocking') {
        data.resource.push(this.$t('test_track.plan_view.blocking'));
      } else if (item.status === 'Skip') {
        data.resource.push(this.$t('test_track.plan_view.skip'));
      } else {
        data.resource.push(this.$t('test_track.plan.plan_status_prepare'));
      }
    },


    handleNodeUpdateForMinder() {
      if (this.noRefreshMinder) {
        // 如果是保存触发的刷新模块，则不刷新脑图
        this.noRefreshMinder = false;
        return;
      }
      this.noRefresh = true;
      // 如果脑图没有修改直接刷新，有修改提示
      if (!this.$store.state.isTestCaseMinderChanged) {
        if (this.$refs.minder) {
          this.$refs.minder.initData();
        }
      } else {
        this.$refs.isChangeConfirm.open();
      }
    },
    save(callback) {
      this.saveCases = [];
      this.saveModules = [];
      this.deleteNodes = []; // 包含测试模块、用例和临时节点
      this.saveExtraNode = {};
      this.saveModuleNodeMap = new Map();
      this.buildSaveParam(window.minder.getRoot());

      this.saveModules.forEach(module => {
        let nodeIds = [];
        getChildNodeId(this.saveModuleNodeMap.get(module.id), nodeIds);
        module.nodeIds = nodeIds;
      });

      let param = {
        projectId: this.projectId,
        data: this.saveCases,
        ids: this.deleteNodes.map(item => item.caseId),
        testCaseNodes: this.saveModules,
        extraNodeRequest:  {
          groupId: this.projectId,
          type: "TEST_CASE",
          data: this.saveExtraNode,
        }
      }

      this.result = this.$post('/test/case/minder/edit', param, () => {
        this.$success(this.$t('commons.save_success'));
        handleAfterSave(window.minder.getRoot());
        this.extraNodeChanged.forEach(item => {
          item.isExtraNode = false;
        });
        this.extraNodeChanged = [];
        // if (!this.noRefresh) {
        //   this.$emit('refresh');
        //   // 保存会刷新模块，刷新完模块，脑图也会自动刷新
        //   // 如果是保存触发的刷新模块，则不刷新脑图
        //   this.noRefreshMinder = true;
        // }
        // // 由于模块修改刷新的脑图，不刷新模块
        // this.noRefresh = false;

        this.setIsChange(false);
        if (callback && callback instanceof Function) {
          callback();
        }

        // 核对检查savecases 中的所有case, 有isEdit false 表示新增，需要单独配置关联
        let newCase = this.saveCases.filter(item => (item.isEdit === false));

        if(newCase.length > 0){
          this.relevance(newCase);
        }

      });



    },
    buildSaveParam(root, parent, preNode, nextNode) {
      let data = root.data;

      if (isCaseNodeData(data)) {
        this.buildSaveCase(root, parent, preNode, nextNode);
      } else {
        let deleteChild = data.deleteChild;
        if (deleteChild && deleteChild.length > 0 && isModuleNodeData(data)) {
          this.deleteNodes.push(...deleteChild);
        }

        if (data.type !== 'tmp' && data.changed) {
          if (isModuleNodeData(data)) {
            if (data.contextChanged) {
              this.buildSaveModules(root, data, parent);
              root.children && root.children.forEach(i => {
                if (isModuleNode(i)) {
                  i.data.changed = true;
                  i.data.contextChanged = true; // 如果当前节点有变化，下面的模块节点也需要level也可能需要变化
                }
              });
            }
          } else {
            // 此处需要新增条件和判断 保存临时节点
            this.buildExtraNode(data, parent, root);
          }
        }

        if (root.children) {
          for (let i = 0; i < root.children.length; i++) {
            let childNode = root.children[i];
            let preNodeTmp = null;
            let nextNodeTmp = null;
            if (i != 0) {
              preNodeTmp = root.children[i - 1];
            }
            if (i + 1 < root.children.length) {
              nextNodeTmp = root.children[i + 1];
            }
            this.buildSaveParam(childNode, root.data, preNodeTmp, nextNodeTmp);
          }
        }
      }
    },
    buildSaveModules(node, data, parent) {
      if (!data.text) {
        return;
      }
      let pId = parent ? (parent.newId ? parent.newId : parent.id) : null;

      if (parent && !isModuleNodeData(parent)) {
        this.throwError(this.$t('test_track.case.minder_not_module_tip', [data.text]));
      }

      let module = {
        id: data.id,
        name: data.text,
        level: parent ? parent.level + 1 : data.level,
        parentId: pId
      };
      data.level = module.level;

      if (data.isExtraNode) {
        // 如果是临时节点，打上了模块标签，则删除临时节点并新建模块
        this.pushDeleteNode(data);
        module.id = null;
        this.extraNodeChanged.push(data);
      }



      if (data.type === 'case') {
        // 如果是用例节点，打上了模块标签，则用例节点并新建模块
        this.pushDeleteNode(data);
        module.id = null;
      }

      if (module.id && module.id.length > 20) {
        module.isEdit = true; // 编辑
      } else {
        module.isEdit = false; // 新增
        module.id = getUUID();
        data.newId = module.id;
        this.moduleOptions.push({id: data.newId, path: getNodePath(pId, this.moduleOptions) + '/' + module.name});
      }

      this.saveModuleNodeMap.set(module.id, node);

      if (module.level > 8) {
        this.throwError(this.$t('commons.module_deep_limit'));
      }
      this.saveModules.push(module);
    },
    buildExtraNode(data, parent, root) {
      if (data.type !== 'node' && data.type !== 'tmp' && parent && isModuleNodeData(parent) && data.changed === true) {
        // 保存额外信息，只保存模块下的一级子节点
        let nodes = this.saveExtraNode[parent.id];
        if (!nodes) {
          nodes = [];
        }
        nodes.push(JSON.stringify(this._buildExtraNode(root)));
        this.saveExtraNode[parent.newId ? parent.newId : parent.id] = nodes;
      }
    },
    validate(parent, data) {
      if (parent.id === 'root') {
        this.throwError(this.$t('test_track.case.minder_all_module_tip'));
      }

      if (parent.isExtraNode && !isModuleNodeData(parent)) {
        this.throwError(this.$t('test_track.case.minder_tem_node_tip', [parent.text]));
      }

      if (data.type === 'node') {
        this.throwError(this.$t('test_track.case.minder_is_module_tip', [data.text]));
      }
    },
    buildSaveCase(node, parent, preNode, nextNode) {
      let data = node.data;

      if (!data.text) {
        return;
      }

      this.validate(parent, data);

      let isChange = false;

      let nodeId = parent ? (parent.newId ? parent.newId : parent.id) : "";
      let priorityDefaultValue;
      if (data.priority ) {
        priorityDefaultValue = 'P' + (data.priority - 1);
      } else {
        priorityDefaultValue = this.testCaseDefaultValue['用例等级'] ? this.testCaseDefaultValue['用例等级'] : 'P' + 0;
      }


      let testCase = {
        id: data.caseId,
        name: data.text,
        nodeId: nodeId,
        nodePath: getNodePath(nodeId, this.moduleOptions),
        type: data.type ? data.type : 'functional',
        method: data.method ? data.method : 'manual',
        maintainer: this.testCaseDefaultValue['责任人'] ? this.testCaseDefaultValue['责任人'] : data.maintainer,
        priority: priorityDefaultValue,
        prerequisite: "",
        remark: "",
        stepDescription: "",
        expectedResult: "",
        status: this.testCaseDefaultValue['用例状态'],
        steps: [{
          num: 1,
          desc: '',
          result: ''
        }],
        tags: "[]",
        stepModel: "STEP"
      };
      if (data.changed) isChange = true;
      let steps = [];
      let stepNum = 1;
      if (node.children) {
        node.children.forEach((childNode) => {
          let childData = childNode.data;
          if (childData.type === 'issue') return;
          if (childData.resource && childData.resource.indexOf(this.$t('test_track.case.prerequisite')) > -1) {
            testCase.prerequisite = childData.text;
            if (childNode.children && childNode.children.length > 0) {
              this.throwError('[' + testCase.name + ']前置条件下不能添加子节点！');
            }
          } else if (childData.resource && childData.resource.indexOf(this.$t('commons.remark')) > -1) {
            testCase.remark = childData.text;
             if (childNode.children && childNode.children.length > 0) {
               this.throwError('[' + testCase.name + ']备注下不能添加子节点！');
             }
          } else {
            // 测试步骤
            let step = {};
            step.num = stepNum++;
            step.desc = childData.text;
            if (childNode.children) {
              let result = "";
              childNode.children.forEach((child) => {
                result += child.data.text;
                if (child.data.changed) {
                  isChange = true;
                }
              })
              step.result = result;
            }
            steps.push(step);

            if (data.stepModel === 'TEXT') {
              testCase.stepDescription = step.desc;
              testCase.expectedResult = step.result;
            }
          }
          if (childData.changed) {
            isChange = true;
          }

          childNode.children.forEach((child) => {
            if (child.children && child.children.length > 0) {
              this.throwError('['+ testCase.name + ']用例下子节点不能超过两层！');
            }
          });
        })
      }
      testCase.steps = JSON.stringify(steps);

      if (data.isExtraNode) {
        // 如果是临时节点，打上了用例标签，则删除临时节点并新建用例节点
        this.pushDeleteNode(data);
        testCase.id = null;
        this.extraNodeChanged.push(data);
      }

      if (isChange) {

        testCase.targetId = null; // 排序处理

        if (this.moveEnable) {
          if (this.isCaseNode(preNode)) {
            let preId = preNode.data.caseId;
            if (preId && preId.length > 15) {
              testCase.targetId = preId;
              testCase.moveMode = 'AFTER';
            } else {
              testCase.moveMode = 'APPEND';
            }
          } else if (this.isCaseNode(nextNode) && !nextNode.data.isExtraNode) {
            let nextId = nextNode.data.caseId;
            if (nextId && nextId.length > 15) {
              testCase.targetId = nextId;
              testCase.moveMode = 'BEFORE';
            }
          }
        }

        if (data.resource.length > 1) {
          if (data.resource.indexOf(this.$t('test_track.plan_view.failure')) > -1) {
            testCase.status = 'Failure';
          } else if (data.resource.indexOf(this.$t('test_track.plan_view.pass')) > -1) {
            testCase.status = 'Pass';
          } else if (data.resource.indexOf(this.$t('test_track.plan_view.blocking')) > -1) {
            testCase.status = 'Blocking';
          } else if (data.resource.indexOf(this.$t('test_track.plan_view.skip')) > -1) {
            testCase.status = 'Skip';
          }
        }

        if (testCase.id && testCase.id.length > 20) {
          testCase.isEdit = true; // 编辑
        } else {
          testCase.isEdit = false; // 新增
          testCase.id = getUUID();
          data.newId = testCase.id;
        }
        this.saveCases.push(testCase);
      }
      if (testCase.nodeId !== 'root' && testCase.nodeId.length < 15) {
        this.throwError(this.$t('test_track.case.create_case') + "'" + testCase.name + "'" + this.$t('test_track.case.minder_create_tip'));
      }
    },
    pushDeleteNode(data) {
      // 如果是临时节点，打上了用例标签，则删除临时节点并新建用例节点
      let deleteData = {};
      Object.assign(deleteData, data);
      this.deleteNodes.push(deleteData);
      data.id = "";
    },
    isCaseNode(node) {
      if (node && node.data.resource && node.data.resource.indexOf(this.$t('api_test.definition.request.case')) > -1) {
        return true;
      }
      return false;
    },
    _buildExtraNode(node) {
      let data = node.data;
      let nodeData = {
        text: data.text,
        id: data.id,
        resource: data.resource,
      };
      if (nodeData.id && nodeData.id.length > 20) {
        nodeData.isEdit = true; // 编辑
      } else {
        nodeData.isEdit = false; // 新增
        nodeData.id = getUUID();
        data.newId = nodeData.id;
      }
      if (node.children) {
        nodeData.children = [];
        node.children.forEach(item => {
          nodeData.children.push(this._buildExtraNode(item));
        });
      }
      data.isExtraNode = true;
      return nodeData;
    },
    tagPlanEditCheck() {
      return tagPlanEditCheck;
    },
    refresh() {
      // 切换tab页，如果没有修改用例，不刷新脑图
      if (this.needRefresh) {
        let jsonImport = window.minder.exportJson();
        this.$refs.minder.setJsonImport(jsonImport);
        this.$nextTick(() => {
          if (this.$refs.minder) {
            this.$refs.minder.reload();
          }
        });
        this.needRefresh = false;
      }
    },
    relevance(newCase){
      // 将新键的用例关联测试计划planId
      let relevanceParam = {
        planId: this.planId,
        checked: true,
        ids: newCase.map(item =>(item.id)),
        request:  {
          components: [],
          nodeIds: [],
          projectId: this.projectId,
          planId: this.planId,
          selectAll: false,
          unSelectIds: []
        }
      }

      this.result = this.$post('/test/plan/relevance', relevanceParam, () => {

      });

    },


    getCurCaseId() {
      return getSelectedNodeData().caseId;
    },
    getCurId() {
      return getSelectedNodeData().id;
    },
    refreshIssue(issue) {
      handleIssueAdd(issue);
    },
    refreshRelateIssue(issues) {
      handleIssueBatch(issues);
      this.$success('关联成功');
    },
    setIsChange(isChanged) {
      this.$store.commit('setIsTestCaseMinderChanged', isChanged);
    },
  }
}
</script>

<style scoped>

</style>
