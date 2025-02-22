<template>
  <el-dialog :close-on-click-modal="false" :visible.sync="visible" width="900px"
             @close="saveAndClose" destroy-on-close ref="editFile">
     <span slot="title" class="dialog-footer">
       <span>{{ data.name }}</span>
       <i class="el-icon-download ms-header-menu" @click="download"/>
       <i class="el-icon-delete ms-header-menu" @click="deleteData"/>
     </span>
    <el-row align="center" v-loading="loading">
      <el-col style="margin: 10px" :span="10">
        <el-row :gutter="20" style="background: #F5F6F8;height: 480px">
          <el-col :span="2" class="ms-left-col">
            <i class="el-icon-arrow-left ms-icon-arrow" @click="beforeData"/>
          </el-col>
          <el-col :span="18" style="padding-top: 80px">
            <el-card :body-style="{ padding: '0px' }" v-if="isImage(data.type)">
              <img :src="'/file/metadata/info/' + data.id" class="ms-edit-image"/>
            </el-card>
            <el-card :body-style="{ padding: '0px' }" v-else>
              <div class="ms-edit-image">
                <div class="ms-file-item">
                  <div class="icon-title">{{ getType(data.type) }}</div>
                </div>
              </div>
            </el-card>
          </el-col>
          <el-col :span="2" class="ms-right-col">
            <i class="el-icon-arrow-right ms-icon-arrow" @click="nextData"/>
          </el-col>
        </el-row>
      </el-col>

      <el-col :span="13">
        <el-form :model="data" label-position="right" label-width="80px" size="small">
          <!-- 基础信息 -->
          <el-form-item :label="$t('commons.description')" prop="description">
            <el-input class="ms-http-textarea"
                      v-model="data.description"
                      type="textarea"
                      :rows="2" size="small" @blur="save"/>
          </el-form-item>

          <el-form-item :label="$t('load_test.file_name')" prop="name">
            <el-input
              class="ms-file-item-input"
              size="small"
              v-model="data.name"
              show-word-limit @blur="save"/>
          </el-form-item>

          <el-form-item :label="$t('load_test.file_type')" prop="type">
            <span>{{ data.type }}</span>
          </el-form-item>

          <el-form-item :label="$t('load_test.file_size')" prop="size">
            <span>{{ data.size }} KB</span>
          </el-form-item>

          <el-form-item :label="$t('api_test.automation.tag')" prop="tags">
            <ms-input-tag :currentScenario="data" ref="tag" @onblur="save"/>
          </el-form-item>

          <el-form-item :label="$t('test_track.case.module')" prop="name">
            <ms-select-tree size="small" :data="moduleOptions" :defaultKey="data.moduleId"
                            @getValue="setModule" :obj="moduleObj" clearable checkStrictly/>

          </el-form-item>

          <el-form-item :label="$t('project.creator')" prop="createUser">
            <span>{{ data.createUser }}</span>
          </el-form-item>

          <el-form-item :label="$t('commons.create_time')" prop="createTime">
            <span>{{ data.createTime | timestampFormatDate }}</span>
          </el-form-item>

          <el-form-item :label="'加载Jar包'" prop="loadJar" v-if="data.type === 'JAR'">
            <el-switch v-model="data.loadJar" :active-text="$t('project.file_jar_message')" @change="save"/>
          </el-form-item>

          <el-form-item :label="$t('project.upload_file_again')" prop="name">
            <el-upload
              style="width: 38px; float: left;"
              action="#"
              :http-request="handleUpload"
              :show-file-list="false">
              <el-button icon="el-icon-plus" size="mini"/>
            </el-upload>
          </el-form-item>

        </el-form>
      </el-col>
    </el-row>
  </el-dialog>
</template>

<script>
import {getCurrentProjectID, operationConfirm} from "@/common/js/utils";

