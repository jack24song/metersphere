<template>
  <el-card class="table-card">

    <template v-slot:header>
      <ms-table-header :condition.sync="condition" @search="initTableData" @create="handleCreate"
                       :create-tip="$t('custom_field.create_issue_template')"/>
    </template>

    <ms-table
      v-loading="result.loading"
      :data="tableData"
      :condition="condition"
      :total="total"
      :page-size.sync="pageSize"
      :operators="operators"
      :screen-height="tableHeight"
      :enable-selection="false"
      @handlePageChange="initTableData"
      @refresh="initTableData"
      ref="table">

      <ms-table-column
        :label="$t('commons.name')"
        :fields="fields"
        prop="name">
        <template v-slot="scope">
          <span v-if="scope.row.system">{{ scope.row.name }}(默认模板)</span>
          <span v-else>{{ scope.row.name }}</span>
        </template>
      </ms-table-column>

      <ms-table-column
        :label="$t('custom_field.issue_platform')"
        :fields="fields"
        :filters="platformFilters"
        prop="platform">
        <template v-slot="scope">
          <span>{{ issuePlatformMap[scope.row.platform] }}</span>
        </template>
      </ms-table-column>

      <ms-table-column
        :label="$t('commons.description')"
        :fields="fields"
        prop="description">
      </ms-table-column>

      <ms-table-column
        sortable
        :label="$t('commons.create_time')"
        :fields="fields"
        prop="createTime">
        <template v-slot="scope">
          <span>{{ scope.row.createTime | timestampFormatDate }}</span>
        </template>
      </ms-table-column>

      <ms-table-column
        sortable
        :label="$t('commons.update_time')"
        :fields="fields"
        prop="updateTime">
        <template v-slot="scope">
          <span>{{ scope.row.updateTime | timestampFormatDate }}</span>
        </template>
      </ms-table-column>
    </ms-table>

    <ms-table-pagination :change="initTableData" :current-page.sync="currentPage" :page-size.sync="pageSize"
                         :total="total"/>

    <issue-template-copy ref="templateCopy" @refresh="initTableData"/>
    <issue-template-edit ref="templateEdit" @refresh="initTableData"/>
    <ms-delete-confirm :title="$t('commons.template_delete')" @delete="_handleDelete" ref="deleteConfirm"/>
  </el-card>
</template>

<script>
import {ISSUE_TEMPLATE_LIST} from "@/common/js/default-table-header";
import {ISSUE_PLATFORM_OPTION} from "@/common/js/table-constants";
import {getCurrentProjectID} from "@/common/js/utils";
import MsTableHeader from "@/business/components/common/components/MsTableHeader";
import MsTablePagination from "@/business/components/common/pagination/TablePagination";
import MsTableButton from "@/business/components/common/components/MsTableButton";
import MsTableOperators from "@/business/components/common/components/MsTableOperators";
import MsTableColumn from "@/business/components/common/components/table/MsTableColumn";
import MsTable from "@/business/components/common/components/table/MsTable";
import TestCaseReportTemplate from "@/business/components/project/template/TestCaseReportTemplate";
import IssueTemplateCopy from "@/business/components/project/template/IssueTemplateCopy";
import IssueTemplateEdit from "@/business/components/project/template/IssueTemplateEdit";
import MsDeleteConfirm from "@/business/components/common/components/MsDeleteConfirm";

export default {
  name: "IssuesTemplateList",
  components: {
    IssueTemplateEdit,
    IssueTemplateCopy,
    TestCaseReportTemplate,
    MsTableHeader,
    MsTablePagination,
    MsTableButton,
    MsTableOperators,
    MsTableColumn,
    MsTable,
    MsDeleteConfirm
  },
  data() {
    return {
      tableData: [],
      condition: {},
      total: 0,
      pageSize: 10,
      currentPage: 1,
      result: {},
      issuePlatformMap: {
        Local: 'Metersphere',
        Jira: 'JIRA',
        Tapd: 'Tapd',
        Zentao: '禅道',
        AzureDevops: 'Azure Devops',
      },
      operators: [
        {
          tip: this.$t('commons.edit'), icon: "el-icon-edit",
          exec: this.handleEdit
        }, {
          tip: this.$t('commons.copy'), icon: "el-icon-copy-document", type: "success",
          exec: this.handleCopy
        }, {
          tip: this.$t('commons.delete'), icon: "el-icon-delete", type: "danger",
          exec: this.handleDelete
        }
      ],
    };
  },
  created() {
    this.initTableData();
  },
  computed: {
    fields() {
      return ISSUE_TEMPLATE_LIST;
    },
    platformFilters() {
      return ISSUE_PLATFORM_OPTION;
    },
    tableHeight() {
      return document.documentElement.clientHeight - 200;
    }
  },
  methods: {
    initTableData() {
      this.condition.projectId = getCurrentProjectID();
      this.result = this.$post('field/template/issue/list/' + this.currentPage + '/' + this.pageSize,
        this.condition, (response) => {
          let data = response.data;
          this.total = data.itemCount;
          this.tableData = data.listObject;
          if (this.$refs.table) {
            this.$refs.table.reloadTable();
          }
        });
    },
    handleEdit(data) {
      this.$refs.templateEdit.open(data);
    },
    handleCreate() {
      this.$refs.templateEdit.open();
    },
    handleCopy(data) {
      let copyData = {};
      Object.assign(copyData, data);
      this.$refs.templateCopy.open(copyData);
    },
    handleDelete(data) {
      this.$refs.deleteConfirm.open(data);
    },
    _handleDelete(data) {
      this.result = this.$get('field/template/issue/delete/' + data.id, () => {
        this.$success(this.$t('commons.delete_success'));
        this.initTableData();
      });
    },
    systemDisable(row) {
      if (row.system) {
        return true;
      }
      return false;
    }
  }
};
</script>

<style scoped>
</style>