export default {
  name: "MsEditFileMetadata",
  components: {
    MsSelectTree: () => import("../../../../common/select-tree/SelectTree"),
    MsInputTag: () => import("../../../../api/automation/scenario/MsInputTag"),
  },
  data() {
    return {
      data: {},
      visible: false,
      isFirst: false,
      isLast: false,
      results: [],
      moduleObj: {
        id: 'id',
        label: 'name',
      },
      total: 0,
      pageSize: 10,
      loading: false,
      currentPage: 1,
      images: ["bmp", "jpg", "png", "tif", "gif", "pcx", "tga", "exif", "fpx", "svg", "psd", "cdr", "pcd", "dxf", "ufo", "eps", "ai", "raw", "WMF", "webp", "avif", "apng"]
    };
  },
  props: {
    moduleOptions: Array,
    metadataArray: {
      type: Array,
      default() {
        return [];
      },
    },
    condition: {
      type: Object,
      default() {
        return {};
      },
    },
  },
  methods: {
    setModule(id, data) {
      if (this.data.moduleId !== id) {
        this.data.moduleId = id;
        this.save();
        this.$emit('refreshModule');
      }
    },
    close() {
      this.visible = false;
    },
    saveAndClose() {
      this.visible = false;
      this.$emit("setCurrentPage", this.currentPage);
    },
    open(data, size, page, t) {
      this.pageSize = size;
      this.currentPage = page;
      this.total = t;
      this.data = data;
      this.results = this.metadataArray;
      this.visible = true;
    },
    save() {
      let request = JSON.parse(JSON.stringify(this.data));
      request.tags = JSON.stringify(request.tags);
      this.$post('/file/metadata/update', request, () => {
      });
    },
    getType(type) {
      return type ? type.substr(0, 1).toUpperCase() : "";
    },
    isImage(type) {
      return (type && this.images.indexOf(type.toLowerCase()) !== -1);
    },
    download() {
      this.$emit("download", this.data);
    },
    deleteData() {
      operationConfirm(this, this.$t('project.file_delete_tip', [this.data.name]), () => {
        this.close();
        this.data.confirm = true;
        this.$emit("delete", this.data);
      });
    },
    handleUpload(uploadResources) {
      let file = uploadResources.file;
      let formData = new FormData();
      let url = '/file/metadata/upload';
      let request = JSON.parse(JSON.stringify(this.data));
      request.tags = JSON.stringify(request.tags);
      formData.append("request", new Blob([JSON.stringify(request)], {type: "application/json"}));
      formData.append("file", file);
      let options = {
        method: 'POST',
        url: url,
        data: formData,
        headers: {
          'Content-Type': undefined
        }
      };
      this.$request(options).then(response => {
        this.$success(this.$t('commons.save_success'));
        if (response.data && response.data.data) {
          this.data = response.data.data;
          if (this.data.tags) {
            this.data.tags = JSON.parse(this.data.tags);
          }
          this.$emit("reload");
        }
      });
    },
    getProjectFiles(before) {
      this.loading = true;
      this.$post('/file/metadata/project/' + getCurrentProjectID() + "/" + this.currentPage + "/" + this.pageSize, this.condition, res => {
        let data = res.data;
        this.total = data.itemCount;
        this.results = data.listObject;
        this.results.forEach(item => {
          if (item.tags && item.tags.length > 0) {
            item.tags = JSON.parse(item.tags);
          }
        });
        if (this.results.length > 0) {
          let index = before ? this.results.length - 1 : 0;
          this.data = this.results[index];
        }
        this.loading = false;
      });
    },
    beforeData() {
      const index = this.results.findIndex(e => e.id === this.data.id);
      this.isFirst = index <= 0;
      if (!this.isFirst) {
        this.data = this.results[index - 1];
      } else {
        if (this.currentPage === 1) {
          this.$warning(this.$t("project.file_first"));
        } else {
          // 向上翻页
          this.currentPage--;
          this.getProjectFiles(true);
        }
      }
    },
    nextData() {
      const index = this.results.findIndex(e => e.id === this.data.id);
      this.isLast = (this.results.length - 1) === index;
      if (!this.isLast) {
        this.data = this.results[index + 1];
      } else {
        let totalPages = Math.ceil(this.total / this.pageSize);
        if (totalPages === this.currentPage) {
          this.$warning(this.$t("project.file_last"));
        } else {
          // 向后翻页
          this.currentPage++;
          this.getProjectFiles(false);
        }
      }
    }
  },
}
</script>

<style scoped>
.ms-edit-image {
  width: 100%;
  height: 320px;
  display: block;
  background: #407C51;
}

.ms-edit-image:hover {
  border-color: var(--color);
  cursor: pointer;
}

.ms-file-item {
  text-align: center;
  padding-top: 140px
}

.icon-title {
  color: #fff;
  text-align: center;
  font-size: 22px;
}


.ms-file-item-input {
  width: 100%;
}

.ms-left-col {
  padding-top: 220px;
  margin-right: 15px;
}

.ms-icon-arrow {
  font-size: 20px;
}

.ms-icon-arrow:hover {
  font-size: 21px;
  cursor: pointer;
  color: var(--color);
}

.ms-right-col {
  padding-top: 220px;
  margin-left: 10px
}

.ms-header-menu {
  font-size: 18px;
  margin-left: 10px;
}

.ms-header-menu:hover {
  cursor: pointer;
  color: var(--color);
}
</style>
